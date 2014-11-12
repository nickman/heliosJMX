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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.jboss.netty.buffer.ChannelBuffer;

import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.opentsdb.TSDBSubmitter.SubmitterFlush;


/**
 * <p>Title: ExpressionResult</p>
 * <p>Description: A container for a processed expression result and metric accumulator that feeds to the
 * creating TSDBSubmitter on flush.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.ExpressionResult</code></p>
 */
public class ExpressionResult {
	/** The OpenTSDB metric name */
	protected String metricName = null;
	/** The OpenTSDB tags */
	protected Map<String, String> tags = new LinkedHashMap<String, String>(8);
	/** The OpenTSDB root tags that do not get reset */
	protected Map<String, String> rootTags = new LinkedHashMap<String, String>(2);
	
	/** The metric value if a double type */
	protected double dValue = 0D;
	/** The metric value if a long type */
	protected long lValue = 0L;
	/** Indicates if the metric value is a double type */
	protected boolean doubleValue = true;
	
	/** The submitted metric buffer */
	protected final ChannelBuffer buffer;
	/** The flush target where this buffer will flush to */
	protected final SubmitterFlush flushTarget;
	
	/** Fast string builder */
	private static final ThreadLocal<StringBuilder> SB = new ThreadLocal<StringBuilder>() {
		@Override
		protected StringBuilder initialValue() {
			final StringBuilder b = new StringBuilder(1024);
			return b;
		}
	};
	
	/**
	 * Creates a new ExpressionResult
	 * @param rootTags An optional map of root tags
	 * @param buffer The submitted metric buffer
	 * @param flushTarget The target the buffer will be flushed to
	 * @return a new ExpressionResult
	 */
	static ExpressionResult newInstance(final Map<String, String> rootTags, final ChannelBuffer buffer, final SubmitterFlush flushTarget) {
		return new ExpressionResult(rootTags, buffer, flushTarget);
	}
	


	/**
	 * Creates a new ExpressionResult
	 * @param rootTags An optional map of root tags
	 * @param buffer The submitted metric buffer
	 * @param flushTarget The target the buffer will be flushed to
	 */
	private ExpressionResult(final Map<String, String> rootTags, final ChannelBuffer buffer, final SubmitterFlush flushTarget) {
		this.buffer = buffer;
		this.flushTarget = flushTarget;
		if(rootTags!=null && !rootTags.isEmpty()) {
			for(final Map.Entry<String, String> tag: rootTags.entrySet()) {
				this.rootTags.put(clean(tag.getKey()), clean(tag.getValue()));
			}
		}
	}
	
	public void process(Map<String, Object> attrValues, ObjectName objectName, String...expressions) {
		
	}
	
	private StringBuilder getSB() {
		StringBuilder b = SB.get();
		b.setLength(0);
		return b;
	}
	
	/**
	 * Resets this result
	 * @return this result in a reset state
	 */
	public ExpressionResult reset() {
		metricName = null;
		tags.clear();		
		return this;
	}
	
	/**
	 * Sets the metric name for this result
	 * @param name The metric name to set
	 * @return this result
	 */
	public ExpressionResult metric(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed meric name was null or empty");
		metricName = clean(name);
		return this;
	}
	
	/**
	 * Sets the result value as a long
	 * @param value The long value
	 * @return this result
	 */
	public ExpressionResult value(final long value) {
		lValue = value;
		doubleValue = false;
		return this;
	}
	
	/**
	 * Sets the result value as a double
	 * @param value The double value
	 * @return this result
	 */
	public ExpressionResult value(final double value) {
		dValue = value;
		doubleValue = true;
		return this;
	}
	
	/**
	 * Accepts an opaque object and attempts to convert the string value of it to a double or a long and applies it
	 * @param value The opaque value whose {@link #toString()} should render the intended number
	 * @return this result
	 */
	public ExpressionResult value(final Object value) {
		if(value==null) throw new IllegalArgumentException("The passed value was null");
		if(value instanceof Number) {
			if(value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
				value(((Number)value).doubleValue());
			} else {
				value(((Number)value).longValue());
			}
		} else {
			String valStr = value.toString().trim();
			if(valStr.isEmpty()) throw new IllegalArgumentException("The passed value evaluated to an empty string");
			if(valStr.indexOf('.')!=-1) {
				value(Double.parseDouble(valStr));
			} else {
				value(Long.parseLong(valStr));
			}
		}
		return this;
	}
	
	
	/**
	 * Populates this ExpressionResult from the passed ObjectName stringy.
	 * @param cs The ObjectName stringy
	 * @return this result
	 */
	public ExpressionResult objectName(final CharSequence cs) {
		if(cs==null) throw new IllegalArgumentException("The passed CharSequence was null");
		return objectName(JMXHelper.objectName(cs));
	}
	
