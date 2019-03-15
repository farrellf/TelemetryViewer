import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class WidgetEnumeration extends Widget {
	
	String label;
	JComboBox<String> combobox;
	Consumer<String> handler;
	
	/**
	 * A widget that lets the user pick one choice from a drop-down list.
	 * 
	 * @param labelText       Label to show at the left of the combobox.
	 * @param choices         A String[] of choices.
	 * @param eventHandler    Will be notified when the chosen value changes.
	 */
	public WidgetEnumeration(String labelText, String[] choices, Consumer<String> eventHandler) {
		
		super();
		
		label = labelText;
		handler = eventHandler;
		
		combobox = new JComboBox<String>(choices);
		combobox.addActionListener(event -> handler.accept((String) combobox.getSelectedItem()));
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel(labelText + ": "));
		add(combobox);
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(Controller.QueueOfLines lines) {

		// parse the text
		String enumeration = ChartUtils.parseString(lines.remove(), label.trim().toLowerCase() + " = %s");
		
		// update the widget
		for(int i = 0; i < combobox.getItemCount(); i++)
			if(combobox.getItemAt(i).equals(enumeration))
				combobox.setSelectedIndex(i);
		
		// update the chart
		handler.accept((String) combobox.getSelectedItem());
		
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
