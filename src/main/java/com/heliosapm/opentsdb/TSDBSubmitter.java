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
package com.heliosapm.opentsdb;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.ReconnectorService;
import com.heliosapm.jmx.util.helpers.StringHelper;
import com.heliosapm.opentsdb.AnnotationBuilder.TSDBAnnotation;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;

/**
 * <p>Title: TSDBSubmitter</p>
 * <p>Description: A Telnet TCP client for buffering and sending metrics to OpenTSDB</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.TSDBSubmitter</code></p>
 * TODO:
 * fluent metric builder
 * jmx response trace formatters
 * 		transforms
 * 		object name matcher
 * 		formatter cache and invoker
 * Delta Service
 * terminal output parser -> trace
 * disconnector & availability trace
 * root tags
 * dup checks mode
 * off line accumulator and flush on connect
 * option for TSDB time as seconds or milliseconds
 * option to log all traced metrics
 */

public class TSDBSubmitter {
	/** The OpenTSDB host */
	protected final String host;
	/** The OpenTSDB port */
	protected final int port;
	/** The ElasticSearch host */
	protected String esHost = null;
	/** The ElasticSearch port */
	protected int esPort = -1;
	
	/** The socket connected to the host/port */
	protected Socket socket = null;
	/** The http client to submit http ops to the tsdb server */
	protected AsyncHttpClient httpClient = null;
	/** The HTTP base URL for HTTP submitted requests */
	protected String baseURL = null;
	/** The HTTP base URL for ES requests */
	protected String esURL = null;
	
	/** The socket keep-alive flag */
	protected boolean keepAlive = true;
	/** The socket re-use address flag */
	protected boolean reuseAddress = true;
	/** The socket linger flag */
	protected boolean linger = false;
	/** The socket tcp nodelay flag */
	protected boolean tcpNoDelay = true;	
	/** Indicates if times are traced in seconds (true) or milliseconds (false) */
	protected boolean traceInSeconds = true;
	/** Indicates if traces should be logged */
	protected boolean logTraces = false;
	/** Indicates if traces are disabled */
	protected boolean disableTraces = false;
	
	private static final SLogger LOG = SimpleLogger.logger(TSDBSubmitter.class);
	
	/** The ObjectName transform cache */
	protected final TransformCache transformCache = new TransformCache();
	
	/** The sequential root tags applied to all traced metrics */
	protected final Set<String> rootTags = new LinkedHashSet<String>();
	/** The root tags map applied to all traced metrics */
	protected final Map<String, String> rootTagsMap = new LinkedHashMap<String, String>();
	
	/** Deltas for long values */
	protected final NonBlockingHashMap<String, Long> longDeltas = new NonBlockingHashMap<String, Long>(); 
	/** Deltas for double values */
	protected final NonBlockingHashMap<String, Double> doubleDeltas = new NonBlockingHashMap<String, Double>(); 
	/** Deltas for int values */
	protected final NonBlockingHashMap<String, Integer> intDeltas = new NonBlockingHashMap<String, Integer>(); 
	
	/** The socket receive buffer size in bytes */
	protected int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;
	/** The socket send buffer size in bytes */
	protected int sendBufferSize = DEFAULT_SEND_BUFFER_SIZE;
	/** The socket linger time in seconds */
	protected int lingerTime = -1;
	/** The socket timeout in milliseconds */
	protected int timeout = 1000;
	
	/** The socket output stream */
	protected OutputStream os = null;
	/** The socket input stream */
	protected InputStream is = null;
	/** The buffer for incoming data */
	protected final ChannelBuffer dataBuffer = ChannelBuffers.dynamicBuffer(bufferFactory);
	/** A counter of traces between each flush */
	protected final AtomicInteger traceCount = new AtomicInteger(0);
	
	/** The default socket send buffer size in bytes */
	public static final int DEFAULT_SEND_BUFFER_SIZE;
	/** The default socket receive buffer size in bytes */
	public static final int DEFAULT_RECEIVE_BUFFER_SIZE;
	
	/** The default character set */
	public static final Charset CHARSET = Charset.forName("UTF-8");
	
	/** The buffered data direct buffer factory */
	private static final DirectChannelBufferFactory bufferFactory = new DirectChannelBufferFactory(1024); 
	
