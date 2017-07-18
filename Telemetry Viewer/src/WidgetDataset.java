import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class WidgetDataset extends JPanel {
	
	JComboBox<Dataset> combobox;
	Consumer<Dataset[]> handler;
	
	
	/**
	 * A widget that lets the user pick one dataset from a drop-down list.
	 * 
	 * @param eventHandler    Will be notified when the chosen dataset changes.
	 */
	public WidgetDataset(String labelText, Consumer<Dataset[]> eventHandler) {
		
		super();
		
		handler = eventHandler;
		
		combobox = new JComboBox<Dataset>(Controller.getAllDatasets());
		combobox.addActionListener(event -> handler.accept(new Dataset[] {(Dataset) combobox.getSelectedItem()}));
		
		setLayout(new GridLayout(1, 2, 10, 10));
		add(new JLabel(labelText + ": "));
		add(combobox);
		
		handler.accept(new Dataset[] {(Dataset) combobox.getSelectedItem()});
		
	}
	
	/**
	 * Sets the combobox to a specific dataset.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param dataset    The dataset.
	 */
	public void setDataset(Dataset dataset) {
		
		combobox.setSelectedItem(dataset);
		
	}
	
	/**
	 * Ensures the widget is in a consistent state, then calls the event handler.
	 */
	public void sanityCheck() {
		
		if(combobox.getSelectedIndex() < 0)
			combobox.setSelectedIndex(0);
		
		handler.accept(new Dataset[] {(Dataset) combobox.getSelectedItem()});
		
	}

}
