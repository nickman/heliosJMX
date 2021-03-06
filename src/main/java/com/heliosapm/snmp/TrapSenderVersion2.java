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

/**
 * <p>Title: TrapSenderVersion2</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.snmp.TrapSenderVersion2</code></p>
 */

import java.util.Date;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class TrapSenderVersion2 {

	public static final String community = "public";

	// Sending Trap for sysLocation of RFC1213
	public static final String Oid = ".1.3.6.1.2.1.1.8";

	//IP of Local Host
	public static final String ipAddress = "127.0.0.1"; //"10.230.13.67";

	//Ideally Port 162 should be used to send receive Trap, any other available Port can be used
	public static final int port = 163;

	public static void main(String[] args) {
		TrapSenderVersion2 trapV2 = new TrapSenderVersion2();
		trapV2.sendTrap_Version2();
	}
	/**
	 * This methods sends the V1 trap to the Localhost in port 162
	 */
	public void sendTrap_Version2() {
		try {
			// Create Transport Mapping
			TransportMapping transport = new DefaultUdpTransportMapping();
			transport.listen();

			// Create Target
			CommunityTarget cTarget = new CommunityTarget();
			cTarget.setCommunity(new OctetString(community));
			cTarget.setVersion(SnmpConstants.version2c);
			cTarget.setAddress(new UdpAddress(ipAddress + "/" + port));
			cTarget.setRetries(2);
			cTarget.setTimeout(5000);

			// Create PDU for V2
			PDU pdu = new PDU();

			// need to specify the system up time
			pdu.add(new VariableBinding(SnmpConstants.sysUpTime,
					new OctetString(new Date().toString())));
			pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OID(
					Oid)));
			pdu.add(new VariableBinding(SnmpConstants.snmpTrapAddress,
					new IpAddress(ipAddress)));

			pdu.add(new VariableBinding(new OID(Oid), new OctetString(
					"Major")));
			pdu.setType(PDU.NOTIFICATION);
			pdu.add(new VariableBinding(new OID(".1.3.6.1.2.1.1.9"), new OctetString("Hello World !")));
			
			
			OID JOID  = new OID("jvmLowMemoryPoolUsageNotif");
			pdu.add(new VariableBinding(JOID, new Counter64(Long.MAX_VALUE)));
			

			// Send the PDU
			Snmp snmp = new Snmp(transport);
			System.out.println("Sending V2 Trap... Check Wheather NMS is Listening or not? ");
			snmp.send(pdu, cTarget);
			snmp.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
