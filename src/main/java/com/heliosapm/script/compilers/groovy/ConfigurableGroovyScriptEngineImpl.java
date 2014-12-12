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

import java.io.IOException;
import java.io.Reader;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.codehaus.groovy.jsr223.GroovyCompiledScript;
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
	
	
	/**
	 * {@inheritDoc}
	 * @see org.codehaus.groovy.jsr223.GroovyScriptEngineImpl#compile(java.io.Reader)
	 */
	@Override
	public CompiledScript compile(final Reader source) throws ScriptException {
		GroovyClassLoader gcl = compilationCustomizer.getGroovyClassLoader(source); 
		final String src = readFully(source);
		compilationCustomizer.clearCompilerContext();
		Class<?> clazz = gcl.parseClass(src);		
		return new ConfigurableGroovyCompiledScript(this, clazz);
	}
	
	/**
	 * {@inheritDoc}
	 * @see org.codehaus.groovy.jsr223.GroovyScriptEngineImpl#compile(java.lang.String)
	 */
	@Override
	public CompiledScript compile(final String source) throws ScriptException {
		GroovyClassLoader gcl = compilationCustomizer.getGroovyClassLoader(source); 
		Class<?> clazz = gcl.parseClass(source);		
		return new ConfigurableGroovyCompiledScript(this, clazz);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.script.AbstractScriptEngine#eval(java.io.Reader)
	 */
	@Override
	public Object eval(Reader reader) throws ScriptException {
		// TODO Auto-generated method stub
		return super.eval(reader);
	}
	
	
    private String readFully(Reader reader) throws ScriptException {
        char[] arr = new char[8 * 1024]; // 8K at a time
        StringBuilder buf = new StringBuilder();
        int numChars;
        try {
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                buf.append(arr, 0, numChars);
            }
        } catch (IOException exp) {
            throw new ScriptException(exp);
        }
        return buf.toString();
    }
	

}
