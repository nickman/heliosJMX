/* groovy.errors.tolerance=0 */ 
import groovy.transform.Field;
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])

tmpl = new File('/tmp').listFiles().length 
//return new File('/tmp').listFiles().length *2
println "Temp Length: $tmpl"
println "THost: $thost";
r = new Random(System.currentTimeMillis());
Thread.sleep(Math.abs(r.nextInt(10)));
return tmpl;
