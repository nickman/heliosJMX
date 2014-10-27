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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import ch.ethz.ssh2.LocalStreamForwarder;

/**
 * <p>Title: WrappedStreamForwarder</p>
 * <p>Description: A wrapped local stream forwarder</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.WrappedStreamForwarder</code></p>
 */

public class WrappedStreamForwarder {
	/** The underlying stream forwarder */
	protected volatile LocalStreamForwarder lsf = null;
	/** The JMXServiceURL that the forwarder is associated to */
	protected JMXServiceURL serviceURL = null;
	/** The JMX client encironment */
	protected Map<String, Object> env = null;
	
//	/** The underlying connection */
//	protected ConnectionWrapper conn = null;
	
	/**
	 * Creates a new WrappedStreamForwarder
	 * @param serviceURL The JMXServiceURL describing the stream forwarder required for the connection
	 * @param env The JMX client environment
	 */
	public WrappedStreamForwarder(final JMXServiceURL serviceURL, final Map<String, Object> env) {
		if(serviceURL==null) throw new IllegalArgumentException("The passed JMXServiceURL was null");
		if(!"tunnel".equals(serviceURL.getProtocol())) throw new IllegalArgumentException("The passed JMXServiceURL (" + serviceURL +  ") had an invalid protocol [" + serviceURL.getProtocol() + "]");
		this.serviceURL = serviceURL;
		this.env = env;
	}

	/**
	 * @return
	 * @throws IOException
	 * @see ch.ethz.ssh2.LocalStreamForwarder#getInputStream()
	 */
	public InputStream getInputStream() throws IOException {
		if(lsf==null) {
			synchronized(this) {
				if(lsf==null) {
					lsf = TunnelRepository.getInstance().streamForward(serviceURL, env);
				}
			}
		}
		return lsf.getInputStream();
	}

	/**
	 * @return
	 * @throws IOException
	 * @see ch.ethz.ssh2.LocalStreamForwarder#getOutputStream()
	 */
	public OutputStream getOutputStream() throws IOException {
		if(lsf==null) {
			synchronized(this) {
				if(lsf==null) {
					lsf = TunnelRepository.getInstance().streamForward(serviceURL, env);
				}
			}
		}
		return lsf.getOutputStream();
	}

	/**
	 * @throws IOException
	 * @see ch.ethz.ssh2.LocalStreamForwarder#close()
	 */
	public void close() throws IOException {
		try { lsf.close(); } catch (Exception ex) {/* No Op */}
		lsf = null;
	}

	/**
	 * @return
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return String.format("LocalStreamForwarder to [service:jmx:%s://%s:%s]", serviceURL.getProtocol(), serviceURL.getHost(), serviceURL.getPort());
	}

}
