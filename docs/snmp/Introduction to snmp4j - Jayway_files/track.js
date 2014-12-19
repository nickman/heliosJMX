/* Debug */
if(typeof pe_debug === 'undefined')
	pe_debug = false;

/* declare namespace ProspectEye */
(function( ProspectEye, undefined ) {

	/* pe_data types */
	ProspectEye.kPEForm = 'F';

	/* append types */
	ProspectEye.kPEAppendScript = 21001;
	ProspectEye.kPEAppendFrame = 21002;

	/* tracker callers */
	ProspectEye.trackerCallers = {};

	ProspectEye.trackVisit = function() {
		ProspectEye.callTracker({}, ProspectEye.kPEAppendScript, true);
		if(!ProspectEye.inArray(ProspectEye.getSiteId(), ['36514ff45d','b8cbf98771'])) {  // handle customers that don't want this file included
			ProspectEye.addScriptWithAppendChild('track_includes.js', true);
		}
	};

	ProspectEye.callTracker = function(PEDataObject, nAppendType, bShouldAppend, callback) {
		if(PEDataObject === null) {
			PEDataObject = {};
		}

		var sUrl = null;
		if('url' in PEDataObject) {
			sUrl = PEDataObject.url;
		}
		else {
			sUrl = ProspectEye.getUrl();
		}

		var sPagename = null;
		if('pagename' in PEDataObject) {
			sPagename = PEDataObject.pagename;
		}
		else {
			sPagename = ProspectEye.getPagename();
		}

		var sSiteId = null;
		if('siteid' in PEDataObject) {
			sSiteId = PEDataObject.siteid;
		}
		else {
			sSiteId = ProspectEye.getSiteId();
		}

		var sReferer = null;
		if('referer' in PEDataObject) {
			sReferer = PEDataObject.referer;
		}
		else {
			sReferer = ProspectEye.getReferer();
		}

		var bCookieEnabled = null;
		if('cookie' in PEDataObject) {
			bCookieEnabled = PEDataObject.cookie;
		}
		else {
			bCookieEnabled = ProspectEye.getCookieIsEnabled();
		}

		var sPEData = null;
		if('pedata' in PEDataObject) {
			sPEData = ProspectEye.getPEData(PEDataObject.pedata);
		}

		var ps_src = ProspectEye.getTrackerQuery(sUrl,sPagename,sSiteId,sReferer,bCookieEnabled, sPEData);

		if(bShouldAppend && typeof callback == 'function') {
			// generate a uniqueId for server to to have to communicate with frontend
			var frontendUUID = ProspectEye.generateFrontendUUID();
			ps_src += '&frontendid=' + frontendUUID;

			ProspectEye.trackerCallers[frontendUUID] = false;
			ProspectEye.iterCallback(frontendUUID, callback);
		}

		var returnScript = null;
		if(nAppendType == ProspectEye.kPEAppendScript) {
			returnScript = ProspectEye.addScriptWithAppendChild(ps_src, bShouldAppend);
		}
		else if(nAppendType == ProspectEye.kPEAppendFrame) {
			returnScript = ProspectEye.addScriptWithIFrame(ps_src, bShouldAppend);
		}

		return returnScript;
	};

	ProspectEye.iterCallback = function(frontendUUID, callback, index) {
		if(!index) {
			index = 0;
		}

		if(index > 20 || ProspectEye.trackerCallers[frontendUUID] === true) {
			ProspectEye.trackerCallers[frontendUUID] = true;
			if(typeof callback == 'function') {
				callback();
			}
		}
		else {
			index += 1;
			setTimeout(function() {
				ProspectEye.iterCallback(frontendUUID, callback, index);
			},100);
		}
	}

	ProspectEye.generateFrontendUUID = function() {
		function s4() {
			return Math.floor((1 + Math.random()) * 0x10000).toString(16).substring(1);
		}

  		return s4()+s4()+s4();
	};

	ProspectEye.getTrackerQuery = function(sUrl, sPagename, sSiteId, sReferer, bCookieEnabled, sPEData) {
		var sQuery = '?url='+ProspectEye.escapeString(sUrl)+'&pagename='+ProspectEye.escapeString(sPagename)+'&id='+sSiteId+'&ref='+ProspectEye.escapeString(sReferer)+'&c='+ProspectEye.escapeString(bCookieEnabled);
		if(sPEData) {
			sQuery += '&pe_data='+sPEData;
		}
		return sQuery;
	};

	ProspectEye.getUrl = function() {
		return document.location.href;
	};

	ProspectEye.getPagename = function() {
		var pagename = '';
		if (document.title && document.title !== "") {
			pagename = document.title;
		}

		return pagename;
	};

	ProspectEye.getSiteId = function() {
		// psSite is declared in the user-script
		if(typeof psSite == 'undefined') {
			return '0';
		}
		return psSite;
	};

	ProspectEye.getReferer = function() {
		var ps_rtu = '';
		try {
			ps_rtu = top.document.referrer;
		}
		catch(e) {
			if (parent) {
				if (parent.ps_gR) {
					try {
						ps_rtu = parent.ps_gR;
					}
					catch(E3) {
						ps_rtu = '';
					}
				}
				else {
					try {
						ps_rtu = parent.document.referrer;
					}
					catch(E) {
						try {
							ps_rtu = document.referrer;
						} catch(E2) {
							ps_rtu = '';
						}
					}
				}
				try {
					parent.ps_gR = document.location.href;
				}
				catch(E){}
			}
			else {
				try {
					ps_rtu = document.referrer;
				}
				catch(E3) {
					ps_rtu = '';
				}
			}
		}

		return ps_rtu;
	};

	ProspectEye.getCookieIsEnabled = function() {
		return navigator.cookieEnabled;
	};

	ProspectEye.getPEData = function(peDataObj) {
		if(typeof peDataObj === 'string') {
			if(ProspectEye.inArray(peDataObj.charAt(0), ['M','A','B','D'])) {
				return peDataObj;
			}
		}
		else if(typeof peDataObj === 'object'){
			if('type' in peDataObj) {
				if(peDataObj.type == ProspectEye.kPEForm) {
					var sQuery = 'F';
					for(var key in peDataObj) {
						if(key != 'type') {
							var sKey = key;
							var sValue = peDataObj[key];
							var sPair = sKey + '=' + sValue;
							sQuery += ProspectEye.escapeString(sPair) + '|';
						}
					}

					sQuery = sQuery.replace(/\|$/, "");

					return sQuery;
				}
			}
		}
		return null;
	};

	ProspectEye.addScriptWithAppendChild = function(sScriptUrl, bAppend) {
		var script = document.createElement("script");
		script.type = "text/javascript";
		var peJsHost = ProspectEye.getProtocol();
		script.src = peJsHost + "tr.prospecteye.com/" + sScriptUrl;

		if(!pe_debug && bAppend) {
			if(ProspectEye.getSiteId() != '0') {
				document.getElementsByTagName("head")[0].appendChild(script);
			}
		}
		else {
			return script;
		}
	};

	ProspectEye.addScriptWithIFrame = function(sScriptUrl, bAppend) {
		var iframe = document.createElement("iframe");
		var peJsHost = ProspectEye.getProtocol();
		iframe.src = peJsHost + "tr.prospecteye.com/" + sScriptUrl;
		iframe.width = 1;
		iframe.height = 1;

		if(!pe_debug && bAppend) {
			if(ProspectEye.getSiteId() != '0') {
				document.getElementsByTagName("head")[0].appendChild(iframe);
			}
		}
		else {
			return iframe;
		}
	};

	ProspectEye.addScript = function(sScript, bAppend) {
		var script = document.createElement("script");
		script.text = sScript;

		if(!pe_debug && bAppend) {
			if(ProspectEye.getSiteId() != '0') {
				document.getElementsByTagName("head")[0].appendChild(script);
			}
		}
		else {
			return script;
		}
	};

	ProspectEye.getProtocol = function() {
		var peJsHost = (("https:" == document.location.protocol) ? "https://" : "http://");
		return peJsHost;
	};

	ProspectEye.escapeString = function(ps_str){
		if(typeof(encodeURIComponent) == 'function') {
			return encodeURIComponent(ps_str);
		}
		else {
			return escape(ps_str);
		}
	};

	ProspectEye.inArray = function(needle, haystack) {
		var length = haystack.length;
		for(var i = 0; i < length; i++) {
			if(haystack[i] === needle) return true;
		}
		return false;
	};

}( window.ProspectEye = window.ProspectEye || {}));

if(!pe_debug) {
	if(typeof psDynamicSite === 'undefined') {
		ProspectEye.trackVisit();
	}
	else {
		if(!psDynamicSite) {
			ProspectEye.trackVisit();
		}
	}
}

function pe_callTracker(PEDataObject, nAppendType, bShouldAppend) {
	if(typeof ProspectEye !== 'undefined') {
		return ProspectEye.callTracker(PEDataObject, nAppendType, bShouldAppend);
	}
	return null;
}