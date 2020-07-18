import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

public class WidgetDatasets extends Widget {
	
	Map<Dataset, JCheckBox>  datasetCheckboxes; // used if any number        of normal datasets (not bitfields) may  be selected
	List<JComboBox<Dataset>> datasetComboboxes; // used if a specific number of normal datasets (not bitfields) must be selected
	Map<Dataset.Bitfield.State, JToggleButton> bitfieldEdgeButtons;  // used if any number of bitfields edges  may be selected
	Map<Dataset.Bitfield.State, JToggleButton> bitfieldLevelButtons; // used if any number of bitfields levels may be selected
	Map<Dataset, JToggleButton> bitfieldEdgeButtonsForDataset;  // the buttons that select all/none of the bitfield states
	Map<Dataset, JToggleButton> bitfieldLevelButtonsForDataset; // the buttons that select all/none of the bitfield states
	
	Consumer<List<Dataset>>                datasetsHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsHandler;
	
	/**
	 * A widget that lets the user pick zero or more normal datasets (not bitfields) from a list.
	 * 
	 * @param datasetsHandler    Will be notified about which datasets are selected.
	 */
	public WidgetDatasets(Consumer<List<Dataset>> datasetsHandler) {
		
		super();
		
		datasetCheckboxes = new LinkedHashMap<Dataset, JCheckBox>();
		datasetComboboxes = null;
		bitfieldEdgeButtons = null;
		bitfieldLevelButtons = null;
		bitfieldEdgeButtonsForDataset = null;
		bitfieldLevelButtonsForDataset = null;
		
		int rowCount = 0;
		widgets.put(new JLabel("Datasets: "), "");
		
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			
			if(dataset.isBitfield)
				continue;
			
			JCheckBox checkbox = new JCheckBox(dataset.name);
			checkbox.addActionListener(event -> notifyHandlers());
			datasetCheckboxes.put(dataset, checkbox);
			
			if(rowCount++ != 0)
				widgets.put(new JLabel(""), "");
			widgets.put(checkbox, "span 3, growx");
			
		}
		
