/**
 * Objects that implement this interface are used to evaluate the fields of a binary packet.
 * They take the raw bytes of some data type and output a float.
 */
public interface BinaryFieldProcessor {
	
	/**
	 * @return    Description for this field's data type. This will be displayed in the DataStructureWindow, and written to any saved layout files.
	 */
	public String toString();
	
	/**
	 * @return    Number of bytes used by this field.
	 */
	public int getByteCount();
	
	/**
	 * @param bytes    Unprocessed bytes that were received from the serial port.
	 * @return         The corresponding number, as a float. This number has *not* been scaled by the Dataset conversion factors.
	 */
	public float extractValue(byte[] bytes);

}
