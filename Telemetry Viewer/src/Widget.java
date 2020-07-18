import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Widget {

	Map<Component, String> widgets; // key = Swing widget, value = MigLayout component constraints
	
	public Widget() {
		
		widgets = new LinkedHashMap<Component, String>();
		
	}

	/**
	 * Updates the widget and chart based on a settings file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	public abstract void importState(CommunicationController.QueueOfLines lines);
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	public abstract String[] exportState();
	
}
