import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DatasetsController {

	public ConnectionTelemetry connection;
	private Map<Integer, Dataset> datasets = new TreeMap<Integer, Dataset>();
	private AtomicInteger sampleCount = new AtomicInteger(0);
	private StorageTimestamps timestamps;
	private long firstTimestamp = 0;
	private BinaryChecksumProcessor checksumProcessor = null;
	private int checksumProcessorOffset = -1;
	
	public static final BinaryFieldProcessor[]    binaryFieldProcessors    = new BinaryFieldProcessor[8];
	public static final BinaryChecksumProcessor[] binaryChecksumProcessors = new BinaryChecksumProcessor[1];
	static {
		binaryFieldProcessors[0] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "uint16 LSB First"; }
			@Override public int getByteCount()                            { return 2; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float) (((0xFF & buffer[0+offset]) << 0) |
					                                                                         ((0xFF & buffer[1+offset]) << 8)); }
		};
		binaryFieldProcessors[1] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "uint16 MSB First"; }
			@Override public int getByteCount()                            { return 2; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float) (((0xFF & buffer[1+offset]) << 0) |
			                                                                                 ((0xFF & buffer[0+offset]) << 8)); }
		};
		binaryFieldProcessors[2] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "int16 LSB First"; }
			@Override public int getByteCount()                            { return 2; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float)(short) (((0xFF & buffer[0+offset]) << 0) |
					                                                                                ((0xFF & buffer[1+offset]) << 8)); }
		};
		binaryFieldProcessors[3] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "int16 MSB First"; }
			@Override public int getByteCount()                            { return 2; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float)(short) (((0xFF & buffer[1+offset]) << 0) |
			                                                                                        ((0xFF & buffer[0+offset]) << 8)); }
		};
		binaryFieldProcessors[4] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "float32 LSB First"; }
			@Override public int getByteCount()                            { return 4; }
			@Override public float extractValue(byte[] buffer, int offset) { return Float.intBitsToFloat(((0xFF & buffer[0+offset]) <<  0) |
			                                                                                             ((0xFF & buffer[1+offset]) <<  8) |
			                                                                                             ((0xFF & buffer[2+offset]) << 16) |
			                                                                                             ((0xFF & buffer[3+offset]) << 24)); }
		};
		binaryFieldProcessors[5] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "float32 MSB First"; }
			@Override public int getByteCount()                            { return 4; }
			@Override public float extractValue(byte[] buffer, int offset) { return Float.intBitsToFloat(((0xFF & buffer[3+offset]) <<  0) |
                                                                                                         ((0xFF & buffer[2+offset]) <<  8) |
                                                                                                         ((0xFF & buffer[1+offset]) << 16) |
                                                                                                         ((0xFF & buffer[0+offset]) << 24)); }
		};
		binaryFieldProcessors[6] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "Bitfield: 8 Bits"; }
			@Override public int getByteCount()                            { return 1; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float) (0xFF & buffer[offset]); }
		};
		binaryFieldProcessors[7] = new BinaryFieldProcessor() {
			@Override public String toString()                             { return "uint8"; }
			@Override public int getByteCount()                            { return 1; }
			@Override public float extractValue(byte[] buffer, int offset) { return (float) (0xFF & buffer[offset]); }
		};
		binaryChecksumProcessors[0] = new BinaryChecksumProcessor() {
			@Override public String toString()                                  { return "uint16 Checksum LSB First"; }
			@Override public int getByteCount()                                 { return 2; }
			@Override public boolean testChecksum(byte[] bytes, int offset, int length) {
				
				// skip past the sync word
				offset++;
				length--;
				
				// sanity check: a 16bit checksum requires an even number of bytes
				if(length % 2 != 0)
					return false;
				
				// calculate the sum
				int wordCount = (length - getByteCount()) / 2; // 16bit words
				
				int sum = 0;
				int lsb = 0;
				int msb = 0;
				for(int i = 0; i < wordCount; i++) {
					lsb = 0xFF & bytes[offset + i*2];
					msb = 0xFF & bytes[offset + i*2 + 1];
					sum += (msb << 8 | lsb);
				}
				
				// extract the reported checksum
				lsb = 0xFF & bytes[offset + length - 2];
				msb = 0xFF & bytes[offset + length - 1];
				int checksum = (msb << 8 | lsb);
				
				// test
				sum %= 65536;
				if(sum == checksum)
					return true;
				else
					return false;
			}
		};
	}
	
	public DatasetsController(ConnectionTelemetry connection) {
		
		this.connection = connection;
		this.timestamps = new StorageTimestamps(connection);
		
	}
	
	/**
	 * @return    The number of fields in the data structure.
	 */
	public int getCount() {
		
		return datasets.size();
		
	}
	
	/**
	 * @param index    An index between 0 and DatasetsController.getCount()-1, inclusive.
	 * @return         The Dataset.
	 */
	public Dataset getByIndex(int index) {
		
		return (Dataset) datasets.values().toArray()[index];
		
	}
	
	/**
	 * @param location    CSV column number, or Binary packet byte offset. (Locations may be sparse.)
	 * @return            The Dataset, or null if it does not exist.
	 */
	public Dataset getByLocation(int location) {
		
		return datasets.get(location);

	}
	
	/**
	 * Creates and stores a new Dataset.
	 * 
	 * @param location             CSV column number, or Binary packet byte offset.
	 * @param processor            BinaryFieldProcessor for the raw samples in the Binary packet. (Ignored in CSV mode.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 * @return                     null on success, or a user-friendly String describing why the field could not be added.
	 */
	public String insert(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		if(connection.csvMode) {
			
			// can't overlap existing fields
			for(Dataset dataset : getList())
				if(dataset.location == location)
					return "Error: A field already exists at column " + location + ".";
			
			// add the field
			datasets.put(location, new Dataset(connection, location, processor, name, color, unit, conversionFactorA, conversionFactorB));
			return null;
			
		} else {
			
			// can't overlap the sync word
			if(location == 0)
				return "Error: Can not place a field that overlaps the sync word.";
			
			// can't overlap or follow the checksum
			if(checksumProcessor != null)
				if(location + processor.getByteCount() - 1 >= checksumProcessorOffset)
					return "Error: Can not place a field that overlaps the checksum or is placed after the checksum.";
			
			// can't overlap existing fields
			int proposedStartByte = location;
			int proposedEndByte = proposedStartByte + processor.getByteCount() - 1;
			for(Dataset dataset : getList()) {
				int existingStartByte = dataset.location;
				int existingEndByte = existingStartByte + dataset.processor.getByteCount() - 1;
				if(proposedStartByte >= existingStartByte && proposedStartByte <= existingEndByte)
					return "Error: Can not place a field that overlaps an existing field."; // starting inside existing range
				if(proposedEndByte >= existingStartByte && proposedEndByte <= existingEndByte)
					return "Error: Can not place a field that overlaps an existing field."; // ending inside existing range
				if(existingStartByte >= proposedStartByte && existingEndByte <= proposedEndByte)
					return "Error: Can not place a field that overlaps an existing field."; // encompassing existing range
			}
			
			// add the field
			datasets.put(location, new Dataset(connection, location, processor, name, color, unit, conversionFactorA, conversionFactorB));
			return null;
			
		}
		
	}
	
	/**
	 * Removes a specific dataset.
	 * If any charts reference it, those charts will also be removed.
	 * If no datasets exist any more, timestamps will also be removed.
	 * 
	 * @param location    CSV column number, or Binary packet byte offset.
	 * @return            null on success, or a user-friendly String describing why the field could not be removed.
	 */
	public String remove(int location) {
		
		// ensure the configure panel isn't open
		ConfigureView.instance.close();
		
		// can't remove what doesn't exist
		Dataset specifiedDataset = getByLocation(location);
		if(specifiedDataset == null)
			return "Error: No field exists at location " + location + ".";
		
		// remove charts containing the dataset
		List<PositionedChart> chartsToRemove = new ArrayList<PositionedChart>();
		for(PositionedChart chart : ChartsController.getCharts())
			if(chart.datasets.contains(specifiedDataset))
				chartsToRemove.add(chart);
		for(PositionedChart chart : chartsToRemove)
			ChartsController.removeChart(chart);
		
		datasets.remove(location);
		specifiedDataset.floats.dispose();
		
		// remove timestamps if nothing is left
		if(datasets.isEmpty()) {
			timestamps.clear();
			sampleCount.set(0);
			firstTimestamp = 0;
			
			CommunicationView.instance.redraw();
			OpenGLChartsView.instance.switchToLiveView();
		}
		
		// success
		return null;
		
	}
	
	/**
	 * Removes all associated charts, Datasets and Cameras.
	 */
	public void removeAll() {
		
		for(Dataset dataset : getList())
			remove(dataset.location);
		
		// also remove cameras
		for(Camera camera : cameras.keySet())
			camera.dispose();
		cameras.clear();
		
		removeChecksum();
		
	}
	
	public void dispose() {
		
		removeAll();
		timestamps.dispose();
		
	}
	
	/**
	 * Removes all samples, timestamps and camera images, but does not remove the Dataset, Chart or Camera objects.
	 */
	public void removeAllData() {
		
		for(Dataset dataset : getList())
			dataset.floats.clear();
		
		timestamps.clear();
		sampleCount.set(0);
		firstTimestamp = 0;
		
		for(Camera camera : cameras.keySet())
			camera.dispose();
		
		CommunicationView.instance.redraw();
		OpenGLChartsView.instance.switchToLiveView();
		
	}
	
	/**
	 * @return    The Datasets.
	 */
	public List<Dataset> getList() {
		
		return new ArrayList<Dataset>(datasets.values());
		
	}
	
	/**
	 * Adds a checksum field to the data structure if possible.
	 * 
	 * @param location     Binary packet byte offset.
	 * @param processor    The type of checksum field.
	 * @return             null on success, or a user-friendly String describing why the checksum field could not be added.
	 */
	public String insertChecksum(int location, BinaryChecksumProcessor processor) {
		
		if(connection.csvMode)
			return "Error: CSV mode does not support checksums.";

		if(checksumProcessor != null)
			return "Error: A checksum field already exists.";
		
		if(location == 0)
			return "Error: A checksum field can not overlap with the sync word.";
		
		for(Dataset d : getList())
			if(location <= d.location + d.processor.getByteCount() - 1)
				return "Error: A checksum field can not be placed in front of existing fields.";
		
		if((location - 1) % processor.getByteCount() != 0)
			return "Error: The checksum must be aligned. The number of bytes before the checksum, not counting the sync word, must be a multiple of " + processor.getByteCount() + " for this checksum type.";
		
		// add the checksum processor
		checksumProcessor = processor;
		checksumProcessorOffset = location;
		
		// no errors
		return null;
		
	}
	
	/**
	 * Removes the checksum field from the data structure if possible.
	 * 
	 * @return    null on success, or a user-friendly String describing why the checksum field could not be removed.
	 */
	public String removeChecksum() {
		
		if(connection.csvMode)
			return "Error: CSV mode does not support checksums.";
		
		if(checksumProcessor == null)
			return "Error: There is no checksum processor to remove.";
		
		// remove the checksum processor
		checksumProcessor = null;
		checksumProcessorOffset = -1;
		return null;
		
	}
	
	/**
	 * @return    The checksum processor.
	 */
	public BinaryChecksumProcessor getChecksumProcessor() {
		
		return checksumProcessor;
		
	}
	
	/**
	 * @return    The location (byte offset) of the checksum in the telemetry packet.
	 */
	public int getChecksumProcessorOffset() {
		
		return checksumProcessorOffset;
		
	}
	
	/**
	 * Tests if a telemetry packet contains a valid checksum.
	 * 
	 * @param packet          The packet, WITHOUT the sync word.
	 * @param packetLength    Length of the packet, WITHOUT the sync word.
	 * @return                True if the checksum is good or not used, false if the checksum failed.
	 */
	public boolean checksumPassed(byte[] packet, int offset, int packetLength) {
		
		if(checksumProcessor == null) // no checksum
			return true;
		
		if(checksumProcessor.testChecksum(packet, offset, packetLength)) // checksum passed
			return true;
		
		// checksum failed
		StringBuilder message = new StringBuilder(1024);
		message.append("A corrupt telemetry packet was received:\n");
		for(int i = 0; i < packetLength; i++)
			message.append(String.format("%02X ", packet[offset + i]));
		NotificationsController.showFailureForSeconds(message.toString(), 5, false);
		return false;
		
	}
	
	/**
	 * @return    The first unoccupied CSV column number or byte offset, or -1 if they are all occupied.
	 */
	public int getFirstAvailableLocation() {
		
		if(connection.csvMode) {
			
			// the packet is empty
			if(getList().isEmpty())
				return 0;
			
			// check for a sparse layout
			int maxOccupiedLocation = 0;
			for(Dataset d : getList())
				if(d.location > maxOccupiedLocation)
					maxOccupiedLocation = d.location;
			for(int i = 0; i < maxOccupiedLocation; i++)
				if(getByLocation(i) == null)
					return i;
			
			// layout is not sparse
			return maxOccupiedLocation + 1;
			
		} else {
			
			// the packet is empty
			if(getList().isEmpty())
				return 1;
			
			// if a checksum exists, check for an opening before it
			if(checksumProcessor != null) {
				for(int i = 1; i < checksumProcessorOffset; i++) {
					boolean available = true;
					for(Dataset d : getList())
						if(i >= d.location && i <= d.location + d.processor.getByteCount() - 1)
							available = false;
					if(available)
						return i;
				}
				return -1; // all bytes before the checksum are occupied
			}
			
			// if no checksum, check for a sparse layout
			int maxOccupiedLocation = 0;
			for(Dataset d : getList())
				if(d.location + d.processor.getByteCount() - 1 > maxOccupiedLocation)
					maxOccupiedLocation = d.location + d.processor.getByteCount() - 1;
			for(int i = 1; i < maxOccupiedLocation; i++) {
				boolean available = true;
				for(Dataset d : getList())
					if(i >= d.location && i <= d.location + d.processor.getByteCount() - 1)
						available = false;
				if(available)
					return i;
			}
			
			// layout is not sparse
			return maxOccupiedLocation + 1;
			
		}
		
	}
	
	/**
	 * Increments the sample count and saves the current timestamp.
	 * Call this function after all datasets have received a new value from a live connection.
	 */
	public void incrementSampleCount() {
		
		long timestamp = System.currentTimeMillis();
		timestamps.appendTimestamp(timestamp);
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamp;
			CommunicationView.instance.redraw();
		}
		
	}
	
	/**
	 * Increments the sample count by an entire block, and processes any bitfields in that block.
	 * Call this function after all datasets have received a block of values from a live connection.
	 */
	public void incrementSampleCountBlock() {
		
		long timestamp = System.currentTimeMillis();
		timestamps.fillBlock(timestamp);
		
		for(Dataset d : getList()) {
			if(d.isBitfield) {
				int sampleNumber = sampleCount.get();
				for(int i = 0; i < StorageFloats.BLOCK_SIZE; i++)
					for(Dataset.Bitfield bitfield : d.bitfields)
						bitfield.processValue((int) d.getSample(sampleNumber), sampleNumber++);
			}
		}
		
		boolean wasZero = sampleCount.get() == 0;
		sampleCount.addAndGet(StorageFloats.BLOCK_SIZE);
		if(wasZero) {
			firstTimestamp = timestamp;
			CommunicationView.instance.redraw();
		}
		
	}
	
	/**
	 * Increments the sample count and sets the timestamp to a specific value.
	 * Call this function when importing a file, after all datasets have received a new value.
	 */
	public void incrementSampleCountWithTimestamp(long timestamp) {
		
		timestamps.appendTimestamp(timestamp);
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamp;
			CommunicationView.instance.redraw();
		}
		
	}
	
	private Map<Camera, Boolean> cameras = new HashMap<Camera, Boolean>(); // the Boolean is true if the camera is currently owned by a chart
	
	/**
	 * Obtains ownership of a camera, preventing other charts from using it.
	 * 
	 * @param name       The camera name or URL.
	 * @param isMjpeg    True if using MJPEG-over-HTTP.
	 * @return           The camera object, or null if the camera is already owned or does not exist.
	 */
	public Camera acquireCamera(String name, boolean isMjpeg) {
		
		// check if the camera is already known
		for(Map.Entry<Camera, Boolean> entry : cameras.entrySet())
			if(entry.getKey().name.equals(name))
				if(entry.getValue() == false) {
					entry.setValue(true);
					return entry.getKey(); // the camera was previously owned but is currently available
				} else {
					return null; // the camera is currently owned by another chart
				}
		
		// the camera is not already known
		Camera c = new Camera(name, isMjpeg);
		cameras.put(c, true);
		return c; // the camera has been acquired
		
	}
	
	/**
	 * Releases ownership of a camera, allowing another chart to acquire it.
	 * 
	 * @param c    The camera.
	 */
	public void releaseCamera(Camera c) {
		
		c.disconnect();
		if(c.getFrameCount() == 0) {
			c.dispose();
			cameras.remove(c);
		}
		
		for(Map.Entry<Camera, Boolean> entry : cameras.entrySet())
			if(entry.getKey() == c)
				entry.setValue(false);
		
	}
	
	/**
	 * @return    A List of the cameras that are/were used.
	 */
	public Set<Camera> getExistingCameras() {
		
		return cameras.keySet();
		
	}
	
	public int getClosestSampleNumberAtOrBefore(long timestamp, int maxSampleNumber) {
		
		return timestamps.getClosestSampleNumberAtOrBefore(timestamp, maxSampleNumber);
		
	}
	
	/**
	 * @return    The timestamp for sample number 0, or 0 if there are no samples.
	 */
	public long getFirstTimestamp() {
		
		return firstTimestamp;
		
	}
	
	/**
	 * Gets the timestamp for one specific sample.
	 * 
	 * @param sampleNumber    Which sample to check.
	 * @return                The corresponding UNIX timestamp.
	 */
	public long getTimestamp(int sampleNumber) {
		
		if(sampleNumber < 0)
			return 0;
		
		return timestamps.getTimestamp(sampleNumber);
		
	}
	
	public FloatBuffer getTimestampsBuffer(int firstSampleNumber, int lastSampleNumber, long plotMinX) {
		
		return timestamps.getTampstamps(firstSampleNumber, lastSampleNumber, plotMinX);
		
	}
	
	/**
	 * @return    The current number of samples stored in the Datasets.
	 */
	public int getSampleCount() {
		
		return sampleCount.get();
		
	}
	
	/**
	 * Moves older samples and timestamps to disk.
	 */
	public void flushOldValues() {
		
		for(Dataset d : getList())
			d.floats.moveOldValuesToDisk();
		
		timestamps.moveOldValuesToDisk();
		
	}
	
	public interface BinaryFieldProcessor {
		
		/**
		 * @return    Description for this field's data type. This will be displayed in the DataStructureBinaryView, and written to any saved settings files.
		 */
		public String toString();
		
		/**
		 * @return    Number of bytes used by this field.
		 */
		public int getByteCount();
		
		/**
		 * @param buffer    Unprocessed telemetry packet, without the sync word.
		 * @param offset    Where in the buffer this value starts.
		 * @return          The corresponding number, as a float. This number has *not* been scaled by the Dataset conversion factors.
		 */
		public float extractValue(byte[] buffer, int offset);

	}
	
	public interface BinaryChecksumProcessor {
		
		/**
		 * @return    Description for this type of checksum processor. This will be displayed in the DataStructureBinaryView, and written to any saved settings files.
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
		public boolean testChecksum(byte[] bytes, int offset, int length);

	}
	
}
