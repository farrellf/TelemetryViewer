import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;

@SuppressWarnings("serial")
public class WidgetDatasets extends Widget {
	
	Map<Dataset, JCheckBox> datasetsMap; // used if any number of datasets may be selected
	List<JComboBox<Dataset>> datasetsList; // used if a specific number of datasets must be selected
	boolean datasetCountUndefined;
	
	Consumer<Dataset[]> handler;
		
	/**
	 * A widget that lets the user pick zero or more datasets from a list of checkboxes.
	 * 
	 * @param allowBitfields    If bitfield datasets should be shown in the list.
	 * @param eventHandler      Will be notified when the chosen datasets change.
	 */
	public WidgetDatasets(boolean allowBitfields, Consumer<Dataset[]> eventHandler) {
		
		super();
		
		Dataset[] datasets = DatasetsController.getAllDatasets();
		datasetsMap = new LinkedHashMap<Dataset, JCheckBox>();
		
		setLayout(new GridLayout(0, 2, Theme.padding, Theme.padding));
		add(new JLabel("Datasets: "));
		
		for(int i = 0; i < datasets.length; i++) {
			
			if(!allowBitfields && datasets[i].isBitfield)
				continue;
			
			JCheckBox checkbox = new JCheckBox(datasets[i].name);
			checkbox.addActionListener(event -> notifyHandler());
			
			datasetsMap.put(datasets[i], checkbox);
			
			if(i != 0)
				add(new JLabel(""));
			add(checkbox);
			
		}
		
		datasetCountUndefined = true;
		
		handler = eventHandler;
		notifyHandler();
		
	}
	
	/**
	 * A widget that lets the user pick a specific number of datasets from drop-down lists.
	 * 
	 * @param datasetCount      How many datasets are required.
	 * @param labels            String[] of labels to show at the left of the drop-down lists.
	 * @param allowBitfields    If bitfield datasets should be shown in the list.
	 * @param eventHandler      Will be notified when the chosen dataset changes.
	 */
	public WidgetDatasets(int datasetCount, String[] labels, boolean allowBitfields, Consumer<Dataset[]> eventHandler) {
		
		super();
		
		datasetsList = new ArrayList<JComboBox<Dataset>>(datasetCount);
		for(int i = 0; i < datasetCount; i++) {
			JComboBox<Dataset> combobox = new JComboBox<Dataset>();
			for(Dataset dataset : DatasetsController.getAllDatasets())
				if(allowBitfields || (!allowBitfields && !dataset.isBitfield))
					combobox.addItem(dataset);
			combobox.addActionListener(event -> notifyHandler());
			datasetsList.add(combobox);
		}

		
		setLayout(new GridLayout(0, 2, 10, 10));
		for(int i = 0; i < datasetCount; i++) {
			add(new JLabel(labels[i] + ": "));
			add(datasetsList.get(i));
		}
		
		datasetCountUndefined = false;
		
		handler = eventHandler;
		notifyHandler();
		
	}
	
	/**
	 * Determines which datasets have been selected, and notifies the handler.
	 */
	private void notifyHandler() {
		
		if(datasetCountUndefined) {
		
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
		
		} else {
			
			Dataset[] datasets = new Dataset[datasetsList.size()];
			for(int i = 0; i < datasetsList.size(); i++)
				datasets[i] = (Dataset) datasetsList.get(i).getSelectedItem();
			
			handler.accept(datasets);
			
		}
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {
		
		Dataset[] datasets = null;
		
		// parse the text
		String datasetIndicies = ChartUtils.parseString(lines.remove(), "datasets = %s");
		
		try {
			
			String[] tokens = datasetIndicies.split(",");
			for(String t : tokens)
				Integer.parseInt(t);
			
			datasets = new Dataset[tokens.length];
			for(int i = 0; i < tokens.length; i++) {
				datasets[i] = DatasetsController.getDatasetByLocation(Integer.parseInt(tokens[i]));
				if(datasets[i] == null)
					throw new Exception();
			}
			
		} catch(Exception e) {
			
			throw new AssertionError("Invalid datasets list.");
			
		}
		
		// update the widget
		if(datasetCountUndefined) {
		
			// uncheck all
			for(JCheckBox checkbox : datasetsMap.values())
				checkbox.setSelected(false);
		
			// check the specified datasets
			for(Dataset dataset : datasets)
				datasetsMap.get(dataset).setSelected(true);
		
		} else {
			
			// sanity check
			if(datasets.length != datasetsList.size())
				return;
			
			// select the specified datasets
			for(int i = 0; i < datasets.length; i++)
				datasetsList.get(i).setSelectedItem(datasets[i]);
			
		}
		
		// update the chart
		notifyHandler();
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		String text = "datasets = ";
		
		if(datasetCountUndefined) {
			for(Entry<Dataset, JCheckBox> dataset : datasetsMap.entrySet())
				if(dataset.getValue().isSelected())
					text += dataset.getKey().location + ",";
		} else {
			for(JComboBox<Dataset> combobox : datasetsList)
				text += ((Dataset) (combobox.getSelectedItem())).location + ",";
		}
		
		if(text.endsWith(","))
			text = text.substring(0, text.length() - 1);

		return new String[] {text};
		
	}

}
