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
package com.heliosapm.script.compilers.groovy;

import java.lang.annotation.Annotation;

import org.codehaus.groovy.jsr223.GroovyCompiledScript;

/**
 * <p>Title: ConfigurableGroovyCompiledScript</p>
 * <p>Description: Wrapper for a configurable compiler compiled {@link GroovyCompiledScript}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.ConfigurableGroovyCompiledScript</code></p>
 */

public class ConfigurableGroovyCompiledScript extends GroovyCompiledScript {
	/** The compiled script class */
	protected final Class<?> clazz;
	/**
	 * Creates a new ConfigurableGroovyCompiledScript
	 * @param engine The engine that compiled this script
	 * @param clazz The compiled script class
	 */
	public ConfigurableGroovyCompiledScript(final ConfigurableGroovyScriptEngineImpl engine, final Class<?> clazz) {
		super(engine, clazz);
		this.clazz = clazz;
	}
	
	/**
	 * Returns the annotations of the underlying exec
	 * @param annotationClass The annotation to get
	 * @return the annotation or null if it was not present
	 */
	public <A extends Annotation> A getAnnotation(final Class<A> annotationClass) {
		return clazz.getAnnotation(annotationClass);
	}

	
	

}
