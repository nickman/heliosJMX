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
package com.heliosapm.script.fixtures;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: FixtureInjectionCustomizer</p>
 * <p>Description: Fixture injection annotation processor</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.fixtures.FixtureInjectionCustomizer</code></p>
 */

public class FixtureInjectionCustomizer extends CompilationCustomizer {
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());
	/** A map to track what's been done during the compilation phase */
	protected final ConcurrentHashMap<String, Object> compilerContext;
	
	/** The fixture annotation class node */
	protected static final ClassNode INJ_FIXTURE_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.InjectFixture.class);
	/** The fixture param annotation class node */
	protected static final ClassNode INJ_FIXTURE_ARG_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.InjectFixture.class);
	/** The fixture result annotation class node */
	protected static final ClassNode INJ_FIXTURE_RES_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.InjectFixtureResult.class);
	/** The fixture result param annotation class node */
	protected static final ClassNode INJ_FIXTURE_ARG_RES_CLASS_NODE = ClassHelper.make(com.heliosapm.script.annotations.InjectFixture.class);
	
	/** The fixture annotation node */
	protected static final AnnotationNode INJ_FIXTURE_ANNOTATION = new AnnotationNode(INJ_FIXTURE_CLASS_NODE);
	/** The fixture param annotation node */
	protected static final AnnotationNode INJ_FIXTURE_ARG_ANNOTATION = new AnnotationNode(INJ_FIXTURE_ARG_CLASS_NODE);
	
	
	
	
	


	/**
	 * Creates a new FixtureInjectionCustomizer
	 * @param phase The compilation phase
	 * @param compilerContext The compiler context
	 */
	public FixtureInjectionCustomizer(final CompilePhase phase, final ConcurrentHashMap<String, Object> compilerContext) {
		super(phase);
		this.compilerContext = compilerContext;
	}

	/**
	 * {@inheritDoc}
	 * @see org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation#call(org.codehaus.groovy.control.SourceUnit, org.codehaus.groovy.classgen.GeneratorContext, org.codehaus.groovy.ast.ClassNode)
	 */
	@Override
	public void call(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) throws CompilationFailedException {
		if(compilerContext.containsKey("fixtures")) return;
		final AtomicInteger annotationsProcessed = new AtomicInteger(0);
		try {
			final int targetNodesBitMask =  AnnotationNode.FIELD_TARGET | AnnotationNode.LOCAL_VARIABLE_TARGET; 
			classNode.visitContents(new ClassCodeVisitorSupport() {
				@Override
				protected SourceUnit getSourceUnit() {
					return source;
				}
				@Override
				public void visitAnnotations(final AnnotatedNode node) {
					
					for(AnnotationNode dep: node.getAnnotations()) {
						if(dep.isTargetAllowed(targetNodesBitMask)) {
							log.info("Examining Annotation [{}] on [{}].[{}]", dep.getClassNode(), classNode.getName(), node.getText());
							final List<AnnotationNode> fixAnns = node.getAnnotations(INJ_FIXTURE_CLASS_NODE);
							if(!fixAnns.isEmpty()) {
								final AnnotationNode fix = fixAnns.get(0);
								String name = ((ConstantExpression)fix.getMember("name")).getText();
								ClassExpression ce = (ClassExpression)fix.getMember("type");
								if(ce==null) {
									ce = new ClassExpression(ClassHelper.make(Object.class));
								}
								Class<?> clazz = ce.getType().getTypeClass();
								log.info("Processing @InjectFixture for [{}]/[{}] on [{}].[{}], type: [{}]", name, clazz.getName(), classNode.getName(), node.getText(), node.getClass().getName());
								if(node instanceof DeclarationExpression) {
									DeclarationExpression de = (DeclarationExpression)node;
									if(((ConstantExpression)de.getRightExpression()).getValue()==null) {
										de.setRightExpression(FixtureCache.getMethodCallExpression(name));
									}
								} else if(node instanceof PropertyNode) {
									PropertyNode pnode = (PropertyNode)node;
									log.info("Processing Property: [{}]", pnode);
								} else if(node instanceof FieldNode) {
									FieldNode fnode = (FieldNode)node;
									FixtureAccessor<?> fa = FixtureCache.getInstance().get(name);
									if(fa==null) {
										log.warn("No fixture accessor found for Fixture Name [{}] while compiling [{}].[{}]", name, classNode.getName(), node.getText());
									} else {
										fnode.setInitialValueExpression(FixtureCache.getMethodCallExpression(name));
										//fnode.setInitialValueExpression(new ConstantExpression("Hello World !"));
									}									
								}
								annotationsProcessed.incrementAndGet();
							}
							fixAnns.clear();
							fixAnns.addAll(node.getAnnotations(INJ_FIXTURE_RES_CLASS_NODE));
							if(!fixAnns.isEmpty()) {
								final AnnotationNode fix = fixAnns.get(0);
								String name = ((ConstantExpression)fix.getMember("name")).getText();
								ClassExpression ce = (ClassExpression)fix.getMember("type");
								if(ce==null) {
									ce = new ClassExpression(ClassHelper.make(Object.class));
								}
								Class<?> clazz = ce.getType().getTypeClass();
								Expression argsExpr = fix.getMember("args");
								log.info("Processing @InjectFixtureResult for [{}]/[{}] : [{}}", name, clazz.getName(), argsExpr);
								if(node instanceof DeclarationExpression) {
									DeclarationExpression de = (DeclarationExpression)node;
									if(((ConstantExpression)de.getRightExpression()).getValue()==null) {
										de.setRightExpression(FixtureCache.getParamMethodCallExpression(name, fix));
									}
								}								
								annotationsProcessed.incrementAndGet();
							}
							
						}
					}
					super.visitAnnotations(node);
				}
			});		
			compilerContext.putIfAbsent("fixtures", annotationsProcessed.get() > 0);
		} finally {
			compilerContext.putIfAbsent("fixtures", false);
		}
		
	}
		
	

}
