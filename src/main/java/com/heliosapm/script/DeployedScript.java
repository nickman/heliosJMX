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
import java.util.concurrent.Callable;

import javax.management.ObjectName;

import com.heliosapm.jmx.config.Configuration;
import com.heliosapm.jmx.execution.ExecutionSchedule;

/**
 * <p>Title: DeployedScript</p>
 * <p>Description: Represents a compiled script created via JSR 233, the groovy compiler etc.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.DeployedScript</code></p>
 * @param <T> The type of the underlying executable script
 */

public interface DeployedScript<T> extends DeployedScriptMXBean, Callable<T> {
	
	/** The config name for the default schedule if a deployment does not provide one */
	public static final String DEFAULT_SCHEDULE_PROP = "com.heliosapm.deployment.defaultschedule";
	/** The internal config key for the schedule */
	public static final String SCHEDULE_KEY = "schedule";
	/** The internal config key for the timeout */
	public static final String TIMEOUT_KEY = "timeout";	
	
	/** The jmx notification type root */
	public static final String NOTIF_ROOT = "heliosapm.deployment";	

	/** The jmx notification type for a status change in the deployment */
	public static final String NOTIF_STATUS_CHANGE = NOTIF_ROOT + ".statechange";	
	/** The jmx notification type for a config change in the deployment */
	public static final String NOTIF_CONFIG_CHANGE = NOTIF_ROOT + ".configchange";	
	/** The jmx notification type for a config change in the deployment */
	public static final String NOTIF_RECOMPILE = NOTIF_ROOT + ".recompile";	
	/** The jmx notification type for a config change in a configuration source */
	public static final String NOTIF_CONFIG_MOD = NOTIF_ROOT + ".config.change";
	/** The jmx notification type for a new configuration source registration */
	public static final String NOTIF_CONFIG_NEW = NOTIF_ROOT + ".config.new";	
	
	/** The default schedule if not configured */
	public static final int DEFAULT_SCHEDULE = 15;
	
	/** The JMX domain for configurations */
	public static final String CONFIG_DOMAIN = "com.heliosapm.configuration";
	/** The JMX domain for fixtures */
	public static final String FIXTURE_DOMAIN = "com.heliosapm.fixture";
	/** The JMX domain for services */
	public static final String SERVICE_DOMAIN = "com.heliosapm.service";
	/** The JMX domain for deployments */
	public static final String DEPLOYMENT_DOMAIN = "com.heliosapm.deployment";
	/** The JMX domain for hot directories */
	public static final String HOTDIR_DOMAIN = "com.heliosapm.hotdir";
	
	/** The binding name for the binding */
	public static final String BINDING_NAME = "_binding_";
	
	/**
	 * Indicates if the passed text is a comment line for this script's language
	 * @param text The text to test
	 * @return true if the passed text is a comment line for this script's language, false otherwise
	 */
	public boolean isCommentLine(String text);
	
	/**
	 * Updates the executable
	 * @param executable The executable
	 * @param checksum The checksum of the source file
	 * @param lastModified The last modified timestamp of the deployment
	 */
	public void setExecutable(final T executable, long checksum, long lastModified);
	
	/**
	 * Performs any initialization required on a new executable
	 */
	public void initExcutable();
	
	/**
	 * Marks the deployment as broken
	 * @param errorMessage The compilation error message
	 * @param checksum The [broken] source checksum
	 * @param lastModified The [broken] source last modification timestamp
	 */
	public void setFailedExecutable(final String errorMessage, long checksum, long lastModified);
	
	
	/**
	 * Returns the executable deployment
	 * @return the executable deployment
	 */
	public T getExecutable();
	
	/**
	 * Returns the local configuration for this deployment
	 * @return the local configuration for this deployment
	 */
	public Configuration getConfiguration();
	
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
	
//	/**
//	 * Triggers a config reload when a config item this deployment depends on changes
//	 * @param dependency The JMX ObjectName of the config item this deployment depends on
//	 * @param changedConfig The new config
//	 */
//	public void triggerConfigChange(final ObjectName dependency, final Map<String, Object> changedConfig);
	
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
	 * Makes an invocable call with no arguments and the result (if not null) is string converted
	 * @param name The name of the invocable
	 * @return the return value
	 */
	public String callInvocable(String name);
	
	
	/**
	 * Returns the execution schedule for this deployment
	 * @return the execution schedule for this deployment
	 */
	public ExecutionSchedule getExecutionSchedule();
	
	/**
	 * Activates the passed execution schedule for this depoyment
	 * @param newSchedule the new execution schedule for this depoyment
	 */
	public void setExecutionSchedule(final ExecutionSchedule newSchedule);
	
	/**
	 * Returns the JMX ObjectName of the configuration watched by this object
	 * @return the JMX ObjectName of the configuration watched by this object
	 */
	public ObjectName getWatchedConfiguration();
	


	
	
}
