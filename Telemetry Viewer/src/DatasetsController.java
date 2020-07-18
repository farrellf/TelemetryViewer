import java.awt.Color;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.jogamp.common.nio.Buffers;

public class DatasetsController {

	private static Map<Integer, Dataset> datasets = new TreeMap<Integer, Dataset>();
	private static AtomicInteger sampleCount = new AtomicInteger(0);
	
	public static final int SLOT_SIZE = 1048576; // 1M values
	public static final int SLOT_COUNT = Integer.MAX_VALUE / SLOT_SIZE + 1; // +1 to round up
	/**
	 * Timestamps are stored in an array of Slots. Each Slot contains 1M values, and may be flushed to disk if RAM runs low.
	 */
	private static class Slot {
		
		public volatile boolean inRam = true;
		public volatile boolean flushing = false;
		private volatile long[] values = new long[SLOT_SIZE];
		private String pathOnDisk = "cache/" + this.toString();
		
		public void moveToDisk() {
			
			if(!inRam || flushing)
				return;
			
			flushing = true;
			
			new Thread(() -> {
				try {
					ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(pathOnDisk));
					stream.writeObject(values);
					stream.close();
					inRam = false;
					values = null;
					flushing = false;
				} catch(Exception e) {
					e.printStackTrace();
				}
			}).start();
			
		}
		
		private void copyToRam() {
			
			try {
				ObjectInputStream stream = new ObjectInputStream(new FileInputStream(pathOnDisk));
				values = (long[]) stream.readObject();
				stream.close();
				inRam = true;
				flushIfNecessary();
			} catch(Exception e) {
				e.printStackTrace();
			}
			
		}
		
		public void removeFromDisk() {
			
			try { Files.deleteIfExists(Paths.get(pathOnDisk)); } catch (IOException e) {}
			
		}
		
		public void setValue(int index, long value) {
			
			if(!inRam)
				copyToRam();
			values[index] = value;
			
		}
		
