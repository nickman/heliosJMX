package com.heliosapm.jmx.expr;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.CompiledScript;

import com.heliosapm.jmx.util.helpers.StateService;

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
	/** The directive matching expression for an ObjectNameKeyValueDirective */
	public static final Pattern KEYVALUE_EXPR = Pattern.compile("\\{keyvalue:(.*?)\\}");

	/** The directive matching expression for a JavaScript eval  */
	public static final Pattern EVAL_EXPR = Pattern.compile("\\{eval:(?:d\\((.*?)\\):)?(.*)\\}");
	
	
	
	/** The directive matching expression for an AttributeDirective */
	public static final Pattern ATTR_EXPR = Pattern.compile("\\{attr:(.*?)\\}");
	
	/** The directive matching string for all ObjectName key/value pairs */
	public static final String ALL_KEYVALUES = "{allkeys}";
	/** The directive matching string for the ObjectName domain */
	public static final String DOMAIN = "{domain}";
	
	/** Serial number for compiled script keys */
	protected static final AtomicLong evalSerial = new AtomicLong();
	
	
	/** Directive subscript expression */
	public static final Pattern SUBSCRIPT_EXPR = Pattern.compile("(.*?)\\((.*?)\\)$");
	
	/** The default providers */
	public static final Set<DirectiveCodeProvider> PROVIDERS = Collections.unmodifiableSet(new HashSet<DirectiveCodeProvider>(Arrays.asList(
			new AllKeysDirective(),
			new AttributeDirective(),
			new DomainDirective(),
			new ObjectNameKeyDirective(),
			new ObjectNameKeyValueDirective(),
			new EvalDirective()
	)));	
	
	
	/**
	 * <p>Title: EvalDirective</p>
	 * <p>Description: Directive processor that exeecutes the embedded JavaScript in the directive</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.EvalDirective</code></b>
	 */
	public static class EvalDirective implements DirectiveCodeProvider {
		/** The state service which compiles and caches the JS fragments */
		static final StateService state = StateService.getInstance();
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			Matcher m = EVAL_EXPR.matcher(directive);
			m.matches();
			final String defaultValue = m.group(1);
			final String sourceCode = m.group(2);
			final String evalKey = "eval" + evalSerial.incrementAndGet();
			CompiledScript cs = state.getCompiledScript(sourceCode);
			state.put(evalKey, cs);
			code.append("\n\tBindings b = StateService.getInstance().getBindings(\"").append(evalKey).append("\");");
			code.append("\n\tb.put(\"sourceId\", $1);");
			code.append("\n\tb.put(\"attrValues\", $2);");
			code.append("\n\tb.put(\"objectName\", $3);");
			code.append("\n\tb.put(\"exResult\", $4);");
			code.append("\n\tObject cs = StateService.getInstance().get(\"").append(evalKey).append("\");");
			if(defaultValue!=null && !defaultValue.trim().isEmpty()) {
				code.append("\n\tnBuff.append(invokeEval(cs, b, \"").append(defaultValue).append("\"));");
			} else {
				code.append("\n\tnBuff.append(invokeEval(cs, b));");
			}
		}
		
		
		// DirectiveCodeProvider
		//  public void generate(final String directive, final StringBuilder code);
		//  nBuff
		//  final String sourceId, Map<String, Object> attrValues, ObjectName objectName
		
		public boolean match(final String directive) {
			return patternMatch(directive, EVAL_EXPR);
		}		
	}
	
	
	
	/**
	 * <p>Title: ObjectNameKeyValueDirective</p>
	 * <p>Description: Directive processor that acquires an ObjectName key and value</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.ObjectNameKeyValueDirective</code></b>
	 */
	public static class ObjectNameKeyValueDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			Matcher m = KEY_EXPR.matcher(directive);
			m.matches();
			String arg = m.group(1);
			m = SUBSCRIPT_EXPR.matcher(arg);
			if(m.matches()) {
				String key = m.group(1);
				String sub = m.group(2);
				code.append(String.format("\n\tnBuff.append(\"%s\").append(\"=\").append($3.getKeyProperty(\"%s\")%s).append(\",\");", key, key, sub));
			} else {
				code.append(String.format("\n\tnBuff.append(\"%s\").append(\"=\").append($3.getKeyProperty(\"%s\")).append(\",\");", arg, arg));
			}
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, KEYVALUE_EXPR);
		}		
	}

	
	
	/**
	 * <p>Title: ObjectNameKeyDirective</p>
	 * <p>Description: Directive processor that acquires an ObjectName keyed value</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.ObjectNameKeyDirective</code></b>
	 */
	public static class ObjectNameKeyDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			Matcher m = KEY_EXPR.matcher(directive);
			m.matches();
			String arg = m.group(1);
			m = SUBSCRIPT_EXPR.matcher(arg);
			if(m.matches()) {
				arg = m.group(1);
				String sub = m.group(2);
				code.append("\n\tnBuff.append($3.getKeyProperty(\"").append(arg).append("\")").append(sub).append(");");
			} else {
				code.append("\n\tnBuff.append($3.getKeyProperty(\"").append(arg).append("\"));");
			}
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, KEY_EXPR);
		}		
	}
	
	
	
	/**
	 * <p>Title: AttributeDirective</p>
	 * <p>Description: Directive processor that acquires an ObjectName key and value</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.AttributeDirective</code></b>
	 */
	public static class AttributeDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			Matcher m = ATTR_EXPR.matcher(directive);
			m.matches();
			String arg = m.group(1);
			m = SUBSCRIPT_EXPR.matcher(arg);
			if(m.matches()) {
				arg = m.group(1);
				String sub = m.group(2);
				code.append("\n\tnBuff.append($2.get(\"").append(arg).append("\")").append(sub).append(");");
			} else {
				code.append("\n\tnBuff.append($2.get(\"").append(arg).append("\"));");
			}
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, ATTR_EXPR);
		}		
	}
	
	
	/**
	 * <p>Title: AllKeysDirective</p>
	 * <p>Description: Directive processor that acquires all ObjectName key/value pairs</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.AllKeysDirective</code></b>
	 */
	public static class AllKeysDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			code.append("\n\tnBuff.append($3.getKeyPropertyListString());");
		}
		
		public boolean match(final String directive) {
			return ALL_KEYVALUES.equals(directive);
		}
		
	}
	
	
	/**
	 * <p>Title: DomainDirective</p>
	 * <p>Description: Directive processor that acquires the ObjectName domain</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.DomainDirective</code></b>
	 */
	public static class DomainDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final StringBuilder code) {
			code.append("\n\tnBuff.append($3.getDomain());");
		}
		
		public boolean match(final String directive) {
			return DOMAIN.equals(directive);
		}		
	}

	
	
	
	/**
	 * Determines if the passed directive pattern matches with the passed pattern
	 * @param directive The directive to test
	 * @param expr The expression to match
	 * @return true for a match, false otherwise
	 */
	public static boolean patternMatch(final String directive, final Pattern expr) {
		if(directive==null || directive.trim().isEmpty()) throw new IllegalArgumentException("The passed directive was null or empty");
		if(expr==null) throw new IllegalArgumentException("The passed pattern was null");
		return expr.matcher(directive).matches();
	}
	
	private Directives() {}

}
