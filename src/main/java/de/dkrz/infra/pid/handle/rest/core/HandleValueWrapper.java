package de.dkrz.infra.pid.handle.rest.core;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;

/**
 * Read-only wrapper around properties of HandleValue to provide a properly
 * filtered and formatted JSON representation.
 * 
 * @author tobiasweigel
 * 
 */
@XmlRootElement
public class HandleValueWrapper {

	@XmlTransient
	private HandleValue handleValue;

	/**
	 * Constructor.
	 * 
	 * @param handleValue
	 */
	public HandleValueWrapper(HandleValue handleValue) {
		this.handleValue = handleValue;
	}
	
	/**
	 * Constructor when used in unmarshalling the JavaBean. It is expected that index, type and data will be set
	 * immediately after this constructor has been called.
	 */
	public HandleValueWrapper() {
		super();
		handleValue = new HandleValue();
	}

	@XmlElement(name="idx")
	public int getIndex() {
		return handleValue.getIndex();
	}
	
	public void setIndex(int index) {
		handleValue.setIndex(index);
	}
	
	@XmlElement(name="type")
	public String getType() {
		return handleValue.getTypeAsString();
	}
	
	public void setType(String t) {
		handleValue.setType(t.getBytes());
	}
	
	@XmlElement(name="data")
	public byte[] getData() {
		return handleValue.getData();
	}
	
	public void setData(byte[] bytes) {
		handleValue.setData(bytes);
	}

	@XmlElement(name="parsed_data")
	public String getParsedData() {
		return handleValue.getDataAsString();
	}
	
	public void setParsedData(String parsed_data) {
		handleValue.setData(Util.encodeString(parsed_data));
	}

	@XmlElement(name="timestamp")
	public String getTimestamp() {
		return handleValue.getTimestampAsString();
	}
	
	@XmlElement(name="ttl_type")
	public byte getTtl_type() {
		return handleValue.getTTLType();
	}
	
	@XmlElement(name="ttl")
	public int getTtl() {
		return handleValue.getTTL();
	}
		
	@XmlElement(name="refs")
	public String getRefs() {
		String s = "[";
		for (ValueReference ref: handleValue.getReferences()) {
			s += ref.toString()+", ";
		}
		return s+"]";
	}
	
	@XmlElement(name="privs")
	public String getPrivs() {
		return handleValue.getPermissionString();
	}

	@XmlTransient
	public HandleValue getHandleValue() {
		return handleValue;
	}

}
