
package com.heliosapm.jmx.batch.aggregate;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;








/**
 * <p>Title: AggregateFunction</p>
 * <p>Description: Defines aggregate functions for aggregating the values of multiple attributes into one return value</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction</code></p>
 */

public enum AggregateFunction implements IAggregator {
	/** Calculates the sum of the returned values */
	SUM("sum", new SumAggregator(false)),
	/** Calculates the sum of the returned values (strict) */
	STRSUM(true, "ssum", new SumAggregator(true)),
	/** Returns the number of items in the result set */
	COUNT("count", new CountAggregator(false)),
	/** Returns the number of items in the result set (strict) */
	STRCOUNT(true, "scount", new CountAggregator(true)),
	
	/** Returns the average of the returned values */
	AVG("avg", new AverageAggregator(false)),
	/** Returns the average of the returned values (strict) */
	STRAVG(true, "savg", new AverageAggregator(true)),
	/** Returns the minimum value */
	MIN("min", new MinAggregator(false)),
	/** Returns the minimum value (strict)*/
	STRMIN(true, "smin", new MinAggregator(true)),
	
	/** Returns the maximum value */
	MAX("max", new MaxAggregator(false)),
	/** Returns the maximum value (strict)*/
	STRMAX(true, "smax", new MaxAggregator(true)),
		
	/** Returns the average delta of the sequence of passed items */
	DELTA_ALL("delta", new Delta(false)),	
	/** Returns the delta of the most recent 2 of the passed items */
	DELTA_LAST("deltal", new Delta(true)),
	
	/** Returns the average rate per second from the sequence of passed items */
	PS_RATE_ALL("rate", new Rate(false, TimeUnit.SECONDS)),	
	/** Returns the rate per second from the most recent 2 of the passed items */
	PS_RATE_LAST("ratel", new Rate(false, TimeUnit.SECONDS)),

	/** Returns the average rate per milli-second from the sequence of passed items */
	PMS_RATE_ALL("msrate", new Rate(false, TimeUnit.MILLISECONDS)),	
	/** Returns the rate per milli-second from the most recent 2 of the passed items */
	PMS_RATE_LAST("msratel", new Rate(false, TimeUnit.MILLISECONDS)),
	
	/** Returns the number of distinct items based on {@link Object#equals(Object)}  */
	DISTINCT("dist", new DistinctAggregator()),
	
	
	/** Returns a json group with each unique item and a count of the occurences */
	GROUP(false, "grp", new GroupAggregator()),
	/** Returns a JSON composite of Min, Max, Average and Count */
	MMAC(false, "mmac", new MinMaxAvgCntAggregator(false)),
	/** Returns a JSON composite of Min, Max, Average and Count (strict) */
	STRMMAC(true, false, "smmac", new MinMaxAvgCntAggregator(true));

	
//	  /**
//	   * Jackson de/serializer initialized, configured and shared
//	   */
//	  private static final ObjectMapper jsonMapper = new ObjectMapper();
	
	
//	  /**
//	   * Serializes the given object to a JSON string
//	   * @param object The object to serialize
//	   * @return A JSON formatted string
//	   * @throws IllegalArgumentException if the object was null
//	   */
//	  public static final String serializeToString(final Object object) {
//	    if (object == null)
//	      throw new IllegalArgumentException("Object was null");
//	    try {
//	      return jsonMapper.writeValueAsString(object);
//	    } catch (Exception e) {
//	      throw new RuntimeException(e);
//	    }
//	  }	
	

	/**
	 * Creates a new numeric return AggregateFunction
	 * @param name The aggregate short name
	 * @param aggr The entry's aggregator
	 */
	private AggregateFunction(final String name, final IAggregator aggr) {
		this(true, name, aggr);
	}
	
	/**
	 * Creates a new AggregateFunction
	 * @param numeric true for numeric returns, false for JSON
	 * @param name The aggregate short name
	 * @param aggr The entry's aggregator
	 */
	private AggregateFunction(final boolean numeric, final String name, final IAggregator aggr) {
		this(false, true, name, aggr);
	}
	
