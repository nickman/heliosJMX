package com.heliosapm.opentsdb;

import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.json.JSONArray;

import com.heliosapm.opentsdb.AnnotationBuilder.TSDBAnnotation;

/**
 * <p>Title: Submitter</p>
 * <p>Description: The base OpenTSDB client</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.Submitter</code></p>
 */
public interface TSDBSubmitter {

	/**
	 * Creates a new ExpressionResult using the submitter's root tags and that flushes into this submitter's metric buffer.
	 * @return a new ExpressionResult 
	 */
	public ExpressionResult newExpressionResult();
	
	/**
	 * Creates a new ExpressionResult with the passed root tags and that flushes into this submitter's metric buffer.
	 * @param rootTagsMap The root tags to use for the returned expression result
	 * @return the expression result
	 */
	public ExpressionResult newExpressionResult(Map<String, String> rootTagsMap);

	/**
	 * Indicates if the submitter is connected
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected();

	/**
	 * Fast thread local string builder
	 * @return a thread local string builder
	 */
	public StringBuilder getSB();

	/**
	 * Adds a filter for any metric in for which all submitted metrics must match
	 * @param in The map of tag keys and values
	 */
	public void addFilterIn(Map<String, String> in);

	/**
	 * Adds a filter for the specified metric in for which all submitted metrics must match
	 * @param metric The metric key the filter should be applied to
	 * @param in The map of tag keys and values
	 */
	public void addFilterIn(String metric, Map<String, String> in);

	/**
	 * Validates that the passed tags should be included according to the registered filter-ins.
	 * If no filter-ins are registered, returns true
	 * @param metric The metric key to validate
	 * @param tags The tags to validate
	 * @return true if should be included, false otherwise
	 */
	public boolean matches(String metric, Map<String, String> tags);

	/**
	 * Returns the current time.
	 * @return the current time in seconds if {@link #traceInSeconds} is true, otherwise in milliseconds
	 */
	public long time();

	/**	 * Converts the passed ms time.
	 * @param time The time to convert
	 * @return the time converted to seconds if {@link #traceInSeconds} is true, otherwise in milliseconds (unchanged)
	 */
	public long time(long time);
	
	

	/**
	 * Traces a double metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(String metric, double value,
			Map<String, String> tags);

	/**
	 * Traces a double metric
	 * @param metric The full metric as a JMX {@link ObjectName}
	 * @param value The value
	 */
	public void trace(ObjectName metric, double value);

	/**
	 * Yet another tracing overload
	 * @param target The target ObjectName that was sampled
	 * @param metricName The desgnted metric name
	 * @param attributeValues The collected values keyed by the attribute name
	 * @param objectNameKeys The keys of the target ObjectName to add to the tags
	 */
	public void trace(ObjectName target, String metricName,
			Map<String, Object> attributeValues, String... objectNameKeys);

	/**
	 * Formats the passed metric and tags into a URL with comma separated name/value pair URLEncoded string
	 * @param metric The metric name 
	 * @param tags The tags to format
	 * @return the formatted URL string
	 */
	public String formatTags(String metric, String... tags);

	/**
	 * Issues a synchronous TSDB query for the passed metric and tags.
	 * The intent is for meta only so the start time is <b><code>1s-ago</code></b>. 
	 * @param metric The metric name
	 * @param tags The tags
	 * @return A JSON array of the parsed results
	 */
	public JSONArray query(String metric, String... tags);

	/**
	 * Issues a synchronous TSDB query for the passed metric and tags.
	 * The intent is for meta only so the start time is <b><code>1s-ago</code></b>. 
	 * @param url The prepared and encoded URL 
	 * @return A JSON array of the parsed results
	 */
	public JSONArray query(String url);

	/**
	 * Issues a synchronous TSDB query for the passed metric and tags to find a single TSUID.
	 * In other words, if zero or more than one TSUIDs are returned, it's an error. 
	 * @param metric The metric name
	 * @param tags The tags
	 * @return A JSON array of the parsed results
	 */
	public String tsuid(String metric, String... tags);

