package de.dkrz.infra.pid.handle.rest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.handle.api.HSAdapter;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;

/**
 * Implementation of the Handle API main interface with Java data structures for
 * testing purposes.
 * 
 * @author tobiasweigel
 * 
 */
public class InMemoryStore implements HSAdapter {

	private Map<String, Map<Integer, HandleValue>> storage = new HashMap<String, Map<Integer, HandleValue>>();

	@Override
	public void addHandleValues(String handle, HandleValue[] values)
			throws HandleException {
		if (!storage.containsKey(handle))
			throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
		// create safe copy of given values
		for (HandleValue hv : values) {
			if (storage.get(handle).containsKey(hv.getIndex()))
				throw new HandleException(HandleException.INVALID_VALUE);
			storage.get(handle).put(hv.getIndex(), hv.duplicate());
		}
	}

	@Override
	public HandleValue createAdminValue(String adminHandle, int keyIndex,
			int index) throws HandleException {
		// somewhat quick and dirty - the proper data would be different, but we
		// don't have to care here for testing...
		return new HandleValue(keyIndex, "HS_ADMIN".getBytes(),
				adminHandle.getBytes());
	}

	@Override
	public void createHandle(String handle, HandleValue[] values)
			throws HandleException {
		if (storage.containsKey(handle)) 
			throw new HandleException(HandleException.HANDLE_ALREADY_EXISTS);
		Map<Integer, HandleValue> handlerecord = new HashMap<Integer, HandleValue>();
		for (HandleValue hv: values) {
			handlerecord.put(hv.getIndex(), hv.duplicate());
		}
		storage.put(handle, handlerecord);
	}

	@Override
	public HandleValue createHandleValue(int index, String type, String data)
			throws HandleException {
		return new HandleValue(index, type.getBytes(), data.getBytes());
	}

	@Override
	public void deleteHandle(String handle) throws HandleException {
		if (!storage.containsKey(handle))
			throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
		storage.remove(handle);
	}

	@Override
	public void deleteHandleValues(String handle, HandleValue[] values)
			throws HandleException {
		if (!storage.containsKey(handle))
			throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
		Map<Integer, HandleValue> handlerecord = storage.get(handle);
		for (HandleValue hv: values) {
			handlerecord.remove(hv.getIndex());
		}
	}

	@Override
	public HandleValue[] resolveHandle(String handle, String[] types,
			int[] indexes) throws HandleException {
		if (!storage.containsKey(handle)) 
			throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
		// naive strategy: retrieve all and filter afterwards
		Map<Integer, HandleValue> handlerecord = new HashMap<Integer, HandleValue>(storage.get(handle));
		if (types != null) {
			for (String t: types) {
				for (Iterator<Integer> iter = handlerecord.keySet().iterator(); iter.hasNext();) {
					int key = iter.next();
					HandleValue hv = handlerecord.get(key);
					if (!hv.getType().equals(t))
						iter.remove();
				}
			}
		}
		if (indexes != null) {
			for (int index: indexes) {
				handlerecord.remove(index);
			}
		}
		HandleValue[] result = new HandleValue[handlerecord.size()];
		result = handlerecord.values().toArray(result);
		return result;
	}

	@Override
	public void setTcpTimeout(int newTcpTimeout) {
		return;
	}

	@Override
	public int getTcpTimeout() {
		return 0;
	}

	@Override
	public void setUseUDP(boolean useUDP) {
		return;
	}

	@Override
	public void updateHandleValues(String handle, HandleValue[] values)
			throws HandleException {
		if (!storage.containsKey(handle))
			throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
		// create safe copy of given values
		for (HandleValue hv : values) {
			storage.get(handle).put(hv.getIndex(), hv.duplicate());
		}
	}

}
