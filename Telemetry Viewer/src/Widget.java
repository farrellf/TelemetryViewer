import javax.swing.JPanel;

@SuppressWarnings("serial")
public abstract class Widget extends JPanel {
	
	public Widget() {
		
		super();
		
	}

	/**
	 * Updates the widget and chart based on settings from a layout file.
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
