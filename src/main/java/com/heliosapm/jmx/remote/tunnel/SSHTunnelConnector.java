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
package com.heliosapm.jmx.remote.tunnel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.remote.JMXServiceURL;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.ConnectionMonitor;
import ch.ethz.ssh2.KnownHosts;
import ch.ethz.ssh2.ServerHostKeyVerifier;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import ch.ethz.ssh2.crypto.PEMDecoder;
import ch.ethz.ssh2.signature.DSAPrivateKey;
import ch.ethz.ssh2.signature.RSAPrivateKey;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.remote.InetAddressCache;
import com.heliosapm.jmx.util.helpers.ConfigurationHelper;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.URLHelper;

/**
 * <p>Title: SSHTunnelConnector</p>
 * <p>Description: Gathers and encapsulates the data required to establish an SSH connection</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.remote.tunnel.SSHTunnelConnector</code></p>
 * TODO: default names system prop override
 */

public class SSHTunnelConnector implements ServerHostKeyVerifier, ConnectionMonitor {
	/** The SSH Host */
	protected String sshHost = null;
	/** The SSHD listening sshPort */
	protected int sshPort = 22;
	/** The target jmx connector server sshHost */
	protected String jmxConnectorHost = null;
	/** The target jmx connector server sshPort */
	protected int jmxConnectorPort = -1;
	
	/** The local listening sshPort */
	protected int localPort = 0;
	/** The SSH user name */
	protected String userName = null;
	/** The SSH user password */
	protected String userPassword = null;
	/** The private key passphrase */
	protected String passPhrase = null;
	/** The characters of the private key */
	protected char[] privateKey = null;
	/** The delegate protocol */
	protected String delegateProtocol = "jmxmp";
	/** The sub protocol */
	protected String subProtocol = "ssh";
	
	/** The JMXMP client timeout in ms. */
	protected long readTimeout = DEFAULT_JMXMP_READ_TIMEOUT;
	
	/** the key type */
	protected String keyType = null;
	
	/** The ssh known hosts file name */
	protected String knownHostsFile = null;
	/** The ssh known hosts repository */
	protected KnownHosts knownHosts = null;
	/** Validate server ssh key */
	protected boolean validateServer = true;

	/** The SSH connect timeout in ms. */
	protected int sshConnectTimeout = 0;
	/** The SSH key exchange timeout in ms. */
	protected int sshKeyExchangeTimeout = 0;
	
	/** Indicates if the JMXConnector should auto-reconnect on connection failure */
	protected boolean autoReconnect = true;
	
	
	
	/** A map of known sshHost instances keyed by the underlying file name */
	protected static final Map<String, KnownHosts> KNOWN_HOSTS = new ConcurrentHashMap<String, KnownHosts>();
	
	
	/** Simple static class logger */
	static final SLogger LOG = SimpleLogger.logger(SSHTunnelConnector.class);
	
	/** The message returned by the PEMDecoder if the pk is encrypted but we supplied no passphrase */
	public static final String NO_PASSPHRASE_MESSAGE = "PEM is encrypted, but no password was specified";
	/** The default DSA Private Key file name */
	public static final String DEFAULT_DSA_FILE = "id_dsa";
	/** The default RSA Private Key file name */
	public static final String DEFAULT_RSA_FILE = "id_rsa";	
	/** The default user SSH key directory */
	public static final String DEFAULT_SSH_HOME = String.format("%s%s.ssh%s", System.getProperty("user.home"), File.separator, File.separator);
	/** The default DSA Private Key location and file name */
	public static final String DEFAULT_DSA = DEFAULT_SSH_HOME + DEFAULT_DSA_FILE;
	/** The default RSA Private Key location and file name */
	public static final String DEFAULT_RSA = DEFAULT_SSH_HOME + DEFAULT_RSA_FILE;
	/** The default TSSH property config location and file name */
	public static final String DEFAULT_PROPS = String.format("%s%s.ssh%stunnel.properties", System.getProperty("user.home"), File.separator, File.separator);
	
	/** Splitter for the SSH args */
	public static final Pattern SPLIT_TRIM_SSH = Pattern.compile("\\s*,\\s*");
	/** Splitter for the SSH args */
	public static final Pattern SPLIT_TRIM_URL_SSH = Pattern.compile("\\s*&\\s*");
	
	/** Splitter for the SSH key-val pairs */
	public static final Pattern SPLIT_SSH_ARG = Pattern.compile("\\s*=\\s*");
	/** The pattern of the TSSH URL Path */
	public static final Pattern TSSH_URL_PATH_PATTERN = Pattern.compile("/(.*?)/(.*?):(.*)");
	
