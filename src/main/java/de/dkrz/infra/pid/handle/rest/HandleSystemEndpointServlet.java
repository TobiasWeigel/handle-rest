package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sun.misc.BASE64Decoder;

import net.handle.api.HSAdapter;
import net.handle.api.HSAdapterFactory;
import net.handle.hdllib.Common;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;

import com.fasterxml.jackson.core.*;

public class HandleSystemEndpointServlet extends HttpServlet {
	
	/**
	 * Reference to a Handle or moer specifically, to one or more of its index values.
	 * 
	 * @author tobiasweigel
	 *
	 */
	protected final static class HandleReference {
		
		protected final String handle;
		protected final int[] indexes;
		
		public HandleReference(String handle, int[] indexes) {
			this.handle = handle;
			this.indexes = indexes;
		}

		public String getHandle() {
			return handle;
		}

		public int[] getIndexes() {
			return indexes;
		}
	}

	private static final int DEFAULT_ADMIN_VALUE_INDEX = 100;
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());
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
	 * Analyses the given URI and returns the implied reference to a Handle and potentially also to specific Handle 
	 * index values, if allowed.
	 * 
	 * @param uri
	 * @param allowIndexes If true, index specification may be allowed. If false and indexes are specified, the method 
	 * will fail with an IllegalArgumentException.
	 * @return a HandleReference object
	 * @throws IllegalArgumentException if the URI is malformed
	 */
	protected static HandleReference determineHandleReference(String uri, boolean allowIndexes) {
		String[] parts = uri.split("/", 3);
		if (parts.length < 3) {
			throw new IllegalArgumentException("Bad Request: No handle given.");
		}
		String handle = parts[2];
		int[] indexes = null;
		// detect key index prefixed to Handle prefix (key:prefix/suffix)
		if ((handle.indexOf(":") >= 0) && (handle.indexOf(":") < handle.indexOf("/"))) {
			if (!allowIndexes) {
				throw new IllegalArgumentException("Index specification not allowed for this operation!");
			}
			indexes = new int[1];
			String subs = handle.substring(0, handle.indexOf(":"));
			try {
				indexes[0] = Integer.parseInt(subs);
			}
			catch (NumberFormatException exc) {
				throw new IllegalArgumentException("Invalid handle index: "+subs);
			}
			handle = handle.substring(handle.indexOf(":")+1);
		}
		return new HandleReference(handle, indexes);
	}

	/**
	 * Analyses the given URI and returns the implied reference to a Handle and potentially also to specific Handle 
	 * index values.
	 * 
	 * @param uri
	 * @return a HandleReference object
	 * @throws IllegalArgumentException if the URI is malformed
	 */
	public static HandleReference determineHandleReference(String uri) {
		return determineHandleReference(uri, true);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req.getRequestURI());
		}
		catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			return;
		}
		try {
			HandleValue[] allhv = hsAdapter.resolveHandle(handleref.getHandle(), null, handleref.getIndexes());
			// encode all values in JSON
			JsonGenerator json = jsonFactory.createJsonGenerator(resp.getWriter());
			json.writeStartObject();
			for (HandleValue hv: allhv) {
				json.writeObjectFieldStart(((Integer)hv.getIndex()).toString());
				json.writeStringField("type", hv.getTypeAsString());
				if (isStringType(hv.getTypeAsString())) {
					json.writeStringField("data", hv.getDataAsString());
				} else {
					json.writeBinaryField("data", hv.getData());
				}
				json.writeStringField("perm", hv.getPermissionString());
				json.writeEndObject();
			}
			json.writeEndObject();
			json.close();
		}
		catch (HandleException exc) {
			resp.sendError(500, "Could not resolve Handle: "+exc.getLocalizedMessage()+" ["+exc.getCode()+"]");
			return;
		}
		catch (Exception exc) {
			StringWriter wr = new StringWriter();
			PrintWriter pwr = new PrintWriter(wr);
			exc.printStackTrace(pwr);
			pwr.flush();
			wr.flush();
			resp.sendError(500, "Error during Handle lookup or JSON encoding.\n\n"+wr.toString());
			return;
		}
	}
	
	public static boolean isStringType(String typeAsString) {
		return (typeAsString.equals("URL") || typeAsString.equals("URN") || typeAsString.equals("EMAIL") || typeAsString.equals("HS_ALIAS"));
	}
	
	/**
	 * Parses the JSON input from the given Reader into a vector of HandleValue instances.
	 * 
	 * @param reader
	 * @return Vectory<HandleValue> containing the parsed data
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	protected Vector<HandleValue> parseJSONHandleValues(Reader reader) throws JsonParseException, IOException {
		BASE64Decoder base64 = new BASE64Decoder();
		Vector<HandleValue> hvNew = new Vector<HandleValue>();
		/* The method will replace all current values on the handle with the given values (JSON encoded)
		 * plus an admin handle value */
		JsonParser json = jsonFactory.createJsonParser(reader);
		// parse JSON. Variations exist. First, find out if base entity is array or object.
		JsonToken baseEle = json.nextToken();
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
						if (!field.equals(JsonToken.FIELD_NAME)) throw new IllegalArgumentException("JSON format error - expected field name - at "+json.getCurrentLocation());
						String fieldName = json.getText();
						if (fieldName.equalsIgnoreCase("index")) {
							json.nextToken();
							index = json.getIntValue();
						}
						else if (fieldName.equalsIgnoreCase("type")) {
							json.nextToken();
							type = json.getText();
						}
						else if (fieldName.equalsIgnoreCase("data")) {
							// decode base64 later.. for now, we save text
							json.nextToken();
							data = json.getText();
						}
						field = json.nextToken();
					}
					// check read values
					if ((type == null) || (data == null)) throw new IllegalArgumentException("JSON format error - must specify all of index, type and data - near "+json.getCurrentLocation());
					if (index < 0) throw new IllegalArgumentException("Illegal index value ("+index+") - must be positive - near "+json.getCurrentLocation());
					// now decode base64 data if applicable
					byte[] data_byte = null;
					if (!isStringType(type)) {
						data_byte = base64.decodeBuffer(data);
					}
					else data_byte = data.getBytes();
					// values are ok; now assign HandleValue
					hvNew.add(new HandleValue(index, type.getBytes(), data_byte));
				}
				else throw new IllegalArgumentException("JSON format error - expected start of an object at "+json.getCurrentLocation());
				ele = json.nextToken();
			}
		} else if (baseEle.equals(JsonToken.START_OBJECT)) {
			// object-based JSON.
			throw new IllegalArgumentException("Using an object as the JSON base element is not yet implemented!");
		} else throw new IllegalArgumentException("Base JSON element must be an Array or an Object!");
		return hvNew;
	}
	
	/**
	 * POST request to replace all values of a handle and create a new handle if it does not exist yet.
	 * The method will add an admin handle value at index 100 automatically, unless the given values contain such a 
	 * value at any index.  
	 * 
	 * The JSON format is array-based. The handle values must be provided as an array, where each array entry is an 
	 * object with fields "index", "type" and "data". By default, the index field must be a positive integer, the type
	 * field must be a string. The data field defaults to a base64 encoded string, however, if the type field is any one
	 * of URL, URN, EMAIL or HS_ALIAS, the data field is passed as it is and not base64-decoded.
	 * 
	 * Special care is taken of HS_ADMIN values. If there is no HS_ADMIN value specified in the JSON data and the Handle
	 * does not exist yet, the method will automatically add a default HS_ADMIN value. If the handle did exist, the 
	 * method will not remove any existing HS_ADMIN values to prevent the emergence of invalid Handles that do not bear
	 * admin information. 
	 * 
	 * This also means that this method is not suited for updating HS_ADMIN information on existing Handles! 
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (!req.getContentType().equals("application/json")) {
			resp.sendError(415, "Only application/json is allowed as request MIME type.");
			return;
		}
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req.getRequestURI(), false);
		}
		catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			return;
		}
		try{
			Vector<HandleValue> hvNew = parseJSONHandleValues(req.getReader());
			HandleValue hvAdmin = hsAdapter.createAdminValue(authInfo.getAdminHandle(), authInfo.getKeyIndex(), DEFAULT_ADMIN_VALUE_INDEX);
			HandleValue[] hvOrig = null;
			// check if handle exists
			boolean doCreate = false;
			try {
				hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null, null); 
			}
			catch (HandleException exc) {
				doCreate = true;
			}
			// transform hvNew to array
			HandleValue[] handlevalues = new HandleValue[hvNew.size()];
			handlevalues = hvNew.toArray(handlevalues);
			if (doCreate) {
				// handle did not exist; create handle with new values
				boolean hvNewContainsAdminValue = false;
				for (HandleValue hv: hvNew) {
					if (hv.getTypeAsString().equals("HS_ADMIN")) {
						hvNewContainsAdminValue = true;
						break;
					}
				}
				if (!hvNewContainsAdminValue) {
					// add admin handle value if none present in new values yet
					hvNew.add(hvAdmin);
				}
				hsAdapter.createHandle(handleref.getHandle(), handlevalues);
			}
			else {
				// handle exists already; clear all handle values and replace with new values
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
					hsAdapter.deleteHandleValues(handleref.getHandle(), hvOrigCleanCopied);
				}
				// add new handle values
				hsAdapter.addHandleValues(handleref.getHandle(), handlevalues);
			}
		}
		catch (Exception exc) {
			StringWriter wr = new StringWriter();
			PrintWriter pwr = new PrintWriter(wr);
			exc.printStackTrace(pwr);
			pwr.flush();
			wr.flush();
			resp.sendError(500, "Error while processing the request.\n\n"+wr.toString());
			return;
		}
	}
	
	/**
	 * PUT request to update values on an existing Handle. The method will fail if the specified Handle does not exist.
	 * 
	 * The method does not support index prefixes since specific handle values can be addressed in the JSON data.
	 * 
	 * The JSON format is basically the same as for the POST method. However, it is possible to remove handle values
	 * if their index is given, but type and data are left empty (empty string). In this case, the method will remove
	 * these index values. 
	 * 
	 * The method can be used to manipulate HS_ADMIN values, though this is not recommended. The method will check and
	 * fail if the last HS_ADMIN value is in danger of being removed.
	 */
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (!req.getContentType().equals("application/json")) {
			resp.sendError(415, "Only application/json is allowed as request MIME type.");
			return;
		}
		HandleReference handleref;
		try {
			handleref = determineHandleReference(req.getRequestURI(), false);
		}
		catch (IllegalArgumentException exc) {
			resp.sendError(400, exc.getMessage());
			return;
		}
		try {
			Vector<HandleValue> hvNew = parseJSONHandleValues(req.getReader());
			// get old handle values
			HandleValue[] hvOrig = null;
			try {
				hvOrig = hsAdapter.resolveHandle(handleref.getHandle(), null, null); 
			}
			catch (HandleException exc) {
				throw new IllegalStateException("Handle "+handleref.getHandle()+" does not exist. Use POST method to create new Handles!");
			}
			int hsadminsLost = 0;
			int hsadminsGained = 0;
			/* Go through hvNew and determine index values to delete, add and update. */
			Vector<HandleValue> hvDelete = new Vector<HandleValue>();
			Vector<HandleValue> hvAdd = new Vector<HandleValue>();
			Vector<HandleValue> hvUpdate = new Vector<HandleValue>();
			for (HandleValue hv: hvNew) {
				int index = hv.getIndex();
				// check if original value exists
				boolean valueExists = false;
				boolean origIsHSAdmin = false;
				for (HandleValue hvo: hvOrig) {
					if (hvo.getIndex() == index) {
						valueExists = true;
						if (hvo.getTypeAsString().equals("HS_ADMIN")) origIsHSAdmin = true;
						break;
					}
				}
				if ((hv.getData().length == 0) && (hv.getType().length == 0)) {
					// delete this value (but only if it still exists)
					if (valueExists) {
						hvDelete.add(hv);
						if (origIsHSAdmin) hsadminsLost++;
					}
					continue;
				}
				if (hv.getTypeAsString().equals("HS_ADMIN")) hsadminsGained++;
				if (valueExists) {
					hvUpdate.add(hv);
					if (origIsHSAdmin) hsadminsLost++;
				}
				else hvAdd.add(hv);
			}
			/* security check: prevent removal of ALL HS_ADMIN values */
			int hsadminsOrig = 0;
			for (HandleValue hv: hvOrig) {
				if (hv.getTypeAsString().equals("HS_ADMIN")) hsadminsOrig++;
			}
			if (hsadminsOrig+hsadminsGained-hsadminsLost <= 0) {
				throw new Exception("You are not allowed to remove the last HS_ADMIN value from a Handle!");
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
		}
		catch (Exception exc) {
			StringWriter wr = new StringWriter();
			PrintWriter pwr = new PrintWriter(wr);
			exc.printStackTrace(pwr);
			pwr.flush();
			wr.flush();
			resp.sendError(500, "Error while processing the request.\n\n"+wr.toString());
			return;
		}
	}

}
