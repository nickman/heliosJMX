/* groovy.errors.tolerance=0, groovy.output.verbose=true, groovy.output.debug=true */ 
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])
@InjectFixtureResult(name="JMXConnector", args=[
	@com.heliosapm.script.annotations.FixtureArg(name="${thost}", value="localhost"),
	@com.heliosapm.script.annotations.FixtureArg(name="port", value="8006") 
])
def connector = null;
@InjectFixture(name="JMXConnector")
@Field
connectorFactory = null; //com.heliosapm.script.fixtures.FixtureCache.getInstance().get("JMXConnector");
a = 2;
try {
	println "connectorFactory: $connectorFactory"; 
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