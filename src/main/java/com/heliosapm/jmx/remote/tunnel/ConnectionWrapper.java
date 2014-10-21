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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.ConnectionInfo;
import ch.ethz.ssh2.ConnectionMonitor;
import ch.ethz.ssh2.DHGexParameters;
import ch.ethz.ssh2.InteractiveCallback;
import ch.ethz.ssh2.LocalPortForwarder;
import ch.ethz.ssh2.LocalStreamForwarder;
import ch.ethz.ssh2.SCPClient;
import ch.ethz.ssh2.ServerHostKeyVerifier;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.auth.AgentProxy;
import ch.ethz.ssh2.transport.ClientTransportManager;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.remote.CloseListener;
import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: ConnectionWrapper</p>
 * <p>Description: Indexable wrapper for connections and tunnels</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.ConnectionWrapper</code></p>
 */

public class ConnectionWrapper implements ConnectionWrapperMBean, Closeable, ConnectionMonitor, CloseListener<LocalPortForwarderWrapper> {
	/** The connection to wrap */
	final Connection connection;
	/** The sshHost name */
	private final String host;
	/** The sshPort */
	private final int port;
	/** The socket */
	private final Socket socket;
	/** Indicates if the tunnel should be closed when the usage drops to zero */
	protected final boolean closeOnZeroUsage;
	
	/** A map of tunnels keyed by keyed by <b><code>&lt;local-sshPort&gt;:&lt;remote-sshHost&gt;:&lt;remote-sshPort&gt;</code> */
	protected final Map<String, LocalPortForwarderWrapper> tunnels = new ConcurrentHashMap<String, LocalPortForwarderWrapper>();
	
	/** A set of close listeners */
	protected final Set<CloseListener<ConnectionWrapper>> closeListeners = new CopyOnWriteArraySet<CloseListener<ConnectionWrapper>>();
	/** The usage count */
	protected final AtomicInteger usage = new AtomicInteger(0);
	
	private static final SLogger LOG = SimpleLogger.logger(ConnectionWrapper.class);
	
	/** The JMX ObjectName this connection is registered as */
	final ObjectName objectName;
	
	
	/** The connection's transport manager field name */
	public static final String TRANSPORT_MANAGER_FIELD_NAME = "tm";
	/** The transport manager's socket field name */
	public static final String SOCKET_FIELD_NAME = "sock";
	
	/** An empty connection useful as a placeholder so we don't jam up the caches when synchronizing */
	public static final ConnectionWrapper EMPTY_CONNECTION = new ConnectionWrapper(); 

	
	/** The connection's transport manager field  */
	protected static final Field connectionTransportMgr;
	/** The transport manager's socket field */
	protected static final Field sock;
	
