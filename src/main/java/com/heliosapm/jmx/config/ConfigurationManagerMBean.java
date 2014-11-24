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
package com.heliosapm.jmx.config;

import javax.management.ObjectName;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: ConfigurationManagerMBean</p>
 * <p>Description: Standard MBean interface for the {@link ConfigurationManager}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.ConfigurationManagerMBean</code></p>
 */

public interface ConfigurationManagerMBean {
	/** The ConfigurationManager JMX ObjectName */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.configuration:service=ConfigurationManager");
	
	
	/**
	 * Returns the configuration map of the configuration MBean with the passed ObjectName
	 * @param configMBean The ObjectName of the configuration MBean 
	 * @return the configuration map
	 */
	public Configuration getConfig(ObjectName configMBean);
	
	/**
	 * Returns the number of confuiguration deployments registered
	 * @return the number of confuiguration deployments registered
	 */
	public int getConfigurationCount();
	
	/**
	 * Returns the number of processed config updates 
	 * @return the configUpdateCount
	 */
	public long getConfigUpdateCount();
	
	/**
	 * Returns a string of all managed config properties
	 * @return a string of all managed config properties
	 */
	public String printAllConfig();
}