	/**
	 * Creates a new AggregateFunction
	 * @param strict true if the numeric aggregator is strict
	 * @param numeric true for numeric returns, false for JSON
	 * @param name The aggregate short name
	 * @param aggr The entry's aggregator
	 */
	private AggregateFunction(final boolean strict, final boolean numeric, final String name, final IAggregator aggr) {
		this.aggr = aggr;
		this.name = name;
		this.numeric = numeric;
		this.strict = strict;
		
	}
	

	
	/** The enum entry's aggregator implementation */
	public final IAggregator aggr;
	/** The enum entry's short name */
	public final String name;
	/** Indicates if the enum entry's aggregator implementation returns a number */
	public final boolean numeric;
	/** Indicates if the enum entry's numeric aggregator implementation is strict */
	public final boolean strict;
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
	 */
	@Override
	public Object aggregate(List<Object> items) {
		return aggr.aggregate(items);
	}
	
	
	/**
	 * Returns the AggregateFunction for the passed name. Applies trim and toUpper to the name first.
	 * @param name The name of the function
	 * @return the named AggregateFunction 
	 */
	public static AggregateFunction forName(CharSequence name) {
		if(name==null) throw new IllegalArgumentException("The passed AggregateFunction name was null", new Throwable());
		try {
			return AggregateFunction.valueOf(name.toString().trim().toUpperCase());
		} catch (Exception e) {
			throw new IllegalArgumentException("The passed AggregateFunction name [" + name + "] is not a valid function name", new Throwable());
		}
	}
	
