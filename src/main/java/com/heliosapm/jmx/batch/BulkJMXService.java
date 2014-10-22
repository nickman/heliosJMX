
package com.heliosapm.jmx.batch;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.sql.DataSource;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;
import com.heliosapm.jmx.batch.aggregate.AggregateFunction;
import com.heliosapm.jmx.util.helpers.JMXHelper;


/**
 * <p>Title: BulkJMXService</p>
 * <p>Description: Service to execute bulk JMX operation and attribute retrievals.</p> 
 * <p>Company: ICE</p>
 * @author Whitehead 
 * <p><code>com.heliosapm.jmx.batch.aggregate.BulkJMXService</code></p>
 */
public class BulkJMXService implements BulkJMXServiceMBean, MBeanRegistration {
	/** The BulkJMXService singleton instance */
	private static volatile BulkJMXService instance = null;
	/** The BulkJMXService singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** This MBean's ObjectName */
	protected final ObjectName myObjectName = JMXHelper.objectName("com.heliosapm.jmx:service=BulkJMXService");
	/** The MBeanServer this service is registered in */
	protected MBeanServer server = null;
	/** Indicates if registration has started on this instance */
	protected boolean registered = false;
	/** Instance logger */
	protected SLogger log = SimpleLogger.logger(getClass());
	
	/** The script engine manager */
	protected final ScriptEngineManager sem = new ScriptEngineManager();
	/** The script engine instance */
	protected final ScriptEngine se = sem.getEngineByExtension("js");
	
	/** The cache of compiled scripts keyed by the script name */
	protected final Map<String, CompiledScriptImpl> compiledScripts = new ConcurrentHashMap<String, CompiledScriptImpl>();

	
	
