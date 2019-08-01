import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * An object to store all bitfield events that will be displayed on a chart.
 * These events can come from multiple datasets, multiple bitfields, and multiple points in time.
 */
public class BitfieldEvents {
	
	List<EventsAtSampleNumber> events = new ArrayList<EventsAtSampleNumber>();
	
	/**
	 * Adds a new event to the list.
	 * 
	 * @param sampleNumber    The moment in time associated with this event.
	 * @param name            A String describing this event.
	 * @param color           A Color to show for this event.
	 */
	public void add(int sampleNumber, String name, Color color) {
		
		// check if an object already exists for this sample number
		for(EventsAtSampleNumber event : events)
			if(event.sampleNumber == sampleNumber) {
				event.names.add(name);
				event.colors.add(color);
				return;
			}
		
		// an object does not exist, so create a new one
		events.add(new EventsAtSampleNumber(sampleNumber, name, color));
		
	}
	
	/**
	 * @return    A List of all the events.
	 */
	public List<EventsAtSampleNumber> get() {
		
		return events;
		
	}

	/**
	 * Represents a single moment in time, and all of the bitfield events that occurred at that time.
	 */
	public class EventsAtSampleNumber {
		
		int sampleNumber = 0;
		float pixelX = 0;
		List<String> names = new ArrayList<String>();
		List<Color> colors = new ArrayList<Color>();
		
		public EventsAtSampleNumber(int sampleNumber, String name, Color color) {
			this.sampleNumber = sampleNumber;
			this.names.add(name);
			this.colors.add(color);
		}
		
	}
	
}
