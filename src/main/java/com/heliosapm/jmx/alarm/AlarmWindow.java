/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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
package com.heliosapm.jmx.alarm;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: AlarmWindow</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.alarm.AlarmWindow</code></p>
 * @param <T> The type being tracked
 */

public class AlarmWindow<T extends Number> extends NotificationBroadcasterSupport {
	/** The alarm JMX ObjectName */
	protected final ObjectName objectName;
	/** The hash code */
	protected final long hashCode;
	
	final String metricName;
	final Map<String, String> tags; 
	final int windowSize;
	final T threshold;
	final ConcurrentSkipListMap<Long, T> window = new ConcurrentSkipListMap<Long, T>(); 
	
	
	/** The JMX domain for alarms */
	public static final String ALARM_DOMAIN = "com.heliosapm.alarms";

	private static final HashFunction hashFunction = Hashing.murmur3_128();
	/** The UTF8 Charset  */
	public static final Charset CHARSET = Charset.forName("UTF8");
	
	/** The notification infos */
	protected static final MBeanNotificationInfo[] NOTIF_INFOS = new MBeanNotificationInfo[]{}; 
	
	/** A map of all alarms keyed by the alaram hashcode */
	protected static final NonBlockingHashMapLong<AlarmWindow<? extends Number>> thresholdAlarms = new NonBlockingHashMapLong<AlarmWindow<? extends Number>>(128, true); 
	
	/**
	 * Creates a new AlarmWindow to alert on {@code windowSize} consecutive above the threshold of {@code threshold}
	 * @param metricName  The metric name of the metric to track
	 * @param tags The metric tags of the metric to track
	 * @param windowSize The number of consecutive samples that will trigger an alarm
	 * @param threshold The threshold above or equal to which a sample's value will include it in the series 
	 */
	public AlarmWindow(final String metricName, final Map<String, String> tags, int windowSize, final T threshold) {
		super(SharedNotificationExecutor.getInstance(), NOTIF_INFOS);
		objectName = buildObjectName(metricName, tags);		
		hashCode = hashCode(metricName, tags);
		thresholdAlarms.put(hashCode, this);
		this.metricName = metricName;
		this.tags = Collections.unmodifiableMap(tags);
		this.windowSize = windowSize;
		this.threshold = threshold;		
	}
	
	
	/**
	 * Applies the sample, firing the alarm if the window's threshold is broken
	 * @param timestamp The timestamp of the sample
	 * @param value The value of the sample
	 */
	protected void sample(long timestamp, final T value) {
		if(value.doubleValue() >= threshold.doubleValue()) {
			synchronized(window) {
				window.put(timestamp, value);
				if(window.size() >= windowSize) {
					fire(new TreeMap<Long, T>(window));
					while(window.size() >= windowSize) {
						window.remove(window.firstKey());
					}									
				}
			}
		} else {			
			synchronized(window) {				
				while(window.size() >= windowSize) {
					window.remove(window.firstKey());
				}
			}
		}
	}
	
	
	protected void fire(final TreeMap<Long, T> copyOfWindow) {
		
	}
	
	
	/**
	 * Locates the alarm identified by the metric name and tags, and if found samples using the passed timestamp
	 * @param metricName The metric name
	 * @param tags The metric tags
	 * @param timestamp The timestamp of the sample in ms.
	 * @param value The value to submit
	 */
	public static <T extends Number> void sample(final String metricName, final Map<String, String> tags, long timestamp, final T value) {
		AlarmWindow<T> window = (AlarmWindow<T>) thresholdAlarms.get(hashCode(metricName, tags));
		if(window!=null) window.sample(timestamp, value);
	}
	
	/**
	 * Locates the alarm identified by the metric name and tags, and if found samples using the current timestamp
	 * @param metricName The metric name
	 * @param tags The metric tags
	 * @param value The value to submit
	 */
	public static <T extends Number> void sample(final String metricName, final Map<String, String> tags, final T value) {
		sample(metricName, tags, System.currentTimeMillis(), value);
	}

	
	/**
	 * Builds an object name for the passed metric name and tags
	 * @param metricName The metric name
	 * @param tags The metric tags
	 * @return the built object name
	 */
	protected static ObjectName buildObjectName(final String metricName, final Map<String, String> tags) {
		final LinkedHashMap<String, String> ht = new LinkedHashMap<String, String>(tags.size());
		ht.put("type", "alarm");
		ht.put("metricname", metricName);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			String key = clean(entry.getKey());
			if("type".equals(key)) {
				key = "xtype";
			}
			ht.put(clean(entry.getKey()), clean(entry.getValue()));
		}
		
		return JMXHelper.objectName(ALARM_DOMAIN, ht);
	}
	
	
	/**
	 * Cleans the passed stringy
	 * @param cs The stringy to clean
	 * @return the cleaned stringy
	 */
	public static String clean(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) return "";
		String s = cs.toString().trim();
		final int index = s.indexOf('/');
		if(index!=-1) {
			s = s.substring(index+1);
		}
		return s.replace(" ", "_");
	}
	
	/**
	 * Hashes the metric
	 * @param metricName The metric name
	 * @param tags The metric tags
	 * @return the metric hash code
	 */
	public static long hashCode(final String metricName, final Map<String, String> tags) {
		final Hasher hasher = hashFunction.newHasher();
		hasher.putString(metricName, CHARSET);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			hasher.putString(entry.getKey(), CHARSET).putString(entry.getValue(), CHARSET);
		}		
		return hasher.hash().asLong();
		
	}
	
}
