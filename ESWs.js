//var wsurl = "ws://pdk-pt-cetsd-01:9400/websocket";
var wsurl = "ws://localhost:9400/websocket";
var wsSub = new WebSocket(wsurl);
var subInterval = -1;
wsSub.onclose = function(evt) {
	if(evt.wasClean) console.info("WebSocket Closed")
	else console.error("WebSocket Closed: [%s]", evt.reason);
}
wsSub.onopen = function(evt) {
	console.info("WebSocket Connected")	
	subInterval = setInterval(function() {
		if(wsSub.bufferedAmount == 0) {
			console.debug("Starting SubGo");
			subgo();
		}
	}, 100);
}
wsSub.onmessage = function(messageEvent) {
	console.group("WebSocket Message:")
	console.info("%s", messageEvent.data);
	console.groupEnd();
	
}

function subgo() {
	console.info("Canceling Interval %s", subInterval);
	clearInterval(subInterval);
	wsSub.send('{"type" : "subscribe", "data" : {"topic": "mytopic", "subscriber" : "nicholas"}}');
}