/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import com.heliosapm.jmx.util.helpers.ArrayUtils;

/**
 * <p>Title: RecursiveIterablesTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.RecursiveIterablesTest</code></p>
 */

public class RecursiveIterablesTest {
	protected Map<Integer, Iterable<?>> iters = new TreeMap<Integer, Iterable<?>>();
	/**
	 * Creates a new RecursiveIterablesTest
	 */
	public RecursiveIterablesTest() {
		iters.put(0, ArrayUtils.toIterable(new String[] {"One", "Two", "Three"}));
		iters.put(1, ArrayUtils.toIterable(new LinkedHashSet<String>(Arrays.asList("A", "B", "C", "D"))));
		iters.put(2, ArrayUtils.toIterable(new LinkedHashSet<String>(Arrays.asList("Foo", "Bar", "Snafu"))));		
	}
	
	public void iterate() {
		final NestedIterator top = NestedIterator.group(true, false, iters.values());
		final Stack<Object> stack = new Stack<Object>();
		loop(top, stack);
//		
//		
//		
//		
//		final Iterable<Iterator<?>> mainIter = ResettingIterable.group(true, false, iters.values());
//		final Stack<Object> stack = new Stack<Object>();
//		loop(null, mainIter.iterator(), stack);
//		Iterator<?> lastIterator = null;
//		Set<Iterator<?>> iters = new LinkedHashSet<Iterator<?>>(this.iters.size());
//		for(Iterable<?> ia: this.iters.values()) {
//			lastIterator = ia.iterator();
//			iters.add(lastIterator);
//		}
//		iterate(iters, lastIterator, null);
	}
	
	protected void loop(NestedIterator<?> iter, Stack<Object> scope) {
		if(!iter.hasNested()) {			
			while(iter.hasNext()) {
				try {
					scope.push(iter.next());
					groupComplete(scope);
				} finally {
					scope.pop();
				}
			}			
		} else {
			if(iter.hasNext()) {
				scope.push(iter.next());
			}
			loop((NestedIterator<?>) iter.nested(), scope);
		}
	}
	
	protected void iterate(final Collection<Iterator<?>> iterables, Iterator<?> lastIterator, Stack<Object> stack) {		
		if(stack==null) stack = new Stack<Object>();
		for(Iterator<?> iterator: iterables) {
			if(iterator.hasNext()) {				
				if(iterator==lastIterator) {					
					groupComplete(stack);
					iterate(iterables, lastIterator, stack);
					
				} else {
					stack.push(iterator.next());
				}
			}
		}
		
	}

	protected void groupComplete(Collection<?> args) {
		log("Group Complete: %s", args.toString());
	}
	
	protected void groupComplete(final Object...args) {
		log("Group Complete: %s", Arrays.toString(args));
	}
	
	public static void main(String[] args) {
		log("RecursiveIterablesTest Test");
		RecursiveIterablesTest rit = new RecursiveIterablesTest();
		rit.iterate();
	}
	
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}

}
