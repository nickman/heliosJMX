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
package com.heliosapm.snmp;

import java.io.IOException;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

/**
 * <p>Title: TrapReceiver</p>
 * <p>Description: Testing Trap receiver</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.snmp.TrapReceiver</code></p>
 */
///** Instance logger */
//protected final Logger log = LoggerFactory.getLogger(getClass());

public class TrapReceiver implements CommandResponder {
	
	public static void main(String[] args) {
		TrapReceiver snmp4jTrapReceiver = new TrapReceiver();
		try {
			snmp4jTrapReceiver.listen(new UdpAddress("localhost/162"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Trap Listner
	 */
	public synchronized void listen(TransportIpAddress address) throws IOException {
		AbstractTransportMapping transport;
		if (address instanceof TcpAddress) {
			transport = new DefaultTcpTransportMapping((TcpAddress) address);
		} else {
			transport = new DefaultUdpTransportMapping((UdpAddress) address);
		}

		ThreadPool threadPool = ThreadPool.create("DispatcherPool", 10);
		MessageDispatcher mDispathcher = new MultiThreadedMessageDispatcher(
				threadPool, new MessageDispatcherImpl());

		// add message processing models
		mDispathcher.addMessageProcessingModel(new MPv1());
		mDispathcher.addMessageProcessingModel(new MPv2c());

		// add all security protocols
		SecurityProtocols.getInstance().addDefaultProtocols();
		SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());

		// Create Target
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));

		Snmp snmp = new Snmp(mDispathcher, transport);
		snmp.addCommandResponder(this);

		transport.listen();
		System.out.println("Listening on " + address);

		try {
			this.wait();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * This method will be called whenever a pdu is received on the given port
	 * specified in the listen() method
	 */
	public synchronized void processPdu(CommandResponderEvent cmdRespEvent) {
		System.out.println("Received PDU...");
		PDU pdu = cmdRespEvent.getPDU();
		if (pdu != null) {
			System.out.println("Trap Type = " + pdu.getType());
			System.out.println("Variables = " + pdu.getVariableBindings());
		}		
	}
}