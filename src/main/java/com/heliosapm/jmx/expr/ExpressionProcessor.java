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

/**
 * <p>Title: ExpressionProcessor</p>
 * <p>Description: Defines an expression processor which extracts and traces values sampled from a JMX MBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.ExpressionProcessor</code></p>
 */

public interface ExpressionProcessor {
	
	public static final String OBJECTNAME_KEY = "\\{key:.*?\\}";
	
	
	/**
	 * Executes a trace for one value extracted from the values collected from the passed JMX ObjectName
	 * @param sourceId A unique id identifying where the values and object name were collected from
	 * @param attrValues A map of attribute values keyed by the attribute name
	 * @param objectName The JMX ObjectName of the MBean the attribute values were sampled from
	 * @return The expression result which can render an OpenTSDB put command for the extracted metric, or null if one could not be read
	 */
	public ExpressionResult process(final String sourceId, Map<String, Object> attrValues, ObjectName objectName); 
}
