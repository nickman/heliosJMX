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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.Map;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.codehaus.groovy.ast.AnnotationNode;

/**
 * <p>Title: ElementTypeMapping</p>
 * <p>Description: Maps java element types to groovy target types</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.script.compilers.groovy.ElementTypeMapping</code></p>
 */

public enum ElementTypeMapping {
    /** Element type mapping for TYPE*/
    TYPE(AnnotationNode.TYPE_TARGET), 
    /** Element type mapping for FIELD*/
    FIELD(AnnotationNode.FIELD_TARGET), 
    /** Element type mapping for METHOD*/
    METHOD(AnnotationNode.METHOD_TARGET), 
    /** Element type mapping for PARAMETER*/
    PARAMETER(AnnotationNode.PARAMETER_TARGET), 
    /** Element type mapping for CONSTRUCTOR*/
    CONSTRUCTOR(AnnotationNode.CONSTRUCTOR_TARGET), 
    /** Element type mapping for LOCAL_VARIABLE*/
    LOCAL_VARIABLE(AnnotationNode.LOCAL_VARIABLE_TARGET), 
    /** Element type mapping for ANNOTATION_TYPE*/
    ANNOTATION_TYPE(AnnotationNode.ANNOTATION_TARGET), 
    /** Element type mapping for PACKAGE*/
    PACKAGE(AnnotationNode.PACKAGE_TARGET);

    
    private ElementTypeMapping(final int mask) {
    	this.mask = mask;
    }
    
    /** The mapping mask */
    public final int mask;
    
    /** A cache of annotation classes keyed by the classname */
    private static final Map<String,WeakReference<Class<? extends Annotation>>> classMap = new NonBlockingHashMap<String,WeakReference<Class<? extends Annotation>>>();
    
    
    /**
     * Returns the bitmask for the element types in the passed target annotation
     * @param target The target annotation
     * @param minus An array of element types that should not be included in the mask
     * @return the computed bitmask
     */
    public static int getMaskFor(final Target target, final ElementType...minus) {
    	int m = 0;
    	for(ElementType et: target.value()) {
    		m = m | ElementTypeMapping.valueOf(et.name()).mask;
    	}
    	for(ElementType et: minus) {
    		m = m & ~ElementTypeMapping.valueOf(et.name()).mask; 
    	}
    	return m;
    }
    
    /**
     * Returns the bitmask for the passed Groovy AST annotation node
     * @param node The Groovy AST annotation node
     * @param minus An array of element types that should not be included in the mask
     * @return the computed bitmask
     */
	public static int getMaskFor(final AnnotationNode node, final ElementType...minus) {
    	String annotationClassName = node.getClassNode().getName();
    	Class<? extends Annotation> annClass = null;
    	try {
    		annClass = classForName(annotationClassName);
    	} catch (Exception ex) {
    		throw new RuntimeException("Failed to class load [" + annotationClassName + "]", ex);
    	}
    	Target target = annClass.getAnnotation(Target.class);
    	if(target==null) {
    		throw new RuntimeException("No @Target annotation on [" + annotationClassName + "]");
    	}
    	return getMaskFor(target, minus);
    }
	
	/**
	 * Sets the allowed target bitmask on the passed annotation node
	 * @param node The annotation node to set on
	 * @param minus An array of element types that should not be included in the mask
	 * @return The bitmask that was set
	 */
	public static int setMaskFor(final AnnotationNode node, final ElementType...minus) {
		final int mask = getMaskFor(node, minus);
		node.setAllowedTargets(mask);
		return mask;
	}
	
    
    /**
     * Retrieves the annotation class for the passed class name
     * @param className The name of the annotation class
     * @return The annotation class
     * @throws ClassNotFoundException thrown if the named class cannot be loaded
     */
    @SuppressWarnings("unchecked")
	public static Class<? extends Annotation> classForName(final String className) throws ClassNotFoundException {
    	if(className==null || className.trim().isEmpty()) throw new IllegalArgumentException("The passed classname was null or empty");
    	WeakReference<Class<? extends Annotation>> annotationClass = classMap.get(className.trim());
    	if(annotationClass == null || annotationClass.get()==null) {
    		synchronized(classMap) {
    			annotationClass = classMap.get(className.trim());
    	    	if(annotationClass == null || annotationClass.get()==null) {
    	    		Class<? extends Annotation> clazz = (Class<? extends Annotation>)Class.forName(className); 
    	    		annotationClass = new WeakReference<Class<? extends Annotation>>(clazz); 
    	    		classMap.put(className.trim(), annotationClass);
    	    	}
    		}
    	}
    	return annotationClass.get();
    }
}
