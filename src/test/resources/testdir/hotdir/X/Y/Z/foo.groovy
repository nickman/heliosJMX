/* groovy.errors.tolerance=0, groovy.output.verbose=true, groovy.output.debug=true */ 
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])
@Inject(injectionType=com.heliosapm.script.annotations.InjectionType.FIXTURE, name="JMXTunnel_JMXConnector")
jmxConnector;
try {
	if(jmxConnector!=null) println "Injected jmxConnector: $jmxConnector  -->  ${jmxConnector.get(['HOST' : 'localhost', 'PORT' : 8006, 'KEYPHR' : 'helios'])}   -  ${System.identityHashCode(jmxConnector)}";
	else println "jmxConnector is still null";
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