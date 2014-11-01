package com.heliosapm.jmx.expr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Title: Directives</p>
 * <p>Description: A collection of standard directive code providers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.jmx.expr.Directives</code></p>
 */

public class Directives {

	/** The directive matching expression for an ObjectNameKeyDirective */
	public static final Pattern KEY_EXPR = Pattern.compile("\\{key:(.*?)\\}");
	
	public static final Pattern SUBSCRIPT_EXPR = Pattern.compile("(.*?)\\((.*?)\\)");
	
	public static class ObjectNameKeyDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			Matcher m = KEY_EXPR.matcher(directive);
			m.matches();
			String arg = m.group(1);
			m = SUBSCRIPT_EXPR.matcher(arg);
			if(m.matches()) {
				// arg has a subscript
				arg = m.group(1);
				String sub = m.group(2);
				code.append("\n\tnBuff.append($3.getKeyProperty(\"").append(arg).append("\")").append(sub).append(");");
			} else {
				// no subscript
				code.append("\n\tnBuff.append($3.getKeyProperty(\"").append(arg).append("\");");
			}
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, KEY_EXPR);
		}
		
	}
	
	
	public static boolean patternMatch(final String directive, final Pattern expr) {
		if(directive==null || directive.trim().isEmpty()) throw new IllegalArgumentException("The passed directive was null or empty");
		if(expr==null) throw new IllegalArgumentException("The passed pattern was null");
		return expr.matcher(directive).matches();
	}
	
	// DirectiveCodeProvider
	//  public void generate(final String directive, final StringBuilder code);
	//  nBuff
	//  final String sourceId, Map<String, Object> attrValues, ObjectName objectName
	
	
	
	private Directives() {}

}
