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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.remote.protocol.tunnel.ClientProvider;

/**
 * <p>Title: SSHOption</p>
 * <p>Description: Functional enumeration of the SSH connection parameters</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.SSHOption</code></p>
 */

public enum SSHOption {
	/** The SSH user name */
	USER("u", ClientProvider.PROTOCOL_NAME + ".user", System.getProperty("user.name"), OptionReaders.STRING_READER),
	/** The SSH user password */
	PASS("p", ClientProvider.PROTOCOL_NAME + ".password", null, OptionReaders.STRING_READER),
//	/** The JMX connector server sshHost */
//	JMXHOST("jmxh", ClientProvider.PROTOCOL_NAME + ".jmxhost", "localhost", OptionReaders.STRING_READER),
//	/** The JMX connector server sshPort */
//	JMXPORT("jmxp", ClientProvider.PROTOCOL_NAME + ".jmxport", -1, OptionReaders.INT_READER),	
	/** The JMX user name */
	JMXUSER("jmxu", ClientProvider.PROTOCOL_NAME + ".jmxuser", null, OptionReaders.STRING_READER),
	/** The JMX user password */
	JMXPASS("jmxp", ClientProvider.PROTOCOL_NAME + ".jmxpassword", null, OptionReaders.STRING_READER),	
	/** The SSH private key (file, URL, char[] or CharSequence) */
	KEY("k", ClientProvider.PROTOCOL_NAME + ".privatekey", String.format("%s%s.ssh%sid_dsa", System.getProperty("user.home"), File.separator, File.separator), OptionReaders.CHAR_ARR_READER),
	/** The private key passphrase */
	KEYPHR("kp", ClientProvider.PROTOCOL_NAME + ".passphrase", null, OptionReaders.STRING_READER),
	/** The SSH sshHost to connect to */
	HOST("h", ClientProvider.PROTOCOL_NAME + ".host", null, OptionReaders.STRING_READER),
	/** The listening sshPort of the SSH sshHost to connect to */
	PORT("pt", ClientProvider.PROTOCOL_NAME + ".port", 22, OptionReaders.INT_READER),
	/** The local sshPort of the SSH tunnel */
	LOCAL_PORT("lp", ClientProvider.PROTOCOL_NAME + ".localport", 0, OptionReaders.INT_READER),	
	/** Indicates if the server key should be validated */
	SVRKEY("sk", ClientProvider.PROTOCOL_NAME + ".serverkey", "true", OptionReaders.BOOLEAN_READER),
	/** The local hosts file to use if server keys are validated */
	HOSTFILE("hf", ClientProvider.PROTOCOL_NAME + ".hostfile", String.format("%s%s.ssh%sknown_hosts", System.getProperty("user.home"), File.separator, File.separator), OptionReaders.STRING_READER),
	/** A URL or file name to load ssh params from as properties */
	SSHPROPS("pr", ClientProvider.PROTOCOL_NAME + ".propfile", String.format("%s%s.ssh%sjmx.tssh", System.getProperty("user.home"), File.separator, File.separator), OptionReaders.STRING_READER),
	/** The property prefix to use when reading from properties */
	PROPSPREF("pref", ClientProvider.PROTOCOL_NAME + ".proppref", "", OptionReaders.STRING_READER),
	/** The sub-protocol, which for right now is only "ssh" */
	SUBPROTO("subproto", ClientProvider.PROTOCOL_NAME + ".subproto", "ssh", OptionReaders.STRING_READER),
	/** The delegate-protocol, which for right now is only "jmxmp" */
	DELPROTO("delproto", ClientProvider.PROTOCOL_NAME + ".delproto", "jmxmp", OptionReaders.STRING_READER),
	/** The SSH connection timeout, non-negative, in millisecondsm where zero is no timeout */
	SSHTO("to", ClientProvider.PROTOCOL_NAME + ".timeout", 10000, OptionReaders.INT_READER),
	/** The SSH key exchange timeout, non-negative, in millisecondsm where zero is no timeout */
	SSHKTO("kto", ClientProvider.PROTOCOL_NAME + ".kextimeout", 2000, OptionReaders.INT_READER),
	/** The SSH/JMXMP specific read timeout in ms. */
	READTO("rto", ClientProvider.PROTOCOL_NAME + ".readtimeout", 0, OptionReaders.LONG_READER),
	/** The JMXConnector auto-reconnect on fail option */
	RECON("recon", ClientProvider.PROTOCOL_NAME + ".reconnect", true, OptionReaders.BOOLEAN_READER);
	
	
	/**
	 * JMXHOST, JMXPORT
	 * InteractiveCallback
	 * connect timeout
	 * kex timeout
	 * 
	 * JMXServiceURL.getHost/Port specified in URL (no option) --> tunnels to this endpoint
	 * Tunnel connection:
	 * ==================
	 * Defaults to JMXServiceURL.getHost:22
	 * Overriden with options: HOST / PORT
	 * 
	 * 
	 * 
	 */
	
	
	/** The env property name for the JMXMP read timeout */
	public static final String JMXMP_READ_TIMEOUT = "jmx.remote.x.request.timeout";
	
	
	/** A map of SSHOptions keyed by the short code */
	public static final Map<String, SSHOption> CODE2ENUM;
	/** A map of SSHOptions keyed by the ordinal */
	public static final Map<Integer, SSHOption> ORD2ENUM;
	/** A map of SSHOptions keyed by the property name */
	public static final Map<String, SSHOption> PROP2ENUM;
	