	/** The env property name for the JMXMP read timeout */
	public static final String JMXMP_READ_TIMEOUT = "jmx.remote.x.request.timeout";
	/** The JMXMP read timeout default in ms */
	public static final long DEFAULT_JMXMP_READ_TIMEOUT = 15000;

	
	/** An empty connector useful as a placeholder so we don't jam up the caches when synchronizing */
	public static final SSHTunnelConnector EMPTY_CONNECTOR = new SSHTunnelConnector(); 
	
	
	/**
	 * Creates a new SSHTunnelConnector
	 * @param jmxServiceURL The JMXServiceURL requested to connect to 
	 * @param env An optional environment map
	 */
	public SSHTunnelConnector(JMXServiceURL jmxServiceURL, Map<String, Object> env) {
		if(jmxServiceURL==null) throw new IllegalArgumentException("The passed JMXServiceURL was null");
		Map<SSHOption, Object> options = gather(jmxServiceURL, env);
		initialize(options);
		try {
			jmxConnectorPort = jmxServiceURL.getPort();
		} catch (Exception ex) {/* No Op */} 
		try {
			jmxConnectorHost = InetAddressCache.getInstance().getAliases(jmxServiceURL.getHost())[0];
		} catch (Exception ex) {/* No Op */}		
	}
	
	/**
	 * Creates a new SSHTunnelConnector
	 * @param url A tunnel URL with the SSH options encoded 
	 */
	public SSHTunnelConnector(URL url) {
		Map<SSHOption, Object> options = new EnumMap<SSHOption, Object>(SSHOption.class);

		String u = url.getUserInfo();
		if(u!=null && !u.trim().isEmpty()) {
			String[] auth = parseUserInfo(url);
			if(auth[0] != null) {
				options.put(SSHOption.USER, auth[0].trim());
			}
			if(auth[1] != null) {
				options.put(SSHOption.PASS, auth[1].trim());
			}			
		}
		
		if(!options.containsKey(SSHOption.PORT) && url.getPort()!=-1) {
			options.put(SSHOption.PORT, url.getPort());
		}
		
		
		options.putAll(parseOptions(url));
		final Map<SSHOption, Object> top = gather(null, options);
		options.putAll(top);
		if(!options.containsKey(SSHOption.HOST)) {
			options.put(SSHOption.HOST, url.getHost());
		}
		if(!options.containsKey(SSHOption.PORT)) {
			options.put(SSHOption.PORT, url.getPort()==-1 ? SSHOption.PORT.defaultValue : url.getPort());
		}
		
		initialize(options);
		try {
			jmxConnectorPort = url.getPort();
		} catch (Exception ex) {/* No Op */}
		try {
			jmxConnectorHost = InetAddressCache.getInstance().getAliases(url.getHost())[0];
		} catch (Exception ex) {/* No Op */}	
		if(sshHost==null & jmxConnectorHost != null) {
			sshHost = jmxConnectorHost;
		}		
		LOG.log(this.toString());
	}
	
	private String[] parseUserInfo(final URL url) {
		final String[] parsed = new String[2];
		final String u = url.getUserInfo();
		if(u!=null && !u.trim().isEmpty()) {
			final int index = u.indexOf(':');
			if(index==-1) {
				parsed[0] = u;
			} else {
				parsed[0] = u.substring(0, index);
				parsed[1] = u.substring(index+1);
			}
		}
		return parsed;
	}
	
	
	/**
	 * Creates a new SSHTunnelConnector from the passed options stringy
	 * @param sshOptions and SSHOption stringy
	 */
	public SSHTunnelConnector(final CharSequence sshOptions) {
		Map<SSHOption, Object> map = extractTunnelOpts(sshOptions);
		Map<SSHOption, Object> options = gather(null, map);
		initialize(options);
	}
	
	/**
	 * Creates a new placeholder SSHTunnelConnector
	 */
	private SSHTunnelConnector() {
		
	}
	
	/**
	 * Initializes this SSHTunnelConnector with the passed map of options
	 * @param options The SSHOptions specifying how a tunnel should be created
	 */
	protected void initialize(final Map<SSHOption, Object> options) {		
		for(Map.Entry<SSHOption, Object> entry: options.entrySet()) {
			SSHOption option = entry.getKey();
			Object optionValue = entry.getValue();
			switch(option) {
			case DELPROTO:
				delegateProtocol = optionValue.toString();
				break;
			case HOST:
				sshHost = InetAddressCache.getInstance().getAliases(optionValue.toString())[0];
				break;
			case HOSTFILE:
				if(OptionReaders.isFile(optionValue.toString())) {
					knownHostsFile = optionValue.toString();
					buildKnownHosts();
				}
				break;
			case RECON:
				
				break;
			case KEY:
				if(optionValue!=null) {
					if(optionValue instanceof char[]) {
						privateKey = (char[])optionValue;
					} else {
						String urlStr = optionValue.toString().trim();
						if(URLHelper.isFile(urlStr) || URLHelper.isValidURL(urlStr)) {
							privateKey = URLHelper.getCharsFromURL(urlStr);
							LOG.log("PrivateKey read from [%s]", urlStr);
						} else {
							urlStr = DEFAULT_SSH_HOME + urlStr;
							if(URLHelper.isFile(urlStr) || URLHelper.isValidURL(urlStr)) {
								privateKey = URLHelper.getCharsFromURL(urlStr);
								LOG.log("PrivateKey read from [%s]", urlStr);
							}
						}
					}
				}						 
				break;
			case KEYPHR:
				passPhrase = optionValue.toString();
				break;
			case LOCAL_PORT:
				int lp = (Integer)optionValue;
				if(lp!=0) {
					localPort = lp;
				}
				break;
			case PASS:
				userPassword = optionValue.toString();
				break;
			case PORT:
				sshPort = Integer.parseInt(optionValue.toString().trim());
				break;
			case PROPSPREF:
				break;
			case SSHPROPS:
				break;
			case SUBPROTO:
				subProtocol = optionValue.toString();
				break;
			case SVRKEY:
				validateServer = Boolean.parseBoolean(optionValue.toString());
				break;
			case USER:
				userName = optionValue.toString();
				break;
			case SSHTO:
				sshConnectTimeout = Integer.parseInt(optionValue.toString().trim());
				break;
			case SSHKTO:
				sshKeyExchangeTimeout = Integer.parseInt(optionValue.toString().trim());
				break;
//			case JMXHOST:
//				jmxConnectorHost = optionValue.toString();
//				break;
//			case JMXPORT:
//				jmxConnectorPort = (Integer)optionValue;
//				break;
			case READTO:
				readTimeout = ((Number)optionValue).longValue();
				break;
			default:
				break;
			
			}			
			setKeyType();
		}		
		if(sshHost==null & jmxConnectorHost != null) {
			sshHost = jmxConnectorHost;
		}		
	}
	
