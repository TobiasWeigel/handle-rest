package de.dkrz.infra.pid.handle.rest;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import net.handle.api.HSAdapter;
import net.handle.api.HSAdapterFactory;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.dkrz.infra.pid.handle.rest.core.HandleAuthorizationInfo;
import de.dkrz.infra.pid.handle.rest.core.HandleReference;
import de.dkrz.infra.pid.handle.rest.core.HandleValueWrapper;
import de.dkrz.infra.pid.handle.rest.core.IdentifierNameGenerator;
import de.dkrz.infra.pid.handle.rest.core.IdentifierNameGeneratorFactory;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;

/**
 * Servlet for the root directory of all handle operations (/handles/). Provides
 * mechanism for creating one or multiple Handles (batch processing).
 * 
 * @author tobiasweigel
 * 
 */
public class HandleRootServlet extends HttpServlet {
	
	private static final int DEFAULT_ADMIN_VALUE_INDEX = 100;

	private final static Logger logger = Logger
			.getLogger(HandleRootServlet.class);

	protected Configuration freemarkerConfig;
	protected HSAdapter hsAdapter;
	protected HandleAuthorizationInfo authInfo;

	private RootHTMLRetriever rootHTMLRetriever;
	private RootJSONRetriever rootJSONRetriever;

	private JsonFactory jsonFactory = new JsonFactory();

