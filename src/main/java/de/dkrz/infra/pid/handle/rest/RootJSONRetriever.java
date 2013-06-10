package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Manages all GETs for JSON content.
 * 
 * @author tobiasweigel
 *
 */
public class RootJSONRetriever {
	
	public void exec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.sendError(404);
	}

}
