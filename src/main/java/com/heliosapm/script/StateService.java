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
package com.heliosapm.script;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.heliosapm.jmx.util.helpers.ArrayUtils;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.compilers.DeploymentCompiler;
import com.heliosapm.script.compilers.GroovyCompiler;
import com.heliosapm.script.compilers.JSR223Compiler;

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

	/** The conf property name for a list of comma separated URLs to additional classpaths to add to the script engine factory classloader */
	public static final String SCRIPT_CLASSPATH_PROP = "com.heliosapm.jmx.stateservice.classpath";
	
	
	/** The conf property name for the cache spec for the simple object cache */
	public static final String STATE_CACHE_PROP = "com.heliosapm.jmx.stateservice.simplecachespec";
	/** The conf property name for the cache spec for the long delta cache */
	public static final String STATE_LONG_CACHE_PROP = "com.heliosapm.jmx.stateservice.longcachespec";
	/** The conf property name for the cache spec for the double delta cache */
	public static final String STATE_DOUBLE_CACHE_PROP = "com.heliosapm.jmx.stateservice.doublecachespec";
	/** The conf property name for the cache spec for the script cache */
	public static final String STATE_SCRIPT_CACHE_PROP = "com.heliosapm.jmx.stateservice.scriptcachespec";
	/** The conf property name for the cache spec for the deployment cache */
	public static final String STATE_DEPLOYMENT_CACHE_PROP = "com.heliosapm.jmx.stateservice.deploymentcachespec";
	
	/** The conf property name for the cache spec for the script binding cache */
	public static final String STATE_BINDING_CACHE_PROP = "com.heliosapm.jmx.stateservice.bindingcachespec";
	/** The default cache spec */
	public static final String STATE_CACHE_DEFAULT_SPEC = 
		"concurrencyLevel=" + CORES + "," + 
		"initialCapacity=256," + 
		"maximumSize=5120," + 
		"expireAfterWrite=15m," +
		"expireAfterAccess=15m," +
		"weakValues," +
		"recordStats";
	
	/** A set of javascript helper source code file names */
	public static final Set<String> JS_HELPERS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"math.js", "helpers.js"
	)));
	/** The script engine manager */
	private final ScriptEngineManager sem;
	/** The simple state cache  */
	private final Cache<Object, Object> simpleStateCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build(); 
	/** The cache for long deltas */
	private final Cache<Object, long[]> longDeltaCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_LONG_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The cache for double deltas */
	private final Cache<Object, double[]> doubleDeltaCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_DOUBLE_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The compiled script cache keyed by the script extension */
	private final Cache<String, Cache<String, CompiledScript>> scriptCache = CacheBuilder.newBuilder().concurrencyLevel(CORES).initialCapacity(16).recordStats().build();
	/** The scoped script bindings cache  */
	private final Cache<Object, Bindings> bindingsCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_BINDING_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	
	//===========================================================================================
	//		Deployment Management
	//===========================================================================================
	/** The compiled deployment cache  */
	private final Cache<String, DeployedScript<?>> deploymentCache = CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_DEPLOYMENT_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
	/** The deployment compilers keyed by script extension */
	private final Map<String, DeploymentCompiler<?>> deploymentCompilers = new NonBlockingHashMap<String, DeploymentCompiler<?>>(16);
	/** The catch-all deployment compiler */
	private final DeploymentCompiler<CompiledScript> catchAllCompiler;
	//===========================================================================================
	
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
//	/** The script engine */
//	private final ScriptEngine engine; 
//	/** The script compiler */
//	private final Compilable compiler;
	/** The script engines keyed by script extension */
	private final Map<String, ScriptEngine> engines = new NonBlockingHashMap<String, ScriptEngine>(16); 
	/** The script compilers keyed by script extension */
	private final Map<String, Compilable> compilers = new NonBlockingHashMap<String, Compilable>(16);
	
	
	
	
	
	/** The engine level bindings (shared amongst all scripts) */
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
	 * Determines if a script engine is registered that can handle a script of the passed extension
	 * @param extension The extension to test
	 * @return true if supported, false otherwise
	 */
	public boolean isExtensionSupported(final String extension) {
		if(extension==null || extension.trim().isEmpty()) return false; //throw new IllegalArgumentException("The passed extension was null or empty");
		return engines.containsKey(extension.trim().toLowerCase());
	}
	
	/**
	 * Installs a deployment compiler
	 * @param deploymentCompiler The deployment compiler to install
	 */
	protected void installDeploymentCompiler(final DeploymentCompiler<?> deploymentCompiler) {
		if(deploymentCompiler==null) throw new IllegalArgumentException("DeploymentCompiler was null");
		final String[] extensions = deploymentCompiler.getSupportedExtensions();		
		for(String extension: extensions) {			
			deploymentCompilers.put(extension, deploymentCompiler);			
		}
		log.info("Installed DeploymentCompiler [{}] for extensions {}", deploymentCompiler.getClass().getSimpleName(), Arrays.toString(extensions));
	}
	
	/**
	 * Returns the cached compiled script for the passed source code, compiling it if it does not exist
	 * @param extension The script extension
	 * @param code The source code to get the compiled script for
	 * @return the compiled script
	 */
	public CompiledScript getCompiledScript(final String extension, final String code) {
		if(code==null || code.trim().isEmpty()) throw new IllegalArgumentException("The passed code was null or empty");
		if(extension==null || extension.trim().isEmpty()) throw new IllegalArgumentException("The passed extension was null or empty");
		final String key = extension.trim().toLowerCase();
		final Compilable compiler = getCompilerForExtension(key);
		try {
			final Cache<String, CompiledScript> extensionCache = scriptCache.get(key, new Callable<Cache<String, CompiledScript>>(){
				@Override
				public Cache<String, CompiledScript> call() throws Exception {					
					return CacheBuilder.from(ConfigurationHelper.getSystemThenEnvProperty(STATE_SCRIPT_CACHE_PROP, STATE_CACHE_DEFAULT_SPEC)).build();
				}
			});
			return extensionCache.get(code, new Callable<CompiledScript>() {
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
	 * Returns the ScriptEngine for the passed script extension
	 * @param extension The script extension
	 * @return the associated ScriptEngine
	 */
	public ScriptEngine getEngineForExtension(final String extension) {
		if(extension==null || extension.trim().isEmpty()) throw new IllegalArgumentException("The passed extension was null");
		final String key = extension.trim().toLowerCase();
		ScriptEngine se = engines.get(key);
		if(se==null) {
			synchronized(engines) {
				se = engines.get(key);
				if(se==null) {
					try {
						se = sem.getEngineByExtension(key); 
					} catch (Exception ex) {
						throw new RuntimeException("No script engine found for extension [" + key + "]");
					}
					if(!(se instanceof Compilable)) {
						throw new RuntimeException("Script engine does not support Compilable\n" + renderEngine(se));
					}
					installScriptEngine(se);
				}
			}
		}
		return se;
	}
	
	/**
	 * Retrieves the executable deployed script for the passed source file, creating it if it does not exist
	 * @param sourceFile The source file to the the deployed script for
	 * @return the deployed script instance
	 */
	public DeployedScript<?> getDeployedScript(final String sourceFile) {
		if(sourceFile==null || sourceFile.trim().isEmpty()) throw new IllegalArgumentException("The passed source file was null or empty");
		final File f = new File(sourceFile.trim()).getAbsoluteFile();
		if(!f.exists()) throw new IllegalArgumentException("The passed file [" + f + "] does not exist");
		if(!f.isFile()) throw new IllegalArgumentException("The passed file [" + f + "] is *not* a regular file");		
		// TODO:  Check for linked files !
		try {
			return deploymentCache.get(f.getAbsolutePath(), new Callable<DeployedScript<?>>(){
				@Override
				public DeployedScript<?> call() throws Exception {
					DeploymentCompiler<?> compiler = deploymentCompilers.get(URLHelper.getFileExtension(f));
					return compiler.deploy(sourceFile);					
				}
			});
			//Cache<String, DeployedScript<?>> deploymentCache
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get deployment script for file [" + sourceFile + "]", ex);
		}
	}
	
	/**
	 * Returns the DeploymentCompiler for the passed script extension
	 * @param extension The script extension
	 * @return the associated DeploymentCompiler
	 */
	public Compilable getCompilerForExtension(final String extension) {
		if(extension==null || extension.trim().isEmpty()) throw new IllegalArgumentException("The passed extension was null");
		final String key = extension.trim().toLowerCase();
		Compilable compiler = compilers.get(key);
		if(compiler==null) {
			synchronized(compilers) {
				compiler = compilers.get(key);
				if(compiler==null) {
					final ScriptEngine se = getEngineForExtension(key);
					if(!(se instanceof Compilable)) {
						throw new RuntimeException("Script engine does not support Compilable\n" + renderEngine(se));
					}
					compiler = (Compilable)se;
					for(String ext: se.getFactory().getExtensions()) {
						String extKey = ext.trim().toLowerCase();
						if(!compilers.containsKey(extKey)) {
							compilers.put(extKey, compiler);							
						}
					}					
				}
			}
		}
		return compiler;		
	}
	
	
	/**
	 * Finds a JS script engine that works with <a href="http://mathjs.org/">math.js</a>
	 * due to a <a href="https://github.com/mozilla/rhino/issues/127">JDK Rhino issue</a>
	 * @return A JS script engine that works or null if one could not be found
	 */
	private ScriptEngine findEngine() {
		final String javaHome = URLHelper.toURL(new File(System.getProperty("java.home"))).toString().toLowerCase();		
		ScriptEngine se = null;		
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
				log.warn("Discarding engine [{}] v. [{}]", sef.getEngineName(), sef.getEngineVersion());
			}
		}
		throw new RuntimeException("No compatible script engine found. Try Nashorn, Rhino or see https://github.com/mozilla/rhino/issues/127");		
	}
	
	private void installScriptEngine(final ScriptEngine se) {
		final Set<String> addedExtensions = new HashSet<String>();
		se.setBindings(engineBindings, ScriptContext.ENGINE_SCOPE);
		for(String ext: se.getFactory().getExtensions()) {
			String extKey = ext.trim().toLowerCase();
			if(!engines.containsKey(extKey)) {
				engines.put(extKey, se);
				addedExtensions.add(extKey);
			}
		}
		log.info("Installed ScriptEngine\n{}", renderEngine(se, addedExtensions));		
	}
	
	/**
	 * Creates a new StateService
	 */
	private StateService() {
		sem = new ScriptEngineManager(getScriptClasspath());
		engineBindings = new SimpleBindings();
		engineBindings.put("stateService", this);		
		// need to get a specific JS engine
		installScriptEngine(findEngine());
		for(ScriptEngineFactory foundSef: sem.getEngineFactories()) {
			if(foundSef.getExtensions().contains("js")) continue;			
			log.info("Located Additional ScriptEngine Impl:{}", foundSef.getScriptEngine());
			for(String ext: foundSef.getExtensions()) {
				if(!engines.containsKey(ext.trim().toLowerCase())) {
					installScriptEngine(foundSef.getScriptEngine());
					break;
				}
			}
		}
		installDeploymentCompiler(new GroovyCompiler());
		loadJavaScriptHelpers();
		catchAllCompiler = new JSR223Compiler(this);
	}
	
	/**
	 * Checks for sysprop/env defined extra classpath for the script engines and installs it if found
	 * @return the class loader to use for the script engine manager
	 */
	private ClassLoader getScriptClasspath() {
		String[] paths = ArrayUtils.trim(ConfigurationHelper.getSystemThenEnvProperty(SCRIPT_CLASSPATH_PROP, "").split(","));
		if(paths.length==0) return Thread.currentThread().getContextClassLoader();
		Set<URL> urls = new HashSet<URL>();
		for(String path: paths) {
			try {
				URL url = URLHelper.toURL(path);
				if(URLHelper.resolves(url)) {
					urls.add(url);
				}
			} catch (Exception x) { /* No Op */ }
		}
		if(urls.isEmpty()) return Thread.currentThread().getContextClassLoader();
		log.info("Script Extra Classpath: {}", urls.toString());
		return new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
	}
	
	private boolean areWeJarred() {
		final String myClassPath = getClass().getProtectionDomain().getCodeSource().getLocation().toString();
		return myClassPath.toLowerCase().endsWith(".jar");
	}
	
	private void loadJavaScriptHelpers() {
		for(String fileName: JS_HELPERS) {
			log.info("Loading JS Helper [{}]", fileName);
			InputStream is = null; 
			InputStreamReader isReader = null;
			try {
				if(areWeJarred()) {
					is = getClass().getClassLoader().getResourceAsStream("/javascript/" + fileName);
				} else {
					is = new FileInputStream("./src/main/resources/javascript/" + fileName);
				}
				if(is==null) {
					log.error("Could not find JS Helper File [{}]", fileName);
					continue;
				}
				isReader = new InputStreamReader(is);
				try {
					Object c = getCompilerForExtension("js").compile(isReader); 
					log.info("Compiled [{}] to [{}]:[{}]", fileName, c.getClass().getName(), c);
				} catch (Exception ex) {
					log.info("Compilation of [{}] Failed. Using Eval", fileName);
					getEngineForExtension("js").eval(isReader);
					
				}
				
				log.info("Loaded JS Helper [{}]", fileName);
			} catch (Exception ex) {
				log.error("Failed to load JS Helper [{}]", fileName, ex);
			} finally {
				if(isReader!=null) try { isReader.close(); } catch (Exception x) {/* No Op */}
				if(is!=null) try { is.close(); } catch (Exception x) {/* No Op */}
			}
		}
		
	}
	
	/**
	 * Renders an informative string about the passed script engine
	 * @param se The ScriptEngine to inform on
	 * @return the ScriptEngine description
	 */
	public static String renderEngine(final ScriptEngine se) {
		return renderEngine(se, null);
	}
	
	
	/**
	 * Renders an informative string about the passed script engine
	 * @param se The ScriptEngine to inform on
	 * @param actualExtensions The actual installed extensions
	 * @return the ScriptEngine description
	 */
	public static String renderEngine(final ScriptEngine se, final Set<String> actualExtensions) {
		if(se==null) throw new IllegalArgumentException("The passed script engine was null");
		final ScriptEngineFactory sef = se.getFactory();
		StringBuilder b = new StringBuilder("ScriptEngine [");
		b.append("\n\tEngine:").append(sef.getEngineName()).append(" v.").append(sef.getEngineVersion());
		b.append("\n\tLanguage:").append(sef.getLanguageName()).append(" v.").append(sef.getLanguageVersion());
		b.append("\n\tExtensions:").append(actualExtensions==null ? sef.getExtensions().toString() : actualExtensions.toString());
		b.append("\n\tMIME Types:").append(sef.getMimeTypes().toString());
		b.append("\n\tShort Names:").append(sef.getNames().toString());
		return b.toString();
	}

}
