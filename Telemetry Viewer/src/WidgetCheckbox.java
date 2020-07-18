import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

public class WidgetCheckbox extends Widget {
	
	String label;
	JCheckBox checkbox;
	Consumer<Boolean> handler;
	
	/**
	 * A widget that lets the user check or uncheck a checkbox.
	 * 
	 * @param labelText       Label to show at the right of the checkbox.
	 * @param isChecked       If the checkbox should default to checked.
	 * @param eventHandler    Will be notified when the checkbox changes.
	 */
	public WidgetCheckbox(String labelText, boolean isChecked, Consumer<Boolean> eventHandler) {
		
		super();
		
		label = labelText;
		handler = eventHandler;
		
		checkbox = new JCheckBox(label);
		checkbox.setSelected(isChecked);
		checkbox.addActionListener(event -> handler.accept(checkbox.isSelected()));
		
		widgets.put(new JLabel(""), "");
		widgets.put(checkbox, "span 3, growx");
		
		handler.accept(checkbox.isSelected());
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		boolean checked = ChartUtils.parseBoolean(lines.remove(), label.trim().toLowerCase() + " = %b");
		
		// update the widget
		checkbox.setSelected(checked);
		
		// update the chart
		handler.accept(checkbox.isSelected());
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		return new String[] {
			label.trim().toLowerCase() + " = " + checkbox.isSelected()
		};
		
	}

}
