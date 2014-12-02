/* groovy.errors.tolerance=0 */
@Dependency(keys=["thost"])
def tmpl = new File('/tmp').listFiles().length 
//return new File('/tmp').listFiles().length *2
println "Temp Length: $tmpl"
println "THost: $thost";
return tmpl;
