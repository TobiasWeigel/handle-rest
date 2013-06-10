package de.dkrz.infra.pid.handle.rest.core;

/**
 * Reference to a Handle or moer specifically, to one or more of its index
 * values.
 * 
 * @author tobiasweigel
 * 
 */
public class HandleReference {

	protected final String handle;
	protected int[] indexes;

	public HandleReference(String handle, int[] indexes) {
		this.handle = handle;
		if (indexes == null) {
			this.indexes = new int[0];
		} else
			this.indexes = indexes;
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
}