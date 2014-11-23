/**
 * 
 */
package com.heliosapm.jmx.config;

import java.util.Map;

/**
 * <p>Title: DependencyProvider</p>
 * <p>Description: Defines a dependency provider for use when annotations are not supported</p>
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><b><code>com.heliosapm.jmx.config.DependencyProvider</code></b>
 */

public interface DependencyProvider {
	/**
	 * Returns a map of dependencies where the key is the configuration key
	 * and the class is the expected type of the value
	 * @return a dependency map
	 */
	public Map<String, Class<?>> getDependencies();
}
