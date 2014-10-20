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

import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.helios.jmx.remote.CloseListener;

import ch.ethz.ssh2.LocalPortForwarder;

/**
 * <p>Title: LocalPortForwarderWrapper</p>
 * <p>Description: A wrapper for {@link LocalPortForwarder} instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.remote.tunnel.LocalPortForwarderWrapper</code></p>
 */

public class LocalPortForwarderWrapper implements TunnelHandle, LocalPortForwarderWrapperMBean {
	/** The wrapped sshPort forwarder */
	protected final LocalPortForwarder localPortForwarder;
	
	/** The remote sshHost name */
	protected final String hostName;
	/** The remote sshHost address */
	protected final String hostAddress;
	/** The remote sshPort connected to */
	protected final int remotePort;
	/** The local sshPort tunneled to the remote sshPort */
	protected final int localPort;
	/** Indicates if the tunnel should be closed when the usage drops to zero */
	protected final boolean closeOnZeroUsage;
	/** A set of close listeners */
	protected final Set<CloseListener<LocalPortForwarderWrapper>> closeListeners = new CopyOnWriteArraySet<CloseListener<LocalPortForwarderWrapper>>();
	
	/** Flag indicating if the tunnel is open */
	protected final AtomicBoolean open = new AtomicBoolean(true);
	
	/** The usage count */
	protected final AtomicInteger usage = new AtomicInteger(0);
	
	/**
	 * Creates a new LocalPortForwarderWrapper
	 * @param localPortForwarder The wrapped sshPort forwarder
	 * @param remoteHost The remote sshHost tunneled to
	 * @param remotePort The remote sshPort tunneled to
	 * @param closeOnZeroUsage Indicates if the tunnel should be closed when the usage drops to zero
	 */
	public LocalPortForwarderWrapper(LocalPortForwarder localPortForwarder, String remoteHost, int remotePort, boolean closeOnZeroUsage) {		
		this.localPortForwarder = localPortForwarder;
		this.remotePort = remotePort;
		this.localPort = localPortForwarder.getLocalSocketAddress().getPort();
		this.closeOnZeroUsage = closeOnZeroUsage;
		try {
			InetAddress iaddr = InetAddress.getByName(remoteHost);
			hostAddress = iaddr.getHostAddress();
			hostName = iaddr.getHostName();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to resolve remote sshHost [" + remoteHost + "]", ex);
		}
	}
	
	/**
	 * Returns the keys that uniquely identify this tunnel within the scope of one connection
	 * @return an array of tunnel ids
	 */
	public String[] getKeys() {
		return new String[]{String.format("%s:%s", hostName, remotePort), String.format("%s:%s", hostAddress, remotePort)};
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
	 * Returns the unwrapped sshPort forward
	 * @return the unwrapped sshPort forward
	 */
	public LocalPortForwarder getLocalPortForwarder() {
		return localPortForwarder;
	}


	/**
	 * Returns the remote sshHost name
	 * @return the remote sshHost name
	 */
	public String getHostName() {
		return hostName;
	}


	/**
	 * Returns the remote sshHost address
	 * @return the remote sshHost address
	 */
	public String getHostAddress() {
		return hostAddress;
	}

	/**
	 * Returns the remote sshPort
	 * @return the remote sshPort
	 */
	public int getRemotePort() {
		return remotePort;
	}

	/**
	 * Returns the local sshPort
	 * @return the local sshPort
	 */
	public int getLocalPort() {
		return localPort;
	}

	/**
	 * Returns the sshPort forwarder's local socket address
	 * @return the sshPort forwarder's local socket address
	 */
	public InetSocketAddress getLocalSocketAddress() {
		return localPortForwarder.getLocalSocketAddress();
	}

	/**
	 * Indicates if the tunnel is open
	 * @return true if the tunnel is open, false otherwise
	 */
	public boolean isOpen() {
		return open.get();
	}

	/**
	 * Closes the wrapped sshPort forwarder
	 */
	public void close() {
		if(open.compareAndSet(true, false)) {
			try {
				localPortForwarder.close();
			} catch (Exception ex) {/* No Op */}
			fireClosed();
			closeListeners.clear();
		}
	}
	
	/**
	 * Registers a close listener on this tunnel
	 * @param listener the listener to be notified when this tunnel closes
	 */
	public void addCloseListener(CloseListener<LocalPortForwarderWrapper> listener) {
		if(listener!=null) {
			closeListeners.add(listener);
		}
	}
	
	/**
	 * Removes a close listener from this tunnel
	 * @param listener the listener to be removed
	 */
	public void removeCloseListener(CloseListener<LocalPortForwarderWrapper> listener) {
		if(listener!=null) {
			closeListeners.remove(listener);
		}
	}
	
	
	/**
	 * Fires the closed callback on all registered listeners
	 */
	protected void fireClosed() {
		for(CloseListener<LocalPortForwarderWrapper> listener: closeListeners) {
			listener.onClosed(this);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("LocalPortForwarderWrapper [");
		if (hostName != null) {
			builder.append("hostName=");
			builder.append(hostName);
			builder.append(", ");
		}
		if (hostAddress != null) {
			builder.append("hostAddress=");
			builder.append(hostAddress);
			builder.append(", ");
		}
		builder.append("remotePort=");
		builder.append(remotePort);
		builder.append(", localPort=");
		builder.append(localPort);
		builder.append(", closeOnZeroUsage=");
		builder.append(closeOnZeroUsage);
		builder.append(", ");
		if (open != null) {
			builder.append("open=");
			builder.append(open);
			builder.append(", ");
		}
		if (usage != null) {
			builder.append("usage=");
			builder.append(usage);
		}
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((hostAddress == null) ? 0 : hostAddress.hashCode());
		result = prime * result
				+ ((hostName == null) ? 0 : hostName.hashCode());
		result = prime * result + localPort;
		result = prime * result + remotePort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LocalPortForwarderWrapper other = (LocalPortForwarderWrapper) obj;
		if (hostAddress == null) {
			if (other.hostAddress != null)
				return false;
		} else if (!hostAddress.equals(other.hostAddress))
			return false;
		if (hostName == null) {
			if (other.hostName != null)
				return false;
		} else if (!hostName.equals(other.hostName))
			return false;
		if (localPort != other.localPort)
			return false;
		if (remotePort != other.remotePort)
			return false;
		return true;
	}
	
}
