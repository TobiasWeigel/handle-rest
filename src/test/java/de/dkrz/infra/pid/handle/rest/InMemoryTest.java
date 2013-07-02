package de.dkrz.infra.pid.handle.rest;

import net.handle.api.HSAdapter;

import org.junit.Before;

import de.dkrz.infra.pid.handle.rest.core.ApplicationContext;
import de.dkrz.infra.pid.handle.rest.core.HandleAuthorizationInfo;

public class InMemoryTest extends CommonTest {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		HSAdapter hsAdapter = new InMemoryStore();
		ApplicationContext context = new ApplicationContext(hsAdapter,
				new HandleAuthorizationInfo("", 300, null, null));
	}

}
