package de.dkrz.infra.pid.handle.rest;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.naming.spi.DirectoryManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Servlet for the root directory of all handle operations (/handles/). Provides
 * mechanism for creating one or multiple Handles (batch processing).
 * 
 * @author tobiasweigel
 * 
 */
public class HandleRootServlet extends HttpServlet {

	private final static Logger logger = Logger
			.getLogger(HandleRootServlet.class);

	protected Configuration freemarkerConfig;
	
	private RootHTMLRetriever rootHTMLRetriever;
	private RootJSONRetriever rootJSONRetriever;

	public HandleRootServlet() throws IOException {
		super();
		this.freemarkerConfig = new Configuration();
		this.freemarkerConfig.setClassForTemplateLoading(this.getClass(),
				"/templates");

		this.freemarkerConfig.setObjectWrapper(new DefaultObjectWrapper());
		this.rootHTMLRetriever = new RootHTMLRetriever(this.freemarkerConfig);
		this.rootJSONRetriever = new RootJSONRetriever();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// TODO Auto-generated method stub
		logger.debug("POST");
		super.doPost(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("GET");
		if ((req.getHeader("Accept").indexOf("text/html") > 0)
				|| (req.getHeader("Accept").indexOf("application/xhtml+xml") > 0)) {
			logger.debug("RESPONSE: HTML");
			this.rootHTMLRetriever.exec(req, resp);
		}
		else if (req.getHeader("Accept").indexOf("application/json") > 0) {
			logger.debug("RESPONSE: JSON");
			this.rootJSONRetriever.exec(req, resp);
		}
		else {
			resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
		}
	}

}