	static {
		@SuppressWarnings("resource")
		Socket t = new Socket();
		try {
			DEFAULT_SEND_BUFFER_SIZE = t.getSendBufferSize();
			DEFAULT_RECEIVE_BUFFER_SIZE = t.getReceiveBufferSize();
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	
	/**
	 * Creates a new TSDBSubmitter
	 * @param host The OpenTSDB host or ip address
	 * @param port The OpenTSDB listening port 
	 */
	public TSDBSubmitter(final String host, final int port) {
		this.host = host;
		this.port = port;
		socket = new Socket();
		Map<String, String> tags = new LinkedHashMap<String, String>();
//		tags.put("app", "groovy");
//		tags.put("host", "tpsolaris");
//		transformCache.register(JMXHelper.objectName("*:*"), new com.heliosapm.opentsdb.Transformers.DefaultTransformer(tags, null));
		baseURL = "http://" + host + ":" + port + "/";
	}
	
	interface SubmitterFlush {
		void flush();
	}
	
	/**
	 * Creates a new ExpressionResult using the submitter's root tags and that flushes into this submitter's metric buffer.
	 * @return a new ExpressionResult 
	 */
	public ExpressionResult newExpressionResult() {
		final ChannelBuffer _buffer = ChannelBuffers.dynamicBuffer(bufferFactory);
		final SubmitterFlush _flushTarget = new SubmitterFlush() {
			@Override
			public void flush() {
				synchronized(_buffer) {
					synchronized(dataBuffer) {
						dataBuffer.writeBytes(_buffer);
						_buffer.clear();
					}					
				}
			}
		};
		return ExpressionResult.newInstance(rootTagsMap, _buffer, _flushTarget);
	}
	
	/**
	 * Creates a new TSDBSubmitter using the default OpenTSDB port
	 * @param host The OpenTSDB host or ip address
	 */
	public TSDBSubmitter(final String host) {
		this(host, 4242);
	}
	
	public static final String AUTO_RECONECT = "jmx.remote.x.client.autoreconnect";
	
	public void loopOnJVMStats() {
		JMXConnector connector = null;
		addRootTag("host", "ord-pr-ceas-a01")
			.addRootTag("app", "ECS")
			.setLogTraces(false)
			.setTimeout(2000)
			.setTracingDisabled(true);
//			.connect();

		try {
			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe,p=mysol!1");
//			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe");
			connector = JMXConnectorFactory.connect(surl);
//			ReconnectorService.getInstance().autoReconnect(connector, surl, false, null);
			MBeanServerConnection server = connector.getMBeanServerConnection();
			LOG.log("Connected");
			// public void trace(
				//final ObjectName target, 
				//final String metricName, 
				//final Map<String, Object> attributeValues, 
				//final String...objectName
			
			
			
			while(true) {
				try {
					Set<ObjectName> ons = server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null);
					for(final ObjectName on: ons) {
						String[] attrNames  = getAttributeNames(server, on);
//						LOG.log("AttributeNames: %s", Arrays.toString(attrNames));
						AttributeList attrList = server.getAttributes(on, attrNames);
						Map<String, Object> attrValues = new HashMap<String, Object>(attrList.size());
						for(Attribute a: attrList.asList()) {
							attrValues.put(a.getName(), a.getValue());
						}
						trace(on, "java.lang.gc", attrValues, "name", "type");
					}					
					flush();
					try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
				} catch (Exception ex) {
					LOG.log("Loop Error: %s", ex);
					while(true) {
						try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
						try {
							server = connector.getMBeanServerConnection();
							server.getMBeanCount();
							LOG.log("\nReconnected....");
							break;
						} catch (Exception x) {
							System.err.print(".");
						}
					}
				}
			}
		} catch (Exception ex) {
			LOG.loge("Unexpected Loop Exit: %s", ex);
		} finally {
			if(connector!=null) try { connector.close(); } catch (Exception x) {/* No Op */}
			try { this.close(); } catch (Exception x) {/* No Op */}
		}
	}
	
	public static void main(String[] args) {
		try {
			LOG.log("Submitter Test");
			TSDBSubmitter submitter = new TSDBSubmitter("localhost", 4242);
			submitter.loopOnJVMStats();
//			final MBeanServer ser = ManagementFactory.getPlatformMBeanServer();
//	//		TSDBSubmitter submitter = new TSDBSubmitteklr("opentsdb", 8080).addRootTag("host", "ord-pr-ceas-a01").addRootTag("app", "ECS").setLogTraces(true).setTimeout(2000).connect();
//			TSDBSubmitter submitter = new TSDBSubmitter("localhost", 4242).addRootTag("host", "ord-pr-ceas-a01").addRootTag("app", "ECS").setLogTraces(true).setTimeout(2000).connect();
//	//		TSDBSubmitter submitter = new TSDBSubmitter("localhost").addRootTag("host", "nicholas").addRootTag("app", "MyApp").setLogTraces(true).setTimeout(2000).connect();
//			LOG.log(submitter);
//			// q = new URL("http://opentsdb:8080/api/query?start=1m-ago&show_tsuids=true&m=avg:ecsmetric{
//			// host=ord-pr-ceas-a01,app=ECS,metric=InUseConnectionCount,service=ManagedConnectionPool,name=ECS}");
//			long start = System.currentTimeMillis();
//			JSONArray jarr = submitter.query("ecsmetric", "name", "ECS", "service", "ManagedConnectionPool"); //, "metric", "InUseConnectionCount");
//			long elapsed = System.currentTimeMillis() - start;
//			
//			LOG.log("Query Returned In [%s] ms. Results:\n%s", elapsed, jarr.toString(2));
//			
//			start = System.currentTimeMillis();
//			String tsuid = submitter.tsuid("ecsmetric", "name", "ECS", "service", "ManagedConnectionPool", "metric", "InUseConnectionCount");
//			elapsed = System.currentTimeMillis() - start;
//			
//			LOG.log("TSUID Returned In [%s] ms. TSUID: [%s]", elapsed, tsuid);
//			
//			
//			TSDBAnnotation ann = new AnnotationBuilder().setTSUID(tsuid).setDescription("ECS Prod Conn Pool").setCustom("current", "" + 14).build();
//			LOG.log("Annotation:\n%s", ann);
//			submitter.trace(ann);
//			try { Thread.sleep(5000); } catch (Exception x) {}		
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			System.exit(1);
		}
		
//		while(true) {
//			final long start = System.currentTimeMillis();
//			final Map<ObjectName, Map<String, Object>> mbeanMap = new HashMap<ObjectName, Map<String, Object>>(ser.getMBeanCount());
//			for(ObjectName on: ser.queryNames(null, null)) {
//				try {
//					MBeanInfo minfo = ser.getMBeanInfo(on);
//					MBeanAttributeInfo[] ainfos = minfo.getAttributes();					
//					String[] attrNames = new String[ainfos.length];
//					for(int i = 0; i < ainfos.length; i++) {
//						attrNames[i] = ainfos[i].getName();
//					}
//					AttributeList attrList = ser.getAttributes(on, attrNames);
//					Map<String, Object> attrValues = new HashMap<String, Object>(attrList.size());
//					for(Attribute a: attrList.asList()) {
//						attrValues.put(a.getName(), a.getValue());
//					}
//					mbeanMap.put(on, attrValues);
//				} catch (Exception ex) {
//					ex.printStackTrace(System.err);
//				}
//			}
//			LOG.log("Tracing All Attributes for [%s] MBeans", mbeanMap.size());
//			submitter.trace(mbeanMap);
//			
			
//			for(final MemoryPoolMXBean pool: ManagementFactory.getMemoryPoolMXBeans()) {
//				final MemoryUsage mu = pool.getUsage();			
//				final String poolName = pool.getName();
//				submitter.trace("used", mu.getUsed(), "type", "MemoryPool", "name", poolName);
//				submitter.trace("max", mu.getMax(), "type", "MemoryPool", "name", poolName);
//				submitter.trace("committed", mu.getCommitted(), "type", "MemoryPool", "name", poolName);
//			}
//			for(final GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
//				final ObjectName on = gc.getObjectName();
//				final String gcName = gc.getName();
//				final Long count = submitter.longDelta(gc.getCollectionCount(), on.toString(), gcName, "count");
//				final Long time = submitter.longDelta(gc.getCollectionTime(), on.toString(), gcName, "time");
//				if(count!=null) {
//					submitter.trace("collectioncount", count, "type", "GarbageCollector", "name", gcName);
//				}
//				if(time!=null) {
//					submitter.trace("collectiontime", time, "type", "GarbageCollector", "name", gcName);
//				}
//			}
//			submitter.flush();
//			final long elapsed = System.currentTimeMillis() - start;
//			LOG.log("Completed flush in %s ms", elapsed);
//			System.gc();
//			try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
//		}		
	}
	
	/**
	 * Indicates if the submitter is connected
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return socket.isConnected();
	}
	
	/**
	 * Connects this submitter.
	 * Does nothing if already connected.
	 * @return this submitter
	 * @throws RuntimeException if connection fails
	 */
	public TSDBSubmitter connect() {
		try {
			if(socket.isConnected()) return this;
			LOG.log("Connecting to [%s:%s]....", host, port);
			socket = new Socket();
			socket.setKeepAlive(keepAlive);
			socket.setReceiveBufferSize(receiveBufferSize);
			socket.setSendBufferSize(sendBufferSize);
			socket.setReuseAddress(reuseAddress);
			socket.setSoLinger(linger, lingerTime);
			socket.setSoTimeout(timeout);
			socket.setTcpNoDelay(tcpNoDelay);
			socket.connect(new InetSocketAddress(host, port));
			LOG.log("Connected to [%s:%s]", host, port);
			os = socket.getOutputStream();
			is = socket.getInputStream();
			httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setAllowPoolingConnection(true).setConnectionTimeoutInMs(2000).build());
			LOG.log("Version: %s", getVersion());
			return this;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to connect to [" + host + ":" + port + "]", ex);
		}
	}
	