	/*
	 * sshHost/sshPort: from jmxServiceURL.getHost() - The actual target endpoint, may also be the SSH sshHost to tunnel through (the bridge sshHost)
	 * jmxConnectorHost/jmxConnectorPort:  option [jmxh] specified, the SSH bridge sshHost. If not specified, is the same as the sshHost 
	 */
	
	/**
	 * Creates the required tunnel for the passed JMXServiceURL
	 * @param jmxServiceURL The tunnel JMXServiceURL
	 * @return the modified environment map
	 */
	public static Map<?, Object> tunnel(final JMXServiceURL jmxServiceURL) {
		return tunnel(jmxServiceURL, null);
	}
	
	
	
	/**
	 * Creates a tunnel to enable a JMX connection specified by the passed JMXServiceURL and env map
	 * @param jmxServiceURL The JMXServiceURL describing the JMX connection and SSH tunneling options
	 * @param env The JMX environment map
	 * @return the modified environment map
	 */
	@SuppressWarnings("resource")
	public static Map<?, Object> tunnel(final JMXServiceURL jmxServiceURL, Map<String, Object> env) {
		SSHTunnelConnector tc = new SSHTunnelConnector(jmxServiceURL, env);
		TunnelRepository.getInstance().connect(tc);
		TunnelHandle tunnelHandle = TunnelRepository.getInstance().tunnel(tc);
		if(env==null) {
			env = new HashMap<String, Object>();
		}
		if(!env.containsKey(JMXMP_READ_TIMEOUT)) {
			env.put(JMXMP_READ_TIMEOUT, tc.readTimeout);
		}
		JMXServiceURL serviceURL = JMXHelper.serviceUrl("service:jmx:%s://%s:%s", tc.getDelegateProtocol(), "localhost", tunnelHandle.getLocalPort());
		LOG.log("Rewritten Tunneled JMXServiceURL [%s]", serviceURL);
		env.put("JMXServiceURL", serviceURL);
		env.put("TunnelHandle", tunnelHandle);
		return env;
	}
	
	/**
	 * Creates a tunnel in accordance with the options extracted from the passed stringy
	 * @param sshOptions A stringy of comma separated name value pairs of SSHOptions.
	 * @return the tunnel handle
	 */
	public static TunnelHandle tunnel(CharSequence sshOptions) {
		SSHTunnelConnector tc = new SSHTunnelConnector(sshOptions);		
		return TunnelRepository.getInstance().tunnel(tc);
	}
	
