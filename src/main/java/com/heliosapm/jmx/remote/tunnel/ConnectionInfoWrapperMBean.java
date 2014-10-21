package com.heliosapm.jmx.remote.tunnel;

public interface ConnectionInfoWrapperMBean {

	/**
	 * The used key exchange (KEX) algorithm in the latest key exchange.
	 */
	public String getKeyExchangeAlgorithm();

	/**
	 * The currently used crypto algorithm for packets from to the client to the
	 * server.
	 */
	public String getClientToServerCryptoAlgorithm();

	/**
	 * The currently used crypto algorithm for packets from to the server to the
	 * client.
	 */
	public String getServerToClientCryptoAlgorithm();

	/**
	 * The currently used MAC algorithm for packets from to the client to the
	 * server.
	 */
	public String getClientToServerMACAlgorithm();

	/**
	 * The currently used MAC algorithm for packets from to the server to the
	 * client.
	 */
	public String getServerToClientMACAlgorithm();

	/**
	 * The type of the server host key (currently either "ssh-dss" or
	 * "ssh-rsa").
	 */
	public String getServerHostKeyAlgorithm();

	/**
	 * The server host key that was sent during the latest key exchange.
	 */
	public byte[] getServerHostKey();

	/**
	 * Number of kex exchanges performed on this connection so far.
	 */
	public int getKeyExchangeCounter();

}