package com.heliosapm.jmx.batch;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.QueryExp;

import com.heliosapm.jmx.util.helpers.JMXHelper;

/**
 * <p>Title: BulkJMXServiceMBean</p>
 * <p>Description: JMX MBean interface for {@link BulkJMXService}</p> 
 * <p>Company: ICE</p>
 * @author Whitehead 
 * <p><code>com.heliosapm.jmx.batch.aggregate.BulkJMXServiceMBean</code></p>
 */
public interface BulkJMXServiceMBean {
	
	/** This MBean's ObjectName */
	public static final ObjectName OBJECT_NAME = JMXHelper.objectName("com.heliosapm.jmx:service=BulkJMXService");
	

	/**
	 * Executes a SQL query and returns the result set as a CachedRowSet
	 * @param jndiName The JNDI name of the data source to get a connection from
	 * @param sqlText The SQL query
	 * @return a serialized result set
	 */
	public ResultSet query(String jndiName, String sqlText);
	
	/**
	 * Bulk retrieves the values of the named attributes from any MBeans registered with ObjectNames matching any of the passed ObjectName patterns
	 * @param attributeNames The names of attributes to retrieve
	 * @param objectNames An array of [optionally wildcarded] object names
	 * @return A map of values keyed by the attribute name within a map keyed by the ObjectName
	 */
	public Map<ObjectName, Map<String, Object>> getAttributes(List<String> attributeNames, ObjectName ...objectNames);
		
	
	/**
	 * Invokes an operation on all matching MBeans
	 * @param objectNames An array of [optionally wildcarded] object names to invoke on
	 * @param opName The operation name to invoke
	 * @param params The operation arguments
	 * @param signature The arg signature of the operation 
	 * @return a map of values keyed by the ObjectName
	 */
	public Map<ObjectName, Object> invoke(ObjectName objectNames[], String opName, Object[] params, String[] signature);
	
	/**
	 * Implements a server side aggregation of attribute values from all matching MBeans matching the object name pattern and query
	 * @param pattern The ObjectName pattern
	 * @param query An optional query
	 * @param attribute The attribute name indicating with attribute should be aggregated
	 * @param aggregateFunction The name of the aggregate function to aggregate with
	 * @return The return value of the aggregation
	 */
	public Object aggregate(ObjectName pattern, QueryExp query, String attribute, String aggregateFunction);
	
	/**
	 * Implements a server side aggregation of attribute values from all matching MBeans matching the object name pattern
	 * @param pattern The ObjectName pattern
	 * @param attribute The attribute name indicating with attribute should be aggregated
	 * @param aggregateFunction The name of the aggregate function to aggregate with
	 * @return The return value of the aggregation
	 */
	public Object aggregate(ObjectName pattern, String attribute, String aggregateFunction);
	
	/**
	 * Executes a JS script defined in the passed source using a simple eval.
	 * @param source The script source to execute
	 * @param classLoader The optional classloader object name. Can be a ClassLoader MBean, otherwise will use the classloader of the referenced mbean.
	 * @param args The optional arguments to the script
	 * @return The return value of the script execution
	 */
	public Object script(String source, ObjectName classLoader, Object[] args);
	
	/**
	 * Executes a JS script defined in the passed source using a simple eval.
	 * @param source The script source to execute
	 * @param args The optional arguments to the script
	 * @return The return value of the script execution
	 */
	public Object script(String source, Object[] args);
	
	/**
	 * Compiles and executes the passed script source, caching it using the name as the key. 
	 * @param name The name of the script
	 * @param source The script source to execute. Optional if named script is already cached.
	 * @param classLoader The optional classloader object name. Can be a ClassLoader MBean, otherwise will use the classloader of the referenced mbean.
	 * @param args The optional arguments to the script
	 * @return The return value of the script execution
	 */
	public Object cscript(String name, String source, ObjectName classLoader, Object[] args);
	
	/**
	 * Compiles and executes the passed script source, caching it using the name as the key. 
	 * @param name The name of the script
	 * @param source The script source to execute. Optional if named script is already cached.
	 * @param args The optional arguments to the script
	 * @return The return value of the script execution
	 */
	public Object cscript(String name, String source, Object[] args);
	
	/**
	 * Prints the contents of the compiled JS cache
	 * @return an HTML table summarizing the contents
	 */
	public String printCompiledCache();
	
	/**
	 * Flushes the compiled JS cache
	 */
	public void flushCompiledCache();
	
	/**
	 * Returns the number of compiled JS entries in cache
	 * @return the number of compiled JS entries in cache
	 */
	public int getCompiledCacheSize();
	
	/**
	 * Indicates if the named script is in cache
	 * @param name The name of the compiled script to test for
	 * @return true if the named script is in cache, false otherwise
	 */
	public boolean isCompiledCached(String name);
}
