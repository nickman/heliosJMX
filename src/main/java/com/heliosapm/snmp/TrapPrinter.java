package com.heliosapm.snmp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

/**
 * <p>Title: TrapPrinter</p>
 * <p>Description: Trap receiver and printer testing service</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.org.helios.apmrouter.snmp.TrapPrinter</code></p>
 */
public class TrapPrinter implements CommandResponder {
	/** The port to listen on */
	protected final int port;
	/** The address to listen on */
	protected final UdpAddress address;
	protected TransportMapping transport; // = new DefaultUdpTransportMapping();
	protected Snmp snmp;  // = new Snmp(transport);
	
	/** Decodes for PDU int constants */
	protected static final Map<Integer, String> PDU_DECODES = new HashMap<Integer, String>();
	/** Decodes for PDU v1 int constants */
	protected static final Map<Integer, String> PDUv1_DECODES = new HashMap<Integer, String>();
	
	static {
		try {
			for(Field f: PDU.class.getDeclaredFields()) {
				if(int.class==f.getType() && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
					Integer key = f.getInt(null);
					String value = f.getName();
					PDU_DECODES.put(key, value);
				}
			}
			for(Field f: PDUv1.class.getDeclaredFields()) {
				if(int.class==f.getType() && Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) {
					Integer key = f.getInt(null);
					String value = f.getName();
					PDUv1_DECODES.put(key, value);
				}
			}
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	 
	 

	/**
	 * Creates a new TrapPrinter
	 * @param port The port to listen on
	 */
	public TrapPrinter(int port) {
		this.port = port;
		address = new UdpAddress(this.port);
		
	}
	
	/**
	 * Starts the trap receiver
	 */
	public void start() {
		try {
			log("Starting TrapPrinter ...");
			transport = new DefaultUdpTransportMapping(address);
			snmp = new Snmp(transport);
			snmp.addCommandResponder(this);
			transport.listen();
			log("Started TrapPrinter on [" + address + "]");
		} catch (Exception e) {
			throw new RuntimeException("Failed to start TrapPrinter", e);
		}
	}

	/**
	 * Boots the printer
	 * @param args Option 1 arg: the port to listen on
	 */
	public static void main(String[] args) {
		log("TrapPrinter");
		int port = 1062;
		if(args.length<1) {
			try { port = Integer.parseInt(args[0].trim()); } catch (Exception e) { port = 1062; }
		}
		TrapPrinter tp = new TrapPrinter(port);
		tp.start();
		try {
			Thread.currentThread().join();
		} catch (Exception e) {}

	}
	
	/**
	 * Out logger
	 * @param msg The message to log
	 */
	public static void log(Object msg) {
		System.out.println("[" + Thread.currentThread().getName() + "]" + msg);
	}
	
	/**
	 * Err logger
	 * @param msg The message to log
	 */
	public static void elog(Object msg) {
		System.err.println("[" + Thread.currentThread().getName() + "]" + msg);
	}

	@Override
	public void processPdu(CommandResponderEvent event) {
		StringBuilder b = new StringBuilder("Received PDU:");
		PDU pdu = event.getPDU();
		event.getPeerAddress();
		b.append("\n\tReceived From: [" + event.getPeerAddress() + "]");
		b.append("\n\tType Code: [" + PDU_DECODES.get(pdu.getType()) + "]");
		b.append("\n\tRequest ID: [" + pdu.getRequestID() + "]");
		b.append("\n\tStatus: [" + pdu.getErrorStatusText() + "]");
		processV2(pdu, b);
		if(pdu instanceof PDUv1) {
			processV1((PDUv1)pdu, b);
		}
		log(b);
		
	}
	
	protected void processV2(PDU pdu, final StringBuilder b) {
		int vCount = pdu.size();
		if(vCount>0) {
			b.append("\n\tBindings [" + vCount + "]:");
			for(int i = 0; i < vCount; i++) {
				VariableBinding vb = pdu.get(i);
				b.append("\n\t\t#").append(i).append(":");
				b.append("\n\t\t\tOID:").append(vb.getOid().toString());
				Variable var = vb.getVariable();
				b.append("\n\t\t\tType:").append(var.getClass().getSimpleName());
				b.append("\n\t\t\tVaue:").append(var.toString());
			}
		} else {
			b.append("\n\tNo Bindings");
		}
		

	}
	
	protected void processV1(PDUv1 pdu, final StringBuilder b) {
		b.append("\n\tEnterprise:").append(pdu.getEnterprise());
		b.append("\n\tGenericTrap:").append(PDUv1_DECODES.get(pdu.getGenericTrap()));
		b.append("\n\tSpecificTrap:").append(pdu.getSpecificTrap());
		b.append("\n\tTimestamp:").append(pdu.getTimestamp());
	}
	
	
	
	

}

