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
    
    
    /**
     * Returns the bitmask for the element types in the passed target annotation
     * @param target The target annotation
     * @return the computed bitmask
     */
    public static int getMaskFor(final Target target) {
    	int m = 0;
    	for(ElementType et: target.value()) {
    		m = m | ElementTypeMapping.valueOf(et.name()).mask;
    	}
    	return m;
    }
    
    /**
     * Returns the bitmask for the passed Groovy AST annotation node
     * @param node The Groovy AST annotation node
     * @return the computed bitmask
     */
    public static int getMaskFor(final AnnotationNode node) {
    	String annotationClassName = node.getClassNode().getName();
    	Class<? extends Annotation> annClass = null;
    	try {
    		annClass = (Class<? extends Annotation>)Class.forName(annotationClassName);
    	} catch (Exception ex) {
    		throw new RuntimeException("Failed to class load [" + annotationClassName + "]", ex);
    	}
    	Target target = annClass.getAnnotation(Target.class);
    	if(target==null) {
    		throw new RuntimeException("No @Target annotation on [" + annotationClassName + "]");
    	}
    	return getMaskFor(target);
    }
}
