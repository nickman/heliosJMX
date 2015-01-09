var DEFAULT_DELIM = "/";
function cached(key, def) {
	return stateService.get(key, def);
}
function cache(key, value) {
	return stateService.put(key, value);
}
function elapsed(ts) {
	return stateService.put(key, value);
}
function objectName(s) {
	return com.heliosapm.jmx.util.helpers.JMXHelper.objectName(s.toString());
}

function sla(s, d) {
    if(s==null) return null;
    var arr = s.split(d ? d : DEFAULT_DELIM);
    return arr[arr.length-1];
}
function sfi(s, d) {
    if(s==null) return null;
    var arr = s.split(d ? d : DEFAULT_DELIM);
    return arr[0];
}    
