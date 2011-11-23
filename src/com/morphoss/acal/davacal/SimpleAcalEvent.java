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

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;


/**
 * 
 * @author Morphoss Ltd
 *
 */
public class SimpleAcalEvent implements Parcelable, Comparable<SimpleAcalEvent> {

	private static final String TAG = "SimpleAcalEvent"; 

	//Start and end times are in UTC
	public final long start;
	public final long end;
	public final int resourceId;
	public final String summary;
	public final int colour;
	public final String location;
	public final boolean hasRepeat;
	public final boolean hasAlarm;
	public final boolean isAllDay;
	public final boolean isPending;
	public final boolean alarmEnabled;
	public final int startDateHash;
	public final int endDateHash;
	public final String timezoneId;

	final public static int  EVENT_OPERATION_NONE = 0;
	final public static int  EVENT_OPERATION_VIEW = 1;
	final public static int  EVENT_OPERATION_EDIT = 2;
	final public static int  EVENT_OPERATION_COPY = 3;
	final public static int  EVENT_OPERATION_MOVE = 4;
	
	public int operation = EVENT_OPERATION_NONE;
	
	final private static SimpleDateFormat fmtDebugDate = new SimpleDateFormat("MMM d HH:mm");
	
	/**
	 * Construct a new SimpleAcalEvent from all of the parameters
	 * @param start
	 * @param end
	 * @param resourceId
	 * @param summary
	 * @param location
	 * @param colour
	 * @param isAlarming
	 * @param isRepetitive
	 * @param allDayEvent
	 * @param isPending
	 * @param timezoneId - the TZID of the event we may be modifying
	 */
	public SimpleAcalEvent(long start, long end, int resourceId, String summary, String location, int colour,
				boolean isAlarming, boolean isRepetitive, boolean allDayEvent, boolean isPending,
				boolean alarmEnabled, String timezoneId ) {
		this.start = start;
		this.end = end;
		this.resourceId = resourceId;
		this.summary = summary;
		this.location = location;
		this.colour = colour;
		this.hasAlarm = isAlarming;
		this.alarmEnabled = alarmEnabled;
		this.hasRepeat = isRepetitive;
		this.isAllDay = allDayEvent;
		this.isPending = isPending;
		this.startDateHash = getDateHash(start);
		this.endDateHash = getDateHash(end - 1);
		this.timezoneId = timezoneId;

//		if ( Constants.LOG_VERBOSE ) {
//			Log.v(TAG,"Event at " + fmtDebugDate.format(new Date(this.start*1000)) + " ("+this.start+")" +
//						" to " + fmtDebugDate.format(new Date(this.end*1000)) + " ("+this.end+")" +
//						" for: " + this.summary
//						);
/*
			try {
				throw new Exception("debug");
			}
			catch ( Exception e ) {
				Log.v(TAG,Log.getStackTraceString(e));
			}
*/
//		}

	}

	private int getDateHash(long epochSecs) {
		Date d = new Date(epochSecs*1000);
		return getDateHash(d.getDate(), d.getMonth() + 1,d.getYear()+1900);
	}

	static public int getDateHash(int day, int month, int year) {
		return (year << 9) + (month << 5) + day;
	}
	
