package de.dkrz.infra.pid.handle.rest.resources;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import net.handle.api.HSAdapter;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.dkrz.infra.pid.handle.rest.core.ApplicationContext;
import de.dkrz.infra.pid.handle.rest.core.HandleAuthorizationInfo;
import de.dkrz.infra.pid.handle.rest.core.HandleReference;
import de.dkrz.infra.pid.handle.rest.core.HandleValueWrapper;
import de.dkrz.infra.pid.handle.rest.core.IdentifierNameGenerator;
import de.dkrz.infra.pid.handle.rest.core.IdentifierNameGeneratorFactory;

@Path("handles")
public class HandleRootResource {

	public static final int DEFAULT_ADMIN_VALUE_INDEX = 100;

	private static final Logger logger = Logger
			.getLogger(HandleRootResource.class);

	@Context
	protected UriInfo uriInfo;
	@Context
	protected Request request;

	protected HSAdapter hsAdapter;
	protected HandleAuthorizationInfo authInfo;

	private JsonFactory jsonFactory = new JsonFactory();

	public HandleRootResource() {
		this.hsAdapter = ApplicationContext.getInstance().getHSAdapter();
		this.authInfo = ApplicationContext.getInstance().getAuthInfo();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getHandles() {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{prefix}")
	public List<HandleReference> getHandlesByPrefix(
			@PathParam("prefix") String prefix) {
		throw new UnsupportedOperationException("not implemented yet");
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{prefix}/{suffix}")
	public HandleReference getHandle(@PathParam("prefix") String prefix,
			@PathParam("suffix") String suffix) throws HandleException {
		HandleReference handleref = new HandleReference(prefix + "/" + suffix);
		HSAdapter hsAdapter = ApplicationContext.getInstance().getHSAdapter();
		try {
			HandleValue[] allhv = hsAdapter.resolveHandle(handleref.getHandle(),
					null, handleref.getIndexes());
			handleref.addValues(allhv);
			return handleref; 
		} catch (HandleException exc) {
			if (exc.getCode() == HandleException.HANDLE_DOES_NOT_EXIST)
				throw new WebApplicationException(404);
			else throw exc;
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public Response execRootPost(@QueryParam("generator") @DefaultValue("") String generatorName, String requestBody) {
		// request must contain one or many full handle names
		IdentifierNameGenerator generator;
		if (generatorName.equals("")) {
			generator = ApplicationContext.getInstance()
					.getDefaultIdentifierNameGenerator();
		} else
			generator = IdentifierNameGeneratorFactory
					.fromString(generatorName);
		return createHandles(null, generator, requestBody);
	}

	@POST
	@Path("{prefix}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response execPrefixPost(@PathParam("prefix") String prefix,
			@QueryParam("generator") @DefaultValue("") String generatorName,
			String requestBody) {
		// request will either contain only handle values, but no suffix (must
		// use generator)
		// but it might also work with json data that contains one or many full
		// handle names (for this prefix!)
		IdentifierNameGenerator generator;
		if (generatorName.equals("")) {
			generator = ApplicationContext.getInstance()
					.getDefaultIdentifierNameGenerator();
		} else
			generator = IdentifierNameGeneratorFactory
					.fromString(generatorName);
		return createHandles(prefix, generator, requestBody);
	}

	@PUT
	@Path("{prefix}/{suffix}")
	@Consumes("application/json")
	public Response putHandle(@PathParam("prefix") String prefix,
			@PathParam("suffix") String suffix,
			@HeaderParam("If-Match") @DefaultValue("") String ifMatch,
			@HeaderParam("If-None-Match") @DefaultValue("") String ifNoneMatch,
			String requestBody) {
		logger.debug("PUT on specific handle...");
		// replace a particular Handle
		HandleReference handleref = new HandleReference(prefix + "/" + suffix);
		Vector<HandleReference> hrefVec;
		try {
			hrefVec = parseJSONHandleValues(requestBody);
		} catch (IOException exc) {
			logger.error(exc);
			throw new WebApplicationException(exc, 400);
		}
		catch (HandleException exc) {
			logger.error(exc);
			throw new WebApplicationException(exc, 500);
		}
		if (hrefVec.size() != 1) {
			logger.debug("Must supply exactly 1 handle record. Size was: "
					+ hrefVec.size());
			return Response.status(400).build();
		}
		HandleReference hrefVec0 = hrefVec.get(0);
		if (hrefVec0.hasProperName()) {
			// providing a handle name in the json data is not allowed
			throw new WebApplicationException(400);
		}
		// inject path-specified handle name into work record
		hrefVec0.setHandle(handleref.getHandle());
		boolean created = createSingleHandle(hrefVec.get(0), ifMatch,
				ifNoneMatch);
		if (created)
			return Response.created(handleref.buildUri(uriInfo)).build();
		else
			return Response.noContent().build();
	}

	/**
	 * Creates a single Handle.
	 * 
	 * @param handleref
	 *            is assumed to be complete, i.e. full Handle name and value
	 *            record.
	 * @param ifMatch
	 *            value of the if-Match HTTP header or empty string.
	 * @param ifNoneMatch
	 *            value of the if-None-Match HTTP header or empty string.
	 * @return true if the Handle was newly created, false if it existed and was
	 *         successfully overwritten
	 */
	private boolean createSingleHandle(HandleReference handleref,
			String ifMatch, String ifNoneMatch) {
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
				if (ifMatch.equals("*")) {
					throw new WebApplicationException(412);
					// Handle does not exist, but If-Match header was set
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
				for (int i = 0; i < hvNew.size(); i++) {
					handlevalues[i] = hvNew.get(i).getHandleValue();
				}
				logger.debug("Creating Handle " + handleref.getHandle()
						+ " with values " + Arrays.toString(handlevalues));
				hsAdapter.createHandle(handleref.getHandle(), handlevalues);
				return true;
			} else {
				// handle exists already; check for precondition
				if (ifNoneMatch.equals("*")) {
					throw new WebApplicationException(412);
					// Handle exists, but If-None-Match header was set
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
				for (int i = 0; i < hvNew.size(); i++) {
					handlevalues[i] = hvNew.get(i).getHandleValue();
				}
				// add new handle values
				logger.debug("Adding values to Handle " + handleref.getHandle()
						+ ": " + Arrays.toString(handlevalues));
				hsAdapter.addHandleValues(handleref.getHandle(), handlevalues);
				return false;
			}
		} catch (HandleException exc) {
			logger.error(exc.getMessage(), exc);
			throw new WebApplicationException(exc, 500);
		}
	}

	protected Vector<HandleReference> parseJSONHandleValues(String requestData)
			throws JsonParseException, IOException, HandleException {
		/*
		 * The method will replace all current values on the handle with the
		 * given values (JSON encoded) plus an admin handle value
		 */
		JsonParser json = this.jsonFactory.createJsonParser(requestData);
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
						} else throw new JsonParseException("Invalid/unexpected field name: "+fieldName, json.getCurrentLocation());
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
					// values are ok; now assign HandleValue
					hvNew.add(hsAdapter.createHandleValue(index, type, data));
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

	/**
	 * Creates one or many Handles.
	 * 
	 * @param prefix
	 *            String with a prefix or null
	 * @param generator
	 *            Generator to use if prefix-only Handles are to be created
	 * @param requestBody
	 *            JSON request body. The contents of this highly affect the
	 *            behaviour of the method. In the body, there may be Handle
	 *            names fully specified, but also only partially if they bear
	 *            the given prefix, and also not at all, but then the prefix
	 *            parameter must be specified.
	 */
	private Response createHandles(String prefix,
			IdentifierNameGenerator generator, String requestBody) {
		// parse json; must contain handle values, but not different Handle
		// entries.
		Vector<HandleReference> hrMultiple;
		try {
			hrMultiple = parseJSONHandleValues(requestBody);
		} catch (Exception exc) {
			logger.error(exc);
			throw new WebApplicationException(exc, 400);
		}
		if (hrMultiple.size() > 1) {
			// Create multiple Handles
			if (prefix == null) {
				// no prefix; thus all HandleReferences must be complete
				for (HandleReference hr : hrMultiple) {
					if (!hr.hasProperName()) {
						throw new WebApplicationException(400);
					}
				}
			} else {
				// prefix specified; all HandleReferences must be either empty
				// or contain the given prefix
				for (HandleReference hr : hrMultiple) {
					if (!hr.hasNoName() && !hr.getPrefix().equals(prefix)) {
						throw new WebApplicationException(400);
					}
				}
			}
			// now create handles
			// TODO: multipart response
			for (HandleReference hr : hrMultiple) {
				if (hr.hasNoName()) {
					hr.setHandle(generator.generateName(prefix));
				}
				createSingleHandle(hr, "", "");
			}
			return Response.noContent().build();
		} else if (hrMultiple.size() == 1) {
			// Create a single Handle
			HandleReference singlehandle = hrMultiple.get(0);
			if ((prefix == null) && !singlehandle.hasProperName()) {
				// no prefix given, so generator cannot be used, but also no
				// name in json record given!
				throw new WebApplicationException(400);
			}
			if (!singlehandle.hasNoName()
					&& !singlehandle.getPrefix().equals(prefix)) {
				// prefix given in both json data and query, but they don't
				// match!
				throw new WebApplicationException(400);
			}
			// okay, all checks are done, no we can safely proceed
			if (!singlehandle.hasProperName()) {
				singlehandle.setHandle(generator.generateName(prefix));
			}
			boolean created = createSingleHandle(singlehandle, "", "");
			if (created) {
				return Response.created(singlehandle.buildUri(uriInfo)).build();
			} else
				return Response.noContent().build();
		} else {
			throw new WebApplicationException(400);
		}
	}
}
