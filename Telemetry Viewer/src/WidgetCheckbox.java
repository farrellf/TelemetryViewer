import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class WidgetCheckbox extends JPanel {
	
	JCheckBox checkbox;
	Consumer<Boolean> handler;
	
	/**
	 * A widget that lets the user check or uncheck a checkbox.
	 * 
	 * @param label           Label to show at the right of the checkbox.
	 * @param isChecked       If the checkbox should default to checked.
	 * @param eventHandler    Will be notified when the checkbox changes.
	 */
	public WidgetCheckbox(String label, boolean isChecked, Consumer<Boolean> eventHandler) {
		
		super();
		
		handler = eventHandler;
		
		checkbox = new JCheckBox(label);
		checkbox.setSelected(isChecked);
		checkbox.addActionListener(event -> handler.accept(checkbox.isSelected()));
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel(""));
		add(checkbox);
		
		handler.accept(checkbox.isSelected());
		
	}
	
	/**
	 * Sets the checkbox to a particular state.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param checked    True for checked, false for unchecked.
	 */
	public void setChecked(boolean checked) {
		
		checkbox.setSelected(checked);
		
	}
	
	/**
	 * Ensures the widget is in a consistent state, then calls the event handler.
	 */
	public void sanityCheck() {
		
		// nothing to check, just call the event handler
		handler.accept(checkbox.isSelected());
		
	}

}
