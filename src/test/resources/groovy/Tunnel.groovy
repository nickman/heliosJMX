import ch.ethz.ssh2.*;

conn = null;
userName = System.getProperty('user.name');
userHome = System.getProperty('user.home');
sshDir = "$userHome\\.ssh";
knownHosts = "$sshDir\\known_hosts";
myCert = "$sshDir\\id_dsa";
println "User: $userName";
lpf1 = null;
lpf2 = null;
lpf3 = null;

try {
    conn = new Connection(jumpHost); 					// the host to jump through
    kh = new KnownHosts(new File(knownHosts));
    verifier = [
        verifyServerHostKey : { hostname, port, serverHostKeyAlgorithm, serverHostKey ->
            println "Verifying $hostname HostKey [${KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)}] with [$knownHosts] using algo [$serverHostKeyAlgorithm]";
            return 0 == kh.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey);
        }
    ] as ServerHostKeyVerifier;
    ci = conn.connect(verifier, 0, 0);
    println "Connected.";
    authed = conn.authenticateWithPublicKey(userName, new File(myCert).getText().toCharArray() , null);
    if(!authed) {
        throw new Exception("Public Key Auth Failed");
    }
    println "Authenticated.";
    lpf1 = conn.createLocalPortForwarder(lp1, host1, rp1);   // (<local port>, <host name>, <remote port>)
    lpf2 = conn.createLocalPortForwarder(lp2, host2, rp2);    
    lpf3 = conn.createLocalPortForwarder(lp3, host3, rp3);    
    println "Local Port Forwards Created";
    try {
        Thread.currentThread().join();
    } catch (e) {
        println "Close Signal Received...";
    }
} finally {
    if(lpf1!=null) try { lpf1.close();  println "Portforward lpf1 Closed"; } catch (e) {}
    if(lpf2!=null) try { lpf2.close();  println "Portforward lpf2 Closed"; } catch (e) {}
    if(lpf3!=null) try { lpf3.close();  println "Portforward lpf3 Closed"; } catch (e) {}
    if(conn!=null) try { conn.close();  println "Connection Closed"; } catch (e) {}
}
