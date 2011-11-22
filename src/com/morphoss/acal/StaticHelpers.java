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

package com.morphoss.acal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentValues;
import android.graphics.Color;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;


public final class StaticHelpers {

	public static String[] mergeArrays(String[] first, String[] second) {
		String[] result = new String[first.length+second.length];
		for (int i = 0; i < first.length; i++) result[i] = first[i];
		for (int i = 0; i < second.length; i++) result[first.length+i] = second[i];
		return result;
	}


	public static void heapDebug(String TAG,String msg) {
		Runtime r = Runtime.getRuntime();
		long used = r.totalMemory() - r.freeMemory();
		double percentUsed = ((double) used) / ((double) r.maxMemory()) * 100.0;
		used /= 1024;
		if ( percentUsed > 80 )
			Log.e(TAG, String.format("%-40.40s: Heap used: %dk (%.2f%%) of max: %dk", msg, used, percentUsed, r.maxMemory()/1024));
		else if ( percentUsed > 50 )
			Log.w(TAG, String.format("%-40.40s: Heap used: %dk (%.2f%%) of max: %dk", msg, used, percentUsed, r.maxMemory()/1024));
		else
			Log.i(TAG, String.format("%-40.40s: Heap used: %dk (%.2f%%) of max: %dk", msg, used, percentUsed, r.maxMemory()/1024));
	}

	/**
	 * A helper to reliably turn a string into an int, returning 0 on any failure condition. 
	 * @param intThing
	 * @return
	 */
	public static int safeToInt( String intThing ) {
		if ( intThing == null ) return 0;
		try {
			int ret = Integer.parseInt(intThing);
			return ret;
		}
		catch ( Exception e ){
			return 0;
		}
	}

	public static String randomColorString() {
		String ret = "#";
		int colours[] = new int[3]; 
		int startpos = (int) (Math.random() * 3);
		colours[startpos] = (int) (Math.random() * 5.0) + 3;
		if ( ++startpos > 2 ) startpos -= 2;
		colours[startpos] = (int) (Math.random() * 9.0) + 3;
		if ( ++startpos > 2 ) startpos -= 2;
		colours[startpos] = (int) (Math.random() * 7.0) + 3;
		
		if ( Math.random() < 0.5 ) {
			int tmp = colours[1];
			colours[1] = colours[2];
			colours[2] = tmp;
		}

		if ( Math.random() < 0.5 ) {
			int tmp = colours[0];
			colours[0] = colours[2];
			colours[2] = tmp;
		}

		if ( Math.random() < 0.5 ) {
			int tmp = colours[0];
			colours[0] = colours[1];
			colours[1] = tmp;
		}

		for (int i = 0; i<3; i++) {
			ret += Integer.toHexString(colours[i]) + Integer.toHexString(colours[i]);
		}
		return ret;
	}
	
	/**
	 * Capitalise words in a sentence, apart from some specifically excepted ones.
	 * @param boring
	 * @return
	 */
	public static String capitaliseWords( String boring ) {
		final Pattern wordSplitter = Pattern.compile("\\b(\\w+)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL );
		StringBuffer capped = new StringBuffer();
		Matcher m = wordSplitter.matcher(boring);
		while( m.find() ) {
			if ( m.group().matches("(de|von|to|for|of|vom)") )
				m.appendReplacement(capped, m.group());
			else
				m.appendReplacement(capped, m.group().substring(0,1).toUpperCase() + m.group().substring(1));
		}
		m.appendTail(capped);
		return capped.toString();
	}

	static final char[] HEXES = new char[] {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
	/**
	 * Convert a byte array into a hex string
	 * @param raw
	 * @return
	 */
	public static String toHexString( byte[] raw ) {
		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		for (final byte b : raw) {
			hex.append(HEXES[(b & 0xF0) >> 4]).append(HEXES[(b & 0x0F)]);
		}
		return hex.toString();
	}

	
	public static void copyContentValue(ContentValues cloned, ContentValues serverData, String columnName) {
		String aValue = serverData.getAsString(columnName);
		if ( aValue != null ) cloned.put(columnName, aValue);
	}


	/**
	 * Trim spaces, newlines & carriage-returns from the RHS of the string.
	 * 
	 * @param toTrim
	 * @param toStrip
	 * @return the trimmed string.
	 */
	public static String rTrim( String toTrim ) {
		int pos = toTrim.length();
		
		while( pos >= 0 ) {
			char ch = toTrim.charAt(pos-1);
			if ( ch == '\n' || ch == '\r' || ch == ' ' ) {
				pos--;
				continue;
			}
			break;
		}
		return toTrim.substring(0,pos);
	}

	
	/**
	 * URL escape things in the string.
	 * @param toEscape The String to be escaped
	 * @param makeParas set to true if you want \n => <p> conversion as well.
	 * @return The urlescaped string.
	 */
	public static String urlescape( String toEscape, boolean makeParas ) {
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < toEscape.length(); i++) {
			char chr = toEscape.charAt(i);
			switch (chr) {
				case '%':	escaped.append("%25");		break;
				case '\'':	escaped.append("%27");		break;
				case '#':	escaped.append("%23");		break;
				case '?':	escaped.append("%3f");		break;
				case '\n':
					if ( makeParas ) {
						escaped.append("<p>");
						break;
					}
				default:	escaped.append(chr);
			}
		}
		return escaped.toString();
	}

	
	/**
	 * Sometimes we are writing Long values which may be null into parcels
	 * @param dest The Parcel
	 * @param l The Long
	 */
	public static void writeNullableLong( Parcel dest, Long l) {
		dest.writeByte((byte)  (l == null ? 'N' : '+'));
		if ( l == null ) return;
		dest.writeLong(l);
	}

	/**
	 * Sometimes we are reading Long values which may be null from parcels
	 * @param dest The Parcel
	 * @param l The Long
	 */
	public static Long readNullableLong( Parcel src ) {
		byte b = src.readByte();
		if ( b == 'N' ) return null;
		return src.readLong();
	}
	

	/**
	 * The way we are theming some things is to have a LinearLayout with a solid
	 * background assigned, containing (normally) a button with a 9-patch which is
	 * white/transparent overlay.  This utility helps us set the colour on the
	 * relevant object.
	 * 
	 * @param someView
	 * @param someColour
	 */
	public static void setContainerColour(View someView, int someColour) {
		ViewParent vp;
		do {
			vp = someView.getParent();
		} while ( !(vp instanceof View) );
		((View) vp).setBackgroundColor(someColour);
	}


	/**
	 * Given the supplied background colour, tries to pick a foreground colour with good
	 * contrast which is not too jarring against it.
	 * 
	 * This initial implementation is simplistic and probably needs refinement.
	 * 
	 * @param backgroundColour
	 * @return
	 */
	public static int pickForegroundForBackground( int backgroundColour ) {
		int r = (backgroundColour & 0xff0000) >> 32;
		int g = (backgroundColour & 0xff00) >> 16;
		int b = (backgroundColour & 0xff);
		
		if ( ((r + g + b) / 3) < 150 ) return Color.WHITE;
		return Color.BLACK;
	}
}
