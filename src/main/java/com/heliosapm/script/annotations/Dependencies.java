/**
 * 
 */
package com.heliosapm.script.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Title: Dependencies</p>
 * <p>Description: Annotation to specify multiple sets of names and types of dependencies required by a Deployment.</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.script.annotations.Dependencies</code></b>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Dependencies {
	/**
	 * An array of dependencies
	 */
	Dependency[] value() default {};
}
