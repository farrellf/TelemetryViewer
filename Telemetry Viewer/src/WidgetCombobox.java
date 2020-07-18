import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;

public class WidgetCombobox extends Widget {
	
	String label;
	JComboBox<String> combobox;
	Consumer<String> handler;
	
	/**
	 * A widget that lets the user select one option from a combobox.
	 * 
	 * @param textLabel       Label to show at the left of the combobox.
	 * @param values          A String[] of options to show in the combobox.
	 * @param eventHandler    Will be notified when the combobox selection changes.
	 */
	public WidgetCombobox(String textLabel, String[] values, Consumer<String> eventHandler) {
		
		super();
		
		label = textLabel;
		handler = eventHandler;
		
		combobox = new JComboBox<String>(values);
		combobox.addActionListener(event -> eventHandler.accept(combobox.getSelectedItem().toString()));
		
		widgets.put(new JLabel(label + ": "), "");
		widgets.put(combobox, "span 3, growx");
		
		eventHandler.accept(combobox.getSelectedItem().toString());
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		String text = ChartUtils.parseString(lines.remove(), label.trim().toLowerCase() + " = %s");
		
		// update the widget
		boolean found = false;
		for(int i = 0; i < combobox.getItemCount(); i++) {
			if(combobox.getItemAt(i).equals(text)) {
				combobox.setSelectedIndex(i);
				found = true;
				break;
			}
		}
		
		if(!found)
			throw new AssertionError("Invalid option.");
		
		// update the chart
		handler.accept(combobox.getSelectedItem().toString());
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		return new String[] {
			label.trim().toLowerCase() + " = " + combobox.getSelectedItem().toString()
		};
		
	}

}
