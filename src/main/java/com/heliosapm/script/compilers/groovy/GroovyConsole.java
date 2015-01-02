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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.jmx.expr.ExpressionCompiler;
import com.heliosapm.jmx.util.helpers.JMXHelper;
import com.heliosapm.jmx.util.helpers.SystemClock;
import com.heliosapm.jmx.util.helpers.URLHelper;
import com.heliosapm.opentsdb.TSDBSubmitterConnection;

/**
 * <p>Title: GroovyConsole</p>
 * <p>Description: Groovy Console Invocation Service</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.GroovyConsole</code></p>
 */

public class GroovyConsole implements GroovyConsoleMXBean {
	/** Singleton instance */
	private static volatile GroovyConsole instance = null;
	/** Singleton instance ctor lock */
	private static final Object lock = new Object();
	/** The instance logger */
	protected final Logger log = LoggerFactory.getLogger(getClass());
	/** The groovy compilation customizer */
	protected final GroovyCompilationCustomizer compilationCustomizer = new GroovyCompilationCustomizer();
	/** The shared bindings */
	protected final Map<String, Object> beans = new HashMap<String, Object>();
	/** This service's JMX ObjectName */
	protected final ObjectName objectName;
	/** A groovy classloader for compiling scripts */
	protected final GroovyClassLoader groovyClassLoader; 
	/** The console UI class */
	protected final Class<?> consoleClass;
	/** The console UI class */
	protected final Constructor<?> consoleCtor;
	
	
	
	/**
	 * Acquires the GroovyConsole singleton instance
	 * @return the GroovyConsole singleton instance
	 */
	public static GroovyConsole getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {					
					instance = new GroovyConsole();
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new GroovyConsole
	 */
	private GroovyConsole() {
		objectName = JMXHelper.objectName(getClass().getPackage().getName() + ":service=" + getClass().getSimpleName());
		groovyClassLoader =  new GroovyClassLoader(getClass().getClassLoader(), compilationCustomizer.getDefaultConfig());
		
		// ==================================
		// Prepare Console Class
		// ==================================
		URL consoleSourceUrl = getClass().getClassLoader().getResource("groovy/ui/Console.groovy");
		byte[] consoleBytes = URLHelper.getBytesFromURL(consoleSourceUrl);
		
		if(JMXHelper.getHeliosMBeanServer().isRegistered(objectName)) {
			try { JMXHelper.getHeliosMBeanServer().unregisterMBean(objectName); } catch (Exception ex) {/* No Op */}
		}
		try { 
			JMXHelper.getHeliosMBeanServer().registerMBean(this, objectName); 
			log.info("\n\t============================================\n\tRegistered [" + objectName + "]\n\t============================================\n");
		} catch (Exception ex) {
			log.warn("Failed to register GroovyService Management Interface", ex);
		}
		try {
			groovyClassLoader.parseClass(new String(consoleBytes, Charset.defaultCharset()));
			consoleClass =  Class.forName("groovy.ui.Console", true, groovyClassLoader);
			consoleCtor = consoleClass.getDeclaredConstructor(ClassLoader.class, Binding.class);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to compile Console", ex);
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.groovy.GroovyConsoleMXBean#launchConsole()
	 */
	@Override
	public void launchConsole() {
		launchConsole(null);
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.script.compilers.groovy.GroovyConsoleMXBean#launchConsole(java.lang.String)
	 */
	@Override
	public void launchConsole(String fileName) {
		final ClassLoader cl = Thread.currentThread().getContextClassLoader();
		final ClassLoader thisClassLoader = groovyClassLoader;
		try {
			Thread.currentThread().setContextClassLoader(thisClassLoader);
			Class<?> clazz = Class.forName("groovy.ui.Console", true, thisClassLoader);
			Constructor<?> ctor = clazz.getDeclaredConstructor(ClassLoader.class, Binding.class);
			Object console = ctor.newInstance(thisClassLoader, getBindings());
			console.getClass()
				.getDeclaredMethod("run")
					.invoke(console);
			console.getClass()
			.getDeclaredMethod("setConfig", CompilerConfiguration.class)
				.invoke(console, compilationCustomizer.getDefaultConfig());
//			console.getClass()
//			.getDeclaredMethod("setCaptureStdErr", boolean.class)
//				.invoke(null, false);
//			console.getClass()
//			.getDeclaredMethod("setCaptureStdOut", boolean.class)
//				.invoke(null, false);

			
			if(fileName!=null) {
				fileName = fileName.trim();
				File f = new File(fileName);
				if(f.canRead()) {
					clazz.getDeclaredMethod("loadScriptFile", File.class).invoke(console, f);
				}
			}
		} catch (Exception e) {
			log.error("Failed to launch console", e);
			if(e.getCause()!=null) {
				log.error("Failed to launch console cause", e.getCause());
			}
			throw new RuntimeException("Failed to launch console", e);
		} finally {
			Thread.currentThread().setContextClassLoader(cl);
		}
	}
	
	/**
	 * Returns a bindings instance
	 * @return a bindings instance
	 */
	protected Binding getBindings() {
		final Binding binding = new Binding(beans);
		if(beans.isEmpty()) {
			synchronized(beans) {
				if(beans.isEmpty()) {
					beans.put("binding", binding);
					beans.put("sysclock", SystemClock.class);
					beans.put("log", log);
					beans.put("tsdbConn", TSDBSubmitterConnection.class);
					beans.put("mbeanServer", JMXHelper.getHeliosMBeanServer());
					beans.put("jmxHelper", JMXHelper.class);
					beans.put("exprCompiler", ExpressionCompiler.getInstance());
					
					
//					for(String beanName: applicationContext.getBeanDefinitionNames()) {
//						Object bean = applicationContext.getBean(beanName);
//						if(bean==null) continue;
//						beans.put(beanName, bean);
//					}
//					beans.put("RootCtx", applicationContext);					
				}
			}
		}
		//return new ThreadSafeNoNullsBinding(beans);
		
		
		return binding;
	}
	

}
