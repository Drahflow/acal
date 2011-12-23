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

import java.util.ArrayList;
import java.util.TimeZone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.morphoss.acal.AcalTheme;
import com.morphoss.acal.Constants;
import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalDateTimeFormatter;
import com.morphoss.acal.acaltime.AcalDuration;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedEvent;
import com.morphoss.acal.database.resourcesmanager.ResourceChangedListener;
import com.morphoss.acal.database.resourcesmanager.ResourceManager;
import com.morphoss.acal.database.resourcesmanager.ResourceResponse;
import com.morphoss.acal.database.resourcesmanager.ResourceResponseListener;
import com.morphoss.acal.database.resourcesmanager.requests.RRRequestInstance;
import com.morphoss.acal.dataservice.Collection;
import com.morphoss.acal.dataservice.EventInstance;
import com.morphoss.acal.dataservice.EventInstance.BadlyConstructedEventException;
import com.morphoss.acal.dataservice.EventInstance.EVENT_BUILDER;
import com.morphoss.acal.davacal.AcalAlarm;
import com.morphoss.acal.davacal.PropertyName;
import com.morphoss.acal.davacal.VComponent;
import com.morphoss.acal.davacal.AcalAlarm.ActionType;
import com.morphoss.acal.providers.DavCollections;
import com.morphoss.acal.service.aCalService;
import com.morphoss.acal.widget.AlarmDialog;
import com.morphoss.acal.widget.DateTimeDialog;
import com.morphoss.acal.widget.DateTimeSetListener;

public class EventEdit extends AcalActivity implements  OnClickListener, OnCheckedChangeListener, ResourceChangedListener, ResourceResponseListener {

	public static final String TAG = "aCal EventEdit";
	public static final int APPLY = 0;
	public static final int CANCEL = 1;

	// keys for the data we return.
	public static final String			resultSimpleAcalEvent		= "newSimpleEvent";
	public static final String			resultAcalEvent					= "newAcalEvent";
	public static final String			resultCollectionId			= "newCollectionId";
	
	public static final String RESOURCE_ID_KEY = "resourceId";
	public static final String RECCURENCE_ID_KEY = "reccurenceId";
	public static final String ACTION_KEY = "action";
	public static final String NEW_EVENT_DATE_TIME_KEY = "datetime";
	
	public static final int ACTION_EDIT = 1;
	public static final int ACTION_CREATE = 2;
	public static final int ACTION_COPY = 3;
	public static final int ACTION_DELETE = 4;
	
	public static final int INSTANCES_SINGLE = 0;
	public static final int INSTANCES_ALL = 1;
	public static final int INSTANCES_THIS_FUTURE = 2;
	
	
	private EventInstance event;
	private static final int START_DATE_DIALOG = 0;
	private static final int END_DATE_DIALOG = 2;
	private static final int SELECT_COLLECTION_DIALOG = 4;
	private static final int ADD_ALARM_DIALOG = 5;
	private static final int SET_REPEAT_RULE_DIALOG = 6;
	private static final int WHICH_EVENT_DIALOG = 7;
	private static final int LOADING_EVENT_DIALOG = 8;
	private static final int SAVING_DIALOG = 9;

	boolean prefer24hourFormat = false;
	
	private String[] repeatRules;
	private String[] repeatRulesValues;
	private String[] eventChangeRanges; // See strings.xml R.array.EventChangeAffecting
		
	private String[] alarmRelativeTimeStrings;
	// Must match R.array.RelativeAlarmTimes (strings.xml)
	public static final AcalDuration[] alarmValues = new AcalDuration[] {
		new AcalDuration(),
		new AcalDuration("-PT10M"),
		new AcalDuration("-PT15M"),
		new AcalDuration("-PT30M"),
		new AcalDuration("-PT1H"),
		new AcalDuration("-PT2H"),
		//** Custom **//
	};

	//GUI Components
	private Button btnStartDate;
	private Button btnEndDate;
	private LinearLayout sidebar;
	private LinearLayout sidebarBottom;
	private TextView eventName;
	private TextView locationView;
	private TextView notesView;
	private TableLayout alarmsList;
	private Button repeatsView;
	private Button alarmsView;
	private LinearLayout llSelectCollection;
	private Button btnSelectCollection;
	