	/**
	 * Indicates if the passed name is a valid AggregateFunction. Applies trim and toUpper to the name first.
	 * @param name The name of the function
	 * @return true if the passed name is a valid AggregateFunction, false otherwise
	 */
	public static boolean isName(CharSequence name) {
		if(name==null) throw new IllegalArgumentException("The passed AggregateFunction name was null", new Throwable());
		try {
			AggregateFunction.valueOf(name.toString().trim().toUpperCase());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	
	/** Comma split pattern */
	public static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	/** Empty array const */
	public static final AggregateFunction[] EMPTY_ARR = {};
	
	/** A map of AggregateFunction keyed by the short names */
	public static final Map<String, AggregateFunction> NAME2ENUM;
	
	static {
		final AggregateFunction[] values = AggregateFunction.values();
		Map<String, AggregateFunction> tmp = new HashMap<String, AggregateFunction>(values.length);
		for(AggregateFunction af: values) {
			tmp.put(af.name, af);
		}
		NAME2ENUM = Collections.unmodifiableMap(tmp);
	}
	
	/**
	 * Retrieves the AggregateFunctions by short name or enum name, returning an empty array for no match
	 * @param name The comma separated values to decode
	 * @return an array (possibly zero length) of matching AggregateFunctions
	 */
	public static AggregateFunction[] forNames(CharSequence name) {
		if(name==null || name.toString().trim().isEmpty()) return EMPTY_ARR;
		try {
			final Set<AggregateFunction> set = EnumSet.noneOf(AggregateFunction.class);
			String[] names = COMMA_SPLITTER.split(name.toString().replace(" ", ""));
			for(String s: names) {
				if(isName(s)) set.add(valueOf(s.trim().toUpperCase()));
				else {
					AggregateFunction af = NAME2ENUM.get(s.trim().toLowerCase());
					if(af!=null) {
						set.add(af);
					}
				}
			}
			return set.toArray(new AggregateFunction[set.size()]);
		} catch (Exception e) {
			return EMPTY_ARR;
		}
	}
	
	/**
	 * Computes and returns the aggregate for the named aggregator and list of input items
	 * @param name The name of the aggregator function
	 * @param items The list of items to aggregate
	 * @return the aggregate value
	 */
	public static Object aggregate(CharSequence name, List<Object> items) {
		return AggregateFunction.forName(name).aggr.aggregate(items);
	}
	
	/**
	 * Computes and returns the aggregate for the named aggregator and object of nput items.
	 * The object is introspected to determine if it is:<ul>
	 *  <li>Null</li>
	 * 	<li>{@link java.util.Map}</li>
	 * 	<li>{@link java.util.Collection}</li>
	 * 	<li>An array</li>
	 * </ul>.
	 * If it is none of the above, a runtime exception is thrown.
	 * If it is a map or an array, it is converted to a list for aggregate computation.
	 * @param name The name of the aggregator function
	 * @param item The object of items to aggregate
	 * @return the aggregate value
	 * TODO:  Do we need to support multi dimmensional arrays ?
	 */
	@SuppressWarnings("unchecked")
	public static Object aggregate(CharSequence name, Object item) {				
		final List<Object> items;		
		final AggregateFunction function = AggregateFunction.forName(name);
		if(item==null) {
			items = Collections.EMPTY_LIST;
		} else if(item instanceof Map) {
			Map<Object, Object> map = (Map<Object, Object>)item;
			items = new ArrayList<Object>(map.keySet());
		} else if(item instanceof Collection) {
			items = new ArrayList<Object>((Collection<Object>)item);
		} else if(item.getClass().isArray())  {			
			int length = Array.getLength(item);
			items = new ArrayList<Object>(length);
			for(int i = 0; i < length; i++) {
				items.add(i, Array.get(item, i));
			}
		} else {
			throw new IllegalArgumentException("Aggregate object of type [" + item.getClass().getName() + "] was not a Map, Collection or Array", new Throwable());
		}
		return function.aggregate(items);					
	}
	
	
	
	/**
	 * <p>Title: NumericAggregator</p>
	 * <p>Description: Aggregates a numeric computation of all the values that are assumed to be numbers or implement {@link INumberProvider}</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.SumAggregator</code></p>
	 */
	public static abstract class NumericAggregator implements IAggregator {
		/** If true, throws an error if any item is null or not a number */
		protected final boolean strict;
		/**
		 * Creates a new SumAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public NumericAggregator(boolean strict) {
			this.strict = strict;
		}
		/**
		 * Sifts through the passed items, looking for exceptions and if non-strict, returns a list of compliant items.
		 * @param items The list of items to sift
		 * @return a list of valid items and instances of {@link INumberProvider}s wrapped as Numbers.
		 */
		protected List<Number> sift(List<Object> items) {
			if(items==null) {
				if(strict) throw new RuntimeException("List of items was null and aggregator was strict", new Throwable());
			}			
			List<Number> numbers = new ArrayList<Number>(items.size());
			for(Object o: items) {
				if(o==null) {
					if(strict) throw new RuntimeException("List of items had a null and aggregator was strict", new Throwable());
					continue;
				}
				if(o instanceof Number) {
					numbers.add((Number)o);
					continue;
				}
				if(o instanceof INumberProvider) {
					numbers.add(NumberWrapper.getNumber((INumberProvider)o));
					continue;					
				}
				if(strict) throw new RuntimeException("List of items had a non-number item [" + o.getClass().getName() + "] and aggregator was strict", new Throwable());
			}
			return numbers;
		}
		
		/**
		 * Sums an array 
		 * @param items The items to sum
		 * @return the total sum
		 */
		protected long sum(long[] items) {
			if(items==null || items.length<1) return 0L;
			long total = 0;
			for(long t: items) {
				total += t;
			}
			return total;
		}
		
		/**
		 * Sums an array 
		 * @param items The items to sum
		 * @return the total sum
		 */
		protected double sum(double[] items) {
			if(items==null || items.length<1) return 0D;
			double total = 0;
			for(double t: items) {
				total += t;
			}
			return total;
		}
	}
	
	/**
	 * <p>Title: Delta</p>
	 * <p>Description: Computes deltas of the passed numbers</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.Delta</code></p>
	 */
	public static class Delta extends NumericAggregator {
		/** If true, returns the delta of the last two items, otherwise returns the average delta of all entries */
		protected final boolean last;
		/**
		 * Creates a new Delta
		 * @param last If true, returns the delta of the last two items, otherwise returns the average delta of all entries
		 */
		public Delta(boolean last) {
			super(true);
			this.last = last;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {
			List<Number> numbers = sift(items);
			if(numbers.size()<2) return 0;
			if(last) {
				return numbers.get(0).doubleValue() - numbers.get(1).doubleValue(); 
			} else {
				double[] deltas = new double[numbers.size()-1];
				for(int i = 0; i < numbers.size()-1; i++) {
					deltas[i] = numbers.get(i+1).doubleValue() - numbers.get(i).doubleValue();
				}
				return STRAVG.aggregate(deltas);
			}			
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items.length<2) return 0;
			if(last) {
				return items[0] - items[1]; 
			} else {
				long[] deltas = new long[items.length-1];
				for(int i = 0; i < items.length-1; i++) {
					deltas[i] = items[i+1] - items[i];
				}
				return STRAVG.aggregate(deltas);
			}			
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items.length<2) return 0;
			if(last) {
				return items[0] - items[1]; 
			} else {
				double[] deltas = new double[items.length-1];
				for(int i = 0; i < items.length-1; i++) {
					deltas[i] = items[i+1] - items[i];
				}
				return STRAVG.aggregate(deltas);
			}			
		}
	}
	
	/**
	 * <p>Title: Rate</p>
	 * <p>Description: Computes rate of the passed numbers</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.Rate</code></p>
	 */
	public static class Rate extends Delta {
		/** The time unit for which to report rates */
		protected final TimeUnit rateUnit;
		
		/**
		 * Creates a new Rate
		 * @param last If true, returns the delta of the last two items, otherwise returns the average delta of all entries
		 * @param rateUnit The time unit for which to report rates
		 */
		public Rate(boolean last, TimeUnit rateUnit) {
			super(last);
			this.rateUnit = rateUnit;
		}
		
		/**
		 * Calculates a rate from the passed numbers. The time window is passed as the first entry in the list
		 * and is assumed to be in the same unit as this Rate instance and the values are assumed to be ticks per second.
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.AggregateFunction.Delta#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {
			List<Number> numbers = sift(items);
			if(numbers.size()<3) return 0;
			double window = rateUnit.convert(numbers.remove(0).longValue(), TimeUnit.SECONDS);
			double delta = ((Number)super.aggregate(new ArrayList<Object>(numbers))).doubleValue();
			return delta/window;
		}
		
		/**
		 * Calculates a rate from the passed double array. The time window is passed as the first array entry
		 * and is assumed to be in the same unit as this Rate instance and the values are assumed to be ticks per second.
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.AggregateFunction.Delta#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items.length<3) return 0;
			double[] anumbers = new double[items.length-1];
			System.arraycopy(items, 1, anumbers, 0, items.length-1);
			double window = TimeUnit.SECONDS.convert((long)items[0], rateUnit);
			double delta = super.aggregate(anumbers);
			if(delta<=0 || window<=0) return 0D;
			return delta/window;
		}
		
		/**
		 * Calculates a rate from the passed long array. The time window is passed as the first array entry
		 * and is assumed to be in the same unit as this Rate instance and the values are assumed to be ticks per second.
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.AggregateFunction.Delta#aggregate(double[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items.length<3) return 0;
			long[] anumbers = new long[items.length-1];
			System.arraycopy(items, 1, anumbers, 0, items.length-1);
			double window = rateUnit.convert(items[0], TimeUnit.SECONDS);
			double delta = super.aggregate(anumbers);
			if(delta<=0 || window<=0) return 0L;
			return (long)(delta/window);
		}		
		
	}
	
	
	/**
	 * <p>Title: SumAggregator</p>
	 * <p>Description: Aggregates the numeric sum of all the values</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.SumAggregator</code></p>
	 */
	public static class SumAggregator extends NumericAggregator {
		/**
		 * Creates a new SumAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public SumAggregator(boolean strict) {
			super(strict);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Double aggregate(List<Object> items) {
			double d = 0D;
			for(Number n: sift(items)) {
				d += n.doubleValue();
			}
			return d;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			return sum(items);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			return sum(items);
		}
		
	}
	
	/**
	 * <p>Title: AverageAggregator</p>
	 * <p>Description: Aggregates the mathematical average  of all the values</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.AverageAggregator</code></p>
	 */
	public static class AverageAggregator extends NumericAggregator {
		/**
		 * Creates a new AverageAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public AverageAggregator(boolean strict) {
			super(strict);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Double aggregate(List<Object> items) {
			double total = 0D;
			double count = 0D;			
			for(Number n: sift(items)) {
				count++;
				total += n.doubleValue();
			}
			if(total==0 || count==0) return 0D;
			return total/count;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items==null) return 0;
			double total = sum(items);
			double length = items.length;
			if(total==0 || length==0) return 0;
			double avg = total/length;
			return (long)avg;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items==null) return 0;
			double total = sum(items);
			double length = items.length;
			if(total==0 || length==0) return 0;
			double avg = total/length;
			return avg;
		}		
	}
	
	/**
	 * <p>Title: MinAggregator</p>
	 * <p>Description: Computes the lowest numeric value of all the values. If non-strict, returns -1D for an empty list</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.MinAggregator</code></p>
	 */
	public static class MinAggregator extends NumericAggregator {
		/**
		 * Creates a new MinAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public MinAggregator(boolean strict) {
			super(strict);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Double aggregate(List<Object> items) {
			List<Number> numbers = sift(items);
			if(numbers.isEmpty()) {
				if(strict) throw new RuntimeException("List of items for MIN was empty and aggregator was strict", new Throwable());
				return -1D;
			}
			double min = Double.MAX_VALUE;
			for(Number n: sift(items)) {
				if(n.doubleValue() < min) min = n.doubleValue();
			}
			return min;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items==null) return 0;
			Arrays.sort(items);
			return items[0];
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items==null) return 0;
			Arrays.sort(items);
			return items[0];
		}
	}

	/**
	 * <p>Title: MaxAggregator</p>
	 * <p>Description: Computes the highest numeric value of all the values. If non-strict, returns -1D for an empty list</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.MaxAggregator</code></p>
	 */
	public static class MaxAggregator extends NumericAggregator {
		/**
		 * Creates a new MaxAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public MaxAggregator(boolean strict) {
			super(strict);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Double aggregate(List<Object> items) {
			List<Number> numbers = sift(items);
			if(numbers.isEmpty()) {
				if(strict) throw new RuntimeException("List of items for MAX was empty and aggregator was strict", new Throwable());
				return -1D;
			}
			double max = Double.MIN_VALUE;
			for(Number n: sift(items)) {
				if(n.doubleValue() > max) max = n.doubleValue();
			}
			return max;
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items==null) return 0;
			Arrays.sort(items);
			return items[items.length-1];
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items==null) return 0;
			Arrays.sort(items);
			return items[items.length-1];
		}		
	}
	
	
	/**
	 * <p>Title: CountAggregator</p>
	 * <p>Description: Aggregates the items to a simple count of the number of items. If strict, throws an exception if any item is null, otherwise ignores null items.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.CountAggregator</code></p>
	 */
	public static class CountAggregator implements IAggregator {
		/** The strict indicator */
		private final boolean strict;
		
		/**
		 * Creates a new CountAggregator
		 * @param strict If true, implements strict count rules
		 */
		public CountAggregator(boolean strict) {
			this.strict = strict;
		}
		
		/**
		 * Counts the number if items
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {
			if(items==null) {
				if(strict) {
					throw new RuntimeException("List of items for Count was null and aggregator was strict", new Throwable());
				} 
				return 0;
			}
			if(strict) {
				for(Object o: items) {
					if(o==null) throw new RuntimeException("List of items for Count contained a null and aggregator was strict", new Throwable());
				}
			} 
			return items.size();
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {			
			return items==null ? 0 : items.length;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			return items==null ? 0 : items.length;
		}
	}
	
	
	/**
	 * <p>Title: DistinctAggregator</p>
	 * <p>Description: Aggregates the items to a simple count of the number of distinct items where equality is determined by {@link Object#equals(Object)}.  </p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.DistinctAggregator</code></p>
	 */
	public static class DistinctAggregator implements IAggregator {

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {
			if(items==null || items.isEmpty()) return 0;
			Set<Object> set = new CopyOnWriteArraySet<Object>(items);
			return set.size();
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			if(items==null || items.length<1) return 0;
			Set<Long> set = new HashSet<Long>(items.length);
			for(int i = 0; i < items.length; i++) { set.add(items[i]); }
			return set.size();
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			if(items==null || items.length<1) return 0;
			Set<Double> set = new HashSet<Double>(items.length);
			for(int i = 0; i < items.length; i++) { set.add(items[i]); }
			return set.size();
		}		
	}
	
	/**
	 * <p>Title: GroupAggregator</p>
	 * <p>Description: Aggregates the items to a a json group with each unique item and a count of the occurences where equality is determined by and items
	 * are represented using {@link Object#toString()}.  Null items are ignored.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.GroupAggregator</code></p>
	 */
	public static class GroupAggregator implements IAggregator {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {						
			Map<String, Long> map = new HashMap<String, Long>();
			if(items==null || items.isEmpty()) {
				for(Object o: items) {
					if(o==null) continue;
					String key = o.toString();
					Long l = map.get(key);
					if(l==null) {
						l = 0L;
					}
					l = l++;
					map.put(key, l);
				}
			}
			return map.toString(); //serializeToString(map);
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			throw new UnsupportedOperationException("The direct aggregate(long[]) cannot return a JSONObject. Please use AggregateFunction.aggreaget(Object)", new Throwable());
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			throw new UnsupportedOperationException("The direct aggregate(double[]) cannot return a JSONObject. Please use AggregateFunction.aggreaget(Object)", new Throwable());
		}		
	}
	
	
	/**
	 * <p>Title: MinMaxAvgCntAggregator</p>
	 * <p>Description: Aggregates the passed numberic items to a JSON object representing the min, max, average and count. If non-strict, returns -1D values for a null list and for null items</p> 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.AggregateFunction.MinMaxAvgCntAggregator</code></p>
	 */
	public static class MinMaxAvgCntAggregator extends NumericAggregator {
		/** The MinMaxAvgCnt key for the min value */
		public static final String KEY_MIN = "min";
		/** The MinMaxAvgCnt key for the max value */
		public static final String KEY_MAX = "max";
		/** The MinMaxAvgCnt key for the avg value */
		public static final String KEY_AVG = "avg";
		/** The MinMaxAvgCnt key for the value count */
		public static final String KEY_CNT = "cnt";
		
		/** The base response for a non-strict MinMaxAvgCnt with null items */
		public static final String BASE_RESP;
		
		static {
			Map<String, Double> map = new HashMap<String, Double>(4);
			map.put(KEY_MIN, -1D); map.put(KEY_MAX, -1D); map.put(KEY_AVG, -1D); map.put(KEY_CNT, 0D);
			try {
				BASE_RESP = "{}"; //new ObjectMapper().writeValueAsString(map);
			} catch (Exception x) {
				throw new RuntimeException(x);
			}
		}
		
		/**
		 * Creates a new MaxAggregator
		 * @param strict If true, throws an error if any item is null or not a number. Otherwise ignores the non number items
		 */
		public MinMaxAvgCntAggregator(boolean strict) {
			super(strict);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(java.util.List)
		 */
		@Override
		public Object aggregate(List<Object> items) {
			List<Number> numbers = sift(items);
			if(numbers.isEmpty()) {
				if(strict) throw new RuntimeException("List of items for MinMaxAvgCnt was empty and aggregator was strict", new Throwable());
				return BASE_RESP;
			}
			double max = Double.MIN_VALUE;
			double min = Double.MAX_VALUE;
			double total = 0D;
			double count = 0D;
			for(Number n: sift(items)) {
				Double d = n.doubleValue();
				if(d > max) max = d;
				if(d < min) min = d;
				total += d;
				count++;
			}
			double avg = (total==0D || count==0D) ? 0D : (total/count);
			Map<String, Double> map = new HashMap<String, Double>(4);
			map.put(KEY_MIN, min); map.put(KEY_MAX, max); map.put(KEY_AVG, avg); map.put(KEY_CNT, count);
			return map.toString(); // serializeToString(map);
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
		 */
		@Override
		public long aggregate(long[] items) {
			throw new UnsupportedOperationException("The direct aggregate(long[]) cannot return a JSONObject. Please use AggregateFunction.aggreaget(Object)", new Throwable());
		}
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
		 */
		@Override
		public double aggregate(double[] items) {
			throw new UnsupportedOperationException("The direct aggregate(double[]) cannot return a JSONObject. Please use AggregateFunction.aggreaget(Object)", new Throwable());
		}		
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(long[])
	 */
	@Override
	public long aggregate(long[] items) {
		return aggr.aggregate(items);
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.aggregate.IAggregator#aggregate(double[])
	 */
	@Override
	public double aggregate(double[] items) {
		return aggr.aggregate(items);
	}

	public static void log(Object msg) {
		System.out.println(msg);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) {
		log("DELTA Test");
		Object d = null;
		d = DELTA_LAST.aggregate(new long[]{20, 10, 5});
		log("DeltaLast:" + d);
		d = DELTA_ALL.aggregate(new long[]{20, 10, 5});
		log("DeltaAll:" + d);
		
		d = DELTA_LAST.aggregate(new ArrayList(Arrays.asList(20, 10, 5)));
		log("DeltaLast:" + d);
		d = DELTA_ALL.aggregate(new ArrayList(Arrays.asList(20, 10, 5)));
		log("DeltaAll:" + d);

		
		log("\nRATE Test");
		d = PS_RATE_LAST.aggregate(new long[]{1, 10, 20});
		log("PS Last:" + d);
		d = PS_RATE_LAST.aggregate(new ArrayList(Arrays.asList(1, 10, 20)));
		log("PS Last:" + d);
		d = PMS_RATE_LAST.aggregate(new long[]{1, 10000, 20000});
		log("PMS Last:" + d);
		d = PMS_RATE_LAST.aggregate(new ArrayList(Arrays.asList(1, 10000, 20000)));
		log("PMS Last:" + d);

		
	}
	
}