		this.datasetsHandler = datasetsHandler;
		this.bitfieldEdgesHandler = null;
		this.bitfieldLevelsHandler = null;
		notifyHandlers();
		
	}
	
	/**
	 * A widget that lets the user pick a specific number of normal datasets (not bitfields) from comboboxes.
	 * 
	 * @param datasetsHandler    Will be notified about which datasets are selected.
	 */
	public WidgetDatasets(int datasetCount, String[] labels, Consumer<List<Dataset>> datasetsHandler) {
		
		super();
		
		datasetCheckboxes = null;
		datasetComboboxes = new ArrayList<JComboBox<Dataset>>(datasetCount);
		bitfieldEdgeButtons = null;
		bitfieldLevelButtons = null;
		bitfieldEdgeButtonsForDataset = null;
		bitfieldLevelButtonsForDataset = null;
		
		for(int i = 0; i < datasetCount; i++) {
			JComboBox<Dataset> combobox = new JComboBox<Dataset>();
			for(Dataset dataset : DatasetsController.getAllDatasets()) {
				if(dataset.isBitfield)
					continue;
				combobox.addItem(dataset);
			}
			combobox.addActionListener(event -> notifyHandlers());
			datasetComboboxes.add(combobox);
		}

		for(int i = 0; i < datasetCount; i++) {
			widgets.put(new JLabel(labels[i] + ": "), "");
			widgets.put(datasetComboboxes.get(i), "span 3, growx");
		}
		
		this.datasetsHandler = datasetsHandler;
		notifyHandlers();
		
	}
	
	/**
	 * A widget that lets the user pick zero or more bitfields (not normal datasets) from a list.
	 * 
	 * @param bitfieldEdgesHandler     Will be notified about which bitfield state edges are selected.
	 * @param bitfieldLevelsHandler    Will be notified about which bitfield state levels are selected.
	 */
	public WidgetDatasets(Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsHandler) {
		
		super();
		
		datasetCheckboxes = null;
		datasetComboboxes = null;
		bitfieldEdgeButtons  = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
		bitfieldLevelButtons = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
		bitfieldEdgeButtonsForDataset = new LinkedHashMap<Dataset, JToggleButton>();
		bitfieldLevelButtonsForDataset = new LinkedHashMap<Dataset, JToggleButton>();
		
		int rowCount = 0;
		widgets.put(new JLabel("Bitfields: "), "");
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			
			if(!dataset.isBitfield)
				continue;
			
			// use a narrow border for the edge and level buttons, to make them smaller
			JToggleButton temp = new JToggleButton("_");
			Insets insets = temp.getBorder().getBorderInsets(temp);
			Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));
			
			// show toggle button for the complete dataset
			JToggleButton edgeButton = new JToggleButton("_\u20D2\u203E");
			edgeButton.setBorder(narrowBorder);
			edgeButton.setToolTipText("Show edges");
			edgeButton.addActionListener(event -> {
				for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldEdgeButtons.entrySet())
					if(entry.getKey().dataset == dataset)
						entry.getValue().setSelected(((JToggleButton) event.getSource()).isSelected());
				notifyHandlers();
			});
			
			JToggleButton levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
			levelButton.setBorder(narrowBorder);
			levelButton.setToolTipText("Show levels");
			levelButton.addActionListener(event -> {
				for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldLevelButtons.entrySet())
					if(entry.getKey().dataset == dataset)
						entry.getValue().setSelected(((JToggleButton) event.getSource()).isSelected());
				notifyHandlers();
			});
			
			if(rowCount++ != 0)
				widgets.put(new JLabel(""), "");
			widgets.put(edgeButton, "");
			widgets.put(levelButton, "");
			widgets.put(new JLabel("<html><b>" + dataset.name + " (All / None)</b></html>"), "");
			
			bitfieldEdgeButtonsForDataset.put(dataset, edgeButton);
			bitfieldLevelButtonsForDataset.put(dataset, levelButton);
			
			// also show toggle buttons for each state of each bitfield
			for(Dataset.Bitfield bitfield : dataset.bitfields) {
				for(Dataset.Bitfield.State state : bitfield.states) {
					
					edgeButton = new JToggleButton("_\u20D2\u203E");
					edgeButton.setBorder(narrowBorder);
					edgeButton.setToolTipText("Show edges");
					edgeButton.addActionListener(event -> notifyHandlers());
					bitfieldEdgeButtons.put(state, edgeButton);
					
					levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
					levelButton.setBorder(narrowBorder);
					levelButton.setToolTipText("Show levels");
					levelButton.addActionListener(event -> notifyHandlers());
					bitfieldLevelButtons.put(state, levelButton);
					
					widgets.put(new JLabel(""), "");
					widgets.put(edgeButton, "");
					widgets.put(levelButton, "");
					widgets.put(new JLabel(state.name), "");
					
				}
			}
			
		}
		
		this.datasetsHandler = null;
		this.bitfieldEdgesHandler = bitfieldEdgesHandler;
		this.bitfieldLevelsHandler = bitfieldLevelsHandler;
		notifyHandlers();
		
	}
	
	/**
	 * A widget that lets the user pick zero or more datasets, and zero or more bitfield states, from a list.
	 * 
	 * @param datasetsHandler          Will be notified about which datasets are selected.
	 * @param bitfieldEdgesHandler     Will be notified about which bitfield state edges are selected.
	 * @param bitfieldLevelsHandler    Will be notified about which bitfield state levels are selected.
	 */
	public WidgetDatasets(Consumer<List<Dataset>> datasetsHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsHandler) {
	
		super();
		
		datasetCheckboxes = new LinkedHashMap<Dataset, JCheckBox>();
		datasetComboboxes = null;
		bitfieldEdgeButtons  = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
		bitfieldLevelButtons = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
		bitfieldEdgeButtonsForDataset = new LinkedHashMap<Dataset, JToggleButton>();
		bitfieldLevelButtonsForDataset = new LinkedHashMap<Dataset, JToggleButton>();
		
		int rowCount = 0;
		widgets.put(new JLabel("Datasets: "), "");
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			
			if(dataset.isBitfield)
				continue;
			
			JCheckBox checkbox = new JCheckBox(dataset.name);
			checkbox.addActionListener(event -> notifyHandlers());
			datasetCheckboxes.put(dataset, checkbox);
			
			if(rowCount++ != 0)
				widgets.put(new JLabel(""), "");
			widgets.put(checkbox, "span 3, growx");
			
		}

		rowCount = 0;
		widgets.put(new JLabel("Bitfields: "), "");
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			
			if(!dataset.isBitfield)
				continue;
			
			// use a narrow border for the edge and level buttons, to make them smaller
			JToggleButton temp = new JToggleButton("_");
			Insets insets = temp.getBorder().getBorderInsets(temp);
			Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));
			
			// show toggle button for the complete dataset
			JToggleButton edgeButton = new JToggleButton("_\u20D2\u203E");
			edgeButton.setBorder(narrowBorder);
			edgeButton.setToolTipText("Show edges");
			edgeButton.addActionListener(event -> {
				for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldEdgeButtons.entrySet())
					if(entry.getKey().dataset == dataset)
						entry.getValue().setSelected(((JToggleButton) event.getSource()).isSelected());
				notifyHandlers();
			});
			
			JToggleButton levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
			levelButton.setBorder(narrowBorder);
			levelButton.setToolTipText("Show levels");
			levelButton.addActionListener(event -> {
				for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldLevelButtons.entrySet())
					if(entry.getKey().dataset == dataset)
						entry.getValue().setSelected(((JToggleButton) event.getSource()).isSelected());
				notifyHandlers();
			});
			
			if(rowCount++ != 0)
				widgets.put(new JLabel(""), "");
			widgets.put(edgeButton, "");
			widgets.put(levelButton, "");
			widgets.put(new JLabel("<html><b>" + dataset.name + " (All / None)</b></html>"), "");
			
			bitfieldEdgeButtonsForDataset.put(dataset, edgeButton);
			bitfieldLevelButtonsForDataset.put(dataset, levelButton);
			
			// also show toggle buttons for each state of each bitfield
			for(Dataset.Bitfield bitfield : dataset.bitfields) {
				for(Dataset.Bitfield.State state : bitfield.states) {
					
					edgeButton = new JToggleButton("_\u20D2\u203E");
					edgeButton.setBorder(narrowBorder);
					edgeButton.setToolTipText("Show edges");
					edgeButton.addActionListener(event -> notifyHandlers());
					bitfieldEdgeButtons.put(state, edgeButton);
					
					levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
					levelButton.setBorder(narrowBorder);
					levelButton.setToolTipText("Show levels");
					levelButton.addActionListener(event -> notifyHandlers());
					bitfieldLevelButtons.put(state, levelButton);
					
					widgets.put(new JLabel(""), "");
					widgets.put(edgeButton, "");
					widgets.put(levelButton, "");
					widgets.put(new JLabel(state.name), "");
					
				}
			}
			
		}
		
		this.datasetsHandler = datasetsHandler;
		this.bitfieldEdgesHandler = bitfieldEdgesHandler;
		this.bitfieldLevelsHandler = bitfieldLevelsHandler;
		notifyHandlers();
	
	}
	
	/**
	 * Determines which normal datasets, bitfield edges and bitfield levels have been selected, then notifies the handlers.
	 */
	private void notifyHandlers() {
		
		if(datasetsHandler != null) {
			List<Dataset> datasets = new ArrayList<Dataset>();
			if(datasetCheckboxes != null) 
				for(Entry<Dataset, JCheckBox> entry : datasetCheckboxes.entrySet())
					if(entry.getValue().isSelected())
						datasets.add(entry.getKey());
			if(datasetComboboxes != null)
				for(JComboBox<Dataset> combobox : datasetComboboxes)
					datasets.add((Dataset) combobox.getSelectedItem());
			datasetsHandler.accept(datasets);
		}

		if(bitfieldEdgesHandler != null) {
			List<Dataset.Bitfield.State> states = new ArrayList<Dataset.Bitfield.State>();
			for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldEdgeButtons.entrySet())
				if(entry.getValue().isSelected())
					states.add(entry.getKey());
			bitfieldEdgesHandler.accept(states);
			
			// check if every state for every bitfield of a dataset are enabled/disabled
			for(Dataset d : DatasetsController.getAllDatasets()) {
				boolean allSelected = true;
				if(d.isBitfield) {
					for(Dataset.Bitfield bitfield : d.bitfields)
						for(Dataset.Bitfield.State state : bitfield.states)
							if(!bitfieldEdgeButtons.get(state).isSelected())
								allSelected = false;
					bitfieldEdgeButtonsForDataset.get(d).setSelected(allSelected);
				}
			}
		}
		
		if(bitfieldLevelsHandler != null) {
			List<Dataset.Bitfield.State> states = new ArrayList<Dataset.Bitfield.State>();
			for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldLevelButtons.entrySet())
				if(entry.getValue().isSelected())
					states.add(entry.getKey());
			bitfieldLevelsHandler.accept(states);
			
			// check if every state for every bitfield of a dataset are enabled/disabled
			for(Dataset d : DatasetsController.getAllDatasets()) {
				boolean allSelected = true;
				if(d.isBitfield) {
					for(Dataset.Bitfield bitfield : d.bitfields)
						for(Dataset.Bitfield.State state : bitfield.states)
							if(!bitfieldLevelButtons.get(state).isSelected())
								allSelected = false;
					bitfieldLevelButtonsForDataset.get(d).setSelected(allSelected);
				}
			}
		}
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {
		
		// reset the GUI
		if(datasetCheckboxes != null)
			for(JCheckBox checkbox : datasetCheckboxes.values())
				checkbox.setSelected(false);
		if(bitfieldEdgeButtons != null)
			for(JToggleButton button : bitfieldEdgeButtons.values())
				button.setSelected(false);
		if(bitfieldLevelButtons != null)
			for(JToggleButton button : bitfieldLevelButtons.values())
				button.setSelected(false);
		
		// parse the normal datasets line
		String line = ChartUtils.parseString(lines.remove(), "normal datasets = %s");
		if(!line.equals("")) {
			try {
				String[] indices = line.split(",");
				if(datasetComboboxes != null && indices.length != datasetComboboxes.size())
					throw new Exception();
				for(int i = 0; i < indices.length; i++) {
					Dataset d = DatasetsController.getDatasetByLocation(Integer.parseInt(indices[i]));
					if(d == null)
						throw new Exception();
					if(datasetCheckboxes != null)
						datasetCheckboxes.get(d).setSelected(true);
					if(datasetComboboxes != null)
						datasetComboboxes.get(i).setSelectedItem(d);
				}
			} catch(Exception e) { throw new AssertionError("Invalid datasets list."); }
		}
		
		// parse the bitfield edge states line
		line = ChartUtils.parseString(lines.remove(), "bitfield edge states = %s");
		if(!line.equals("")) {
			try {
				String[] states = line.split(",");
				for(String state : states) {
					boolean found = false;
					for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldEdgeButtons.entrySet())
						if(entry.getKey().toString().equals(state)) {
							entry.getValue().setSelected(true);
							found = true;
							break;
						}
					if(!found)
						throw new Exception();
				}
			} catch(Exception e) { throw new AssertionError("Invalid bitfield edge states list."); }
		}
		
		// parse the bitfield level states line
		line = ChartUtils.parseString(lines.remove(), "bitfield level states = %s");
		if(!line.equals("")) {
			try {
				String[] states = line.split(",");
				for(String state : states) {
					boolean found = false;
					for(Entry<Dataset.Bitfield.State, JToggleButton> entry : bitfieldLevelButtons.entrySet())
						if(entry.getKey().toString().equals(state)) {
							entry.getValue().setSelected(true);
							found = true;
							break;
						}
					if(!found)
						throw new Exception();
				}
			} catch(Exception e) { throw new AssertionError("Invalid bitfield level states list."); }
		}
		
		// update the chart
		notifyHandlers();
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		String[] lines = new String[3];
		
		lines[0] = new String("normal datasets = ");
		lines[1] = new String("bitfield edge states = ");
		lines[2] = new String("bitfield level states = ");
		
		if(datasetCheckboxes != null)
			for(Entry<Dataset, JCheckBox> dataset : datasetCheckboxes.entrySet())
				if(dataset.getValue().isSelected())
					lines[0] += dataset.getKey().location + ",";
		if(datasetComboboxes != null)
			for(JComboBox<Dataset> combobox : datasetComboboxes)
				lines[0] += ((Dataset) (combobox.getSelectedItem())).location + ",";
		if(lines[0].endsWith(","))
			lines[0] = lines[0].substring(0, lines[0].length() - 1);
		
		if(bitfieldEdgeButtons != null)
			for(Entry<Dataset.Bitfield.State, JToggleButton> bitfieldState : bitfieldEdgeButtons.entrySet())
				if(bitfieldState.getValue().isSelected())
					lines[1] += bitfieldState.getKey().toString() + ",";
		if(lines[1].endsWith(","))
			lines[1] = lines[1].substring(0, lines[1].length() - 1);
		
		if(bitfieldLevelButtons != null)
			for(Entry<Dataset.Bitfield.State, JToggleButton> bitfieldState : bitfieldLevelButtons.entrySet())
				if(bitfieldState.getValue().isSelected())
					lines[2] += bitfieldState.getKey().toString() + ",";
		if(lines[2].endsWith(","))
			lines[2] = lines[2].substring(0, lines[2].length() - 1);

		return lines;
		
	}

}
