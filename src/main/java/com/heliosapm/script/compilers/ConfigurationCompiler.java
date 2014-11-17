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
package com.heliosapm.script.compilers;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.ConfigurationDeployedScript;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.StateService;

/**
 * <p>Title: ConfigurationCompiler</p>
 * <p>Description: A deployment "compiler" for configurations.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.ConfigurationCompiler</code></p>
 */

public class ConfigurationCompiler implements DeploymentCompiler<Map<String, Object>> {
	/** The extensions */
	private static final String[] extensions = new String[]{"config"};
	
	/** Const scripted configuration reader */
	public static final ConfigurationReader SCRIPTED_CONF_READER = new ScriptedConfigurationReader();
	/** Const json configuration reader */
	public static final ConfigurationReader JSON_CONF_READER = new JSONConfigurationReader();
	/** Const properties configuration reader */
	public static final ConfigurationReader PROPS_CONF_READER = new PropertiesConfigurationReader();
	
	/** The installed subextensions in the state service */
	private static final Set<String> subExtensions = new CopyOnWriteArraySet<String>();
	
	/** A map of readers keyed by extension */
	private static final Map<String, ConfigurationReader> readers = new LinkedHashMap<String, ConfigurationReader>(16);
	
	static {
		readers.put("properties", PROPS_CONF_READER);
		readers.put("json", JSON_CONF_READER);
		for(String ext: SCRIPTED_CONF_READER.getSubExtensions()) {
			readers.put(ext, SCRIPTED_CONF_READER);
		}		
	}
	
	/**
	 * Adds a sub extension 
	 * @param subExtension a sub extension 
	 */
	public static void addSubExtension(final String subExtension) {
		if(subExtension==null || subExtension.trim().isEmpty()) throw new IllegalArgumentException("The passed sub extension was null or empty");
		subExtensions.add(subExtension);
		readers.put(subExtension, SCRIPTED_CONF_READER);
	}
	
	/**
	 * Creates a new ConfigurationCompiler
	 */
	public ConfigurationCompiler() {
		
	}
	
