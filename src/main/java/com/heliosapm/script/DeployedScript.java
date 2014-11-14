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

import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

/**
 * <p>Title: DeployedScript</p>
 * <p>Description: Represents a compiled script created via JSR 233, the groovy compiler etc.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeployedScript</code></p>
 * @param <T> The type of the underlying executable script
 */

public interface DeployedScript<T> extends DeployedScriptMXBean {
	
	/** The config name for the default schedule if a deployment does not provide one */
	public static final String DEFAULT_SCHEDULE_PROP = "com.heliosapm.deployment.defaultschedule";
	/** The internal config key for the schedule */
	public static final String SCHEDULE_KEY = "schedule";	
	
	/** The default schedule if not configured */
	public static final int DEFAULT_SCHEDULE = 15;
	
	
	
	/**
	 * Returns the absolute file name of the source deployment
	 * @return the absolute file name of the source deployment
	 */
	public String getFileName();
	
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
	 * Updates the executable
	 * @param executable The executable
	 */
	public void setExecutable(final T executable);
	
	
	/**
	 * Returns the executable deployment
	 * @return the executable deployment
	 */
	public T getExecutable();
	
	/**
	 * Returns the local configuration for this deployment
	 * @return the local configuration for this deployment
	 */
	public Map<String, Object> getConfiguration();
	
	/**
	 * Adds the passed configuration
	 * @param config the incoming config
	 */
	public void addConfiguration(Map<String, Object> config);
	
	/**
	 * Adds the passed configuration
	 * @param key The config key
	 * @param value The config value
	 */
	public void addConfiguration(String key, Object value);
	
	/**
	 * Returns the config item with the passed key
	 * @param key The config key to get the config for
	 * @param type The type of the config value
	 * @return the config value or null if not found
	 */
	public <E> E getConfig(String key, Class<E> type);
	
	/**
	 * Returns the config item with the passed key
	 * @param key The config key to get the config for
	 * @return the config value or null if not found
	 */
	public Object getConfig(String key);
	
	
	/**
	 * Returns the status of the deployment
	 * @return the status of the deployment
	 */
	public DeploymentStatus getStatus();
	
	/**
	 * Executes the underlying executable script
	 * @return the execution return value
	 */
	public Object execute();
	
	/**
	 * Invokes a sub-invocable exposed by the executable
	 * @param name The name of the sub-invocable
	 * @param args The arguments to the sub-invocable
	 * @return the sub-invocable invocation return value
	 */
	public Object invoke(String name, Object...args);
	
	/**
	 * Sets the deployment's scheduled execution period
	 * @param period An integral number
	 */
	public void setSchedulePeriod(Object period);
	
	
}
