/**
 * 
 */
package com.heliosapm.jmx.expr;

/**
 * <p>Title: CodeBuilder</p>
 * <p>Description: Helper class for building javassist code sequences</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.jmx.expr.CodeBuilder</code></b>
 */

public class CodeBuilder {
	/** The running code buffer */
	protected final StringBuilder buff = new StringBuilder();
	
	/**
	 * Creates a new CodeBuilder
	 */
	public CodeBuilder() {

	}
	
	/**
	 * Appends a stringy, optionally formatted with the passed tokens
	 * @param stringy The stringy to append
	 * @param tokens The format tokens
	 * @return this CodeBuilder
	 */
	public CodeBuilder append(final CharSequence stringy, Object...tokens) {
		if(stringy==null) throw new IllegalArgumentException("The passed stringy was null");
		buff.append(String.format(stringy.toString(), tokens));
		return this;
	}
	
	/**
	 * Resets this CodeBuilder
	 * @return this CodeBuilder
	 */
	public CodeBuilder reset() {
		buff.setLength(0);
		return this;
	}
	
	/**
	 * Renders the code, optionally formatted if tokens are provided
	 * @param tokens The format tokens
	 * @return the rendered code
	 */
	public String render(Object...tokens) {
		return String.format(buff.toString(), tokens);
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return buff.toString();
	}

}
