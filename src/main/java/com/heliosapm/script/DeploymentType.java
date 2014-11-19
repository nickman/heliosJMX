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
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.management.ObjectName;

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
	SCRIPT(DeployedScript.DEPLOYMENT_DOMAIN, "script");
	
	/** The non script extensions */
	public static final Set<String> NON_SCRIPT_EXTENSIONS;
	/** The non script jmx domains */
	public static final Set<String> NON_SCRIPT_DOMAINS;
	
	static {
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
	 * Determines if the passed ObjectName represents the passed deployment type
	 * @param deploymentType  The deployment type to determine the match against
	 * @param objectName The ObjectName to test
	 * @return true if the passed ObjectName represents the passed deployment type, false otherwise
	 */
	public static boolean isDeploymentType(final DeploymentType deploymentType, final ObjectName objectName) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(deploymentType==null) throw new IllegalArgumentException("The passed DeploymentType was null");
		final String ext = objectName.getKeyProperty("extension");
		if(ext==null) return false;
		final String domain = objectName.getDomain();
		if(deploymentType==SCRIPT) return NON_SCRIPT_EXTENSIONS.contains(ext) && SCRIPT.jmxDomain.equals(domain);
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
		if(deploymentType==SCRIPT) return NON_SCRIPT_EXTENSIONS.contains(ext);
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
		if(deploymentType==SCRIPT) return NON_SCRIPT_EXTENSIONS.contains(ext);
		return deploymentType.key.equals(ext); 
	}
	
	private static class DeploymentTypeStackedFilter implements FilenameFilter {
		final EnumSet<DeploymentType> deploymentTypes;
		
		
		@Override
		public boolean accept(final File dir, final String name) {

			return false;
		}
		
	}
	
	
	
	/**
	 * <p>Title: DeploymentTypeFilter</p>
	 * <p>Description: Defines filters and identifiers for {@link DeploymentType}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.DeploymentType.DeploymentTypeFilter</code></p>
	 */
	public static interface DeploymentTypeFilter {
	}
	
}
