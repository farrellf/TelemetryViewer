import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class WidgetEnumeration extends JPanel {
	
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
		
		handler = eventHandler;
		
		combobox = new JComboBox<String>(choices);
		combobox.addActionListener(event -> handler.accept((String) combobox.getSelectedItem()));
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel(labelText + ": "));
		add(combobox);
		
	}
	
	/**
	 * Sets the combobox to a specific selection.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param selection    The selection.
	 */
	public void setSelection(String selection) {
		
		for(int i = 0; i < combobox.getItemCount(); i++)
			if(combobox.getItemAt(i).equals(selection))
				combobox.setSelectedIndex(i);
		
	}
	
	/**
	 * Ensures the widget is in a consistent state, then calls the event handler.
	 */
	public void sanityCheck() {
		
		// nothing to check, just call the event handler
		handler.accept((String) combobox.getSelectedItem());
		
	}

}
