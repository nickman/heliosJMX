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
package com.heliosapm.opentsdb;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.opentsdb.AnnotationBuilder.TSDBAnnotation;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;

/**
 * <p>Title: TSDBSubmitterImpl</p>
 * <p>Description: The default {@link TSDBSubmitter} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.TSDBSubmitterImpl</code></p>
 */

class TSDBSubmitterImpl implements TSDBSubmitter {
	
	/** The underlying TSDBSubmitterConnection */
	final TSDBSubmitterConnection tsdbConnection;
	/** This submitter's tracing buffer */
	final ChannelBuffer dataBuffer;
	/** Indicates if times are traced in seconds (true) or milliseconds (false) */
	protected boolean traceInSeconds = true;
	/** Indicates if traces should be logged */
	protected boolean logTraces = false;
	/** Indicates if traces are disabled */
	protected boolean disableTraces = false;
	/** The sequential root tags applied to all traced metrics */
	protected final Set<String> rootTags = new LinkedHashSet<String>();
	/** The root tags map applied to all traced metrics */
	protected final Map<String, String> rootTagsMap = new LinkedHashMap<String, String>();
	/** Filter in map defs */
	protected final NonBlockingHashMap<String, Map<String, String>> filterIns = new NonBlockingHashMap<String, Map<String, String>>(); 
	

	/** Instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	/** The default character set */
	public static final Charset CHARSET = Charset.forName("UTF-8");
	/** The query template to get TSUIDs from a metric name and tags. Tokens are: http server, http port, metric, comma separated key value pairs */
	public static final String QUERY_TEMPLATE = "http://%s:%s/api/query?start=1s-ago&show_tsuids=true&m=avg:%s%s";
	

	
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
	
	/**
	 * Cleans a simple name
	 * @param s The simple name stringy
	 * @return the cleaned name
	 */
	public static String simpleName(final CharSequence s) {
		if(s==null) return null;
		String str = clean(s);
		final int index = str.lastIndexOf('.');
		return index==-1 ? str : str.substring(index+1);
	}
	
	

