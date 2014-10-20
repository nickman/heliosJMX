/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.jmx.util.helpers;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;



/**
 * <p>Title: SystemClock</p>
 * <p>Description: General purpose time and timings provider.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.rindle.util.SystemClock</code></p>
 */

public class SystemClock {
	/** The platform high rez clock */
	private static final sun.misc.Perf PERF = sun.misc.Perf.getPerf();
	/** The JVM start time */
	private static final long START_DATE = ManagementFactory.getRuntimeMXBean().getStartTime();
	/** The JVM up time */
	private static final long START_TIME = ManagementFactory.getRuntimeMXBean().getUptime();

	/** The high res clock ticks per s */
	private static final double TICK_FREQ_S = PERF.highResFrequency();
	/** The high res clock ticks per s */
	private static final double TICK_FREQ_MS = TICK_FREQ_S/1000D;
	/** The high res clock ticks per us */
	private static final double TICK_FREQ_US = TICK_FREQ_MS/1000D;	
	/** The high res clock ticks per ns */
	private static final double TICK_FREQ_NS = TICK_FREQ_US/1000D;

	// VM Linux: 786828  Native Windows: 218969  Linux: 1000000
	public static void main(String[] args) {
		System.out.println("High Rez Freq:" + TICK_FREQ_S);
		System.out.println("High Rez Freq (ms):" + TICK_FREQ_MS);
		System.out.println("High Rez Freq (us):" + TICK_FREQ_US);
		System.out.println("High Rez Freq (ns):" + TICK_FREQ_NS);
	}
	
	/**
	 * Starts a new timer
	 * @return the elapsed time object on which elapsed times can be drawn
	 */
	public static ElapsedTime startClock() {
		return new ElapsedTime(System.nanoTime());
	}
	
	/**
	 * Sleeps for the specified number of ms.
	 * @param ms The number of ms. to sleep
	 */
	public static void sleep(final long ms) {
		if(ms<1L) return;
		try {
			Thread.currentThread().join(ms);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to sleep");
		}
	}
	
	
	/**
	 * Returns the current time in ms.
	 * @return the current time in ms.
	 */
	public static long time() {
		return System.currentTimeMillis();
	}
	
	/**
	 * Returns the current time in milliseconds to second precision
	 * @return the second precision current timestamp in ms.
	 */
	public static long rtime() {
		return TimeUnit.MILLISECONDS.convert(TimeUnit.SECONDS.convert(time(), TimeUnit.MILLISECONDS), TimeUnit.SECONDS);
	}
	
	/**
	 * Returns a JDBC timestamp for the current time
	 * @return a JDBC timestamp for the current time
	 */
	public static java.sql.Timestamp getTimestamp() {
		return new java.sql.Timestamp(time());
	}
	
	/**
	 * Returns the current time in Unix Time (s.)
	 * @return the current time in Unix Time
	 */
	public static long unixTime() {
		return TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	}
	
	
	/**
	 * Returns the relative time in ns.
	 * @return the relative time in ms.
	 */
	public static long timens() {
		return System.nanoTime();
	}
	
	
	
	
	/**
	 * <p>Title: ElapsedTime</p>
	 * <p>Description: An elapsed time reporter</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>org.helios.rindle.util.SystemClock.ElapsedTime</code></p>
	 * TODO: Lots....
	 * Format multiple time units, avg units
	 * Lap times
	 */
	public static class ElapsedTime {
		/** The start time in ns. */
		public final long startNs;
		/** The last lap end time in ns. */
		private long endNs = -1L;
		/** The closed elapsed time in ns. */
		private long elapsedNs = -1L;
	
		/**
		 * Creates a new ElapsedTime
		 * @param start The start time in ns.
		 */
		private ElapsedTime(long start){
			startNs = start;
		}
		