		public long getValue(int index) {
			
			if(!inRam)
				copyToRam();
			return values[index];
			
		}
		
	}
	private static Slot[] timestamps = new Slot[SLOT_COUNT];
	private static long firstTimestamp = 0;
	
	// for keeping track of what slots not to flush to disk
	private static int minimumSampleNumberOnScreen = -1;
	private static int maximumSampleNumberOnScreen = -1;
	private static int minimumSampleNumberExporting = -1;
	private static int maximumSampleNumberExporting = -1;
	
	/**
	 * @return    The number of fields in the data structure.
	 */
	public static int getDatasetsCount() {
		
		return datasets.size();
		
	}
	
	/**
	 * @param index    An index between 0 and getDatasetsCount()-1, inclusive.
	 * @return         The Dataset.
	 */
	public static Dataset getDatasetByIndex(int index) {
		
		return (Dataset) datasets.values().toArray()[index];
		
	}
	
	/**
	 * @param location    CSV column number, or Binary packet byte offset. Locations may be sparse.
	 * @return            The Dataset, or null if it does not exist.
	 */
	public static Dataset getDatasetByLocation(int location) {
		
		return datasets.get(location);

	}
	
	/**
	 * Creates and stores a new Dataset. If a Dataset already exists for the same location, the new Dataset will replace it.
	 * 
	 * @param location             CSV column number, or Binary packet byte offset.
	 * @param processor            BinaryProcessor for the raw samples in the Binary packet. (Ignored in CSV mode, use null.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 */
	public static void insertDataset(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		datasets.put(location, new Dataset(location, processor, name, color, unit, conversionFactorA, conversionFactorB));
		
	}
	
	/**
	 * Removes a specific dataset.
	 * 
	 * @return    true on success, false if nothing existed there.
	 */
	public static boolean removeDataset(int location) {
		
		PositionedChart[] charts = ChartsController.getCharts().toArray(new PositionedChart[0]);
		for(PositionedChart chart : charts)
			if(chart.datasets != null)
				for(Dataset dataset : chart.datasets)
					if(dataset.location == location)
						ChartsController.removeChart(chart);
		
		Dataset removedDataset = datasets.remove(location);
		if(removedDataset != null)
			for(Dataset.Slot slot : removedDataset.slots)
				if(slot != null)
					slot.removeFromDisk();
		
		if(datasets.isEmpty()) {
			for(Slot timestamp : timestamps)
				if(timestamp != null)
					timestamp.removeFromDisk();
			timestamps = new Slot[SLOT_COUNT];
			
			sampleCount.set(0);
			firstTimestamp = 0;
			
			CommunicationView.instance.allowExporting(false);
			OpenGLChartsView.instance.switchToLiveView();
		}
		
		if(removedDataset == null)
			return false;
		else
			return true;
		
	}
	
	/**
	 * Removes all charts, Datasets and Cameras.
	 */
	public static void removeAllDatasets() {
		
		ChartsController.removeAllCharts();
		
		for(Dataset dataset : getAllDatasets())
			for(Dataset.Slot slot : dataset.slots)
				if(slot != null)
					slot.removeFromDisk();
		datasets.clear();
		
		for(Slot timestamp : timestamps)
			if(timestamp != null)
				timestamp.removeFromDisk();
		timestamps = new Slot[SLOT_COUNT];
		
		sampleCount.set(0);
		firstTimestamp = 0;
		
		for(Camera camera : cameras.keySet())
			camera.dispose();
		cameras.clear();
		
		CommunicationView.instance.allowExporting(false);
		OpenGLChartsView.instance.switchToLiveView();
		
	}
	
	/**
	 * Removes all samples, timestamps and camera images, but does not remove the Dataset, Chart or Camera objects.
	 */
	public static void removeAllData() {
		
		for(Dataset dataset : getAllDatasets())
			for(Dataset.Slot slot : dataset.slots)
				if(slot != null)
					slot.removeFromDisk();
		
		for(Slot timestamp : timestamps)
			if(timestamp != null)
				timestamp.removeFromDisk();
		timestamps = new Slot[SLOT_COUNT];
		
		sampleCount.set(0);
		firstTimestamp = 0;
		
		for(Camera camera : cameras.keySet())
			camera.dispose();
		
		CommunicationView.instance.allowExporting(false);
		OpenGLChartsView.instance.switchToLiveView();
		
	}
	
	/**
	 * @return    The Datasets.
	 */
	public static Dataset[] getAllDatasets() {
		
		return datasets.values().toArray(new Dataset[datasets.size()]);
		
	}
	
	/**
	 * Increments the sample count. Call this function after all datasets have received a new value.
	 */
	public static void incrementSampleCount() {
		
		int currentSize = getSampleCount();
		int slotNumber = currentSize / SLOT_SIZE;
		int slotIndex  = currentSize % SLOT_SIZE;
		if(slotIndex == 0) {
			timestamps[slotNumber] = new Slot();
			flushIfNecessary();
		}
		timestamps[slotNumber].setValue(slotIndex, System.currentTimeMillis());
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamps[0].getValue(0);
			if(!CommunicationController.getPort().equals(CommunicationController.PORT_FILE))
				CommunicationView.instance.allowExporting(true);
		}
		
	}
	
	/**
	 * Increments the sample count and sets the timestamp to a specific value. Call this function when importing a file, after all datasets have received a new value.
	 */
	public static void incrementSampleCountWithTimestamp(long timestamp) {
		
		int currentSize = getSampleCount();
		int slotNumber = currentSize / SLOT_SIZE;
		int slotIndex  = currentSize % SLOT_SIZE;
		if(slotIndex == 0) {
			timestamps[slotNumber] = new Slot();
			flushIfNecessary();
		}
		timestamps[slotNumber].setValue(slotIndex, timestamp);
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamps[0].getValue(0);
			if(!CommunicationController.getPort().equals(CommunicationController.PORT_FILE))
				CommunicationView.instance.allowExporting(true);
		}
		
	}
	
	private static Map<Camera, Boolean> cameras = new HashMap<Camera, Boolean>(); // the Boolean is true if the camera is currently owned by a chart
	
	/**
	 * Obtains ownership of a camera, preventing other charts from using it.
	 * 
	 * @param name       The camera name or URL.
	 * @param isMjpeg    True if using MJPEG-over-HTTP.
	 * @return           The camera object, or null if the camera is already owned or does not exist.
	 */
	public static Camera acquireCamera(String name, boolean isMjpeg) {
		
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
	public static void releaseCamera(Camera c) {
		
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
	 * Removes a Camera from the Map of acquired camera.
	 * This method should be called when
	 * 
	 * @param c    The camera.
	 */
	public static void removeCamera(Camera c) {
		
		cameras.remove(c);
		
	}
	
	/**
	 * @return    A List of the cameras that are/were used.
	 */
	public static Set<Camera> getExistingCameras() {
		
		return cameras.keySet();
		
	}
	
	/**
	 * @return    The timestamp for sample number 0, or 0 if there are no samples.
	 */
	public static long getFirstTimestamp() {
		
		return firstTimestamp;
		
	}
	
	/**
	 * Gets the timestamp for one specific sample.
	 * 
	 * @param sampleNumber    Which sample to check.
	 * @return                The corresponding UNIX timestamp.
	 */
	public static long getTimestamp(int sampleNumber) {
		
		if(sampleNumber < 0)
			return 0;
		
		int slotNumber = sampleNumber / SLOT_SIZE;
		int slotIndex  = sampleNumber % SLOT_SIZE;
		return timestamps[slotNumber].getValue(slotIndex);
		
	}
	
	public static FloatBuffer getTimestampsBuffer(int firstSampleNumber, int lastSampleNumber, long plotMinX) {
		
		FloatBuffer buffer = Buffers.newDirectFloatBuffer(lastSampleNumber - firstSampleNumber + 1);
		
		if(firstSampleNumber < 0)
			return buffer;
		
		for(int i = firstSampleNumber; i <= lastSampleNumber; i++)
			buffer.put((float) (getTimestamp(i) - plotMinX));
		
		buffer.rewind();
		return buffer;
		
	}
	
	/**
	 * @return    The current number of samples stored in the Datasets.
	 */
	public static int getSampleCount() {
		
		return sampleCount.get();
		
	}
	
	/**
	 * Prevents slots from being flushed to disk because they are actively being used to draw charts on screen.
	 * 
	 * @param minimumSampleNumber    Minimum sample number shown on screen.
	 * @param maximumSampleNumber    Maximum sample number shown on screen.
	 */
	public static void dontFlushRangeOnScreen(int minimumSampleNumber, int maximumSampleNumber) {
		
		minimumSampleNumberOnScreen = minimumSampleNumber;
		maximumSampleNumberOnScreen = maximumSampleNumber;
		
	}
	
	/**
	 * Prevents slots from being flushed to disk because they are actively being exported to a CSV file.
	 * 
	 * @param minimumSampleNumber    Minimum sample number shown on screen.
	 * @param maximumSampleNumber    Maximum sample number shown on screen.
	 */
	public static void dontFlushRangeBeingExported(int minimumSampleNumber, int maximumSampleNumber) {
		
		minimumSampleNumberExporting = minimumSampleNumber;
		maximumSampleNumberExporting = maximumSampleNumber;
		
	}
	
	/**
	 * If more than half of the max heap size is used, flush the oldest unneeded slot to disk.
	 */
	public static void flushIfNecessary() {
		
		// determine how much space is available, and how much is used by the timestamps and datasets
		long maxHeapSize = Runtime.getRuntime().maxMemory();
		long currentSize = 0;
		for(int i = 0; i < timestamps.length; i++)
			if(timestamps[i] == null)
				break;
			else if(timestamps[i].inRam)
				currentSize += SLOT_SIZE * 8;
		currentSize += (long) Math.ceil(getDatasetsCount() / 2.0) * currentSize;
		
		// if using more than half of the available space, flush one slot
		if(currentSize > maxHeapSize / 2) {
			
			// figure out which slots to NOT flush
			int firstProtectedSlotA = minimumSampleNumberOnScreen / SLOT_SIZE;
			int lastProtectedSlotA = maximumSampleNumberOnScreen / SLOT_SIZE;
			int firstProtectedSlotB = minimumSampleNumberExporting / SLOT_SIZE;
			int lastProtectedSlotB = maximumSampleNumberExporting / SLOT_SIZE;
			
			for(int i = 0; i < timestamps.length; i++) {
				
				// don't flush protected slots
				if(i >= firstProtectedSlotA && i <= lastProtectedSlotA)
					continue;
				if(i >= firstProtectedSlotB && i <= lastProtectedSlotB)
					continue;
				
				// if connected, don't flush the last slot
				if(CommunicationController.isConnected() && i == getSampleCount() / SLOT_SIZE)
					return;
				
				// stop checking if we reached the end
				if(timestamps[i] == null)
					return;
				
				// move the timestamp array, and corresponding dataset arrays, to disk
				if(timestamps[i].inRam) {
					timestamps[i].moveToDisk();
					for(int j = 0; j < getDatasetsCount(); j++)
						getDatasetByIndex(j).slots[i].moveToDisk();
					return;
				}
				
			}
			
		}
		
	}
	
}
