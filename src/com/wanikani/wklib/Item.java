package com.wanikani.wklib;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* 
 *  Copyright (c) 2013 Alberto Cuda
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public abstract class Item implements Serializable {
	
	public static final long serialVersionUID = 1L;
	
	public static class SortByToxicity implements Comparator<Item> {
		
		boolean ascending;
		
		Comparator<Item> secondKey;

		public final static SortByToxicity INSTANCE = new SortByToxicity (false);

		public final static SortByToxicity INSTANCE_ASCENDING = new SortByToxicity (true);

		private SortByToxicity (boolean ascending)
		{
			this.ascending = ascending;
			
			secondKey = ascending ? SortByMaxStreaks.INSTANCE : SortByTime.INSTANCE_ASCENDING;
		}
		
		public SortByToxicity (boolean ascending, Comparator<Item> secondKey)
		{
			this.ascending = ascending;
			this.secondKey = secondKey;
		}
		
		public int compare (Item a, Item b)
		{
			int ans, am, bm;
			
			/* Klooge to make sure that when percentage is unknown,
			 * items go at the end of the list */
			am = a.stats != null && !a.stats.burned ? 
					(a.stats.reading != null ? a.stats.reading.incorrect : 0) + 
					(a.stats.meaning != null ? a.stats.meaning.incorrect : 0)  : -1; 
										
			bm = b.stats != null && !b.stats.burned ? 
					(b.stats.reading != null ? b.stats.reading.incorrect : 0) + 
					(b.stats.meaning != null ? b.stats.meaning.incorrect : 0)  : -1;
					
			if (am < 0 && ascending)
				am = -1;
			if (bm < 0 && ascending)
				bm = -1;
			
			ans = am - bm;
	
			ans = ascending ? ans : -ans;

			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}
	
	public static class SortByMaxStreaks implements Comparator<Item> {
		
		boolean ascending;
		
		Comparator<Item> secondKey;

		public final static SortByMaxStreaks INSTANCE = new SortByMaxStreaks (false);

		public final static SortByMaxStreaks INSTANCE_ASCENDING = new SortByMaxStreaks (true);

		private SortByMaxStreaks (boolean ascending)
		{
			this.ascending = ascending;
			
			secondKey = ascending ? SortByErrors.INSTANCE : SortByErrors.INSTANCE_ASCENDING;
		}
		
		public SortByMaxStreaks (boolean ascending, Comparator<Item> secondKey)
		{
			this.ascending = ascending;
			this.secondKey = secondKey;
		}
		
		public int compare (Item a, Item b)
		{
			int ans, am, bm;
			
			/* Klooge to make sure that when percentage is unknown,
			 * items go at the end of the list */
			am = a.stats != null ? 
					(a.stats.reading != null ? a.stats.reading.maxStreak : 0) + 
					(a.stats.meaning != null ? a.stats.meaning.maxStreak : 0)  : -1; 
										
			bm = b.stats != null ? 
					(b.stats.reading != null ? b.stats.reading.maxStreak : 0) + 
					(b.stats.meaning != null ? b.stats.meaning.maxStreak : 0)  : -1; 

			if (am < 0 && ascending)
				am = -1;
			if (bm < 0 && ascending)
				bm = -1;
			
			ans = am - bm;
	
			ans = ascending ? ans : -ans;

			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}

	public static class SortByErrors implements Comparator<Item> {
		
		boolean ascending;
		
		Comparator<Item> secondKey;

		public final static SortByErrors INSTANCE = new SortByErrors (false);

		public final static SortByErrors INSTANCE_ASCENDING = new SortByErrors (true);

		private SortByErrors (boolean ascending)
		{
			this.ascending = ascending;
			
			secondKey = ascending ? SortByTime.INSTANCE_ASCENDING : SortByTime.INSTANCE;
		}
		
		public SortByErrors (boolean ascending, Comparator<Item> secondKey)
		{
			this.ascending = ascending;
			this.secondKey = secondKey;
		}
		
		public int compare (Item a, Item b)
		{
			int ans, ap, bp;
			
			/* Klooge to make sure that when percentage is unknown,
			 * items go at the end of the list */
			ap = a.percentage;
			if (ap < 0 && !ascending)
				ap = 101;
			bp = b.percentage;
			if (bp < 0 && !ascending)
				bp = 101;
			
			ans = bp - ap;
	
			ans = ascending ? ans : -ans;

			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}
	

	public static class SortByTime implements Comparator<Item> {
		
		boolean ascending;

		public final static SortByTime INSTANCE = new SortByTime (false);

		public final static SortByTime INSTANCE_ASCENDING = new SortByTime (true);
		
		private SortByTime (boolean ascending)
		{
			this.ascending = ascending;
		}
		
		public int compare (Item a, Item b)
		{
			Date ula, ulb;
			int ans;

			ula = a.getUnlockedDate ();
			ulb = b.getUnlockedDate ();

			/* Non-unlocked items should always appear last */
			if (ula != null) {
				if (ulb != null)
					ans = ula.compareTo (ulb);
				else
					return -1;
			} else {
				if (ulb != null)
					return 1;
				else
					return 0;
			}
			
			return ascending ? ans : -ans;
		}
	}
	
	public static class SortByAvailable implements Comparator<Item> {
		
		boolean ascending;

		public final static SortByAvailable INSTANCE = new SortByAvailable (false);

		public final static SortByAvailable INSTANCE_ASCENDING = new SortByAvailable (true);
		
		private SortByAvailable (boolean ascending)
		{
			this.ascending = ascending;
		}
		
		public int compare (Item a, Item b)
		{
			Date ala, alb;
			boolean ba, bb;
			int ans;

			ba = a.stats != null && a.stats.burned;
			bb = b.stats != null && b.stats.burned;
			
			/* Burned items go last */
			if (ba && !bb)
				return 1;
			if (!ba && bb)
				return -1;
			
			ala = a.getAvailableDate ();
			alb = b.getAvailableDate ();
			
			/* Non-unlocked items should always appear last */
			if (ala != null) {
				if (alb != null)
					ans = -ala.compareTo (alb);
				else
					return -1;
			} else {
				if (alb != null)
					return 1;
				else
					return 0;
			}
			
			return ascending ? ans : -ans;
		}
	}
	
	public static class SortByLevel implements Comparator<Item> {
		
		boolean ascending;
		
		Comparator<Item> secondKey;
		
		public final static SortByLevel INSTANCE = new SortByLevel (false);

		public final static SortByLevel INSTANCE_ASCENDING = new SortByLevel (true);

		private SortByLevel (boolean ascending)
		{
			this.ascending = ascending;
			secondKey = ascending ? SortBySRS.INSTANCE_ASCENDING : SortBySRS.INSTANCE;
		}
		
		public SortByLevel (boolean ascending, Comparator<Item> secondKey)
		{
			this.ascending = ascending;
			this.secondKey = secondKey;
		}

		public int compare (Item a, Item b)
		{
			int ans;
			
			ans = a.level - b.level;
			
			ans = ascending ? ans : -ans;

			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}

	public static class SortBySRS implements Comparator<Item> {
		
		boolean ascending;
		
		Comparator<Item> secondKey;
		
		public final static SortBySRS INSTANCE = new SortBySRS (false);

		public final static SortBySRS INSTANCE_ASCENDING = new SortBySRS (true);

		private SortBySRS (boolean ascending)
		{
			this.ascending = ascending;
			secondKey = ascending ? SortByAvailable.INSTANCE_ASCENDING : 
					SortByAvailable.INSTANCE;
		}
		
		public SortBySRS (boolean ascending, Comparator<Item> secondKey)
		{
			this.ascending = ascending;
			this.secondKey = secondKey;
		}

		public int compare (Item a, Item b)
		{
			int ans;
			
			if (a.stats != null && b.stats != null)
				ans = a.stats.srs.compareTo (b.stats.srs);
			else if (a.stats != null)
				ans = 1;
			else if (b.stats != null)
				ans = -1;
			else
				ans = 0;
			
			ans = ascending ? ans : -ans;

			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}

	public static class SortByType implements Comparator<Item> {
		
		Comparator<Item> secondKey;
		
		public final static SortByType INSTANCE = new SortByType (false);

		public final static SortByType INSTANCE_ASCENDING = new SortByType (true);

		private SortByType (boolean ascending)
		{
			secondKey = ascending ? SortByLevel.INSTANCE_ASCENDING : SortByLevel.INSTANCE;
		}
		
		public SortByType (boolean ascending, Comparator<Item> secondKey)
		{
			this.secondKey = secondKey;
		}

		public int compare (Item a, Item b)
		{
			int ans;
			
			ans = a.type.compareTo (b.type);
			
			return ans == 0 ? secondKey.compare (a, b) : ans;
		}
	}

	public interface Factory<T extends Item> {
		
		public T deserialize (JSONObject obj)
			throws JSONException;
		
	};
		
	public static enum Type {
		
		RADICAL	{
			public Factory<Item> getFactory ()
			{
				return Radical.ITEM_FACTORY;
			}
		},
		KANJI {
			public Factory<Item> getFactory ()
			{
				return Kanji.ITEM_FACTORY;
			}
		},
		VOCABULARY {
			public Factory<Item> getFactory ()
			{
				return Vocabulary.ITEM_FACTORY;
			}
		};
		
		public static Type fromString (String s)
		{
			if (s.equals ("radical"))
				return RADICAL;
			else if (s.equals ("kanji"))
				return KANJI;
			else if (s.equals ("vocabulary"))
				return VOCABULARY;
			
			return null;
		}
		
		public abstract Factory<Item> getFactory ();

	}
	
	public static class Performance implements Serializable {
				
		public static final long serialVersionUID = 1L;
		
		public int correct;
		
		public int incorrect;
		
		public int maxStreak;
		
		public int currentStreak;
		
		Performance (JSONObject obj, String prefix)
			throws JSONException
		{
			correct = Util.getInt (obj, prefix + "_correct");
			incorrect = Util.getInt (obj, prefix + "_incorrect");
			maxStreak = Util.getInt (obj, prefix + "_max_streak");
			currentStreak = Util.getInt (obj, prefix + "_current_streak");
		}
		
		public Performance ()
		{
			/* empty */
		}
		
	};
	
	public static class Stats implements Serializable {
		
		public static final long serialVersionUID = 1L;

		public SRSLevel srs;
		
		private Date unlockedDate;
		
		public Date availableDate;
		
		public Date burnedDate;
		
		public boolean burned;
				
		public Item.Performance reading;
		
		public Item.Performance meaning;		

		public String readingNote;
		
		public String meaningNote;
		
		public String userSynonyms [];
		
		Stats (JSONObject obj, boolean hasReading)
			throws JSONException
		{
			JSONArray synonyms;
			String s;
			int i;
			
			s = obj.getString ("srs");
			srs = SRSLevel.fromString (s);
			if (srs == null)
				throw new JSONException ("Bad SRS: " + s);
			
			unlockedDate = Util.getDate (obj, "unlocked_date");
			availableDate = Util.getDate (obj, "available_date"); 
			burnedDate = Util.getDate (obj, "burned_date");
			burned = Util.getBoolean (obj, "burned");
			
			if (hasReading)
				reading = new Item.Performance (obj, "reading");			
			meaning = new Item.Performance (obj, "meaning");
			
			meaningNote = Util.getString (obj, "meaning_note");
			if (obj.has ("reading_note"))
				readingNote = Util.getString (obj, "reading_note");
			if (!obj.isNull ("user_synonyms")) {
				synonyms = obj.getJSONArray ("user_synonyms");
				userSynonyms = new String [synonyms.length ()];
				for (i = 0; i < userSynonyms.length; i++)
					userSynonyms [i] = synonyms.getString (i);
			}
		}
		
		public Stats ()
		{
			/* empty */
		}
	};
	
	private static class DynamicFactory implements Item.Factory<Item> {

		public Item deserialize (JSONObject obj)
			throws JSONException
		{
			Type type;
			String s;
			
			s = obj.getString ("type");
			type = Type.fromString (s);
			if (type == null)
				throw new JSONException ("Unexpected type: " + s);

			return type.getFactory ().deserialize (obj);
		}
	}
	
	public static final Factory<Item> FACTORY = new DynamicFactory ();

	public Type type;
	
	public String character;
	
	public String meaning;
	
	public int level;
	
	public Stats stats;
	
	public int percentage;
	
	private Date unlockedDate;
		
	public Date instanceCreationDate;
	
	protected Item (JSONObject obj, Type type)
		throws JSONException
	{
		this.type = type;
		
		instanceCreationDate = new Date ();
		character = Util.getString (obj, "character");
		meaning = Util.getString (obj, "meaning");
		level = Util.getInt (obj, "level");
		
		if (!obj.isNull ("user_specific"))
			stats = new Stats (obj.getJSONObject ("user_specific"), hasReading ());
		
		/* Only for critical items */
		if (!obj.isNull ("percentage"))
			percentage = obj.optInt ("percentage");
		else if (stats != null)
			setStats (stats);
		else
			percentage = -1;	/* No info */
			
		
		/* Only for recent unlocks */
		unlockedDate = Util.getDate (obj, "unlocked_date");
	}
	
	protected Item (Type type)
	{
		this.type = type;
		
		percentage = -1;
	}
	
	public void setStats (Stats stats)
	{
		int num, den;

		this.stats = stats;
		
		num = stats.meaning.correct;
		den = stats.meaning.correct + stats.meaning.incorrect;
		if (stats.reading != null) {
			num += stats.reading.correct;
			den += stats.reading.correct + stats.reading.incorrect;
		}
		percentage = den != 0 ? num * 100 / den : 100; 		
	}
	
	public Date getUnlockedDate ()
	{
		return 	unlockedDate != null ? unlockedDate :
					stats == null ? null : stats.unlockedDate;
	}
	
	public void setUnlockedDate (Date date)
	{
		unlockedDate = date;
		if (stats != null)
			stats.unlockedDate = date;
	}
	
	public Date getAvailableDate ()
	{
		return stats == null ? null : stats.availableDate;
	}

	protected boolean hasReading ()
	{
		return true;
	}
	
	public String getItemURLComponent ()
	{
		return character;
	}
	
	protected abstract String getClassURLComponent ();
	
	public String getURL (boolean tls)
	{
		String scheme;
		
		scheme = tls ? "https" : "http";
		
		return scheme + "://www.wanikani.com/" + 
				getClassURLComponent () + "/" + getItemURLComponent ();
	}
	
	public boolean matches (String s)
	{
		return meaning.contains (s) ||
				(character != null && character.contains (s));
	}
	
	public void fixup ()
	{
		/* empty */
	}
}
