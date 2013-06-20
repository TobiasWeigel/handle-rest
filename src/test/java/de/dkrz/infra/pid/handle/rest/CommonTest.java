package de.dkrz.infra.pid.handle.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import net.handle.api.HSAdapter;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;

import de.dkrz.infra.pid.handle.rest.core.HandleReference;

public class CommonTest extends JerseyTest {

	private static final Logger logger = Logger.getLogger(JerseyTest.class);

	private ArrayList<String> handlesCreated = new ArrayList<String>();

	public CommonTest() {
		super("de.dkrz.infra.pid.handle.rest.resources");
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
	}

	@Override
	@After
	public void tearDown() throws Exception {
		super.tearDown();
		// delete all created Handles
		HSAdapter hsAdapter = ApplicationContext.getInstance().getHSAdapter();
		for (String h : handlesCreated) {
			try {
				hsAdapter.deleteHandle(h);
			} catch (Exception exc) {
				logger.error(exc);
			}
		}
	}

	@Test
	public void test() {
		WebResource webResource = resource();
		// PUT
		String json = "[{\"idx\": 1, \"type\": \"URL\", \"data\":\"http://www.google.de\"}]";
		ClientResponse resp = webResource.path("/handles/10876/test-001")
				.type("application/json").put(ClientResponse.class, json);
		assertEquals(201, resp.getStatus());
		handlesCreated.add("10876/test-001");
		// GET
		HandleReference handleref = webResource.path("/handles/10876/test-001")
				.get(HandleReference.class);
		assertNotNull(handleref);
		assertEquals("10876/test-001", handleref.getHandle());
		assertEquals("http://www.google.de", handleref.getValues().get(0)
				.getParsedData());

	}

}
