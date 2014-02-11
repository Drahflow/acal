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

package com.morphoss.acal.activity;

import java.util.Map;
import java.util.LinkedHashMap;

import android.app.ListActivity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.morphoss.acal.ServiceManager;
import com.morphoss.acal.database.alarmmanager.AlarmQueueManager;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.requests.CRClearCacheRequest;
import com.morphoss.acal.service.SyncChangesToServer;
import com.morphoss.acal.service.WorkerClass;

public class DebugSettings extends ListActivity {
	public static final String TAG = "aCal Debug Settings";

	abstract private class ExceptionRunnable {
		abstract public void run() throws Throwable;
	};

	/** A list of setting that can be configured in DEBUG mode */
	private final Map<String, ExceptionRunnable> TASKS = new LinkedHashMap<String, ExceptionRunnable>() {
		public static final long serialVersionUID = 1;
	{
		put("Save Database", new ExceptionRunnable() { public void run() throws RemoteException {
			DebugSettings.this.serviceManager.getServiceRequest().saveDatabase();
		}});
		put("Revert Database", new ExceptionRunnable() { public void run() throws RemoteException {
			DebugSettings.this.serviceManager.getServiceRequest().revertDatabase();
		}});
		put("Full System Sync", new ExceptionRunnable() { public void run() throws RemoteException {
			DebugSettings.this.serviceManager.getServiceRequest().fullResync();
		}});
		put("Home Set Discovery", new ExceptionRunnable() { public void run() throws RemoteException {
			DebugSettings.this.serviceManager.getServiceRequest().discoverHomeSets();
		}});
		put("Update Home DavCollections", new ExceptionRunnable() { public void run() throws RemoteException {
			DebugSettings.this.serviceManager.getServiceRequest().updateCollectionsFromHomeSets();
		}});
		put("Clear Cache", new ExceptionRunnable() { public void run() {
			CacheManager.getInstance(DebugSettings.this).sendRequest(new CRClearCacheRequest());
		}});
		put("Sync local changes to server", new ExceptionRunnable() { public void run() {
			WorkerClass.getExistingInstance().addJobAndWake(new SyncChangesToServer());
		}});
		put("Log current alarm queue", new ExceptionRunnable() { public void run() {
			AlarmQueueManager.logCurrentAlarms(DebugSettings.this);
		}});
		put("Rebuild alarm queue", new ExceptionRunnable() { public void run() {
			AlarmQueueManager.logCurrentAlarms(DebugSettings.this);
			AlarmQueueManager.rebuildAlarmQueue(DebugSettings.this);
			AlarmQueueManager.logCurrentAlarms(DebugSettings.this);
		}});
	}};
	private final String[] TASK_NAMES = TASKS.keySet().toArray(new String[0]);

	private ServiceManager serviceManager;

	/**
	 * <p>Creates a list of debug settings.</p>
	 *
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, TASK_NAMES));
		ListView lv = getListView();
		lv.setTextFilterEnabled(true);
		lv.setOnItemClickListener(new SettingsListClickListener());
	}

	@Override
	public void onPause() {
		super.onPause();
		if (this.serviceManager != null) this.serviceManager.close();
	}

	@Override
	public void onResume() {
		super.onResume();
		this.serviceManager = new ServiceManager(this);
	}

	/**
	 * Click listener for Settings List.
	 */
	private class SettingsListClickListener implements OnItemClickListener {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			try {
				TASKS.get(TASK_NAMES[position]).run();
			} catch (Throwable t) {
				Log.e(TAG, "Unable to execute " + TASK_NAMES[position] + ": " + t.getMessage());
				Toast.makeText(DebugSettings.this, "Request failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
			}
		}
	}
}
