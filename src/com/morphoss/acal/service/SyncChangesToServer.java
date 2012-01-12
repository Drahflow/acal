/*
 * Copyright (C) 2011 Morphoss Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLException;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.database.DMAction;
import com.morphoss.acal.database.DMQueryBuilder;
import com.morphoss.acal.database.DatabaseTableManager.QUERY_ACTION;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.WriteableResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.BlockingResourceRequest;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.connector.AcalRequestor;
import com.morphoss.acal.service.connector.ConnectionFailedException;
import com.morphoss.acal.service.connector.SendRequestFailedException;

public class SyncChangesToServer extends ServiceJob implements BlockingResourceRequest {

	public static final String	TAG					= "aCal SyncChangesToServer";
	
	private boolean processed = false;
	private static boolean DEBUG = true && Constants.DEBUG_MODE;
	
	private long timeToWait = 90000;
	private aCalService acalService;

	private AcalRequestor requestor;
	
	private ArrayList<ContentValues> pendingChangesList = null;
	private int pendingPos = -1;
	private Set<Integer> collectionsToSync = null;
	
	private boolean updateSyncStatus = false;
	
	private WriteableResourceTableManager processor;
	
	
	public SyncChangesToServer() {
		this.TIME_TO_EXECUTE = System.currentTimeMillis();
		
	}

	@Override
	public void run(aCalService context) {
		this.acalService = context;
		ResourceManager rm = ResourceManager.getInstance(context);
		//send request
		rm.sendBlockingRequest(this);
		
		if ( this.updateSyncStatus ) {
			this.TIME_TO_EXECUTE = System.currentTimeMillis() + timeToWait;
			context.addWorkerJob(this);
		}

	}
	
	@Override
	public void process(WriteableResourceTableManager processor) throws ResourceProcessingException {
		this.processor = processor;
		this.requestor = new AcalRequestor();
		
		pendingChangesList = processor.getPendingResources();
		
		if ( pendingChangesList.isEmpty() ) {
			if (DEBUG) Log.println(Constants.LOGD, TAG, "No local changes to synchronise.");
			processed = true;
			return; // without rescheduling
		}
		else if ( !connectivityAvailable() ) {
			if (DEBUG) Log.println(Constants.LOGD,TAG, "No connectivity available to sync local changes.");
		}
		else {
			if (DEBUG)
				Log.println(Constants.LOGD,TAG, "Starting sync of local changes");
			
			pendingPos = -1;
			collectionsToSync = new HashSet<Integer>();
	
			try {
				ContentValues pendingChange;
				while( (pendingChange = getChangeToSync()) != null ) {
					syncOneChange(pendingChange);
				}

				if ( collectionsToSync.size() > 0 ) {
					for( Integer collectionId : collectionsToSync ) {
						acalService.addWorkerJob(new SyncCollectionContents(collectionId, true) );
					}
					
					// Fallback hack to really make sure the updated event actually gets displayed.
					// Push this out 30 seconds in the future to nag us to fix it properly!
					ServiceJob job = new SynchronisationJobs(SynchronisationJobs.CACHE_RESYNC);
					job.TIME_TO_EXECUTE = 3000L;
					acalService.addWorkerJob(job);
				}
			}
			catch( Exception e ) {
				Log.e(TAG,Log.getStackTraceString(e));
			}

		}

		updateSyncStatus = updateSyncStatus();
		processed = true;
	}

	
	private boolean connectivityAvailable() {
		try {
			ConnectivityManager conMan = (ConnectivityManager) processor.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo netInfo = conMan.getActiveNetworkInfo();
			if ( netInfo.isConnected() ) return true;
		}
		catch ( Exception e ) {
		}
		return false;
	}


	

	
	private ContentValues getChangeToSync() {
		if ( ++pendingPos < pendingChangesList.size() )
			return pendingChangesList.get(pendingPos);
		return null;
	}

	
	private void syncOneChange(ContentValues pending) throws SSLException {

		int collectionId = pending.getAsInteger(ResourceTableManager.PEND_COLLECTION_ID);
		ContentValues collectionData = processor.getCollectionRow(collectionId);
		if (collectionData == null) {
			invalidPendingChange(pending.getAsInteger(ResourceTableManager.PENDING_ID), 
						"Error getting collection data from DB - deleting invalid pending change record." );				
			return;
		}

		int serverId = collectionData.getAsInteger(DavCollections.SERVER_ID);
		ContentValues serverData = processor.getServerData(serverId);
		if (serverData == null) {
			invalidPendingChange(pending.getAsInteger(ResourceTableManager.PENDING_ID), 
						"Error getting server data from DB - deleting invalid pending change record." );				
			Log.e(TAG, "Deleting invalid collection Record.");
			processor.deleteInvalidCollectionRecord(collectionId);
			return;
		}
		requestor.applyFromServer(serverData);

		String collectionPath = collectionData.getAsString(DavCollections.COLLECTION_PATH);
		String newData = pending.getAsString(ResourceTableManager.NEW_DATA);
		String oldData = pending.getAsString(ResourceTableManager.OLD_DATA);
		
		
		DMQueryBuilder builder = processor.getNewQueryBuilder();
		//WriteActions action = WriteActions.UPDATE;
		builder.setAction(QUERY_ACTION.UPDATE);
		

		ContentValues resourceData = null;
		String latestDbData = null;
		String eTag = "*";

		BasicHeader eTagHeader = null;
		BasicHeader contentHeader = new BasicHeader("Content-type", getContentType(newData) );

		Integer resourceId = pending.getAsInteger(ResourceTableManager.PEND_RESOURCE_ID);
		Integer pendingId = pending.getAsInteger(ResourceTableManager.PENDING_ID);
		resourceData = processor.getResource(resourceId);
		String resourcePath = resourceData.getAsString(ResourceTableManager.RESOURCE_NAME);
		if ( resourcePath == null || resourceId < 1 ) {
			//action = WriteActions.INSERT;
			eTagHeader = new BasicHeader("If-None-Match", "*" );

			String contentExtension = getContentType(newData);
			if ( contentExtension.length() > 14 && contentExtension.substring(0,13).equals("text/calendar") )
				contentExtension = ".ics";
			else if ( contentExtension.substring(0,10).equals("text/vcard") )
				contentExtension = ".vcf";
			else
				contentExtension = ".txt";
			
			try {
				resourcePath = pending.getAsString(ResourceTableManager.UID) + contentExtension;
			}
			catch ( Exception e ) {
				if ( DEBUG )
					Log.println(Constants.LOGD,TAG,"Unable to get UID from resource");
				if ( Constants.LOG_VERBOSE )
					Log.println(Constants.LOGV,TAG,Log.getStackTraceString(e));
			};
			if ( resourcePath == null ) {
					resourcePath = UUID.randomUUID().toString() + contentExtension;
			}
			resourceData.put(ResourceTableManager.RESOURCE_NAME, resourcePath);
			resourceData.put(ResourceTableManager.COLLECTION_ID, collectionId);
		}
		else {
			if (resourceData == null) {
				invalidPendingChange(pendingId, 
							"Error getting resource data from DB - deleting invalid pending change record." );				
				return;
			}
			latestDbData = resourceData.getAsString(ResourceTableManager.RESOURCE_DATA);
			eTag = resourceData.getAsString(ResourceTableManager.ETAG);
			eTagHeader = new BasicHeader("If-Match", eTag );

			if ( newData == null ) {
				//action = WriteActions.DELETE;
				builder.setAction(QUERY_ACTION.DELETE);
			}
			else {
				if ( oldData != null && latestDbData != null && oldData.equals(latestDbData) ) {
					newData = mergeAsyncChanges( oldData, latestDbData, newData );
				}
			}
		}

		String path = collectionPath + resourcePath;

		Header[] headers = new Header[] { eTagHeader, contentHeader};
		
		if (DEBUG)	
			//Log.println(Constants.LOGD,TAG,	"Making "+action.toString()+" request to "+path);
			Log.println(Constants.LOGD,TAG,	"Making "+builder.getAction().toString()+" request to "+path);

		// If we made it this far we should do a sync on this collection ASAP after we're done
		collectionsToSync.add(collectionId);
		
		InputStream in = null;
		try {
			//if ( action == WriteActions.DELETE )
			if (builder.getAction() == QUERY_ACTION.DELETE)
				in = requestor.doRequest( "DELETE", path, headers, null);
			else
				in = requestor.doRequest( "PUT", path, headers, newData);
		}
		catch (ConnectionFailedException e) {
			Log.w(TAG,"HTTP Connection failed: "+e.getMessage());
			return;
		}
		catch (SendRequestFailedException e) {
			Log.w(TAG,"HTTP Request failed: "+e.getMessage());
			return;
		}
			
		int status = requestor.getStatusCode();
		switch (status) {
			case 201: // Status Created (normal for INSERT).
			case 204: // Status No Content (normal for DELETE).
			case 200: // Status OK. (normal for UPDATE)
				if (DEBUG) Log.println(Constants.LOGD,TAG, "Response "+status+" against "+path);
				resourceData.put(ResourceTableManager.RESOURCE_DATA, newData);
				resourceData.put(ResourceTableManager.NEEDS_SYNC, 1);
				resourceData.put(ResourceTableManager.ETAG, "unknown etag after PUT before sync");
/**
 * This is sadly unreliable.  Some servers will return an ETag and yet
 * still change the event server-side, without consequently changing the ETag
 * 
				for (Header hdr : requestor.getResponseHeaders()) {
					if (hdr.getName().equalsIgnoreCase("ETag")) {
						resourceData.put(DavResources.ETAG, hdr.getValue());
						resourceData.put(DavResources.NEEDS_SYNC, false);
						break;
					}
				}
*/
				
				if ( DEBUG ) Log.println(Constants.LOGD,TAG, 
						"Applying resource modification to local database");
				builder.setValues(resourceData);
				if (builder.getAction() != QUERY_ACTION.INSERT) {
					builder.setWhereClause(ResourceTableManager.RESOURCE_ID + " = ?");
					builder.setwhereArgs(new String[] { resourceData.getAsString(ResourceTableManager.RESOURCE_ID) });
				}
				
				DMAction action = builder.build();
				
				
				
				//ResourceModification changeUnit = new ResourceModification( action, resourceData, pendingId);
				//SQLiteDatabase db = processor.getWriteableDB();
				//boolean completed =false;
				try {
					// Attempting to keep our transaction as narrow as possible.
					//db.beginTransaction();
					//changeUnit.commit(db, processor.getTableName(this));
					//db.setTransactionSuccessful();
					//completed = true;
					
					processor.syncToServer(action, resourceData.getAsLong(ResourceTableManager.RESOURCE_ID), pendingId);
					
				}
				catch (Exception e) {
					Log.w(TAG, action.toString()+": Exception applying resource modification: " + e.getMessage());
					Log.println(Constants.LOGD,TAG, Log.getStackTraceString(e));
				}
				finally {
					
					//if ( completed ) changeUnit.notifyChange();

				}
				break;

			case 412: // Server won't accept it
			case 403: // Server won't accept it
			case 405: // Server won't accept it - Method not allowed
				Log.w(TAG, builder.getAction().toString()+": Status " + status + " on request for " + path + " giving up on change.");
				processor.deletePendingChange(pendingId);
				break;

			default: // Unknown code
				Log.w(TAG, builder.getAction().toString()+": Status " + status + " on request for " + path);
				if ( in != null ) {
					// Possibly we got an error message...
					byte[] buffer = new byte[1024];
					try {
						in.read(buffer, 0, 1020);
						System.setProperty("file.encoding","UTF-8");
						String response = new String(buffer);
						Log.i(TAG,"Server response was: "+response);
					}
					catch (IOException e) {
					}
				}
		}
	}


	private String mergeAsyncChanges(String oldData, String latestDbData, String newData) {
		/**
		 * @todo Around here is where we should handle the case where latestDbData != oldData. We
		 * need to parse out both objects and work out what the differences are between oldData
		 * and newData, and see if we can apply them to latestDbData without them overwriting
		 * differences between oldData and latestDbData... 
		 */
		return newData;
	}


	private String getContentType(String fromData) {
		if ( fromData == null ) return "text/plain";

		if ( fromData.substring(6, 15).equalsIgnoreCase("vcalendar") ) {
			return "text/calendar; charset=\"utf-8\"";
		}
		else if ( fromData.substring(6, 11).equalsIgnoreCase("vcard") ) {
			return "text/vcard; charset=\"utf-8\"";
		}
		 return "text/plain";
	}

	
	private void invalidPendingChange(int pendingId, String msg) {
		Log.e(TAG, msg );
		processor.deletePendingChange(pendingId);
	}

	
	private boolean updateSyncStatus() {
		return true;
	}

	

