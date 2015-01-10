/* groovy.errors.tolerance=0, groovy.output.verbose=true, groovy.output.debug=true */ 
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])
@Inject(injectionType=com.heliosapm.script.annotations.InjectionType.FIXTURE, name="JMXTunnel_JMXConnector")
jmxConnector;
/*
@SSHShell([
	@com.heliosapm.script.annotations.SSHOptionValue(
		value=com.heliosapm.jmx.remote.tunnel.SSHOption.HOST,
		optValue="localhost"
	),
	@com.heliosapm.script.annotations.SSHOptionValue(
		value=com.heliosapm.jmx.remote.tunnel.SSHOption.KEYPHR,
		optValue="helios"
	)
])
*/
//SSH_CommandTerminal
@Inject(injectionType=com.heliosapm.script.annotations.InjectionType.FIXTURE, name="SSH_CommandTerminal")
shellFactory;
@Field
jmxConn = null;

@Field
shell = null;
mbs = null;
try {
	if(jmxConnector!=null) {
		if(jmxConn==null) {
			jmxConn = jmxConnector.get(['HOST' : 'localhost', 'PORT' : 8006, 'KEYPHR' : 'helios', 'SVRKEY' : false]);			

		}
		mbs = jmxConn.getMBeanServerConnection();
		//println "JMXConn: ${mbs.getAttribute(MBeanServerDelegate.DELEGATE_NAME, 'MBeanServerId')}\n\t --------> ${System.identityHashCode(mbs)}"
	}
	else println "jmxConnector is still null";
	//println "======================";

	if(shellFactory!=null) {
		if(shell==null) {
			shell = shellFactory.get(['HOST' : 'localhost', 'KEYPHR' : 'helios', 'SVRKEY' : false]);
		}
		//println "Injected shellFactory: $shellFactory  -->  ${shell}   -  ${System.identityHashCode(shellFactory)}";
		//println "UPTIME: ${shell.exec('uptime')} \n\t --------> ${System.identityHashCode(shell)} WOOT !"

	} else println "shellFactory is still null";




	//println "connectorFactory: $connectorFactory"; 
	tmpDir = System.getProperty("java.io.tmpdir");
	//println "TMP: ${tmpDir}";
	tmpl = new File(tmpDir).listFiles().length 	
	//println "Temp [${tmpDir}] Length: $tmpl"
	//println "THost: $thost";
	r = new Random(System.currentTimeMillis());
	long sleep = Math.abs(r.nextInt(10) * 100);
	if(sleep>0) {
		//println "Sleeping for $sleep ms...";
	}
	Thread.sleep(sleep);
	return tmpl;
} catch (e) {
	e.printStackTrace(System.err);
	throw e;
}