	/**
	 * Retrieves the attribute names of the MBean registered in the passed MBeanServer
	 * @param conn The connection to the remote MBeanServer
	 * @param target The JMX ObjectName of the target MBean
	 * @return An array of attribute names
	 */
	public String[] getAttributeNames(MBeanServerConnection conn,
			ObjectName target);

	/**
	 * Traces a tsdb annotation
	 * @param annotation the annotation to trace
	 */
	public void trace(TSDBAnnotation annotation);

	/**
	 * Traces a double metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(String metric, double value, String... tags);

	/**
	 * Traces a long metric
	 * @param timestamp The provided time in ms.
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(long timestamp, String metric, long value,
			Map<String, String> tags);

	/**
	 * Traces a double metric
	 * @param timestamp The provided time in ms.
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(long timestamp, String metric, double value,
			Map<String, String> tags);

	/**
	 * Traces a long metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(String metric, long value,
			Map<String, String> tags);

	/**
	 * Traces a long metric
	 * @param metric The full metric as a JMX {@link ObjectName}
	 * @param value The value
	 */
	public void trace(ObjectName metric, long value);

	/**
	 * Traces raw JMX BatchService lookup results
	 * @param batchResults A map of JMX attribute values keyed by the attribute name within a map keyed by the ObjectName
	 */
	public void trace(Map<ObjectName, Map<String, Object>> batchResults);

	/**
	 * Traces a long metric
	 * @param metric The metric name
	 * @param value The value
	 * @param tags The metric tags
	 */
	public void trace(String metric, long value, String... tags);

	/**
	 * Flushes the databuffer to the connection's buffer
	 */
	public void flush();
	
	/**
	 * Flushes the databuffer to the connection's buffer and to the underlying socket output stream and on success, clears the data buffer
	 */
	public void deepFlush();
	
	/**
	 * Closes the underlying TSDBSubmitterConnection.
	 * Use with caution since other submitters may be using the connection
	 */
	public void close();

	/**
	 * Returns the connected OpenTSDB version
	 * @return the connected OpenTSDB version
	 */
	public String getVersion();

	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Double doubleDelta(double value, String... id);

	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Long longDelta(long value, String... id);

	/**
	 * Computes the positive delta between the submitted value and the prior value for the same id
	 * @param value The value to compute the delta for
	 * @param id The id of the delta, a compound array of names which will be concatenated
	 * @return the delta value, or null if this was the first submission for the id, or the delta was reset
	 */
	public Integer longInteger(int value, String... id);

	/**
	 * Flushes all the delta states
	 */
	public void flushDeltas();

	/**
	 * Creates and returns a new tag map
	 * @return a tag map
	 */
	public FluentMap tagMap();

	/**
	 * Sets the tracing time unit
	 * @param traceInSeconds true to trace in seconds, false to trace in milliseconds
	 * @return this submitter
	 */
	public TSDBSubmitter setTraceInSeconds(boolean traceInSeconds);
	
	/**
	 * Indicates if TSDB times are traced in seconds, or milliseconds
	 * @return true if TSDB times are traced in seconds, false if in milliseconds 
	 */
	public boolean isTraceInSeconds();


	/**
	 * Adds a root tag
	 * @param key The root tag key
	 * @param value The root tag value
	 * @return this submitter
	 */
	public TSDBSubmitter addRootTag(String key, String value);

	/**
	 * Sets the trace logging
	 * @param logTraces true to trace logs, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setLogTraces(boolean logTraces);

	/**
	 * Indicates if tracing is disabled
	 * @return true if tracing is disabled, false otherwise
	 */
	public boolean isTracingDisabled();
	
	/**
	 * Indicates if traces are being logged
	 * @return true if traces are being logged, false otherwise
	 */
	public boolean isLogTraces();

	/**
	 * Enables or disables actual tracing. To view what would be traced,
	 * without actually tracing, set {@link #setLogTraces(boolean)} to true
	 * and this to false;
	 * @param disableTraces true to disable, false otherwise
	 * @return this submitter
	 */
	public TSDBSubmitter setTracingDisabled(boolean disableTraces);

}