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
package com.heliosapm.script.configuration;

import java.util.Map;

import com.heliosapm.script.DeployedScriptMXBean;

/**
 * <p>Title: DeployedConfigurationMXBean</p>
 * <p>Description: JMX MXBean interface for deployed configurations</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.configuration.DeployedConfigurationMXBean</code></p>
 */

public interface DeployedConfigurationMXBean extends DeployedScriptMXBean {
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

}
