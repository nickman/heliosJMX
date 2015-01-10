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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DirectChannelBufferFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.StringHelper;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;

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

public class TSDBSubmitterConnection  {
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
	/** The http client to submit http ops to the tsdb server and ES */
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
	
	private static final Logger LOG = LoggerFactory.getLogger(TSDBSubmitterConnection.class); 

	
	/** The ObjectName transform cache */
	protected final TransformCache transformCache = new TransformCache();
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
	
	protected static final Map<String, TSDBSubmitterConnection> instances = new NonBlockingHashMap<String, TSDBSubmitterConnection>(12);
	
	/**
	 * Acquires a TSDBSubmitterConnection for the passed host and port
	 * @param host The OpenTSDB host or ip address
	 * @param port The OpenTSDB listening port 
	 * @return a TSDBSubmitterConnection for the passed host and port
	 */
	public static TSDBSubmitterConnection getTSDBSubmitterConnection(final String host, final int port) {
		if(host==null || host.trim().isEmpty()) throw new IllegalArgumentException("The passed host name was null or empty");
		final String key = host + ":" + port;
		TSDBSubmitterConnection connection = instances.get(key);
		if(connection==null) {
			synchronized(instances) {
				connection = instances.get(key);
				if(connection==null) {
					connection = new TSDBSubmitterConnection(host, port);
					connection.connect();
					instances.put(key, connection);
				}
			}
		}
		return connection;
	}
	
