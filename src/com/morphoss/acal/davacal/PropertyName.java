package com.morphoss.acal.davacal;

public enum PropertyName {
	UID, DTSTAMP, CREATED, LAST_MODIFIED, DTSTART, DTEND, DUE, DURATION, LOCATION, SUMMARY,
	DESCRIPTION, RRULE, RDATE, EXDATE, PERCENT_COMPLETE, COMPLETED, STATUS, TRIGGER, ACTION,
	RECURRENCE_ID, INVALID;
	
	public String toString() {
		return super.toString().replace('_', '-');
	}

	/**
	 * Returns a static array of the properties which can be localised with a TZID.
	 * @return
	 */
	public static PropertyName[] localisableDateProperties() {
		return new PropertyName[] { DTSTART, DTEND, DUE, COMPLETED };
	}
};

