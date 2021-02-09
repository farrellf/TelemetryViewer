import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

public class WidgetDatasets extends Widget {
	
	List<Dataset>                selectedDatasets       = new ArrayList<Dataset>();
	List<Dataset.Bitfield.State> selectedBitfieldEdges  = new ArrayList<Dataset.Bitfield.State>();
	List<Dataset.Bitfield.State> selectedBitfieldLevels = new ArrayList<Dataset.Bitfield.State>();
	
	Consumer<List<Dataset>>                datasetsEventHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesEventHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsEventHandler;
	
	boolean  showNormalDatasets;
	boolean  showBitfieldDatasets;
	boolean  comboboxesMode; // if we show comboboxes or checkboxes
	String[] comboboxLabels;
	
	Map<Dataset, JCheckBox>  datasetCheckboxes = new LinkedHashMap<Dataset, JCheckBox>();
	List<JComboBox<Dataset>> datasetComboboxes = new ArrayList<JComboBox<Dataset>>();
	Map<Dataset.Bitfield.State, JToggleButton> edgeButtons  = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
	Map<Dataset.Bitfield.State, JToggleButton> levelButtons = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
	Map<Dataset, JToggleButton> bitfieldEdgeButtonsForDataset  = new LinkedHashMap<Dataset, JToggleButton>();
	Map<Dataset, JToggleButton> bitfieldLevelButtonsForDataset = new LinkedHashMap<Dataset, JToggleButton>();
	
	/**
	 * A widget that lets the user pick zero or more normal datasets (not bitfields) from a list.
	 * 
	 * @param datasetsHandler    Will be notified about which datasets are selected.
	 */
	public WidgetDatasets(Consumer<List<Dataset>> datasetsHandler) {
		
		super();
		
		showNormalDatasets = true;
		showBitfieldDatasets = false;
		comboboxesMode = false;
		
		datasetsEventHandler = datasetsHandler;
		
		update();
		
	}
	
	/**
	 * A widget that lets the user pick a specific number of normal datasets (not bitfields) from comboboxes.
	 * 
	 * @param datasetsHandler    Will be notified about which datasets are selected.
	 */
	public WidgetDatasets(int datasetCount, String[] labels, Consumer<List<Dataset>> datasetsHandler) {
		
		super();

		showNormalDatasets = true;
		showBitfieldDatasets = false;
		comboboxesMode = true;
		comboboxLabels = labels;
		
		datasetsEventHandler = datasetsHandler;
		
		update();
		
	}
	
	/**
	 * A widget that lets the user pick zero or more bitfields (not normal datasets) from a list.
	 * 
	 * @param bitfieldEdgesHandler     Will be notified about which bitfield state edges are selected.
	 * @param bitfieldLevelsHandler    Will be notified about which bitfield state levels are selected.
	 */
	public WidgetDatasets(Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsHandler) {
		
		super();
		
		showNormalDatasets = false;
		showBitfieldDatasets = true;
		comboboxesMode = false;
		
		bitfieldEdgesEventHandler = bitfieldEdgesHandler;
		bitfieldLevelsEventHandler = bitfieldLevelsHandler;
		
		update();
		
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
		
		showNormalDatasets = true;
		showBitfieldDatasets = true;
		comboboxesMode = false;
		
		datasetsEventHandler = datasetsHandler;
		bitfieldEdgesEventHandler = bitfieldEdgesHandler;
		bitfieldLevelsEventHandler = bitfieldLevelsHandler;
		
		update();
	
	}
	
	/**
	 * Changes the dataset labels (and possibly the number of datasets) for combobox mode.
	 * 
	 * @param newLabels    The names to show next to each combobox.
	 */
	public void setDatasetLabels(String[] newLabels) {
		
		comboboxLabels = newLabels;
		update();
		
	}
	
