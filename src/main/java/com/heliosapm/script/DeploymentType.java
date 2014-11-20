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
package com.heliosapm.script;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashSet;

import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: DeploymentType</p>
 * <p>Description: Defines a deployment type that has specific handling when written to an activated hot dir</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeploymentType</code></p>
 */

public enum DeploymentType {
	/** Represents a hot (watched) directory */
	DIRECTORY(DeployedScript.HOTDIR_DOMAIN, "dir"),
	/** Represents a configuration deployment */
	CONFIG(DeployedScript.CONFIG_DOMAIN ,"config"),
	/** Represents a fixture deployment */
	FIXTURE(DeployedScript.FIXTURE_DOMAIN ,"fixture"),
	/** Represents a service deployment */	
	SERVICE(DeployedScript.SERVICE_DOMAIN ,"service"),
	/** Represents an executable script configuration deployment */
	SCRIPT(DeployedScript.DEPLOYMENT_DOMAIN, "script");  // THIS GUY ALWAYS LAST
	
	/** A cache of created deployment type file filters */
	private static final Map<String, FileFilter> filters = new ConcurrentHashMap<String, FileFilter>();
	/** The supported script extensions */
	private static final Set<String> SCRIPT_EXTENSIONS = new NonBlockingHashSet<String>();	
	/** The non script extensions */
	public static final Set<String> NON_SCRIPT_EXTENSIONS;
	/** The non script jmx domains */
	public static final Set<String> NON_SCRIPT_DOMAINS;
	
	static {
		SCRIPT_EXTENSIONS.add("js");
		SCRIPT_EXTENSIONS.add("java");
		SCRIPT_EXTENSIONS.add("groovy");
		DeploymentType[] values = DeploymentType.values();
		Set<String> tmp = new HashSet<String>(values.length-1);
		Set<String> tmp2 = new HashSet<String>(values.length-1);
		for(DeploymentType dt: values) {
			tmp.add(dt.key);
			tmp2.add(dt.jmxDomain);
		}
		NON_SCRIPT_EXTENSIONS = Collections.unmodifiableSet(tmp);
		NON_SCRIPT_DOMAINS = Collections.unmodifiableSet(tmp2);
	}
	
	
	private DeploymentType(final String jmxDomain, final String key) {
		this.jmxDomain = jmxDomain;
		this.key = key;
	}
	
	/** The JMX domain of ObjectNames for MBeans of this deployment type are registered */
	public final String jmxDomain;
	/** The extension for this deployment type */
	public final String key;
	
	/**
	 * Used by StateService to add supported script extensions
	 * @param ext the script extension to add
	 */
	static void addScriptExtension(final String ext) {
		if(ext==null || ext.trim().isEmpty()) throw new IllegalArgumentException("The passed script extension was null or empty");
		SCRIPT_EXTENSIONS.add(ext.trim().toLowerCase());
	}
	
