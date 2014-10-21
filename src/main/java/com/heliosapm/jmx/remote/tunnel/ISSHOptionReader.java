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
package com.heliosapm.jmx.remote.tunnel;

import java.util.Map;

import javax.management.remote.JMXServiceURL;

/**
 * <p>Title: ISSHOptionReader</p>
 * <p>Description: Defines an SSHOption reader</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.ISSHOptionReader</code></p>
 * @param <T> The expected type of the SSH option value
 */

public interface ISSHOptionReader<T> {

	
	/**
	 * Returns the value for the passed key or the supplied default if a value is not found
	 * @param env The JMX environment map or decoded url arguments that may have the encoded key/value pairs
	 * @param key The SSHOption key
	 * @param defaultValue The default value if they key cannot be decoded
	 * @return the option value or the default
	 */
	public T getOption(Map env, String key, T defaultValue);
	
	/**
	 * Inspects the system properties and environmental variables and 
	 * returns the value for the passed key or the supplied default if a value is not found
	 * @param key The SSHOption key
	 * @param defaultValue The default value if they key cannot be decoded
	 * @return the option value or the default
	 */
	public T getOption(String key, T defaultValue);
	
	public Object getRawOption(String key, Object defaultValue);
	
	public T convert(String value, T defaultValue);


	
	
}
