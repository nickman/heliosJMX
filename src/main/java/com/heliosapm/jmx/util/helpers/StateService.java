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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;

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
		"softValues," +
		"recordStats";
	
	/** A set of javascript helper source code file names */
	public static final Set<String> JS_HELPERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"math.js"
	)));
	
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
	/** Instance simple logger */
	private final SLogger log = SimpleLogger.logger(getClass());
	/** The script engine */
	private final ScriptEngine engine; 
	/** The script compiler */
	private final Compilable compiler;
	/** The engine level bindings (shared amongst all scripts */
	private final Bindings engineBindings;
	
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
					b.put("stateService", getInstance());
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
	
	public static void main(String[] args) {
		getInstance();
	}
	
	/**
	 * Finds a JS script engine that works with <a href="http://mathjs.org/">math.js</a>
	 * due to a <a href="https://github.com/mozilla/rhino/issues/127">JDK Rhino issue</a>
	 * @return A JS script engine that works or null if one could not be found
	 */
	private ScriptEngine findEngine() {
		final String javaHome = URLHelper.toURL(new File(System.getProperty("java.home"))).toString().toLowerCase();		
		ScriptEngine se = null;
		ScriptEngineManager sem = new ScriptEngineManager();
		Set<ScriptEngineFactory> nativeFirstScriptEngines = new LinkedHashSet<ScriptEngineFactory>();
		List<ScriptEngineFactory> tmp = new ArrayList<ScriptEngineFactory>(sem.getEngineFactories());
		for(Iterator<ScriptEngineFactory> iter = tmp.iterator(); iter.hasNext(); ) {
			final ScriptEngineFactory sef = iter.next();			
			if(!sef.getExtensions().contains("js")) {
				iter.remove();
				continue;			
			}
			try {
				String codeSource = sef.getScriptEngine().getClass().getProtectionDomain().getCodeSource().getLocation().toString();				
				if(codeSource.toLowerCase().startsWith(javaHome)) throw new Exception();
			} catch (Exception ex) {
				// some classes in system will throw an exception
				nativeFirstScriptEngines.add(sef);
				iter.remove();								
			}
		}
		nativeFirstScriptEngines.addAll(tmp);
		for(ScriptEngineFactory sef: nativeFirstScriptEngines) {
			try {
				if(!sef.getExtensions().contains("js")) continue;
				se = sef.getScriptEngine();
				se.eval("var obj = {};");      
		        se.eval("obj.boolean = 2;");    // error if engine has bug
		        return se;
			} catch (Exception ex) {
				se = null;
				log.loge("Discarding engine [%s] v. [%s]", sef.getEngineName(), sef.getEngineVersion());
			}
		}
		throw new RuntimeException("No compatible script engine found. Try Nashorn, Rhino or see https://github.com/mozilla/rhino/issues/127");
		
	}
	
	/**
	 * Creates a new StateService
	 */
	private StateService() {
		engine = findEngine();
		StringBuilder b = new StringBuilder("==============================\n\tSelected JS Engine:");
		b.append("\n\tEngine: [").append(engine.getFactory().getEngineName()).append("] version ").append(engine.getFactory().getEngineVersion());
		b.append("\n\tLanguage: [").append(engine.getFactory().getLanguageName()).append("] version ").append(engine.getFactory().getLanguageVersion());
		b.append("\n\tMime Types: ").append(engine.getFactory().getMimeTypes().toString());
		b.append("\n\tExtensions: ").append(engine.getFactory().getExtensions().toString());
		URL location = null;
		try { location = engine.getFactory().getClass().getProtectionDomain().getCodeSource().getLocation(); } catch (Exception x) {/* No Op */}		
		b.append("\n\tSEF: ").append(engine.getFactory().getClass().getName()).append("  CP:").append(location);
		log.log(b);
		
		compiler = (Compilable)engine;
		engineBindings = engine.createBindings();
		engineBindings.put("stateService", this);		
		engine.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
		
		loadJavaScriptHelpers();
		
	}
	
	private boolean areWeJarred() {
		final String myClassPath = getClass().getProtectionDomain().getCodeSource().getLocation().toString();
		return myClassPath.toLowerCase().endsWith(".jar");
	}
	
	private void loadJavaScriptHelpers() {
		for(String fileName: JS_HELPERS) {
			log.log("Loading JS Helper [%s]", fileName);
			InputStream is = null; 
			InputStreamReader isReader = null;
			try {
				if(areWeJarred()) {
					is = getClass().getClassLoader().getResourceAsStream("/javascript/" + fileName);
				} else {
					is = new FileInputStream("./src/main/resources/javascript/" + fileName);
				}
				if(is==null) {
					log.loge("Could not find JS Helper File [%s]", fileName);
					continue;
				}
				isReader = new InputStreamReader(is);
				try {
					Object c = compiler.compile(isReader);
					log.log("Compiled [%s] to [%s]:[%s]", fileName, c.getClass().getName(), c);
				} catch (Exception ex) {
					log.log("Compilation of [%s] Failed. Using Eval", fileName);
					engine.eval(isReader);
				}
				
				log.log("Loaded JS Helper [%s]", fileName);
			} catch (Exception ex) {
				log.loge("Failed to load JS Helper [%s] : %s", fileName, ex);
			} finally {
				if(isReader!=null) try { isReader.close(); } catch (Exception x) {/* No Op */}
				if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
			}
		}
		
	}

}