	/**
	 * Creates a new TSDBSubmitterImpl
	 * @param tsdbConnection The underlying TSDBSubmitterConnection
	 */
	TSDBSubmitterImpl(final TSDBSubmitterConnection tsdbConnection) {
		if(tsdbConnection==null) throw new IllegalArgumentException("The passed TSDBSubmitterConnection was null");
		this.tsdbConnection = tsdbConnection;
		this.dataBuffer = this.tsdbConnection.newChannelBuffer();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#newExpressionResult()
	 */
	@Override
	public ExpressionResult newExpressionResult() {
		final ChannelBuffer _buffer = tsdbConnection.newChannelBuffer();
		return ExpressionResult.newInstance(rootTagsMap, _buffer, tsdbConnection.newSubmitterFlush(_buffer));
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#newExpressionResult(java.util.Map)
	 */
	@Override
	public ExpressionResult newExpressionResult(final Map<String, String> rootTagsMapOverride) {
		final ChannelBuffer _buffer = tsdbConnection.newChannelBuffer();
		return ExpressionResult.newInstance(rootTagsMapOverride, _buffer, tsdbConnection.newSubmitterFlush(_buffer));
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#isConnected()
	 */
	@Override
	public boolean isConnected() {
		return tsdbConnection.isConnected();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#getSB()
	 */
	@Override
	public StringBuilder getSB() {
		return tsdbConnection.getSB();
	}
	
	// =========================================================================================================================
	//    Filter Ins
	// =========================================================================================================================

	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#addFilterIn(java.util.Map)
	 */
	@Override
	public void addFilterIn(final Map<String, String> in) {
		if(in!=null) filterIns.put("*", in);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#addFilterIn(java.lang.String, java.util.Map)
	 */
	@Override
	public void addFilterIn(final String metric, final Map<String, String> in) {
		final String _metric = metric==null ? "*" :
			metric.trim().isEmpty() ? "*" : metric.trim();
		if(in!=null) filterIns.put(_metric, in);
	}
	
	// =========================================================================================================================
	//    Filter Ins Matching
	// =========================================================================================================================

	public boolean matches(final String metric, final Map<String, String> tags) {
		if(metric==null || metric.trim().isEmpty() || tags==null || tags.isEmpty()) return false;
		final String _metric = metric.trim();
		if(filterIns.isEmpty()) return true;
		for(final Map.Entry<String, Map<String, String>> entry: filterIns.entrySet()) {
			final String fmet = entry.getKey();
			if("*".equals(fmet) || _metric.equals(fmet)) {
				for(Map.Entry<String, String> f: entry.getValue().entrySet()) {
					if(!matches(f.getKey(), f.getValue(), tags)) return false;				
				}				
			}
		}
		return true;
	}
	
	/**
	 * Checks a filter entry item against the passed tags
	 * @param key The defined filter key
	 * @param value The defined filter value
	 * @param tags The tag map to verify
	 * @return true for a match, false otherwise
	 */
	protected boolean matches(final String key, final String value, final Map<String, String> tags) {
		final String _value = tags.get(key);
		if(_value==null) return false;
		if("*".equals(value)) return true;
		return _value.equals(value);		
	}
	
	// =========================================================================================================================
	//    Time Gathering
	// =========================================================================================================================

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#time()
	 */
	@Override
	public long time() {
		return traceInSeconds ? System.currentTimeMillis()/1000 : System.currentTimeMillis(); 
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#time(long)
	 */
	@Override
	public long time(final long time) {
		return traceInSeconds ? TimeUnit.SECONDS.convert(time, TimeUnit.MILLISECONDS) : time; 
	}
	
	// =========================================================================================================================
	//    OpenType Mapping
	// =========================================================================================================================
	
	
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
	

	// =========================================================================================================================
	//    Tracing Formats
	// =========================================================================================================================

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
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#formatTags(java.lang.String, java.lang.String[])
	 */
	@Override
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
			return String.format(QUERY_TEMPLATE, tsdbConnection.host, tsdbConnection.port, metric.trim(), formattedTags);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to format " + Arrays.toString(tags), ex);
		}
	}
	
	// =========================================================================================================================
	//    Tracing
	// =========================================================================================================================
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(java.lang.String, double, java.util.Map)
	 */
	@Override
	public void trace(final String metric, final double value, final Map<String, String> tags) {
		trace(time(), metric, value, tags);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(long, java.lang.String, double, java.util.Map)
	 */
	@Override
	public void trace(final long timestamp, final String metric, final double value, final Map<String, String> tags) {
		if(!matches(metric, tags)) return;
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric)).append(" ").append(time()).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final byte[] trace = b.deleteCharAt(b.length()-1).append("\n").toString().getBytes(CHARSET);
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);			
		}
		tsdbConnection.traceCount.incrementAndGet();
	}	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(java.lang.String, long, java.util.Map)
	 */
	@Override
	public void trace(final String metric, final long value, final Map<String, String> tags) {
		trace(time(), metric, value, tags);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(long, java.lang.String, long, java.util.Map)
	 */
	@Override
	public void trace(final long timestamp, final String metric, final long value, final Map<String, String> tags) {
		if(!matches(metric, tags)) return;
		StringBuilder b = getSB();
		b.append("put ").append(clean(metric)).append(" ").append(timestamp).append(" ").append(value).append(" ");
		appendRootTags(b);
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			b.append(clean(entry.getKey())).append("=").append(clean(entry.getValue())).append(" ");
		}
		final byte[] trace = b.deleteCharAt(b.length()-1).append("\n").toString().getBytes(CHARSET);
		synchronized(dataBuffer) {
			dataBuffer.writeBytes(trace);			
		}
		tsdbConnection.traceCount.incrementAndGet();
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(javax.management.ObjectName, double)
	 */
	@Override
	public void trace(final ObjectName metric, final double value) {
		if(metric==null || metric.isPattern()) return;
		trace(time(), metric.getDomain(), value, metric.getKeyPropertyList());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(javax.management.ObjectName, long)
	 */
	@Override
	public void trace(final ObjectName metric, final long value) {
		if(metric==null || metric.isPattern()) return;
		trace(time(), metric.getDomain(), value, metric.getKeyPropertyList());
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(java.lang.String, double, java.lang.String[])
	 */
	@Override
	public void trace(final String metric, final double value, final String... tags) {
		if(tags==null) return;
		if(tags.length%2!=0) throw new IllegalArgumentException("The tags varg " + Arrays.toString(tags) + "] has an odd number of values");
		final int pairs = tags.length/2;
		final Map<String, String> map = new LinkedHashMap<String, String>(pairs);
		for(int i = 0; i < tags.length; i++) {
			map.put(tags[i], tags[++i]);
		}
		if(!matches(metric, map)) return;
		trace(metric, value, map);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(java.lang.String, long, java.lang.String[])
	 */
	@Override
	public void trace(final String metric, final long value, final String... tags) {
		if(tags==null) return;
		if(tags.length%2!=0) throw new IllegalArgumentException("The tags varg " + Arrays.toString(tags) + "] has an odd number of values");
		final int pairs = tags.length/2;
		final Map<String, String> map = new LinkedHashMap<String, String>(pairs);
		for(int i = 0; i < tags.length; i++) {
			map.put(tags[i], tags[++i]);
		}
		if(!matches(metric, map)) return;
		trace(metric, value, map);
	}
	
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(javax.management.ObjectName, java.lang.String, java.util.Map, java.lang.String[])
	 */
	@Override
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
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(com.heliosapm.opentsdb.AnnotationBuilder.TSDBAnnotation)
	 */
	@Override
	public void trace(final TSDBAnnotation annotation) {
		if(annotation==null) throw new IllegalArgumentException("The passed annotation was null");
		try {
			final long start = System.currentTimeMillis();
			tsdbConnection.httpPost("api/annotation", annotation.toJSON(), new AsyncHandler<Object>(){
				protected HttpResponseStatus responseStatus = null;
				@Override
				public void onThrowable(final Throwable t) {
					log.error("Async failure on annotation send for [{}]", annotation, t);
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
					log.info("Annotation Send Complete in [{}] ms. Response: [{}], URI: [{}]", elapsed, responseStatus.getStatusText(), responseStatus.getUrl());
					return null;
				}				
			});
		} catch (Exception ex) {
			log.error("Failed to send annotation [{}]", annotation, ex);
		}
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#trace(java.util.Map)
	 */
	@Override
	public void trace(final Map<ObjectName, Map<String, Object>> batchResults) {
		if(batchResults==null || batchResults.isEmpty()) return;
		for(Map.Entry<ObjectName, Map<String, Object>> entry: batchResults.entrySet()) {
			final ObjectName on = entry.getKey();
			final Map<String, Object> keyValuePairs = entry.getValue();
			TSDBJMXResultTransformer transformer = tsdbConnection.transformCache.getTransformer(on);
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

	


	// =========================================================================================================================
	//    OpenTSDB Queries
	// =========================================================================================================================


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#query(java.lang.String, java.lang.String[])
	 */
	@Override
	public JSONArray query(final String metric, final String... tags) {
		return query(formatTags(metric, tags));
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#query(java.lang.String)
	 */
	@Override
	public JSONArray query(final String url) {		
		try {
			return tsdbConnection.query(url);
		} catch (Exception ex) {
			log.error("Failed to retrieve content for [{}]", url, ex);
			throw new RuntimeException(String.format("Failed to retrieve content for [%s] - %s", url, ex), ex);
		}		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#tsuid(java.lang.String, java.lang.String[])
	 */
	@Override
	public String tsuid(final String metric, final String... tags) {
		return tsdbConnection.tsuid(formatTags(metric, tags));
	}
	
	// =========================================================================================================================
	//    JMX Ops and Queries
	// =========================================================================================================================
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#getAttributeNames(javax.management.MBeanServerConnection, javax.management.ObjectName)
	 */
	@Override
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

	

	
	// =========================================================================================================================
	//    Delta Ops
	// =========================================================================================================================

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#doubleDelta(double, java.lang.String[])
	 */
	@Override
	public Double doubleDelta(final double value, final String... id) {
		return tsdbConnection.doubleDelta(value, id);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#longDelta(long, java.lang.String[])
	 */
	@Override
	public Long longDelta(final long value, final String... id) {		
		return tsdbConnection.longDelta(value, id);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#longInteger(int, java.lang.String[])
	 */
	@Override
	public Integer longInteger(final int value, final String... id) {
		return tsdbConnection.longInteger(value, id);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#flushDeltas()
	 */
	@Override
	public void flushDeltas() {
		tsdbConnection.flushDeltas();
	}
	
	// =========================================================================================================================
	//    Flush Ops
	// =========================================================================================================================
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#flush()
	 */
	@Override
	public void flush() {
		synchronized(dataBuffer) {
			tsdbConnection.acceptFlush(dataBuffer);
		}
	}


	// =========================================================================================================================
	//    Misc Ops
	// =========================================================================================================================	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#tagMap()
	 */
	@Override
	public FluentMap tagMap() {
		return new FluentMap();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#getVersion()
	 */
	@Override
	public String getVersion() {
		return tsdbConnection.getVersion();
	}
	

	// =========================================================================================================================
	//    Submitter Configs
	// =========================================================================================================================	
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#setTraceInSeconds(boolean)
	 */
	@Override
	public TSDBSubmitter setTraceInSeconds(final boolean traceInSeconds) {
		this.traceInSeconds = traceInSeconds;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#isTraceInSeconds()
	 */
	@Override
	public boolean isTraceInSeconds() {
		return this.traceInSeconds;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#addRootTag(java.lang.String, java.lang.String)
	 */
	@Override
	public TSDBSubmitter addRootTag(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		rootTags.add(clean(key) + "=" + clean(value));
		rootTagsMap.put(clean(key), clean(value));
		return this;
	}
	
	public TSDBSubmitter addRootTags(final Map<String, String> tags) {
		if(tags!=null && !tags.isEmpty()) {
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				addRootTag(entry.getKey(), entry.getValue());
			}
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#setLogTraces(boolean)
	 */
	@Override
	public TSDBSubmitter setLogTraces(final boolean logTraces) {
		this.logTraces = logTraces;
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#isLogTraces()
	 */
	@Override
	public boolean isLogTraces() {
		return logTraces;
	}	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#isTracingDisabled()
	 */
	@Override
	public boolean isTracingDisabled() {
		return this.disableTraces;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#setTracingDisabled(boolean)
	 */
	@Override
	public TSDBSubmitter setTracingDisabled(final boolean disableTraces) {
		this.disableTraces = disableTraces;
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.TSDBSubmitter#close()
	 */
	@Override
	public void close() {
		tsdbConnection.close();
	}

}
