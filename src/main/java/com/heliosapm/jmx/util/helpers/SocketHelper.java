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
package com.heliosapm.jmx.util.helpers;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Date;

/**
 * <p>Title: SocketHelper</p>
 * <p>Description: Static socket helper utility methods</p> 
 * <p>Company: Helios Develpment Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.SocketHelper</code></p>
 */

public class SocketHelper {

	/**
	 * Dumps the state of a socket
	 * @param socket The socket to dump
	 * @return the state of the socket
	 */
	public static String dump(final Socket socket) {
		if(socket==null) return "Socket [null]";
		final StringBuilder b = new StringBuilder("Socket [");
		b.append("\n\tConnected:").append(socket.isConnected());
		final SocketAddress lsa = socket.getLocalSocketAddress();
		final SocketAddress sa = socket.getRemoteSocketAddress();
		if(sa!=null && (sa instanceof InetSocketAddress)) {
			final InetSocketAddress isa = (InetSocketAddress)sa; 
			b.append("\n\tRemote Host:").append(isa.getHostString());
			b.append("\n\tRemote Port:").append(isa.getPort());
		}
		if(lsa!=null && (lsa instanceof InetSocketAddress)) {
			final InetSocketAddress isa = (InetSocketAddress)lsa; 
			b.append("\n\tLocal Iface:").append(isa.getHostString());
			b.append("\n\tLocal Port:").append(isa.getPort());
		}
		
		try { b.append("\n\tKeepAlive:").append(socket.getKeepAlive()); } catch (Exception x) {/* No Op */}
		try { b.append("\n\tReuseAddress:").append(socket.getReuseAddress()); } catch (Exception x) {/* No Op */}
		try { b.append("\n\tSoLinger:").append(socket.getSoLinger()); } catch (Exception x) {/* No Op */}
		try { b.append("\n\tSoTimeout:").append(socket.getSoTimeout()); } catch (Exception x) {/* No Op */}
		try { b.append("\n\tTCPNoDelay:").append(socket.getTcpNoDelay()); } catch (Exception x) {/* No Op */}
		b.append("\n\tInputShutdown:").append(socket.isInputShutdown());
		b.append("\n\tOutputShutdown:").append(socket.isOutputShutdown());				
		b.append("\n]");
		return b.toString();
	}
	
	public static void log(final String location, final Socket socket) {		
		System.out.println("[" + location + "/" + new Date() + "]:" + dump(socket));
	}
	
	private SocketHelper() {}

}