	/**
	 * Creates an un-executed SSHTunnelConnector for the passed tunnel URL
	 * @param url a tunnel protocol URL
	 * @return the un-executed SSHTunnelConnector that can establish an SSH connection and tunnel as encoded in the URL
	 */
	public static SSHTunnelConnector connector(URL url) {
		if(url==null) throw new IllegalArgumentException("The passed URL was null");
		String protocol = url.getProtocol();
		if(protocol==null || protocol.trim().isEmpty()) throw new IllegalArgumentException("The passed URL [" + url + "] had a null or empty protocol");
		if(!"tunnel".equals(url.getProtocol().trim().toLowerCase()) && !"ssh".equals(url.getProtocol().trim().toLowerCase()))  throw new IllegalArgumentException("Unrecognized tunnel protocol [" + protocol + "]");		
		return new SSHTunnelConnector(url);
	}

	
	/**
	 * Validates the most basic requirements to establish a connection 
	 */
	protected void validateRequirementsLevelOne() {
		String[] aliases = InetAddressCache.getInstance().getAliases(sshHost);
		if(sshPort < 1 || sshPort > 65534) throw new IllegalArgumentException("The configured sshPort is out of range [" + sshPort + "]");
		if(userName==null || userName.trim().isEmpty()) throw new IllegalArgumentException("The configured user name is null or empty");
	}
	

	
	/**
	 * Attempts to establish an authenticated connection based on the state of this connector
	 * @return a connected and authenticated connection
	 */
	public Connection connectAndAuthenticate() {
		Connection conn = null;
		Set<String> authFailures = new LinkedHashSet<String>();
		validateRequirementsLevelOne();
		boolean authenticated = false;
		try {
			LOG.log("Connecting to: [" + sshHost + ":" + sshPort + "]");
			conn = new Connection(sshHost, sshPort);
			
			if(validateServer) {
				conn.connect(this, sshConnectTimeout, sshKeyExchangeTimeout);
			} else {
				conn.connect(null, sshConnectTimeout, sshKeyExchangeTimeout);
			}
//			authenticated = conn.authenticateWithNone(userName);
//			if(authenticated) return conn;
			authFailures.add("authenticateWithNone failed for user [" + userName + "]");
			if(userPassword!=null) {
				authenticated = conn.authenticateWithPassword(userName, userPassword);
				if(authenticated) return conn;
				authFailures.add("authenticateWithPassword failed for user [" + userName + "]");
			} else {
				authFailures.add("authenticateWithPassword skipped for [" + userName + "] since password was null");
			}
			authenticated = conn.authenticateWithPublicKey(userName, privateKey, passPhrase);
			if(authenticated) return conn;
			authFailures.add("authenticateWithPublicKey failed for user [" + userName + "]");
			throw new RuntimeException("Unable to authenticate user [" + userName + "]. Attempted methods:" + authFailures.toString());
		} catch (Exception ex) {
			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
			if(ex instanceof RuntimeException) {
				throw (RuntimeException)ex;
			}
			throw new RuntimeException("Unexpected connection failure", ex);
		} finally {
//			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	public static String dumpHostInfo(Connection conn) {
		Session sess = null;
		try {
			sess = conn.openSession();
			sess.execCommand("uname -a && date && uptime && who");
			InputStream stdout = new StreamGobbler(sess.getStdout());
			BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
			StringBuilder b = new StringBuilder();
			while (true) {
				String line = br.readLine();				
				if (line == null) {
					br.close();
					break;
				}
				b.append("\n").append(line);
			}
			return b.toString();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			if(sess!=null) try { sess.close(); } catch (Exception x) {/* No Op */}
		}
			
	}
	
	
	/**
	 * Builds the know sshHost instance or resets if fails
	 */
	private void buildKnownHosts() {
		if(knownHostsFile!=null && OptionReaders.isFile(knownHostsFile)) {
			knownHostsFile = knownHostsFile.trim();
			KnownHosts kh = KNOWN_HOSTS.get(knownHostsFile);
			if(kh==null) {
				synchronized(KNOWN_HOSTS) {
					kh = KNOWN_HOSTS.get(knownHostsFile);
					if(kh==null) {
						try {
							kh = new KnownHosts(new File(knownHostsFile));
							KNOWN_HOSTS.put(knownHostsFile, kh);
						} catch (Exception ex) {
							knownHostsFile = null;
							knownHosts = null;
							throw new RuntimeException("Failed to build a known hosts instance from file [" + knownHostsFile + "]");
						}
					}
				}
			}
			knownHosts = kh;
		}
		
	}




	
	public static enum KeyStatus {
		/** Key is ok */
		OK, 
		/** Key validation failed */
		NOWAY,
		/** Key requires passphrase */
		PASS;
	}
	/**
	 * <p>Title: OptionSource</p>
	 * <p>Description: Enumerates the sources where SSHOptions can be decoded from, in precdence order</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.remote.tunnel.SSHTunnelConnector.OptionSource</code></p>
	 */
	public static enum OptionSource {
		JMXServiceURL,
		EnvMap,
		PropFile,
		DefaultPropFile,
		ConfigHelper,
		DefaultKeys;
	}
	

	
	
	/**
	 * Extracts the JMXServiceURL encoded SSH options into a map
	 * @param jmxServiceURL The JMXServiceURL to extract from
	 * @return the map of extracted options
	 */
	public static Map<SSHOption, Object> extractJMXServiceURLOpts(JMXServiceURL jmxServiceURL) {
		if(jmxServiceURL==null) return Collections.EMPTY_MAP;
		Map<SSHOption, Object> map = new EnumMap<SSHOption, Object>(SSHOption.class);
		String urlArgs = jmxServiceURL.getURLPath();		
		
		Matcher m = TSSH_URL_PATH_PATTERN.matcher(urlArgs);
		if(!m.matches()) {
			throw new RuntimeException("Failed to recognize URL Path pattern [" + urlArgs + "]");
		} 
		String subProtocol = m.group(1);
		map.put(SSHOption.SUBPROTO, subProtocol);
		String delegateProtocol = m.group(2);
		map.put(SSHOption.DELPROTO, delegateProtocol);
		urlArgs = m.group(3);
		if(urlArgs==null || urlArgs.trim().isEmpty()) return Collections.EMPTY_MAP;
		map.putAll(parseOptions(urlArgs));
		return map;
	}
	
	/**
	 * Parses a stringy to extract a map of SSHOptions specifying a tunnel specification
	 * @param sshOptions A stringy of comma separated key value pairs
	 * @return a map of SSHOptions
	 */
	public static Map<SSHOption, Object> extractTunnelOpts(CharSequence sshOptions) {
		if(sshOptions==null) return Collections.EMPTY_MAP;
		return parseOptions(sshOptions.toString().trim());
	}
	
	/**
	 * Parses a string to extract a map of SSHOptions specifying a tunnel specification
	 * @param urlArgs A string of comma separated key value pairs
	 * @return a map of SSHOptions
	 */
	protected static Map<SSHOption, Object> parseOptions(String urlArgs) {
		if(urlArgs==null || urlArgs.trim().isEmpty()) return Collections.EMPTY_MAP;
		Map<SSHOption, Object> map = new EnumMap<SSHOption, Object>(SSHOption.class);
		for(String pair: SPLIT_TRIM_SSH.split(urlArgs.trim())) {
			String[] kv = SPLIT_SSH_ARG.split(pair);
			if(kv!=null && kv.length==2) {
				SSHOption option = SSHOption.decode(kv[0]);
				if(option==null) continue;
				Object optionValue = option.optionReader.convert(kv[1], null);
				if(option!=null && optionValue != null) {
					map.put(option, optionValue);
				}
			}
		}
		return map;
	}
	
	/**
	 * Parses a tunnel URL and returns a map of the SSHOptions
	 * @param tunnelURL A tunnel URL
	 * @return the map of options
	 */
	protected static Map<SSHOption, Object> parseOptions(URL tunnelURL) {
		String query = tunnelURL.getQuery();
		String file = tunnelURL.getFile();
		String line = null;
		if(query!=null && !query.trim().isEmpty()) {
			line = query;
		} else if (file!=null && !file.trim().isEmpty()) {
			line = file.substring(1);
		}
		if(line==null || line.trim().isEmpty()) return Collections.EMPTY_MAP;
		Map<SSHOption, Object> map = new EnumMap<SSHOption, Object>(SSHOption.class);
		for(String pair: SPLIT_TRIM_URL_SSH.split(line.trim())) {
			String[] kv = SPLIT_SSH_ARG.split(pair);
			if(kv!=null && kv.length==2) {
				SSHOption option = SSHOption.decode(kv[0]);
				if(option==null) continue;
				Object optionValue = option.optionReader.convert(kv[1], null);
				if(option!=null && optionValue != null) {
					map.put(option, optionValue);
				}
			}
		}
		return map;
	}
	
	
	protected void setKeyType() {
		if(privateKey!=null) {
			try {
				Object key = PEMDecoder.decode(privateKey, passPhrase);
				if(key instanceof DSAPrivateKey) {
					keyType = "dsa";
				} if(key instanceof RSAPrivateKey) {
					keyType = "rsa";
				}
			} catch (IOException iex) {
				//privateKey = null;
			}
			
		}
	}
	
	/**
	 * Validates an SSH key
	 * @return the status of the key validation
	 */
	protected KeyStatus validateKey() {
		if(privateKey==null) return KeyStatus.NOWAY;
		Object key = null;
		try {
			key = PEMDecoder.decode(privateKey, passPhrase);
		} catch (IOException iex) {
			return KeyStatus.NOWAY;
		}
		if(key instanceof DSAPrivateKey || key instanceof RSAPrivateKey) {
			try {
				PEMDecoder.decode(privateKey, passPhrase);
				return KeyStatus.OK;
			} catch (Exception ex) {
				if(NO_PASSPHRASE_MESSAGE.equals(ex.getMessage())) {
					return KeyStatus.PASS;
				}
				return KeyStatus.NOWAY;
			}
		}
		return KeyStatus.NOWAY;
	}
	
	public static void main(String[] args) {
		try {
			LOG.log("Testing Gather");
			// u h k pt
			JMXServiceURL[] urls = new JMXServiceURL[] {
					new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c,jmxu=admin,kp=helios"),
					new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c:/users/nwhitehe/.ssh/id_dsa"),
					new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c:/users/nwhitehe/.ssh/id_rsa_2048"),
					new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c:/users/nwhitehe/.ssh/id_rsa_8092")
			};
			//JMXServiceURL url = new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c:/users/nwhitehe/.ssh/id_dsa");
			//gather(url, null);
			for(JMXServiceURL url: urls) {
				SSHTunnelConnector stc = new SSHTunnelConnector(url, null);
				LOG.log(stc.toString());
			}
			
			LOG.log("Testing Connect....");
			Connection conn = null;
			try {
				//JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://localhost:8006/ssh/jmxmp:u=nwhitehead,h=pdk-pt-ceas-03,pt=22,k=c,jmxu=admin,kp=helios");
				JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://10.12.114.48:8006/ssh/jmxmp:u=nwhitehe,h=10.12.114.48,pt=22,k=c,jmxu=admin,p=jer1029");
				
				SSHTunnelConnector connector = new SSHTunnelConnector(surl, null);
				conn = connector.connectAndAuthenticate();
				LOG.log("Connection: authed:" + conn.isAuthenticationComplete());
				conn.forceKeyExchange();				
				LOG.log(dumpHostInfo(conn));
				LOG.log(new ConnectionInfoWrapper(conn.getConnectionInfo()));
			} finally {
				if(conn!=null) try { conn.close(); } catch (Exception x) {}
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Gathers the SSHOptions from the different possible sources and then trims them down by the precedence rules
	 * @param serviceURL The JMXServiceURL being connected to
	 * @param env The optional JMXServiceURL environment map
	 * @return a map of final options
	 */
	public static Map<SSHOption, Object> gather(JMXServiceURL serviceURL, Map<?, Object> env) {
		Map<SSHOption, Object> top = new EnumMap<SSHOption, Object>(SSHOption.class); 
		merge(gatherDefaults(), top);
		merge(gatherConfigHelper(), top);
		merge(gatherFromSource(DEFAULT_PROPS), top);
		merge(gatherFromSource(ConfigurationHelper.getSystemThenEnvProperty(SSHOption.SSHPROPS.propertyName, "")), top);
		merge(gatherFromMap(env), top);
		if(serviceURL!=null) merge(extractJMXServiceURLOpts(serviceURL), top);
		// derives any missing options from the service URL, where applicable
		if(!top.containsKey(SSHOption.HOST)) {
			if(serviceURL!=null) {
				top.put(SSHOption.HOST, serviceURL.getHost());
			}
		}
		
		// dereferences options from the defined properties file
		decodePropsFile(top);
		
		// fills in any missing options with defaults
		for(SSHOption opt: SSHOption.values()) {
			if(!top.containsKey(opt)) {
				Object dv = opt.defaultValue;
				if(dv!=null) top.put(opt, dv);
			}
		}
		StringBuilder b = new StringBuilder("\nSSHOptions: [");
		for(Map.Entry<SSHOption, Object> entry: top.entrySet()) {
			b.append("\n\t").append(entry.getKey()).append(" : [").append(entry.getValue()).append("]");
		}
		b.append("\n]");
		LOG.log(b);
//		if(!top.isEmpty()) {
//			StringBuilder b = new StringBuilder("\n\tFinal SSHoptions:\n\t===================================================");
//			for(Map.Entry<SSHOption, Object> entry: top.entrySet()) {
//				b.append("\n\t").append(entry.getKey()).append(":").append(entry.getValue());
//			}
//			b.append("\n\t===================================================\n");
//			LOG.log(b.toString());
//		} else {
//			LOG.log("Empty ????");
//		}
		
		return top;
	}	
	
	/**
	 * Overrides any props from the defined {@link: SSHOption#SSHPROPS} and {@link: SSHOption#PROPSPREF} prefix if defined. 
	 * @param top The map to enrich
	 */
	public static void decodePropsFile(final Map<SSHOption, Object> top) {
		String prefix = (String)top.get(SSHOption.PROPSPREF);
		if(prefix!=null) {
			prefix = prefix.trim() + ".";
		} else {
			prefix = "";
		}
		String propFile = (String)top.get(SSHOption.SSHPROPS);
		if(propFile==null || propFile.trim().isEmpty()) {
			if(prefix.isEmpty()) {
				return;
			}
		}
		// if a prefix was defined, but a prop file was not
		// then we use the default.
		propFile = (String) SSHOption.SSHPROPS.defaultValue;
		URL url = URLHelper.toURL(propFile.trim());
		
		Properties p = load(url, prefix);
		for(SSHOption opt: SSHOption.values()) {
			String value = p.getProperty(opt.propertyName);
			if(value==null || value.trim().isEmpty()) continue;
			top.put(opt, value.trim());
		}
	}
	
	/**
	 * Loads props and returns from the passed URL.
	 * If the lower case file ends with <b><code>.xml</code></b>, will load as XML format. 
	 * @param url The URL endpoint for the properties
	 * @param prefix The optional prefix
	 * @return the read properties which will be empty if the read failed, or no matching props were found.
	 */
	private static Properties load(final URL url, final String prefix) {		
		final Properties p = URLHelper.readProperties(url);
		if(p.isEmpty()) return p;
		if(prefix!=null && !prefix.trim().isEmpty()) {
			final Properties filtered = new Properties();
			for(String key: p.stringPropertyNames()) {
				if(key.startsWith(prefix)) {
					filtered.setProperty(key.replace(prefix, ""), p.getProperty(key));
				}
			}
			return filtered;
		}
		return p;
	}
	
	
	/**
	 * Gathers SSHOptions from the passed map
	 * @param map The map to gather from
	 * @return a possibly empty map of SSHOptions and values
	 */
	public static Map<SSHOption, Object> gatherFromMap(Map<?, Object> map) {
		if(map==null || map.isEmpty()) return Collections.emptyMap();
		Map<SSHOption, Object> config = new EnumMap<SSHOption, Object>(SSHOption.class);
		for(Object e: map.entrySet()) {
			Map.Entry<Object, Object> entry = (Map.Entry<Object, Object>)e;
			String key = entry.getKey().toString();
			Object v = entry.getValue();
			SSHOption option = SSHOption.decode(key);
			if(option==null) continue;
			
			Object value = v!=null ? v : option.optionReader.getRawOption(option.propertyName, option.defaultValue);
			if(value!=null) {
				config.put(option, value);
			}
		}
		return config;
	}
	
	/**
	 * Gathers SSHOptions from the passed file or URL source
	 * @param source A file name or URL to read properties from
	 * @return a possibly empty map of SSHOptions
	 */
	public static Map<SSHOption, Object> gatherFromSource(String source) {
		Map<SSHOption, Object> config = new EnumMap<SSHOption, Object>(SSHOption.class);
		String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, "");
		if(!prefix.isEmpty()) {
			prefix = prefix + ".";
		}
		Properties props = OptionReaders.getProperties(source);
		if(props!=null && !props.isEmpty()) {
			for(SSHOption option: SSHOption.values()) {
				String optionValue = System.getProperty(prefix + option.propertyName, null);
				if(optionValue!=null) {
					Object value = option.optionReader.getRawOption(option.propertyName, option.defaultValue);
					if(value!=null) {
						config.put(option, value);
					}
				}
			}			
		}
		return config;
	}
	
	
	/**
	 * Gathers SSH options from the system properties and environmental variables
	 * @return a possibly empty map of SSHOptions
	 */
	public static Map<SSHOption, Object> gatherConfigHelper() {
		Map<SSHOption, Object> config = new EnumMap<SSHOption, Object>(SSHOption.class);
		String prefix = ConfigurationHelper.getSystemThenEnvProperty(SSHOption.PROPSPREF.propertyName, "");
		if(!prefix.isEmpty()) {
			prefix = prefix + ".";
		}
		for(SSHOption option: SSHOption.values()) {
			String optionValue = ConfigurationHelper.getSystemThenEnvProperty(prefix + option.propertyName, null);
			if(optionValue!=null) {
				Object value = option.optionReader.getRawOption(option.propertyName, option.defaultValue);
				if(value!=null) {
					config.put(option, value);
				}
			}
		}
		return config;
	}
	
	/**
	 * Looks for the default keys (<b><code>${user.home}/.ssh/id_dsa</code></b> and <b><code>${user.home}/.ssh/id_rsa</code></b>) 
	 * @return a map that may contain a {@link SSHOption#KEY} option and the key characters
	 */
	public static Map<SSHOption, Object> gatherDefaults() {
		Map<SSHOption, Object> keys = new EnumMap<SSHOption, Object>(SSHOption.class);
		char[] key = null;
		if(OptionReaders.isFile(DEFAULT_DSA)) {
			key = OptionReaders.CHAR_ARR_READER.getOption(DEFAULT_DSA, null);
			if(key!=null) keys.put(SSHOption.KEY, key);
		}
		if(!keys.containsKey(SSHOption.KEY)) {
			if(OptionReaders.isFile(DEFAULT_RSA)) {
				key = OptionReaders.CHAR_ARR_READER.getOption(DEFAULT_RSA, null);
				if(key!=null) keys.put(SSHOption.KEY, key);
			}
		}
		if(OptionReaders.isFile(SSHOption.HOSTFILE.defaultValue.toString())) {
			keys.put(SSHOption.HOSTFILE, SSHOption.HOSTFILE.defaultValue.toString());
		}
		return keys;
	}
	
	
	
	/**
	 * Merges a map of SSHOptions
	 * @param from The source map
	 * @param into The target map
	 */
	public static void merge(final Map<SSHOption, Object> from, final Map<SSHOption, Object> into) {
		if(from!=null && !from.isEmpty() && into!=null) {
			for(Map.Entry<SSHOption, Object> entry: from.entrySet()) {
				if(into.containsKey(entry.getKey())) {
//					LOG.log("Replacing [%s/%s] with [%s/%s]", entry.getKey(), into.get(entry.getKey()), entry.getKey(), entry.getValue());
				} else {
//					LOG.log("Initing [%s/%s]", entry.getKey(), entry.getValue()); 
				}
				into.put(entry.getKey(), entry.getValue());
			}
		}
	}



	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final int maxLen = 10;
		StringBuilder builder = new StringBuilder();
		builder.append("SSHTunnelConnector [");
		if (sshHost != null) {
			builder.append("sshHost=");
			builder.append(sshHost);
			builder.append(", ");
		}
		builder.append("sshPort=");
		builder.append(sshPort);
		builder.append(", localPort=");
		builder.append(localPort);
		builder.append(", ");
		builder.append("autoReconnect=");
		builder.append(autoReconnect);
		
		if (userName != null) {
			builder.append("userName=");
			builder.append(userName);
			builder.append(", ");
		}
		if (userPassword != null) {
			builder.append("userPassword=");
			builder.append(userPassword);
			builder.append(", ");
		}
		if (passPhrase != null) {
			builder.append("passPhrase=");
			builder.append(passPhrase);
			builder.append(", ");
		}
		if (privateKey != null) {
			builder.append("privateKey=(").append(keyType).append(") char[").append(privateKey.length).append("], ");
		}
		if (jmxConnectorHost != null) {
			builder.append("jmxConnectorHost=");
			builder.append(jmxConnectorHost);
			builder.append(", ");
		}
		builder.append("jmxConnectorPort=");
		builder.append(jmxConnectorPort);
		builder.append(", ");
		
		if (delegateProtocol != null) {
			builder.append("delegateProtocol=");
			builder.append(delegateProtocol);
			builder.append(", ");
		}
		if (subProtocol != null) {
			builder.append("subProtocol=");
			builder.append(subProtocol);
			builder.append(", ");
		}
		if (knownHostsFile != null) {
			builder.append("knownHostsFile=");
			builder.append(knownHostsFile);
			builder.append(", ");
		}
		builder.append("validateServer=");
		builder.append(validateServer);
		
		
		
		
		builder.append("]");
		return builder.toString();
	}



	/**
	 * {@inheritDoc}
	 * @see ch.ethz.ssh2.ServerHostKeyVerifier#verifyServerHostKey(java.lang.String, int, java.lang.String, byte[])
	 */
	@Override
	public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
		if(knownHosts!=null && validateServer) {
			int result = knownHosts.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey);
			switch(result) {
				case KnownHosts.HOSTKEY_HAS_CHANGED:
					return false;
				case KnownHosts.HOSTKEY_IS_NEW:
					return false;
				case KnownHosts.HOSTKEY_IS_OK:
					return true;
				default:
					return false;
			}
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @see ch.ethz.ssh2.ConnectionMonitor#connectionLost(java.lang.Throwable)
	 */
	@Override
	public void connectionLost(Throwable reason) {
		// TODO Auto-generated method stub
		
	}

	public String getSSHHost() {
		return sshHost==null ? jmxConnectorHost : sshHost;
	}

	public int getSSHPort() {
		return sshPort;
	}

	public int getLocalPort() {
		return localPort;
	}

	public String getUserName() {
		return userName;
	}

	public String getUserPassword() {
		return userPassword;
	}

	public String getPassPhrase() {
		return passPhrase;
	}

	public char[] getPrivateKey() {
		return privateKey;
	}

	public String getDelegateProtocol() {
		return delegateProtocol;
	}

	public String getSubProtocol() {
		return subProtocol;
	}

	public String getKeyType() {
		return keyType;
	}

	public String getKnownHostsFile() {
		return knownHostsFile;
	}

	public boolean isValidateServer() {
		return validateServer;
	}

	/**
	 * Returns the tunnel endpoint host
	 * @return the tunnel endpoint host
	 */
	public String getJmxConnectorHost() {
		return jmxConnectorHost;
	}

	/**
	 * Returns the tunnel endpoint port
	 * @return the tunnel endpoint port
	 */
	public int getJmxConnectorPort() {
		return jmxConnectorPort;
	}

	/**
	 * Returns the SSH connect timeout in ms.
	 * @return the SSH connect timeout in ms.
	 */
	public int getSshConnectTimeout() {
		return sshConnectTimeout;
	}

	public int getSshKeyExchangeTimeout() {
		return sshKeyExchangeTimeout;
	} 

	/**
	 * Sets the SSH connect timeout in ms.
	 * @param sshConnectTimeout the SSH connect timeout in ms.
	 */
	public void setSshConnectTimeout(int sshConnectTimeout) {
		this.sshConnectTimeout = sshConnectTimeout;
	}

	/**
	 * Indicates if the JMXConnector should auto-reconnect on connection failure
	 * @return true if the JMXConnector should auto-reconnect on connection failure, false otherwise
	 */
	public final boolean isAutoReconnect() {
		return autoReconnect;
	}

	/**
	 * Sets the auto-reconnect on connection failure
	 * @param autoReconnect true to auto-reconnect on connection failure, false otherwise
	 */
	public final void setAutoReconnect(boolean autoReconnect) {
		this.autoReconnect = autoReconnect;
	}

	
	
	/*
	 * Ways to find key:
	 * Default location
	 * Env Map
	 * JMXServiceURL encoded
	 * SystemProp / EnvVar
	 * Properties file
	 * 
	 * Prescedence:
	 * ============
		JMXServiceURL,
		EnvMap,
		PropFile,
		DefaultPropFile,
		ConfigHelper,
		DefaultKeys;
	 * 
	 */

}


/*
import javax.management.remote.*;
import java.util.regex.*;

surl = new JMXServiceURL("service:jmx:rmi://localhost:1099/ssh:nwhitehe[@localhost[:22]]], pw=XXXX ,key=c:/tmp/key_dsa,pp=YYYYY");
p = Pattern.compile('(?:\\s*(.*?)=(.*?)\\s*(?:,\\s-$|$|,))')
// 

println surl
urlPath = surl.getURLPath().replace("/ssh:", "").trim();
println "URLPath: [$urlPath]";
m = p.matcher(urlPath);
while(m.find()) {
    println "${m.group(1)}=${m.group(2)}";
}


println urlPath.split("\\s*,\\s*");
 */