	static {
		try {
			connectionTransportMgr = Connection.class.getDeclaredField(TRANSPORT_MANAGER_FIELD_NAME);
			connectionTransportMgr.setAccessible(true);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
		try {
			sock = ClientTransportManager.class.getDeclaredField(SOCKET_FIELD_NAME);
			sock.setAccessible(true);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}		
	}
	
	
	/**
	 * Creates a new empty placeholder ConnectionWrapper
	 */
	private ConnectionWrapper() {
		socket = null;
		port = -1;
		host = null;
		connection = null;
		closeOnZeroUsage = false;
		objectName = null;
	}
	
	
	/**
	 * Pattern logger
	 * @param format The pattern format
	 * @param args The pattern values
	 * @return the formatted message 
	 */
	public static String log(Object format, Object...args) {
		String msg = String.format("[TestServers]" + format.toString(),args);
		System.out.println(msg);
		return msg;
	}		
	
	/**
	 * Creates a new ConnectionWrapper
	 * @param connection The connection to wrap
	 * @param closeOnZeroUsage  Indicates if the tunnel should be closed when the usage drops to zero 
	 */
	public ConnectionWrapper(Connection connection, boolean closeOnZeroUsage) {
		this.connection = connection;
		host = connection.getHostname();
		port = connection.getPort();
		this.closeOnZeroUsage = closeOnZeroUsage;
		try {
			final Object o = connectionTransportMgr.get(connection);
			if(o instanceof ClientTransportManager) {
				ClientTransportManager ctm = (ClientTransportManager)o;
				socket = (Socket)sock.get(ctm);
				objectName = JMXHelper.objectName(new StringBuilder(getClass().getPackage().getName()).append(":")
						.append("service=").append("SSHConnection")
						.append(",host=").append(getHostname())
						.append(",port=").append(getPort())
				);
				JMXHelper.registerMBean(this, objectName);
			} else {
				// this only happens if an older version of ganymed snuck into the classpath
				log("CTM Class Source: %s", o.getClass().getProtectionDomain().getCodeSource().getLocation());
				socket = null;
				objectName = null;
			}
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get connection socket", ex);
		}
	}
	
	/**
	 * Creates a new tunnel
	 * @param sshHost The sshHost to tunnel to
	 * @param sshPort The sshPort to tunnel to
	 * @return the tunnel handle
	 */
	public TunnelHandle tunnel(String host, int port) {
		return tunnel(host, port, 0);
	}
	
	
	/**
	 * Creates a new tunnel
	 * @param sshHost The sshHost to tunnel to
	 * @param sshPort The sshPort to tunnel to
	 * @param localPort the local sshPort
	 * @return the tunnel handle
	 */
	@SuppressWarnings("resource")
	public TunnelHandle tunnel(String host, int port, int localPort) {
		if(host==null || host.trim().isEmpty()) throw new IllegalArgumentException("Host was null or empty");
		if(port < 1 || port > 65534) throw new IllegalArgumentException("Port number [" + port + "] out of range");
		if(port < 0 || port > 65534) throw new IllegalArgumentException("Local Port number [" + port + "] out of range");
		String address = getAliases(host)[0];
		final String key = String.format("%s:%s", address, port);
		LocalPortForwarderWrapper localPortForwarderWrapper = tunnels.get(key);
		if(localPortForwarderWrapper==null) {
			try {
				LocalPortForwarder localPortForwarder = connection.createLocalPortForwarder(localPort, address, port);
				localPortForwarderWrapper = new LocalPortForwarderWrapper(localPortForwarder, address, port, closeOnZeroUsage);
				localPortForwarderWrapper.addCloseListener(this);
				tunnels.put(key, localPortForwarderWrapper);
			} catch (Exception ex) {
				throw new RuntimeException("Failed to create tunnel [" + String.format("%s:%s:%s", localPort, host, port) + "]", ex);
			}			
		}
		final ConnectionWrapper self = this;
		final LocalPortForwarderWrapper lpfw = localPortForwarderWrapper;
		this.incrUsage();
		lpfw.incrUsage();
		return lpfw;
//		return new TunnelHandle() {
//			@Override
//			public void close() throws IOException {
//				lpfw.decrUsage();
//				self.decrUsage();					
//			}
//			@Override
//			public int getLocalPort() {
//				return lpfw.getLocalPort();
//			}
//		};
		
	}
	
	/**
	 * Returns the address and sshHost name for the passed string
	 * @param hostName The sshHost string or address to resolve
	 * @return a string array with the address and sshHost name
	 */
	public static String[] getAliases(String hostName) {
		try {
			InetAddress ia = InetAddress.getByName(hostName);
			return new String[] {ia.getHostAddress(), ia.getHostName()};
		} catch (Exception ex) {
			throw new RuntimeException("Failed to resolve sshHost [" + hostName + "]", ex);
		}
	}
	
	/**
	 * Increments the usage
	 * @return the new usage
	 */
	public int incrUsage() {
		return usage.incrementAndGet();
	}
	
	/**
	 * Decrements the usage
	 * @return the new usage
	 */
	public int decrUsage() {
		int u = usage.decrementAndGet();
		if(u < 1 && closeOnZeroUsage) {
			close();
		}
		return u;
	}
	
	/**
	 * Returns the current usage
	 * @return the current usage
	 */
	public int getUsage() {
		return usage.get();
	}
	
	
	/**
	 * Indicates if the connection is open and authenticated
	 * @return true if the connection is open and authenticated, false otherwise
	 */
	public boolean isOpen() {
		return connection!=null && connection.isAuthenticationComplete();
	}
	
	/**
	 * @return
	 */
	public Set<LocalPortForwarderWrapperMBean> getLocalPortForwarders() {
		return new HashSet<LocalPortForwarderWrapperMBean>(tunnels.values());
	}


	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() {
		if(isOpen()) {
			try {
				connection.close();
			} catch (Exception ex) {/* No Op */}
		}	
		try {
			JMXHelper.unregisterMBean(objectName);
		} catch (Exception ex) {/* No Op */}
	}
	
	/**
	 * Registers a close listener on this connection
	 * @param listener the listener to be notified when this connection closes
	 */
	public void addCloseListener(CloseListener<ConnectionWrapper> listener) {
		if(listener!=null) {
			closeListeners.add(listener);
		}
	}
	
	/**
	 * Removes a close listener from this connection
	 * @param listener the listener to be removed
	 */
	public void removeCloseListener(CloseListener<ConnectionWrapper> listener) {
		if(listener!=null) {
			closeListeners.remove(listener);
		}
	}
	
	/**
	 * Adds a {@link ConnectionMonitor} to the wrapped connection
	 * @param cmon the connection monitor to add
	 */
	public void addConnectionMonitor(ConnectionMonitor cmon) {
		if(cmon!=null) connection.addConnectionMonitor(cmon);
	}
	
    /**
     * Remove a {@link ConnectionMonitor} from the wrapped connection.
     * @param cmon the connection monitor to add
     * @return whether the monitor could be removed
     */
    public boolean removeConnectionMonitor(ConnectionMonitor cmon) {
    	if(cmon!=null) return connection.removeConnectionMonitor(cmon);
    	return false;
    }
	
	/**
	 * Fires the closed callback on all registered listeners
	 */
	protected void fireClosed() {
		for(CloseListener<ConnectionWrapper> listener: closeListeners) {
			listener.onClosed(this);
		}
	}

	@Override
	public void connectionLost(Throwable reason) {
		log("Connection to [%s:%s] closed. Reason was: %s", host, port, reason);
		if(reason!=null) {
			reason.printStackTrace(System.err);
		}
		int closedTunnels = 0;
		for(LocalPortForwarderWrapper lpfw: tunnels.values()) {
			try { lpfw.close(); } catch (Exception x) {/* No Op */}
			closedTunnels++;
		}
		tunnels.clear();
		LOG.log("\n\t===============================\n\tCleared [%s] Tunnels\n\t===============================\n", closedTunnels);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.CloseListener#onClosed(java.io.Closeable)
	 */
	@Override
	public void onClosed(LocalPortForwarderWrapper closeable) {		
		for(String key: closeable.getKeys()) {
			tunnels.remove(key);
		}
	}

	/**
	 * @return
	 */
	public String getHostname() {
		return connection.getHostname();
	}



	public boolean authenticateWithDSA(String user, String pem, String password)
			throws IOException {
		return connection.authenticateWithDSA(user, pem, password);
	}

	public boolean authenticateWithKeyboardInteractive(String user,
			InteractiveCallback cb) throws IOException {
		return connection.authenticateWithKeyboardInteractive(user, cb);
	}

	public boolean authenticateWithKeyboardInteractive(String user,
			String[] submethods, InteractiveCallback cb) throws IOException {
		return connection.authenticateWithKeyboardInteractive(user, submethods,
				cb);
	}

	public boolean authenticateWithAgent(String user, AgentProxy proxy)
			throws IOException {
		return connection.authenticateWithAgent(user, proxy);
	}

	public boolean authenticateWithPassword(String user, String password)
			throws IOException {
		return connection.authenticateWithPassword(user, password);
	}

	public boolean authenticateWithNone(String user) throws IOException {
		return connection.authenticateWithNone(user);
	}

	public boolean authenticateWithPublicKey(String user, char[] pemPrivateKey,
			String password) throws IOException {
		return connection.authenticateWithPublicKey(user, pemPrivateKey,
				password);
	}

	public boolean authenticateWithPublicKey(String user, File pemFile,
			String password) throws IOException {
		return connection.authenticateWithPublicKey(user, pemFile, password);
	}

	public void close(Throwable t, boolean hard) {
		connection.close(t, hard);
	}

	public ConnectionInfo connect() throws IOException {
		return connection.connect();
	}

	public ConnectionInfo connect(ServerHostKeyVerifier verifier)
			throws IOException {
		return connection.connect(verifier);
	}

	public ConnectionInfo connect(ServerHostKeyVerifier verifier,
			int connectTimeout, int kexTimeout) throws IOException {
		return connection.connect(verifier, connectTimeout, kexTimeout);
	}

	/**
	 * Creates a LocalPortForwarder
	 * @param local_port The port of the local side of the tunnel
	 * @param host_to_connect The host to tunnel to
	 * @param port_to_connect The port on the remote side being tunneled to
	 * @return a handle to the local port forwarder
	 * @throws IOException thrown on any IO error
	 */
	public TunnelHandle localPortForwarder(int local_port,
			String host_to_connect, int port_to_connect) throws IOException {
		return tunnel(host_to_connect, port_to_connect, local_port);
	}

	/**
	 * Creates a LocalPortForwarder
	 * @param addr The local socket to bind the tunnel to
	 * @param host_to_connect The host to tunnel to
	 * @param port_to_connect The port on the remote side being tunneled to
	 * @return a handle to the local port forwarder
	 * @throws IOException thrown on any IO error
	 */
	public TunnelHandle localPortForwarder(InetSocketAddress addr,
			String host_to_connect, int port_to_connect) throws IOException {
		return tunnel(host_to_connect, port_to_connect, addr.getPort());
	}
	
	
	
	
	
	
	

	public LocalStreamForwarder createLocalStreamForwarder(
			String host_to_connect, int port_to_connect) throws IOException {
		return connection.createLocalStreamForwarder(host_to_connect,
				port_to_connect);
	}

	public SCPClient createSCPClient() throws IOException {
		return connection.createSCPClient();
	}

	public void forceKeyExchange() throws IOException {
		connection.forceKeyExchange();
	}

	public int getPort() {
		return connection.getPort();
	}

	public ConnectionInfo getConnectionInfo() throws IOException {
		return connection.getConnectionInfo();
	}

	public String[] getRemainingAuthMethods(String user) throws IOException {
		return connection.getRemainingAuthMethods(user);
	}

	public boolean isAuthenticationComplete() {
		return connection.isAuthenticationComplete();
	}

	public boolean isAuthenticationPartialSuccess() {
		return connection.isAuthenticationPartialSuccess();
	}

	public boolean isAuthMethodAvailable(String user, String method)
			throws IOException {
		return connection.isAuthMethodAvailable(user, method);
	}

	public Session openSession() throws IOException {
		return connection.openSession();
	}

	public void sendIgnorePacket() throws IOException {
		connection.sendIgnorePacket();
	}

	public void sendIgnorePacket(byte[] data) throws IOException {
		connection.sendIgnorePacket(data);
	}

	public void setClient2ServerCiphers(String[] ciphers) {
		connection.setClient2ServerCiphers(ciphers);
	}

	public void setClient2ServerMACs(String[] macs) {
		connection.setClient2ServerMACs(macs);
	}

	public void setDHGexParameters(DHGexParameters dgp) {
		connection.setDHGexParameters(dgp);
	}

	public void setServer2ClientCiphers(String[] ciphers) {
		connection.setServer2ClientCiphers(ciphers);
	}

	public void setServer2ClientMACs(String[] macs) {
		connection.setServer2ClientMACs(macs);
	}

	public void setServerHostKeyAlgorithms(String[] algos) {
		connection.setServerHostKeyAlgorithms(algos);
	}

	public void setTCPNoDelay(boolean enable) throws IOException {
		connection.setTCPNoDelay(enable);
	}

	public void requestRemotePortForwarding(String bindAddress, int bindPort,
			String targetAddress, int targetPort) throws IOException {
		connection.requestRemotePortForwarding(bindAddress, bindPort,
				targetAddress, targetPort);
	}

	public void cancelRemotePortForwarding(int bindPort) throws IOException {
		connection.cancelRemotePortForwarding(bindPort);
	}

	public void setSecureRandom(SecureRandom rnd) {
		connection.setSecureRandom(rnd);
	}
	
	
	
}
