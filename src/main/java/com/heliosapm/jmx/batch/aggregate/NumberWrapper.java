package com.heliosapm.jmx.batch.aggregate;

/**
 * <p>Title: NumberWrapper</p>
 * <p>Description: Accepts an {@link INumberProvider} and wraps it to disguise it as a {@link Number}.</p> 
 * <p>Company: ICE</p>
 * @author Whitehead 
 * <p><code>com.heliosapm.jmx.batch.aggregate.NumberWrapper</code></p>
 */
public class NumberWrapper {
	/** The wrapped number provider */
	private final INumberProvider provider;

	/**
	 * Returns a Number which is a synthetic which delegates to the passed {@link INumberProvider}
	 * @param provider The inner provider
	 * @return a Number
	 */
	public static Number getNumber(INumberProvider provider) {
		if(provider==null) throw new IllegalArgumentException("The passed number provider was null", new Throwable());
		return new NumberWrapper(provider).getNumber();
	}
	
	/**
	 * Creates a new NumberWrapper
	 * @param provider The provider to wrap
	 */
	private NumberWrapper(INumberProvider provider) {
		this.provider = provider;
	}
	
	/**
	 * @return
	 */
	private Number getNumber() {
		return new Number() {

			/**  */
			private static final long serialVersionUID = 8150101253348237543L;

			@Override
			public int intValue() {
				return provider.getNumber().intValue();
			}

			@Override
			public long longValue() {				
				return provider.getNumber().longValue();
			}

			@Override
			public float floatValue() {
				return provider.getNumber().floatValue();
			}

			@Override
			public double doubleValue() {
				return provider.getNumber().doubleValue();
			}			
		};
	}
}