	/**
	 * Ensures this widget is in sync with its state.
	 */
	@SuppressWarnings("serial")
	@Override public void update() {
		
		widgets.clear();
		
		datasetCheckboxes.clear();
		datasetComboboxes.clear();
		edgeButtons.clear();
		levelButtons.clear();
		bitfieldEdgeButtonsForDataset.clear();
		bitfieldLevelButtonsForDataset.clear();
		
		// ensure the selected datasets still exist
		selectedDatasets.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.connection));
		selectedBitfieldEdges.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.dataset.connection));
		selectedBitfieldLevels.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.dataset.connection));
		
		if(!comboboxesMode) {
			
			if(showNormalDatasets) {
				
				for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections) {
					
					if(!connection.dataStructureDefined)
						continue;
					
					int rowCount = 0;
					String label = ConnectionsController.telemetryConnections.size() == 1 ? "Datasets: " : connection.name + " Datasets: ";
					
					for(Dataset dataset : connection.datasets.getList()) {
						
						if(dataset.isBitfield)
							continue;
						
						JCheckBox checkbox = new JCheckBox(dataset.name);
						checkbox.setSelected(selectedDatasets.contains(dataset));
						checkbox.addActionListener(event -> {
							selectedDatasets.remove(dataset);
							if(checkbox.isSelected())
								selectedDatasets.add(dataset);
							disableDatasetsFromOtherConnections();
							notifyHandlers();
						});
						datasetCheckboxes.put(dataset, checkbox);
						
						if(rowCount++ == 0)
							widgets.put(new JLabel(label), "");
						else
							widgets.put(new JLabel(""), "");
						widgets.put(checkbox, "span 3, growx");
						
					}
					
				}
			
			}
			
			if(showBitfieldDatasets) {
		
				// use a narrow border for the edge and level buttons, to make them smaller
				JToggleButton temp = new JToggleButton("_");
				Insets insets = temp.getBorder().getBorderInsets(temp);
				Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));
				
				for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections) {
					
					if(!connection.dataStructureDefined)
						continue;
					
					int rowCount = 0;
					String label = ConnectionsController.telemetryConnections.size() == 1 ? "Bitfields: " : connection.name + " Bitfields: ";
					
					for(Dataset dataset : connection.datasets.getList()) {
						
						if(!dataset.isBitfield)
							continue;
						
						// show toggle buttons for the entire dataset
						JToggleButton allEdgesButton = new JToggleButton("_\u20D2\u203E");
						allEdgesButton.setBorder(narrowBorder);
						allEdgesButton.setToolTipText("Show edges");
						boolean allEdgesSelected = true;
						for(Dataset.Bitfield b : dataset.bitfields)
							for(Dataset.Bitfield.State s : b.states)
								if(!selectedBitfieldEdges.contains(s))
									allEdgesSelected = false;
						allEdgesButton.setSelected(allEdgesSelected);
						allEdgesButton.addActionListener(event -> {
							selectedBitfieldEdges.removeIf(item -> item.dataset == dataset);
							if(allEdgesButton.isSelected()) {
								for(Dataset.Bitfield bitfield : dataset.bitfields)
									for(Dataset.Bitfield.State state : bitfield.states)
										selectedBitfieldEdges.add(state);
								for(Entry<Dataset.Bitfield.State, JToggleButton> entry : edgeButtons.entrySet())
									if(entry.getKey().dataset == dataset)
										entry.getValue().setSelected(true);
							} else {
								for(Entry<Dataset.Bitfield.State, JToggleButton> entry : edgeButtons.entrySet())
									if(entry.getKey().dataset == dataset)
										entry.getValue().setSelected(false);
							}
							disableDatasetsFromOtherConnections();
							notifyHandlers();
						});
						
						JToggleButton allLevelsButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
						allLevelsButton.setBorder(narrowBorder);
						allLevelsButton.setToolTipText("Show levels");
						boolean allLevelsSelected = true;
						for(Dataset.Bitfield b : dataset.bitfields)
							for(Dataset.Bitfield.State s : b.states)
								if(!selectedBitfieldLevels.contains(s))
									allLevelsSelected = false;
						allLevelsButton.setSelected(allLevelsSelected);
						allLevelsButton.addActionListener(event -> {
							selectedBitfieldLevels.removeIf(item -> item.dataset == dataset);
							if(allLevelsButton.isSelected()) {
								for(Dataset.Bitfield bitfield : dataset.bitfields)
									for(Dataset.Bitfield.State state : bitfield.states)
										selectedBitfieldLevels.add(state);
								for(Entry<Dataset.Bitfield.State, JToggleButton> entry : levelButtons.entrySet())
									if(entry.getKey().dataset == dataset)
										entry.getValue().setSelected(true);
							} else {
								for(Entry<Dataset.Bitfield.State, JToggleButton> entry : levelButtons.entrySet())
									if(entry.getKey().dataset == dataset)
										entry.getValue().setSelected(false);
							}
							disableDatasetsFromOtherConnections();
							notifyHandlers();
						});
						
						if(rowCount++ == 0)
							widgets.put(new JLabel(label), "");
						else
							widgets.put(new JLabel(""), "");
						widgets.put(allEdgesButton, "");
						widgets.put(allLevelsButton, "");
						widgets.put(new JLabel("<html><b>" + dataset.name + " (All / None)</b></html>"), "");
						
						bitfieldEdgeButtonsForDataset.put(dataset, allEdgesButton);
						bitfieldLevelButtonsForDataset.put(dataset, allLevelsButton);
						
						// also show toggle buttons for each state of each bitfield
						for(Dataset.Bitfield bitfield : dataset.bitfields) {
							for(Dataset.Bitfield.State state : bitfield.states) {
								
								JToggleButton edgeButton = new JToggleButton("_\u20D2\u203E");
								edgeButton.setBorder(narrowBorder);
								edgeButton.setToolTipText("Show edges");
								edgeButton.setSelected(selectedBitfieldEdges.contains(state));
								edgeButton.addActionListener(event -> {
									selectedBitfieldEdges.remove(state);
									if(edgeButton.isSelected())
										selectedBitfieldEdges.add(state);
									boolean allSelected = true;
									for(Dataset.Bitfield.State s : bitfield.states)
										if(!selectedBitfieldEdges.contains(s))
											allSelected = false;
									allEdgesButton.setSelected(allSelected);
									disableDatasetsFromOtherConnections();
									notifyHandlers();
								});
								edgeButtons.put(state, edgeButton);
								
								JToggleButton levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
								levelButton.setBorder(narrowBorder);
								levelButton.setToolTipText("Show levels");
								levelButton.setSelected(selectedBitfieldLevels.contains(state));
								levelButton.addActionListener(event -> {
									selectedBitfieldLevels.remove(state);
									if(levelButton.isSelected())
										selectedBitfieldLevels.add(state);
									boolean allSelected = true;
									for(Dataset.Bitfield.State s : bitfield.states)
										if(!selectedBitfieldLevels.contains(s))
											allSelected = false;
									allLevelsButton.setSelected(allSelected);
									disableDatasetsFromOtherConnections();
									notifyHandlers();
								});
								levelButtons.put(state, levelButton);
								
								widgets.put(new JLabel(""), "");
								widgets.put(edgeButton, "");
								widgets.put(levelButton, "");
								widgets.put(new JLabel(state.name), "");
								
							}
						}
						
					}
						
				}
				
			}

		} else if(showNormalDatasets && !showBitfieldDatasets && comboboxesMode) {
			
			if(selectedDatasets.size() != comboboxLabels.length) {
				selectedDatasets.clear();
				Dataset firstDataset = null;
				for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections) {
					if(!connection.dataStructureDefined)
						continue;
					firstDataset = connection.datasets.getList().get(0);
					break;
				}
				if(firstDataset != null)
					for(int i = 0; i < comboboxLabels.length; i++)
						selectedDatasets.add(firstDataset);
			}
			
			for(int i = 0; i < comboboxLabels.length; i++) {
				JComboBox<Dataset> combobox = new JComboBox<Dataset>();
				for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections) {
					if(!connection.dataStructureDefined)
						continue;
					for(Dataset dataset : connection.datasets.getList()) {
						if(dataset.isBitfield)
							continue;
						combobox.addItem(dataset);
					}
				}
				int connectionCount = 0;
				for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections)
					if(connection.dataStructureDefined)
						connectionCount++;
				if(connectionCount > 1)
					combobox.setRenderer(new DefaultListCellRenderer() {
						@Override public Component getListCellRendererComponent(final JList<?> list, Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
							return super.getListCellRendererComponent(list, ((Dataset)value).connection.name + ": " + value.toString(), index, isSelected, cellHasFocus);
						}
					});
				if(selectedDatasets.size() > i)
					combobox.setSelectedItem(selectedDatasets.get(i));
				else
					combobox.setEnabled(false);
				int index = i;
				combobox.addActionListener(event -> {
					Dataset d = (Dataset) combobox.getSelectedItem();
					selectedDatasets.set(index, d);
					for(JComboBox<Dataset> box : datasetComboboxes)
						if(box != combobox && ((Dataset)box.getSelectedItem()).connection != d.connection)
							box.setSelectedItem(d);
					notifyHandlers();
				});
				datasetComboboxes.add(combobox);
				
				String label = comboboxLabels[i].equals("") ? "" : comboboxLabels[i] + ": ";
				widgets.put(new JLabel(label), "");
				widgets.put(datasetComboboxes.get(i), "span 3, growx");
			}
			
		}
		
		disableDatasetsFromOtherConnections();
		notifyHandlers();
		
	}
	

	/**
	 * Grays out datasets from other connections.
	 */
	private void disableDatasetsFromOtherConnections() {
		
		// not needed if only one connection
		if(ConnectionsController.telemetryConnections.size() < 2)
			return;
		
		// re-enable all widgets if nothing is selected
		if(selectedDatasets.isEmpty() && selectedBitfieldEdges.isEmpty() && selectedBitfieldLevels.isEmpty()) {
			for(JCheckBox checkbox : datasetCheckboxes.values())
				checkbox.setEnabled(true);
			for(JToggleButton button : bitfieldEdgeButtonsForDataset.values())
				button.setEnabled(true);
			for(JToggleButton button : bitfieldLevelButtonsForDataset.values())
				button.setEnabled(true);
			for(JToggleButton button : edgeButtons.values())
				button.setEnabled(true);
			for(JToggleButton button : levelButtons.values())
				button.setEnabled(true);
			return;
		}
		
		// determine which connection has been selected
		ConnectionTelemetry connection = !selectedDatasets.isEmpty()      ? selectedDatasets.get(0).connection :
		                                 !selectedBitfieldEdges.isEmpty() ? selectedBitfieldEdges.get(0).dataset.connection :
		                                                                    selectedBitfieldLevels.get(0).dataset.connection;
		
		// disable widgets for datasets from the other connections
		for(Map.Entry<Dataset, JCheckBox> entry : datasetCheckboxes.entrySet())
			if(entry.getKey().connection != connection)
				entry.getValue().setEnabled(false);
		
		for(Entry<Dataset, JToggleButton> entry : bitfieldEdgeButtonsForDataset.entrySet())
			if(entry.getKey().connection != connection)
				entry.getValue().setEnabled(false);
		
		for(Entry<Dataset, JToggleButton> entry : bitfieldLevelButtonsForDataset.entrySet())
			if(entry.getKey().connection != connection)
				entry.getValue().setEnabled(false);
		
		for(Entry<Dataset.Bitfield.State, JToggleButton> entry : edgeButtons.entrySet())
			if(entry.getKey().dataset.connection != connection)
				entry.getValue().setEnabled(false);
		
		for(Entry<Dataset.Bitfield.State, JToggleButton> entry : levelButtons.entrySet())
			if(entry.getKey().dataset.connection != connection)
				entry.getValue().setEnabled(false);
		
	}
	
	/**
	 * Determines which normal datasets, bitfield edges and bitfield levels have been selected, then notifies the handlers.
	 */
	private void notifyHandlers() {
		
		// important: provide the chart with a NEW list, to ensure comparisons fail and caches flush
		
		if(datasetsEventHandler != null)
			datasetsEventHandler.accept(new ArrayList<Dataset>(selectedDatasets));

		if(bitfieldEdgesEventHandler != null)
			bitfieldEdgesEventHandler.accept(new ArrayList<Dataset.Bitfield.State>(selectedBitfieldEdges));
		
		if(bitfieldLevelsEventHandler != null)
			bitfieldLevelsEventHandler.accept(new ArrayList<Dataset.Bitfield.State>(selectedBitfieldLevels));
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(ConnectionsController.QueueOfLines lines) {
		
		selectedDatasets.clear();
		selectedBitfieldEdges.clear();
		selectedBitfieldLevels.clear();
		
		// parse the telemetry datasets line
		String line = ChartUtils.parseString(lines.remove(), "datasets = %s");
		if(!line.equals("")) {
			try {
				String[] tokens = line.split(",");
				if(comboboxesMode && tokens.length != comboboxLabels.length)
					throw new Exception();
				for(String token : tokens) {
					int connectionN = Integer.parseInt(token.split(" ")[1]);
					int locationN   = Integer.parseInt(token.split(" ")[3]);
					Dataset d = ConnectionsController.telemetryConnections.get(connectionN).datasets.getByLocation(locationN);
					if(d == null)
						throw new Exception();
					selectedDatasets.add(d);
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
					for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections)
						for(Dataset dataset : connection.datasets.getList())
							if(dataset.isBitfield)
								for(Dataset.Bitfield bitfield : dataset.bitfields)
									for(Dataset.Bitfield.State s : bitfield.states)
										if(s.toString().equals(state)) {
											found = true;
											selectedBitfieldEdges.add(s);
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
					for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections)
						for(Dataset dataset : connection.datasets.getList())
							if(dataset.isBitfield)
								for(Dataset.Bitfield bitfield : dataset.bitfields)
									for(Dataset.Bitfield.State s : bitfield.states)
										if(s.toString().equals(state)) {
											found = true;
											selectedBitfieldLevels.add(s);
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
		
		// selected datasets
		lines[0] = new String("datasets = ");
		for(Dataset d : selectedDatasets)
			lines[0] += "connection " + ConnectionsController.telemetryConnections.indexOf(d.connection) + " location " + d.location + ",";
		if(lines[0].endsWith(","))
			lines[0] = lines[0].substring(0, lines[0].length() - 1);
		
		// selected bitfield edges
		lines[1] = new String("bitfield edge states = ");
		for(Dataset.Bitfield.State s : selectedBitfieldEdges)
			lines[1] += s.toString() + ",";
		if(lines[1].endsWith(","))
			lines[1] = lines[1].substring(0, lines[1].length() - 1);
		
		// selected bitfield levels
		lines[2] = new String("bitfield level states = ");
		for(Dataset.Bitfield.State s : selectedBitfieldLevels)
			lines[2] += s.toString() + ",";
		if(lines[2].endsWith(","))
			lines[2] = lines[2].substring(0, lines[2].length() - 1);

		return lines;
		
	}

}
