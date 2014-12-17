package com.heliosapm.jmx.expr;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.CompiledScript;

import com.heliosapm.jmx.util.helpers.CacheService;
import com.heliosapm.script.StateService;

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
	public static final Pattern EVAL_EXPR = Pattern.compile("\\{eval(.*?):(?:d\\((.*?)\\):)?(.*)\\}");
	
	/** The directive matching expression for an array of objects (literals)  */
	public static final Pattern ARR_EXPR = Pattern.compile("\\[(.*?)\\]");
	
	
	/** The directive matching expression for an AttributeDirective */
	public static final Pattern ATTR_EXPR = Pattern.compile("\\{attr:(.*?)\\}");
	/** Comma separated values splitter */
	public static final Pattern CSV_SPLIT = Pattern.compile(",");
	
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
			new EvalDirective(),
			new ArrayDirective()
	)));	
	
//	Directives:
//		===========
//		elapsed
//		elapsed(time)
//		delta(value)
//		rdelta(value)

	/** The directive matching expression for an ElapsedTimeDirective */
	public static final Pattern ELAPSED_EXPR = Pattern.compile("\\{elapsed(?:\\((.*?)\\))?\\}");


	/**
	 * <p>Title: ElapsedTimeDirective</p>
	 * <p>Description: Directive processor that computes the elapsed time since the prior call for the same sourceId and key, or the passed timestamp.</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.ElapsedTimeDirective</code></b>
	 */
	public static class ElapsedTimeDirective implements DirectiveCodeProvider {
		/** The cache service to store elapsed time starters */
		final CacheService cache = CacheService.getInstance();

		@Override
		public void generate(final String directive, final CodeBuilder code) {
			Matcher m = ELAPSED_EXPR.matcher(directive);
			m.matches();
			final String ext = m.group(1);
			
			code.append("\n\tb.put(\"sourceId\", $1);");
			code.append("\n\tb.put(\"attrValues\", $2);");
			code.append("\n\tb.put(\"objectName\", $3);");
			code.append("\n\tb.put(\"exResult\", er);");
			code.append("\n\tlong cs = StateService.getInstance().get(\"").append(evalKey).append("\");");
			if(defaultValue!=null && !defaultValue.trim().isEmpty()) {
				code.append("\n\tnBuff.append(invokeEval(cs, b, \"").append(defaultValue).append("\"));");
			} else {
				code.append("\n\tnBuff.append(invokeEval(cs, b));");
			}			
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, ELAPSED_EXPR);
		}		
		
	}
	
	
	/**
	 * <p>Title: EvalDirective</p>
	 * <p>Description: Directive processor that executes the embedded JavaScript in the directive</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><b><code>com.heliosapm.jmx.expr.Directives.EvalDirective</code></b>
	 */
	public static class EvalDirective implements DirectiveCodeProvider {
		/** The state service which compiles and caches the JS fragments */
		final StateService state = StateService.getInstance();
		
		@Override
		public void generate(final String directive, final CodeBuilder code) {
			Matcher m = EVAL_EXPR.matcher(directive);
			m.matches();
			final String ext = m.group(1);
			final String extension = (ext!=null && !ext.trim().isEmpty()) ? ext.trim().toLowerCase() : "js";
			final String defaultValue = m.group(2);
			final String sourceCode = m.group(3);
			final String evalKey = "eval" + evalSerial.incrementAndGet();
			CompiledScript cs = state.getCompiledScript(extension, sourceCode);
			state.put(evalKey, cs);
			code.append("\n\tBindings b = StateService.getInstance().getBindings(\"").append(evalKey).append("\");");
			code.append("\n\tb.put(\"sourceId\", $1);");
			code.append("\n\tb.put(\"attrValues\", $2);");
			code.append("\n\tb.put(\"objectName\", $3);");
			code.append("\n\tb.put(\"exResult\", er);");
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
		public void generate(final String directive, final CodeBuilder code) {
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
		public void generate(final String directive, final CodeBuilder code) {
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
		public void generate(final String directive, final CodeBuilder code) {
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
		public void generate(final String directive, final CodeBuilder code) {
			code.append("\n\tnBuff.append($3.getKeyPropertyListString());");
		}
		
		public boolean match(final String directive) {
			return ALL_KEYVALUES.equals(directive);
		}
		
	}
	
	/**
	 * <p>Title: ArrayDirective</p>
	 * <p>Description: Directive processor that creates an array of objects
	 * from an expression such as <pre>['Foo', 'Bar', 37]</pre>
	 * </p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.expr.Directives.ArrayDirective</code></p>
	 */
	public static class ArrayDirective implements DirectiveCodeProvider {
		
		@Override
		public void generate(final String directive, final CodeBuilder code) {
			final Set<String> values = new LinkedHashSet<String>();
			Matcher m = ARR_EXPR.matcher(directive);
			m.matches();
			String csv = m.group(1);
			String[] parsedValues = CSV_SPLIT.split(csv);
			for(String s: parsedValues) {
				s = s.trim().replace("\"", "").replace("'", "");
				if(isNumber(s)) {
					values.add(s);
				} else {
					values.add("\"" + s + "\"");
				}
				
			}
			code.append("\n\t = new Object[]");
			code.append(values.toString().replace('[', '{').replace(']', '}'));
			code.append(";");
		}
		
		public boolean match(final String directive) {
			return patternMatch(directive, ARR_EXPR);
		}
		
	}
	
	
	public static boolean isNumber(final String s) {
		try {
			new Double(s);
			return true;
		} catch (Exception x) {
			return false;
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
		public void generate(final String directive, final CodeBuilder code) {
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
