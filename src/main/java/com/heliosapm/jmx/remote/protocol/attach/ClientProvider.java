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
package com.heliosapm.jmx.remote.protocol.attach;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

/**
 * <p>Title: ClientProvider</p>
 * <p>Description: JMXConnector provider for acquiring an MBeanServer connection 
 * through the <a href="http://docs.oracle.com/javase/7/docs/technotes/guides/attach/index.html">Java Attach API</a>.</p>
 * <p>The {@link JMXServiceURL} syntax is one of the following:<ul>
 * 	<li><b><code>service:jmx:attach://&lt;PID&gt;</code></b> where the PID is the Java virtual machine ID or usually, the OS process ID.</li>
 *  <li><b><code>service:jmx:attach:///&lt;DISPLAY NAME&gt;</code></b> where the DISPLAY NAME is an expression matching the Java virtual machine's display name. or a single display name matching regex.</li>
 *  <li><b><code>service:jmx:attach:///&lt;[REGEX]&gt;</code></b> where the REGEX is a regular expression that will match one and only one JVM's display name</li>
 * </ul></p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>org.helios.jmx.remote.protocol.attach.ClientProvider</code></p>
 */

public class ClientProvider implements JMXConnectorProvider {
	/** The protocol name */
	public static final String PROTOCOL_NAME = "attach";

	/** The URL prefix */
	public static final String URL_PREFIX = "://";
	
    /**
     * {@inheritDoc}
     * @see javax.management.remote.JMXConnectorProvider#newJMXConnector(javax.management.remote.JMXServiceURL, java.util.Map)
     */
    public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map environment) throws IOException {
		if (!serviceURL.getProtocol().equals(PROTOCOL_NAME)) {
			throw new MalformedURLException("Protocol not [" + PROTOCOL_NAME + "]: " +
						    serviceURL.getProtocol());
		}
		String s = serviceURL.toString();
		int index = s.indexOf(URL_PREFIX);
		return new AttachJMXConnector(s.substring(index + URL_PREFIX.length()));
    }


}
