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
package org.helios.jmx.remote.tunnel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.helios.jmx.remote.InetAddressCache;
import org.helios.opentsdb.TSDBSubmitter;
import org.helios.ssh.terminal.CommandTerminal;
import org.helios.ssh.terminal.WrappedSession;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;

import sun.net.www.protocol.TunnelURLConnection;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.ConnectionMonitor;
import ch.ethz.ssh2.LocalPortForwarder;
import ch.ethz.ssh2.Session;

/**
 * <p>Title: TunnelRepository</p>
 * <p>Description: A repository of open SSH tunneling connections</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.remote.tunnel.TunnelRepository</code></p>
 */

public class TunnelRepository {
	/** The singleton instance */
	private static volatile TunnelRepository instance = null;
	/** The singleton ctor lock */
	private static final Object lock = new Object();
	
	/** A map of open tunnel connections keyed by <b><code>&lt;address&gt;:&lt;remote-sshPort&gt;</code></b> */
	private final Map<String, ConnectionWrapper> connectionsByAddress = new ConcurrentHashMap<String, ConnectionWrapper>();
	/** A map of open tunnels keyed by <b><code>&lt;address&gt;:&lt;local-Port&gt;:&lt;remote-jmxPort&gt;</code></b> */
	private final Map<String, LocalPortForwarderWrapper> tunnelsByAddressWithLocal = new ConcurrentHashMap<String, LocalPortForwarderWrapper>();
	/** A map of open tunnels keyed by <b><code>&lt;address&gt;:&lt;remote-jmxPort&gt;</code></b> */
	private final Map<String, LocalPortForwarderWrapper> tunnelsByAddress = new ConcurrentHashMap<String, LocalPortForwarderWrapper>();
	
	private static final SLogger LOG = SimpleLogger.logger(TunnelRepository.class);
	
	/**
	 * Acquires the TunnelRepository singleton instance
	 * @return the TunnelRepository singleton instance
	 */
	public static TunnelRepository getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new TunnelRepository();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Closes all connections
	 */
	public void purge() {
		for(ConnectionWrapper cw: connectionsByAddress.values()) {
			cw.close();
		}
		connectionsByAddress.clear();
		tunnelsByAddressWithLocal.clear();
		tunnelsByAddress.clear();
//		LOG.log("Remaining Connections: %s", connectionsByAddress.size());
//		LOG.log("Remaining Tunnels: %s", tunnelsByAddress.size() + tunnelsByAddressWithLocal.size());
	}
	
