import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class WidgetDatasets extends JPanel {
	
	Map<Dataset, JCheckBox> datasetsMap;
	Consumer<Dataset[]> handler;
		
	/**
	 * A widget that lets the user pick zero or more datasets from a list of checkboxes.
	 * 
	 * @param eventHandler    Will be notified when the chosen datasets change.
	 */
	public WidgetDatasets(Consumer<Dataset[]> eventHandler) {
		
		super();
		
		Dataset[] datasets = Controller.getAllDatasets();
		datasetsMap = new LinkedHashMap<Dataset, JCheckBox>();
		
		setLayout(new GridLayout(datasets.length, 2, 10, 10));
		add(new JLabel("Datasets: "));
		
		for(int i = 0; i < datasets.length; i++) {
			
			JCheckBox checkbox = new JCheckBox(datasets[i].name);
			checkbox.addActionListener(event -> notifyHandler());
			
			datasetsMap.put(datasets[i], checkbox);
			
			if(i != 0)
				add(new JLabel(""));
			add(checkbox);
			
		}
		
		handler = eventHandler;
		notifyHandler();
		
	}
	
	/**
	 * Determines which datasets have been selected, and notifies the handler.
	 */
	private void notifyHandler() {
		
		// determine how many datasets were chosen
		int datasetsCount = 0;
		for(JCheckBox checkbox : datasetsMap.values())
			if(checkbox.isSelected())
				datasetsCount++;
		
		// create an array of them
		Dataset[] datasets = new Dataset[datasetsCount];
		int i = 0;
		for(Entry<Dataset, JCheckBox> entry : datasetsMap.entrySet())
			if(entry.getValue().isSelected())
				datasets[i++] = entry.getKey();
		
		handler.accept(datasets);
		
	}
	
	/**
	 * Sets the checkboxes to specific datasets.
	 * This should be called after importing a chart since the widget will not be in sync with the chart's state.
	 * 
	 * @param datasets    The datasets.
	 */
	public void setDatasets(Dataset[] datasets) {
		
		// uncheck all
		for(JCheckBox checkbox : datasetsMap.values())
			checkbox.setSelected(false);
	
		// check the specific datasets
		for(Dataset dataset : datasets)
			datasetsMap.get(dataset).setSelected(true);
		
	}

}