	private final Set<StringBuilder> SBs = new CopyOnWriteArraySet<StringBuilder>();
	
	private final ThreadLocal<StringBuilder> SB = new ThreadLocal<StringBuilder>() {
		@Override
		protected StringBuilder initialValue() {
			final StringBuilder b = new StringBuilder(1024);
			SBs.add(b);
			return b;
		}
	};
	
	public StringBuilder getSB() {
		StringBuilder b = SB.get();
		b.setLength(0);
		return b;
	}
	
	/**
	 * Returns the current time.
	 * @return the current time in seconds if {@link #traceInSeconds} is true, otherwise in milliseconds
	 */
	public long time() {
		return traceInSeconds ? System.currentTimeMillis()/1000 : System.currentTimeMillis(); 
	}
	
	/**
	 * Traces a double metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(final String metric, final double value, final Map<String, String> tags) {
		// put $metric $now $value host=$HOST "
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric)).append(" ").append(time()).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final byte[] trace = b.deleteCharAt(b.length()-1).append("\n").toString().getBytes(CHARSET);
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);
			traceCount.incrementAndGet();
		}
	}
	

	
	/**
	 * Traces a double metric
	 * @param metric The full metric as a JMX {@link ObjectName}
	 * @param value The value
	 */
	public void trace(final ObjectName metric, final double value) {
		if(metric==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(metric==null || metric.isPattern()) return;
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric.getDomain())).append(" ").append(time()).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: metric.getKeyPropertyList().entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final String s = b.deleteCharAt(b.length()-1).append("\n").toString();
		if(logTraces) LOG.log("Trace: [%s]", s.trim());
		final byte[] trace = s.getBytes(CHARSET);		
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);
			traceCount.incrementAndGet();
		}
	}
	
	/**
	 * Yet another tracing overload
	 * @param target The target ObjectName that was sampled
	 * @param metricName The desgnted metric name
	 * @param attributeValues The collected values keyed by the attribute name
	 * @param objectNameKeys The keys of the target ObjectName to add to the tags
	 */
	public void trace(final ObjectName target, final String metricName, final Map<String, Object> attributeValues, final String...objectNameKeys) {
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		if(objectNameKeys==null || objectNameKeys.length==0) throw new IllegalArgumentException("At least one ObjectName Key is required");
		if(attributeValues==null || attributeValues.isEmpty()) return;		
		final String m = (metricName==null || metricName.trim().isEmpty()) ? target.getDomain() : metricName.trim();
		final Map<String, String> tags = new LinkedHashMap<String, String>(rootTagsMap);
		int keyCount = 0;
		boolean all = (objectNameKeys.length==1 && "*".equals(objectNameKeys[0]));
		if(all) {
			for(Map.Entry<String, String> entry: target.getKeyPropertyList().entrySet()) {
				tags.put(clean(entry.getKey()), clean(entry.getValue()));
				keyCount++;
			}
		} else {
			for(String key: objectNameKeys) {
				if(key==null || key.trim().isEmpty()) continue;
				String v = clean(target, key.trim());
				if(v==null || v.isEmpty()) continue;
				tags.put(clean(key), clean(v));
				keyCount++;			
			}			
		}
		if(keyCount==0) throw new IllegalArgumentException("No ObjectName Keys Usable as Tags. Keys: " + Arrays.toString(objectNameKeys) + ", ObjectName: [" + target.toString() + "]");
		
		for(Map.Entry<String, Object> attr: attributeValues.entrySet()) {
			final String attributeName = clean(attr.getKey());
			try {
				tags.put("metric", attributeName);
				final Object v = attr.getValue();
				if(v==null) continue;
				if(v instanceof Number) {
					if(v instanceof Double) {
						trace(m, (Double)v, tags);
					} else {																																			
						trace(m, ((Number)v).longValue(), tags);
					}
				} else if(v instanceof CompositeData) {
					final CompositeData cd = (CompositeData)v;					
					tags.put("ctype", attributeName);					
					try {
						Map<String, Number> cmap = fromOpenType(cd);
						for(Map.Entry<String, Number> ce: cmap.entrySet()) {
							final String key = clean(ce.getKey());							
							tags.put("metric", key);
							try {
								final Number cv = ce.getValue();
								if(v instanceof Double) {
									trace(m, cv.doubleValue(), tags);
								} else {
									trace(m, cv.longValue(), tags);
								}
							} finally {
								tags.put("metric", attributeName);
							}
						}
					} finally {
						tags.remove("ctype");
					}
				}
			} finally {
				tags.remove("metric");
			}
		}

	}
	
	
	/**
	 * Cleans the passed stringy
	 * @param cs The stringy to clean
	 * @return the cleaned stringy
	 */
	public static String clean(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) return "";
		String s = cs.toString().trim();
		final int index = s.indexOf('/');
		if(index!=-1) {
			s = s.substring(index+1);
		}
		return s.replace(" ", "_");
	}
	
	/**
	 * Cleans the object name property identified by the passed key
	 * @param on The ObjectName to extract the value from
	 * @param key The key property name
	 * @return the cleaned key property value
	 */
	public static String clean(final ObjectName on, final String key) {
		if(on==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		return clean(on.getKeyProperty(key.trim()));
	}
	
	/** A cache of TSUIDs keyed by the URL which will be called if the tsuid is not in cache */
	protected final NonBlockingHashMap<String, String> tsuidCache = new NonBlockingHashMap<String, String>(128);
	
	/** The query template to get TSUIDs from a metric name and tags. Tokens are: http server, http port, metric, comma separated key value pairs */
	public static final String QUERY_TEMPLATE = "http://%s:%s/api/query?start=1s-ago&show_tsuids=true&m=avg:%s%s";
	
	/**
	 * Formats the passed metric and tags into a URL with comma separated name/value pair URLEncoded string
	 * @param metric The metric name 
	 * @param tags The tags to format
	 * @return the formatted URL string
	 */
	public String formatTags(final String metric, final String...tags) {
		if(metric==null || metric.trim().isEmpty()) throw new IllegalArgumentException("The passed metric was null or empty");
		if(tags==null || tags.length < 2) throw new IllegalArgumentException("Insufficient number of tags. Must have at least 1 tag, which would be 2 values");
		if(tags.length%2!=0) throw new IllegalArgumentException("Odd number of tag values [" + tags.length + "]. Tag values come in pairs");		
		StringBuilder b = new StringBuilder("{");
		final Map<String, String> tagMap = new LinkedHashMap<String, String>(rootTagsMap);
		for(int i = 0; i < tags.length; i++) {
			String k = tags[i].trim();
			i++;
			String v = tags[i].trim();
			tagMap.put(k, v);
		}
		for(Map.Entry<String, String> entry: tagMap.entrySet()) {
			b.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
		}		
		try {
			final String formattedTags = URLEncoder.encode(b.deleteCharAt(b.length()-1).append("}").toString(), "UTF-8");
			return String.format(QUERY_TEMPLATE, host, port, metric.trim(), formattedTags);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to format " + Arrays.toString(tags), ex);
		}
	}
	
	/**
	 * Issues a synchronous TSDB query for the passed metric and tags.
	 * The intent is for meta only so the start time is <b><code>1s-ago</code></b>. 
	 * @param metric The metric name
	 * @param tags The tags
	 * @return A JSON array of the parsed results
	 */
	public JSONArray query(final String metric, final String...tags) {
		return query(formatTags(metric, tags));
	}
	
	/**
	 * Issues a synchronous TSDB query for the passed metric and tags.
	 * The intent is for meta only so the start time is <b><code>1s-ago</code></b>. 
	 * @param url The prepared and encoded URL 
	 * @return A JSON array of the parsed results
	 */
	public JSONArray query(final String url) {
		try {
			String jsonText = httpClient.prepareGet(url).execute().get(timeout, TimeUnit.MILLISECONDS).getResponseBody("UTF-8");
			return new JSONArray(jsonText);
		} catch (Exception ex) {
			LOG.loge("Failed to retrieve content for [%s] - %s", url, ex);
			throw new RuntimeException(String.format("Failed to retrieve content for [%s] - %s", url, ex), ex);
		}		
	}
	
	
	/**
	 * Issues a synchronous TSDB query for the passed metric and tags to find a single TSUID.
	 * In other words, if zero or more than one TSUIDs are returned, it's an error. 
	 * @param metric The metric name
	 * @param tags The tags
	 * @return A JSON array of the parsed results
	 */
	public String tsuid(final String metric, final String...tags) {
		final String key = formatTags(metric, tags);
		String tsuid = tsuidCache.get(key);
		if(tsuid==null) {
			JSONArray jarr = query(key);
			if(jarr.length()==0) throw new RuntimeException("No matches");
			JSONObject m = (JSONObject) jarr.get(0);
			JSONArray tsuidArr = m.getJSONArray("tsuids");
			if(tsuidArr.length()==0) throw new RuntimeException("No matches");
			if(tsuidArr.length()>1) throw new RuntimeException("More than one match (" + tsuidArr.length() + ")");
			tsuid = tsuidArr.getString(0);
			tsuidCache.put(key, tsuid);
		}
		return tsuid;
	}
	
	/**
	 * Retrieves the attribute names of the MBean registered in the passed MBeanServer
	 * @param conn The connection to the remote MBeanServer
	 * @param target The JMX ObjectName of the target MBean
	 * @return An array of attribute names
	 */
	public String[] getAttributeNames(final MBeanServerConnection conn, final ObjectName target) {
		try {
			MBeanAttributeInfo[] attrInfos = conn.getMBeanInfo(target).getAttributes();
			String[] attrNames = new String[attrInfos.length];
			for(int i = 0; i < attrInfos.length; i++) {
				attrNames[i] = attrInfos[i].getName();
			}
			return attrNames;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Traces a tsdb annotation
	 * @param annotation the annotation to trace
	 */	
	public void trace(final TSDBAnnotation annotation) {
		if(annotation==null) throw new IllegalArgumentException("The passed annotation was null");
		try {
			final long start = System.currentTimeMillis();
			httpClient.preparePost(baseURL + "api/annotation").setBody(annotation.toJSON()).execute(new AsyncHandler<Object>(){
				protected HttpResponseStatus responseStatus = null;
				@Override
				public void onThrowable(final Throwable t) {
					LOG.loge("Async failure on annotation send for [%s] - %s", annotation, t);
				}

				@Override
				public STATE onBodyPartReceived(final HttpResponseBodyPart bodyPart) throws Exception {
					return null;
				}

				@Override
				public STATE onStatusReceived(final HttpResponseStatus responseStatus) throws Exception {
					this.responseStatus = responseStatus;
					return null;
				}

				@Override
				public STATE onHeadersReceived(final HttpResponseHeaders headers) throws Exception {
					return null;
				}

				@Override
				public Object onCompleted() throws Exception {
					long elapsed = System.currentTimeMillis() - start;
					LOG.log("Annotation Send Complete in [%s] ms. Response: [%s], URI: [%s]", elapsed, responseStatus.getStatusText(), responseStatus.getUrl());
					return null;
				}				
			});
		} catch (Exception ex) {
			LOG.loge("Failed to send annotation [%s] - %s", annotation, ex);
		}
	}
		
	
	/**
	 * Traces a double metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(final String metric, final double value, final String...tags) {
		if(tags==null) return;
		if(tags.length%2!=0) throw new IllegalArgumentException("The tags varg " + Arrays.toString(tags) + "] has an odd number of values");
		final int pairs = tags.length/2;
		final Map<String, String> map = new LinkedHashMap<String, String>(pairs);
		for(int i = 0; i < tags.length; i++) {
			map.put(tags[i], tags[++i]);
		}
		trace(metric, value, map);
	}
	
	
	/**
	 * Traces a long metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(final String metric, final long value, final Map<String, String> tags) {
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric)).append(" ").append(time()).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final String s = b.deleteCharAt(b.length()-1).append("\n").toString();
		if(logTraces) LOG.log("Trace: [%s]", s.trim());
		final byte[] trace = s.getBytes(CHARSET);
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);
			traceCount.incrementAndGet();
		}
	}
	
	/**
	 * Traces a long metric
	 * @param metric The full metric as a JMX {@link ObjectName}
	 * @param value The value
	 */
	public void trace(final ObjectName metric, final long value) {
		if(metric==null || metric.isPattern()) return;
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric.getDomain())).append(" ").append(time()).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: metric.getKeyPropertyList().entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final String s = b.deleteCharAt(b.length()-1).append("\n").toString();
		if(logTraces) LOG.log("Trace: [%s]", s.trim());
		final byte[] trace = s.getBytes(CHARSET);
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);
			traceCount.incrementAndGet();
		}
	}
	
	public void registerTransformer(final TSDBJMXResultTransformer transformer, final ObjectName on) {
		transformCache.register(on, transformer);
	}
	
	/**
	 * Traces raw JMX BatchService lookup results
	 * @param batchResults A map of JMX attribute values keyed by the attribute name within a map keyed by the ObjectName
	 */
	public void trace(final Map<ObjectName, Map<String, Object>> batchResults) {
		if(batchResults==null || batchResults.isEmpty()) return;
		for(Map.Entry<ObjectName, Map<String, Object>> entry: batchResults.entrySet()) {
			final ObjectName on = entry.getKey();
			final Map<String, Object> keyValuePairs = entry.getValue();
			TSDBJMXResultTransformer transformer = transformCache.getTransformer(on);
			if(transformer!=null) {
				 Map<ObjectName, Number> transformed = transformer.transform(on, keyValuePairs);
				 for(Map.Entry<ObjectName, Number> t: transformed.entrySet()) {
					 final Number v = t.getValue();
					 if(v==null) continue;
					 if(v instanceof Double) {
						 trace(t.getKey(), v.doubleValue());
					 } else {
						 trace(t.getKey(), v.longValue());
					 }
				 }
			} else {
				for(Map.Entry<String, Object> attr: entry.getValue().entrySet()) {
					final Object v = attr.getValue();
					if(v==null) continue;
					if(v instanceof Number) {
						if(v instanceof Double) {
							trace(JMXHelper.objectName(clean(new StringBuilder(on.toString()).append(",metric=").append(attr.getKey()))), ((Number)v).doubleValue());
						} else {
							trace(JMXHelper.objectName(clean(new StringBuilder(on.toString()).append(",metric=").append(attr.getKey()))), ((Number)v).longValue());
						}
					} else if(v instanceof CompositeData) {
						Map<ObjectName, Number> cmap = fromOpenType(on, (CompositeData)v);
						for(Map.Entry<ObjectName, Number> ce: cmap.entrySet()) {
							final Number cv = ce.getValue();
							if(v instanceof Double) {
								trace(ce.getKey(), cv.doubleValue());
							} else {
								trace(ce.getKey(), cv.longValue());
							}
						}
					} 
				}
			}
		}
	}
	
	
	/**
	 * Decomposes a composite data type so it can be traced
	 * @param objectName The ObjectName of the MBean the composite data came from
	 * @param cd The composite data instance
	 * @return A map of values keyed by synthesized ObjectNames that represent the structure down to the numeric composite data items
	 */
	protected Map<ObjectName, Number> fromOpenType(final ObjectName objectName, final CompositeData cd) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		if(cd==null) throw new IllegalArgumentException("The passed CompositeData was null");
		final Map<ObjectName, Number> map = new HashMap<ObjectName, Number>();
		final CompositeType ct = cd.getCompositeType();
		for(final String key: ct.keySet()) {
			final Object value = cd.get(key);
			if(value==null || !(value instanceof Number)) continue;
			StringBuilder b = new StringBuilder(objectName.toString());
			b.append(",ctype=").append(simpleName(ct.getTypeName()));
			b.append(",metric=").append(key);			
			ObjectName on = JMXHelper.objectName(clean(b));
			map.put(on, (Number)value);
			
		}
		return map;
	}
	
	/**
	 * Decoposes the passed composite data instance to a map of numeric values keyed by the composite type key
	 * @param cd The composite data instance
	 * @return a map of numeric values keyed by the composite type key
	 */
	protected Map<String, Number> fromOpenType(final CompositeData cd) {
		if(cd==null) return Collections.emptyMap();
		final Map<String, Number> map = new LinkedHashMap<String, Number>();
		final CompositeType ct = cd.getCompositeType();
		for(final String key: ct.keySet()) {
			final Object value = cd.get(key);
			if(value!=null && (value instanceof Number)) {
				map.put(key, (Number)value);
			}
		}
		return map;
	}

	
	private static String simpleName(final CharSequence s) {
		if(s==null) return null;
		String str = clean(s);
		final int index = str.lastIndexOf('.');
		return index==-1 ? str : str.substring(index+1);
	}
	

	
	/**
	 * Traces a long metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(final String metric, final long value, final String...tags) {
		if(tags==null) return;
		if(tags.length%2!=0) throw new IllegalArgumentException("The tags varg " + Arrays.toString(tags) + "] has an odd number of values");
		final int pairs = tags.length/2;
		final Map<String, String> map = new LinkedHashMap<String, String>(pairs);
		for(int i = 0; i < tags.length; i++) {
			map.put(tags[i], tags[++i]);
		}
		trace(metric, value, map);
	}
	
	

	/**
	 * Flushes the databuffer to the socket output stream and on success, clears the data buffer
	 * @return the number of bytes flushed
	 */
	public int[] flush() {
		final long startTime = System.currentTimeMillis();
		final int[] bytesWritten = new int[]{0, 0};
//		GZIPOutputStream gzip = null;
		synchronized(dataBuffer) {
			if(dataBuffer.readableBytes()<1) return bytesWritten;
			int pos = -1;
			try {				
				final int r = dataBuffer.readableBytes();
//				gzip = new GZIPOutputStream(os, r * 2);
				pos = dataBuffer.readerIndex();
				if(os!=null) {
					dataBuffer.readBytes(os, dataBuffer.readableBytes());
					os.flush();
				}
//				gzip.finish();
//				gzip.flush();
				
				dataBuffer.clear();
				bytesWritten[0] = r;
				bytesWritten[1] = traceCount.getAndSet(0);
				long elapsed = System.currentTimeMillis() - startTime;
				LOG.log("Flushed %s traces in %s bytes. Elapsed: %s ms.", bytesWritten[1], r, elapsed);
			} catch (Exception ex) {
				LOG.log("Failed to flush. Stack trace follows...");
				ex.printStackTrace(System.err);
				if(pos!=-1) dataBuffer.readerIndex(pos);
			} finally {
//				if(gzip!=null) try { gzip.close(); } catch (Exception x) {/* No Op */}
			}
		}
		return bytesWritten;
	}
	/**
	 * Returns the connected OpenTSDB version
	 * @return the connected OpenTSDB version
	 */
	public String getVersion() {
		if(!socket.isConnected()) {
			return "Not Connected";
		}
		try {
			os.write("version\n".getBytes());
			os.flush();
			byte[] b = new byte[1024];
			int bytesRead = is.read(b);
			return new String(b, 0, bytesRead);				
		} catch (Exception x) {
			return "Failed to get version from [" + host + ":" + port + "]" + x;
		}
	}
	
	/**
	 * Closes the submitter's connection
	 */
	public void close() {
		try {
			socket.close();
		} catch (Exception x) {
			/* No Op */
		} finally {
			for(StringBuilder b: SBs) {
				b.setLength(0);
				b.trimToSize();
			}
		}
	}
	
	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Double doubleDelta(final double value, final String...id) {
		Double state = null;
		if(id==null || id.length==0) return null;
		if(id.length==1) {
			state = doubleDeltas.put(id[0], value);
		} else {
			state = doubleDeltas.put(StringHelper.fastConcat(id), value);			
		}
		if(state!=null) {
			double delta = value - state;
			if(delta<0) {
				return null;
			}
			return delta;
		}
		return null;		
	}
	
	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Long longDelta(final long value, final String...id) {
		Long state = null;
		if(id==null || id.length==0) return null;
		if(id.length==1) {
			state = longDeltas.put(id[0], value);
		} else {
			state = longDeltas.put(StringHelper.fastConcat(id), value);			
		}
		if(state!=null) {
			long delta = value - state;
			if(delta<0) {
				return null;
			}
			return delta;
		}
		return null;		
	}
	
	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Integer longInteger(final int value, final String...id) {
		Integer state = null;
		if(id==null || id.length==0) return null;
		if(id.length==1) {
			state = intDeltas.put(id[0], value);
		} else {
			state = intDeltas.put(StringHelper.fastConcat(id), value);			
		}
		if(state!=null) {
			int delta = value - state;
			if(delta<0) {
				return null;
			}
			return delta;
		}
		return null;		
	}
	
	
	/**
	 * Flushes all the delta states
	 */
	public void flushDeltas() {
		longDeltas.clear();
		intDeltas.clear();
		doubleDeltas.clear();
	}
	
	

	/**
	 * Sets the keep alive flag on the socket 
	 * @param keepAlive true to enable SO_KEEPALIVE, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	/**
	 * Sets the socket address reuse
	 * @param reuseAddress true to reuse address, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setReuseAddress(final boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
		return this;
	}



	/**
	 * Sets the socket tcp no delay
	 * @param tcpNoDelay true to enable no-delay, false to disable (and enable Nagle's algorithm)
	 * @return this submitter
	 */
	public TSDBSubmitter setTcpNoDelay(final boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
		return this;
	}

	/**
	 * Sets the socket receive buffer size in bytes
	 * @param receiveBufferSize the receive buffer size to set
	 * @return this submitter
	 */
	public TSDBSubmitter setReceiveBufferSize(final int receiveBufferSize) {
		this.receiveBufferSize = receiveBufferSize;
		return this;
	}

	/**
	 * Sets the socket send buffer size in bytes
	 * @param sendBufferSize the send buffer size to set
	 * @return this submitter
	 */
	public TSDBSubmitter setSendBufferSize(final int sendBufferSize) {
		this.sendBufferSize = sendBufferSize;
		return this;
	}

	/**
	 * Indicates if a socket should linger after close, and if so, for how long in seconds.
	 * @param linger true to linger, false otherwise
	 * @param lingerTime the lingerTime to set
	 * @return this submitter
	 */
	public TSDBSubmitter setLingerTime(final boolean linger, final int lingerTime) {
		this.linger = linger;
		this.lingerTime = linger ? lingerTime : -1;
		return this;		
	}

	/**
	 * Sets the socket timeout in ms.
	 * @param timeout the timeout to set
	 * @return this submitter
	 */
	public TSDBSubmitter setTimeout(final int timeout) {
		this.timeout = timeout;
		return this;
	}
	
	
	
	
	
	/**
	 * Returns the target host
	 * @return the target host
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Returns the target port
	 * @return the target port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Returns the socket keep alive
	 * @return the keepAlive
	 */
	public boolean isKeepAlive() {
		return keepAlive;
	}

	/**
	 * Returns the socket address reuse flag
	 * @return the reuseAddress
	 */
	public boolean isReuseAddress() {
		return reuseAddress;
	}

	/**
	 * Returns the socket linger flag
	 * @return the linger
	 */
	public boolean isLinger() {
		return linger;
	}

	/**
	 * Returns the tcp no delay
	 * @return the tcpNoDelay
	 */
	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	/**
	 * Returns the socket receive buffer size in bytes
	 * @return the receiveBufferSize
	 */
	public int getReceiveBufferSize() {
		return receiveBufferSize;
	}

	/**
	 * Returns the socket send buffer size in bytes
	 * @return the sendBufferSize
	 */
	public int getSendBufferSize() {
		return sendBufferSize;
	}

	/**
	 * Returns the socket linger time in seconds
	 * @return the lingerTime
	 */
	public int getLingerTime() {
		return lingerTime;
	}

	/**
	 * Returns the socket timeout in milliseconds
	 * @return the timeout
	 */
	public int getTimeout() {
		return timeout;
	}


	/**
	 * Indicates if TSDB times are traced in seconds, or milliseconds
	 * @return true if TSDB times are traced in seconds, false if in milliseconds 
	 */
	public boolean isTraceInSeconds() {
		return traceInSeconds;
	}


	/**
	 * Sets the tracing time unit
	 * @param traceInSeconds true to trace in seconds, false to trace in milliseconds
	 * @return this submitter
	 */
	public TSDBSubmitter setTraceInSeconds(final boolean traceInSeconds) {
		this.traceInSeconds = traceInSeconds;
		return this;
	}


	/**
	 * Returns the root tags
	 * @return the rootTags
	 */
	public Set<String> getRootTags() {
		return Collections.unmodifiableSet(rootTags);
	}
	
	/**
	 * Adds a root tag
	 * @param key The root tag key
	 * @param value The root tag value
	 * @return this submitter
	 */
	public TSDBSubmitter addRootTag(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		rootTags.add(clean(key) + "=" + clean(value));
		rootTagsMap.put(clean(key), clean(value));
		return this;
	}
	
	/**
	 * Appends the root tags to the passed buffer
	 * @param b The buffer to append to
	 * @return the same buffer
	 */
	protected StringBuilder appendRootTags(final StringBuilder b) {
		if(b==null) throw new IllegalArgumentException("The passed string builder was null");
		if(!rootTags.isEmpty()) {
			for(String tag: rootTags) {
				b.append(tag).append(" ");
			}
		}
		return b;
	}
	
	/**
	 * Indicates if traces are being logged 
	 * @return true if traces are being logged, false otherwise
	 */
	public boolean isLogTraces() {
		return logTraces;
	}


	/**
	 * Sets the trace logging
	 * @param logTraces true to trace logs, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setLogTraces(final boolean logTraces) {
		this.logTraces = logTraces;
		return this;
	}
	
	/**
	 * Indicates if tracing is disabled
	 * @return true if tracing is disabled, false otherwise
	 */
	public boolean isTracingDisabled() {
		return disableTraces;
	}


	/**
	 * Enables or disables actual tracing. To view what would be traced,
	 * without actually tracing, set {@link #setLogTraces(boolean)} to true
	 * and this to false;
	 * @param disableTraces true to disable, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setTracingDisabled(final boolean disableTraces) {
		this.disableTraces = disableTraces;
		return this;
	}
	
	/**
	 * Returns the configured ES host or IP adddress
	 * @return the configured ES host
	 */
	public String getEsHost() {
		return esHost;
	}

	/**
	 * Sets the ES host or ip address
	 * @param esHost the ES host or ip address
	 * @return this submitter
	 */
	public TSDBSubmitter setEsHost(final String esHost) {
		this.esHost = esHost;
		return this;
	}

	/**
	 * Returns the configured ES http port
	 * @return the configured ES http port
	 */
	public int getEsPort() {
		return esPort;
	}

	/**
	 * Sets the ES http port
	 * @param esPort the ES http port to set
	 * @return this submitter
	 */
	public TSDBSubmitter setEsPort(final int esPort) {
		this.esPort = esPort;
		return this;
	}


	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TSDBSubmitter [");
		if (host != null) {
			builder.append("\n\thost=");
			builder.append(host);
		}
		builder.append("\n\tport=");
		builder.append(port);
		if(esHost!=null) {
			builder.append("\n\tESHost=");
			builder.append(esHost);
			builder.append("\n\tESPort=");
			builder.append(esPort);			
		}
		builder.append("\n\tlogTraces=");
		builder.append(logTraces);
		builder.append("\n\ttracingDisabled=");
		builder.append(disableTraces);		
		builder.append("\n\tkeepAlive=");
		builder.append(keepAlive);
		builder.append("\n\treuseAddress=");
		builder.append(reuseAddress);
		builder.append("\n\tlinger=");
		builder.append(linger);
		builder.append("\n\ttcpNoDelay=");
		builder.append(tcpNoDelay);
		builder.append("\n\ttraceInSeconds=");
		builder.append(traceInSeconds);
		if (rootTags != null) {
			builder.append("\n\trootTags=");
			builder.append(rootTags);
		}
		builder.append("\n\treceiveBufferSize=");
		builder.append(receiveBufferSize);
		builder.append("\n\tsendBufferSize=");
		builder.append(sendBufferSize);
		builder.append("\n\tlingerTime=");
		builder.append(lingerTime);
		builder.append("\n\ttimeout=");
		builder.append(timeout);
		if (dataBuffer != null) {
			builder.append("\n\tdataBufferCapacity=");
			builder.append(dataBuffer.capacity());
		}
		builder.append("]");
		return builder.toString();
	}


}


