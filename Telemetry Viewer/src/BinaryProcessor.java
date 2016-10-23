/**
 * An interface for objects that can convert raw serial port data (bytes) into numbers. This is only used in the Binary packet mode.
 * The serial port receiver thread uses these to convert the received bytes into numbers that can be inserted into Datasets.
 */
public interface BinaryProcessor {
	
	/**
	 * @return    Description for this data type. This will be displayed in the DataStructureWindow.
	 */
	public String toString();
	
	/**
	 * @return    Number of bytes used by this data type.
	 */
	public int getByteCount();
	
	/**
	 * @param rawBytes    Unprocessed bytes that were received from the serial port.
	 * @return            The corresponding number, as a double. The number has NOT been scaled by the Dataset conversion factors.
	 */
	public float extractValue(byte[] rawBytes);

}
