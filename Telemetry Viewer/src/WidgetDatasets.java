import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

public class WidgetDatasets extends Widget {
	
	// "model"
	List<Dataset>                selectedDatasets       = new ArrayList<Dataset>();
	List<Dataset.Bitfield.State> selectedBitfieldEdges  = new ArrayList<Dataset.Bitfield.State>();
	List<Dataset.Bitfield.State> selectedBitfieldLevels = new ArrayList<Dataset.Bitfield.State>();
	long durationSampleCount;
	long durationMilliseconds;
	boolean userSpecifiedTheDuration = false;
	enum DurationUnit {
		SAMPLES { @Override public String toString() { return "Samples"; } },
		SECONDS { @Override public String toString() { return "Seconds"; } },
		MINUTES { @Override public String toString() { return "Minutes"; } },
		HOURS   { @Override public String toString() { return "Hours";   } },
		DAYS    { @Override public String toString() { return "Days";    } }
	};
	DurationUnit durationUnit = DurationUnit.SAMPLES;
	enum AxisType {
		SAMPLE_COUNT { @Override public String toString() { return "Sample Count"; } },
		TIMESTAMPS   { @Override public String toString() { return "Timestamps";   } },
		TIME_ELAPSED { @Override public String toString() { return "Time Elapsed"; } }
	};
	AxisType axisType = AxisType.SAMPLE_COUNT;
	
	// "view"
	Map<Dataset, JCheckBox>  datasetCheckboxes = new LinkedHashMap<Dataset, JCheckBox>();
	List<JComboBox<Dataset>> datasetComboboxes = new ArrayList<JComboBox<Dataset>>();
	Map<Dataset.Bitfield.State, JToggleButton> edgeButtons  = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
	Map<Dataset.Bitfield.State, JToggleButton> levelButtons = new LinkedHashMap<Dataset.Bitfield.State, JToggleButton>();
	Map<Dataset, JToggleButton> bitfieldEdgeButtonsForEntireDataset  = new LinkedHashMap<Dataset, JToggleButton>();
	Map<Dataset, JToggleButton> bitfieldLevelButtonsForEntireDataset = new LinkedHashMap<Dataset, JToggleButton>();
	JTextField durationTextfield = new JTextField(Long.toString(durationSampleCount));
	JComboBox<DurationUnit> durationUnitCombobox = new JComboBox<DurationUnit>(DurationUnit.values());
	JToggleButton sampleCountMode = new JToggleButton("Sample Count", true);
	JToggleButton timestampsMode = new JToggleButton("Timestamps", false);
	JToggleButton timeElapsedMode = new JToggleButton("Time Elapsed", false);
	
	// "controller"
	String[] comboboxLabels;
	boolean  allowTime;
	Consumer<List<Dataset>>                datasetsEventHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesEventHandler;
	Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsEventHandler;
	BiFunction<AxisType, Long, Long>       durationEventHandler;
	
