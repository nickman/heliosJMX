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

import javax.management.ObjectName;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: StateServiceMXBean</p>
 * <p>Description: JMX MXBean definition for {@link StateService}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.StateServiceMXBean</code></p>
 */

public interface StateServiceMXBean {
	
	/** The state service JMX ObjectName */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.script:service=EngineManager");
	
	/**
	 * Returns an array of the installed engine extensions
	 * @return an array of the installed engine extensions
	 */
	public String[] getInstalledExtensions();
	
	/**
	 * Determines if the passed extension is supported by an installed engine
	 * @param extension The extension to test
	 * @return true if the passed extension is supported by an installed engine, false otherwise
	 */
	public boolean isExtensionInstalled(String extension);	
}
