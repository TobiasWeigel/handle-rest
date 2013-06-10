package de.dkrz.infra.pid.handle.rest;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Manages all GETs for HTML content.
 * 
 * @author tobiasweigel
 *
 */
public class RootHTMLRetriever {
	
	protected Configuration freemarkerConfig;
	
	private static final Logger logger = Logger.getLogger(RootHTMLRetriever.class);

	public RootHTMLRetriever(Configuration freemarkerConfig) {
		super();
		this.freemarkerConfig = freemarkerConfig;
	}
	
	public void exec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// determine path
		String path = req.getPathInfo();
		try {
			if ((path == null) || (path.equals("/"))) {
				this.render(resp.getWriter(), "index.ftl");
			}
			else {
				// No matching path
				resp.sendError(404);
			}
		}
		catch (TemplateException exc) {
			this.logger.error("Freemarker template error", exc);
			resp.sendError(500, "Template error.");
		}
	}
	
	protected void render(Writer writer, String templateName) throws TemplateException, IOException {
		// invoke Freemarker
		Template template = this.freemarkerConfig.getTemplate("index.ftl");
		Map basemodel = new HashMap();
		basemodel.put("title", "Handle REST Web Interface");
		template.process(basemodel, writer);
		writer.flush();
	}
	
}
