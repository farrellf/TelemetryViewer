import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

public class BitfieldEvents {
	
	boolean showSampleNumbers;
	boolean showTimestamps;
	Map<Integer, EdgeMarker> edgeMarkers; // key is a sample number, value is an object describing all edge events at that sample number
	Map<Dataset.Bitfield, LevelMarker> levelMarkers; // key is a bitfield, value is a list of level markers for the chosen states
	
	/**
	 * Creates a list of all bitfield events that should be displayed on a chart.
	 * 
	 * @param showSampleNumbers    True if the sample number should be displayed at the top of each marker.
	 * @param showTimestamps       True if the date/time should be displayed at the top of each marker.
	 * @param datasets             Bitfield edges and levels to check for.
	 * @param minSampleNumber      Range of samples numbers to check (inclusive.)
	 * @param maxSampleNumber      Range of samples numbers to check (inclusive.)
	 */
	public BitfieldEvents(boolean showSampleNumbers, boolean showTimestamps, DatasetsInterface datasets, int minSampleNumber, int maxSampleNumber) {
		
		this.showSampleNumbers = showSampleNumbers;
		this.showTimestamps = showTimestamps;
		edgeMarkers = new TreeMap<Integer, EdgeMarker>();
		levelMarkers = new TreeMap<Dataset.Bitfield, LevelMarker>();
		
		if(maxSampleNumber <= minSampleNumber)
			return;
		
		// check for edge events
		datasets.forEachEdge(minSampleNumber, maxSampleNumber, (state, eventSampleNumber) -> {
			if(edgeMarkers.containsKey(eventSampleNumber)) {
				// a marker already exists for this sample number, so append to it
				EdgeMarker event = edgeMarkers.get(eventSampleNumber);
				event.text.add(state.name);
				event.glColors.add(state.glColor);
			} else {
				// a marker does not exist, so create a new one
				edgeMarkers.put(eventSampleNumber, new EdgeMarker(state, eventSampleNumber));
			}
		});
		
		// check for levels
		datasets.forEachLevel(minSampleNumber, maxSampleNumber, (state, range) -> {
			LevelMarker marker;
			if(levelMarkers.containsKey(state.bitfield)) {
				marker = levelMarkers.get(state.bitfield);
			} else {
				marker = new LevelMarker(state.bitfield);
				levelMarkers.put(state.bitfield, marker);
			}
			marker.ranges.add(new int[] {range[0], range[1]});
			marker.labels.add(state.name);
			marker.glColors.add(state.glColor);
		});
		
	}
	
	/**
	 * Calculates the pixelX values for each edge marker, then returns the List of markers.
	 * 
	 * @param sampleNumberToPixelX    Function that takes a Connection and sample number, then returns the corresponding pixelX value.
	 * @return                        List of all the edge markers.
	 */
	public List<EdgeMarker> getEdgeMarkers(BiFunction<ConnectionTelemetry, Integer, Float> sampleNumberToPixelX) {
		
		List<EdgeMarker> list = new ArrayList<EdgeMarker>(edgeMarkers.values());
		for(EdgeMarker marker : list)
			marker.pixelX = sampleNumberToPixelX.apply(marker.connection, marker.sampleNumber);
		
		return list;
		
	}
	
	/**
	 * Calculates the pixelX values for each level marker, then returns the List of markers.
	 * 
	 * @param sampleNumberToPixelX    Function that takes a Connection and sample number, then returns the corresponding pixelX value.
	 * @return                        List of all the level markers.
	 */
	public List<LevelMarker> getLevelMarkers(BiFunction<ConnectionTelemetry, Integer, Float> sampleNumberToPixelX) {
		
		List<LevelMarker> list = new ArrayList<LevelMarker>(levelMarkers.values());
		for(LevelMarker marker : list)
			for(int[] range : marker.ranges)
				marker.pixelXranges.add(new float[] {sampleNumberToPixelX.apply(marker.bitfield.dataset.connection, range[0]), sampleNumberToPixelX.apply(marker.bitfield.dataset.connection, range[1])});
		
		return list;
		
	}

	/**
	 * Represents a single *sample number*, and contains all of the bitfield edges that occurred at that sample number.
	 */
	public class EdgeMarker {
		
		ConnectionTelemetry connection;
		int sampleNumber;
		float pixelX;
		List<String> text;
		List<float[]> glColors;
		
		public EdgeMarker(Dataset.Bitfield.State state, int sampleNumber) {
			
			this.connection = state.dataset.connection;
			this.sampleNumber = sampleNumber;
			pixelX = 0;
			text = new ArrayList<String>();
			glColors = new ArrayList<float[]>();
			
			if(showSampleNumbers) {
				text.add("Sample " + sampleNumber);
				glColors.add(null);
			}
			
			if(showTimestamps) {
				String[] lines = SettingsController.formatTimestampToMilliseconds(connection.datasets.getTimestamp(sampleNumber)).split("\n");
				for(String line : lines) {
					text.add(line);
					glColors.add(null);
				}
			}
			
			text.add(state.name);
			glColors.add(state.glColor);
			
		}
		
	}
	
	/**
	 * Represents a single *bitfield*, and contains all of that its levels that should be displayed on screen.
	 */
	public class LevelMarker {
		
		Dataset.Bitfield bitfield;  // this object contains a List of all the level markers for this bitfield
		List<String> labels;        // name of the state
		List<float[]> glColors;     // color for the state
		List<int[]> ranges;         // sample number range for the state
		List<float[]> pixelXranges; // corresponding pixelX values for those sample number ranges
		
		public LevelMarker(Dataset.Bitfield bitfield) {
			
			this.bitfield = bitfield;
			labels = new ArrayList<String>();
			glColors = new ArrayList<float[]>();
			ranges = new ArrayList<int[]>();
			pixelXranges = new ArrayList<float[]>(ranges.size());
			
		}
		
	}
	
}
