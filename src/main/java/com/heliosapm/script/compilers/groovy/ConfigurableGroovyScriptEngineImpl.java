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
package com.heliosapm.script.compilers.groovy;

import java.io.Reader;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import groovy.lang.GroovyClassLoader;

import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

/**
 * <p>Title: ConfigurableGroovyScriptEngineImpl</p>
 * <p>Description: An extension of the jsr223 Groovy GroovyScriptEngineImpl that supports configuring the compiler options</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.ConfigurableGroovyScriptEngineImpl</code></p>
 */

public class ConfigurableGroovyScriptEngineImpl extends GroovyScriptEngineImpl {
	/** The customizable compilation */
	protected final GroovyCompilationCustomizer compilationCustomizer = new GroovyCompilationCustomizer();

	final GroovyClassLoader gclassLoader;

	/**
	 * Creates a new ConfigurableGroovyScriptEngineImpl
	 * @param classLoader The configured groovy class loader
	 */
	public ConfigurableGroovyScriptEngineImpl(final GroovyClassLoader classLoader) {
		super(classLoader);
		this.gclassLoader = classLoader;
	}
	
	@Override
	public CompiledScript compile(Reader reader) throws ScriptException {
		GroovyClassLoader gcl = new GroovyClassLoader(this.gclassLoader, compilationCustomizer.getConfiguration(reader));
		gcl.s
		return super.compile(reader);
	}

}
