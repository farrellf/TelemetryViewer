import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

public class WidgetTrigger extends Widget {
	
	enum Mode {
		DISABLED { @Override public String toString() { return "Disabled"; } },
		AUTO     { @Override public String toString() { return "Auto";     } },
		NORMAL   { @Override public String toString() { return "Normal";   } },
		SINGLE   { @Override public String toString() { return "Single";   } }
	};
	
	enum Type {
		RISING_EDGE          { @Override public String toString() { return "Rising Edge";              } },
		FALLING_EDGE         { @Override public String toString() { return "Falling Edge";             } },
		RISING_FALLING_EDGES { @Override public String toString() { return "Rising and Falling Edges"; } }
	};
	
	// "model"
	private Mode    triggerMode = Mode.DISABLED;
	private boolean triggerAffectsEveryChart = false;
	private Type    triggerType = Type.RISING_EDGE;
	private Dataset triggerChannel = null;
	private StorageFloats.Cache samplesCache = null;
	private float   triggerLevel = 0;
	private float   triggerHysteresis = 0;
	private int     triggerPrePostRatio = 20;
	private boolean userSpecifiedTheChannel = false;
	
	// "view"
	private JToggleButton modeDisabledButton;
	private JToggleButton modeAutoButton;
	private JToggleButton modeNormalButton;
	private JToggleButton modeSingleButton;
	
	private JToggleButton affectsThisChartButton;
	private JToggleButton affectsEveryChartButton;
	
	private JComboBox<Type> typeCombobox;
	private JComboBox<Dataset> channelCombobox;
	private JTextField levelTextfield;
	private JTextField hysteresisTextfield;
	private JSlider prePostRatioSlider;
	
	// "controller"
	private PositionedChart chart;
	private Consumer<Boolean> eventHandler;
	
	private void setMode(Mode newMode) {
		
		// never ignore, even if unchanged, so clicking "single" can re-arm the trigger
		
		// update the model
		triggerMode = newMode;
		resetTrigger(true);
		
		// update the view
		update();
		
	}
	
	private void setAffectsEveryChart(boolean newAffect) {
		
		// ignore if unchanged
		if(newAffect == triggerAffectsEveryChart)
			return;
		
		// update the model
		triggerAffectsEveryChart = newAffect;
		resetTrigger(true);
		
		// update the view
		update();
		
	}
	
	private void setType(Type newType) {
		
		// ignore if unchanged
		if(newType == triggerType)
			return;
		
		// update the model
		triggerType = newType;
		resetTrigger(true);

		// update the view
		update();
		
	}
	
	private void setChannel(Dataset newChannel, boolean userSpecified) {
		
		// ignore if unchanged
		if(newChannel == triggerChannel)
			return;
		
		// update the model
		triggerChannel = newChannel;
		if(triggerChannel != null)
			samplesCache = triggerChannel.createCache();
		resetTrigger(true);
		if(userSpecified)
			userSpecifiedTheChannel = true;

		// update the view
		update();
		
	}
	
	public void setLevel(float newLevel, boolean resetTrigger) {
		
		// ignore if unchanged
		if(newLevel == triggerLevel)
			return;
		
		// update the model
		triggerLevel = newLevel;
		if(resetTrigger)
			resetTrigger(true);

		// update the view
		update();
		
	}
	
	private void setHysteresis(float newHysteresis) {
		
		// ignore if unchanged
		if(newHysteresis == triggerHysteresis)
			return;
		
		// update the model
		triggerHysteresis = newHysteresis;
		resetTrigger(true);

		// update the view
		update();
		
	}
	
	public void setPrePostRatio(int newRatio, boolean resetTrigger) {
		
		// ignore if unchanged
		if(newRatio == triggerPrePostRatio)
			return;
		
		// update the model but don't reset the trigger
		triggerPrePostRatio = newRatio;
		if(resetTrigger)
			resetTrigger(true);
		
		// update the view
		update();
		
	}
	
	public void setDefaultChannel(Dataset dataset) {
		
		if(!userSpecifiedTheChannel)
			setChannel(dataset, false);
		
	}
	