	/** Dot splitter */
	private static final Pattern DOT_SPLITTER = Pattern.compile("\\.");

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#compile(java.net.URL)
	 */
	@Override
	public Map<String, Object> compile(final URL source) throws CompilerException {
		final String sourceCode = URLHelper.getTextFromURL(source);
		final String[] segments = DOT_SPLITTER.split(URLHelper.getFileName(source));		
		final String subExtension = segments.length <=2 ? null : segments[segments.length-2].trim().toLowerCase();
		Map<String, Object> configMap = null;
		if(subExtension!=null) {
			ConfigurationReader cr = readers.get(subExtension);
			if(cr==null) throw new RuntimeException("The config source [" + source + "] has an unsupported subextension [" + subExtension + "]");
			configMap = cr.readConfig(sourceCode, subExtension);
			if(configMap==null) throw new RuntimeException("Failed to compile config source [" + source + "]");
			return configMap;
		}
		for(Map.Entry<String, ConfigurationReader> entry: readers.entrySet()) {
			configMap = entry.getValue().readConfig(sourceCode, subExtension);
			if(configMap!=null) return configMap;
		}
		throw new CompilerException("Failed to compile config file [" + source + "]", "", new Throwable());
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#deploy(java.lang.String)
	 */
	@Override
	public DeployedScript<Map<String, Object>> deploy(final String sourceFile) throws CompilerException {
		if(sourceFile==null) throw new IllegalArgumentException("The passed source file was null");
		final Map<String, Object> map = compile(URLHelper.toURL(sourceFile));		
		return new ConfigurationDeployedScript(new File(sourceFile), map);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#getSupportedExtensions()
	 */
	@Override
	public String[] getSupportedExtensions() {
		return extensions;
	}
	
	
	/**
	 * <p>Title: ConfigurationReader</p>
	 * <p>Description: Defines a configuration reader</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader</code></p>
	 */
	public static interface ConfigurationReader {
		/**
		 * Reads the content from the passed URL, parses it and returns the collected config map
		 * If the parse or load fails, returns null
		 * @param source The config source
		 * @param subExtension The sub extension, if one was provided, for readers that support multiple extensions 
		 * @return The configuration map or null if the configuration could not be read
		 */
		public Map<String, Object> readConfig(String source, String subExtension);
		/**
		 * Returns the sub extensions supported by this reader
		 * @return the sub extensions supported by this reader
		 */
		public String[] getSubExtensions();
	}
	
	/**
	 * <p>Title: PropertiesConfigurationReader</p>
	 * <p>Description: ConfigurationReader for properties files</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.ConfigurationCompiler.PropertiesConfigurationReader</code></p>
	 */
	public static class PropertiesConfigurationReader implements ConfigurationReader {
		private static final String[] subexts = new String[] {"properties"};

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#readConfig(java.lang.String, java.lang.String)
		 */
		@Override
		public Map<String, Object> readConfig(final String source, final String subExtension) {
			try {
				Properties p = new Properties();
				p.load(new StringReader(source));
				Map<String, Object> map = new HashMap<String, Object>(p.size());
				for(final String key: p.stringPropertyNames()) {
					map.put(key.trim(), makeNumber(p.getProperty(key)));
				}
				return map;			
			} catch (Exception ex) {
				return null;
			}
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#getSubExtensions()
		 */
		@Override
		public String[] getSubExtensions() {
			return subexts.clone();
		}

		/**
		 * Tests the passed string to see if it is a number and returns
		 * either a long or a double. Otherwise returns a string.
		 * @param value The value to test
		 * @return a long, a double or a string (never null)
		 */
		protected Object makeNumber(final String value) {
			if(value==null || value.trim().isEmpty()) return "";
			try {
				Double d = new Double(value.trim());
				if(value.indexOf('.')==-1) {
					return d.longValue();
				}
				return d.doubleValue();
			} catch (Exception ex) {
				return value.trim();
			}
		}
	}
	
	/**
	 * <p>Title: JSONConfigurationReader</p>
	 * <p>Description: ConfigurationReader for JSON files</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.ConfigurationCompiler.JSONConfigurationReader</code></p>
	 */
	public static class JSONConfigurationReader implements ConfigurationReader {
		private static final String[] subexts = new String[] {"json"};

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#readConfig(java.lang.String, java.lang.String)
		 */
		@Override
		public Map<String, Object> readConfig(final String source, final String subExtension) {
			try {
				final JSONObject json = new JSONObject(source);		
				final JSONArray keys = json.names();
				if(keys==null) throw new Exception("Parsed JSON, but had no values");
				final int sz = keys.length();
				Map<String, Object> map = new HashMap<String, Object>(sz);
				for(int i = 0; i < sz; i++) {
					final String key = keys.getString(i);
					map.put(key.trim(), json.get(key));
				}
				return map;
			} catch (Exception ex) {
				return null;
			}
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#getSubExtensions()
		 */
		@Override
		public String[] getSubExtensions() {
			return subexts.clone();
		}
	}
	
	/**
	 * <p>Title: ScriptedConfigurationReader</p>
	 * <p>Description: ConfigurationReader for JSR233 scripted files</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.ConfigurationCompiler.ScriptedConfigurationReader</code></p>
	 */
	public static class ScriptedConfigurationReader implements ConfigurationReader {

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#readConfig(java.lang.String, java.lang.String)
		 */
		@Override
		public Map<String, Object> readConfig(final String source, final String subExtension) {
			final String[] subs = subExtension==null ? getSubExtensions() : new String[] {subExtension};
			for(final String extension: subs) {
				try {
					ScriptEngine se = StateService.getInstance().getEngineForExtension(extension);
					se.eval(source);
					Bindings bindings = se.getBindings(ScriptContext.ENGINE_SCOPE);
					Map<String, Object> map = new HashMap<String, Object>(bindings.size());
					for(String key: bindings.keySet()) {
						map.put(key.trim(), bindings.get(key));
					}
					return map;
				} catch (Exception ex) {
					continue;
				}				
			}
			return null;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.compilers.ConfigurationCompiler.ConfigurationReader#getSubExtensions()
		 */
		@Override
		public String[] getSubExtensions() {
			return subExtensions.toArray(new String[subExtensions.size()]);
		}
	}
	
	

}
