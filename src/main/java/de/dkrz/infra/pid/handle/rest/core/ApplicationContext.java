package de.dkrz.infra.pid.handle.rest.core;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import net.handle.api.HSAdapter;
import net.handle.api.HSAdapterFactory;

/**
 * Singleton context object.
 * 
 * @author tobiasweigel
 * 
 */
public class ApplicationContext {

	private static ApplicationContext instance;

	private HSAdapter hsAdapter;
	private HandleAuthorizationInfo authInfo;
	
	private static final Logger logger = Logger.getLogger(ApplicationContext.class);

	private ApplicationContext() throws Exception {
		instance = this;
		this.authInfo = HandleAuthorizationInfo.createFromFile(new File(
				new File(System.getenv("HOME")), "handleservletconfig.xml"));
		this.hsAdapter = HSAdapterFactory.newInstance(
				authInfo.getAdminHandle(), authInfo.getKeyIndex(),
				authInfo.getPrivateKey(), authInfo.getCipher());
	}
	
	public ApplicationContext(HSAdapter hsAdapter, HandleAuthorizationInfo authInfo) {
		instance = this;
		this.hsAdapter = hsAdapter;
		this.authInfo = authInfo;
		
	}

	public static ApplicationContext getInstance() {
		if (ApplicationContext.instance == null)
			try {
				return new ApplicationContext();
			} catch (Exception exc) {
				logger.error(exc.getMessage(), exc);
			}
		return instance;
	}

	public HSAdapter getHSAdapter() {
		return hsAdapter;
	}

	public HandleAuthorizationInfo getAuthInfo() {
		return authInfo;
	}

	public IdentifierNameGenerator getDefaultIdentifierNameGenerator() {
		// TODO: original API has config file option for this
		return new UUIDGenerator();
	}
}