	public HandleRootServlet() throws IOException, SAXException,
			ParserConfigurationException, HandleException {
		super();
		this.freemarkerConfig = new Configuration();
		this.freemarkerConfig.setClassForTemplateLoading(this.getClass(),
				"/templates");

		this.freemarkerConfig.setObjectWrapper(new DefaultObjectWrapper());
		this.authInfo = HandleAuthorizationInfo.createFromFile(new File(
				new File(System.getenv("HOME")), "handleservletconfig.xml"));
		this.hsAdapter = HSAdapterFactory.newInstance(
				authInfo.getAdminHandle(), authInfo.getKeyIndex(),
				authInfo.getPrivateKey(), authInfo.getCipher());
		this.rootHTMLRetriever = new RootHTMLRetriever(this.freemarkerConfig);
		this.rootJSONRetriever = new RootJSONRetriever(this.hsAdapter);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("POST");
		// accept only json
		if (req.getHeader("Accept").indexOf("application/json") < 0) {
			resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			return;
		}
		// will work only on /handles
		String path = req.getPathInfo();
		if ((path.equals("/handles") || path.equals("/handles/"))) {
			// create new Handle with random name or perform batch processing
			processCreateRequest(req, resp);
		} else if (path.startsWith("/handles/")) {
			// REST: POST to collection member - not really supported
			resp.sendError(405, "Cannot POST to individual Handles. Use PUT!");
		} else {
			// invalid path
			resp.sendError(404);
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("PUT");
		// accept only json
		if (req.getHeader("Accept").indexOf("application/json") < 0) {
			resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			return;
		}
		// will work only on /handles
		String path = req.getPathInfo();
		if ((path.equals("/handles") || path.equals("/handles/"))) {
			resp.sendError(405, "Cannot PUT at this level. Please use POST!");
		} else if (path.startsWith("/handles/")) {
			// create individual handle or replace existing one
			HandleReference handlepathref = HandleReference.fromRequestPath(
					path.substring(9), false);
			createHandles(req, resp, handlepathref);
		} else {
			// invalid path
			resp.sendError(404);
		}
	}
	
	/**
	 * Creates a single Handle.
	 * 
	 * @param req
	 * @param resp
	 * @param handleref is assumed to be complete, i.e. full Handle name and value record.
	 * @throws IOException 
	 */
	private void createSingleHandle(HttpServletRequest req,
			HttpServletResponse resp, HandleReference handleref) throws IOException {
		Vector<HandleValueWrapper> hvNew = handleref.getValues();
		try {
			HandleValue hvAdmin = hsAdapter.createAdminValue(
					this.authInfo.getAdminHandle(),
					this.authInfo.getKeyIndex(), DEFAULT_ADMIN_VALUE_INDEX);
			// check if handle exists
			boolean doCreate = false;
			HandleValue[] hvOrig = null;
			try {
				hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null,
						null);
			} catch (HandleException exc) {
				doCreate = true;
			}
			if (doCreate) {
				// handle did not exist; check for precondition
				if ((req.getHeader("If-Match") != null)
						&& (req.getHeader("If-Match").equals("*"))) {
					resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
							"Handle did not exist, but If-Match header was set.");
					return;
				}
				// create handle with new values
				boolean hvNewContainsAdminValue = false;
				for (HandleValueWrapper hv : hvNew) {
					if (hv.getType().equals("HS_ADMIN")) {
						hvNewContainsAdminValue = true;
						break;
					}
				}
				if (!hvNewContainsAdminValue) {
					// add admin handle value if none present in new values yet
					hvNew.add(new HandleValueWrapper(hvAdmin));
				}
				// transform hvNew to array
				HandleValue[] handlevalues = new HandleValue[hvNew.size()];
				handlevalues = hvNew.toArray(handlevalues);
				logger.debug("Creating Handle " + handleref.getHandle()
						+ " with values " + Arrays.toString(handlevalues));
				hsAdapter.createHandle(handleref.getHandle(), handlevalues);
				resp.setStatus(HttpServletResponse.SC_CREATED);
			} else {
				// handle exists already; check for precondition
				if ((req.getHeader("If-None-Match") != null)
						&& (req.getHeader("If-None-Match").equals("*"))) {
					resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
							"Handle exists, but If-None-Match header was set.");
					return;
				}
				// clear all handle values and replace with new values
				// make sure we don't remove any HS_ADMIN values
				HandleValue[] hvOrigClean = new HandleValue[hvOrig.length];
				int j = 0;
				for (int i = 0; i < hvOrig.length; i++) {
					if (hvOrig[i].getTypeAsString().equals("HS_ADMIN")) {
						continue;
					}
					hvOrigClean[j] = hvOrig[i];
					j++;
				}
				if (j > 0) {
					// remove old non-admin handle values
					HandleValue[] hvOrigCleanCopied = new HandleValue[j];
					System.arraycopy(hvOrigClean, 0, hvOrigCleanCopied, 0, j);
					hsAdapter.deleteHandleValues(handleref.getHandle(),
							hvOrigCleanCopied);
				}
				// transform hvNew to array
				HandleValue[] handlevalues = new HandleValue[hvNew.size()];
				handlevalues = hvNew.toArray(handlevalues);
				// add new handle values
				logger.debug("Adding values to Handle " + handleref.getHandle()
						+ ": " + Arrays.toString(handlevalues));
				hsAdapter.addHandleValues(handleref.getHandle(), handlevalues);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			}
		} catch (HandleException exc) {
			resp.sendError(500, exc.getMessage());
			logger.error(exc.getMessage(), exc);
		}
	}

	/**
	 * Creates one or many Handles.
	 * 
	 * @param handleref
	 *            Affects the mode of the operation. If empty, the operation will
	 *            expect the JSON request data to contain identifier names. If
	 *            the reference contains a prefix but not a full name, the
	 *            method will invoke a name generator. If a full Handle name is
	 *            given, the method will create this single Handle and fail if
	 *            the JSON data contains information for more than one Handle.
	 * @throws IOException
	 * @throws JsonParseException
	 */
	private void createHandles(HttpServletRequest req,
			HttpServletResponse resp, HandleReference handlepathref)
			throws JsonParseException, IOException {
		// parse json; must contain handle values, but not different Handle
		// entries.
		Vector<HandleReference> hvMultiple = parseJSONHandleValues(req
				.getReader());
		if (hvMultiple.size() > 1) {
			// Create multiple Handles
			if (handlepathref != null) {
				resp.sendError(400,
						"Can only process a single Handle in this operation!");
				return;
			}
			return;
		} else if (hvMultiple.size() == 1) {
			// Create a single Handle
			HandleReference singlehandle = hvMultiple.get(0);
			if (handlepathref.getHandle().isEmpty()) {
				// Nothing given in request path
				if (!singlehandle.hasProperName()) {
					resp.sendError(400, "No Handle identifier name given. Please provide at least a prefix.");
					return;
				}
				createSingleHandle(req, resp, singlehandle);
			} else if (handlepathref.isPrefixOnly()) {
				// a prefix but no suffix in path given; use a generator
				if (!singlehandle.getHandle().isEmpty()) {
					resp.sendError(400, "Use of multiple Handle names is not possible at this path.");
					return;
				}
				String generatorName = "";
				if (req.getParameter("generator") != null)
					generatorName = req.getParameter("generator");
				IdentifierNameGenerator generator = IdentifierNameGeneratorFactory
						.fromString(generatorName);
				singlehandle.setHandle(generator.generateName(handlepathref
						.getPrefix()));
				createSingleHandle(req, resp, singlehandle);
			} else {
				// a full name is given in the path; so create a Handle with exactly this name.
				if (!singlehandle.getHandle().isEmpty()) {
					resp.sendError(400, "Use of multiple Handle names is not possible at this path.");
					return;
				}
				singlehandle.setHandle(handlepathref.getHandle());
				createSingleHandle(req, resp, singlehandle);
			}
		} else {
			resp.sendError(400, "Empty JSON data.");
		}
	}

	/**
	 * Creates either a single new Handle or several, depending on the JSON data
	 * submitted.
	 * 
	 * @param req
	 * @param resp
	 */
	private void processCreateRequest(HttpServletRequest req,
			HttpServletResponse resp) {
		IdentifierNameGenerator generator = IdentifierNameGeneratorFactory
				.fromString(req.getParameter("generator"));
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("GET - " + req.getHeader("Accept"));
		if ((req.getHeader("Accept").indexOf("text/html") >= 0)
				|| (req.getHeader("Accept").indexOf("application/xhtml+xml") >= 0)) {
			logger.debug("RESPONSE: HTML");
			this.rootHTMLRetriever.exec(req, resp);
		} else if (req.getHeader("Accept").indexOf("application/json") >= 0) {
			logger.debug("RESPONSE: JSON");
			this.rootJSONRetriever.exec(req, resp);
		} else {
			resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
		}
	}

	protected Vector<HandleReference> parseJSONHandleValues(Reader reader)
			throws JsonParseException, IOException {
		/*
		 * The method will replace all current values on the handle with the
		 * given values (JSON encoded) plus an admin handle value
		 */
		JsonParser json = this.jsonFactory.createJsonParser(reader);
		// parse JSON. Variations exist. First, find out if base entity is array
		// or object.
		JsonToken baseEle = json.nextToken();
		if (baseEle == null) {
			throw new JsonParseException(
					"JSON format error - no base element found / empty content in request!",
					json.getCurrentLocation());
		}
		if (baseEle.equals(JsonToken.START_ARRAY)) {
			// array-based JSON. Will contain only one Handle with multiple
			// values. Iterate all elements of the array.
			Vector<HandleValue> hvNew = new Vector<HandleValue>();
			JsonToken ele = json.nextToken();
			while (!ele.equals(JsonToken.END_ARRAY)) {
				if (ele.equals(JsonToken.START_OBJECT)) {
					// read fields: index, type, data
					int index = 0;
					String type = null;
					String data = null;
					JsonToken field = json.nextToken();
					while (!field.equals(JsonToken.END_OBJECT)) {
						if (!field.equals(JsonToken.FIELD_NAME))
							throw new JsonParseException(
									"JSON format error - expected field name",
									json.getCurrentLocation());
						String fieldName = json.getText();
						if (fieldName.equalsIgnoreCase("idx")) {
							json.nextToken();
							index = json.getIntValue();
						} else if (fieldName.equalsIgnoreCase("type")) {
							json.nextToken();
							type = json.getText();
						} else if (fieldName.equalsIgnoreCase("data")) {
							// decode base64 later.. for now, we save text
							json.nextToken();
							data = json.getText();
						}
						field = json.nextToken();
					}
					// check read values
					if ((type == null) || (data == null))
						throw new JsonParseException(
								"JSON format error - must specify all of index, type and data",
								json.getCurrentLocation());
					if (index < 0)
						throw new JsonParseException("Illegal index value ("
								+ index + ") - must be positive",
								json.getCurrentLocation());
					// now decode base64 data if applicable
					byte[] data_byte = null;
					data_byte = data.getBytes();
					// values are ok; now assign HandleValue
					hvNew.add(new HandleValue(index, type.getBytes(), data_byte));
				} else
					throw new JsonParseException(
							"JSON format error - expected start of an object",
							json.getCurrentLocation());
				ele = json.nextToken();
			}
			Vector<HandleReference> res = new Vector<HandleReference>(1);
			HandleReference handleref = new HandleReference("");
			handleref.addValues(hvNew);
			res.add(handleref);
			return res;
		} else if (baseEle.equals(JsonToken.START_OBJECT)) {
			// TODO: object-based JSON. This is what EPIC API uses.
			throw new JsonParseException(
					"Using an object as the JSON base element is not yet implemented!",
					json.getCurrentLocation());
		} else
			throw new JsonParseException(
					"Base JSON element must be an Array or an Object!",
					json.getCurrentLocation());
	}

}