/*
import org.json.*;
q = new URL("http://localhost:4242/api/search/annotation?query=description:ECS*");
println q.getText();
/*
q = new URL("http://opentsdb:8080/api/query?start=1m-ago&show_tsuids=true&m=avg:ecsmetric{host=ord-pr-ceas-a01,app=ECS,metric=InUseConnectionCount,service=ManagedConnectionPool,name=ECS}");
jsonText = q.getText();
//println jsonText;
json = new JSONArray(jsonText);
println json.toString(2);
println "=========================================================";
if(json.length() != 1) throw new Exception("Result did not contain exactly ONE metric");
metric = json.get(0);
println metric.getClass().getName();
println metric.getJSONArray("tsuids").getString(0);
*/



/*
========================================================
SAMPLE TO HTTP POST METRICS
========================================================
import org.json.*;
import com.ning.http.client.*;

r = new Random(System.currentTimeMillis());

body = new JSONArray();
met = new JSONObject();
long now = System.currentTimeMillis()/1000;
met.put("metric", "sys.cpu.nice");
met.put("timestamp", now);
met.put("value", Math.abs(r.nextInt(100)));
met.put("tags", new JSONObject(
    [
        "host" : "bamboozle",
        "dc"    : "central"
    ]
));
body.put(met);
met = new JSONObject();
now = System.currentTimeMillis()/1000;
met.put("metric", "sys.cpu.nice");
met.put("timestamp", now);
met.put("value", Math.abs(r.nextInt(100)));
met.put("tags", new JSONObject(
    [
        "host" : "slamdingle",
        "dc"    : "central"
    ]
));
body.put(met);
//println body.toString(2);

BASE_URL = "http://localhost:8070/";

httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setAllowPoolingConnection(true).setConnectionTimeoutInMs(2000).build());
httpClient.preparePost(BASE_URL + "api/put").setBody(body.toString()).execute();



*/