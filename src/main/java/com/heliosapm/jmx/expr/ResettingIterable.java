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

/**
 * <p>Title: ResettingIterable</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.ResettingIterable</code></p>
 * @param <T> The expected type iterated by this iterable
 */

public class ResettingIterable<T> implements Iterable<T> {
	/** The delegate iterable wrapped by this instance */
	protected final Iterable<T> delegateIterable;
	/** true if the iterators returned support {@link Iterator#remove()}, false otherwise */
	protected final boolean removable;
	/** The current iterator */
	protected Iterator<T> currentIterator = null;
	
	
	/**
	 * Converts the passed array of iterables to one iterables containing iterators for all the passed iterables
	 * @param resetable true if the returned iterable and the sub iterators should auto-reset on {@link Iterator#hasNext()} = false,  false otherwise 
	 * @param removable true if the returned iterable should be removable, false otherwise
	 * @param iterables The iterables to wrap
	 * @return An iterable of iterators
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Iterable<Iterator<?>> group(final boolean resetable, final boolean removable, final Iterable<?>...iterables) {
		if(iterables==null || iterables.length==0) return (Collection<Iterator<?>>) Collections.emptyIterator();
		List<Iterator<?>> iters = new ArrayList<Iterator<?>>(iterables.length);
		for(Iterable<?> iterable: iterables) {
			if(iterable==null) continue;
			iters.add((Iterator<?>) new ResettingIterable(iterable, removable).iterator());
		}
		return resetable ? new ResettingIterable(iters, removable) : iters;
	}
	
	/**
	 * Converts the passed collection of iterables to one iterables containing iterators for all the passed iterables
	 * @param resetable true if the returned iterable and the sub iterators should auto-reset on {@link Iterator#hasNext()} = false,  false otherwise 
	 * @param removable true if the returned iterable should be removable, false otherwise
	 * @param iterables The iterables to wrap
	 * @return An iterable of iterators
	 */
	public static Iterable<Iterator<?>> group(final boolean resetable, final boolean removable, final Collection<?> iterables) {
		return group(resetable, removable, iterables.toArray(new Iterable[0]));
	}
	
	/**
	 * Creates a new ResettingIterable
	 * @param delegateIterable The delegate iterable wrapped by this instance
	 * @param removable true if the iterators returned support {@link Iterator#remove()}, false otherwise
	 */
	public ResettingIterable(final Iterable<T> delegateIterable, final boolean removable) {
		this.delegateIterable = delegateIterable;
		this.removable = removable;
	}
	
	/**
	 * Creates a new ResettingIterable that supports {@link Iterator#remove()} if the delegate supports it
	 * @param delegateIterable The delegate iterable wrapped by this instance
	 */
	public ResettingIterable(final Iterable<T> delegateIterable) {
		this(delegateIterable, true);
	}
	

	/**
	 * {@inheritDoc}
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<T> iterator() {
		currentIterator = new ResettingIterator(delegateIterable.iterator());
		return currentIterator;
	}

	
	/**
	 * <p>Title: ResettingIterator</p>
	 * <p>Description: The delegate resetting iterator that resets when {@link Iterator#hasNext()} returns false</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.expr.ResettingIterable.ResettingIterator</code></p>
	 */
	@SuppressWarnings("hiding")
	class ResettingIterator<T> implements Iterator<T> {
		private Iterator<T> delegateIterator;
		
		public ResettingIterator(Iterator<T> delegateIterator) {
			this.delegateIterator = delegateIterator;
		}

		/**
	     * Returns {@code true} if the iteration has more elements.
	     * (In other words, returns {@code true} if {@link #next} would
	     * return an element rather than throwing an exception.)
	     * If false is returned, the iterator is reset.
	     *
	     * @return {@code true} if the iteration has more elements
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			final boolean has = delegateIterator.hasNext();
			if(!has) {
				iterator();
				delegateIterator = (Iterator<T>) currentIterator;
			}
			return has;
		}

		/**
		 * @return
		 * @see java.util.Iterator#next()
		 */
		public T next() {
			return delegateIterator.next();
		}

		/**
		 * 
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			if(!removable) throw new UnsupportedOperationException("This iterator does not support remove");
			delegateIterator.remove();			
		}
		
		
		
	}

}