	//Active collections for create mode
	private ArrayList<Collection> activeCollections;
	private String[] collectionsArray;

	private ArrayList<AcalAlarm> alarmList;
	
	private ResourceManager resourceManager;
	
	private int action;
	private int instances = -1;
	
	
	
	private static final int REFRESH = 0;
	private static final int FAIL = 1;
	private static final int CONFLICT = 2;
	private static final int SHOW_LOADING = 3;
	private static final int GIVE_UP = 4;
	private static final int SAVE_RESULT = 5;
	private static final int SAVE_FAILED = 6;
	private static final int SHOW_SAVING = 7;

	private boolean isSaving = false;
	private boolean isLoading = false;

	
	private Dialog loadingDialog = null;
	private Dialog savingDialog = null;
	
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			
			switch (msg.what) {
			case REFRESH: 
				isLoading = false;
				if (loadingDialog != null) {
					loadingDialog.dismiss();
					loadingDialog = null;
				}
				updateLayout();
				break;
			
			case  CONFLICT: 
				Toast.makeText(EventEdit.this, "The resource you are editing has been changed or deleted on the server.", 5).show();
				break;
			
			case SHOW_LOADING: 
					isLoading = true;
					if (event == null) showDialog(LOADING_EVENT_DIALOG);
					break;
					
			case FAIL:
				if (isLoading) {
					Toast.makeText(EventEdit.this, "Error loading data.", 5).show();
					isLoading = false;
				} else if (isSaving) {
					isSaving = false;
					if (savingDialog != null) savingDialog.dismiss();
					Toast.makeText(EventEdit.this, "Something went wrong trying to save data.", Toast.LENGTH_LONG).show();
					setResult(Activity.RESULT_CANCELED, null);
					finish();
					break;
				}
				finish();
				break;
				
			case GIVE_UP:
				isLoading = false;
				if (loadingDialog != null) {
					loadingDialog.dismiss();
					Toast.makeText(EventEdit.this, "Error loading event data.", Toast.LENGTH_LONG).show();
					finish();
				}
				break;
			case SHOW_SAVING: 
				isSaving = true;
				showDialog(SAVING_DIALOG);
				break;
				
			case SAVE_RESULT:
				//dismiss dialog
				isSaving = false;
				if (savingDialog != null) savingDialog.dismiss();
				long res = (Long)msg.obj;
				if (res >= 0) {
					Intent ret = new Intent();
					Bundle b = new Bundle();
					b.putLong(EventView.RESOURCE_ID_KEY, (Long)msg.obj);
					b.putLong(EventView.DTSTART_KEY, event.getStart().getMillis());
					ret.putExtras(b);			
					setResult(RESULT_OK, ret);
					finish();
					
				} else {
					Toast.makeText(EventEdit.this, "Error saving event data.", Toast.LENGTH_LONG).show();
				}
				break;
			case SAVE_FAILED:
				isSaving = false;
				if (savingDialog != null) savingDialog.dismiss();
				Toast.makeText(EventEdit.this, "Something went wrong trying to save data.", Toast.LENGTH_LONG).show();
				setResult(Activity.RESULT_CANCELED, null);
				finish();
				break;
			}
			
		}
	};
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.event_edit);

		//Ensure service is actually running
		startService(new Intent(this, aCalService.class));

		// Get time display preference
		prefer24hourFormat = prefs.getBoolean(getString(R.string.prefTwelveTwentyfour), DateFormat.is24HourFormat(this));

		alarmRelativeTimeStrings = getResources().getStringArray(R.array.RelativeAlarmTimes);
		eventChangeRanges = getResources().getStringArray(R.array.EventChangeAffecting);
		
		resourceManager = ResourceManager.getInstance(this,this);

		//Get collection data
		activeCollections = new ArrayList<Collection>();
		for (ContentValues cv : DavCollections.getCollections( getContentResolver(), DavCollections.INCLUDE_EVENTS ))
			activeCollections.add(Collection.getInstance(cv.getAsLong(DavCollections._ID),this));

		if (activeCollections.isEmpty()) {
			Toast.makeText(this, getString(R.string.errorMustHaveActiveCalendar), Toast.LENGTH_LONG);
			this.finish();	// can't work if no active collections
			return;
		}
		this.collectionsArray = new String[activeCollections.size()];
		int count = 0;
		for (Collection col : activeCollections) {
			collectionsArray[count++] = col.getDisplayName();
		}
		getEventAction();
		this.loadLayout();
	}


	@SuppressWarnings("unchecked")
	private void getEventAction() {
		Bundle b = this.getIntent().getExtras();
		if ( b.containsKey(ACTION_KEY) ) {
			action = b.getInt(ACTION_KEY);
			switch (action) {
				case ACTION_EDIT:
					//We need to load the event - while we are waiting we can show a dialog
					if (!b.containsKey(RESOURCE_ID_KEY) || !b.containsKey(RECCURENCE_ID_KEY)) {
						//invalid data supplied
						this.finish();
						return;
					}
					long rid = b.getLong(RESOURCE_ID_KEY);
					String rrid = b.getString(RECCURENCE_ID_KEY);
					resourceManager.sendRequest(new RRRequestInstance(this,rid, rrid));
					mHandler.sendMessageDelayed(mHandler.obtainMessage(SHOW_LOADING), 50);
					mHandler.sendMessageDelayed(mHandler.obtainMessage(GIVE_UP), 10000);
					break;
				case ACTION_CREATE:
					AcalDateTime start;
					if (b.containsKey(NEW_EVENT_DATE_TIME_KEY))
						start = b.getParcelable(NEW_EVENT_DATE_TIME_KEY);
					else {
						//start at beggining of next hour
						 start = new AcalDateTime()
						 	.setTimeZone(TimeZone.getDefault().getID())
						 	.setHour(new AcalDateTime().getHour())
							.setSecond(0)
							.setMinute(0)
							.addSeconds(AcalDateTime.SECONDS_IN_HOUR);
					}
					
					AcalDuration eventDuration = ( start.isDate() ) ?  new AcalDuration("PT1D") : new AcalDuration("PT1H");
					AcalDuration alarmDuration = ( start.isDate() ) ?  new AcalDuration("-PT12H") : new AcalDuration("-PT15M"); 
					 
					AcalAlarm defaultAlarm = new AcalAlarm( AcalAlarm.RelateWith.START, "", alarmDuration,
								ActionType.AUDIO, start, AcalDateTime.addDuration(start, eventDuration));
					
					long collectionId = activeCollections.get(0).getCollectionId();
					Integer preferredCollectionId = Integer.parseInt(prefs.getString(getString(R.string.DefaultCollection_PrefKey), "-1"));
					if ( preferredCollectionId != -1 ) {
						for( Collection aCollection : activeCollections ) {
							if ( preferredCollectionId == aCollection.getCollectionId()) {
								collectionId = preferredCollectionId;
								break;
							}
						}
					}
					
					try {
						//build default event
						this.event = new EVENT_BUILDER()
							.setStart(start)
							.setDuration(eventDuration)
							.setSummary(getString(R.string.NewEventTitle))
							.setCollection(collectionId)
							.addAlarm(defaultAlarm)
							.build();
					} catch (BadlyConstructedEventException e) {
						Log.e(TAG, "Error creating a new event: "+e+Log.getStackTraceString(e));
						this.finish();
						return;
					}
				break;
			}
		} else {
			//no action! bye bye
			this.finish(); return;
		}
	}

	
	private void setSelectedCollection(String name) {
		for (Collection col : activeCollections) {
			if (col.getDisplayName().equals(name)) {
				this.event.setCollectionId(col.getCollectionId());
				break;
			}
		}
		this.updateLayout();
	}

	private void loadLayout() {
		//Event Colour
		sidebar = (LinearLayout)this.findViewById(R.id.EventEditColourBar);
		sidebarBottom = (LinearLayout)this.findViewById(R.id.EventEditColourBarBottom);

		//Set up Save/Cancel buttons
		this.setupButton(R.id.event_apply_button, APPLY);
		this.setupButton(R.id.event_cancel_button, CANCEL);

		//Title
		this.eventName = (TextView) this.findViewById(R.id.EventName);
		if ( action == ACTION_CREATE ) {
			eventName.setSelectAllOnFocus(true);
		}

		//Collection
		llSelectCollection = (LinearLayout) this.findViewById(R.id.EventEditCollectionLayout);
		btnSelectCollection = (Button) this.findViewById(R.id.EventEditCollectionButton);
		if (activeCollections.size() < 2) {
			llSelectCollection.setVisibility(View.GONE);
		}
		else {
			//set up click listener for collection dialog
			setListen(this.btnSelectCollection, SELECT_COLLECTION_DIALOG);
		}
		
		
		//date/time fields
		btnStartDate = (Button) this.findViewById(R.id.EventFromDateTime);
		btnEndDate = (Button) this.findViewById(R.id.EventUntilDate);

		locationView = (TextView) this.findViewById(R.id.EventLocationContent);
		

		notesView = (TextView) this.findViewById(R.id.EventNotesContent);
		

		alarmsList = (TableLayout) this.findViewById(R.id.alarms_list_table);
		alarmsView = (Button) this.findViewById(R.id.EventAlarmsButton);
		
		repeatsView = (Button) this.findViewById(R.id.EventRepeatsContent);	
		
		//Button listeners
		setListen(btnStartDate,START_DATE_DIALOG);
		setListen(btnEndDate,END_DATE_DIALOG);
		setListen(alarmsView,ADD_ALARM_DIALOG);
		setListen(repeatsView,SET_REPEAT_RULE_DIALOG);

		AcalTheme.setContainerFromTheme(btnStartDate, AcalTheme.BUTTON );
		AcalTheme.setContainerFromTheme(btnEndDate, AcalTheme.BUTTON );
		AcalTheme.setContainerFromTheme(alarmsView, AcalTheme.BUTTON );
		AcalTheme.setContainerFromTheme(repeatsView, AcalTheme.BUTTON );
		
		updateLayout();
	}
	

	
	private void updateLayout() {
		//it is possible that event is not yet loaded. If this is the case we will abort
		if (this.event == null) return;
		
		this.locationView.setText(event.getLocation());
		this.eventName.setText(event.getSummary());
		this.notesView.setText(event.getDescription());
		AcalDateTime start = event.getStart();
		AcalDateTime end = event.getEnd();
		end.setAsDate(start.isDate());
		Collection collection = Collection.getInstance(event.getCollectionId(),this);
		Integer colour = collection.getColour();
		sidebar.setBackgroundColor(colour);
		sidebarBottom.setBackgroundColor(colour);
		eventName.setTextColor(colour);
		if (!activeCollections.isEmpty()) {
			btnSelectCollection.setText(collection.getDisplayName());
			AcalTheme.setContainerColour(btnSelectCollection, colour);
			btnSelectCollection.setTextColor(AcalTheme.pickForegroundForBackground(colour));
		}
		Log.d(TAG,"Start date is "+(start.isFloating()?"":"not ")+"floating in updateLayout...");

		btnStartDate.setText(AcalDateTimeFormatter.fmtFull(start, prefer24hourFormat));
		if ( end.isDate() ) {
			// People expect an event starting on the 13th and ending on the 14th to be for
			// two days.  For iCalendar it is one day, so we display the end date to be
			// one day earlier than the actual setting, if we're viewing 
			end.addDays(-1);
		}
		btnEndDate.setText(AcalDateTimeFormatter.fmtFull(end, prefer24hourFormat));
		
		//Display Alarms
		alarmList = event.getAlarms();
		this.alarmsList.removeAllViews();
		for (AcalAlarm alarm : alarmList) {
			this.alarmsList.addView(this.getAlarmItem(alarm, alarmsList));
		}
		
		//set repeat options
		int dow = start.getWeekDay();
		int weekNum = start.getMonthWeek();
		String dowStr = "";
		String dowLongString = "";
		String everyDowString = "";
		switch (dow) {
			case 0:
				dowStr="MO";
				dowLongString = getString(R.string.Monday);
				everyDowString = getString(R.string.EveryMonday);
				break;
			case 1:
				dowStr="TU";
				dowLongString = getString(R.string.Tuesday);
				everyDowString = getString(R.string.EveryTuesday);
				break;
			case 2:
				dowStr="WE";
				dowLongString = getString(R.string.Wednesday);
				everyDowString = getString(R.string.EveryWednesday);
				break;
			case 3:
				dowStr="TH";
				dowLongString = getString(R.string.Thursday);
				everyDowString = getString(R.string.EveryThursday);
				break;
			case 4:
				dowStr="FR";
				dowLongString = getString(R.string.Friday); 
				everyDowString = getString(R.string.EveryFriday);
				break;
			case 5:
				dowStr="SA";
				dowLongString = getString(R.string.Saturday); 
				everyDowString = getString(R.string.EverySaturday);
				break;
			case 6:
				dowStr="SU";
				dowLongString = getString(R.string.Sunday); 	
				everyDowString = getString(R.string.EverySunday);
				break;
		}
		String dailyRepeatName = getString(R.string.EveryWeekday);
		String dailyRepeatRule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=260";
		if (start.get(AcalDateTime.DAY_OF_WEEK) == AcalDateTime.SATURDAY || start.get(AcalDateTime.DAY_OF_WEEK) == AcalDateTime.SUNDAY) {
			dailyRepeatName = getString(R.string.EveryWeekend);
			dailyRepeatRule = "FREQ=WEEKLY;BYDAY=SA,SU;COUNT=104";
		}

		this.repeatRules = new String[] {
					getString(R.string.OnlyOnce),
					getString(R.string.EveryDay),
					dailyRepeatName,
					everyDowString,
					String.format(this.getString(R.string.EveryNthOfTheMonth),
								start.getMonthDay()+AcalDateTime.getSuffix(start.getMonthDay())),
					String.format(this.getString(R.string.EveryMonthOnTheNthSomeday),
								weekNum+AcalDateTime.getSuffix(weekNum)+" "+dowLongString),
					getString(R.string.EveryYear)
		};
		this.repeatRulesValues = new String[] {
				"FREQ=DAILY;COUNT=400",
				dailyRepeatRule,
				"FREQ=WEEKLY;BYDAY="+dowStr,
				"FREQ=MONTHLY;COUNT=60",
				"FREQ=MONTHLY;COUNT=60;BYDAY="+weekNum+dowStr,
				"FREQ=YEARLY"
		};
		String repeatRuleString = event.getRRule();
		if (repeatRuleString == null) repeatRuleString = "";
		AcalRepeatRule RRule;
		try {
			RRule = new AcalRepeatRule(start, repeatRuleString); 
		}
		catch( IllegalArgumentException  e ) {
			Log.i(TAG,"Illegal repeat rule: '"+repeatRuleString+"'");
			RRule = new AcalRepeatRule(start, null ); 
		}
		String rr = RRule.repeatRule.toPrettyString(this);
		if (rr == null || rr.equals("")) rr = getString(R.string.OnlyOnce);
		repeatsView.setText(rr);
	}

	
	private void setListen(Button b, final int dialog) {
		b.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				showDialog(dialog);

			}
		});
	}

	private void setupButton(int id, int val) {
		Button button = (Button) this.findViewById(id);
		button.setOnClickListener(this);
		button.setTag(val);
		AcalTheme.setContainerFromTheme(button, AcalTheme.BUTTON);
		if ( val == APPLY )
			button.setText((action == ACTION_EDIT ? getString(R.string.Save) : getString(R.string.Add)));
	}
	
	@Override
	public void onClick(View arg0) {
		int button = (int)((Integer)arg0.getTag());
		switch ( button ) {
			case APPLY:
				applyChanges();
				break;
			case CANCEL:
				finish();
		}
	}
	
	@Override
	public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
		this.updateLayout();
	}

	
	public void applyChanges() {
		//check if text fields changed
		//summary
		String oldSum = event.getSummary();
		String newSum = this.eventName.getText().toString() ;
		String oldLoc = event.getLocation();
		String newLoc = this.locationView.getText().toString();
		String oldDesc = event.getDescription();
		String newDesc = this.notesView.getText().toString() ;
		
		if (!oldSum.equals(newSum)) event.setSummary(newSum);
		if (!oldLoc.equals(newLoc)) event.setLocation(newLoc);
		if (!oldDesc.equals(newDesc)) event.setDescription(newDesc);
		
		AcalDateTime start = event.getStart();
		AcalDuration duration = event.getDuration();

		// Ensure end is not before start
		if ( duration.getDays() < 0 || duration.getTimeMillis() < 0 ) {
			start = event.getStart();
			AcalDateTime end = AcalDateTime.addDuration(start, duration);
			while( end.before(start) ) end.addDays(1);
			event.setEndDate(end);
		}
		
		if (action == ACTION_EDIT && instances <0)
			this.showDialog(WHICH_EVENT_DIALOG);
		else if ( !this.saveChanges() ){
				Toast.makeText(this, "Save failed: retrying!", Toast.LENGTH_LONG).show();
				this.saveChanges();
		} else {
			Toast.makeText(this, "Event(s) Saved.", Toast.LENGTH_LONG).show();
		}
	}

	@SuppressWarnings("unchecked")
	private boolean saveChanges() {
		
		
		try {
			if ( Constants.LOG_DEBUG ) Log.d(TAG,"saveChanges: dtstart = "+event.getStart().toPropertyString(PropertyName.DTSTART));
			//display savingdialog
			
			
			
			ResourceManager.getInstance(this).sendRequest(new RREventEditedRequest(this, event, action, instances));
			
			//set message for 10 seconds to fail.
			mHandler.sendEmptyMessageDelayed(SAVE_FAILED, 100000);
			
			//showDialog(SAVING_DIALOG);
			mHandler.sendEmptyMessageDelayed(SHOW_SAVING,50);
			
			
		}
		catch (Exception e) {
			if ( e.getMessage() != null ) Log.d(TAG,e.getMessage());
			if (Constants.LOG_DEBUG)Log.d(TAG,Log.getStackTraceString(e));
			Toast.makeText(this, getString(R.string.ErrorSavingEvent), Toast.LENGTH_LONG).show();
			return false;
		}
		
		return true;
	}
	
	//Dialogs
	protected Dialog onCreateDialog(int id) { 
		switch (id) {
			case SAVING_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Saving...");
				builder.setCancelable(false);
				savingDialog = builder.create();
				return savingDialog;
		}
		if (event != null) {
			AcalDateTime start = event.getStart();
			AcalDateTime end = event.getEnd();
			switch (id) {
				case START_DATE_DIALOG:
					return new DateTimeDialog( this, start.clone(), prefer24hourFormat, true, true,
							new DateTimeSetListener() { public void onDateTimeSet(AcalDateTime newDateTime) {
								AcalDateTime oldStart = event.getStart();
								AcalDuration delta = oldStart.getDurationTo(newDateTime);
								AcalDateTime newEnd = event.getEnd();
								String endTzId = newEnd.getTimeZoneId();
								newEnd.addDuration(delta);
								if ( oldStart.isDate() != newDateTime.isDate() ) {
									newEnd.setAsDate(newDateTime.isDate() );
								}
								String oldTzId = oldStart.getTimeZoneId();
								String newTzId = newDateTime.getTimeZoneId();
								if ( oldTzId == null && newTzId != null ) {
									if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG,
											"The timezone changed from floating to "+newTzId+", EndTzId was "+endTzId);
									if ( endTzId == null ) newEnd.shiftTimeZone(newTzId);
								}
								else if ( oldTzId != null && !oldTzId.equals(newTzId) ) {
									if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG,
											"The timezone changed from "+oldTzId+" to "+newTzId+", EndTzId was "+endTzId);
									if ( oldTzId.equals(endTzId) ) newEnd.shiftTimeZone(newTzId);
								}
								else {
									if ( Constants.LOG_DEBUG ) Log.println(Constants.LOGD, TAG,"The timezone did not change from "+oldTzId+" to "+newTzId+", EndTzId was "+endTzId);
								}
								event.setDates(newDateTime, newEnd);
								updateLayout();
						}
				});

			case END_DATE_DIALOG:
				end.setAsDate(start.isDate());
				if ( end.before(start) ) end = start.clone();
				if ( end.isDate() ) {
					// People expect an event starting on the 13th and ending on the 14th to be for
					// two days.  For iCalendar it is one day, so we display the end date to be
					// one day earlier than the actual setting, if we're viewing 
					end.addDays(-1);
				}
				return new DateTimeDialog( this, end, prefer24hourFormat, false, true,
						new DateTimeSetListener() { public void onDateTimeSet(AcalDateTime newDateTime) {
							if ( newDateTime.isDate() ) newDateTime.addDays(1);
							event.setEndDate(newDateTime);
							updateLayout();
						}
				});

			case SELECT_COLLECTION_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.ChooseACollection));
				builder.setItems(this.collectionsArray, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						setSelectedCollection(collectionsArray[item]);
					}
				});
				return builder.create();

			case ADD_ALARM_DIALOG:
				builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.ChooseAlarmTime));
				builder.setItems(alarmRelativeTimeStrings, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						if ( item < 0 || item > alarmValues.length ) return;
						if ( item == alarmValues.length ) {
							customAlarmDialog();
						}
						else {
							alarmList.add(new AcalAlarm(AcalAlarm.RelateWith.START, event.getDescription(),
									alarmValues[item], ActionType.AUDIO, event.getStart(), AcalDateTime.addDuration(
											event.getStart(), alarmValues[item])));
							event.setAlarms(alarmList);
							updateLayout();
						}
					}
				});
				return builder.create();

			case WHICH_EVENT_DIALOG:
				builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.ChooseInstancesToChange));
				builder.setItems(eventChangeRanges, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						switch ( item ) {
							case 0:
								instances = INSTANCES_SINGLE;
								//dialog.dismiss();
								saveChanges();
								return;
							case 1:
								instances = INSTANCES_ALL;
								//dialog.dismiss();
								saveChanges();
								return;
							case 2:
								instances = INSTANCES_THIS_FUTURE;
								//dialog.dismiss();
								saveChanges();
								return;
						}
					}
				});
				return builder.create();

			case SET_REPEAT_RULE_DIALOG:
				builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.ChooseRepeatFrequency));
				builder.setItems(this.repeatRules, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						String newRule = "";
						if ( item != 0 ) {
							item--;
							newRule = repeatRulesValues[item];
						}
						if ( action == ACTION_EDIT && !newRule.equals(EventEdit.this.event.getRRule())) {
							instances = INSTANCES_ALL;
						}
						event.setRepeatRule(newRule);
						updateLayout();

					}
				});
				return builder.create();
			}
		} else {
			switch (id) {
			case LOADING_EVENT_DIALOG:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Loading...");
				builder.setCancelable(false);
				loadingDialog = builder.create();
				return loadingDialog;
			}
		}
		return null;
	}

	protected void customAlarmDialog() {

		AlarmDialog.AlarmSetListener customAlarmListener = new AlarmDialog.AlarmSetListener() {

			@Override
			public void onAlarmSet(AcalAlarm alarmValue) {
				alarmList.add( alarmValue );
		    	event.setAlarms(alarmList);
		    	updateLayout();
			}
			
		};

		AlarmDialog customAlarm = new AlarmDialog(this, customAlarmListener, null,
				event.getStart(), event.getEnd(), VComponent.VEVENT);
		customAlarm.show();
	}

	public View getAlarmItem(final AcalAlarm alarm, ViewGroup parent) {
		LinearLayout rowLayout;

		LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		TextView title = null; //, time = null, location = null;
		rowLayout = (TableRow) inflater.inflate(R.layout.alarm_list_item, parent, false);

		title = (TextView) rowLayout.findViewById(R.id.AlarmListItemTitle);
		title.setText(alarm.toPrettyString());
		
		ImageView cancel = (ImageView) rowLayout.findViewById(R.id.delete_button);

		rowLayout.setTag(alarm);
		cancel.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				alarmList.remove(alarm);
				updateLayout();
			}
		});
		return rowLayout;
	}
	


	@Override
	public void resourceChanged(ResourceChangedEvent event) {
		// TODO Auto-generated method stub
		
	}


	@SuppressWarnings("unchecked")
	@Override
	public void resourceResponse(ResourceResponse response) {
		Object result = response.result();
		if (result == null) {
			int msg = FAIL;
			mHandler.sendMessage(mHandler.obtainMessage(msg));
		}
		if (result instanceof EventInstance) {
			int msg = FAIL;
			if (response.wasSuccessful()) {
				this.event = (EventInstance) result;
				msg = REFRESH;
			}
			mHandler.sendMessage(mHandler.obtainMessage(msg));		
		} else if (result instanceof Long) {
			mHandler.sendMessage(mHandler.obtainMessage(SAVE_RESULT, result));
		}
		
	}
}
