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
package com.heliosapm.script.compilers;

import java.io.Closeable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.script.DeployedScript;
import com.heliosapm.script.StateService;
import com.heliosapm.script.annotations.Dependency;
import com.heliosapm.script.compilers.groovy.ConfigurableGroovyCompiledScript;
import com.heliosapm.script.fixtures.DeployedFixture;
import com.heliosapm.script.fixtures.Fixture;

/**
 * <p>Title: FixtureCompiler</p>
 * <p>Description: Compiler for fixture deployments which are factories to create instances of specific objects</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.FixtureCompiler</code></p>
 * @param <T> The type of the objects returned by the compiled fixture
 */

public class FixtureCompiler<T> implements DeploymentCompiler<Fixture<T>> {
	/** The extensions */
	private static final String[] extensions = new String[]{"fixture"};
	/** The underlying compilation service */
	protected final StateService stateService;
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	/**
	 * Creates a new FixtureCompiler
	 * @param stateService The state service to compile fixtures
	 */
	public FixtureCompiler(final StateService stateService) {
		this.stateService = stateService;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#compile(java.net.URL)
	 */
	@Override
	public Fixture<T> compile(final URL source) throws CompilerException {
		if(source==null) throw new IllegalArgumentException("The passed source URL was null");
//		final String extension = URLHelper.getExtension(source, "").trim().toLowerCase();
		final String subExtension = URLHelper.getSubExtension(source, "").trim().toLowerCase();
		if(subExtension.isEmpty()) throw new RuntimeException("The source URL [" + source + "] has no extension");
		if(!stateService.isExtensionSupported(subExtension)) throw new RuntimeException("Source type [" + subExtension+ "] in source URL [" + source + "] is not supported");
		try {
			final CompiledScript cs = stateService.getCompilerForExtension(subExtension).compile(URLHelper.getTextFromURL(source, 1000, 1000));
			Dependency dep = cs.getClass().getAnnotation(Dependency.class);
			return new AbstractFixture(cs, URLHelper.getPlainFileName(source));
		} catch (Exception ex) {
			log.error("Failed to compile source [" + source + "]", getDiagnostic(ex),  ex);
			throw new CompilerException("Failed to compile source [" + source + "]", getDiagnostic(ex),  ex);
		}
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#deploy(java.lang.String)
	 */
	@Override
	public DeployedScript<Fixture<T>> deploy(final String sourceFile) throws CompilerException {
		final Fixture<T> fixture = compile(URLHelper.toURL(new File(sourceFile)));
		return new DeployedFixture<T>(new File(sourceFile), fixture);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.DeploymentCompiler#getSupportedExtensions()
	 */
	@Override
	public String[] getSupportedExtensions() {
		return extensions;
	}

	/**
	 * Renders the exception into a compiler diagnostic
	 * @param t The compiler thrown exception
	 * @return the compiler diagnostic
	 */
	protected String getDiagnostic(final Throwable t) {
		if(t==null) return "Null underlying exception";
		if(t instanceof ScriptException) {
			final ScriptException se = (ScriptException)t;
			return new StringBuilder()
				.append("Message: ").append(se.getMessage())
				.append(", File Name: ").append(se.getFileName())
				.append(", Line: ").append(se.getLineNumber())
				.append(", Column: ").append(se.getColumnNumber())
				.toString();
		}
		return "NonCompiler Diagnostic:" + t.toString();
	}
	
	
	/**
	 * <p>Title: AbstractFixture</p>
	 * <p>Description: Concrete fixture wrapper</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.script.compilers.FixtureCompiler.AbstractFixture</code></p>
	 */
	public class AbstractFixture implements Fixture<T>, Closeable {
		/** The underlying compiled script */
		final CompiledScript cs;		
		/** The name of the fixture's script */
		final String name;
		/** Instance logger */
		private final Logger flog;
		
		/**
		 * Creates a new AbstractFixture
		 * @param cs The underlying compiled script
		 * @param name The name of the fixture's script
		 */
		public AbstractFixture(CompiledScript cs, final String name) {
			this.cs = cs;
			this.name = name;
			flog = LoggerFactory.getLogger(getClass().getEnclosingClass().getName() + "." + this.name);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.io.Closeable#close()
		 */
		public void close() {
			if(cs!=null) {
				final Bindings binding = cs.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
				if(binding!=null) {
					for(Object var: binding.values()) {
						if(var==null) continue;
						if(var instanceof Closeable) {
							try {
								((Closeable)var).close();
								log.info("[Executable Recycle]: Closed instance of [{}]", var.getClass().getSimpleName());
							} catch (Exception x) {
								/* No Op */
							}
						}
					}
				}
			}
		}
		
		/**
		 * Returns the annotations of the underlying exec
		 * @param annotationClass The annotation to get
		 * @return the annotation or null if it was not present
		 */
		public <A extends Annotation> A getAnnotation(final Class<A> annotationClass) {
			if(cs instanceof ConfigurableGroovyCompiledScript) {
				return ((ConfigurableGroovyCompiledScript)cs).getAnnotation(annotationClass);
			}
			return cs.getClass().getAnnotation(annotationClass);
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.fixtures.Fixture#get(java.util.Map)
		 */
		@Override
		public T get(final Map<String, Object> config) {
			try {
				if(config==null) {
					return (T) cs.eval();
				} else {
					return (T) cs.eval(new SimpleBindings(config));
				}
			} catch (Exception ex) {
				flog.error("Failed to invoke fixture [{}]", name, ex);
				throw new RuntimeException(ex);
			}
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.script.fixtures.Fixture#get()
		 */
		@Override
		public T get() {
			return get(null);
		}
		
	}
	
}
