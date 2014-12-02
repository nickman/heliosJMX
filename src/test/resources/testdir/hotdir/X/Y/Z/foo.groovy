/* groovy.errors.tolerance=0 */ 
import groovy.transform.Field;
@Scheduled("d5")
@Dependency(keys=["thost", "foobar"])

def tmpl = new File('/tmp').listFiles().length 
//return new File('/tmp').listFiles().length *2
println "Temp Length: $tmpl"
println "THost: $thost";
return tmpl;
