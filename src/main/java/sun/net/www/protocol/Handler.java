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
import java.net.URLStreamHandler;

/**
 * <p>Title: Handler</p>
 * <p>Description: A {@link URLStreamHandler} supporting the tunnel encoding URL protocol</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>sun.net.www.protocol.Handler</code></p>
 */

public class Handler extends URLStreamHandler {

	/**
	 * Creates a new Handler
	 */
	public Handler() {

	}

	/**
	 * {@inheritDoc}
	 * @see java.net.URLStreamHandler#openConnection(java.net.URL)
	 */
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		return new TunnelURLConnection(u);
	}
	
	   /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandler#setURL(java.net.URL, java.lang.String, java.lang.String, int, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    protected void setURL(URL u, String protocol, String host, int port,
            String authority, String userInfo, String path,
            String query, String ref) {    	
    	if(host==null || host.trim().isEmpty()) {
    		host = "localhost";
    	}
    	if(port==-1) {
    		port = 22;
    	}
    	super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    }
    	
    
    /**
     * {@inheritDoc}
     * @see java.net.URLStreamHandler#getDefaultPort()
     */
    @Override
    protected int getDefaultPort() {
    	return 22;
    }    

}
