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

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * <p>Title: StateService</p>
 * <p>Description: Singleton service for saving state and providing numerical deltas</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.StateService</code></p>
 */

public class StateService { 
	/** The singleton instance */
	private static volatile StateService instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The number of processors in the current JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The conf property name for the cache spec for the simple object cache */
	public static final String STATE_CACHE_PROP = "com.heliosapm.jmx.stateservice.simplecachespec";
	/** The conf property name for the cache spec for the long delta cache */
	public static final String STATE_LONG_CACHE_PROP = "com.heliosapm.jmx.stateservice.longcachespec";
	/** The conf property name for the cache spec for the double delta cache */
	public static final String STATE_DOUBLE_CACHE_PROP = "com.heliosapm.jmx.stateservice.doublecachespec";
	/** The conf property name for the cache spec for the script cache */
	public static final String STATE_SCRIPT_CACHE_PROP = "com.heliosapm.jmx.stateservice.scriptcachespec";
	/** The conf property name for the cache spec for the script binding cache */
	public static final String STATE_BINDING_CACHE_PROP = "com.heliosapm.jmx.stateservice.bindingcachespec";
	/** The default cache spec */
	public static final String STATE_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"maximumSize=5120," + 
		"expireAfterWrite=15m," +
		"expireAfterAccess=15m," +
		"softValues=true," +
		"recordStats=true";
	
	/** The simple state cache  */
	private final Cache<Object, Object> simpleStateCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build(); 
	/** The cache for long deltas */
	private final Cache<Object, long[]> longDeltaCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_LONG_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The cache for double deltas */
	private final Cache<Object, double[]> doubleDeltaCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_DOUBLE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The compiled script cache  */
	private final Cache<String, CompiledScript> scriptCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_SCRIPT_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The scoped script bindings cache  */
	private final Cache<Object, Bindings> bindingsCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_BINDING_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	
	/** The script engine */
	private final ScriptEngine engine = new ScriptEngineManager().getEngineByExtension("js");
	/** The script compiler */
	private final Compilable compiler = (Compilable)engine;
	
	/**
	 * Acquires the StateService singleton instance
	 * @return the StateService singleton instance
	 */
	public static StateService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new StateService();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Returns the cached compiled script for the passed source code, compiling it if it does not exist
	 * @param code The source code to get the compiled script for
	 * @return the compiled script
	 */
	public CompiledScript getCompiledScript(final String code) {
		if(code==null || code.trim().isEmpty()) throw new IllegalArgumentException("The passed code was null or empty");
		try {
			return scriptCache.get(code, new Callable<CompiledScript>() {
				@Override
				public CompiledScript call() throws Exception {
					return compiler.compile(code);
				}
			});
		} catch (Exception ex) {
			if(ex instanceof ScriptException) {
				throw new RuntimeException("Exception compiling script for [" + code + "]", ex);
			}
			throw new RuntimeException("Unexpected exception getting compiled script for [" + code + "]", ex);
		}
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
	 * Returns the cached bindings for the passed key, creating new bindings if not found
	 * @param key The key
	 * @return the cached bindings
	 */
	public Bindings getBindings(final Object key) {
		if(key==null) throw new IllegalArgumentException("The passed key was null");
		try {
			return bindingsCache.get(key, new Callable<Bindings>() {
				@Override
				public Bindings call() throws Exception {
					Bindings b = new SimpleBindings();
					b.put("bindings", b);
					b.put("bindingsKey", key);
					return b;
				}
			});
		} catch (Exception ex) {
			throw new RuntimeException("Unexpected exception getting bindings for key [" + key + "]", ex);
		}		
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
	 * @param type The type of the state object stored
	 * @return the state object or null if it was not found 
	 */
	public <T> T get(final Object key, final Class<T> type) {
		return (T)simpleStateCache.asMap().get(key);
	}
	
	/**
	 * Creates a new StateService
	 */
	private StateService() {
	}

}
