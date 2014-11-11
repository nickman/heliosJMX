/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2014, Helios Development Group and individual contributors
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
package com.heliosapm.jmx.expr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.heliosapm.jmx.util.helpers.ArrayUtils;

/**
 * <p>Title: NestedIterator</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.NestedIterator</code></p>
 */

public class NestedIterator<E> implements Iterator<E> {
	/** The delegate iterator, or this */
	private final Iterator<E> delegate;

	/** The next iterator in the chain */
	private Iterator<?> nested;
	
	
	public static NestedIterator<?> group(final boolean resetable, final boolean removable, final Object...iterables) {
		return _group(resetable, removable, ArrayUtils.toIterables(iterables));
	}
	
	public static NestedIterator<?> group(final boolean resetable, final boolean removable, final Collection<Object> iterables) {
		return _group(resetable, removable, ArrayUtils.toIterables(iterables.toArray()));
	}
	
	
	private static NestedIterator<?> _group(final boolean resetable, final boolean removable, final Iterable<?>...iterables) {
		if(iterables==null || iterables.length==0) throw new IllegalArgumentException("No iterables provided. Must provide at least one");
		NestedIterator ni = null;
		NestedIterator top = null;
		final int index = iterables.length-1;
		for(int i = 0; i <= iterables.length; i++) {
			if(ni==null) {
				ni = resetable ? new NestedIterator(new ResettingIterable(iterables[i], removable).iterator()) : new NestedIterator(iterables[i].iterator());
				top = ni;
			}
			if(i < index) {				
				NestedIterator nit = resetable ? new NestedIterator(new ResettingIterable(iterables[i+1], removable).iterator()) : new NestedIterator(iterables[i+1].iterator());
				ni.setNested(nit);
				ni = nit;
			}
		}
		return top;		
	}
	
	/**
	 * Creates a new NestedIterator with no next, 
	 * i.e. it's the last iterator in the chain.
	 * @param delegate The delegate iterator, or this 
	 */
	public NestedIterator(final Iterator<E> delegate) {
		this(delegate, null);
	}
	
	private void setNested(final Iterator<?> nested) {
		this.nested = nested;
	}
	
	/**
	 * Creates a new NestedIterator
	 * @param delegate The delegate iterator, or this 
	 * @param nested The next iterator in the chain
	 */
	public NestedIterator(final Iterator<E> delegate, final Iterator<?> nested) {
		if(delegate==null) throw new IllegalArgumentException("The passed delegate Iterator was null");
		this.delegate = delegate;
		this.nested = nested;
	}
	
	

	/**
	 * {@inheritDoc}
	 * @see java.util.Iterator#hasNext()
	 */
	@Override
	public boolean hasNext() {
		return delegate.hasNext();
	}

	/**
	 * {@inheritDoc}
	 * @see java.util.Iterator#next()
	 */
	@Override
	public E next() {
		return delegate.next();
	}

	/**
	 * {@inheritDoc}
	 * @see java.util.Iterator#remove()
	 */
	@Override
	public void remove() {
		delegate.remove();
	}
	
	/**
	 * Indicates if there is a next iterator in the chain
	 * @return true if there is a next iterator in the chain, false otherwise
	 */
	public boolean hasNested() {
		return nested!=null;
	}
	
	/**
	 * Returns the next iterator in the chain
	 * @return the next iterator in the chain
	 */
	public Iterator<?> nested() {
		if(nested==null) throw new IllegalAccessError("No next iterator");
		return nested;
	}

}
