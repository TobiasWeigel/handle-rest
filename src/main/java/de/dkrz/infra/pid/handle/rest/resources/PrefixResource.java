package de.dkrz.infra.pid.handle.rest.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;

import net.handle.api.HSAdapter;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

import de.dkrz.infra.pid.handle.rest.ApplicationContext;
import de.dkrz.infra.pid.handle.rest.core.HandleReference;
import de.dkrz.infra.pid.handle.rest.core.HandleValueWrapper;

public class PrefixResource {

	@Context
	protected UriInfo uriInfo;
	@Context
	protected Request request;
	protected String prefix;

	public PrefixResource(UriInfo uriInfo, Request request, String prefix) {
		super();
		this.uriInfo = uriInfo;
		this.request = request;
		this.prefix = prefix;
	}

	@GET
	//@Produces(MediaType.APPLICATION_JSON)
	public String get() {
		return "Hello World, this is prefix resource!";
	}
/*
	@GET
	@Path("{suffix}") @Produces(MediaType.APPLICATION_JSON)
	public HandleValueWrapper[] getIndividualHandle(
			@PathParam("suffix") String suffix) throws HandleException {
		HSAdapter hsAdapter = ApplicationContext.getInstance().getHSAdapter();
		HandleValue[] allhv = hsAdapter.resolveHandle(prefix + "/" + suffix,
				null, null);
		HandleValueWrapper[] result = new HandleValueWrapper[allhv.length];
		for (int i = 0; i < allhv.length; i++) {
			result[i] = new HandleValueWrapper(allhv[i]);
		}
		return result;
	}*/

}
