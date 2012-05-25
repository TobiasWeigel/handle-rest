package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.handle.api.HSAdapter;
import net.handle.api.HSAdapterFactory;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

import org.apache.log4j.Logger;

import sun.misc.BASE64Decoder;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class HandleSystemEndpointServlet extends HttpServlet {

	/**
	 * Reference to a Handle or moer specifically, to one or more of its index
	 * values.
	 * 
	 * @author tobiasweigel
	 * 
	 */
	protected final static class HandleReference {

		protected final String handle;
		protected int[] indexes;

		public HandleReference(String handle, int[] indexes) {
			this.handle = handle;
			if (indexes == null) {
				this.indexes = new int[0];
			} else
				this.indexes = indexes;
		}

		public String getHandle() {
			return handle;
		}

		public int[] getIndexes() {
			return indexes;
		}

		public int numIndexes() {
			return indexes.length;
		}

		public void setIndexes(int[] indexes) {
			if (indexes == null)
				throw new IllegalArgumentException(
						"'indexes' parameter must not be null!");
			this.indexes = indexes;
		}

		@Override
		public String toString() {
			if (this.indexes.length == 0)
				return this.handle;
			else if (this.indexes.length == 1)
				return this.indexes[0] + ":" + this.handle;
			else {
				return "[...]:" + this.handle;
			}
		}
	}

	private static final int DEFAULT_ADMIN_VALUE_INDEX = 100;

	private final static Logger logger = Logger
			.getLogger(HandleSystemEndpointServlet.class);
	protected HSAdapter hsAdapter;
	protected HandleAuthorizationInfo authInfo;
	protected JsonFactory jsonFactory;

	public HandleSystemEndpointServlet(HandleAuthorizationInfo authInfo)
			throws HandleException {
		this.hsAdapter = HSAdapterFactory.newInstance(
				authInfo.getAdminHandle(), authInfo.getKeyIndex(),
				authInfo.getPrivateKey(), authInfo.getCipher());
		this.authInfo = authInfo;
		this.jsonFactory = new JsonFactory();
	}

	/**
	 * Analyses the given URI and returns the implied reference to a Handle and
	 * potentially also to specific Handle index values, if allowed.
	 * 
	 * @param uri
	 * @param allowIndexes
	 *            If true, index specification may be allowed. If false and
	 *            indexes are specified, the method will fail with an
	 *            IllegalArgumentException.
	 * @return a HandleReference object
	 * @throws IllegalArgumentException
	 *             if the URI is malformed
	 */
	protected static HandleReference determineHandleReference(
			HttpServletRequest req, boolean allowIndexes) {
		if (req.getPathInfo() == null) {
			throw new IllegalArgumentException(
					"Bad Request: Invalid path or no Handle given.");
		}
		String handle = req.getPathInfo();
		if (handle.startsWith("/"))
			handle = handle.substring(1);
		if (handle.length() == 0) {
			throw new IllegalArgumentException("Bad Request: No Handle given.");
		}
		int[] indexes = null;
		// detect key index prefixed to Handle prefix (key:prefix/suffix)
		if ((handle.indexOf(":") >= 0)
				&& (handle.indexOf(":") < handle.indexOf("/"))) {
			if (!allowIndexes) {
				throw new IllegalArgumentException(
						"Index specification not allowed for this operation!");
			}
			indexes = new int[1];
			String subs = handle.substring(0, handle.indexOf(":"));
			try {
				indexes[0] = Integer.parseInt(subs);
			} catch (NumberFormatException exc) {
				throw new IllegalArgumentException("Invalid handle index: "
						+ subs);
			}
			handle = handle.substring(handle.indexOf(":") + 1);
		}
		return new HandleReference(handle, indexes);
	}

	/**
	 * Analyses the given URI and returns the implied reference to a Handle and
	 * potentially also to specific Handle index values.
	 * 
	 * @param uri
	 * @return a HandleReference object
	 * @throws IllegalArgumentException
	 *             if the URI is malformed
	 */
	public static HandleReference determineHandleReference(
			HttpServletRequest req) {
		return determineHandleReference(req, true);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("GET...");
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req);
		} catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			logger.debug(
					"Could not determine Handle reference due to an Exception.",
					exc);
			return;
		}
		try {
			logger.debug("Trying to look up Handle " + handleref.getHandle());
			HandleValue[] allhv = hsAdapter.resolveHandle(
					handleref.getHandle(), null, handleref.getIndexes());
			logger.debug("Looked up Handle " + handleref.getHandle()
					+ "; it has values: " + Arrays.toString(allhv));
			// encode all values in JSON
			JsonGenerator json = jsonFactory.createJsonGenerator(resp
					.getWriter());
			json.writeStartArray();
			for (HandleValue hv : allhv) {
				json.writeStartObject();
				json.writeStringField("index",
						((Integer) hv.getIndex()).toString());
				json.writeStringField("type", hv.getTypeAsString());
				json.writeStringField("data", hv.getDataAsString());
				json.writeStringField("perm", hv.getPermissionString());
				json.writeEndObject();
			}
			json.writeEndArray();
			json.close();
		} catch (HandleException exc) {
			resp.sendError(404, "Unknown Handle: " + exc.getLocalizedMessage()
					+ " [" + exc.getCode() + "]");
			logger.error(exc);
			return;
		} catch (Exception exc) {
			resp.sendError(500,
					"Error while processing the request: " + exc.getMessage());
			logger.error("Error during GET request:", exc);
			return;
		}
	}

	/**
	 * Parses the JSON input from the given Reader into a vector of HandleValue
	 * instances.
	 * 
	 * @param reader
	 * @return Vectory<HandleValue> containing the parsed data
	 * @throws IOException
	 * @throws JsonParseException
	 */
	protected Vector<HandleValue> parseJSONHandleValues(Reader reader)
			throws JsonParseException, IOException {
		Vector<HandleValue> hvNew = new Vector<HandleValue>();
		/*
		 * The method will replace all current values on the handle with the
		 * given values (JSON encoded) plus an admin handle value
		 */
		JsonParser json = jsonFactory.createJsonParser(reader);
		// parse JSON. Variations exist. First, find out if base entity is array
		// or object.
		JsonToken baseEle = json.nextToken();
		if (baseEle == null) {
			throw new IllegalArgumentException(
					"JSON format error - no base element found / empty content in request!");
		}
		if (baseEle.equals(JsonToken.START_ARRAY)) {
			// array-based JSON. Iterate all elements of the array.
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
							throw new IllegalArgumentException(
									"JSON format error - expected field name - at "
											+ json.getCurrentLocation());
						String fieldName = json.getText();
						if (fieldName.equalsIgnoreCase("index")) {
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
						throw new IllegalArgumentException(
								"JSON format error - must specify all of index, type and data - near "
										+ json.getCurrentLocation());
					if (index < 0)
						throw new IllegalArgumentException(
								"Illegal index value (" + index
										+ ") - must be positive - near "
										+ json.getCurrentLocation());
					// now decode base64 data if applicable
					byte[] data_byte = null;
					data_byte = data.getBytes();
					// values are ok; now assign HandleValue
					hvNew.add(new HandleValue(index, type.getBytes(), data_byte));
				} else
					throw new IllegalArgumentException(
							"JSON format error - expected start of an object at "
									+ json.getCurrentLocation());
				ele = json.nextToken();
			}
		} else if (baseEle.equals(JsonToken.START_OBJECT)) {
			// object-based JSON.
			throw new IllegalArgumentException(
					"Using an object as the JSON base element is not yet implemented!");
		} else
			throw new IllegalArgumentException(
					"Base JSON element must be an Array or an Object!");
		return hvNew;
	}

	/**
	 * PUT request to replace all values of a handle (overwrite) and create a
	 * new handle if it does not exist yet. The method will add an admin handle
	 * value at index 100 automatically, unless the given values contain such a
	 * value at any index.
	 * 
	 * The JSON format is array-based. The handle values must be provided as an
	 * array, where each array entry is an object with fields "index", "type"
	 * and "data". By default, the index field must be a positive integer, the
	 * type field must be a string. The data field is passed as it is (a String)
	 * and not base64-decoded or similarly encoded.
	 * 
	 * Special care is taken of HS_ADMIN values. If there is no HS_ADMIN value
	 * specified in the JSON data and the Handle does not exist yet, the method
	 * will automatically add a default HS_ADMIN value. If the handle did exist,
	 * the method will not remove any existing HS_ADMIN values to prevent the
	 * emergence of invalid Handles that do not bear admin information.
	 * 
	 * This also means that this method is not suited for updating HS_ADMIN
	 * information on existing Handles!
	 */
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("PUT...");
		if ((req.getContentType() == null)
				|| (!req.getContentType().equals("application/json"))) {
			resp.sendError(415,
					"Only application/json is allowed as request MIME type.");
			logger.debug("Refused PUT request due to wrong MIME type: "
					+ req.getContentType());
			return;
		}
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req, false);
		} catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			logger.debug(
					"Could not determine Handle reference due to an Exception.",
					exc);
			return;
		}
		try {
			logger.debug("Processing PUT request for Handle " + handleref);
			Vector<HandleValue> hvNew;
			if (req.getContentLength() <= 0) {
				// empty content - just create Handle with admin value
				hvNew = new Vector<HandleValue>();
			} else {
				hvNew = parseJSONHandleValues(req.getReader());
			}
			HandleValue hvAdmin = hsAdapter.createAdminValue(
					authInfo.getAdminHandle(), authInfo.getKeyIndex(),
					DEFAULT_ADMIN_VALUE_INDEX);
			HandleValue[] hvOrig = null;
			// check if handle exists
			boolean doCreate = false;
			try {
				hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null,
						null);
			} catch (HandleException exc) {
				doCreate = true;
			}
			if (doCreate) {
				// handle did not exist; create handle with new values
				boolean hvNewContainsAdminValue = false;
				for (HandleValue hv : hvNew) {
					if (hv.getTypeAsString().equals("HS_ADMIN")) {
						hvNewContainsAdminValue = true;
						break;
					}
				}
				if (!hvNewContainsAdminValue) {
					// add admin handle value if none present in new values yet
					hvNew.add(hvAdmin);
				}
				// transform hvNew to array
				HandleValue[] handlevalues = new HandleValue[hvNew.size()];
				handlevalues = hvNew.toArray(handlevalues);
				logger.debug("Creating Handle " + handleref.getHandle()
						+ " with values " + Arrays.toString(handlevalues));
				hsAdapter.createHandle(handleref.getHandle(), handlevalues);
			} else {
				// handle exists already; clear all handle values and replace
				// with new values
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
			}
		} catch (Exception exc) {
			resp.sendError(500,
					"Error while processing the request: " + exc.getMessage());
			logger.error("Error during PUT request: ", exc);
			return;
		}
	}

	/**
	 * POST request to update values on an existing Handle. The method will fail
	 * if the specified Handle does not exist. Note that POST will not overwrite
	 * the whole resource, but modify it. Thus, it can be used to e.g. add a
	 * single new value to a Handle without affecting the existing values.
	 * 
	 * The method does not support index prefixes since specific handle values
	 * can be addressed in the JSON data.
	 * 
	 * The JSON format is basically the same as for the POST method. However, it
	 * is possible to remove handle values if their index is given, but type and
	 * data are left empty (empty string). In this case, the method will remove
	 * these index values.
	 * 
	 * The method can be used to manipulate HS_ADMIN values, though this is not
	 * recommended. The method will check and fail if the last HS_ADMIN value is
	 * in danger of being removed.
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("POST...");
		if ((req.getContentType() == null)
				|| (!req.getContentType().equals("application/json"))) {
			resp.sendError(415,
					"Only application/json is allowed as request MIME type.");
			logger.debug("Refused POST request due to wrong MIME type: "
					+ req.getContentType());
			return;
		}
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req, false);
		} catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			logger.error(exc);
			return;
		}
		try {
			logger.debug("Processing POST request for Handle " + handleref);
			if (req.getContentLength() <= 0) {
				resp.sendError(400, "Content missing in POST request!");
				logger.debug("Content missing in POST request");
				return;
			}
			req.getReader().mark(4096);
			logger.debug("Request data: " + req.getReader().readLine());
			req.getReader().reset();
			Vector<HandleValue> hvNew = parseJSONHandleValues(req.getReader());
			// get old handle values
			HandleValue[] hvOrig = null;
			try {
				hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null,
						null);
			} catch (HandleException exc) {
				throw new IllegalStateException(
						"Handle "
								+ handleref.getHandle()
								+ " does not exist. Use PUT method to create new Handles!");
			}
			int hsadminsLost = 0;
			int hsadminsGained = 0;
			/*
			 * Go through hvNew and determine index values to delete, add and
			 * update.
			 */
			Vector<HandleValue> hvDelete = new Vector<HandleValue>();
			Vector<HandleValue> hvAdd = new Vector<HandleValue>();
			Vector<HandleValue> hvUpdate = new Vector<HandleValue>();
			for (HandleValue hv : hvNew) {
				int index = hv.getIndex();
				// check if original value exists
				boolean valueExists = false;
				boolean origIsHSAdmin = false;
				for (HandleValue hvo : hvOrig) {
					if (hvo.getIndex() == index) {
						valueExists = true;
						if (hvo.getTypeAsString().equals("HS_ADMIN"))
							origIsHSAdmin = true;
						break;
					}
				}
				if ((hv.getData().length == 0) && (hv.getType().length == 0)) {
					// delete this value (but only if it still exists)
					if (valueExists) {
						hvDelete.add(hv);
						if (origIsHSAdmin)
							hsadminsLost++;
					}
					continue;
				}
				if (hv.getTypeAsString().equals("HS_ADMIN"))
					hsadminsGained++;
				if (valueExists) {
					hvUpdate.add(hv);
					if (origIsHSAdmin)
						hsadminsLost++;
				} else
					hvAdd.add(hv);
			}
			/* security check: prevent removal of ALL HS_ADMIN values */
			int hsadminsOrig = 0;
			for (HandleValue hv : hvOrig) {
				if (hv.getTypeAsString().equals("HS_ADMIN"))
					hsadminsOrig++;
			}
			if (hsadminsOrig + hsadminsGained - hsadminsLost <= 0) {
				throw new Exception(
						"You are not allowed to remove the last HS_ADMIN value from a Handle!");
			}
			/* Update values */
			if (hvUpdate.size() > 0) {
				HandleValue[] arr = new HandleValue[hvUpdate.size()];
				arr = hvUpdate.toArray(arr);
				hsAdapter.updateHandleValues(handleref.getHandle(), arr);
			}
			/* Add values */
			if (hvAdd.size() > 0) {
				HandleValue[] arr = new HandleValue[hvAdd.size()];
				arr = hvAdd.toArray(arr);
				hsAdapter.addHandleValues(handleref.getHandle(), arr);
			}
			/* Delete values */
			if (hvDelete.size() > 0) {
				HandleValue[] arr = new HandleValue[hvDelete.size()];
				arr = hvDelete.toArray(arr);
				hsAdapter.deleteHandleValues(handleref.getHandle(), arr);
			}
		} catch (Exception exc) {
			resp.sendError(500,
					"Error while processing the request: " + exc.getMessage());
			logger.error("Error during POST request:", exc);
			return;
		}
	}

	/**
	 * DELETE request to delete Handles or specific Handle values via their
	 * index. If the given Handle or the specified Handle values do not exist,
	 * the method will fail with HTTP code 404.
	 * 
	 * This method supports index prefixes, i.e. can be called with
	 * index:prefix/suffix to remove a specific Handle value.
	 */
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("DELETE...");
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req, true);
		} catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			return;
		}
		/* Check for Handle existence */
		HandleValue[] hvOrig = null;
		try {
			hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null, null);
		} catch (HandleException exc) {
			resp.sendError(404, "Handle does not exist.");
			logger.error(exc);
			return;
		}
		try {
			logger.debug("Processing DELETE request for Handle " + handleref);
			if (handleref.numIndexes() > 0) {
				/* Security check: prevent deletion of last HS_ADMIN value */
				int numHSAdminLost = 0;
				int numHSAdminPresent = 0;
				Vector<HandleValue> valuesToDelete = new Vector<HandleValue>();
				for (int idx : handleref.getIndexes()) {
					// find corresponding original handle value
					HandleValue hv = null;
					for (HandleValue hvo : hvOrig) {
						if (hvo.getIndex() == idx) {
							hv = hvo;
							break;
						}
					}
					if (hv == null) {
						throw new Exception("Handle value with index " + idx
								+ " not found in Handle "
								+ handleref.getHandle() + "!");
					}
					if (hv.getTypeAsString().equals("HS_ADMIN")) {
						numHSAdminLost++;
					}
					valuesToDelete.add(hv);
				}
				for (HandleValue hv : hvOrig) {
					if (hv.getTypeAsString().equals("HS_ADMIN")) {
						numHSAdminPresent++;
					}
				}
				if (numHSAdminPresent - numHSAdminLost <= 0) {
					throw new Exception(
							"You are not allowed to remove the last HS_ADMIN value from a Handle!");
				}
				/* Delete Handle value(s) */
				HandleValue[] arr = new HandleValue[valuesToDelete.size()];
				arr = valuesToDelete.toArray(arr);
				hsAdapter.deleteHandleValues(handleref.getHandle(), arr);
			} else {
				/* Delete Handle */
				hsAdapter.deleteHandle(handleref.getHandle());
			}
		} catch (Exception exc) {
			resp.sendError(500,
					"Error while processing the request: " + exc.getMessage());
			logger.error("Error during DELETE request:", exc);
			return;
		}
	}

}
