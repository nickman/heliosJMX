// groovy.errors.tolerance=0 
tmpl = new File('/tmp').listFiles().length 
//return new File('/tmp').listFiles().length *2
println "Temp Length: $tmpl"
return tmpl;
