package de.dkrz.infra.pid.handle.rest.core;

import java.util.Collection;
import java.util.Vector;

import net.handle.hdllib.HandleValue;

/**
 * Reference to a Handle or moer specifically, to one or more of its index
 * values.
 * 
 * @author tobiasweigel
 * 
 */
public class HandleReference {

	protected String handle;
	protected int[] indexes;
	protected Vector<HandleValue> values = new Vector<HandleValue>();

	public HandleReference(String handle, int[] indexes) {
		this.handle = handle;
		if (indexes == null) {
			this.indexes = new int[0];
		} else
			this.indexes = indexes;
	}
	
	public HandleReference(String handle) {
		this.handle = handle;
		this.indexes = new int[0];
	}

	/**
	 * Analyses the given request path segment and returns the implied reference
	 * to a Handle and potentially also to specific Handle index values, if
	 * allowed.
	 * 
	 * @param pathsegment
	 * @param allowIndexes
	 *            If true, index specification may be allowed. If false and
	 *            indexes are specified, the method will fail with an
	 *            IllegalArgumentException.
	 * @return a HandleReference object
	 * @throws IllegalArgumentException
	 *             if the path segment is malformed, empty or index
	 *             specification given but disallowed
	 */
	public static HandleReference fromRequestPath(String pathsegment,
			boolean allowIndexes) {
		if (pathsegment.startsWith("/"))
			pathsegment = pathsegment.substring(1);
		if (pathsegment.length() == 0) {
			throw new IllegalArgumentException("Bad Request: No Handle given.");
		}
		int[] indexes = null;
		// detect key index prefixed to Handle prefix (key:prefix/suffix)
		if ((pathsegment.indexOf(":") >= 0)
				&& (pathsegment.indexOf(":") < pathsegment.indexOf("/"))) {
			if (!allowIndexes) {
				throw new IllegalArgumentException(
						"Index specification not allowed for this operation!");
			}
			indexes = new int[1];
			String subs = pathsegment.substring(0, pathsegment.indexOf(":"));
			try {
				indexes[0] = Integer.parseInt(subs);
			} catch (NumberFormatException exc) {
				throw new IllegalArgumentException("Invalid handle index: "
						+ subs);
			}
			pathsegment = pathsegment.substring(pathsegment.indexOf(":") + 1);
		}
		return new HandleReference(pathsegment, indexes);
	}

	public void setHandle(String handle) {
		this.handle = handle;
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
	
	public boolean isPrefixOnly() {
		return !handle.contains("/");
	}
	
	public String getPrefix() {
		return handle.substring(0, handle.indexOf("/"));
	}
	
	public void addValues(Collection<HandleValue> v) {
		values.addAll(v);
	}

	public Vector<HandleValue> getValues() {
		return values;
	}

	public boolean hasProperName() {
		return handle.contains("/");
	}
	
}