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