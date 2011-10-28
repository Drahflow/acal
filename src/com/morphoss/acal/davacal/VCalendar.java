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

package com.morphoss.acal.davacal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.Constants;
import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.acaltime.AcalRepeatRule;
import com.morphoss.acal.acaltime.AcalRepeatRuleParser;

public class VCalendar extends VComponent {
	public static final String TAG = "aCal VCalendar";
	private AcalDateRange dateRange = null;
	private AcalRepeatRule repeatRule = null;
	private Boolean masterHasOverrides = null;
	private Boolean hasAlarms = null;
	private Boolean hasRepeatRule = null;
	private Long earliestStart;
	private Long latestEnd;
	private boolean isPending = false;


	public VCalendar(ComponentParts splitter, Integer resourceId, Long earliestStart, Long latestEnd, AcalCollection collectionObject,VComponent parent) {
		super(splitter, resourceId, collectionObject,parent);
		this.earliestStart = earliestStart;
		this.latestEnd = latestEnd;
		if ( earliestStart != null ) {
			this.dateRange = new AcalDateRange(AcalDateTime.fromMillis(earliestStart),
					(latestEnd == null ? null : AcalDateTime.fromMillis(latestEnd)));
		}
	}


	protected VCalendar(AcalCollection collection) {
		super( VComponent.VCALENDAR, collection, null );
		try { setPersistentOn(); } catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) { }
		addProperty(new AcalProperty("CALSCALE","GREGORIAN"));
		addProperty(new AcalProperty("PRODID","-//morphoss.com//aCal 1.0//EN"));
		addProperty(new AcalProperty("VERSION","2.0"));
	}


	public static VCalendar getGenericCalendar( AcalCollection collection, AcalEvent newEventData) {
		VCalendar vcal = new VCalendar(collection);
		new VEvent(vcal);
		return vcal;
	}

	public void setPending(boolean isPending) {
		this.isPending = isPending;
	}
	public boolean isPending() {
		return this.isPending;
	}
	
	public VCalendar clone() {
		return new VCalendar(this.content, this.resourceId, this.earliestStart, this.latestEnd, this.collectionData, this.parent);
	}

	public String applyAction(AcalEvent action) {
		try {
			this.setPersistentOn();
			this.populateChildren();
			this.populateProperties();

			Masterable mast = this.getMasterChild();
			mast.setPersistentOn();
			mast.populateChildren();
			mast.populateProperties();

			// first, strip any existing properties which we always modify
			mast.removeProperties( new String[] {"DTSTAMP", "LAST-MODIFIED" } );

			// change DTStamp
			AcalDateTime lastModified = new AcalDateTime();
			lastModified.setTimeZone(TimeZone.getDefault().getID());
			lastModified.shiftTimeZone("UTC");

			mast.addProperty(AcalProperty.fromString(lastModified.toPropertyString("DTSTAMP")));
			mast.addProperty(AcalProperty.fromString(lastModified.toPropertyString("LAST-MODIFIED")));

			
			if ( action.getAction() == AcalEvent.ACTION_DELETE_SINGLE) {
				AcalProperty exDate = mast.getProperty("EXDATE");
				if ( exDate == null || exDate.getValue().equals("") ) 
					exDate = AcalProperty.fromString(action.getStart().toPropertyString("EXDATE"));
				else {
					mast.removeProperties( new String[] {"EXDATE"} );
					exDate = AcalProperty.fromString(exDate.toRfcString() + "," + action.getStart().fmtIcal() );
				}
				mast.addProperty(exDate);
			}
			else if (action.getAction() == AcalEvent.ACTION_DELETE_ALL_FUTURE) {
				AcalRepeatRuleParser parsedRule = AcalRepeatRuleParser.parseRepeatRule(action.getRepetition());
				AcalDateTime until = action.getStart().clone();
				until.addSeconds(-1);
				parsedRule.setUntil(until);
				String rrule = parsedRule.toString();
				action.setRepetition(rrule);
				mast.removeProperties( new String[] {"RRULE"} );
				mast.addProperty(new AcalProperty("RRULE",rrule));
			}
			else if (action.isModifyAction()) {
				this.applyModify(mast,action);

			}
			String ret = this.getCurrentBlob();
			this.destroyProperties();
			this.destroyChildren();
			this.setPersistentOff();
			return ret;
		}
		catch (Exception e) {
			Log.w(TAG,Log.getStackTraceString(e));
			return "";
		}

	}

	private void applyModify(Masterable mast, AcalEvent action) {
		//there are 3 possible modify actions:
		if (action.getAction() == AcalEvent.ACTION_MODIFY_SINGLE) {
			// Only modify the single instance
		}
		else if (action.getAction() == AcalEvent.ACTION_MODIFY_ALL_FUTURE) {
			// Modify this instance, and all future instances.

		}
		else if (action.getAction() == AcalEvent.ACTION_MODIFY_ALL) {
			// Modify all instances

			// First, strip any existing properties which we modify
			mast.removeProperties( new String[] {"DTSTART", "DTEND", "DURATION",
					"SUMMARY", "LOCATION", "DESCRIPTION", "RRULE" } );

			AcalDateTime dtStart = action.getStart().clone().applyLocalTimeZone();
			dtStart.setTimeZone(null);
			mast.addProperty( AcalProperty.fromString( dtStart.toPropertyString("DTSTART")));

			mast.addProperty(new AcalProperty("DURATION", action.getDuration().toString() ) );

			mast.addProperty(new AcalProperty("SUMMARY", action.getSummary()));

			String location = action.getLocation();
			if ( !location.equals("") )
				mast.addProperty(new AcalProperty("LOCATION",location));

			String description = action.getDescription();
			if ( !description.equals("") )
				mast.addProperty(new AcalProperty("DESCRIPTION",description));

			String rrule = action.getRepetition();
			if ( rrule != null && !rrule.equals(""))
				mast.addProperty(new AcalProperty("RRULE",rrule));

			mast.updateAlarmComponents( action.getAlarms() );
		}
	}

	public void checkRepeatRule() {
		try {
			if (repeatRule == null) repeatRule = AcalRepeatRule.fromVCalendar(this);
		}
		catch ( Exception e ) {
			Log.e(TAG,"Exception getting repeat rule from VCalendar", e);
		}
		hasRepeatRule = ( repeatRule != null );
	}

	public boolean appendAlarmInstancesBetween(List<AcalAlarm> alarmList, AcalDateRange rangeRequested) {
		if ( hasRepeatRule == null && repeatRule == null ) checkRepeatRule();
		if ( !hasRepeatRule ) return false;
		this.repeatRule.appendAlarmInstancesBetween(alarmList, rangeRequested);
		return true;
	}

	public boolean appendEventInstancesBetween(List<AcalEvent> eventList, AcalDateRange rangeRequested, boolean isPending) {
		if (isPending) {
			this.isPending = true;
			this.repeatRule = null;
		}
		try {
			if (dateRange != null) {
				AcalDateRange intersection = rangeRequested.getIntersection(this.dateRange);
				if (intersection != null) {
					if (hasRepeatRule == null && repeatRule == null) checkRepeatRule();
					if (hasRepeatRule) {
						//						Log.d(TAG,"Processing event: Summary="+new UnModifiableAcalEvent(thisEvent,new AcalCalendar(), new AcalCalendar()).summary);
						this.repeatRule.appendEventsInstancesBetween(eventList, intersection);
						return true;
					}
				}
			}
			else {
				checkRepeatRule();
				if (repeatRule != null) {
					this.repeatRule.appendEventsInstancesBetween(eventList, rangeRequested);
					return true;
				}
			}
			//			Log.d(TAG,"Skipped event: Summary="+new UnModifiableAcalEvent(thisEvent,new AcalCalendar(), new AcalCalendar()).summary);
		}
		catch(Exception e) {
			if (Constants.LOG_DEBUG)Log.d(TAG,"Exception in RepeatRule handling");
			if (Constants.LOG_DEBUG)Log.d(TAG,Log.getStackTraceString(e));
		}
		return false;
	}

	public Masterable getMasterChild() {
		if (childrenSet) {
			for (VComponent vc : this.getChildren()) {
				if ( vc instanceof VEvent)   return (VEvent)vc;
				if ( vc instanceof VTodo )	 return (VTodo) vc;
			}
		}
		for( PartInfo childInfo : content.partInfo ) {
			if ( childInfo.type.equals(VEVENT) ) {
				return new VEvent(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
			}
			else if ( childInfo.type.equals(VTODO) ) {
				return new VTodo(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
			}
		}
		return null;
	}

	public AcalDateTime getRangeEnd() {
		if ( dateRange == null ) return null;
		return dateRange.end;
	}

	public boolean masterHasOverrides() {
		if ( masterHasOverrides == null ) {
			int countMasterables = 0;
			for( PartInfo childInfo : content.partInfo ) {
				if ( childInfo.type.equals(VEVENT) || childInfo.type.equals(VTODO) ) {
					countMasterables++;
					if ( countMasterables > 1 ) break;
				}
			}
			if ( masterHasOverrides == null ) masterHasOverrides = (countMasterables > 1);
		}
		return masterHasOverrides;
	}


	public Masterable getChildFromRecurrenceId(RecurrenceId recurrenceProperty) {
		if ( masterHasOverrides() ) return this.getMasterChild();
		try {
			this.setPersistentOn();
			List<Masterable> matchingChildren = new ArrayList<Masterable>();
			for (VComponent vc: this.getChildren()) {
				if (vc.containsPropertyKey(recurrenceProperty.getName()) && vc instanceof Masterable)
					matchingChildren.add((Masterable) vc);
			}
			if (matchingChildren.isEmpty()) {
				// Won't happen since we test for this in masterHasOverrides()
				return this.getMasterChild();
			}
			Collections.sort(matchingChildren, RecurrenceId.getVComponentComparatorByRecurrenceId());
			for (int i = matchingChildren.size()-1; i>= 0; i--) {
				RecurrenceId cur = (RecurrenceId) matchingChildren.get(i).getProperty("RECURRENCE-ID");
				if (cur.notAfter(recurrenceProperty)) {
					this.setPersistentOff();
					return matchingChildren.get(i);
				}
			}
		} catch (YouMustSurroundThisMethodInTryCatchOrIllEatYouException e) {
			Log.w(TAG,Log.getStackTraceString(e));
		} finally {
			this.setPersistentOff();	
		}

		return this.getMasterChild();
	}

	
	public String getOlsonName( String TzID ) {
		Matcher m = Constants.tzOlsonExtractor.matcher(TzID);
		if ( m.matches() ) {
			return m.group(1);
		}

		if (childrenSet) {
			for (VComponent vc : this.getChildren()) {
				if ( vc instanceof VTimezone ) {
					if ( vc.getProperty("TZID").getValue() == TzID ) {
						AcalProperty idProperty = vc.getProperty("X-MICROSOFT-CDO-TZID");
						if ( idProperty != null && idProperty.getValue() != null ) {
							switch( Integer.parseInt(idProperty.getValue()) ) {
								case 0:    return("UTC");
								case 1:    return("Europe/London");
								case 2:    return("Europe/Lisbon");
								case 3:    return("Europe/Paris");
								case 4:    return("Europe/Berlin");
								case 5:    return("Europe/Bucharest");
								case 6:    return("Europe/Prague");
								case 7:    return("Europe/Athens");
								case 8:    return("America/Brasilia");
								case 9:    return("America/Halifax");
								case 10:   return("America/New_York");
								case 11:   return("America/Chicago");
								case 12:   return("America/Denver");
								case 13:   return("America/Los_Angeles");
								case 14:   return("America/Anchorage");
								case 15:   return("Pacific/Honolulu");
								case 16:   return("Pacific/Apia");
								case 17:   return("Pacific/Auckland");
								case 18:   return("Australia/Brisbane");
								case 19:   return("Australia/Adelaide");
								case 20:   return("Asia/Tokyo");
								case 21:   return("Asia/Singapore");
								case 22:   return("Asia/Bangkok");
								case 23:   return("Asia/Kolkata");
								case 24:   return("Asia/Muscat");
								case 25:   return("Asia/Tehran");
								case 26:   return("Asia/Baghdad");
								case 27:   return("Asia/Jerusalem");
								case 28:   return("America/St_Johns");
								case 29:   return("Atlantic/Azores");
								case 30:   return("America/Noronha");
								case 31:   return("Africa/Casablanca");
								case 32:   return("America/Argentina/Buenos_Aires");
								case 33:   return("America/La_Paz");
								case 34:   return("America/Indiana/Indianapolis");
								case 35:   return("America/Bogota");
								case 36:   return("America/Regina");
								case 37:   return("America/Tegucigalpa");
								case 38:   return("America/Phoenix");
								case 39:   return("Pacific/Kwajalein");
								case 40:   return("Pacific/Fiji");
								case 41:   return("Asia/Magadan");
								case 42:   return("Australia/Hobart");
								case 43:   return("Pacific/Guam");
								case 44:   return("Australia/Darwin");
								case 45:   return("Asia/Shanghai");
								case 46:   return("Asia/Novosibirsk");
								case 47:   return("Asia/Karachi");
								case 48:   return("Asia/Kabul");
								case 49:   return("Africa/Cairo");
								case 50:   return("Africa/Harare");
								case 51:   return("Europe/Moscow");
								case 53:   return("Atlantic/Cape_Verde");
								case 54:   return("Asia/Yerevan");
								case 55:   return("America/Panama");
								case 56:   return("Africa/Nairobi");
								case 58:   return("Asia/Yekaterinburg");
								case 59:   return("Europe/Helsinki");
								case 60:   return("America/Godthab");
								case 61:   return("Asia/Rangoon");
								case 62:   return("Asia/Kathmandu");
								case 63:   return("Asia/Irkutsk");
								case 64:   return("Asia/Krasnoyarsk");
								case 65:   return("America/Santiago");
								case 66:   return("Asia/Colombo");
								case 67:   return("Pacific/Tongatapu");
								case 68:   return("Asia/Vladivostok");
								case 69:   return("Africa/Ndjamena");
								case 70:   return("Asia/Yakutsk");
								case 71:   return("Asia/Dhaka");
								case 72:   return("Asia/Seoul");
								case 73:   return("Australia/Perth");
								case 74:   return("Asia/Riyadh");
								case 75:   return("Asia/Taipei");
								case 76:   return("Australia/Sydney");
								
								case 57: // null
								case 52: // null
								default: // null
							}
						}
					}
				}
			}
		}
		/**
		 * @todo: We should 
		 */
		return null; // We failed :-(
	}

	
	public boolean hasAlarm() {
		if ( this.hasAlarms != null ) return this.hasAlarms;
		for( PartInfo childInfo : content.partInfo ) {
			if ( childInfo.type.equals(VEVENT) ) {
				VEvent vc = new VEvent(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
				for( PartInfo childChildInfo : vc.content.partInfo ) {
					if ( childChildInfo.type.equals(VALARM)) {
						this.hasAlarms = true;
						return true;
					}
				}
			}
			else if ( childInfo.type.equals(VTODO) ) {
				VTodo vc = new VTodo(new ComponentParts(childInfo.getComponent(content.componentString)),
						resourceId, collectionData,this);
				for( PartInfo childChildInfo : vc.content.partInfo ) {
					if ( childChildInfo.type.equals(VALARM))  {
						this.hasAlarms = true;
						return true;
					}
				}
			}
			else if ( childInfo.type.equals(VALARM))  {
				this.hasAlarms = true;
				return true;
			}
		}
		this.hasAlarms = false;
		return false;
	}

	
	public VCalendar(Parcel in) {
		super(in);
	}

	public static final Parcelable.Creator<VCalendar> CREATOR = new Parcelable.Creator<VCalendar>() {
		public VCalendar createFromParcel(Parcel in) {
			return new VCalendar(in);
		}

		public VCalendar[] newArray(int size) {
			return new VCalendar[size];
		}
	};

}