	/**
	 * Determines if the passed ObjectName represents the passed deployment type
	 * @param deploymentType  The deployment type to determine the match against
	 * @param objectName The ObjectName to test
	 * @return true if the passed ObjectName represents the passed deployment type, false otherwise
	 */
	public static boolean isDeploymentType(final DeploymentType deploymentType, final ObjectName objectName) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(deploymentType==null) throw new IllegalArgumentException("The passed DeploymentType was null");
		if(deploymentType==DIRECTORY) return false;
		final String ext = objectName.getKeyProperty("extension");
		if(ext==null) return false;
		final String domain = objectName.getDomain();
		if(deploymentType==SCRIPT) return SCRIPT_EXTENSIONS.contains(ext) && SCRIPT.jmxDomain.equals(domain);
		return deploymentType.key.equals(ext) && deploymentType.jmxDomain.equals(domain); 
	}
	
	/**
	 * Determines if the passed file name represents an executable script
	 * @param deploymentType  The deployment type to determine the match against
	 * @param fileName The file name to test
	 * @return true if the passed file name represents the passed deployment type, false otherwise
	 */
	public static boolean isDeploymentType(final DeploymentType deploymentType, final String fileName) {
		if(fileName==null) throw new IllegalArgumentException("The passed file name was null");
		if(deploymentType==null) throw new IllegalArgumentException("The passed DeploymentType was null");
		final String ext = URLHelper.getExtension(fileName, null);
		if(ext==null) return false;
		if(deploymentType==DIRECTORY) return new File(fileName).isDirectory();
		if(deploymentType==SCRIPT) return SCRIPT_EXTENSIONS.contains(ext);
		return deploymentType.key.equals(ext); 
	}
	
	/**
	 * Determines if the passed file represents an executable script
	 * @param deploymentType The deployment type to determine the match against
	 * @param file The file to test
	 * @return true if the passed file represents the passed deployment type, false otherwise
	 */
	public static boolean isDeploymentType(final DeploymentType deploymentType, final File file) {
		if(file==null) throw new IllegalArgumentException("The passed file was null");
		if(deploymentType==null) throw new IllegalArgumentException("The passed DeploymentType was null");
		final String ext = URLHelper.getExtension(file, null);
		if(ext==null) return false;
		if(deploymentType==SCRIPT) return SCRIPT_EXTENSIONS.contains(ext);
		if(deploymentType==DIRECTORY) return file.isDirectory();
		if(deploymentType.key.equals(ext)) return true;
		return false;
//		// at this point, we would assume it's a script, but scripts are bit more complicated
//		if(deploymentType==SCRIPT && NON_SCRIPT_EXTENSIONS.contains(ext)) return false;
//		
//		final ObjectName don = JMXHelper.objectName(String.format("%s:path=*%s,extension=%s,name=%s", 
//				SCRIPT.jmxDomain, file.getParentFile().getName(), URLHelper.getExtension(file), URLHelper.getPlainFileName(file)));
//		final ObjectName[] matches = JMXHelper.query(don);
//		if(matches.length==0) return false;
//		final String absName = file.getAbsolutePath();
//		for(final ObjectName on: matches) {
//			 if(absName.equals(JMXHelper.getAttribute(on, "FileName"))) return true;
//		}		
//		return false;
	}
	
	
	/**
	 * Returns a file name filter for the passed deployment type
	 * @param deploymentTypes the deployment types to get a filter for
	 * @return a filename filter
	 */
	public static FileFilter getFilterFor(DeploymentType...deploymentTypes) {
		Set<DeploymentType> sorter = new TreeSet<DeploymentType>(Arrays.asList(deploymentTypes));
		final String key = sorter.toString();
		FileFilter ff = filters.get(key);
		if(ff==null) {
			synchronized(filters) {
				ff = filters.get(key);
				if(ff==null) {
					ff = new DeploymentTypeStackedFilter(deploymentTypes);
				}
			}
		}		
		return ff;
	}
	
	private static class DeploymentTypeStackedFilter implements FileFilter {
		/** The deployment types to filter for */
		final EnumSet<DeploymentType> deploymentTypes;
		/**
		 * Creates a new DeploymentTypeStackedFilter
		 * @param deploymentTypes The deployment types to filter for
		 */
		public DeploymentTypeStackedFilter(DeploymentType...deploymentTypes) {
			EnumSet<DeploymentType> tmp = EnumSet.noneOf(DeploymentType.class);
			if(deploymentTypes!=null) {
				tmp.addAll(Arrays.asList(deploymentTypes));
			}
			this.deploymentTypes = (EnumSet<DeploymentType>) Collections.unmodifiableSet(tmp);
		}
		
		@Override
		public boolean accept(final File file) {
			if(file==null) return false;
			for(final DeploymentType dt: deploymentTypes) {
				if(isDeploymentType(dt, file)) return true;
			}
			return false;
		}		
	}
	
}
