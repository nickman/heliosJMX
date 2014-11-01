import java.util.regex.*;
import java.lang.management.*;
import javax.management.*;

constants = new HashSet<String>(Arrays.asList("domain"));
prefixes = new HashSet<String>(Arrays.asList("key", "attr"));
gcs = ManagementFactory.getGarbageCollectorMXBeans();
Pattern EXPR_PATTERN = Pattern.compile('(.*?)::(.*?)');
Pattern TOKEN_PATTERN = Pattern.compile('\\{(.*?)(?::(.*?))?\\}');



nameSegments = [
    "java.lang.gc::{key:type}{key:name}",
    "   java.lang.gc   :: {key:type}{key:name}  ",
    "java.lang.gc.{attr:Foo}.cominatcha::{key:type}{key:name}",
    "{domain}.gc.{attr:Foo}::{key:type}{key:name}",
    "{domain}.gc.{attr:Foo}::{key:type}{attr:A/B/C}"
]


nameSegments.each() { seg ->
    seg.replace(" ", "").split("::").each() { expr ->
        expr = expr.trim();
        b = new StringBuilder();
        println "Processing [$expr]";
        m = TOKEN_PATTERN.matcher(expr);
        int tokens = 0;
        int lstart = 0;
        literals = [];
        while(m.find()) {
            tokens++;
            mst = m.start();
            est = m.end();
            println "\tstart:$mst, end:$est";
            if(mst>lstart) {
                literals.add([lstart, mst] as int[]);                
            }
            literals.add(m.group());
            lstart = est;
        }
        literals.add([lstart, expr.length()] as int[]);
        if(tokens==0) {
            println "---------------------------->Result: [$expr]";
        } else {
            //println "---------------------------->Result: [$sb]";
            //if(!literals.isEmpty()) println "Literals: $literals";
            literals.each() {
                if(it instanceof CharSequence) {
                    b.append(it);
                } else {
                    b.append(expr.substring(it[0], it[1]));
                }
            }
            println "---------------------------->Result: [$b]";
        }
    }
    
}



return null;
