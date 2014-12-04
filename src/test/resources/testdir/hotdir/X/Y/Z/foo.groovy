/* groovy.errors.tolerance=0 */ 
import groovy.transform.Field;
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])

tmpl = new File('/tmp').listFiles().length 
//return new File('/tmp').listFiles().length *2
//println "Temp Length: $tmpl"
println "THost: $thost";
r = new Random(System.currentTimeMillis());
long sleep = Math.abs(r.nextInt(10) * 1000);
println "Sleeping for $sleep ms...";
Thread.sleep(sleep);
return tmpl;