	/**
	 * Creates, opens and returns a command terminal
	 * @param sshUrl An SSH URL defining the connection parameters
	 * @return a command terminal through which command line directives can be issued
	 */
	public CommandTerminal openCommandTerminal(final URL sshUrl) {
		if(sshUrl==null) throw new IllegalArgumentException("The passed SSH URL was null");
		try {
			final SSHTunnelConnector sshConnector = new TunnelURLConnection(sshUrl).getTunnelConnector();
			final String host = sshConnector.getSSHHost();
			final int port = sshConnector.getSSHPort();
			String[] aliases = InetAddressCache.getInstance().getAliases(host);
			final String key = String.format("%s:%s", aliases[0], port);		
			ConnectionWrapper cw = connectionsByAddress.get(key);
			if(cw==null) {
				synchronized(connectionsByAddress) {
					cw = connectionsByAddress.get(key);
					if(cw==null) {
						connectionsByAddress.put(key, ConnectionWrapper.EMPTY_CONNECTION);
					}
				}
			}
			if(cw==null) {
				try {
					cw = _connect(sshConnector, true);
					connectionsByAddress.put(key, cw);
				} catch (Exception ex) {
					connectionsByAddress.remove(key);
					throw ex;
				}
			}
			final Session session = cw.openSession();
			return new WrappedSession(session, cw).openCommandTerminal();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to open command terminal for SSH URL [" + sshUrl + "]", ex);
		}
	}
	
//	/**
//	 * Creates a new tunnel
//	 * @param sshHost The sshHost to bridge through, defaults to the target jmxHost
//	 * @param sshPort The sshPort to bridge through, defaults to 22
//	 * @param jmxHost The JMX connector endpoint host
//	 * @param jmxPort The JMX connector endpoint port
//	 * @param localPort The local port. If zero is passed, a port will be auto assigned
//	 * @return a tunnel handle used to close the tunnel and that provides the local port assignment
//	 */
	/**
	 * Creates or acquires a new tunnel
	 * @param tunnelConnector The tunnel connector
	 * @return a handle to the tunnel
	 */
	@SuppressWarnings("resource")
	public TunnelHandle tunnel(SSHTunnelConnector tunnelConnector) {
		String jmxHost = tunnelConnector.getJmxConnectorHost();
		int jmxPort = tunnelConnector.getJmxConnectorPort();
		int localPort = tunnelConnector.getLocalPort();		
		LocalPortForwarderWrapper tunnel = null;
		final String key;
		if(localPort < 1) {
			localPort = 0;
			key = String.format("%s:%s", jmxHost, jmxPort);
			tunnel = tunnelsByAddress.get(key);
			if(tunnel == null) {
				synchronized(tunnelsByAddress) {
					tunnelsByAddress.get(key);
					if(tunnel == null) {
						ConnectionWrapper cw = _connect(tunnelConnector);
						try {
							
							LocalPortForwarder lpw = cw.connection.createLocalPortForwarder(localPort, jmxHost, jmxPort); 
							tunnel = new LocalPortForwarderWrapper(
									lpw,
									jmxHost, jmxPort, true
							);
							localPort = lpw.getLocalSocketAddress().getPort();
							LOG.log("Created Tunnel [%s:%s:%s]", tunnel.getLocalPort(), jmxHost, jmxPort);
							registerTunnel(tunnel);
						} catch (IOException e) {
							throw new RuntimeException("Failed to tunnel to [" + jmxHost + ":" + jmxPort + "]", e);
						}
					}
				}
			}
		} else {
			key = String.format("%s:%s:%s", jmxHost, localPort, jmxPort);
			tunnel = tunnelsByAddressWithLocal.get(key);
			if(tunnel == null) {
				synchronized(tunnelsByAddressWithLocal) {
					tunnelsByAddressWithLocal.get(key);
					if(tunnel == null) {
						ConnectionWrapper cw = _connect(tunnelConnector);
						try {
							tunnel = new LocalPortForwarderWrapper(
									cw.connection.createLocalPortForwarder(localPort, jmxHost, jmxPort),
									jmxHost, jmxPort, true
							);
							registerTunnel(tunnel);
						} catch (IOException e) {
							throw new RuntimeException("Failed to tunnel to [" + jmxHost + ":" + jmxPort + "] with local port [" + localPort + "]", e);
						}
					}
				}
			}
		}		
		return tunnel;
	}
	
	
	
	
	
	/**
	 * Registers a newly established connection
	 * @param conn the connection to register
	 */
	protected void registerConnection(ConnectionWrapper conn) {
		connectionsByAddress.put(addressKey(conn), conn);
		conn.addConnectionMonitor(new ConnectionMonitor() {
			@Override
			public void connectionLost(Throwable reason) {
				// TODO Auto-generated method stub
				
			}
		});
	}
	
	/**
	 * Registers a new tunnel
	 * @param tunnel the tunnel to register
	 */
	protected void registerTunnel(LocalPortForwarderWrapper tunnel) {
		tunnelsByAddress.put(String.format("%s:%s", tunnel.getHostAddress(), tunnel.getRemotePort()), tunnel);
		tunnelsByAddressWithLocal.put(String.format("%s:%s:%s", tunnel.getHostAddress(), tunnel.getLocalPort(), tunnel.getRemotePort()), tunnel);
		LOG.log("Created tunnel [%s:%s  ----->   %s:%s]", tunnel.getLocalSocketAddress().getAddress(), tunnel.getLocalPort(), tunnel.getHostName(), tunnel.getRemotePort());
	}
	
	
	/**
	 * Determines if the repo has an open connection to this remote socket
	 * @param host The remote sshHost name or address
	 * @param port The remote sshPort
	 * @return true if the connection exists
	 */
	public boolean hasConnectionFor(String host, int port) {
		String[] aliases = InetAddressCache.getInstance().getAliases(host);
		String key = String.format("%s:%s", aliases[0], port);
		return connectionsByAddress.containsKey(key); 
	}
	
	/**
	 * Determines if an open connection exists for the passed tunnel connector
	 * @param tunnelConnector The tunnel connector specifying the requested connection
	 * @return true if a connection as specified exists, false otherwise
	 */
	public boolean hasConnectionFor(SSHTunnelConnector tunnelConnector) {
		return getConnectionFor(tunnelConnector)!=null;
	}
	
