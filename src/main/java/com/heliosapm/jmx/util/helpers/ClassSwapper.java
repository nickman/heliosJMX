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
package com.heliosapm.jmx.util.helpers;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicReference;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.attachme.agent.LocalAgentInstaller;

/**
 * <p>Title: ClassSwapper</p>
 * <p>Description: Bytecode swapper</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.ClassSwapper</code></p>
 */

public class ClassSwapper {
	private static volatile ClassSwapper instance = null;
	private static final Object lock = new Object();

	public static ClassSwapper getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ClassSwapper();
				}
			}
		}
		return instance;
	}
	
	
	/** The local instrumentation instance */
	private final Instrumentation instr;
	
	private final SLogger log = SimpleLogger.logger(getClass());
	
	/** A cache of the original byte code keyed by the class name */
	private final NonBlockingHashMap<String, byte[]> originalByteCode = new NonBlockingHashMap<String, byte[]>();  
	
	/**
	 * Creates a new ClassSwapper
	 */
	private ClassSwapper() {
		instr = LocalAgentInstaller.getInstrumentation();
	}
	
	public void swapIn(final Class<?> instrumentedClass) {
		log.log("Swapping in class [%s]", instrumentedClass.getName());
		// e.g.   com.sun.jmx.remote.socket.InstrumentedSocketConnection
		final String toName = instrumentedClass.getName();
		final String fromName = instrumentedClass.getName().replace(".Instrumented", ".");	
		log.log("Swap class impl [%s] for [%s]", instrumentedClass.getName(), fromName);
		final String fromInternalName = fromName.replace('.', '/');
		final Class<?> fromClass = findClass(fromName);
		final ClassLoader fromClassLoader = fromClass.getClassLoader();
		final ClassLoader toClassLoader = instrumentedClass.getClassLoader();
		try {
			ClassPool cp = new ClassPool();
			cp.appendSystemPath();
			cp.appendClassPath(new LoaderClassPath(toClassLoader));
			if(toClassLoader != fromClassLoader) {
				cp.appendClassPath(new LoaderClassPath(fromClassLoader));
			}
			CtClass toClazz = cp.get(toName);
			toClazz.setName(fromName);
			CtClass fromClazz = cp.get(fromName);
			int ok = 0, failed = 0;
			Throwable x = null;
			for(final CtMethod ctm: fromClazz.getDeclaredMethods()) {
				try {
					CtMethod newMethod = null;
					try {
						newMethod = toClazz.getMethod(ctm.getName(), ctm.getMethodInfo().getDescriptor());		
					} catch (Exception ex) {
						continue;
					}
					fromClazz.removeMethod(ctm);										
					fromClazz.addMethod(CtNewMethod.copy(newMethod, fromClazz, null));
					ok++;
				} catch (Throwable tx) {
					failed++;
					x = tx;
				}
			}
			log.log("Class Swap Method Counts: OK: [%s],  Failed: [%s]", ok, failed);
			if(failed>0) {
				throw new RuntimeException("Failed to swap classes", x);
			}
			ok = 0;
			failed = 0;
			x = null;
			for(final CtConstructor ctx: fromClazz.getDeclaredConstructors()) {
				try {
					CtConstructor newCtor = null;
					try {
						newCtor = toClazz.getConstructor(ctx.getMethodInfo().getDescriptor());		
					} catch (Exception ex) {
						continue;
					}
					fromClazz.removeConstructor(ctx);										
					fromClazz.addConstructor(CtNewConstructor.copy(newCtor, fromClazz, null));
					ok++;
				} catch (Throwable tx) {
					failed++;
					x = tx;
				}
			}
			log.log("Class Swap Ctor Counts: OK: [%s],  Failed: [%s]", ok, failed);
			if(failed>0) {
				throw new RuntimeException("Failed to swap classes", x);
			}
			
			final byte[] byteCode = fromClazz.toBytecode();
			final ClassFileTransformer ctf = new ClassFileTransformer() {
				@Override
				public byte[] transform(ClassLoader loader, String className,
						Class<?> classBeingRedefined,
						ProtectionDomain protectionDomain,
						byte[] classfileBuffer)
						throws IllegalClassFormatException {
					if(fromInternalName.equals(className)) {
						return byteCode;
					}
					return classfileBuffer;
				}
			};
			try {
				instr.addTransformer(ctf, true);
				instr.retransformClasses(fromClass);
			} finally {
				try { instr.removeTransformer(ctf); } catch (Exception x0) {}
			}			
		} catch (Throwable t) {
			t.printStackTrace(System.err);
			throw new RuntimeException(t);
		}
	}
	
	public Class<?> findClass(final String className) {
		try {
			return Class.forName(className);
		} catch (Exception ex) {			
			for(Class<?> clazz: instr.getAllLoadedClasses()) {
				try {
					if(className.equals(clazz.getName())) {
						return clazz;
					}
				} catch (Exception x) {/* No Op */}
			}
			throw new RuntimeException("Failed to find class [" + className + "]");
		}
	}
	
	public byte[] getByteCodeFor(final Class<?> clazz) {
		final AtomicReference<byte[]> byteCode = new AtomicReference<byte[]>();
		final String internalName = clazz.getName().replace('.', '/');
		final ClassFileTransformer cft = new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className,
					Class<?> classBeingRedefined,
					ProtectionDomain protectionDomain, byte[] bc)
					throws IllegalClassFormatException {
				if(internalName.equals(className)) {
					byteCode.set(bc);
				}
				return bc;
			}
		};
		try {
			instr.addTransformer(cft, true);
			instr.retransformClasses(clazz);
			return byteCode.get();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to transform class [" + clazz.getName() + "]", ex);		
		} finally {
			try { instr.removeTransformer(cft); } catch (Exception ex) {}
		}		
	}

}
