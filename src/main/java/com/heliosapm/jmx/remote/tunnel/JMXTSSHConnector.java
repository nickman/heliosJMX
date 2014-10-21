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
package com.heliosapm.jmx.remote.tunnel;

import java.io.IOException;
import java.util.Map;

import javax.management.remote.JMXServiceURL;
import javax.management.remote.jmxmp.JMXMPConnector;

/**
 * <p>Title: JMXTSSHConnector</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.JMXTSSHConnector</code></p>
 */

public class JMXTSSHConnector extends JMXMPConnector {

	/**  */
	private static final long serialVersionUID = -5118225875394702489L;

	/**
	 * Creates a new JMXTSSHConnector
	 * @param address
	 * @throws IOException
	 */
	public JMXTSSHConnector(JMXServiceURL address) throws IOException {
		super(address);

	}

	/**
	 * Creates a new JMXTSSHConnector
	 * @param address
	 * @param env
	 * @throws IOException
	 */
	public JMXTSSHConnector(JMXServiceURL address, Map<?, ?> env) throws IOException {
		super(address, env);
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.JMXConnector#connect(java.util.Map)
	 */
	@Override
	public void connect(Map env) throws IOException {
		super.connect(env);
	}

	
	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.GenericConnector#connect()
	 */
	@Override
	public void connect() throws IOException {
		super.connect();
	}
}