	static {
		SSHOption[] options = SSHOption.values();
		Map<String, SSHOption> tmpByCode = new HashMap<String, SSHOption>(options.length);
		Map<String, SSHOption> tmpByProp = new HashMap<String, SSHOption>(options.length);
		Map<Integer, SSHOption> tmpByOrd = new HashMap<Integer, SSHOption>(options.length);
		for(SSHOption opt: options) {
			tmpByCode.put(opt.shortCode, opt);
			tmpByProp.put(opt.propertyName, opt);
			tmpByOrd.put(opt.ordinal(), opt);
		}
		CODE2ENUM = Collections.unmodifiableMap(tmpByCode);
		PROP2ENUM = Collections.unmodifiableMap(tmpByProp);
		ORD2ENUM = Collections.unmodifiableMap(tmpByOrd);
	}
	
	/**
	 * Decodes the passed string to an SSHOption
	 * @param key the key to decode
	 * @return an SSHOption or null if one could not be identified
	 */
	public static SSHOption decode(String key) {
		if(key==null || key.trim().isEmpty()) return null;
		String name = key.trim().toUpperCase();
		SSHOption opt = null;
		try { 
			opt = SSHOption.valueOf(name);
			return opt;
		} catch (Exception x) {/* No Op*/}
		name = name.toLowerCase();
		opt = CODE2ENUM.get(name);
		if(opt!=null) return opt;
		opt = PROP2ENUM.get(name);
		if(opt!=null) return opt;
		
		try {
			int ord = Integer.parseInt(name);
			opt = ORD2ENUM.get(ord);
			if(opt!=null) return opt;			
		} catch (Exception x) {/* No Op*/}
		return null;
	}
	
	/**
	 * Creates a new SSHOption
	 * @param shortCode The short code for the SSHOption
	 * @param propertyName The system property or env variable name to get this value from
	 * @param defaultValue The default value if no value can be found, null meaning no default
	 * @param optionReader The value reader for this enum member
	 */
	private SSHOption(String shortCode, String propertyName, Object defaultValue, ISSHOptionReader<?> optionReader) {
		this.shortCode = shortCode;
		this.propertyName = propertyName;
		this.defaultValue = defaultValue;
		this.optionReader = optionReader;
	}
	
	/** The short code for the SSHOption */
	public final String shortCode;
	/** The system property or env variable name to get this value from */
	public final String propertyName;
	/** The default value if no value can be found, null meaning no default */
	public final Object defaultValue;
	/** The option reader for this enum member */
	public final ISSHOptionReader<?> optionReader;
	
	public static void main(String[] args) {
		String prefix = "";
		if(args.length>0) {
			prefix = args[0] + ".";
		}
		for(SSHOption opt: values()) {
			//System.out.println(opt.name() + " : " + opt.propertyName + "   (" + opt.defaultValue + ")");
			if(opt.defaultValue==null) {
				System.out.println("#" + prefix +  opt.propertyName + "=");
			} else {
				System.out.println(prefix +  opt.propertyName + "=" + opt.defaultValue);
			}
		}
	}
	

	/** Static class logger */
	public static final SLogger log = SimpleLogger.logger(SSHOption.class);
	
}


/*
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

raf = null;
fc = null;
try {
    raf = new RandomAccessFile("/home/nwhitehead/.ssh/id_dsa", "r");
    fc = raf.getChannel();
    mbb  = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
    mbb.load();
    decoder = Charset.defaultCharset().newDecoder();
    println decoder.decode(mbb).toString()
    
     
} finally {
    try { raf.close(); } catch (e) {}
    try { fc.close(); } catch (e) {}
}
 */ 
