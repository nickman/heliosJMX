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
package com.heliosapm.jmx.util.helpers;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.cache.CacheStatistics;

/**
 * <p>Title: CacheService</p>
 * <p>Description: Cache service for scoped temporal cached items and delta services</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.CacheService</code></p>
 */

public class CacheService {
	/** The singleton instance */
	protected static volatile CacheService instance = null;
	/** The singleton instance ctor lock */
	protected static final Object lock = new Object();
	
	/** The conf property name for the cache spec for the simple object cache */
	public static final String STATE_CACHE_PROP = "com.heliosapm.jmx.stateservice.simplecachespec";
	/** The conf property name for the cache spec for the long delta cache */
	public static final String STATE_LONG_CACHE_PROP = "com.heliosapm.jmx.stateservice.longcachespec";
	/** The conf property name for the cache spec for the double delta cache */
	public static final String STATE_DOUBLE_CACHE_PROP = "com.heliosapm.jmx.stateservice.doublecachespec";
	/** The number of processors in the current JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The default cache spec */
	public static final String STATE_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"maximumSize=5120," + 
		"expireAfterWrite=15m," +
		"expireAfterAccess=15m," +
		"weakValues" +
		",recordStats";
	
	/** The simple state cache  */
	private final Cache<Object, Object> simpleStateCache = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)), "state"); 
	/** The cache for long deltas */
	private final Cache<Object, long[]> longDeltaCache = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_LONG_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)), "longDeltas");
	/** The cache for double deltas */
	private final Cache<Object, double[]> doubleDeltaCache = CacheStatistics.getJMXStatisticsEnableCache(CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_DOUBLE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)), "doubleDeltas");

	

	
	
	/** Singleton ctor reentrancy check */
	private static final AtomicBoolean initing = new AtomicBoolean(false); 

	/**
	 * Acquires the CacheService singleton instance
	 * @return the CacheService singleton instance
	 */
	public static CacheService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					if(!initing.compareAndSet(false, true)) {
						throw new RuntimeException("Reentrant call to CacheService.getInstance(). Programmer Error.");
					}
					instance = new CacheService();
				}
			}
		}
		return instance;
	}
		

	/**
	 * Creates a new CacheService
	 */
	protected CacheService() {
		
	}

	
	/**
	 * Saves a simple keyed state
	 * @param key The key for the saved state
	 * @param value The value for the saved state
	 * @return The value previously bound to the passed key, or null if there was none
	 */
	public <T> T put(final Object key, final T value) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		if(value==null) throw new IllegalArgumentException("The passed value was null");
		return (T)simpleStateCache.asMap().put(key, value);		
	}

	/**
	 * Computes and returns an elapsed time
	 * @param key The key
	 * @param time The time that defines the end of the elapsed
	 * @return the elapsed time or null if no starting time was in state
	 */
	public Long elapsedTime(final Object key, final long time) {
		return delta(key, time);
	}
	
	/**
	 * Computes and returns an elapsed time using the current time as the end time
	 * @param key The key
	 * @return the elapsed time or null if no starting time was in state
	 */
	public Long elapsedTime(final Object key) {
		return delta(key, System.currentTimeMillis());
	}

	/**
	 * Caches the passed value and if it replaces an existing value, the delta of the two values is returned.
	 * Otherwise null is returned.
	 * @param key The key for this delta
	 * @param value The value to store and delta
	 * @return the delta value or null if no value was already in state
	 */
	public Long delta(final Object key, final long value) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		final long[] state = longDeltaCache.asMap().put(key, new long[] {value});
		if(state==null) return null;
		return value - state[0];
	}
	
	/**
	 * Implements a resetting delta where the state is reset to the specified value if the incoming value
	 * is less than the value in state. When this occurs, a null is returned. Otherwise behaves like {@link #delta(Object, long)}
	 * @param key The key
	 * @param value The incoming value to delta
	 * @param resetValue The value to reset the state to when the delta is reset
	 * @return the delta value or null if there was no value in state, or the delta was reset
	 */
	public Long rdelta(final Object key, final long value, final long resetValue) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		final long[] state = longDeltaCache.asMap().put(key, new long[] {value});
		if(state==null) return null;
		if(value < state[0]) {
			longDeltaCache.asMap().put(key, new long[] {resetValue});
			return null;
		}
		return value - state[0];				
	}
	
	/**
	 * Caches the passed value and if it replaces an existing value, the delta of the two values is returned.
	 * Otherwise null is returned.
	 * @param key The key for this delta
	 * @param value The value to store and delta
	 * @return the delta value or null if no value was already in state
	 */
	public Double delta(final Object key, final double value) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		final double[] state = doubleDeltaCache.asMap().put(key, new double[] {value});
		if(state==null) return null;
		return value - state[0];
	}
	
	/**
	 * Implements a resetting delta where the state is reset to the specified value if the incoming value
	 * is less than the value in state. When this occurs, a null is returned. Otherwise behaves like {@link #delta(Object, long)}
	 * @param key The key
	 * @param value The incoming value to delta
	 * @param resetValue The value to reset the state to when the delta is reset
	 * @return the delta value or null if there was no value in state, or the delta was reset
	 */
	public Double rdelta(final Object key, final double value, final double resetValue) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		final double[] state = doubleDeltaCache.asMap().put(key, new double[] {value});
		if(state==null) return null;
		if(value < state[0]) {
			doubleDeltaCache.asMap().put(key, new double[] {resetValue});
			return null;
		}
		return value - state[0];				
	}

	
	/**
	 * Returns the state object saved under the passed key
	 * @param key The key
	 * @param defaultValue The default value to return if the key is not bound
	 * @param type The type of the state object stored
	 * @return the state object or the defined default if it was not found 
	 */
	public <T> T get(final Object key, final T defaultValue, final Class<T> type) {
		T t = (T)simpleStateCache.asMap().get(key);
		return t!=null ? t : defaultValue; 
	}
	
	/**
	 * Returns the state object saved under the passed key
	 * @param key The key
	 * @param type The type of the state object stored
	 * @param callable The value loader used if the value is not found in cache
	 * @return the cached value
	 */
	public <T> T get(final Object key, final Class<T> type, final Callable<T> callable) {
		try {
			return (T) simpleStateCache.get(key, callable);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get cached value for [" + key + "]", ex);
		}
	}

	/**
	 * Returns the state object saved under the passed key
	 * @param key The key
	 * @param type The expected type of the bound value
	 * @return the cached value or null if not bound
	 */
	public <T> T get(final Object key, final Class<T> type) {
		return get(key, null, type);
	}
	
	/**
	 * Returns the state object saved under the passed key
	 * @param key The key
	 * @param defaultValue The default value to return if the key is not bound
	 * @return the state object or the defined default if it was not found 
	 */
	public Object get(final Object key, final Object defaultValue) {
		return get(key, defaultValue, Object.class);
	}
	
	/**
	 * Returns the state object saved under the passed key
	 * @param key The key
	 * @return the state object or the null if it was not found 
	 */
	public Object get(final Object key) {
		return get(key, null, Object.class);
	}
	
	
	
}