	/**
	 * Returns a connection for the passed tunnel connector if one has been created
	 * @param tunnelConnector The tunnel connector specifying the connection
	 * @return the ConnectionWrapper or null if one was not found
	 */
	protected ConnectionWrapper getConnectionFor(SSHTunnelConnector tunnelConnector) {
		return connectionsByAddress.get(String.format("%s:%s", tunnelConnector.getSSHHost(), tunnelConnector.getSSHPort())); 
	}
	
	
	/**
	 * Creates a connection for the passed tunnel connector if the specified connection does not exist
	 * @param tunnelConnector the connector specifying the target sshHost and sshPort to connect to
	 */
	public void connect(SSHTunnelConnector tunnelConnector) {
		if(!hasConnectionFor(tunnelConnector)) {
			synchronized(connectionsByAddress) {
				if(!hasConnectionFor(tunnelConnector)) {
					Connection connection = tunnelConnector.connectAndAuthenticate();
					ConnectionWrapper cw = new ConnectionWrapper(connection, true);
					registerConnection(cw);
				}
			}
		}
	}
	
	/**
	 * Acquires a connection for the passed tunnel connector, creating one if one does not exist. Will not force cache.
	 * @param tunnelConnector The tunnel connector specifying the connection
	 * @return a connection
	 */
	protected ConnectionWrapper _connect(final SSHTunnelConnector tunnelConnector) {
		return _connect(tunnelConnector, false);
	}
	
	
	/**
	 * Acquires a connection for the passed tunnel connector, creating one if one does not exist
	 * @param tunnelConnector The tunnel connector specifying the connection
	 * @param nocache If true, replaces any connection found in cache
	 * @return a connection
	 */
	@SuppressWarnings("resource")
	protected ConnectionWrapper _connect(final SSHTunnelConnector tunnelConnector, final boolean nocache) {
		if(nocache) {
			return _rawConnect(tunnelConnector);
		}
		ConnectionWrapper cw = getConnectionFor(tunnelConnector);
		if(cw==null) {
			synchronized(connectionsByAddress) {
				cw = getConnectionFor(tunnelConnector);
				if(cw==null) {
					cw = _rawConnect(tunnelConnector);
				}
			}
		}
		return cw;
	}
	
	private ConnectionWrapper _rawConnect(final SSHTunnelConnector tunnelConnector) {
		Connection connection = tunnelConnector.connectAndAuthenticate();
		ConnectionWrapper cw = new ConnectionWrapper(connection, true);
		registerConnection(cw);
		return cw;
		
	}
	
	
	
	/**
	 * Determines if the repo has an open tunnel to this remote socket and the local sshPort
	 * @param host The remote sshHost name or address
	 * @param port The remote sshPort
	 * @param localPort The local sshPort
	 * @return true if the tunnel exists
	 */
	public boolean hasTunnelFor(String host, int port, int localPort) {
		try {
			String name = InetAddress.getByName(host).getHostAddress();
			String key = String.format("%s:%s:%s", name, localPort, port);
			return tunnelsByAddressWithLocal.containsKey(key);
		} catch (UnknownHostException e) {
			return false;
		}		
	}
	
//	public boolean hasTunnelFor(SSHTunnelConnector tunnelConnector) {
//		if(tunnelConnector.getLocalPort()==0) {
//			
//		} else {
//			return hasTunnelFor(tunnelConnector.getSSHHost(), tunnelConnector.getSSHPort(), tunnelConnector.getLocalPort()); 
//		}
//		
//	}
	
	
	
	
	/**
	 * Generates a sshHost name key for a connection
	 * @param conn The connection to get a key for
	 * @return the key
	 */
	public static String nameKey(Connection conn) {
		try {
			String name = InetAddress.getByName(conn.getHostname()).getHostName();
			return String.format("%s:%s", name, conn.getPort());
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	/**
	 * Generates an address name key for a connection
	 * @param conn The connection to get a key for
	 * @return the key
	 */
	public static String addressKey(ConnectionWrapper conn) {
		try {
			String address = InetAddress.getByName(conn.getHostname()).getHostAddress();
			return String.format("%s:%s", address, conn.getPort());
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	/**
	 * Generates a sshHost name key for a tunnel
	 * @param conn The connection that established the tunnel
	 * @param localPort The local sshPort of the tunnel
	 * @return the key
	 */
	public static String nameKey(Connection conn, int localPort) {
		try {
			String name = InetAddress.getByName(conn.getHostname()).getHostName();
			return String.format("%s:%s:%s", name, localPort, conn.getPort());
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	/**
	 * Generates an address name key for a tunnel
	 * @param conn The connection to get a key for
	 * @param localPort The local sshPort of the tunnel
	 * @return the key 
	 */
	public static String addressKey(Connection conn, int localPort) {
		try {
			String address = InetAddress.getByName(conn.getHostname()).getHostAddress();
			return String.format("%s:%s:%s", address, localPort, conn.getPort());
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	
	private TunnelRepository() {
		
	}
}
