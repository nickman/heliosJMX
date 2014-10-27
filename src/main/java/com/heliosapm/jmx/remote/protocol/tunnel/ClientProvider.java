/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

import com.heliosapm.jmx.remote.CloseListener;
import com.heliosapm.jmx.remote.protocol.WrappedJMXConnector;
import com.heliosapm.jmx.remote.tunnel.LocalPortForwarderWrapper;
import com.heliosapm.jmx.remote.tunnel.SSHTunnelConnector;
import com.heliosapm.jmx.remote.tunnel.TunnelHandle;
import com.heliosapm.jmx.util.helpers.ClassSwapper;
import com.heliosapm.jmx.util.helpers.ReconnectorService;

/**
 * <p>Title: ClientProvider</p>
 * <p>Description: JMX Remoting Client Provider for the tunnel protocol</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.protocol.tunnel.ClientProvider</code></p>
 */
public class ClientProvider implements JMXConnectorProvider {
	
	/** The protocol name */
	public static final String PROTOCOL_NAME = "tunnel";
	
	private static boolean INSTR = false;
	
	public static final String TIMEOUT_FOR_CONNECTED_STATE =
			"jmx.remote.x.client.connected.state.timeout";	

    /**
     * {@inheritDoc}
     * @see javax.management.remote.JMXConnectorProvider#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
     */
    @SuppressWarnings({ "unchecked", "resource" })
	public JMXConnector newJMXConnector(final JMXServiceURL serviceURL, final Map environment) throws IOException {
		if (!serviceURL.getProtocol().equals(PROTOCOL_NAME)) {
		    throw new MalformedURLException("Protocol not [" + PROTOCOL_NAME + "]: " +
						    serviceURL.getProtocol());
		}
		Map newenv = SSHTunnelConnector.tunnel(serviceURL, environment);
		newenv.put(TIMEOUT_FOR_CONNECTED_STATE, 2000);
		final TunnelHandle th = (TunnelHandle)newenv.remove("TunnelHandle");
        final JMXConnector connector = WrappedJMXConnector.addressable(JMXConnectorFactory.newJMXConnector((JMXServiceURL)newenv.remove("JMXServiceURL"), newenv), serviceURL);
        
        
        final NotificationListener closeListener = new NotificationListener() {
        	@Override
        	public void handleNotification(final Notification n, final Object handback) {
        		final String type = n.getType();
        		if(JMXConnectionNotification.CLOSED.equals(type) || JMXConnectionNotification.FAILED.equals(type)) {
        			((LocalPortForwarderWrapper)th).close();
        			System.err.println("Connection [" + clean(serviceURL) + "] Closed. Closing Tunnel [" + th + "].....");
        			if(!INSTR) {
        				try {
        					ClassSwapper.getInstance().swapIn(com.sun.jmx.remote.socket.InstrumentedSocketConnection.class);
        					System.out.println("\n\t============\n\tInstrumented JMX Socket\n\t============");
        					INSTR = true;
        				} catch (Exception x) {
        					x.printStackTrace(System.err);
        				}
        			}
        		}
        	}
        };
        
        ((LocalPortForwarderWrapper)th).addCloseListener(new CloseListener<LocalPortForwarderWrapper>(){
        	public void onClosed(LocalPortForwarderWrapper closeable) {
        		try {
        			System.err.println("PortForward [" + closeable + "] Closed. Closing Connector [" + connector.getConnectionId() + "].....");
        			try { connector.removeConnectionNotificationListener(closeListener); } catch (Exception x) {/* No Op */}
					connector.close();
				} catch (IOException x) { /* No Op */}
        	}
        });
        connector.addConnectionNotificationListener(closeListener, null, null);
        ReconnectorService.getInstance().autoReconnect(connector, serviceURL, true, null);
        return connector;
    }
    
	/**
	 * Returns a "clean" JMXServiceURL representation so we don't reveal anything we shouldn't
	 * @param serviceURL The JMXServiceURL to clean
	 * @return the cleaned string
	 */
	protected String clean(final JMXServiceURL serviceURL) {
		return String.format("service:jmx:%s://%s:%s/", serviceURL.getProtocol(), serviceURL.getHost(), serviceURL.getPort());
	}
    
}
