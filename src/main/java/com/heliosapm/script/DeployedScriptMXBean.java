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

import java.util.Date;
import java.util.Map;

import javax.management.ObjectName;

/**
 * <p>Title: DeployedScriptMXBean</p>
 * <p>Description: The base JMX MXBean interface for all deployments</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeployedScriptMXBean</code></p>
 */

public interface DeployedScriptMXBean {

	/**
	 * Returns the deployment domain for this deployment
	 * @return the deployment domain for this deployment
	 */
	public String getDomain();	

	/**
	 * Returns the unqualified name of the deployment with no extensions
	 * @return the unqualified name of the deployment with no extensions
	 */
	public String getShortName();
	
	/**
	 * Returns the version of this deployment.
	 * The version starts at <b><code>1</code></b> and is incremented each
	 * time the script is hot updated.
	 * @return the version of this deployment
	 */
	public int getVersion();
	
	/**
	 * Returns the last modified timestamp of the script's underlying source file
	 * @return the last modified timestamp of the script's underlying source file
	 */
	public long getLastModified();

	/**
	 * Returns the JMX ObjectName of the configuration MBean that this deployment should listen on for changes
	 * @return the JMX ObjectName for the watched configuration MBean
	 */
	public ObjectName getWatchedConfiguration();
	
	/**
	 * Returns the last modified date of the script's underlying source file
	 * @return the last modified date of the script's underlying source file
	 */
	public Date getLastModifiedDate();
	
	/**
	 * Returns the checksum of the source
	 * @return the checksum
	 */
	public long getChecksum();
	
	/**
	 * Returns the deployment class name
	 * @return the deployment class name
	 */
	public String getDeploymentClassName();

	/**
	 * Returns the last set status message
	 * @return the last set status message
	 */
	public String getStatusMessage();	

	/**
	 * Returns the absolute file name of the source deployment
	 * @return the absolute file name of the source deployment
	 */
	public String getFileName();

	/**
	 * Returns the absolute file name of the source deployment linked file source
	 * @return the absolute file name of the source deployment linked file source
	 * or null if the source file is not linked
	 */
	public String getLinkedFileName();	
	
	/**
	 * Returns the root watched directory for this file
	 * @return the root watched directory for this file
	 */
	public String getRoot();
	
	/**
	 * Returns the path segments of this file's root watched directory down to this file's directory
	 * @return the path segments 
	 */
	public String[] getPathSegments();	
	
	/**
	 * Returns the file extension
	 * @return the file extension
	 */
	public String getExtension();
	
	/**
	 * Returns the local configuration for this deployment
	 * @return the local configuration for this deployment
	 */
	public Map<String, String> getConfigurationMap();
	
	/**
	 * Returns the parent configuration for this deployment
	 * @return the parent configuration for this deployment
	 */
	public Map<String, String> getParentConfigurationMap();
	
	/**
	 * Adds the passed configuration
	 * @param key The config key
	 * @param value The config value
	 */
	public void addConfiguration(String key, String value);
	
	/**
	 * Returns the config item as a string with the passed key
	 * @param key The config key to get the config for
	 * @return the config value or null if not found
	 */
	public String getConfigString(String key);

	/**
	 * Returns the deployment's script source code in bytes
	 * @return the deployment's script source code in bytes
	 */
	public byte[] returnSourceBytes();
	
	/**
	 * Returns the deployment's script source code as a string
	 * @return the deployment's script source code as a string
	 */
	public String returnSource();
	
	
	/**
	 * Returns the timestamp of the last modification
	 * @return the timestamp of the last modification
	 */
	public long getLastModTime();
	
	/**
	 * Returns this deployment's calculated ObjectName
	 * @return the deployment's ObjectName
	 */
	public ObjectName getObjectName();
	
	/**
	 * Returns a JSON rendering of this deployment
	 * @return a JSON rendering of this deployment
	 */
	public String toJSON();

	/**
	 * Writes this new source the originating source file.
	 * The header will be flagged to tell the file watcher to skip if recompile is enabled.
	 * @param source The source to write
	 * @param recompile If true, will recompile the deployment
	 */
	public void updateSource(String source, boolean recompile);
	
	/**
	 * Undeploys this deployment
	 */
	public void undeploy();

	/**
	 * Returns the path segments for this depoyment with the specified number of segments
	 * removed from the left (if negative) or the right (if positive) or un-modified ( if zero )
	 * @param trim The segment trim modifier
	 * @return The path segments, trimmed as defined
	 */
	public String[] getPathSegments(int trim);
	
	
	
}
