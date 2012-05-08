package de.dkrz.infra.pid.handle.rest;

import java.security.PrivateKey;

public class HandleAuthorizationInfo {
	
	private String adminHandle;
	private int keyIndex;
	private byte[] privateKey;
	private byte[] cipher;
	
	public String getAdminHandle() {
		return adminHandle;
	}
	public int getKeyIndex() {
		return keyIndex;
	}
	public byte[] getPrivateKey() {
		return privateKey;
	}
	public byte[] getCipher() {
		return cipher;
	}
	public HandleAuthorizationInfo(String adminHandle, int keyIndex,
			byte[] privateKey, byte[] cipher) {
		super();
		this.adminHandle = adminHandle;
		this.keyIndex = keyIndex;
		this.privateKey = privateKey;
		this.cipher = cipher;
	}
	
}
