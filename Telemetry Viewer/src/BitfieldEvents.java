import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An object to store all bitfield events that will be displayed on a chart.
 * These events can come from multiple datasets, multiple bitfields, and multiple points in time.
 */
public class BitfieldEvents {
	
	Map<Integer, EventsAtSampleNumber> events = new TreeMap<Integer, EventsAtSampleNumber>();
	
	/**
	 * Adds a new event to the list.
	 * 
	 * @param sampleNumber    The moment in time associated with this event.
	 * @param name            A String describing this event.
	 * @param glColor         A float[r,g,b,a] color to show for this event.
	 */
	public void add(int sampleNumber, String name, float[] glColor) {
		
		// check if an object already exists for this sample number
		if(events.containsKey(sampleNumber)) {
			EventsAtSampleNumber event = events.get(sampleNumber);
			event.text.add(name);
			event.glColors.add(glColor);
			return;
		}
		
		// an object does not exist, so create a new one
		events.put(sampleNumber, new EventsAtSampleNumber(sampleNumber, name, glColor));
		
	}
	
	/**
	 * @return    A Collection of all the events.
	 */
	public List<EventsAtSampleNumber> get() {
		
		return new ArrayList<EventsAtSampleNumber>(events.values());
		
	}

	/**
	 * Represents a single moment in time, and all of the bitfield events that occurred at that time.
	 */
	public class EventsAtSampleNumber {
		
		int sampleNumber = 0;
		float pixelX = 0;
		List<String> text = new ArrayList<String>();
		List<float[]> glColors = new ArrayList<float[]>();
		
		public EventsAtSampleNumber(int sampleNumber, String name, float[] glColor) {
			this.sampleNumber = sampleNumber;
			
			text.add("Sample " + sampleNumber);
			glColors.add(null);
			String[] lines = SettingsController.formatTimestampToMilliseconds(DatasetsController.getTimestamp(sampleNumber)).split("\n");
			for(String line : lines) {
				text.add(line);
				glColors.add(null);
			}
			
			text.add(name);
			glColors.add(glColor);
		}
		
	}
	
}
