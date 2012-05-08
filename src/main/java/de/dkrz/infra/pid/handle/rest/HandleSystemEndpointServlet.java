package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());
	private HSAdapter hsAdapter;

	public HandleSystemEndpointServlet(HandleAuthorizationInfo authInfo)
			throws HandleException {
		this.hsAdapter = HSAdapterFactory.newInstance(
				authInfo.getAdminHandle(), authInfo.getKeyIndex(),
				authInfo.getPrivateKey(), authInfo.getCipher());
	}
	
	/**
	 * Analyses the given URI and returns the implied reference to a Handle and potentially also to specific Handle 
	 * index values.
	 * 
	 * @param uri
	 * @return a HandleReference object
	 * @throws IllegalArgumentException if the URI is malformed
	 */
	protected static HandleReference determineHandleReference(String uri) {
		String[] parts = uri.split("/", 3);
		if (parts.length < 3) {
			throw new IllegalArgumentException("Bad Request: No handle given.");
		}
		String handle = parts[2];
		int[] indexes = null;
		// detect key index prefixed to Handle prefix (key:prefix/suffix)
		if ((handle.indexOf(":") >= 0) && (handle.indexOf(":") < handle.indexOf("/"))) {
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
			JsonFactory jsonfact = new JsonFactory();
			JsonGenerator json = jsonfact.createJsonGenerator(resp.getWriter());
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
		}
	}
	
	public static boolean isStringType(String typeAsString) {
		return (typeAsString.equals("URL") || typeAsString.equals("URN") || typeAsString.equals("EMAIL") || typeAsString.equals("HS_ALIAS"));
	}
	

}
