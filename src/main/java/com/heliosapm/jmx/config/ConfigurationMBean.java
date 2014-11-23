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
package com.heliosapm.jmx.config;

import java.util.Set;

/**
 * <p>Title: ConfigurationMBean</p>
 * <p>Description: JMX MBean interface for {@link Configuration} instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.ConfigurationMBean</code></p>
 */

public interface ConfigurationMBean {
	
	/** The jmx notification type root */
	public static final String NOTIF_ROOT = "com.heliosapm.jmx.config";
	/** The jmx notification type for a config change, dispatched once per config changing load */
	public static final String NOTIF_CONFIG_CHANGE = NOTIF_ROOT + ".attr.load";	
	/** The jmx notification type for a config attribute change */
	public static final String NOTIF_CONFIG_ATTR_CHANGE = NOTIF_ROOT + ".attr.change";
	/** The jmx notification type for the all dependencies satisfied event */
	public static final String NOTIF_ALL_DEPS_OK = NOTIF_ROOT + ".dep.ok";
	
	
	
	/**
	 * Returns the number of items in the configuration
	 * @return the number of items in the configuration
	 */
	public int size();
	
	/**
	 * Returns the value for the passed key
	 * @param key The config key
	 * @return The config value or null if no value was bound for the key
	 */
	public String get(final String key);
	
	/**
	 * Inserts or updates a configuration item.
	 * If this operation changes the config, will fire listeners and notifications
	 * @param key The config item key
	 * @param value The config item value
	 */
	public void put(String key, String value);	
	
	/**
	 * Determines if the named dependency has been satisfied
	 * @param key The key of the dependency
	 * @return true if the named dependency has been satisfied, false otherwise
	 */
	public <T> boolean isDependencyClosed(final String key);
	
	/**
	 * Returns the dependency keys
	 * @return the dependency keys
	 */
	public Set<String> getDependencyKeys();
	
	/**
	 * Returns the pending dependency keys
	 * @return the pending dependency keys
	 */
	public Set<String> getPendingDependencyKeys();
	

}
