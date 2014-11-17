/**
 * 
 */
package com.heliosapm.jmx.expr;

import java.util.Stack;

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
	/** The save/restore position stack */
	protected final Stack<Integer> saveStack = new Stack<Integer>();
	
	/**
	 * Creates a new CodeBuilder
	 */
	public CodeBuilder() {

	}
	
	/**
	 * Creates a new CodeBuilder
	 * @param initial The initial values to append to this CodeBuilder
	 */
	public CodeBuilder(Object...initial) {
		if(initial!=null) {
			for(Object o: initial) {
				if(o==null) continue;
				buff.append(o.toString());
			}
		}
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
	 * Pushes the current buffer size onto the save/restore stack
	 * @return this code builder
	 */
	public CodeBuilder push() {
		saveStack.push(buff.length());
		return this;
	}
	
	/**
	 * Restores the buff content saved at the most recent {@link #push()}
	 * @return this code builder
	 */
	public CodeBuilder pop() {
		int pos = saveStack.pop();
		buff.setLength(pos);
		return this;
	}
	
	/**
	 * Returns the next code position that would
	 * be returned if the save/restore stack was popped
	 * @return the next popped code position 
	 */
	public int peek() {
		return saveStack.peek();
	}
	
	/**
	 * Trims the last n characters off the end of the current buffer
	 * @param chars The number of characters to trim
	 * @return this CodeBuilder
	 */
	public CodeBuilder trim(final int chars) {
		for(int i = 0; i < chars; i++) {
			buff.deleteCharAt(buff.length()-1);
		}
		return this;
	}
	
	/**
	 * Renders the code, optionally formatted if tokens are provided
	 * @param tokens The format tokens
	 * @return the rendered code
	 */
	public String render(final Object...tokens) {
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
