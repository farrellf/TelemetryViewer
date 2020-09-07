import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DatasetsController {

	private static Map<Integer, Dataset> datasets = new TreeMap<Integer, Dataset>();
	private static AtomicInteger sampleCount = new AtomicInteger(0);
	private static StorageTimestamps timestamps = new StorageTimestamps("timestamps");
	private static long firstTimestamp = 0;
	
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
			removedDataset.floats.dispose();
		
		if(datasets.isEmpty()) {
			timestamps.clear();
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
			dataset.floats.dispose();
		datasets.clear();
		
		timestamps.clear();
		sampleCount.set(0);
		firstTimestamp = 0;
		
		for(Camera camera : cameras.keySet())
			camera.dispose();
		cameras.clear();
		
		CommunicationView.instance.allowExporting(false);
		OpenGLChartsView.instance.switchToLiveView();
		
	}
	
	public static void dispose() {
		
		removeAllDatasets();
		timestamps.dispose();
		
	}
	
	/**
	 * Removes all samples, timestamps and camera images, but does not remove the Dataset, Chart or Camera objects.
	 */
	public static void removeAllData() {
		
		for(Dataset dataset : getAllDatasets())
			dataset.floats.clear();
		
		timestamps.clear();
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
	 * Increments the sample count and saves the current timestamp.
	 * Call this function after all datasets have received a new value from a live connection.
	 */
	public static void incrementSampleCount() {
		
		long timestamp = System.currentTimeMillis();
		timestamps.appendTimestamp(timestamp);
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamp;
			if(!CommunicationController.getPort().equals(CommunicationController.PORT_FILE))
				CommunicationView.instance.allowExporting(true);
		}
		
	}
	
	/**
	 * Increments the sample count and sets the timestamp to a specific value.
	 * Call this function when importing a file, after all datasets have received a new value.
	 */
	public static void incrementSampleCountWithTimestamp(long timestamp) {
		
		timestamps.appendTimestamp(timestamp);
		
		int newSampleCount = sampleCount.incrementAndGet();
		if(newSampleCount == 1) {
			firstTimestamp = timestamp;
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
	 * @return    A List of the cameras that are/were used.
	 */
	public static Set<Camera> getExistingCameras() {
		
		return cameras.keySet();
		
	}
	
	public static int getClosestSampleNumberBefore(long timestamp, int maxSampleNumber) {
		
		return timestamps.getClosestSampleNumberBefore(timestamp, maxSampleNumber);
		
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
		
		return timestamps.getTimestamp(sampleNumber);
		
	}
	
	public static FloatBuffer getTimestampsBuffer(int firstSampleNumber, int lastSampleNumber, long plotMinX) {
		
		return timestamps.getTampstamps(firstSampleNumber, lastSampleNumber, plotMinX);
		
	}
	
	/**
	 * @return    The current number of samples stored in the Datasets.
	 */
	public static int getSampleCount() {
		
		return sampleCount.get();
		
	}
	
	/**
	 * Moves older samples and timestamps to disk.
	 */
	public static void flushOldValues() {
		
		for(Dataset d : getAllDatasets())
			d.floats.moveOldValuesToDisk();
		
		timestamps.moveOldValuesToDisk();
		
	}
	
}
