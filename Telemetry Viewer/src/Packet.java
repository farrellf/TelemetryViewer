import java.awt.Color;
import java.io.InputStream;

import javax.swing.JPanel;

/**
 * The packet (either BinaryPacket or CsvPacket) manages the data structure and contains the Datasets.
 */
public abstract class Packet {
	
	public volatile boolean dataStructureDefined = false;
	public BinaryChecksumProcessor checksumProcessor = null;
	public int checksumProcessorOffset = -1;
	public Thread thread = null;
	
	/**
	 * Adds a field to the data structure if possible.
	 * 
	 * @param location             CSV column number or Binary packet byte offset.
	 * @param processor            BinaryProcessor for the raw samples in the Binary packet. Use null for CSV packets.
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 * @return                     null on success, or a user-friendly String describing why the field could not be added.
	 */
	public abstract String insertField(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB);
	
	/**
	 * Removes a field from the data structure if possible.
	 * 
	 * @param location    The field at this CSV column number or Binary packet byte offset will be removed.
	 * @return            null on success, or a user-friendly String describing why the field could not be removed.
	 */
	public abstract String removeField(int location);
	
	/**
	 * Removes all fields from the data structure.
	 */
	public abstract void reset();

	
	/**
	 * Adds a checksum field to the data structure if possible. This is not currently supported for CSVs.
	 * 
	 * @param location     CSV column number or Binary packet byte offset.
	 * @param processor    The type of checksum field.
	 * @return             null on success, or a user-friendly String describing why the checksum field could not be added.
	 */
	public abstract String insertChecksum(int location, BinaryChecksumProcessor processor);
	
	/**
	 * Removes the checksum field from the data structure if possible.
	 * 
	 * @return    null on success, or a user-friendly String describing why the checksum field could not be removed.
	 */
	public abstract String removeChecksum();
	
	/**
	 * @return    The byte offset or column number of the checksum, or -1 if disabled.
	 */
	public final int getChecksumProcessorLocation() {
		
		return checksumProcessorOffset;
		
	}
	
	/**
	 * @return    The checksum processor index, or -1 if disabled.
	 */
	public final int getChecksumProcessorIndex() {
		
		if(checksumProcessor == null)
			return -1;
		
		BinaryChecksumProcessor[] processors = PacketBinary.getBinaryChecksumProcessors();
		for(int i = 0; i < processors.length; i++)
			if(checksumProcessor.toString().equals(processors[i].toString()))
				return i;
		
		// should never get here
		return -1;
		
	}
	
	public abstract JPanel getDataStructureGui();
	public abstract void startReceivingData(InputStream stream);
	public abstract void stopReceivingData();

}