/*	
	final private static Header[] proppatchHeaders = new Header[] {
		new BasicHeader("Content-Type","text/xml; encoding=UTF-8")
	};

	final static String baseProppatch = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"+
		"<propertyupdate xmlns=\""+Constants.NS_DAV+"\"\n"+
		"    xmlns:ACAL=\""+Constants.NS_ACAL+"\">\n"+
		"<set>\n"+
		"  <prop>\n"+
		"   <ACAL:collection-colour>%s</ACAL:collection-colour>\n"+
		"  </prop>\n"+
		" </set>\n"+
		"</propertyupdate>\n";

	
	private void updateCollectionProperties( ContentValues collectionData ) {
		String proppatchRequest = String.format(baseProppatch,
					collectionData.getAsString(DavCollections.COLOUR)
				);

		try {
			ContentValues serverData = processor.getServerData(collectionData.getAsInteger(DavCollections.SERVER_ID));
			requestor.applyFromServer(serverData);
			requestor.doRequest("PROPPATCH", collectionData.getAsString(DavCollections.COLLECTION_PATH),
						proppatchHeaders, proppatchRequest);

			collectionData.put(DavCollections.SYNC_METADATA, 0);
			processor.updateCollection(collectionData.getAsLong(DavCollections._ID),collectionData);
		}
		catch (Exception e) {
			Log.e(TAG,"Error with proppatch to "+requestor.fullUrl());
			Log.println(Constants.LOGD,TAG,Log.getStackTraceString(e));
		}

	}

*/

	@Override
	public String getDescription() {
		return "Syncing local changes back to the server.";
	}

	@Override
	public boolean isProcessed() {
		return this.processed;
	}

}
