import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;

public class BitfieldEvents {
	
	boolean showSampleNumbers;
	boolean showTimestamps;
	Map<Integer, EdgeMarker> edgeMarkers; // key is a sample number, value is an object describing all edge events at that sample number
	Map<Dataset.Bitfield, LevelMarker> levelMarkers; // key is a bitfield, value is a list of level markers for chosen states
	
	/**
	 * Create a list of all bitfield events that should be displayed on a chart.
	 * 
	 * @param showSampleNumbers      True if the sample number should be displayed at the top of each marker.
	 * @param showTimestamps         True if the date/time should be displayed at the top of each marker.
	 * @param bitfieldEdgeStates     Bitfield states to check for edge events.
	 * @param bitfieldLevelStates    Bitfield states to check for levels.
	 * @param minSampleNumber        Range of samples numbers to check (inclusive.)
	 * @param maxSampleNumber        Range of samples numbers to check (inclusive.)
	 */
	public BitfieldEvents(boolean showSampleNumbers, boolean showTimestamps, List<Dataset.Bitfield.State> bitfieldEdgeStates, List<Dataset.Bitfield.State> bitfieldLevelStates, int minSampleNumber, int maxSampleNumber) {
		
		this.showSampleNumbers = showSampleNumbers;
		this.showTimestamps = showTimestamps;
		edgeMarkers = new TreeMap<Integer, EdgeMarker>();
		levelMarkers = new TreeMap<Dataset.Bitfield, LevelMarker>();
		
		if(maxSampleNumber <= minSampleNumber)
			return;
		
		// check for edges
		for(Dataset.Bitfield.State state : bitfieldEdgeStates)
			for(Integer sampleNumber : state.sampleNumbers)
				if(sampleNumber >= minSampleNumber && sampleNumber <= maxSampleNumber)
					addEdge(sampleNumber, state.name, state.dataset.glColor);
		
		// check for levels
		for(Dataset.Bitfield.State state : bitfieldLevelStates) {
			int sampleNumber = minSampleNumber;
			int rangeMinSampleNumber = -1;
			int rangeMaxSampleNumber = maxSampleNumber;
			
			while(sampleNumber < maxSampleNumber) {
				
				// check for the beginning of a range
				if(state.bitfield.getStateAt(sampleNumber) == state.value) {
					rangeMinSampleNumber = sampleNumber; // begins immediately
				} else {
					for(int number : state.sampleNumbers)
						if(number > sampleNumber && number < maxSampleNumber) {
							rangeMinSampleNumber = number;
							break; // begins after sampleNumber but before maxSampleNumber
						}
				}
				if(rangeMinSampleNumber == -1)
					break; // no more ranges
				
				// check if the range ended somewhere on-screen
				for(Dataset.Bitfield.State s : state.bitfield.states)
					for(int number : s.sampleNumbers)
						if(number > rangeMinSampleNumber && number < maxSampleNumber && number < rangeMaxSampleNumber) {
							rangeMaxSampleNumber = number;
							break; // ends just before this change of state
						}
				
				// append this level to the Map
				addLevel(state.bitfield, new int[] {rangeMinSampleNumber, rangeMaxSampleNumber}, state.name, state.glColor);
				
				// let the loop check for another range
				sampleNumber = rangeMaxSampleNumber + 1;
				rangeMinSampleNumber = -1;
				rangeMaxSampleNumber = maxSampleNumber;
				
			}
			
		}
		
		
	}
	
	/**
	 * Adds a new event to the list.
	 * 
	 * @param sampleNumber    The moment in time associated with this event.
	 * @param name            A String describing this event.
	 * @param glColor         A float[r,g,b,a] color to show for this event.
	 */
	private void addEdge(int sampleNumber, String name, float[] glColor) {
		
		// check if an object already exists for this sample number
		if(edgeMarkers.containsKey(sampleNumber)) {
			EdgeMarker event = edgeMarkers.get(sampleNumber);
			event.text.add(name);
			event.glColors.add(glColor);
			return;
		}
		
		// an object does not exist, so create a new one
		edgeMarkers.put(sampleNumber, new EdgeMarker(sampleNumber, name, glColor));
		
	}
	
	/**
	 * Adds a new level to the list.
	 * 
	 * @param bitfield    Bitfield associated with this level.
	 * @param range       Range of sample numbers corresponding to this level, as a int[]{min,max}.
	 * @param label       String describing this level.
	 * @param glColor     A float[r,g,b,a] background color for this level.
	 */
	private void addLevel(Dataset.Bitfield bitfield, int[] range, String label, float[] glColor) {
		
		// find or create the marker
		LevelMarker marker;
		if(levelMarkers.containsKey(bitfield)) {
			marker = levelMarkers.get(bitfield);
		} else {
			marker = new LevelMarker(bitfield);
			levelMarkers.put(bitfield, marker);
		}
		
		// append this new state to the marker
		marker.ranges.add(range);
		marker.labels.add(label);
		marker.glColors.add(glColor);
		
	}
	
	/**
	 * Calculates the pixelX values for each edge marker, then returns the List of markers.
	 * 
	 * @param sampleNumberToPixelX    Function that takes a sample number and returns the corresponding pixelX value.
	 * @return                        List of all the edge markers.
	 */
	public List<EdgeMarker> getEdgeMarkers(IntFunction<Float> sampleNumberToPixelX) {
		
		List<EdgeMarker> list = new ArrayList<EdgeMarker>(edgeMarkers.values());
		for(EdgeMarker marker : list)
			marker.pixelX = sampleNumberToPixelX.apply(marker.sampleNumber);
		
		return list;
		
	}
	
	/**
	 * Calculates the pixelX values for each level marker, then returns the List of markers.
	 * 
	 * @param sampleNumberToPixelX    Function that takes a sample number and returns the corresponding pixelX value.
	 * @return                        Collection of all the level markers.
	 */
	public List<LevelMarker> getLevelMarkers(IntFunction<Float> sampleNumberToPixelX) {
		
		List<LevelMarker> list = new ArrayList<LevelMarker>(levelMarkers.values());
		for(LevelMarker marker : list)
			for(int[] range : marker.ranges)
				marker.pixelXranges.add(new float[] {sampleNumberToPixelX.apply(range[0]), sampleNumberToPixelX.apply(range[1])});
		
		return list;
		
	}

	/**
	 * Represents a single *sample number*, and contains all of the bitfield edges that occurred at that sample number.
	 */
	public class EdgeMarker {
		
		int sampleNumber;
		float pixelX;
		List<String> text;
		List<float[]> glColors;
		
		public EdgeMarker(int sampleNumber, String name, float[] glColor) {
			
			this.sampleNumber = sampleNumber;
			pixelX = 0;
			text = new ArrayList<String>();
			glColors = new ArrayList<float[]>();
			
			if(showSampleNumbers) {
				text.add("Sample " + sampleNumber);
				glColors.add(null);
			}
			
			if(showTimestamps) {
				String[] lines = SettingsController.formatTimestampToMilliseconds(DatasetsController.getTimestamp(sampleNumber)).split("\n");
				for(String line : lines) {
					text.add(line);
					glColors.add(null);
				}
			}
			
			text.add(name);
			glColors.add(glColor);
			
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
