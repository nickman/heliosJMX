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
	public static final String NOTIF_CONFIG_CHANGE = NOTIF_ROOT + ".configchange";	
	
	
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

}