	/**
	 * Acquires the BulkJMXService singleton instance
	 * @return the BulkJMXService singleton instance
	 */
	public static BulkJMXService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new BulkJMXService();					
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new BulkJMXService
	 */
	BulkJMXService() {
		JMXHelper.registerMBean(myObjectName, this);
		log.log("Registered BulkJMXService MBean");
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#script(java.lang.String, javax.management.ObjectName, java.lang.Object[])
	 */
	@Override
	public Object script(String source, ObjectName classLoader, Object[] args) {
		final ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			
			if(classLoader!=null) {
				ClassLoader cl = server.isInstanceOf(classLoader, ClassLoader.class.getName()) ? server.getClassLoader(classLoader) : server.getClassLoaderFor(classLoader);
				Thread.currentThread().setContextClassLoader(cl);
			}
			if(args!=null && args.length!=0) {				
				return se.eval(source, newBindings(args));
			} else {
				return se.eval(source);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}
	
	/**
	 * Creates a new bindings instance for the passed arguments
	 * @param args The invocation arguments
	 * @return the new bindings
	 */
	protected Bindings newBindings(Object...args) {
		Bindings b = se.createBindings();
		b.put(ScriptEngine.ARGV, args);
		b.put("server", server);
		return b;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#script(java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object script(String source, Object[] args) {
		return script(source, null, args);
	}	
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#getAttributes(java.util.List, javax.management.ObjectName[])
	 */
	@Override
	public Map<ObjectName, Map<String, Object>> getAttributes(List<String> attributeNames, ObjectName ...objectNames) {
		Map<ObjectName, Map<String, Object>> map = null;
		Set<ObjectName> resolved = new HashSet<ObjectName>();
		for(ObjectName on: objectNames) {
			resolved.addAll(server.queryNames(on, null));
		}
		map = new HashMap<ObjectName, Map<String, Object>>(resolved.size());
		for(ObjectName on: resolved) { map.put(on, new HashMap<String, Object>()); }
		for(ObjectName on: resolved) {
			Map<String, Object> onMap = map.get(on);
			if(attributeNames.size()==1 && attributeNames.get(0).contains("*")) {
				Pattern p = Pattern.compile(attributeNames.get(0));
				attributeNames.clear();
				for(String attrName: JMXHelper.getAttributeNames(on, server)) {
					if(p.matcher(attrName).matches()) {
						attributeNames.add(attrName);
					}
				}
			}
			for(String attr: attributeNames) {
				try {
					Object value = server.getAttribute(on, attr);
					if(value instanceof Serializable) {
						onMap.put(attr, value);
					}
				} catch (Exception ex) {}
			}
		}
		return map;
	}
	
	/** A cache of data sources */
	private final Map<String, DataSource> dsCache = new ConcurrentHashMap<String, DataSource>();
	
	/**
	 * Acquires a connection from the data source at the specified jndi name
	 * @param jndiName The jndi name of the data source 
	 * @return a connection
	 */
	private Connection getConnection(final String jndiName) {
		Context ctx = null;
		try {
			DataSource ds = dsCache.get(jndiName);
			if(ds==null) {
				synchronized(dsCache) {
					ds = dsCache.get(jndiName);
					if(ds==null) {
						ctx = new InitialContext();
						ds = (DataSource)ctx.lookup(jndiName);
						dsCache.put(jndiName, ds);
					}
				}
			}
			return ds.getConnection();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get connection from datasource [" + jndiName + "]", ex);
		} finally {
			if(ctx!=null) try { ctx.close(); } catch (Exception x) {/* No Op */}
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#query(java.lang.String, java.lang.String)
	 */
	@Override
	public ResultSet query(final String jndiName, final String sqlText) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rset = null;
		return null;
//		try {
//			conn = getConnection(jndiName);
//			ps = conn.prepareStatement(sqlText);
//			rset = ps.executeQuery();
//			OracleCachedRowSet orset = new OracleCachedRowSet();
//			orset.populate(rset);
//			return orset;
//		} catch (Exception ex) {
//			throw new RuntimeException("Query failed for [" + jndiName + "/" + sqlText + "]", ex);
//		} finally {
//			if(rset!=null) try { rset.close(); } catch (Exception x) {/* No Op */}
//			if(ps!=null) try { ps.close(); } catch (Exception x) {/* No Op */}
//			if(conn!=null) try { conn.close(); } catch (Exception x) {/* No Op */}
//		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#invoke(javax.management.ObjectName[], java.lang.String, java.lang.Object[], java.lang.String[])
	 */
	@Override
	public Map<ObjectName, Object> invoke(ObjectName objectNames[], String opName, Object[] params, String[] signature) {
		Map<ObjectName, Object> map = null;
		Set<ObjectName> resolved = new HashSet<ObjectName>();
		for(ObjectName on: objectNames) {
			resolved.addAll(server.queryNames(on, null));
		}
		map = new HashMap<ObjectName, Object>(resolved.size());
		for(ObjectName on: resolved) {
			try {
				Object value = server.invoke(on, opName, params, signature);
				if(value instanceof Serializable) {
					map.put(on, value);
				}				
			} catch (Exception ex) {}
		}
		return map;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#aggregate(javax.management.ObjectName, javax.management.QueryExp, java.lang.String, java.lang.String)
	 */
	@Override
	public Object aggregate(ObjectName pattern, QueryExp query, String attribute, String aggregateFunction) {
		AggregateFunction af = AggregateFunction.forName(aggregateFunction);
		Set<ObjectName> resolved = server.queryNames(pattern, query);
		Map<ObjectName, Object> map = new HashMap<ObjectName, Object>(resolved.size());
		for(ObjectName on: resolved) {
			try {
				map.put(on, server.getAttribute(on, attribute));
			} catch (Exception ex) {
				/* No Op */
			}
		}
		return af.aggregate(new ArrayList<Object>(map.values()));
	}
	
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#aggregate(javax.management.ObjectName, java.lang.String, java.lang.String)
	 */
	@Override
	public Object aggregate(ObjectName pattern, String attribute, String aggregateFunction) {
		return aggregate(pattern, null, attribute, aggregateFunction);
	}	

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#preRegister(javax.management.MBeanServer, javax.management.ObjectName)
	 */
	@Override
	public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
		this.server = server;
		se.getBindings(ScriptContext.ENGINE_SCOPE).put("server", server);
		return myObjectName;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#postRegister(java.lang.Boolean)
	 */
	@Override
	public void postRegister(Boolean registrationDone) {
		/* No Op */		
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#preDeregister()
	 */
	@Override
	public void preDeregister() throws Exception {
		/* No Op */		
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.MBeanRegistration#postDeregister()
	 */
	@Override
	public void postDeregister() {
		/* No Op */		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#cscript(java.lang.String, java.lang.String, javax.management.ObjectName, java.lang.Object[])
	 */
	@Override
	public Object cscript(String name, String source, ObjectName classLoader, Object[] args) {
		CompiledScript cs = compiledScripts.get(name);
		if(cs==null) {
			synchronized(compiledScripts) {
				if(cs==null) {
					try {
						cs = (CompiledScript) se.eval(source);
						CompiledScriptImpl csi = new CompiledScriptImpl(name, cs, classLoader);
						compiledScripts.put(name, csi);
						cs = csi;
					} catch (Exception e) {
						throw new RuntimeException("Failed to compile named script [" + name + "]", e);
					}
				}
			}
		}
		try {
			if(args!=null && args.length!=0) {				
				return cs.eval(newBindings(args));
			} else {
				return cs.eval();
			}			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to execute named script [" + name + "]", ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#cscript(java.lang.String, java.lang.String, java.lang.Object[])
	 */
	@Override
	public Object cscript(String name, String source, Object[] args) {
		return cscript(name, source, null, args);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#printCompiledCache()
	 */
	@Override
	public String printCompiledCache() {
		return compiledScripts.keySet().toString().replace("[", "").replace("]", "");
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#flushCompiledCache()
	 */
	@Override
	public void flushCompiledCache() {
		compiledScripts.clear();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#getCompiledCacheSize()
	 */
	@Override
	public int getCompiledCacheSize() {
		return compiledScripts.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.jmx.batch.BulkJMXServiceMBean#isCompiledCached(java.lang.String)
	 */
	@Override
	public boolean isCompiledCached(String name) {
		if(name==null) return false;
		return compiledScripts.containsKey(name);
	}
	
	/**
	 * Returns the classloader for the passed ObjectName
	 * @param on The ObjectName to get the classloader for
	 * @return a classloader or null if the passed ObjectName was null or not registered.
	 */
	ClassLoader getClassLoader(ObjectName on) {
		if(on==null || !server.isRegistered(on));
		ClassLoader cl = null;
		try {
			if(server.isInstanceOf(on, ClassLoader.class.getName())) {
				return server.getClassLoader(on);
			} else {
				return server.getClassLoaderFor(on);
			}
		} catch (Exception x) { /* No Op */ }
		return cl;
	}


	/**
	 * <p>Title: CompiledScriptImpl</p>
	 * <p>Description: A cache value entry to hold the compiled script and optional associated class loader ObjectName</p> 
	 * <p>Company: ICE</p>
	 * @author Whitehead 
	 * <p><code>com.heliosapm.jmx.batch.aggregate.BulkJMXService.CompiledScriptImpl</code></p>
	 */
	public class CompiledScriptImpl extends CompiledScript {
		/** The compiled script */
		public final CompiledScript cs;
		/** The compiled script name */
		public final String name;
		/** The optional ObjectName of the classloader */
		public final ObjectName on;
		/** The ObjectName classloader */
		public final ClassLoader classLoader;
		
		/**
		 * Creates a new CompiledScriptImpl
		 * @param name The compiled script name
		 * @param cs The compiled script
		 * @param on The optional ObjectName of the classloader
		 */
		public CompiledScriptImpl(String name, CompiledScript cs, ObjectName on) {
			this.name = name;
			this.cs = cs;
			this.on = on;
			classLoader = getClassLoader(on);
		}

		/**
		 * {@inheritDoc}
		 * @see javax.script.CompiledScript#eval(javax.script.ScriptContext)
		 */
		@Override
		public Object eval(ScriptContext context) throws ScriptException {
			final ClassLoader cl = Thread.currentThread().getContextClassLoader();
			try {
				if(classLoader != null) {
					Thread.currentThread().setContextClassLoader(classLoader);
				}
				return cs.eval(context);
			} finally {
				Thread.currentThread().setContextClassLoader(cl);
			}
		}

		/**
		 * {@inheritDoc}
		 * @see javax.script.CompiledScript#getEngine()
		 */
		@Override
		public ScriptEngine getEngine() {
			return cs.getEngine();
		}

		/**
		 * {@inheritDoc}
		 * @see javax.script.CompiledScript#eval(javax.script.Bindings)
		 */
		public Object eval(Bindings bindings) throws ScriptException {
			final ClassLoader cl = Thread.currentThread().getContextClassLoader();
			try {
				if(classLoader != null) {
					Thread.currentThread().setContextClassLoader(classLoader);
				}
				return cs.eval(bindings);
			} finally {
				Thread.currentThread().setContextClassLoader(cl);
			}
		}

		/**
		 * {@inheritDoc}
		 * @see javax.script.CompiledScript#eval()
		 */
		public Object eval() throws ScriptException {
			final ClassLoader cl = Thread.currentThread().getContextClassLoader();
			try {
				if(classLoader != null) {
					Thread.currentThread().setContextClassLoader(classLoader);
				}
				return cs.eval();
			} finally {
				Thread.currentThread().setContextClassLoader(cl);
			}									
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return String.format("Compiled JS Script [%s]", name);
		}
		
		
	}


	
	



}
