import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A widget that lets the user pick one or more datasets from a list of checkboxes.
 */
@SuppressWarnings("serial")
public class WidgetDatasets extends JPanel {
	
	JLabel label;
	Map<JCheckBox, Dataset> datasetsMap;
	JCheckBox[] checkboxes;
	
	public WidgetDatasets() {
		
		super();
		
		Dataset[] datasets = Controller.getAllDatasets();
		
		setLayout(new GridLayout(datasets.length, 2, 10, 10));
		
		label = new JLabel("Datasets: ");
		add(label);

		datasetsMap = new LinkedHashMap<JCheckBox, Dataset>();
		for(Dataset dataset : datasets)
			datasetsMap.put(new JCheckBox(dataset.name), dataset);
		
		checkboxes = datasetsMap.keySet().toArray(new JCheckBox[datasets.length]);
		add(checkboxes[0]);
		for(int i = 1; i < checkboxes.length; i++) {
			add(new JLabel(""));
			add(checkboxes[i]);
		}
		
	}
	
	/**
	 * @return    An array of the selected datasets.
	 */
	public Dataset[] getDatasets() {
		
		// determine how many were chosen
		int datasetsCount = 0;
		for(JCheckBox checkbox : datasetsMap.keySet())
			if(checkbox.isSelected())
				datasetsCount++;
		
		// create array of them
		Dataset[] datasets = new Dataset[datasetsCount];
		int i = 0;
		for(Entry<JCheckBox, Dataset> entry : datasetsMap.entrySet())
			if(entry.getKey().isSelected())
				datasets[i++] = entry.getValue();
		
		return datasets;
		
	}

}
