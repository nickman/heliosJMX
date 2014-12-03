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

/**
 * <p>Title: InternalConfigurationListener</p>
 * <p>Description: Defines a listener that an executable deployment will register with it's own {@link Configuration} to listen 
 * on granular configuration changes.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.config.InternalConfigurationListener</code></p>
 */

public interface InternalConfigurationListener {
	/**
	 * Fired when a configuration item changes
	 * @param key The key of the changed configuration item
	 * @param value The string value of the changed configuration item
	 */
	public void onConfigurationItemChange(String key, String value);
	
	/**
	 * Fired when the dependency readiness state of the configuration changes
	 * @param ready true if ready, false otherwise
	 * @param message A readiness message. Not relevant when dependencies are ready,
	 * should list the pending dependency keys if not ready.
	 */
	public void onDependencyReadinessChange(boolean ready, String message);
	
	
}
