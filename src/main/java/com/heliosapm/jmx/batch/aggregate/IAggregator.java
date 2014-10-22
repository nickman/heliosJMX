
package com.heliosapm.jmx.batch.aggregate;

import java.util.List;

/**
 * <p>Title: IAggregator</p>
 * <p>Description: Defines an aggregation function that accepts a list of objects and returns an object representing the aggregate</p> 
 * <p>Company: ICE</p>
 * @author Whitehead 
 * <p><code>com.heliosapm.jmx.batch.aggregate.IAggregator</code></p>
 */
public interface IAggregator {
	/**
	 * Computes an aggregate of the passed items
	 * @param items The items to compute an aggregate for
	 * @return The aggregate result
	 */
	public Object aggregate(List<Object> items);
	
	/**
	 * Aggregates a long array
	 * @param items The array of longs to aggregate
	 * @return the aggregated long value
	 */
	public long aggregate(long[] items);
	
	/**
	 * Aggregates a double array
	 * @param items The array of double to aggregate
	 * @return the aggregated double value
	 */	
	public double aggregate(double[] items);
}
