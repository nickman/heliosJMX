
package com.heliosapm.jmx.batch.aggregate;


/**
 * <p>Title: INumberProvider</p>
 * <p>Description: A wrapper interface that can be placed around an object that is not itself a number, but can supply one.</p> 
 * <p>Company: ICE</p>
 * @author Whitehead 
 * <p><code>com.heliosapm.jmx.batch.aggregate.INumberProvider</code></p>
 */
public interface INumberProvider {
	/**
	 * Returns the number we're interested in
	 * @return the number we're interested in
	 */
	public Number getNumber();
}