	/**
	 * Factory method to generate a SimpleAcalEvent from a real AcalEvent.  Since we don't have to worry
	 * about repetition from here on in, we ensure the events are localised to the user's current
	 * timezone before we go any further.
	 * 
	 * @param event The AcalEvent to be finely ground
	 * @return Essence of SimpleAcalEvent which we have ground from the AcalEvent.
	 */
	public static SimpleAcalEvent getSimpleEvent(AcalEvent event) {
		if ( event == null ) return null;
		AcalDateTime start = event.getStart();
//		if ( Constants.LOG_VERBOSE 
//			Log.v(TAG,"AcalEvent at " + start + " to " + event.getEnd() + " for: " + event.summary );

		boolean allDayEvent = start.isDate()
						||	(start.isFloating() && event.getDuration().getTimeMillis() == 0
																	&& event.getDuration().getDays() > 0)
						|| (event.getEnd().after(event.getStart().clone().addDays(2).applyLocalTimeZone().setDaySecond(0)) );

		start.applyLocalTimeZone();
		long finish = event.getEnd().applyLocalTimeZone().getEpoch();
		return new SimpleAcalEvent(start.getEpoch(), finish, event.getResourceId(), event.getSummary(),
					event.getLocation(), event.getColour(), 
					event.hasAlarms(), event.getRepetition().length() > 0, allDayEvent, event.isPending,
					event.getAlarmEnabled(), start.getTimeZoneId());
	}

	
	/**
	 * Construct a SimpleAcalEvent from bits of VComponent, with overrides for
	 * startDate & duration so we can build it inside a repeat rule calculation 
	 * @param event The parsed VComponent which is the event
	 * @param startDate To override the startDate in the event.
	 * @param duration To override the duration or end date in the event 
	 */
	public SimpleAcalEvent(VComponent event, AcalDateTime startDate, AcalDateTime endDate, boolean isPending ) {
		this.resourceId = event.getResourceId();
		
		this.isPending = isPending;

		boolean allDayEvent = startDate.isDate();
		boolean floating = startDate.isFloating();
		timezoneId = startDate.getTimeZoneId();
		startDate.applyLocalTimeZone();
		startDateHash = getDateHash( startDate.getMonthDay(), startDate.getMonth(), startDate.getYear() );
		start = startDate.getEpoch();
		
		long en = start - 1; // illegal value to test for...
		if ( endDate != null )
			en = endDate.getEpoch();
		
		if ( en < start ) {
			AcalProperty dtend = event.getProperty("DTEND");  
			if (dtend != null)
				en = AcalDateTime.fromAcalProperty(dtend).getEpoch();
		}
		if ( en < start ) {
			if ( startDate.isDate() )
				en = start + AcalDateTime.SECONDS_IN_DAY;
			else
				en = start;
		}
		end = en;
		endDateHash = getDateHash(end);

		if ( floating && !allDayEvent && (en - start) > 0 && ((en - start) % AcalDateTime.SECONDS_IN_DAY ) == 0 ) 
			allDayEvent = true;

		isAllDay = allDayEvent;
		
		summary = event.safePropertyValue("SUMMARY");
		location = event.safePropertyValue("LOCATION");
		String repeats = event.safePropertyValue("RRULE") + event.safePropertyValue("RDATE");
		hasRepeat = repeats.length()>0;

		boolean isAlarming = false;
		for( VComponent child : event.getChildren() ) {
			if ( child instanceof VAlarm ) {
				isAlarming = true;
				break;
			}
		}
		hasAlarm = isAlarming;
		
		int aColor = Color.BLUE;
		boolean alarmsForCollection = true;
		try {
			aColor = event.getCollectionColour();
			alarmsForCollection = event.getAlarmEnabled();
		} catch (Exception e) {
			Log.e(TAG,"Error Creating SimpleAcalEvent - "+e.getMessage());
			Log.e(TAG,Log.getStackTraceString(e));
		}
		colour = aColor;
		alarmEnabled = alarmsForCollection;
		
	}

	
	/**
	 * Identifies whether this event overlaps another.  In this case "Overlap" is defined
	 * in accordance with the spirit of RFC5545 et al, in that the event does *not* include
	 * the end time.
	 * 
	 * @param e Another event we might overlap.
	 * @return true iff this event overlaps the one given, otherwise false
	 */
	public boolean overlaps(SimpleAcalEvent e) {
		return this.end > e.start && this.start < e.end;
	}
	
	
	/**
	 * Special Fields/Methods for WeekView
	 */
	
	private int maxWidth;
	private int actualWidth;
	private int lastWidth;
	
	public int calulateMaxWidth(int screenWidth, int HSPP) {
		this.actualWidth = (int)(end-start)/HSPP;
		maxWidth = (actualWidth>screenWidth ? screenWidth : actualWidth);
		return maxWidth;
	}
	
	public int getMaxWidth() {
		return this.maxWidth;
	}
	
	public int getActualWidth() {
		return this.actualWidth;
	}
	
	public int getLastWidth() {
		return this.lastWidth;
	}

	public void setLastWidth(int w) {
		this.lastWidth = w;
	}
	

