package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import net.handle.api.HSAdapter;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import de.dkrz.infra.pid.handle.rest.core.HandleReference;

/**
 * Manages all GETs for JSON content.
 * 
 * @author tobiasweigel
 * 
 */
public class RootJSONRetriever {
	
	private static final Logger logger = Logger.getLogger(RootJSONRetriever.class);
	
	protected JsonFactory jsonFactory;
	protected HSAdapter hsAdapter;
	
	public RootJSONRetriever(HSAdapter hsAdapter) {
		super();
		this.jsonFactory = new JsonFactory();
		this.hsAdapter = hsAdapter;
	}

	public void exec(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		// determine path
		String path = req.getPathInfo();
		if (path == null) {
			resp.sendError(404);
		} else if (path.startsWith("/handles/")) {
			// extract handle reference info
			String handleinfo = path.substring(9);
			HandleReference handleref = HandleReference.fromRequestPath(
					handleinfo, true);
			this.lookupHandle(handleref, resp);
		} else {
			// No matching path
			resp.sendError(404);
		}
	}
	
	private void lookupHandle(HandleReference handleref, HttpServletResponse resp) throws IOException {
		try {
			this.logger.debug("Trying to look up Handle " + handleref.getHandle());
			HandleValue[] allhv = hsAdapter.resolveHandle(
					handleref.getHandle(), null, handleref.getIndexes());
			// encode all values in JSON
			JsonGenerator json = jsonFactory.createJsonGenerator(resp
					.getWriter());
			json.writeStartArray();
			for (HandleValue hv : allhv) {
				json.writeStartObject();
				json.writeStringField("idx",
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
			logger.error("Unknown Handle: "+handleref.getHandle(), exc);
			return;
		} catch (Exception exc) {
			resp.sendError(500,
					"Error while processing the request: " + exc.getMessage());
			logger.error("Error during GET request.", exc);
			return;
		}		
	}

}