	/**
	 * A widget that lets the user select datasets and optionally specify a chart duration.
	 * 
	 * @param datasetsHandler          If not null, allow the user to select normal datasets.
	 * @param bitfieldEdgesHandler     If not null, allow the user to select bitfield edge events.
	 * @param bitfieldLevelsHandler    If not null, allow the user to select bitfield levels.
	 * @param durationHandler          If not null, allow the user to specify the chart duration.
	 * @param allowTime                If true, the chart duration can be specified as a sample count or length of time. If false, only a sample count is allowed.
	 * @param comboboxLabels           If not null, a specific number of normal datasets may be selected with comboboxes. If null, any number of datasets may be selected from checkboxes.
	 */
	public WidgetDatasets(Consumer<List<Dataset>> datasetsHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldEdgesHandler, Consumer<List<Dataset.Bitfield.State>> bitfieldLevelsHandler, BiFunction<AxisType, Long, Long> durationHandler, boolean allowTime, String[] comboboxLabels) {
		
		super();
		
		datasetsEventHandler = datasetsHandler;
		bitfieldEdgesEventHandler = bitfieldEdgesHandler;
		bitfieldLevelsEventHandler = bitfieldLevelsHandler;
		durationEventHandler = durationHandler;
		this.allowTime = allowTime;
		this.comboboxLabels = comboboxLabels;
		
		durationSampleCount = 10_000;
		durationMilliseconds = 10_000;
		if(!ConnectionsController.telemetryConnections.isEmpty())
			durationSampleCount = ConnectionsController.telemetryConnections.get(0).sampleRate * 10L;
		durationTextfield.setText(Long.toString(durationSampleCount));
		
		durationTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { setDuration(durationTextfield.getText(), true); }
			@Override public void focusGained(FocusEvent fe) { durationTextfield.selectAll(); }
		});
		durationTextfield.addActionListener(event -> setDuration(durationTextfield.getText(), true));
		durationUnitCombobox.addActionListener(event -> setDurationUnit((DurationUnit) durationUnitCombobox.getSelectedItem()));
		
		sampleCountMode.setBorder(Theme.narrowButtonBorder);
		sampleCountMode.addActionListener(event -> setAxisType(AxisType.SAMPLE_COUNT));
		timestampsMode.setBorder(Theme.narrowButtonBorder);
		timestampsMode.addActionListener(event -> setAxisType(AxisType.TIMESTAMPS));
		timestampsMode.setEnabled(false);
		timeElapsedMode.setBorder(Theme.narrowButtonBorder);
		timeElapsedMode.addActionListener(event -> setAxisType(AxisType.TIME_ELAPSED));
		timeElapsedMode.setEnabled(false);
		
		ButtonGroup group = new ButtonGroup();
		group.add(sampleCountMode);
		group.add(timestampsMode);
		group.add(timeElapsedMode);
		
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
		bitfieldEdgeButtonsForEntireDataset.clear();
		bitfieldLevelButtonsForEntireDataset.clear();
		
		// ensure the selected datasets still exist
		selectedDatasets.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.connection));
		selectedBitfieldEdges.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.dataset.connection));
		selectedBitfieldLevels.removeIf(item -> !ConnectionsController.telemetryConnections.contains(item.dataset.connection));
		
		boolean comboboxesMode = comboboxLabels != null;
		boolean showNormalDatasets = datasetsEventHandler != null;
		boolean showBitfieldDatasets = bitfieldEdgesEventHandler != null && bitfieldLevelsEventHandler != null;
		boolean showDuration = durationEventHandler != null;
		
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
						checkbox.addActionListener(event -> setNormalDatasetSelected(dataset, checkbox.isSelected()));
						datasetCheckboxes.put(dataset, checkbox);
						
						widgets.put(new JLabel((rowCount++ == 0) ? label : ""), "");
						widgets.put(checkbox, "span 3, growx");
						
					}
					
				}
			
			}
			
			if(showBitfieldDatasets) {
				
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
						allEdgesButton.setBorder(Theme.narrowButtonBorder);
						allEdgesButton.setToolTipText("Show edges");
						boolean allEdgesSelected = true;
						for(Dataset.Bitfield b : dataset.bitfields)
							for(Dataset.Bitfield.State s : b.states)
								if(!selectedBitfieldEdges.contains(s))
									allEdgesSelected = false;
						allEdgesButton.setSelected(allEdgesSelected);
						allEdgesButton.addActionListener(event -> {
							boolean selectAll = allEdgesButton.isSelected();
							for(Dataset.Bitfield bitfield : dataset.bitfields)
								for(Dataset.Bitfield.State state : bitfield.states)
									setBitfieldEdgeSelected(state, selectAll);
						});
						
						JToggleButton allLevelsButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
						allLevelsButton.setBorder(Theme.narrowButtonBorder);
						allLevelsButton.setToolTipText("Show levels");
						boolean allLevelsSelected = true;
						for(Dataset.Bitfield b : dataset.bitfields)
							for(Dataset.Bitfield.State s : b.states)
								if(!selectedBitfieldLevels.contains(s))
									allLevelsSelected = false;
						allLevelsButton.setSelected(allLevelsSelected);
						allLevelsButton.addActionListener(event -> {
							boolean selectAll = allLevelsButton.isSelected();
							for(Dataset.Bitfield bitfield : dataset.bitfields)
								for(Dataset.Bitfield.State state : bitfield.states)
									setBitfieldLevelSelected(state, selectAll);
						});
						
						widgets.put(new JLabel((rowCount++ == 0) ? label : ""), "");
						widgets.put(allEdgesButton, "");
						widgets.put(allLevelsButton, "");
						widgets.put(new JLabel("<html><b>" + dataset.name + " (All / None)</b></html>"), "");
						
						bitfieldEdgeButtonsForEntireDataset.put(dataset, allEdgesButton);
						bitfieldLevelButtonsForEntireDataset.put(dataset, allLevelsButton);
						
						// also show toggle buttons for each state of each bitfield
						for(Dataset.Bitfield bitfield : dataset.bitfields) {
							for(Dataset.Bitfield.State state : bitfield.states) {
								
								JToggleButton edgeButton = new JToggleButton("_\u20D2\u203E");
								edgeButton.setBorder(Theme.narrowButtonBorder);
								edgeButton.setToolTipText("Show edges");
								edgeButton.setSelected(selectedBitfieldEdges.contains(state));
								edgeButton.addActionListener(event -> setBitfieldEdgeSelected(state, edgeButton.isSelected()));
								edgeButtons.put(state, edgeButton);
								
								JToggleButton levelButton = new JToggleButton(" \u0332\u0305 \u0332\u0305 \u0332\u0305 ");
								levelButton.setBorder(Theme.narrowButtonBorder);
								levelButton.setToolTipText("Show levels");
								levelButton.setSelected(selectedBitfieldLevels.contains(state));
								levelButton.addActionListener(event -> setBitfieldLevelSelected(state, levelButton.isSelected()));
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
				combobox.addActionListener(event -> replaceNormalDataset((Dataset) combobox.getSelectedItem(), index));
				datasetComboboxes.add(combobox);
				
				String label = comboboxLabels[i].equals("") ? "" : comboboxLabels[i] + ": ";
				widgets.put(new JLabel(label), "");
				widgets.put(datasetComboboxes.get(i), "span 3, growx");
			}
			
		}
		
		disableDatasetsFromOtherConnections();
		
		if(showDuration && !allowTime) {
			
			widgets.put(new JLabel("Sample Count: "), "");
			widgets.put(durationTextfield, "span 3, growx");
			
		} else if(showDuration && allowTime) {
			
			widgets.put(new JLabel("Duration: "), "");
			widgets.put(durationTextfield, "span 3, split 2, growx");
			widgets.put(durationUnitCombobox, "");
			widgets.put(new JLabel("Show as: "), "");
			widgets.put(sampleCountMode, "span 3, split 3, growx");
			widgets.put(timestampsMode, "growx");
			widgets.put(timeElapsedMode, "growx");
			
		}
		
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
			for(JToggleButton button : bitfieldEdgeButtonsForEntireDataset.values())
				button.setEnabled(true);
			for(JToggleButton button : bitfieldLevelButtonsForEntireDataset.values())
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
		
		for(Entry<Dataset, JToggleButton> entry : bitfieldEdgeButtonsForEntireDataset.entrySet())
			if(entry.getKey().connection != connection)
				entry.getValue().setEnabled(false);
		
		for(Entry<Dataset, JToggleButton> entry : bitfieldLevelButtonsForEntireDataset.entrySet())
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
		
		// important: provide the chart with NEW lists, to ensure comparisons fail and caches flush
		if(datasetsEventHandler != null)
			datasetsEventHandler.accept(new ArrayList<Dataset>(selectedDatasets));

		if(bitfieldEdgesEventHandler != null)
			bitfieldEdgesEventHandler.accept(new ArrayList<Dataset.Bitfield.State>(selectedBitfieldEdges));
		
		if(bitfieldLevelsEventHandler != null)
			bitfieldLevelsEventHandler.accept(new ArrayList<Dataset.Bitfield.State>(selectedBitfieldLevels));
		
		if(durationEventHandler != null) {
			long proposedDuration = (axisType == AxisType.SAMPLE_COUNT) ? durationSampleCount : durationMilliseconds;
			long actualDuration = durationEventHandler.apply(axisType, proposedDuration);
			if(actualDuration != proposedDuration)
				if(durationUnit == DurationUnit.SAMPLES)
					setDuration(Long.toString(actualDuration), false);
				else
					setDuration(Double.toString(convertMillisecondsToDuration(actualDuration)), false);
		}
		
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
				if(comboboxLabels != null && tokens.length != comboboxLabels.length)
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
		
		// parse the duration if enabled
		if(durationEventHandler != null) {
			String number = ChartUtils.parseString(lines.remove(), "duration = %s");
			String unit = ChartUtils.parseString(lines.remove(), "duration unit = %s");
			DurationUnit unitEnum = null;
			for(DurationUnit option : DurationUnit.values())
				if(option.toString().equals(unit))
					unitEnum = option;
			if(unitEnum == null)
				throw new AssertionError("Invalid duration unit.");
			String type = ChartUtils.parseString(lines.remove(), "time axis shows = %s");
			AxisType typeEnum = null;
			for(AxisType option : AxisType.values())
				if(option.toString().equals(type))
					typeEnum = option;
			if(typeEnum == null)
				throw new AssertionError("Invalid time axis type.");
			
			setDurationUnit(unitEnum);
			setAxisType(typeEnum);
			setDuration(number, false);
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
		
		boolean durationEanbled = durationEventHandler != null;
		
		String[] lines = new String[durationEanbled ? 6 : 3];
		
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
		
		// duration
		if(durationEanbled) {
			lines[3] = "duration = " + ((durationUnit == DurationUnit.SAMPLES) ? durationSampleCount : durationMilliseconds);
			lines[4] = "duration unit = " + durationUnit;
			lines[5] = "time axis shows = " + axisType;
		}

		return lines;
		
	}
	
	/**
	 * Adds or removes a normal (not bitfield) dataset from the list of selected datasets.
	 * This method should only be called when checkboxes are used. If comboboxes are used, call replaceNormalDataset() instead.
	 * This method should only be called when the chart accepts normal datasets.
	 * 
	 * @param dataset       The dataset to add or remove.
	 * @param isSelected    True if the dataset is selected.
	 */
	private void setNormalDatasetSelected(Dataset dataset, boolean isSelected) {
		
		// ignore if no change or invalid
		if((isSelected && selectedDatasets.contains(dataset)) || (!isSelected && !selectedDatasets.contains(dataset)))
			return;
		if(comboboxLabels != null || !datasetCheckboxes.containsKey(dataset) || datasetsEventHandler == null)
			return;
		
		// update the model
		if(isSelected)
			selectedDatasets.add(dataset);
		else
			selectedDatasets.remove(dataset);
		
		// reset the duration if appropriate
		if(selectedDatasets.size() == 1 && !userSpecifiedTheDuration)
			setDuration(durationUnit == DurationUnit.SAMPLES ? Integer.toString(dataset.connection.sampleRate * 10) :
			            durationUnit == DurationUnit.SECONDS ? Double.toString(10.0) :
			            durationUnit == DurationUnit.MINUTES ? Double.toString(10.0 / 60.0) :
			            durationUnit == DurationUnit.HOURS   ? Double.toString(10.0 / 60.0 / 60.0) :
			                                                   Double.toString(10.0 / 60.0 / 60.0 / 24.0), false);
		
		// update the view
		datasetCheckboxes.get(dataset).setSelected(isSelected);
		disableDatasetsFromOtherConnections();
		
		// update the chart
		notifyHandlers();
		
	}
	
	/**
	 * Replaced one dataset for another.
	 * This method should only be called when comboboxes are used. If checkboxes are used, call addNormalDataset() or removeNormalDataset() instead.
	 * This method should only be called when the chart accepts normal datasets.
	 * 
	 * @param newDataset    The new dataset to use.
	 * @param index         Which dataset to replace.
	 */
	private void replaceNormalDataset(Dataset newDataset, int index) {
		
		// ignore if already selected or invalid
		if(selectedDatasets.get(index) == newDataset)
			return;
		if(comboboxLabels == null || index >= datasetComboboxes.size() || datasetsEventHandler == null)
			return;
		
		// update the model
		selectedDatasets.set(index, newDataset);
		
		// update the view
		datasetComboboxes.get(index).setSelectedItem(newDataset);
		for(int i = 0; i < datasetComboboxes.size(); i++) {
			if(i != index) {
				Dataset otherCombobox = (Dataset) datasetComboboxes.get(i).getSelectedItem();
				if(otherCombobox.connection != newDataset.connection) {
					selectedDatasets.set(i, newDataset);
					datasetComboboxes.get(i).setSelectedItem(newDataset);
				}
			}
		}
		
		// update the chart
		notifyHandlers();
		
	}
	
	/**
	 * Adds or removes a bitfield edge event from the list of selected events.
	 * This method should only be called when the chart accepts bitfield edges.
	 * 
	 * @param edge          The bitfield edge event to add or remove.
	 * @param isSelected    True if the bitfield edge event is selected.
	 */
	private void setBitfieldEdgeSelected(Dataset.Bitfield.State edge, boolean isSelected) {
		
		// ignore if no change or invalid
		if((isSelected && selectedBitfieldEdges.contains(edge)) || (!isSelected && !selectedBitfieldEdges.contains(edge)))
			return;
		if(!edgeButtons.containsKey(edge) || !bitfieldEdgeButtonsForEntireDataset.containsKey(edge.dataset) || bitfieldEdgesEventHandler == null)
			return;
		
		// update the model
		if(isSelected)
			selectedBitfieldEdges.add(edge);
		else
			selectedBitfieldEdges.remove(edge);
		
		// reset the duration if appropriate
		if(selectedDatasets.size() == 1 && !userSpecifiedTheDuration)
			setDuration(durationUnit == DurationUnit.SAMPLES ? Integer.toString(edge.connection.sampleRate * 10) :
			            durationUnit == DurationUnit.SECONDS ? Double.toString(10.0) :
			            durationUnit == DurationUnit.MINUTES ? Double.toString(10.0 / 60.0) :
			            durationUnit == DurationUnit.HOURS   ? Double.toString(10.0 / 60.0 / 60.0) :
			                                                   Double.toString(10.0 / 60.0 / 60.0 / 24.0), false);
		
		// update the view
		edgeButtons.get(edge).setSelected(isSelected);
		boolean allEdgesOfThisDatasetSelected = true;
		for(Dataset.Bitfield bitfield : edge.dataset.bitfields)
			for(Dataset.Bitfield.State state : bitfield.states)
				if(!selectedBitfieldEdges.contains(state))
					allEdgesOfThisDatasetSelected = false;
		bitfieldEdgeButtonsForEntireDataset.get(edge.dataset).setSelected(allEdgesOfThisDatasetSelected);
		disableDatasetsFromOtherConnections();
		
		// update the chart
		notifyHandlers();
		
	}

	/**
	 * Adds or removes a bitfield level from the list of selected levels.
	 * This method should only be called when the chart accepts bitfield levels.
	 * 
	 * @param level         The bitfield level to add or remove.
	 * @param isSelected    True if the bitfield level is selected.
	 */
	private void setBitfieldLevelSelected(Dataset.Bitfield.State level, boolean isSelected) {
		
		// ignore if no change or invalid
		if((isSelected && selectedBitfieldLevels.contains(level)) || (!isSelected && !selectedBitfieldLevels.contains(level)))
			return;
		if(!levelButtons.containsKey(level) || !bitfieldLevelButtonsForEntireDataset.containsKey(level.dataset) || bitfieldLevelsEventHandler == null)
			return;
		
		// update the model
		if(isSelected)
			selectedBitfieldLevels.add(level);
		else
			selectedBitfieldLevels.remove(level);
		
		// reset the duration if appropriate
		if(selectedDatasets.size() == 1 && !userSpecifiedTheDuration)
			setDuration(durationUnit == DurationUnit.SAMPLES ? Integer.toString(level.connection.sampleRate * 10) :
			            durationUnit == DurationUnit.SECONDS ? Double.toString(10.0) :
			            durationUnit == DurationUnit.MINUTES ? Double.toString(10.0 / 60.0) :
			            durationUnit == DurationUnit.HOURS   ? Double.toString(10.0 / 60.0 / 60.0) :
			                                                   Double.toString(10.0 / 60.0 / 60.0 / 24.0), false);
		
		// update the view
		levelButtons.get(level).setSelected(isSelected);
		boolean allLevelsOfThisDatasetSelected = true;
		for(Dataset.Bitfield bitfield : level.dataset.bitfields)
			for(Dataset.Bitfield.State state : bitfield.states)
				if(!selectedBitfieldLevels.contains(state))
					allLevelsOfThisDatasetSelected = false;
		bitfieldLevelButtonsForEntireDataset.get(level.dataset).setSelected(allLevelsOfThisDatasetSelected);
		disableDatasetsFromOtherConnections();
		
		// update the chart
		notifyHandlers();
		
	}
	
	/**
	 * Converts from a number of milliseconds to a numbers of seconds/minutes/hours/days depending on the current unit.
	 * 
	 * @param milliseconds    Number of milliseconds.
	 * @return                Corresponding number of seconds/minutes/hours/days.
	 */
	private double convertMillisecondsToDuration(long milliseconds) {
		
		return (durationUnit == DurationUnit.SECONDS) ? (milliseconds /      1_000.0) :
		       (durationUnit == DurationUnit.MINUTES) ? (milliseconds /     60_000.0) :
		       (durationUnit == DurationUnit.HOURS)   ? (milliseconds /  3_600_000.0) :
		                                                (milliseconds / 86_400_000.0);
		
	}
	
	/**
	 * Sets the duration of the chart.
	 * 
	 * @param text             The duration, which may be a sample count or number of seconds/minutes/hours/days (depending on durationUnit).
	 * @param userSpecified    If true, the user specified this duration.
	 */
	private void setDuration(String text, boolean userSpecified) {
		
		if(durationUnit == DurationUnit.SAMPLES) {
			
			// sanitize
			long newSampleCount = durationSampleCount;
			try {
				long newValue = Long.parseLong(text.trim());
				if(newValue > 0)
					newSampleCount = newValue;
			} catch(Exception e) { }
			
			// update the model
			durationSampleCount = newSampleCount;
			if(userSpecified)
				userSpecifiedTheDuration = true;
			
			// update the view
			durationTextfield.setText(Long.toString(durationSampleCount));
			
			// update the chart
			notifyHandlers();
			
		} else {
			
			// sanitize
			double newTime = convertMillisecondsToDuration(durationMilliseconds);
			long newMilliseconds = durationMilliseconds;
			try {
				double newValue = Double.parseDouble(text.trim());
				long milliseconds = (durationUnit == DurationUnit.SECONDS) ? Math.round(newValue *      1_000.0) :
				                    (durationUnit == DurationUnit.MINUTES) ? Math.round(newValue *     60_000.0) :
				                    (durationUnit == DurationUnit.HOURS)   ? Math.round(newValue *  3_600_000.0) :
				                                                             Math.round(newValue * 86_400_000.0);
				if(milliseconds > 0) {
					newTime = convertMillisecondsToDuration(milliseconds);
					newMilliseconds = milliseconds;
				}
			} catch(Exception e) { }
			
			// update the model
			durationMilliseconds = newMilliseconds;
			if(userSpecified)
				userSpecifiedTheDuration = true;
			
			// update the view
			durationTextfield.setText(Double.toString(newTime));
			
			// update the chart
			notifyHandlers();
			
		}
		
	}
	
	/**
	 * Sets how the duration is specified, and converts to the new unit if necessary.
	 * 
	 * @param newUnit    The new duration unit: DurationUnit.SAMPLES or .SECONDS or .MINUTES or .HOURS or .DAYS
	 */
	private void setDurationUnit(DurationUnit newUnit) {
		
		// update the model
		if(durationUnit == DurationUnit.SAMPLES && newUnit != DurationUnit.SAMPLES) {
			// convert from sample count to milliseconds
			int sampleRateHz = !selectedDatasets.isEmpty()                           ?       selectedDatasets.get(0).connection.sampleRate :
			                   !selectedBitfieldEdges.isEmpty()                      ?  selectedBitfieldEdges.get(0).connection.sampleRate :
			                   !selectedBitfieldLevels.isEmpty()                     ? selectedBitfieldLevels.get(0).connection.sampleRate :
			                   !ConnectionsController.telemetryConnections.isEmpty() ? ConnectionsController.telemetryConnections.get(0).sampleRate : 1000;
			durationMilliseconds = Math.round((double) durationSampleCount / (double) sampleRateHz * 1000.0);
			if(axisType == AxisType.SAMPLE_COUNT)
				axisType = AxisType.TIMESTAMPS;
		} else if(durationUnit != DurationUnit.SAMPLES && newUnit == DurationUnit.SAMPLES) {
			// convert from milliseconds to sample count
			int sampleRateHz = !selectedDatasets.isEmpty()                           ?       selectedDatasets.get(0).connection.sampleRate :
			                   !selectedBitfieldEdges.isEmpty()                      ?  selectedBitfieldEdges.get(0).connection.sampleRate :
			                   !selectedBitfieldLevels.isEmpty()                     ? selectedBitfieldLevels.get(0).connection.sampleRate :
			                   !ConnectionsController.telemetryConnections.isEmpty() ? ConnectionsController.telemetryConnections.get(0).sampleRate : 1000;
			durationSampleCount = Math.round((double) durationMilliseconds / 1000.0 * (double) sampleRateHz);
			axisType = AxisType.SAMPLE_COUNT;
		}
		durationUnit = newUnit;
		
		// update the view
		durationTextfield.setText(durationUnit == DurationUnit.SAMPLES ?                   Long.toString(durationSampleCount) :
		                          durationUnit == DurationUnit.SECONDS ?      Double.toString(durationMilliseconds / 1_000.0) :
		                          durationUnit == DurationUnit.MINUTES ?     Double.toString(durationMilliseconds / 60_000.0) :
		                          durationUnit == DurationUnit.HOURS   ?  Double.toString(durationMilliseconds / 3_600_000.0) :
		                                                                 Double.toString(durationMilliseconds / 86_400_000.0));
		durationUnitCombobox.setSelectedItem(newUnit);
		if(axisType == AxisType.SAMPLE_COUNT) {
			sampleCountMode.setSelected(true);
			sampleCountMode.setEnabled(true);
			timestampsMode.setEnabled(false);
			timeElapsedMode.setEnabled(false);
		} else {
			sampleCountMode.setEnabled(false);
			timestampsMode.setSelected(axisType == AxisType.TIMESTAMPS);
			timeElapsedMode.setSelected(axisType == AxisType.TIME_ELAPSED);
			timestampsMode.setEnabled(true);
			timeElapsedMode.setEnabled(true);
		}
		
		// update the chart
		notifyHandlers();
		
	}
	
	/**
	 * Sets how the time axis should be displayed to the user.
	 * 
	 * @param newType    The new axis type: AxisType.SAMPLE_COUNT or .TIMESTAMPS or .TIME_ELAPSED
	 */
	private void setAxisType(AxisType newType) {
		
		// ignore if invalid
		if(newType == AxisType.SAMPLE_COUNT && durationUnit != DurationUnit.SAMPLES)
			newType = axisType;
		if(newType != AxisType.SAMPLE_COUNT && durationUnit == DurationUnit.SAMPLES)
			newType = axisType;
		
		// update the model
		axisType = newType;
		
		// update the view
		if(axisType == AxisType.SAMPLE_COUNT)
			sampleCountMode.setSelected(true);
		else if(axisType == AxisType.TIMESTAMPS)
			timestampsMode.setSelected(true);
		else
			timeElapsedMode.setSelected(true);
		
		// update the chart
		notifyHandlers();
		
	}

}
