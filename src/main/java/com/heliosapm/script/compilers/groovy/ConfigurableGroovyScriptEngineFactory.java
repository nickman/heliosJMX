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

import groovy.lang.GroovyClassLoader;

import javax.script.ScriptEngine;

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;

/**
 * <p>Title: ConfigurableGroovyScriptEngineFactory</p>
 * <p>Description: An extension of the jsr223 Groovy ScriptEngineFactory that supports configuring the compiler options</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.ConfigurableGroovyScriptEngineFactory</code></p>
 */

public class ConfigurableGroovyScriptEngineFactory extends GroovyScriptEngineFactory {
	/** The customized groovy class loader */
	protected final GroovyClassLoader groovyClassLoader;
	/** The customizable compilation */
	protected final GroovyCompilationCustomizer compilationCustomizer = new GroovyCompilationCustomizer();
	
	/**
	 * Creates a new ConfigurableGroovyScriptEngineFactory
	 */
	public ConfigurableGroovyScriptEngineFactory() {
		groovyClassLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), compilationCustomizer.getDefaultConfig(), true);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.codehaus.groovy.jsr223.GroovyScriptEngineFactory#getEngineName()
	 */
	@Override
	public String getEngineName() {	
		return "Configurable " + super.getEngineName();
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.codehaus.groovy.jsr223.GroovyScriptEngineFactory#getScriptEngine()
	 */
	@Override
	public ScriptEngine getScriptEngine() {
		return new ConfigurableGroovyScriptEngineImpl(groovyClassLoader);
	}

}
