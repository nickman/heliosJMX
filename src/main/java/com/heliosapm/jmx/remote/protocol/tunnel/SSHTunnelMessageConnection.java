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

import static com.heliosapm.jmx.remote.tunnel.TunnelState.CONNECTED;
import static com.heliosapm.jmx.remote.tunnel.TunnelState.TERMINATED;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.management.ManagementFactory;
import java.security.Principal;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.generic.MessageConnection;
import javax.management.remote.message.Message;

import com.heliosapm.jmx.remote.tunnel.TunnelState;
import com.heliosapm.jmx.remote.tunnel.WrappedStreamForwarder;
import com.sun.jmx.remote.generic.DefaultConfig;

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
	/** The buffered object input stream */
	private ObjectInputStream oin = null;
	/** The buffered object output strea, */
	private ObjectOutputStream oout;
	
	/** The wrapped SSH tunnel stream forwarder */
	protected WrappedStreamForwarder wsf = null;
	/** The default class loader */
	protected ClassLoader defaultClassLoader;
	
	/** The state of this message connection */
	protected final AtomicReference<TunnelState> state = new AtomicReference<TunnelState>(TunnelState.UNCONNECTED); 
	/** The time to wait for a connected state in ms. */
	private long  waitConnectedState = 1000;
	
	private final String defaultConnectionId = "Uninitialized connection id";
	
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
		waitConnectedState = DefaultConfig.getTimeoutForWaitConnectedState(env);
		
		state.set(TunnelState.CONNECTING);			
		if(env!=null) {
			defaultClassLoader = (ClassLoader)env.get(JMXConnectorFactory.DEFAULT_CLASS_LOADER);
		}
		bufferStreams();  // this will trigger the creation of the tunnel
		state.set(TunnelState.CONNECTED);
		checkState();
	}
	
	/**
	 * Acquires the stream forwarder raw streams, triggering a connect if they are not initialized,
	 * and buffers them
	 * @throws IOException thrown on any IO error
	 */
	protected void bufferStreams() throws IOException {
		bis = new BufferedInputStream(wsf.getInputStream());
		bos = new BufferedOutputStream(wsf.getOutputStream());
		oin = new ObjectInputStreamWithLoader(bis, defaultClassLoader);
		oout = new ObjectOutputStream(bos);
	}
	
	protected void checkState() {
	    if (state.get() == CONNECTED) {
	    	return;
	    } else if (state.get() == TERMINATED) {
	    	throw new IllegalStateException("The connection has been closed.");
	    }
	    final long waitingTime = waitConnectedState;	    
	    TunnelState.waitForState(state, waitingTime, CONNECTED, TERMINATED);
	    if (state.get() == CONNECTED) {
	    	return;
	    }
	    close();
		throw new IllegalStateException("The connection is not currently established.");
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#readMessage()
	 */
	@Override
	public Message readMessage() throws IOException, ClassNotFoundException {
		checkState();
		return (Message) oin.readObject();
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#writeMessage(javax.management.remote.message.Message)
	 */
	@Override
	public void writeMessage(final Message msg) throws IOException {
		checkState();
		oout.writeObject(msg);
		oout.flush();
		oout.reset();
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#close()
	 */
	@Override
	public void close() {
		state.set(TERMINATED);
		try { wsf.close(); } catch (Exception x) {/* No Op */}
		bis = null;
		bos = null;
		oin = null;
		oout = null;
	}
	
	/**
	 * Returns the state of this message connection
	 * @return the state of this message connection
	 */
	public TunnelState getState() {
		return state.get();
	}

	
	private static final String NODEID = ManagementFactory.getRuntimeMXBean().getName();
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.remote.generic.MessageConnection#getConnectionId()
	 */
	@Override
	public String getConnectionId() {
		if(state.get() != CONNECTED) {
			return defaultConnectionId;
		}
		final JMXServiceURL address = wsf.getAddress();
		StringBuilder buf = new StringBuilder("tunnel://");		
		buf.append(address.getHost()).append(":").append(address.getPort());
		buf.append("[").append(NODEID).append("]");
		buf.append(" ").append(System.identityHashCode(this));
		return buf.toString();
	}
	
	/**
	 * <p>Title: ObjectInputStreamWithLoader</p>
	 * <p>Description: Classloader enabled object inout stream</p>
	 * <p>Copied from: {@link com.sun.jmx.remote.socket.SocketConnection}</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.remote.protocol.tunnel.SSHTunnelMessageConnection.ObjectInputStreamWithLoader</code></p>
	 */
	private static class ObjectInputStreamWithLoader extends ObjectInputStream {
		
		/**
		 * Creates a new ObjectInputStreamWithLoader
		 * @param in The underlying input stream
		 * @param cl The specified class loader
		 * @throws IOException thrown on any IO errors
		 */
		public ObjectInputStreamWithLoader(InputStream in, ClassLoader cl) throws IOException {
			super(in);
			this.cloader = cl;
		}

		protected Class<?> resolveClass(ObjectStreamClass aClass) throws IOException, ClassNotFoundException {
			return cloader == null ? super.resolveClass(aClass) : Class.forName(aClass.getName(), false, cloader);
		}

		private final ClassLoader cloader;
	}
	

}
