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

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: AnnotationHelper</p>
 * <p>Description: Annotation utility functions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.util.helpers.AnnotationHelper</code></p>
 */

public class AnnotationHelper {

    /** A weak reference cache of annotation classes keyed by the classname */
    private static final Map<String,WeakReference<Class<? extends Annotation>>> classMap = new NonBlockingHashMap<String,WeakReference<Class<? extends Annotation>>>();
    
    /** A set of primitives and their Object equivalents */
    public static final Set<Class<?>> PTYPES = Collections.unmodifiableSet(new HashSet<Class<?>>(Arrays.asList(
    		byte.class, Byte.class, boolean.class, Boolean.class, short.class, Short.class,
    		char.class, Character.class, int.class, Integer.class, long.class, Long.class,
    		float.class, Float.class, double.class, Double.class
    )));

    /**
     * Retrieves the annotation class for the passed class name
     * @param className The name of the annotation class
     * @return The annotation class
     * @throws ClassNotFoundException thrown if the named class cannot be loaded
     */
	public static Class<? extends Annotation> classForName(final String className) throws ClassNotFoundException {
    	if(className==null || className.trim().isEmpty()) throw new IllegalArgumentException("The passed classname was null or empty");
    	WeakReference<Class<? extends Annotation>> annotationClass = classMap.get(className.trim());
    	if(annotationClass == null || annotationClass.get()==null) {
    		synchronized(classMap) {
    			annotationClass = classMap.get(className.trim());
    	    	if(annotationClass == null || annotationClass.get()==null) {
    	    		@SuppressWarnings("unchecked")
					Class<? extends Annotation> clazz = (Class<? extends Annotation>)Class.forName(className); 
    	    		annotationClass = new WeakReference<Class<? extends Annotation>>(clazz); 
    	    		classMap.put(className.trim(), annotationClass);
    	    	}
    		}
    	}
    	return annotationClass.get();
    }
    
	/**
	 * Returns a formatted rendering of the passed annotation
	 * @param annotation The annotation to print
	 * @param full true to print fully qualified class and enum names, false for simple names
	 * @return a string representing the structure of the passed annotation
	 */
	public static String print(final Annotation annotation, final boolean full) {
		if(annotation==null) return null;
		final StringBuilder b = new StringBuilder();
		final Class<?> annType = annotation.annotationType();
		b.append("public @interface ").append(getName(annType, full)).append(" {");
		for(Method m: annType.getDeclaredMethods()) {
			Class<?> mtype = m.getReturnType();
			String mname = m.getName();
			
		}
		return b.toString();
	}
	
	/**
	 * Renders the value of an annotation member
	 * @param value The value
	 * @param declaredType The defined type in the annotation
	 * @param full true to print fully qualified class and enum names, false for simple names
	 * @return the rendered string
	 */
	public static String renderValue(final Object value, final Class<?> declaredType, final boolean full) {
		if(value==null) return "<null>";		
		if(!declaredType.isArray()) {
			if(PTYPES.contains(declaredType)) return value.toString();
			if(Class.class.equals(declaredType)) return getName(declaredType, full);
			if(Enum.class.isAssignableFrom(declaredType)) {
				Enum e = (Enum)value;
				return getName(e.getDeclaringClass(), full) + "." + e.name();
			} else {
				return "<unknown type:" + declaredType.getName() + ">";
			}
		} else {
			int dim = ArrayUtils.getArrayDimension(value);
			
			return Arrays.deepToString((Object[]) value);
		}
	}
	
	/**
	 * Returns the value of an annotation attribute
	 * @param annotation The annotation instance
	 * @param method The annotation member method
	 * @return the value 
	 */
	public static Object getValue(final Annotation annotation, final Method method) {
		try {
			return method.invoke(annotation);
		} catch (Exception ex) {
			return "<reflection error>";
		}
	}
	
	/**
	 * Renders the class name as specified 
	 * @param clazz The class to return the name of
	 * @param full true for fully qualfied name, false for simple name
	 * @return the class name
	 */
	public static String getName(final Class<?> clazz, final boolean full) {
		if(clazz==null) return null;
		if(clazz.isPrimitive()) return clazz.getName();
		return full ? clazz.getName() : clazz.getSimpleName();
	}
	
	
	private AnnotationHelper() {}

}