	/**
	 * Acquires a TSDBSubmitterConnection for the passed host and default port (4242)
	 * @param host The OpenTSDB host or ip address
	 * @return a TSDBSubmitterConnection for the passed host and default port
	 */
	public static TSDBSubmitterConnection getTSDBSubmitterConnection(final String host) {
		return getTSDBSubmitterConnection(host, 4242);
	}
	
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
	 * Creates a new TSDBSubmitterConnection
	 * @param host The OpenTSDB host or ip address
	 * @param port The OpenTSDB listening port 
	 */
	private TSDBSubmitterConnection(final String host, final int port) {
		this.host = host;
		this.port = port;
		socket = new Socket();
		Map<String, String> tags = new LinkedHashMap<String, String>();
//		tags.put("app", "groovy");
//		tags.put("host", "tpsolaris");
//		transformCache.register(JMXHelper.objectName("*:*"), new com.heliosapm.opentsdb.Transformers.DefaultTransformer(tags, null));
		baseURL = "http://" + host + ":" + port + "/";
		final Thread shutdownHook = new Thread() {
			public void run() {
				if(socket!=null) {
					if(socket.isConnected()) {
						try { 
							socket.close(); 
							LOG.warn("Closed Socket [{}:{}] in shutdown hook", host, port);
						} catch (Exception x) {/* No Op */} 
					}
				}
			}
		};
		
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	interface SubmitterFlush {
		void flush();
		void deepFlush();
	}
	
	
	/**
	 * Creates and returns a new SubmitterFlush that flushes to this connection's buffer
	 * @return a new SubmitterFlush that flushes to this connection's buffer	 * 
	 */
	SubmitterFlush newSubmitterFlush(final ChannelBuffer _buffer, final boolean logTraces) {
		final TSDBSubmitterConnection conn = this;
		final SubmitterFlush _flushTarget = new SubmitterFlush() {
			@Override
			public void deepFlush() {
				this.flush();
				conn.flush(logTraces);				
			}
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
		return _flushTarget;
	}
	
	ChannelBuffer newChannelBuffer() {
		return ChannelBuffers.dynamicBuffer(bufferFactory);
	}
	
	
	/**
	 * Creates a new TSDBSubmitter using the default OpenTSDB port
	 * @param host The OpenTSDB host or ip address
	 */
	private TSDBSubmitterConnection(final String host) {
		this(host, 4242);
	}
	
	public static final String AUTO_RECONECT = "jmx.remote.x.client.autoreconnect";
	
//	public void loopOnJVMStats() {
//		JMXConnector connector = null;
//		addRootTag("host", "ord-pr-ceas-a01")
//			.addRootTag("app", "ECS")
//			.setLogTraces(false)
////			.setTimeout(2000)
//			.setTracingDisabled(true);
////			.connect();
//
//		try {
//			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe,p=mysol!1");
////			JMXServiceURL surl = new JMXServiceURL("service:jmx:tunnel://tpsolaris:8006/ssh/jmxmp:u=nwhitehe");
//			connector = JMXConnectorFactory.connect(surl);
////			ReconnectorService.getInstance().autoReconnect(connector, surl, false, null);
//			MBeanServerConnection server = connector.getMBeanServerConnection();
//			LOG.info("Connected");
//			// public void trace(
//				//final ObjectName target, 
//				//final String metricName, 
//				//final Map<String, Object> attributeValues, 
//				//final String...objectName
//			
//			
//			
//			while(true) {
//				try {
//					Set<ObjectName> ons = server.queryNames(JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), null);
//					for(final ObjectName on: ons) {
//						String[] attrNames  = getAttributeNames(server, on);
////						LOG.log("AttributeNames: %s", Arrays.toString(attrNames));
//						AttributeList attrList = server.getAttributes(on, attrNames);
//						Map<String, Object> attrValues = new HashMap<String, Object>(attrList.size());
//						for(Attribute a: attrList.asList()) {
//							attrValues.put(a.getName(), a.getValue());
//						}
//						trace(on, "java.lang.gc", attrValues, "name", "type");
//					}					
//					flush();
//					try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
//				} catch (Exception ex) {
//					LOG.error("Loop Error", ex);
//					while(true) {
//						try { Thread.currentThread().join(5000); } catch (Exception x) {/* No Op */}
//						try {
//							server = connector.getMBeanServerConnection();
//							server.getMBeanCount();
//							LOG.info("\nReconnected....");
//							break;
//						} catch (Exception x) {
//							System.err.print(".");
//						}
//					}
//				}
//			}
//		} catch (Exception ex) {
//			LOG.error("Unexpected Loop Exit", ex);
//		} finally {
//			if(connector!=null) try { connector.close(); } catch (Exception x) {/* No Op */}
//			try { this.close(); } catch (Exception x) {/* No Op */}
//		}
//	}
	
	public static void main(String[] args) {
		try {
			LOG.info("Submitter Test");
			TSDBSubmitterConnection submitter = new TSDBSubmitterConnection("localhost", 4242);
//			submitter.loopOnJVMStats();
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
	 * Indicates if this TSDBSubmitterConnection is connected
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected() {
		return socket.isConnected();
	}
	
	/**
	 * Creates a new Submitter with the passed root tags
	 * @param rootTags The root tags for the new Submitter
	 * @return the new Submitter
	 */
	public TSDBSubmitter submitter(final Map<String, String> rootTags) {		
		return new TSDBSubmitterImpl(this).addRootTags(rootTags);
	}
	
	/**
	 * Creates a new Submitter with no initial root tags
	 * @return the new Submitter
	 */
	public TSDBSubmitter submitter() {
		return submitter(null);
	}
	
	/**
	 * Connects this submitter.
	 * Does nothing if already connected.
	 * @return this submitter
	 * @throws RuntimeException if connection fails
	 */
	public TSDBSubmitterConnection connect() {
		try {
			if(socket.isConnected()) return this;
			LOG.info("Connecting to [{}:{}]....", host, port);
			socket = new Socket();
			socket.setKeepAlive(keepAlive);
			socket.setReceiveBufferSize(receiveBufferSize);
			socket.setSendBufferSize(sendBufferSize);
			socket.setReuseAddress(reuseAddress);
			socket.setSoLinger(linger, lingerTime);
			socket.setSoTimeout(timeout);
			socket.setTcpNoDelay(tcpNoDelay);
			socket.connect(new InetSocketAddress(host, port));
			LOG.info("Connected to [{}:{}]", host, port);
			os = socket.getOutputStream();
			is = socket.getInputStream();
			httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setAllowPoolingConnection(true).setConnectionTimeoutInMs(2000).build());
			LOG.info("Version: {}", getVersion());
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
	
	StringBuilder getSB() {
		return SB.get();
	}
	

	
	
	
	
	/** A cache of TSUIDs keyed by the URL which will be called if the tsuid is not in cache */
	protected final NonBlockingHashMap<String, String> tsuidCache = new NonBlockingHashMap<String, String>(128);
	
	
	
	
	public JSONArray query(final String url) {
		try {
			String jsonText = httpClient.prepareGet(url).execute().get(timeout, TimeUnit.MILLISECONDS).getResponseBody("UTF-8");
			return new JSONArray(jsonText);
		} catch (Exception ex) {
			LOG.error("Failed to retrieve content for [{}]", url, ex);
			throw new RuntimeException(String.format("Failed to retrieve content for [%s] - %s", url, ex), ex);
		}		
	}
	
	
	public String tsuid(final String key) {
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
	 * Prepares and executes an HTTP Post
	 * @param url The URL to post to
	 * @param json The JSON body to post
	 * @param asyncHandler The response handler
	 * @throws IOException thrown on any IO error
	 */
	void httpPost(final String url, final String json, final AsyncHandler<Object> asyncHandler) throws IOException {
		httpClient.preparePost(baseURL + url).setBody(json).execute(asyncHandler);
	}
	
	
	
	
	
	/**
	 * Registers a JMX query result transformer
	 * @param transformer The transformer
	 * @param on The ObjectName to match for this transformer to be applied
	 */
	public void registerTransformer(final TSDBJMXResultTransformer transformer, final ObjectName on) {
		transformCache.register(on, transformer);
	}
	
	


	void acceptFlush(final ChannelBuffer dbuff) {
		if(dbuff!=null) {
			synchronized(dataBuffer) {
				dataBuffer.writeBytes(dbuff);
			}
		}
	}
	

	public int[] flush(final boolean logTraces) {
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
				if(logTraces) {
					LOG.info("\n{}", dataBuffer.toString(CHARSET));
				}
				
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
				LOG.debug("Flushed {} traces in {} bytes. Elapsed: {} ms.", bytesWritten[1], r, elapsed);
			} catch (Exception ex) {
				LOG.error("Failed to flush", ex);
				if(pos!=-1) dataBuffer.readerIndex(pos);
			} finally {
//				if(gzip!=null) try { gzip.close(); } catch (Exception x) {/* No Op */}
			}
		}
		return bytesWritten;
	}

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
			instances.remove(this.host + ":" + this.port);
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
	public TSDBSubmitterConnection setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	/**
	 * Sets the socket address reuse
	 * @param reuseAddress true to reuse address, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setReuseAddress(final boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
		return this;
	}



	/**
	 * Sets the socket tcp no delay
	 * @param tcpNoDelay true to enable no-delay, false to disable (and enable Nagle's algorithm)
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setTcpNoDelay(final boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
		return this;
	}

	/**
	 * Sets the socket receive buffer size in bytes
	 * @param receiveBufferSize the receive buffer size to set
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setReceiveBufferSize(final int receiveBufferSize) {
		this.receiveBufferSize = receiveBufferSize;
		return this;
	}

	/**
	 * Sets the socket send buffer size in bytes
	 * @param sendBufferSize the send buffer size to set
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setSendBufferSize(final int sendBufferSize) {
		this.sendBufferSize = sendBufferSize;
		return this;
	}

	/**
	 * Indicates if a socket should linger after close, and if so, for how long in seconds.
	 * @param linger true to linger, false otherwise
	 * @param lingerTime the lingerTime to set
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setLingerTime(final boolean linger, final int lingerTime) {
		this.linger = linger;
		this.lingerTime = linger ? lingerTime : -1;
		return this;		
	}

	/**
	 * Sets the socket timeout in ms.
	 * @param timeout the timeout to set
	 * @return this submitter
	 */
	public TSDBSubmitterConnection setTimeout(final int timeout) {
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
	public TSDBSubmitterConnection setEsHost(final String esHost) {
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
	public TSDBSubmitterConnection setEsPort(final int esPort) {
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
		builder.append("\n\tkeepAlive=");
		builder.append(keepAlive);
		builder.append("\n\treuseAddress=");
		builder.append(reuseAddress);
		builder.append("\n\tlinger=");
		builder.append(linger);
		builder.append("\n\ttcpNoDelay=");
		builder.append(tcpNoDelay);
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



ES Annotations
===============

#Create an index
curl -XPUT 'http://localhost:9200/events/'     <---  index name: events


#add a mapping to make sure timestamp is automatically saved
curl -XPUT 'http://localhost:9200/alerts/test/_mapping' -d '
{
    "test" : {
        "_timestamp" : { "enabled" : true, "store": "yes" }
    }
}'

#Post some data
curl -XPOST 'http://localhost:9200/alerts/test' -d '{
    "tags" : ["test","start"],
    "message" : "Just a test of events for grafana. Test start. "
}'


Verify
======
$ curl -XGET 'http://localhost:9200/events/test/ZTQ7V-3uRCmb48hwJzPgJg?pretty&fields=_timestamp'
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 171

{
  "_index" : "events",
  "_type" : "test",
  "_id" : "ZTQ7V-3uRCmb48hwJzPgJg",
  "_version" : 1,
  "found" : true,
  "fields" : {
    "_timestamp" : 1410189580537
  }
}





GC Dash
=======
{
  "id": null,
  "title": "MFT Java GC",
  "originalTitle": "MFT Java GC",
  "tags": [
    "app=MFT"
  ],
  "style": "light",
  "timezone": "browser",
  "editable": true,
  "hideControls": true,
  "sharedCrosshair": false,
  "rows": [
    {
      "title": "Row1",
      "height": "120px",
      "editable": true,
      "collapse": false,
      "panels": [
        {
          "title": "% Used: Tenured",
          "error": false,
          "span": 3,
          "editable": true,
          "type": "singlestat",
          "id": 1,
          "links": [],
          "maxDataPoints": 100,
          "interval": null,
          "targets": [
            {
              "errors": {},
              "aggregator": "max",
              "downsampleAggregator": "sum",
              "hide": false,
              "metric": "java.mem",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "phase": "postalloc",
                "metric": "percentUsed",
                "space": "tenured"
              }
            }
          ],
          "cacheTimeout": null,
          "format": "percent",
          "prefix": "",
          "postfix": "",
          "nullText": null,
          "valueMaps": [
            {
              "value": "null",
              "op": "=",
              "text": "Offline"
            }
          ],
          "nullPointMode": "connected",
          "valueName": "current",
          "prefixFontSize": "50%",
          "valueFontSize": "80%",
          "postfixFontSize": "50%",
          "thresholds": "0,50,80",
          "colorBackground": false,
          "colorValue": true,
          "colors": [
            "rgba(50, 172, 45, 0.97)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(245, 54, 54, 0.9)"
          ],
          "sparkline": {
            "show": true,
            "full": false,
            "lineColor": "rgb(31, 120, 193)",
            "fillColor": "rgba(31, 118, 189, 0.18)"
          }
        },
        {
          "title": "% Allocated: Nursery",
          "error": false,
          "span": 3,
          "editable": true,
          "type": "singlestat",
          "id": 2,
          "links": [],
          "maxDataPoints": 100,
          "interval": null,
          "targets": [
            {
              "errors": {},
              "aggregator": "max",
              "downsampleAggregator": "sum",
              "hide": false,
              "metric": "java.mem",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "phase": "postalloc",
                "metric": "percentUsed",
                "space": "nursery"
              }
            }
          ],
          "cacheTimeout": null,
          "format": "percent",
          "prefix": "",
          "postfix": "",
          "nullText": null,
          "valueMaps": [
            {
              "value": "null",
              "op": "=",
              "text": "Offline"
            }
          ],
          "nullPointMode": "connected",
          "valueName": "current",
          "prefixFontSize": "50%",
          "valueFontSize": "80%",
          "postfixFontSize": "50%",
          "thresholds": "0,50,80",
          "colorBackground": false,
          "colorValue": true,
          "colors": [
            "rgba(50, 172, 45, 0.97)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(245, 54, 54, 0.9)"
          ],
          "sparkline": {
            "show": true,
            "full": false,
            "lineColor": "rgb(31, 120, 193)",
            "fillColor": "rgba(31, 118, 189, 0.18)"
          }
        },
        {
          "title": "% Allocated: SOA",
          "error": false,
          "span": 3,
          "editable": true,
          "type": "singlestat",
          "id": 3,
          "links": [],
          "maxDataPoints": 100,
          "interval": null,
          "targets": [
            {
              "errors": {},
              "aggregator": "max",
              "downsampleAggregator": "sum",
              "hide": false,
              "metric": "java.mem",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "phase": "postalloc",
                "metric": "percentUsed",
                "space": "soa"
              }
            }
          ],
          "cacheTimeout": null,
          "format": "percent",
          "prefix": "",
          "postfix": "",
          "nullText": null,
          "valueMaps": [
            {
              "value": "null",
              "op": "=",
              "text": "Offline"
            }
          ],
          "nullPointMode": "connected",
          "valueName": "current",
          "prefixFontSize": "50%",
          "valueFontSize": "80%",
          "postfixFontSize": "50%",
          "thresholds": "0,50,80",
          "colorBackground": false,
          "colorValue": true,
          "colors": [
            "rgba(50, 172, 45, 0.97)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(245, 54, 54, 0.9)"
          ],
          "sparkline": {
            "show": true,
            "full": false,
            "lineColor": "rgb(31, 120, 193)",
            "fillColor": "rgba(31, 118, 189, 0.18)"
          }
        },
        {
          "title": "% Allocated: LOA",
          "error": false,
          "span": 3,
          "editable": true,
          "type": "singlestat",
          "id": 4,
          "links": [],
          "maxDataPoints": 100,
          "interval": null,
          "targets": [
            {
              "errors": {},
              "aggregator": "max",
              "downsampleAggregator": "sum",
              "hide": false,
              "metric": "java.mem",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "phase": "postalloc",
                "metric": "percentUsed",
                "space": "loa"
              }
            }
          ],
          "cacheTimeout": null,
          "format": "percent",
          "prefix": "",
          "postfix": "",
          "nullText": null,
          "valueMaps": [
            {
              "value": "null",
              "op": "=",
              "text": "Offline"
            }
          ],
          "nullPointMode": "connected",
          "valueName": "current",
          "prefixFontSize": "50%",
          "valueFontSize": "80%",
          "postfixFontSize": "50%",
          "thresholds": "0,50,80",
          "colorBackground": false,
          "colorValue": true,
          "colors": [
            "rgba(50, 172, 45, 0.97)",
            "rgba(237, 129, 40, 0.89)",
            "rgba(245, 54, 54, 0.9)"
          ],
          "sparkline": {
            "show": true,
            "full": false,
            "lineColor": "rgb(31, 120, 193)",
            "fillColor": "rgba(31, 118, 189, 0.18)"
          }
        }
      ]
    },
    {
      "title": "Row1",
      "height": "250px",
      "editable": true,
      "collapse": false,
      "panels": [
        {
          "title": "Space % Used: $space",
          "error": false,
          "span": 12,
          "editable": true,
          "type": "graph",
          "id": 5,
          "datasource": null,
          "renderer": "flot",
          "x-axis": true,
          "y-axis": true,
          "y_formats": [
            "percent",
            "short"
          ],
          "grid": {
            "leftMax": 110,
            "rightMax": null,
            "leftMin": 0,
            "rightMin": null,
            "threshold1": 70,
            "threshold2": 90,
            "threshold1Color": "rgba(216, 200, 27, 0.09)",
            "threshold2Color": "rgba(234, 112, 112, 0.07)",
            "thresholdLine": false
          },
          "lines": true,
          "fill": 2,
          "linewidth": 1,
          "points": true,
          "pointradius": 1,
          "bars": false,
          "stack": false,
          "percentage": false,
          "legend": {
            "show": true,
            "values": true,
            "min": true,
            "max": true,
            "current": true,
            "total": false,
            "avg": true,
            "alignAsTable": true
          },
          "nullPointMode": "connected",
          "steppedLine": false,
          "tooltip": {
            "value_type": "cumulative",
            "shared": false
          },
          "targets": [
            {
              "errors": {},
              "aggregator": "max",
              "downsampleAggregator": "sum",
              "metric": "java.mem",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "space": "$space",
                "metric": "percentUsed",
                "phase": "$phase",
                "gctype": "global"
              }
            }
          ],
          "aliasColors": {
            "java.mem{app=MFT, gctype=global, host=mfthost, metric=percentUsed, space=tenured, phase=postalloc}": "#E24D42"
          },
          "seriesOverrides": [],
          "links": [],
          "leftYAxisLabel": "Percent Usage"
        }
      ]
    },
    {
      "title": "Row1",
      "height": "250px",
      "editable": true,
      "collapse": false,
      "panels": [
        {
          "title": "Space % Used: $space",
          "error": false,
          "span": 12,
          "editable": true,
          "type": "graph",
          "id": 6,
          "datasource": null,
          "renderer": "flot",
          "x-axis": true,
          "y-axis": true,
          "y_formats": [
            "ms",
            "short"
          ],
          "grid": {
            "leftMax": null,
            "rightMax": null,
            "leftMin": 0,
            "rightMin": null,
            "threshold1": 10000,
            "threshold2": 15000,
            "threshold1Color": "rgba(216, 200, 27, 0.09)",
            "threshold2Color": "rgba(234, 112, 112, 0.07)",
            "thresholdLine": false
          },
          "lines": true,
          "fill": 2,
          "linewidth": 1,
          "points": true,
          "pointradius": 1,
          "bars": false,
          "stack": false,
          "percentage": false,
          "legend": {
            "show": true,
            "values": true,
            "min": true,
            "max": true,
            "current": true,
            "total": false,
            "avg": true,
            "alignAsTable": true
          },
          "nullPointMode": "connected",
          "steppedLine": false,
          "tooltip": {
            "value_type": "cumulative",
            "shared": false
          },
          "targets": [
            {
              "errors": {},
              "aggregator": "avg",
              "downsampleAggregator": "sum",
              "metric": "java.gc",
              "currentTagKey": "",
              "currentTagValue": "",
              "tags": {
                "host": "mfthost",
                "app": "MFT",
                "metric": "gcElapsed"
              }
            }
          ],
          "aliasColors": {
            "java.gc{app=MFT, host=mfthost, metric=gcElapsed}": "#6ED0E0"
          },
          "seriesOverrides": [],
          "links": [],
          "leftYAxisLabel": "Elapsed Time (ms)"
        }
      ]
    }
  ],
  "nav": [
    {
      "type": "timepicker",
      "enable": true,
      "status": "Stable",
      "time_options": [
        "5m",
        "15m",
        "1h",
        "6h",
        "12h",
        "24h",
        "2d",
        "7d",
        "30d"
      ],
      "refresh_intervals": [
        "5s",
        "10s",
        "30s",
        "1m",
        "5m",
        "15m",
        "30m",
        "1h",
        "2h",
        "1d"
      ],
      "now": true,
      "collapse": false,
      "notice": false
    }
  ],
  "time": {
    "from": "now-15m",
    "to": "now"
  },
  "templating": {
    "list": [
      {
        "type": "custom",
        "datasource": null,
        "refresh_on_load": false,
        "name": "space",
        "options": [
          {
            "text": "tenured",
            "value": "tenured"
          },
          {
            "text": "nursery",
            "value": "nursery"
          },
          {
            "text": "soa",
            "value": "soa"
          },
          {
            "text": "load",
            "value": "load"
          }
        ],
        "includeAll": false,
        "allFormat": "glob",
        "refresh": true,
        "query": "tenured,nursery,soa,load",
        "current": {
          "text": "tenured",
          "value": "tenured"
        }
      },
      {
        "type": "custom",
        "datasource": null,
        "refresh_on_load": false,
        "name": "gctype",
        "options": [
          {
            "text": "scavenger",
            "value": "scavenger"
          },
          {
            "text": "global",
            "value": "global"
          }
        ],
        "includeAll": false,
        "allFormat": "glob",
        "query": "scavenger,global",
        "current": {
          "text": "global",
          "value": "global"
        }
      },
      {
        "type": "custom",
        "datasource": null,
        "refresh_on_load": false,
        "name": "phase",
        "options": [
          {
            "text": "pregc",
            "value": "pregc"
          },
          {
            "text": "postgc",
            "value": "postgc"
          },
          {
            "text": "postalloc",
            "value": "postalloc"
          }
        ],
        "includeAll": false,
        "allFormat": "glob",
        "query": "pregc,postgc,postalloc",
        "current": {
          "text": "postalloc",
          "value": "postalloc"
        }
      }
    ],
    "enable": true
  },
  "annotations": {
    "list": [],
    "enable": true
  },
  "refresh": "5s",
  "version": 6,
  "hideAllLegends": false
}




*/