		/**
		 * Creates a new ElapsedTime
		 * @param start The start time in ns.
		 * @param end the end time in ns.
		 */
		private ElapsedTime(long start, long end){
			startNs = start;
			endNs = end;
			elapsedNs = endNs - startNs;
		}
		
		/**
		 * Stops the clock and returns the completed elapsed time
		 * @return the completed elapsed time
		 */
		public ElapsedTime stopClock() {
			long now = System.nanoTime();
			return new ElapsedTime(startNs, now);
		}
		
		/**
		 * Returns a lap clock which measures the time in between lap calls, 
		 * or if it is the first call, the time between the clock start and the first lap 
		 * @return a lap elapsed time
		 */
		public ElapsedTime lap() {
			long now = System.nanoTime();
			ElapsedTime lapTime = null;
			if(endNs==-1L) {
				// startClock --> first lap
				endNs = now;
				lapTime = new ElapsedTime(startNs, now);				
			} else {
				// nth lap --> n+1th lap
				lapTime = new ElapsedTime(endNs, now);
				endNs = now;
			}
			return lapTime;
		}
		
		
		
		
		/**
		 * Returns the start time in ns.
		 * @return the start time in ns.
		 */
		public long startTime() {
			return startNs;
		}
		
		/**
		 * Returns the start time in ms.
		 * @return the start time in ms.
		 */
		public long startTimeMs() {
			return TimeUnit.MILLISECONDS.convert(startNs, TimeUnit.NANOSECONDS);
		}
		
		/**
		 * Returns the start time in s.
		 * @return the start time in s.
		 */
		public long startTimeS() {
			return TimeUnit.SECONDS.convert(startNs, TimeUnit.NANOSECONDS);
		}
		
		
		
		
		/** Some extended time unit entries */
		public static final Map<TimeUnit, String> UNITS;
		
		static {
			Map<TimeUnit, String> tmp =  new EnumMap<TimeUnit, String>(TimeUnit.class);
			tmp.put(TimeUnit.DAYS, "days");
			tmp.put(TimeUnit.HOURS, "hrs.");
			tmp.put(TimeUnit.MICROSECONDS, "\u00b5s.");
			tmp.put(TimeUnit.MILLISECONDS, "ms.");
			tmp.put(TimeUnit.MINUTES, "min.");
			tmp.put(TimeUnit.NANOSECONDS, "ns.");
			tmp.put(TimeUnit.SECONDS, "s.");
			UNITS = Collections.unmodifiableMap(tmp);			
		}
		
		
		
//		private ElapsedTime(boolean lap, long endTime) {
//			endNs = endTime;
//			startNs = timerStart.get()[0];
//			long[] lastLapRead = lapTime.get();
//			if(lastLapRead!=null) {
//				lastLapNs = lastLapRead[0];
//			}
//			if(lap) {
//				lapTime.set(new long[]{endTime});
//			} else {
//				timerStart.remove();
//				lapTime.remove();
//			}
//			elapsedNs = endNs-startNs;
//			elapsedMs = TimeUnit.MILLISECONDS.convert(elapsedNs, TimeUnit.NANOSECONDS);
//			if(lastLapNs!=-1L) {
//				elapsedSinceLastLapNs = endTime -lastLapNs;
//				elapsedSinceLastLapMs = TimeUnit.MILLISECONDS.convert(elapsedSinceLastLapNs, TimeUnit.NANOSECONDS);
//			}
//			 
//		}
		/**
		 * Returns the average elapsed time in ms. for the passed number of events
		 * @param cnt The number of events
		 * @return The average elapsed time in ms.
		 */
		public long avgMs(double cnt) {			
			return _avg(elapsed(TimeUnit.MILLISECONDS), cnt);
		}
		