	/**
	 * A widget that lets the user configure a trigger and all of its settings.
	 * 
	 * @param eventHandler    Will be notified when the trigger is enabled or disabled.
	 */
	public WidgetTrigger(PositionedChart chart, Consumer<Boolean> eventHandler) {
		
		super();
		
		this.chart = chart;
		this.eventHandler = eventHandler;
		
		// trigger mode
		modeDisabledButton = new JToggleButton(Mode.DISABLED.toString(), triggerMode == Mode.DISABLED);
		modeAutoButton     = new JToggleButton(Mode.AUTO.toString(),     triggerMode == Mode.AUTO);
		modeNormalButton   = new JToggleButton(Mode.NORMAL.toString(),   triggerMode == Mode.NORMAL);
		modeSingleButton   = new JToggleButton(Mode.SINGLE.toString(),   triggerMode == Mode.SINGLE);
		modeDisabledButton.setBorder(Theme.narrowButtonBorder);
		modeAutoButton.setBorder(Theme.narrowButtonBorder);
		modeNormalButton.setBorder(Theme.narrowButtonBorder);
		modeSingleButton.setBorder(Theme.narrowButtonBorder);
		modeDisabledButton.addActionListener(event -> setMode(Mode.DISABLED));
		modeAutoButton.addActionListener(event -> setMode(Mode.AUTO));
		modeNormalButton.addActionListener(event -> setMode(Mode.NORMAL));
		modeSingleButton.addActionListener(event -> setMode(Mode.SINGLE));
		ButtonGroup group1 = new ButtonGroup();
		group1.add(modeDisabledButton);
		group1.add(modeAutoButton);
		group1.add(modeNormalButton);
		group1.add(modeSingleButton);
		
		// trigger affects this chart or all charts
		affectsThisChartButton = new JToggleButton("This Chart", !triggerAffectsEveryChart);
		affectsEveryChartButton = new JToggleButton("Every Chart", triggerAffectsEveryChart);
		affectsThisChartButton.setBorder(Theme.narrowButtonBorder);
		affectsEveryChartButton.setBorder(Theme.narrowButtonBorder);
		affectsThisChartButton.addActionListener(event -> setAffectsEveryChart(!affectsThisChartButton.isSelected()));
		affectsEveryChartButton.addActionListener(event -> setAffectsEveryChart(!affectsThisChartButton.isSelected()));
		ButtonGroup group2 = new ButtonGroup();
		group2.add(affectsThisChartButton);
		group2.add(affectsEveryChartButton);
		
		// trigger type
		typeCombobox = new JComboBox<Type>(Type.values());
		typeCombobox.setSelectedItem(triggerType);
		typeCombobox.addActionListener(event -> setType((Type) typeCombobox.getSelectedItem()));
		
		// trigger channel
		channelCombobox = new JComboBox<Dataset>();
		ConnectionsController.telemetryConnections.forEach(connection -> {
			connection.datasets.getList().forEach(dataset -> {
				if(!dataset.isBitfield)
					channelCombobox.addItem(dataset);
			});
		});
		if(channelCombobox.getItemCount() > 0)
			triggerChannel = (Dataset) channelCombobox.getSelectedItem();
		channelCombobox.addActionListener(event -> setChannel((Dataset) channelCombobox.getSelectedItem(), true));
		
		String defaultUnit = channelCombobox.getItemCount() == 0 ? "" : " " + ((Dataset) channelCombobox.getSelectedItem()).unit;
		
		// trigger level
		levelTextfield = new JTextField(Float.toString(triggerLevel) + defaultUnit);
		ActionListener levelTextfieldHandler = event -> {
			String unit = channelCombobox.getSelectedItem() == null ? "" : ((Dataset) channelCombobox.getSelectedItem()).unit;
			try {
				String text = levelTextfield.getText().trim();
				if(text.endsWith(unit))
					text = text.substring(0, text.length() - unit.length()).trim();
				setLevel(Float.parseFloat(text), true);
			} catch(Exception e2) {
				levelTextfield.setText(Float.toString(triggerLevel) + " " + unit);
			}
		};
		levelTextfield.addActionListener(levelTextfieldHandler);
		levelTextfield.addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { levelTextfield.selectAll(); }
			@Override public void focusLost(FocusEvent e) { levelTextfieldHandler.actionPerformed(null); }
		});
		
		// trigger hysteresis
		hysteresisTextfield = new JTextField(Float.toString(triggerHysteresis) + defaultUnit);
		ActionListener hysteresisTextfieldHandler = event -> {
			String unit = channelCombobox.getSelectedItem() == null ? "" : ((Dataset) channelCombobox.getSelectedItem()).unit;
			try {
				String text = hysteresisTextfield.getText().trim();
				if(text.endsWith(unit))
					text = text.substring(0, text.length() - unit.length()).trim();
				setHysteresis(Float.parseFloat(text));
			} catch(Exception e2) {
				hysteresisTextfield.setText(Float.toString(triggerHysteresis) + " " + unit);
			}
		};
		hysteresisTextfield.addActionListener(hysteresisTextfieldHandler);
		hysteresisTextfield.addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { hysteresisTextfield.selectAll(); }
			@Override public void focusLost(FocusEvent e) { hysteresisTextfieldHandler.actionPerformed(null); }
		});
		
		prePostRatioSlider = new JSlider();
		prePostRatioSlider.setValue(triggerPrePostRatio);
		prePostRatioSlider.addChangeListener(event -> setPrePostRatio(prePostRatioSlider.getValue(), true));

		widgets.put(new JLabel("Trigger Mode: "), "");
		widgets.put(modeDisabledButton, "span 3, split 4, growx");
		widgets.put(modeAutoButton, "growx");
		widgets.put(modeNormalButton, "growx");
		widgets.put(modeSingleButton, "growx");
		widgets.put(new JLabel("Trigger Affects: "), "");
		widgets.put(affectsThisChartButton, "span 3, split 2, growx");
		widgets.put(affectsEveryChartButton, "growx");
		widgets.put(new JLabel("Trigger Type: "), "");
		widgets.put(typeCombobox, "span 3, growx");
		widgets.put(new JLabel("Trigger Channel: "), "");
		widgets.put(channelCombobox, "span 3, growx");
		widgets.put(new JLabel("Trigger Level: "), "");
		widgets.put(levelTextfield, "span 3, growx");
		widgets.put(new JLabel("Trigger Hysteresis: "), "");
		widgets.put(hysteresisTextfield, "span 3, growx");
		widgets.put(new JLabel("Trigger Pre/Post Ratio: "), "");
		widgets.put(prePostRatioSlider, "span 3, growx");
		
		SwingUtilities.invokeLater(() -> update()); // invokeLater so the constructor finishes before the chart is updated
		
	}
	
	/**
	 * Ensures the GUI is in sync with the variables, then notifies the event handler.
	 */
	@Override public void update() {
		
		modeDisabledButton.setSelected(triggerMode == Mode.DISABLED);
		modeAutoButton.setSelected(triggerMode == Mode.AUTO);
		modeNormalButton.setSelected(triggerMode == Mode.NORMAL);
		modeSingleButton.setSelected(triggerMode == Mode.SINGLE);
		
		affectsThisChartButton.setSelected(!triggerAffectsEveryChart);
		affectsEveryChartButton.setSelected(triggerAffectsEveryChart);
		
		typeCombobox.setSelectedItem(triggerType);
		
		channelCombobox.setSelectedItem(triggerChannel);
		if(triggerChannel == null) {
			eventHandler.accept(false);
			return;
		}
		
		List<Dataset> oldDatasetsList = new ArrayList<Dataset>();
		for(int i = 0; i < channelCombobox.getItemCount(); i++)
			oldDatasetsList.add(channelCombobox.getItemAt(i));
		
		List<Dataset> currentDatasetsList = new ArrayList<Dataset>();
		ConnectionsController.telemetryConnections.forEach(connection -> {
			connection.datasets.getList().forEach(dataset -> {
				if(!dataset.isBitfield)
					currentDatasetsList.add(dataset);
			});
		});
		
		boolean datasetsChanged = !oldDatasetsList.containsAll(currentDatasetsList) || !currentDatasetsList.containsAll(oldDatasetsList);
		if(datasetsChanged) {
			channelCombobox.removeAllItems();
			currentDatasetsList.forEach(dataset -> channelCombobox.addItem(dataset));
			if(currentDatasetsList.contains(triggerChannel))
				channelCombobox.setSelectedItem(triggerChannel);
			else {
				setChannel((Dataset) channelCombobox.getSelectedItem(), false);
				return;
			}
		}
		
		levelTextfield.setText(Float.toString(triggerLevel) + " " + triggerChannel.unit);
		hysteresisTextfield.setText(Float.toString(triggerHysteresis) + " " + triggerChannel.unit);
		prePostRatioSlider.setValue(triggerPrePostRatio);
		
		if(modeDisabledButton.isSelected() || channelCombobox.getItemCount() == 0) {
			channelCombobox.setEnabled(false);
			levelTextfield.setEnabled(false);
			hysteresisTextfield.setEnabled(false);
		} else {
			channelCombobox.setEnabled(true);
			levelTextfield.setEnabled(true);
			hysteresisTextfield.setEnabled(true);
		}
		
		boolean triggerEnabled = triggerMode != Mode.DISABLED && triggerChannel != null;
		eventHandler.accept(triggerEnabled);
		
	}
	
	private boolean triggered = false;
	private int triggeredSampleNumber = -1;
	private int triggeredEndSampleNumber = -1;
	private long triggeredTimestamp = -1;
	private long triggeredEndTimestamp = -1;
	private int nextTriggerableSampleNumber = -1;
	private long nextTriggerableTimestamp = -1;
	private int previousMaxSampleNumber = -1;
	
	/**
	 * Prepares for detecting the next trigger event.
	 * 
	 * @param resetNextTriggerPoint    If true, also allow the next trigger point to occur before the current trigger point.
	 */
	public void resetTrigger(boolean resetNextTriggerPoint) {
		triggered = false;
		triggeredSampleNumber = -1;
		triggeredEndSampleNumber = -1;
		triggeredTimestamp = -1;
		triggeredEndTimestamp = -1;
		previousMaxSampleNumber = -1;
		triggeredMinSampleNumber = -1;
		if(resetNextTriggerPoint) {
			nextTriggerableSampleNumber = -1;
			nextTriggerableTimestamp = -1;
			if(OpenGLChartsView.instance.isTriggeredView())
		      	  OpenGLChartsView.instance.switchToLiveView();
		}
	}
	
	/**
	 * Similar to resetTrigger(), and allows the next trigger point to occur before the current trigger point, but does NOT switch back to Live View.
	 * 
	 * This method is called when the user is dragging the trigger level or trigger pre/post ratio widgets.
	 * This allows a live redraw of where a new trigger would have occurred.
	 */
	public void clearTrigger() {
		triggered = false;
		triggeredSampleNumber = -1;
		triggeredEndSampleNumber = -1;
		triggeredTimestamp = -1;
		triggeredEndTimestamp = -1;
		previousMaxSampleNumber = -1;
		nextTriggerableSampleNumber = -1;
		nextTriggerableTimestamp = -1;
	}
	
	/**
	 * Called by the chart so it can draw a marker at the trigger point.
	 * 
	 * @return    The triggered sample number, or -1 if not triggered.
	 */
	public int getTriggeredSampleNumber() {
		
		return triggered ? triggeredSampleNumber : -1;
		
	}
	
	/**
	 * Called by the chart so it can draw a marker at the trigger level.
	 * 
	 * @return    The y-axis value that would cause a trigger.
	 */
	public float getTriggerLevel() {
		
		return triggerLevel;
		
	}
	
	/**
	 * Checks for a new trigger event if the chart is showing time as the x-axis.
	 * 
	 * @param endTimestamp     Timestamp that should be at the right edge of the plot if not triggered.
	 * @param zoomLevel        Current zoom level.
	 * @param recalcTrigger    If true, force recalculation of the trigger.
	 * @return                 Timestamp that should be at the right edge of the plot.
	 */
	public long checkForTriggerMillisecondsMode(long endTimestamp, double zoomLevel, boolean recalcTrigger) {
		
		// recalculate the trigger if the user is dragging the trigger level or pre/post markers
		if(recalcTrigger)
			clearTrigger();
		
		// don't trigger if the user is time-shifting
		if(OpenGLChartsView.instance.isPausedView())
			return endTimestamp;
		
		// don't trigger if already triggered in single-trigger mode
		if(triggered && triggerMode == Mode.SINGLE)
			return triggeredEndTimestamp;
		
		// determine which samples to test
		long chartDomain = (long) Math.ceil(chart.duration * zoomLevel);
		double preTriggerPercent = triggerPrePostRatio / 100.0;
		double postTriggerPercent = 1.0 - preTriggerPercent;
		int maxSampleNumber = triggerChannel.controller.getClosestSampleNumberAtOrBefore(endTimestamp, triggerChannel.connection.getSampleCount() - 1);
		long startTimestamp = triggerChannel.connection.getTimestamp(maxSampleNumber) - chartDomain;
		int minSampleNumber = triggerChannel.controller.getClosestSampleNumberAtOrBefore(startTimestamp, maxSampleNumber);
		if(minSampleNumber > previousMaxSampleNumber && previousMaxSampleNumber != -1)
			minSampleNumber = previousMaxSampleNumber;
		if(recalcTrigger && triggeredMinSampleNumber != -1)
			minSampleNumber = triggeredMinSampleNumber;
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		previousMaxSampleNumber = maxSampleNumber;
		
		// don't trigger if already triggered, and it should still be on screen
		if(triggered && nextTriggerableTimestamp >= endTimestamp)
			return triggeredEndTimestamp;
		
		// not triggered, so reset if auto-trigger mode
		if(triggerMode == Mode.AUTO) {
			resetTrigger(false);
			if(triggerAffectsEveryChart && OpenGLChartsView.instance.isTriggeredView())
				OpenGLChartsView.instance.setLiveView();
		}
		
		// check for a new trigger
		minSampleNumber = Integer.max(minSampleNumber, triggerChannel.controller.getClosestSampleNumberAfter(nextTriggerableTimestamp));
		boolean triggerOnRisingEdge  = (triggerType == Type.RISING_EDGE)  || (triggerType == Type.RISING_FALLING_EDGES);
		boolean triggerOnFallingEdge = (triggerType == Type.FALLING_EDGE) || (triggerType == Type.RISING_FALLING_EDGES);
		boolean risingEdgeArmed = false;
		boolean fallingEdgeArmed = false;
		FloatBuffer buffer = triggerChannel.getSamplesBuffer(minSampleNumber, maxSampleNumber, samplesCache);
		for(int sampleNumber = minSampleNumber; sampleNumber <= maxSampleNumber; sampleNumber++) {
			float value = buffer.get(sampleNumber - minSampleNumber);
			if(triggerOnRisingEdge && value < triggerLevel - triggerHysteresis)
				risingEdgeArmed = true;
			if(triggerOnFallingEdge && value > triggerLevel + triggerHysteresis)
				fallingEdgeArmed = true;
			if((risingEdgeArmed && triggerOnRisingEdge && value >= triggerLevel) || (fallingEdgeArmed && triggerOnFallingEdge && value <= triggerLevel)) {
				triggeredSampleNumber = sampleNumber;
				triggeredTimestamp = triggerChannel.connection.getTimestamp(sampleNumber);
				triggered = true;
				nextTriggerableTimestamp = triggeredTimestamp + (long) Math.round(chartDomain * postTriggerPercent);
				long millisecondsAfterTrigger = (long) Math.round(chartDomain * postTriggerPercent);
				triggeredMinSampleNumber = minSampleNumber;
				triggeredEndTimestamp = triggeredTimestamp + millisecondsAfterTrigger;
				if(triggerAffectsEveryChart)
					OpenGLChartsView.instance.setTriggeredView(triggeredEndTimestamp, triggerChannel.connection, triggeredEndSampleNumber);
				return triggeredEndTimestamp;
			}
		}
		
		// done
		return triggered ? triggeredEndTimestamp :
		       triggerMode == Mode.AUTO ? endTimestamp :
		       triggerChannel.controller.getFirstTimestamp() - 1;
		
	}
	
	int triggeredMinSampleNumber;
	
	/**
	 * Checks for a new trigger event if the chart is showing sample numbers as the x-axis.
	 * 
	 * @param endSampleNumber    Sample number that should be at the right edge of the plot if not triggered.
	 * @param zoomLevel          Current zoom level.
	 * @param recalcTrigger      If true, force recalculation of the trigger.
	 * @return                   Sample number that should be at the right edge of the plot.
	 */
	public int checkForTriggerSampleCountMode(int endSampleNumber, double zoomLevel, boolean recalcTrigger) {
		
		// recalculate the trigger if the user is dragging the trigger level or pre/post markers
		if(recalcTrigger)
			clearTrigger();
		
		// don't trigger if the user is time-shifting
		if(OpenGLChartsView.instance.isPausedView())
			return endSampleNumber;
		
		// don't trigger if already triggered in single-trigger mode
		if(triggered && triggerMode == Mode.SINGLE)
			return triggeredEndSampleNumber;
		
		// determine which samples to test
		int chartDomain = (int) ((chart.duration - 1) * zoomLevel);
		if(chartDomain < 1)
			chartDomain = 1;
		double preTriggerPercent = triggerPrePostRatio / 100.0;
		double postTriggerPercent = 1.0 - preTriggerPercent;
		int maxSampleNumber = Integer.min(endSampleNumber, triggerChannel.connection.getSampleCount() - 1);
		int minSampleNumber = maxSampleNumber - chartDomain;
		if(minSampleNumber > previousMaxSampleNumber && previousMaxSampleNumber != -1)
			minSampleNumber = previousMaxSampleNumber;
		if(recalcTrigger && triggeredMinSampleNumber != -1)
			minSampleNumber = triggeredMinSampleNumber;
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		previousMaxSampleNumber = maxSampleNumber;
		
		// don't trigger if already triggered, and it should still be on screen
		if(triggered && nextTriggerableSampleNumber >= maxSampleNumber)
			return triggeredEndSampleNumber;
		
		// not triggered, so reset if auto-trigger mode
		if(triggerMode == Mode.AUTO) {
			resetTrigger(false);
			if(triggerAffectsEveryChart && OpenGLChartsView.instance.isTriggeredView())
				OpenGLChartsView.instance.setLiveView();
		}
		
		// check for a new trigger
		minSampleNumber = Integer.max(minSampleNumber, nextTriggerableSampleNumber);
		boolean triggerOnRisingEdge  = (triggerType == Type.RISING_EDGE)  || (triggerType == Type.RISING_FALLING_EDGES);
		boolean triggerOnFallingEdge = (triggerType == Type.FALLING_EDGE) || (triggerType == Type.RISING_FALLING_EDGES);
		boolean risingEdgeArmed = false;
		boolean fallingEdgeArmed = false;
		FloatBuffer buffer = triggerChannel.getSamplesBuffer(minSampleNumber, maxSampleNumber, samplesCache);
		for(int sampleNumber = minSampleNumber; sampleNumber <= maxSampleNumber; sampleNumber++) {
			float value = buffer.get(sampleNumber - minSampleNumber);
			if(triggerOnRisingEdge && value < triggerLevel - triggerHysteresis)
				risingEdgeArmed = true;
			if(triggerOnFallingEdge && value > triggerLevel + triggerHysteresis)
				fallingEdgeArmed = true;
			if((risingEdgeArmed && triggerOnRisingEdge && value >= triggerLevel) || (fallingEdgeArmed && triggerOnFallingEdge && value <= triggerLevel)) {
				triggeredSampleNumber = sampleNumber;
				triggered = true;
				nextTriggerableSampleNumber = triggeredSampleNumber + (int) Math.round(chartDomain * postTriggerPercent);
				long triggeredTimestamp = triggerChannel.connection.getTimestamp(triggeredSampleNumber);
				long millisecondsAfterTrigger = (long) ((chartDomain / triggerChannel.connection.sampleRate * 1000) * postTriggerPercent);
				long triggeredEndTimestamp = triggeredTimestamp + millisecondsAfterTrigger;
				triggeredMinSampleNumber = minSampleNumber;
				triggeredEndSampleNumber = triggeredSampleNumber + (int) Math.round(chartDomain * postTriggerPercent);
				if(triggerAffectsEveryChart)
					OpenGLChartsView.instance.setTriggeredView(triggeredEndTimestamp, triggerChannel.connection, triggeredEndSampleNumber);
				return triggeredEndSampleNumber;
			}
		}
		
		// done
		return triggered ? triggeredEndSampleNumber :
		       triggerMode == Mode.AUTO ? endSampleNumber :
		       -1;
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(ConnectionsController.QueueOfLines lines) {

		String mode = ChartUtils.parseString(lines.remove(), "trigger mode = %s");
		Mode newMode = null;
		for(Mode option : Mode.values())
			if(option.toString().equals(mode))
				newMode = option;
		if(newMode == null)
			throw new AssertionError("Invalid trigger mode.");
		setMode(newMode);
		
		String affects = ChartUtils.parseString(lines.remove(), "trigger affects = %s");
		if(affects.equals("Every Chart"))
			setAffectsEveryChart(true);
		else if(affects.equals("This Chart"))
			setAffectsEveryChart(false);
		else
			throw new AssertionError("Invalid trigger affect.");
		
		String type = ChartUtils.parseString(lines.remove(), "trigger type = %s");
		Type newType = null;
		for(Type option : Type.values())
			if(option.toString().equals(type))
				newType = option;
		if(newType == null)
			throw new AssertionError("Invalid trigger type.");
		setType(newType);
		
		String channel = ChartUtils.parseString(lines.remove(), "trigger channel = %s");
		if(channelCombobox.getItemCount() != 0 || !channel.equals("")) {
			try {
				int connectionN = Integer.parseInt(channel.split(" ")[1]);
				int datasetLocationN = Integer.parseInt(channel.split(" ")[3]);
				ConnectionTelemetry connection = (ConnectionTelemetry) ConnectionsController.allConnections.get(connectionN);
				Dataset dataset = connection.datasets.getByLocation(datasetLocationN);
				setChannel(dataset, false);
			} catch(Exception e) {
				throw new AssertionError("Invalid trigger channel.");
			}
		}
		
		String level = ChartUtils.parseString(lines.remove(), "trigger level = %s");
		try {
			setLevel(Float.parseFloat(level), true);
		} catch(Exception e) {
			throw new AssertionError("Invalid trigger level.");
		}
		
		String hysteresis = ChartUtils.parseString(lines.remove(), "trigger hysteresis = %s");
		try {
			setHysteresis(Float.parseFloat(hysteresis));
		} catch(Exception e) {
			throw new AssertionError("Invalid trigger hysteresis.");
		}
		
		int ratio = ChartUtils.parseInteger(lines.remove(), "trigger pre/post ratio = %d");
		if(ratio < 0 || ratio > 100)
			throw new AssertionError("Invalid trigger pre/post ratio.");
		else
			setPrePostRatio(ratio, true);
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		Dataset dataset = (Dataset) channelCombobox.getSelectedItem();
		String channelString = dataset == null ? "" : "connection " + ConnectionsController.allConnections.indexOf(dataset.connection) + " location " + dataset.location;
		
		return new String[] {
			"trigger mode = " + triggerMode.toString(),
			"trigger affects = " + (triggerAffectsEveryChart ? "Every Chart" : "This Chart"),
			"trigger type = " + triggerType.toString(),
			"trigger channel = " + channelString,
			"trigger level = " + triggerLevel,
			"trigger hysteresis = " + triggerHysteresis,
			"trigger pre/post ratio = " + triggerPrePostRatio
		};
		
	}

}