	/**
	 * Return a pretty string indicating the time period of the event.  If the start or end
	 * are on a different date to the view start/end then we also include the date on that
	 * element.  If it is an all day event, we say so. 
	 * @param viewDateStart - the start of the viewed range
	 * @param viewDateEnd - the end of the viewed range
	 * @param as24HourTime - from the pref
	 * @return A nicely formatted string explaining the start/end of the event.
	 */
	public String getTimeText(Context c, long viewDateStart, long viewDateEnd, boolean as24HourTime ) {
		String timeText = "";
		String timeFormatString = (as24HourTime ? "HH:mm" : "hh:mmaa");
		SimpleDateFormat timeFormatter = new SimpleDateFormat(timeFormatString);

		Date st = new Date(start*1000);
		Date en = new Date(end*1000);
		if ( start < viewDateStart || end  > viewDateEnd ){
			if ( isAllDay ) {
				timeFormatter = new SimpleDateFormat("MMM d");
				timeText = c.getString(R.string.AllDaysInPeriod, timeFormatter.format(st), timeFormatter.format(en));
			}
			else {
				SimpleDateFormat startFormatter = timeFormatter;
				SimpleDateFormat finishFormatter = timeFormatter;
				
				if ( start < viewDateStart )
					startFormatter  = new SimpleDateFormat("MMM d, "+timeFormatString);
				if ( end >= viewDateEnd )
					finishFormatter = new SimpleDateFormat("MMM d, "+timeFormatString);
		
				timeText = startFormatter.format(st)+" - " + finishFormatter.format(en);
			}
		}
		else if ( isAllDay ) {
			timeText = c.getString(R.string.ForTheWholeDay);
		}
		else {
			timeText = timeFormatter.format(st) + " - " + timeFormatter.format(en);
		}
		return timeText;
	}

	/**
	 * They're only equal if they're the same object, or if their resourceId is > 0 and equal
	 * @param another
	 * @return Whether these two events are equal
	 */
	@Override
	public boolean equals( Object another ) {
		return (this == another
					|| (another instanceof SimpleAcalEvent && this.resourceId > 0
								&& this.resourceId == ((SimpleAcalEvent) another).resourceId ) );
	}

	
	/**
	 * Compare this SimpleAcalEvent to another.  If this is earlier than the other return a negative
	 * integer and if this is after return a positive integer.  If they are the same return 0.
	 * @param another
	 * @return -1, 1 or 0
	 */
	@Override
	public int compareTo( SimpleAcalEvent another ) {
		if ( equals(another) ) return 0;
		if ( this.start < another.start ) return -1;
		if ( this.start > another.start ) return 1;
		return ( this.end < another.end ? -1 : (this.end > another.end ? 1 : 0));
	}

	
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(start);
		dest.writeLong(end);
		dest.writeInt(resourceId);
		dest.writeString(summary);
		dest.writeString(location);
		dest.writeInt(colour);
		dest.writeByte( (byte) ((alarmEnabled?0x10:0) | (hasAlarm?0x08:0) | (hasRepeat?0x04:0) | (isAllDay?0x02:0) | (isPending?0x01:0)) );
		dest.writeInt(startDateHash);
		dest.writeInt(endDateHash);
		dest.writeInt(operation);
		dest.writeString(timezoneId);
	}

	SimpleAcalEvent(Parcel src) {
		start = src.readLong();
		end = src.readLong();
		resourceId = src.readInt();
		summary = src.readString();
		location = src.readString();
		colour = src.readInt();
		byte b = src.readByte();
		alarmEnabled  = ((b & 0x10) == 0x10);
		hasAlarm  = ((b & 0x08) == 0x08);
		hasRepeat = ((b & 0x04) == 0x04);
		isAllDay  = ((b & 0x02) == 0x02);
		isPending = ((b & 0x01) == 0x01);
		startDateHash = src.readInt();
		endDateHash = src.readInt();
		operation = src.readInt();
		timezoneId = src.readString();
	}

	public static final Parcelable.Creator<SimpleAcalEvent> CREATOR = new Parcelable.Creator<SimpleAcalEvent>() {
		public SimpleAcalEvent createFromParcel(Parcel in) {
			return new SimpleAcalEvent(in);
		}

		public SimpleAcalEvent[] newArray(int size) {
			return new SimpleAcalEvent[size];
		}
	};

	public AcalEvent getAcalEvent(Context c) {
		AcalDateTime dtStart = new AcalDateTime().setTimeZone(timezoneId).setEpoch(start);
		return AcalEvent.fromDatabase(c, resourceId, dtStart );
	}
	
}
