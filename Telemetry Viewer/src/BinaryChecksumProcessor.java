/**
 * Objects that implement this interface are used to evaluate the checksum of a binary packet.
 */
public interface BinaryChecksumProcessor {
	
	/**
	 * @return    Description for this type of checksum processor. This will be displayed in the DataStructureWindow, and written to any saved layout files.
	 */
	public String toString();
	
	/**
	 * @return    Number of bytes occupied by the checksum field.
	 */
	public int getByteCount();
	
	/**
	 * @param bytes    All of the packet bytes *after* (not including!) the sync word.
	 * @return         True if the checksum is valid, false otherwise.
	 */
	public boolean testChecksum(byte[] bytes, int length);

}
