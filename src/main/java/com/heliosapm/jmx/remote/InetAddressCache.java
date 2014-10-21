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
package com.heliosapm.jmx.remote;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.remote.tunnel.TunnelRepository;

/**
 * <p>Title: InetAddressCache</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.InetAddressCache</code></p>
 */

public class InetAddressCache {
	/** The singleton instance */
	private static volatile InetAddressCache instance = null;
	/** The singleton ctor lock */
	private static final Object lock = new Object();
	
	/** A cache of sshHost address/sshHost name arrays keyed by the inquired name */
	protected final NonBlockingHashMap<String, String[]> nameCache = new NonBlockingHashMap<String, String[]>();
	/** A cache of sshHost address/sshHost name arrays keyed by the inquired address */
	protected final NonBlockingHashMap<String, String[]> addressCache = new NonBlockingHashMap<String, String[]>();
	
	
	private static final SLogger LOG = SimpleLogger.logger(InetAddressCache.class);
	
	/** IPV4 Pattern */
	public static final Pattern IP4_ADDRESS_PATTERN = Pattern.compile( 
			"^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
	
	/** IPV6 Pattern */
	public static final Pattern IP6_ADDRESS_PATTERN = Pattern.compile( 
			"^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
	
	
	/**
	 * Indicates if the passed value pattern matches a V4 or v6 IP address
	 * @param value The value to test
	 * @return true if matched, false otherwise
	 */
	public static boolean isIPAddress(CharSequence value) {
		if(value==null) return false;
		Matcher m = IP4_ADDRESS_PATTERN.matcher(value);
		if(m.matches()) return true;
		m = IP6_ADDRESS_PATTERN.matcher(value);
		return m.matches();		
	}
	
	/**
	 * Acquires the InetAddressCache singleton instance
	 * @return the InetAddressCache singleton instance
	 */
	public static InetAddressCache getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new InetAddressCache();
				}
			}
		}
		return instance;
	}
	

	/**
	 * Returns the address and sshHost name for the passed string
	 * @param hostName The sshHost string or address to resolve
	 * @return a string array with the address and sshHost name
	 */
	public String[] getAliases(final String hostName) {
		if(isIPAddress(hostName)) {
			return getAliasesByAddress(hostName);
		}
		try {
			String[] item = nameCache.get(hostName);
			if(item==null) {
				synchronized(nameCache) {
					item = nameCache.get(hostName);
					if(item==null) {						
						long start = System.currentTimeMillis();
						InetAddress ia =  InetAddress.getByName(hostName);
						item = new String[] {ia.getHostAddress(), ia.getHostName(), ""};
						long elapsed = System.currentTimeMillis()-start;						
						LOG.log("Host: [%s] Aliases %s Elapsed: %s ms.", hostName, Arrays.toString(item), elapsed);
						nameCache.put(hostName, item);
						if(!addressCache.containsKey(item[0])) {
							addressCache.put(item[0], item);
						}
						//item[2] = "" + start;
					}
				}
			}
			return item;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to resolve sshHost [" + hostName + "]", ex);
		}
	}	
	
	
	/**
	 * Returns the address and sshHost name for the passed string
	 * @param address The sshHost address to resolve
	 * @return a string array with the address and sshHost name
	 */
	protected String[] getAliasesByAddress(final String address) {
		try {
			String[] item = addressCache.get(address);
			if(item==null) {
				synchronized(addressCache) {
					item = nameCache.get(address);
					if(item==null) {												
						InetAddress ia =  InetAddress.getByName(address);
						item = new String[] {ia.getHostAddress(), ia.getCanonicalHostName(), ""};											
						addressCache.put(address, item);
						if(!nameCache.containsKey(item[1])) {
							nameCache.put(item[1], item);
						}
						
					}
				}
			}
			return item;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to resolve address [" + address + "]", ex);
		}
	}	
	

	/**
	 * Creates a new InetAddressCache
	 */
	private InetAddressCache() {

	}

}
