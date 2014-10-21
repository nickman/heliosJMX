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

import java.util.Arrays;

import ch.ethz.ssh2.ConnectionInfo;

/**
 * <p>Title: ConnectionInfoWrapper</p>
 * <p>Description: A wrapper for connection infos.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapper</code></p>
 */

public class ConnectionInfoWrapper implements ConnectionInfoWrapperMBean {
	
	/** The info delegate */
	protected final ConnectionInfo delegate;
	
	/**
	 * Creates a new ConnectionInfoWrapper
	 * @param delegate The info delegate
	 */
	public ConnectionInfoWrapper(ConnectionInfo delegate) {
		this.delegate = delegate;
	}
	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("\nConnectionInfoWrapper [");
		if (delegate.keyExchangeAlgorithm != null) {
			builder.append("\n\tkeyExchangeAlgorithm=");
			builder.append(delegate.keyExchangeAlgorithm);
			builder.append(", ");
		}
		if (delegate.clientToServerCryptoAlgorithm != null) {
			builder.append("\n\tclientToServerCryptoAlgorithm=");
			builder.append(delegate.clientToServerCryptoAlgorithm);
			builder.append(", ");
		}
		if (delegate.serverToClientCryptoAlgorithm != null) {
			builder.append("\n\tserverToClientCryptoAlgorithm=");
			builder.append(delegate.serverToClientCryptoAlgorithm);
			builder.append(", ");
		}
		if (delegate.clientToServerMACAlgorithm != null) {
			builder.append("\n\tclientToServerMACAlgorithm=");
			builder.append(delegate.clientToServerMACAlgorithm);
			builder.append(", ");
		}
		if (delegate.serverToClientMACAlgorithm != null) {
			builder.append("\n\tserverToClientMACAlgorithm=");
			builder.append(delegate.serverToClientMACAlgorithm);
			builder.append(", ");
		}
		if (delegate.serverHostKeyAlgorithm != null) {
			builder.append("\n\tserverHostKeyAlgorithm=");
			builder.append(delegate.serverHostKeyAlgorithm);
			builder.append(", ");
		}
		if (delegate.serverHostKey != null) {
			builder.append("\n\tserverHostKey=");
			builder.append(Arrays.toString(Arrays.copyOf(delegate.serverHostKey,
					Math.min(delegate.serverHostKey.length, maxLen))));
			builder.append(", ");
		}
		builder.append("\n\tkeyExchangeCounter=");
		builder.append(delegate.keyExchangeCounter);
		builder.append("\n]");
		return builder.toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getKeyExchangeAlgorithm()
	 */
	@Override
	public String getKeyExchangeAlgorithm() {
		return delegate.keyExchangeAlgorithm;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getClientToServerCryptoAlgorithm()
	 */
	@Override
	public String getClientToServerCryptoAlgorithm() {
		return delegate.clientToServerCryptoAlgorithm;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getServerToClientCryptoAlgorithm()
	 */
	@Override
	public String getServerToClientCryptoAlgorithm() {
		return delegate.serverToClientCryptoAlgorithm;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getClientToServerMACAlgorithm()
	 */
	@Override
	public String getClientToServerMACAlgorithm() {
		return delegate.clientToServerMACAlgorithm;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getServerToClientMACAlgorithm()
	 */
	@Override
	public String getServerToClientMACAlgorithm() {
		return delegate.serverToClientMACAlgorithm;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getServerHostKeyAlgorithm()
	 */
	@Override
	public String getServerHostKeyAlgorithm() {
		return delegate.serverHostKeyAlgorithm;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getServerHostKey()
	 */
	@Override
	public byte[] getServerHostKey() {
		return delegate.serverHostKey;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.remote.tunnel.ConnectionInfoWrapperMBean#getKeyExchangeCounter()
	 */
	@Override
	public int getKeyExchangeCounter() {
		return delegate.keyExchangeCounter;
	}
	

}
