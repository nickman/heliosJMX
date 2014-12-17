import ch.ethz.ssh2.*;

@GrabResolver(name='ice', root='https://maven.intcx.net/')
@Grab(group='ch.ethz.ganymed', module='ganymed-ssh2', version='262')



class SSHTunnel {
	// ================  Statics ================
	static final String userName = System.getProperty('user.name');
	static final String userHome = System.getProperty('user.home');
	static final String sshDir = "$userHome\\.ssh";
	static final String knownHosts = "$sshDir\\known_hosts";
	static final String myCert = "$sshDir\\id_dsa";
	static final KnownHosts kh = new KnownHosts(new File(knownHosts));
	static final ServerHostKeyVerifier verifier = [
        verifyServerHostKey : { hostname, port, serverHostKeyAlgorithm, serverHostKey ->
            println "Verifying $hostname HostKey [${KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)}] with [$knownHosts] using algo [$serverHostKeyAlgorithm]";
            return 0 == kh.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey);
        }
    ] as ServerHostKeyVerifier;
  // ================  Instance Final ================
  final Map<Integer, LocalPortForwarder> portForwards = new HashMap<Integer, LocalPortForwarder>();
	// ================  Instance ================	
	String jumpHost = null;
	Connection conn = null;
	ConnectionInfo connectionInfo = null;
	boolean authed = false;
	
	
	public SSHTunnel(String jumpHost) {		
		this.jumpHost = jumpHost;
		conn = new Connection(jumpHost);
		connectionInfo = conn.connect(verifier, 5000, 5000);
		println "Connected to $jumpHost";
		authed = conn.authenticateWithPublicKey(userName, new File(myCert).getText().toCharArray() , null);
    if(!authed) {
        throw new Exception("Public Key Auth Failed");
    }
    println "Authenticated.";
	}
	
	public int portForward(String host, int remotePort, int localPort) {
		LocalPortForwarder lpf = conn.createLocalPortForwarder(localPort < 1 ? 0 : localPort, host, remotePort);
		int actualLocalPort = lpf.getLocalSocketAddress().getPort();
		portForwards.put(actualLocalPort, lpf);
		return actualLocalPort;
	}
	
	public void close() {
		String me = toString();
		portForwards.each() { key, lpf ->
			try { lpf.close(); } catch (e) {}
		}
		portForwards.clear();
		conn.close();
		conn = null;
		println "CLOSED >>>>>\n  $me\n <<<<<";
	}
	
	public String toString() {
			StringBuilder b = new StringBuilder("SSHTunnel : $jumpHost\n\tLocalPortForwards:");
			portForwards.each() { key, lpf -> 
				b.append("\n\t\tlocalhost:$key   -->  ${lpf.host_to_connect}:${lpf.port_to_connect}");
			}
			return b.toString();
	}
	
}

SSHTunnel tunnel = null;
try {
	tunnel = new SSHTunnel("ord-pr-ceas-a01");
	tunnel.portForward("10.6.202.113", 1430, 1430);
	println tunnel;
	Thread.currentThread().join();
} catch (e) {
	e.printStackTrace(System.err);
} finally {
	if(tunnel!=null) try { tunnel.close(); } catch (e) {}
}

