package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Servlet for operations on an individual Handle. Can return and modify Handle
 * Values. Cannot create or delete Handles.
 * 
 * @author tobiasweigel
 * 
 */
public class IndividualHandleServlet extends HttpServlet {
	
	private final static Logger logger = Logger.getLogger(IndividualHandleServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("GET");

		// TODO Auto-generated method stub
		super.doGet(req, resp);
	}

}
