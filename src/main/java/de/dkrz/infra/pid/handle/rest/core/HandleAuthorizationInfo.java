package de.dkrz.infra.pid.handle.rest.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

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

	public static HandleAuthorizationInfo createFromFile(File configFile) throws IOException, SAXException, ParserConfigurationException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(configFile);
		Element docele = doc.getDocumentElement();
		String adminHandle = docele.getElementsByTagName("adminHandle").item(0).getTextContent();
		int keyIndex = Integer.parseInt(docele.getElementsByTagName("keyIndex").item(0).getTextContent());
		byte[] cipher = docele.getElementsByTagName("cipher").item(0).getTextContent().getBytes();
		// load private key from file
		String privateKeyFilename = docele.getElementsByTagName("privateKeyFile").item(0).getTextContent();
		byte[] privateKey = readKeyFromFile(privateKeyFilename);
		return new HandleAuthorizationInfo(adminHandle, keyIndex, privateKey, cipher);
		
	}
	
	public static byte[] readKeyFromFile(String fn) throws IOException {
		File f = new File(fn);
		if (f.length() > 1024) {
			throw new IOException("Secret Key file too large / illegal format!");
		}
		FileInputStream fis;
		byte[] res = new byte[(int) f.length()];
		fis = new FileInputStream(new File(fn));
		fis.read(res);
		fis.close();
		return res;
	}	
}
