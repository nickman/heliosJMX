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

/**
 * <p>Title: LocalPortForwarderWrapperMBean</p>
 * <p>Description: JMX MBean interface for {@link LocalPortForwarderWrapper}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.LocalPortForwarderWrapperMBean</code></p>
 */

public interface LocalPortForwarderWrapperMBean {
	
	/**
	 * Indicates if the tunnel is open
	 * @return true if the tunnel is open, false otherwise
	 */
	public boolean isOpen();	
	
	/**
	 * Returns the local sshPort
	 * @return the local sshPort
	 */
	public int getLocalPort();
	
	
	/**
	 * Returns the remote sshHost name
	 * @return the remote sshHost name
	 */
	public String getHostName();


	/**
	 * Returns the remote sshHost address
	 * @return the remote sshHost address
	 */
	public String getHostAddress();

	/**
	 * Returns the remote sshPort
	 * @return the remote sshPort
	 */
	public int getRemotePort();

	
}