		/**
		 * Returns the rate of events in events per ms. for the passed number of events
		 * @param cnt The number of events
		 * @return The average rate time in event/ms.
		 */
		public long rateMs(double cnt) {			
			return _rate(elapsed(TimeUnit.MILLISECONDS), cnt);
		}
		
		
		/**
		 * Returns the average elapsed time in ns. for the passed number of events
		 * @param cnt The number of events
		 * @return The average elapsed time in ns.
		 */
		public long avgNs(double cnt) {			
			return _avg(elapsedNs, cnt);
		}
		
		
		private long _avg(double time, double cnt) {
			if(time==0 || cnt==0 ) return 0L;
			double d = time/cnt;
			return Math.round(d);
		}
		
		private long _rate(double time, double cnt) {
			if(time==0 || cnt==0 ) return 0L;
			double d = cnt/time;
			return Math.round(d);
		}
		
		
		/**
		 * Returns the elapsed time since start in ns.
		 * @return elapsed ns.
		 */
		public long elapsed() {
			return elapsedNs;
		}
		
		/**
		 * Returns the elapsed time since start in ms.
		 * @return elapsed ms.
		 */
		public long elapsedMs() {
			return elapsed(TimeUnit.MILLISECONDS);
		}
		
		/**
		 * Returns the elapsed time since start in us.
		 * @return elapsed us.
		 */
		public long elapsedUs() {
			return elapsed(TimeUnit.MICROSECONDS);
		}
		
		
		/**
		 * Returns the elapsed time since start in s.
		 * @return elapsed s.
		 */
		public long elapsedS() {
			return elapsed(TimeUnit.SECONDS);
		}
		
		/**
		 * Returns the elapsed time since start in the passed unit
		 * @param unit The unit to report elapsed time in
		 * @return the elapsed time
		 */
		public long elapsed(TimeUnit unit) {
			ElapsedTime et = stopClock();
			if(et.elapsedNs==-1L) throw new IllegalStateException("Unclosed clock");			
			if(unit==null) unit = TimeUnit.NANOSECONDS;
			return unit.convert(et.elapsedNs, TimeUnit.NANOSECONDS);
		}
		
		/**
		 * Returns the decorated elapsed time since start in the passed unit
		 * @param unit The unit to report elapsed time in
		 * @return the decorated elapsed time 
		 */
		public String elapsedStr(TimeUnit unit) {
			if(unit==null) unit = TimeUnit.NANOSECONDS;
			return new StringBuilder("[").append(unit.convert(elapsedNs, TimeUnit.NANOSECONDS)).append("] ").append(UNITS.get(unit)).toString();
		}

		/**
		 * Returns the decorated elapsed time since start in ns.
		 * @return the decorated elapsed time since start in ns.
		 */
		public String elapsedStr() {			
			return elapsedStr(TimeUnit.NANOSECONDS);
		}
		
		/**
		 * Returns the decorated elapsed time since start in ms.
		 * @return the decorated elapsed time since start in ms.
		 */
		public String elapsedStrMs() {			
			return elapsedStr(TimeUnit.MILLISECONDS);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return printTime();
		}
		
		/**
		 * Prints the elapsed time in all units for seconds and below
		 * @return the elapsed times in all units 
		 */
		public String printTime() {
			ElapsedTime et = stopClock();
			return String.format("s: %s, ms: %s, \u00b5s: %s, ns: %s", 
					et.elapsedS(), et.elapsedMs(), et.elapsedUs(), et.elapsed()
					);
		}

		public String printAvg(String unitName, double cnt) {
			endNs = System.nanoTime();
			long elapsedNs = endNs - startNs;
			long avgNs = _avg(elapsedNs, cnt);
			return String.format("Completed %s %s in %s ms.  AvgPer: %s ms/%s \u00b5s/%s ns.",
					cnt,
					unitName, 
					TimeUnit.MILLISECONDS.convert(elapsedNs, TimeUnit.NANOSECONDS),
					TimeUnit.MILLISECONDS.convert(avgNs, TimeUnit.NANOSECONDS),
					TimeUnit.MICROSECONDS.convert(avgNs, TimeUnit.NANOSECONDS),
					avgNs					
			);
		}
		
	}
	

}
