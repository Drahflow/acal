package com.morphoss.acal.database.resourcesmanager.requests;

import java.util.ArrayList;

import android.content.ContentValues;
import android.util.Log;

import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.resourcesmanager.ResourceProcessingException;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ReadOnlyResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.ResourceManager.ResourceTableManager;
import com.morphoss.acal.database.resourcesmanager.requesttypes.ReadOnlyResourceRequestWithResponse;
import com.morphoss.acal.dataservice.CalendarInstance;
import com.morphoss.acal.dataservice.Resource;

public class RRRequestInstance extends ReadOnlyResourceRequestWithResponse<CalendarInstance> {

	public static final String TAG = "aCal RRRequestInstance";
	
	private long rid;
	private String rrid;
	private boolean processed = false;
	
	public RRRequestInstance(ResourceResponseListener<CalendarInstance> callBack, CacheObject co) {
		super(callBack);
		this.rid = co.getResourceId();
		this.rrid = co.getRecurrenceId();
	}
	
	public RRRequestInstance(ResourceResponseListener<CalendarInstance> callBack, long resourceId, String recurrenceId) {
		super(callBack);
		this.rid = resourceId;
		this.rrid = recurrenceId;
	}
	

	@Override
	public void process(ReadOnlyResourceTableManager processor) throws ResourceProcessingException {
		ArrayList<ContentValues> cv = processor.query(null, ResourceTableManager.RESOURCE_ID+" = ?", new String[]{rid+""}, null,null,null);
		ArrayList<ContentValues> pcv = processor.getPendingResources();
		try {
			
			//check pending first
			for (ContentValues val : pcv) {
				if (val.getAsLong(ResourceTableManager.PEND_RESOURCE_ID) == this.rid) {
					String blob = val.getAsString(ResourceTableManager.NEW_DATA);
					if (blob == null || blob.equals("")) {
						//this resource has been deleted
						throw new Exception("Resource deleted.");
					} else {
						CalendarInstance ci = CalendarInstance.fromPendingRowAndRRID(val,rrid);
						this.postResponse(new RRRequestInstanceResponse<CalendarInstance>(ci));
						this.processed = true;
					}
				}
			}
			if (!processed) {
				Resource res = Resource.fromContentValues(cv.get(0));
				CalendarInstance ci = CalendarInstance.fromResourceAndRRId(res, rrid);
				this.postResponse(new RRRequestInstanceResponse<CalendarInstance>(ci));
				this.processed = true;
			}
		}
		catch ( Exception e ) {
			Log.e(TAG, e.getMessage() + Log.getStackTraceString(e));
			this.postResponse(new RRRequestInstanceResponse<CalendarInstance>(e));
			this.processed = true;
		}
		
	}

	public class RRRequestInstanceResponse<CalendarIntstance> extends ResourceResponse<CalendarInstance> {

		private CalendarInstance result = null;
		public RRRequestInstanceResponse(CalendarInstance ci) { this.result = ci; }
		public RRRequestInstanceResponse(Exception e) { super(e); }
		
		@Override
		public CalendarInstance result() {
			return this.result;
		}
		
	}

	@Override
	public boolean isProcessed() {
		return this.processed;
	}
}
