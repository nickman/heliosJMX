//var wsurl = "ws://pdk-pt-cetsd-01:9400/websocket";
var wsurl = "ws://localhost:9400/websocket";
var ws = new WebSocket(wsurl);
ws.onclose = function(evt) {
	if(evt.wasClean) console.info("WebSocket Closed")
	else console.error("WebSocket Closed: [%s]", evt.reason);
}
ws.onopen = function(evt) {
	console.info("WebSocket Connected")	
	setInterval(function() {
		if(ws.bufferedAmount == 0) {
			go();
		}
	}, 100);
}
ws.onmessage = function(messageEvent) {
	console.group("WebSocket Message:")
	console.info("%s", messageEvent.data);
	console.groupEnd();
	ws.close();
}

function go() {
	ws.send('{"type" : "subscribe", "data" : {"topic": "mytopic", "subscriber" : "nicholas"}}');
}