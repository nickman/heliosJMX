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

import java.util.Map;

import javax.management.ObjectName;

import com.heliosapm.opentsdb.ExpressionResult;

/**
 * <p>Title: ExpressionProcessor</p>
 * <p>Description: Defines an expression processor which extracts and traces values sampled from a JMX MBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.ExpressionProcessor</code></p>
 */

public interface ExpressionProcessor {
	
	/**
	 * Executes a trace for one value extracted from the values collected from the passed JMX ObjectName
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @param outer Specifies if the passed loopers are looped outside any expression based loopers (true) or inside (false)
	 * @param loopers The iterables to nest the execution with
	 * @return The expression result rendered to a char sequence
	 */
	public CharSequence process(final String sourceId, Map<String, Object> attrValues, ObjectName objectName, final boolean outer, Iterable<?>...loopers);
	
	/**
	 * Executes a trace for one value extracted from the values collected from the passed JMX ObjectName
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @param loopers The outer loopers iterables to nest the execution with
	 * @return The expression result rendered to a char sequence
	 */
	public CharSequence process(final String sourceId, Map<String, Object> attrValues, ObjectName objectName, Iterable<?>...loopers);

}
