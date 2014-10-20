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
package sun.net.www.protocol;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.helios.jmx.remote.tunnel.SSHTunnelConnector;
import org.helios.jmx.remote.tunnel.TunnelHandle;
import org.helios.jmx.remote.tunnel.TunnelRepository;

/**
 * <p>Title: TunnelURLConnection</p>
 * <p>Description: A {@link URLConnection} that establishes an SSH tunnel</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>sun.net.www.protocol.TunnelURLConnection</code></p>
 */

public class TunnelURLConnection extends URLConnection {
	/** The tunnel connector parsed from the URL that contains the tunnel definition */
	protected final SSHTunnelConnector tc;
	/** The tunnel handle */
	protected TunnelHandle tunnelHandle = null;
	
	/**
	 * Creates a new TunnelURLConnection
	 * @param url The tunnel URL
	 */
	public TunnelURLConnection(URL url) {
		super(url);
		tc = SSHTunnelConnector.connector(url);				
	}
	
	/**
	 * <p>Returns the tunnel handle</p>
	 * {@inheritDoc}
	 * @see java.net.URLConnection#getContent()
	 */
	@Override
	public Object getContent() throws IOException {		
		return tunnelHandle;
	}
	
	/**
	 * <p>Returns the SSH connect timeout</p>
	 * {@inheritDoc}
	 * @see java.net.URLConnection#getConnectTimeout()
	 */
	@Override
	public int getConnectTimeout() {		
		return tc.getSshConnectTimeout();
	}
	
	@Override
	public void setConnectTimeout(int timeout) {
		// TODO Auto-generated method stub
		super.setConnectTimeout(timeout);
	}

	/**
	 * {@inheritDoc}
	 * @see java.net.URLConnection#connect()
	 */
	@Override
	public void connect() throws IOException {
		if(tunnelHandle==null) {
			tunnelHandle = TunnelRepository.getInstance().tunnel(tc);
			System.out.println("[TunnelHandle]:" + tunnelHandle);
		}
	}
	
	/**
	 * Returns the URL's SSHTunnelConnector
	 * @return the SSHTunnelConnector with the decoded SSH options
	 */
	public SSHTunnelConnector getTunnelConnector() {
		return tc;
	}
	
	/**
	 * Returns the local port of the tunnel if the connection has been opened
	 * @return the local port of the tunnel
	 */
	public int getLocalPort() {
		if(tunnelHandle!=null) {
			return tunnelHandle.getLocalPort();
		}
		throw new IllegalStateException("The URLConnection is not connected");
	}
	
	

}
