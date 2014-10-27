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
package com.heliosapm.jmx.remote.protocol.tunnel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.generic.MessageConnection;
import javax.management.remote.message.Message;

import com.heliosapm.jmx.remote.tunnel.TunnelState;
import com.heliosapm.jmx.remote.tunnel.WrappedStreamForwarder;

/**
 * <p>Title: SSHTunnelMessageConnection</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.protocol.tunnel.SSHTunnelMessageConnection</code></p>
 */

public class SSHTunnelMessageConnection implements MessageConnection {
	/** The buffered input stream */
	protected BufferedInputStream bis = null;
	/** The buffered output stream */
	protected BufferedOutputStream bos = null;
	
	/** The wrapped SSH tunnel stream forwarder */
	protected WrappedStreamForwarder wsf = null;
	/** The default class loader */
	protected ClassLoader defaultClassLoader;
	
	/** The state of this message connection */
	protected final AtomicReference<TunnelState> state = new AtomicReference<TunnelState>(TunnelState.UNCONNECTED); 
	
	
	/**
	 * Creates a new SSHTunnelMessageConnection
	 * @param wsf The wrapped SSH tunnel stream forwarderte
	 */
	public SSHTunnelMessageConnection(final WrappedStreamForwarder wsf) {
		this.wsf = wsf;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#connect(java.util.Map)
	 */
	@Override
	public void connect(final Map env) throws IOException {
		state.set(TunnelState.CONNECTING);
		bufferStreams();  // this will trigger the creation of the tunnel		
		if(env!=null) {
			defaultClassLoader = (ClassLoader)env.get(JMXConnectorFactory.DEFAULT_CLASS_LOADER);
		}
		state.set(TunnelState.CONNECTED);
	}
	
	/**
	 * Acquires the stream forwarder raw streams, triggering a connect if they are not initialized,
	 * and buffers them
	 * @throws IOException thrown on any IO error
	 */
	protected void bufferStreams() throws IOException {
		bis = new BufferedInputStream(wsf.getInputStream());
		bos = new BufferedOutputStream(wsf.getOutputStream());
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#readMessage()
	 */
	@Override
	public Message readMessage() throws IOException, ClassNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#writeMessage(javax.management.remote.message.Message)
	 */
	@Override
	public void writeMessage(Message msg) throws IOException {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#close()
	 */
	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub

	}
	
	/**
	 * Returns the state of this message connection
	 * @return the state of this message connection
	 */
	public TunnelState getState() {
		return state.get();
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#getConnectionId()
	 */
	@Override
	public String getConnectionId() {
		// TODO Auto-generated method stub
		return null;
	}

}
