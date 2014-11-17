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
package com.heliosapm.jmx.cache;

import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.json.JSONObject;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.jmx.notif.SharedNotificationExecutor;
import com.heliosapm.jmx.util.helpers.JMXHelper;


/**
 * <p>Title: CacheStatistics</p>
 * <p>Description: JMX enables cache statistics for guava caches</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.cache.CacheStatistics</code></p>
 * @param <K> the key type for the underlying cache
 * @param <V> the value type for the underlying cache
 */

public class CacheStatistics<K,V> extends NotificationBroadcasterSupport implements CacheStatisticsMBean, RemovalListener<Object, Object> {
	/** The wrapped cache instance */
	protected final Cache<K, V> cache;	
	/** The cache name */
	protected final String cacheName;
	/** The cache stats JMX object name */
	protected final ObjectName objectName;
	/** Notification sequence number factory */
	protected final AtomicLong sequence = new AtomicLong(0L);

	/** The descriptors of the JMX notifications emitted by this service */
	private static final MBeanNotificationInfo[] notificationInfos = new MBeanNotificationInfo[] {
		new MBeanNotificationInfo(new String[] {"cache.removal"}, Notification.class.getName(), "Notification emitted when a cache entry is removed")
	};

	/**
	 * Creates a new CacheStatistics and the underlying cache from the passed {@link CacheBuilder}.
	 * This call causes this CacheStatistics to register itself as the cache's removal listener
	 * and removal events are broadcast as JMX notifications.
	 * @param builder The guava cache builder
	 * @param cacheName The assigned name for this cache
	 * @return The build cache instance
	 */
	public static <K, V> Cache<K, V> getJMXStatisticsEnableCache(final CacheBuilder<?, ?> builder, final String cacheName) {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		CacheStatistics<K, V> cs = new CacheStatistics(builder, cacheName);
		cs.register();
		return cs.cache;
	}

	/**
	 * Creates a new CacheStatistics
	 * @param cache The guava cache instance to wrap
	 * @param cacheName The assigned name for this cache
	 */
	public CacheStatistics(final Cache<K, V> cache, final String cacheName) {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
		this.cache = cache;
		this.cacheName = cacheName;
		objectName = JMXHelper.objectName(JMXHelper.objectName("com.heliosapm.cache:name=" + cacheName));
	}
	
	/**
	 * Creates a new CacheStatistics and the underlying cache from the passed {@link CacheBuilder}.
	 * This call causes this CacheStatistics to register itself as the cache's removal listener
	 * and removal events are broadcast as JMX notifications.
	 * @param builder The guava cache builder
	 * @param cacheName The assigned name for this cache
	 */
	public CacheStatistics(final CacheBuilder<K, V> builder, final String cacheName) {
		super(SharedNotificationExecutor.getInstance(), notificationInfos);
		builder.removalListener(this);
		this.cache = builder.build();
		this.cacheName = cacheName;
		objectName = JMXHelper.objectName(JMXHelper.objectName("com.heliosapm.cache:name=" + cacheName));		
	}

	/**
	 * Registers the JMX interface for this cache stats, if not already registered
	 */
	public void register() {
		if(!JMXHelper.getHeliosMBeanServer().isRegistered(objectName)) {
			try {
				JMXHelper.getHeliosMBeanServer().registerMBean(this, objectName);
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getCacheClass()
	 */
	@Override
	public String getCacheClass() {
		return cache.getClass().getName();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(RemovalNotification<Object, Object> removal) {
		final Notification notif = new Notification("cache.removal", objectName, sequence.incrementAndGet(), System.currentTimeMillis(), "Cache entry [" + removal.toString() + "] removed from cache [" + cacheName + "]. Cause:" + removal.getCause().name());
		final JSONObject json = new JSONObject();
		json.put("event", "cache.removal");
		json.put("cacheName", cacheName);
		json.put("key", removal.getKey());
		json.put("value", removal.getValue());
		json.put("cause", removal.getCause().name());		
		notif.setUserData(json.toString());
		sendNotification(notif);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#invalidateAll()
	 */
	@Override
	public void invalidateAll() {
		cache.invalidateAll();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#cleanup()
	 */
	@Override
	public void cleanup() {
		cache.cleanUp();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getSize()
	 */
	@Override
	public long getSize() {
		return cache.size();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getRequestCount()
	 */
	@Override
	public long getRequestCount() {
		return cache.stats().requestCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getHitCount()
	 */
	@Override
	public long getHitCount() {
		return cache.stats().hitCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getHitRate()
	 */
	@Override
	public double getHitRate() {
		return cache.stats().hitRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getMissCount()
	 */
	@Override
	public long getMissCount() {
		return cache.stats().missCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getMissRate()
	 */
	@Override
	public double getMissRate() {
		return cache.stats().missRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getLoadCount()
	 */
	@Override
	public long getLoadCount() {
		return cache.stats().loadCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getLoadSuccessCount()
	 */
	@Override
	public long getLoadSuccessCount() {
		return cache.stats().loadSuccessCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getLoadExceptionCount()
	 */
	@Override
	public long getLoadExceptionCount() {
		return cache.stats().loadExceptionCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getLoadExceptionRate()
	 */
	@Override
	public double getLoadExceptionRate() {
		return cache.stats().loadExceptionRate();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getTotalLoadTime()
	 */
	@Override
	public long getTotalLoadTime() {
		return cache.stats().totalLoadTime();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getAverageLoadPenalty()
	 */
	@Override
	public double getAverageLoadPenalty() {
		return cache.stats().averageLoadPenalty();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#getEvictionCount()
	 */
	@Override
	public long getEvictionCount() {
		return cache.stats().evictionCount();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.cache.CacheStatisticsMBean#printStats()
	 */
	@Override
	public String printStats() {		
		return cache.stats().toString();
	}

}