	/**
	 * Populates this ExpressionResult from the passed ObjectName.
	 * @param on The ObjectName
	 * @return this result
	 */
	public ExpressionResult objectName(final ObjectName on) {
		if(on==null) throw new IllegalArgumentException("The passed ObjectName was null");
		metric(on.getDomain());
		tags(on.getKeyPropertyList());
		return this;
	}
	
	/**
	 * Appends a tag to this result
	 * @param key The tag key
	 * @param value The tag value
	 * @return this result
	 */
	public ExpressionResult tag(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		tags.put(clean(key), clean(value));
		return this;
	}
	
	/**
	 * Appends a map of tags to this result
	 * @param tags The map of tags to add
	 * @return this result
	 */
	public ExpressionResult tags(final Map<String, String> tags) {
		if(tags==null) throw new IllegalArgumentException("The passed tag map was null");
		for(final Map.Entry<String, String> tag: tags.entrySet()) {
			this.tags.put(clean(tag.getKey()), clean(tag.getValue()));
		}		
		return this;
	}
	
	/**
	 * Appends an OpenTSDB telnet put text line to submit this result to the passed buffer.
	 * @param timestamp The timestamp in ms.
	 * @param buffer The buffer to append to
	 * @return this expression result
	 */
	public ExpressionResult appendPut(final long timestamp, final StringBuilder buffer) {
		if(buffer==null) throw new IllegalArgumentException("The passed buffer was null");		
		buffer.append(renderPut(timestamp));
		return this;
	}
	
	/**
	 * Appends an OpenTSDB telnet put text line to submit this result for the current timestamp to the passed buffer.
	 * @param buffer The buffer to append to
	 * @return this expression result
	 */
	public ExpressionResult appendPut(final StringBuilder buffer) {
		if(buffer==null) throw new IllegalArgumentException("The passed buffer was null");		
		buffer.append(renderPut(System.currentTimeMillis()));
		return this;
	}
	

	/**
	 * Renders an OpenTSDB telnet put text line to submit this result for the current timestamp 
	 * @return the rendered metric put command
	 */
	public String renderPut() {
		return renderPut(System.currentTimeMillis());
	}	
	
	/**
	 * Renders an OpenTSDB telnet put text line to submit this result 
	 * @param timestamp The timestamp of the metric in ms.
	 * @return the rendered metric put command
	 */
	public String renderPut(final long timestamp) {
		// put $metric $now $value host=$HOST
		StringBuilder b = getSB()
				.append("put ")
				.append(clean(metricName)).append(" ")
				.append(TimeUnit.SECONDS.convert(timestamp, TimeUnit.MILLISECONDS)).append(" ")
				.append(doubleValue ? dValue : lValue).append(" ");
		for(final Map.Entry<String, String> tag: rootTags.entrySet()) {
			b.append(clean(tag.getKey())).append("=").append(clean(tag.getValue())).append(" ");
		}		
		for(final Map.Entry<String, String> tag: tags.entrySet()) {
			b.append(clean(tag.getKey())).append("=").append(clean(tag.getValue())).append(" ");
		}
		
		return b.deleteCharAt(b.length()-1).append("\n").toString();
	}
	
	public String toString() {
		StringBuilder b = new StringBuilder(clean(metricName)).append(":");
		for(final Map.Entry<String, String> tag: rootTags.entrySet()) {
			b.append(clean(tag.getKey())).append("=").append(clean(tag.getValue())).append(",");
		}		
		for(final Map.Entry<String, String> tag: tags.entrySet()) {
			b.append(clean(tag.getKey())).append("=").append(clean(tag.getValue())).append(",");
		}
		b.deleteCharAt(b.length()-1);
		b.append("/").append(doubleValue ? dValue : lValue);
		return b.toString();
		
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

}