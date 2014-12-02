/**
 * 
 */
package com.heliosapm.jmx.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * <p>Title: Dependency</p>
 * <p>Description: Annotation to specify the names and types of dependencies required by a Deployment.</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.jmx.config.Dependency</code></b>
 */

@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public @interface Dependency {
	/**
	 * An array of configuration keys representing config values that are depended on.
	 * It is assumed that all keys defined in one shot have the same type as defined in {@link #type()}
	 */
	String[] keys();
	/**
	 * The type of the values keyed by the keys defined in {@link #keys()}
	 */
	Class<?> type() default Object.class;
}
