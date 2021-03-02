import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

public class WidgetTrigger extends Widget {
	
	JToggleButton modeDisabledButton;
	JToggleButton modeAutoButton;
	JToggleButton modeNormalButton;
	JToggleButton modeSingleButton;
	
	JToggleButton affectsThisChartButton;
	JToggleButton affectsEveryChartButton;
	
	JComboBox<String> typeCombobox;
	JComboBox<Dataset> channelCombobox;
	JTextField levelTextfield;
	JTextField hysteresisTextfield;
	JSlider prePostRatioSlider;
	
	PositionedChart chart;
	Consumer<Boolean> eventHandler;
	
	/**
	 * A widget that lets the user configure a trigger and all of its settings.
	 * 
	 * @param eventHandler    Will be notified when the trigger is enabled or disabled.
	 */
	public WidgetTrigger(PositionedChart chart, Consumer<Boolean> eventHandler) {
		
		super();
		
		this.chart = chart;
		this.eventHandler = eventHandler;
		
		// using a narrow border for mutually-exclusive buttons
		JToggleButton temp = new JToggleButton("_");
		Insets insets = temp.getBorder().getBorderInsets(temp);
		Border narrowBorder = new EmptyBorder(insets.top, Integer.max(insets.top, insets.bottom), insets.bottom, Integer.max(insets.top, insets.bottom));

		modeDisabledButton = new JToggleButton("Disabled");
		modeDisabledButton.setSelected(true);
		modeDisabledButton.setBorder(narrowBorder);
		modeAutoButton = new JToggleButton("Auto");
		modeAutoButton.setSelected(false);
		modeAutoButton.setBorder(narrowBorder);
		modeNormalButton = new JToggleButton("Normal");
		modeNormalButton.setSelected(false);
		modeNormalButton.setBorder(narrowBorder);
		modeSingleButton = new JToggleButton("Single");
		modeSingleButton.setSelected(false);
		modeSingleButton.setBorder(narrowBorder);
		ActionListener modeListener = event -> {
			JToggleButton button = (JToggleButton) event.getSource();
			modeDisabledButton.setSelected(button == modeDisabledButton);
			modeAutoButton.setSelected(button == modeAutoButton);
			modeNormalButton.setSelected(button == modeNormalButton);
			modeSingleButton.setSelected(button == modeSingleButton);
			update();
			resetTrigger(true);
		};
		modeDisabledButton.addActionListener(modeListener);
		modeAutoButton.addActionListener(modeListener);
		modeNormalButton.addActionListener(modeListener);
		modeSingleButton.addActionListener(modeListener);
		JPanel modeButtons = new JPanel(new MigLayout("fillx, insets 0, gap " + Theme.padding));
		modeButtons.add(modeDisabledButton, "growx");
		modeButtons.add(modeAutoButton, "growx");
		modeButtons.add(modeNormalButton, "growx");
		modeButtons.add(modeSingleButton, "growx");
		
		affectsThisChartButton = new JToggleButton("This Chart");
		affectsThisChartButton.setSelected(false);
		affectsThisChartButton.setBorder(narrowBorder);
		affectsEveryChartButton = new JToggleButton("Every Chart");
		affectsEveryChartButton.setSelected(true);
		affectsEveryChartButton.setBorder(narrowBorder);
		ActionListener affectsListener = event -> {
			JToggleButton button = (JToggleButton) event.getSource();
			affectsThisChartButton.setSelected(button == affectsThisChartButton);
			affectsEveryChartButton.setSelected(button == affectsEveryChartButton);
			update();
			resetTrigger(true);
		};
		affectsThisChartButton.addActionListener(affectsListener);
		affectsEveryChartButton.addActionListener(affectsListener);
		JPanel affectsButtons = new JPanel(new MigLayout("fillx, insets 0, gap " + Theme.padding));
		affectsButtons.add(affectsThisChartButton, "growx");
		affectsButtons.add(affectsEveryChartButton, "growx");
		
		typeCombobox = new JComboBox<String>(new String[] {"Rising Edge", "Falling Edge", "Rising and Falling Edges"});
		typeCombobox.addActionListener(event -> {
			update();
			resetTrigger(true);
		});
		
		channelCombobox = new JComboBox<Dataset>();
		ConnectionsController.telemetryConnections.forEach(connection -> {
			connection.datasets.getList().forEach(dataset -> {
				if(!dataset.isBitfield)
					channelCombobox.addItem(dataset);
			});
		});
		channelCombobox.addActionListener(event -> {
			update();
			resetTrigger(true);
		});
		String defaultUnit = channelCombobox.getItemCount() == 0 ? "" : ((Dataset) channelCombobox.getSelectedItem()).unit;
		
		levelTextfield = new JTextField("0 " + defaultUnit);
		ActionListener levelTextfieldHandler = event -> {
			String unit = "";
			if(channelCombobox.getSelectedItem() != null)
				unit = ((Dataset) channelCombobox.getSelectedItem()).unit;
			String text = levelTextfield.getText().trim();
			if(text.endsWith(unit))
				text = text.substring(0, text.length() - unit.length()).trim();
			try {
				Float.parseFloat(text);
				levelTextfield.setText(text + " " + unit);
			} catch(Exception e2) {
				levelTextfield.setText("0 " + unit);
			}
			update();
			resetTrigger(true);
		};
		levelTextfield.addActionListener(levelTextfieldHandler);
		levelTextfield.addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { levelTextfield.selectAll(); }
			@Override public void focusLost(FocusEvent e) { levelTextfieldHandler.actionPerformed(null); }
		});
		
		hysteresisTextfield = new JTextField("0 " + defaultUnit);
		ActionListener hysteresisTextfieldHandler = event -> {
			String unit = "";
			if(channelCombobox.getSelectedItem() != null)
				unit = ((Dataset) channelCombobox.getSelectedItem()).unit;
			String text = hysteresisTextfield.getText().trim();
			if(text.endsWith(unit))
				text = text.substring(0, text.length() - unit.length()).trim();
			try {
				Float.parseFloat(text);
				hysteresisTextfield.setText(text + " " + unit);
			} catch(Exception e2) {
				hysteresisTextfield.setText("0 " + unit);
			}
			update();
			resetTrigger(true);
		};
		hysteresisTextfield.addActionListener(hysteresisTextfieldHandler);
		hysteresisTextfield.addFocusListener(new FocusListener() {
			@Override public void focusGained(FocusEvent e) { hysteresisTextfield.selectAll(); }
			@Override public void focusLost(FocusEvent e) { hysteresisTextfieldHandler.actionPerformed(null); }
		});
		
		prePostRatioSlider = new JSlider();
		prePostRatioSlider.setValue(20);

		widgets.put(new JLabel("Trigger Mode: "), "");
		widgets.put(modeButtons, "span 3, growx");
		widgets.put(new JLabel("Trigger Affects: "), "");
		widgets.put(affectsButtons, "span 3, growx");
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
	
	private boolean triggerModeAuto = false;
	private boolean triggerModeSingle = false;
	private boolean triggerOnRisingEdge = false;
	private boolean triggerOnFallingEdge = false;
	private Dataset triggerChannel = null;
	private float triggerLevel = 0;
	private float triggerHysteresis = 0;
	
	/**
	 * Ensures the GUI is in sync with the variables, then notifies the event handler.
	 */
	@Override public void update() {
		
		Dataset selectedDataset = (Dataset) channelCombobox.getSelectedItem();
		if(selectedDataset == null) {
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
			if(currentDatasetsList.contains(selectedDataset))
				channelCombobox.setSelectedItem(selectedDataset);
			else
				selectedDataset = (Dataset) channelCombobox.getSelectedItem();
		}
		
		if(!levelTextfield.getText().endsWith(selectedDataset.unit)) {
			String level = levelTextfield.getText().split(" ")[0];
			levelTextfield.setText(level + " " + selectedDataset.unit);
		}
		
		if(!hysteresisTextfield.getText().endsWith(selectedDataset.unit)) {
			String hyst = hysteresisTextfield.getText().split(" ")[0];
			hysteresisTextfield.setText(hyst + " " + selectedDataset.unit);
		}
		
		if(modeDisabledButton.isSelected() || channelCombobox.getItemCount() == 0) {
			channelCombobox.setEnabled(false);
			levelTextfield.setEnabled(false);
			hysteresisTextfield.setEnabled(false);
		} else {
			channelCombobox.setEnabled(true);
			levelTextfield.setEnabled(true);
			hysteresisTextfield.setEnabled(true);
		}
		
		triggerModeAuto   = modeAutoButton.isSelected();
		triggerModeSingle = modeSingleButton.isSelected();
		triggerOnRisingEdge  = typeCombobox.getSelectedItem().toString().contains("Rising");
		triggerOnFallingEdge = typeCombobox.getSelectedItem().toString().contains("Falling");
		triggerChannel = (Dataset) channelCombobox.getSelectedItem();
		triggerLevel = 0;
		try { triggerLevel = Float.parseFloat(levelTextfield.getText().split(" ")[0]); } catch(Exception e) { }
		triggerHysteresis = 0;
		try { triggerHysteresis = Float.parseFloat(hysteresisTextfield.getText().split(" ")[0]); } catch(Exception e) { }
		
		boolean triggerEnabled = !modeDisabledButton.isSelected() && triggerChannel != null;
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
	 * Called by the chart when the user is dragging the trigger level widget.
	 * 
	 * @param newTriggerLevel    The new y-axis value that would cause a trigger.
	 */
	public void setTriggerLevel(float newTriggerLevel) {
		
		levelTextfield.setText(newTriggerLevel + "");
		update();
		
	}
	
	/**
	 * Called by the chart when the user is dragging the trigger pre/post ratio widget.
	 * 
	 * @param newPrePostRatio    The new ratio of x-axis space before and after the trigger point.
	 */
	public void setPrePostRatio(int newPrePostRatio) {
		
		prePostRatioSlider.setValue(newPrePostRatio);
		update();
		
	}
	
	/**
	 * Checks for a new trigger event if the chart is showing time as the x-axis.
	 * 
	 * @param endTimestamp    Timestamp that should be at the right edge of the plot if not triggered.
	 * @param zoomLevel       Current zoom level.
	 * @return                Timestamp that should be at the right edge of the plot.
	 */
	public long checkForTriggerMillisecondsMode(long endTimestamp, double zoomLevel) {
		
		// don't trigger if the user is time-shifting
		if(OpenGLChartsView.instance.isPausedView())
			return endTimestamp;
		
		// don't trigger if already triggered in single-trigger mode
		if(triggered && triggerModeSingle)
			return triggeredEndTimestamp;
		
		// determine which samples to test
		long chartDomain = (long) Math.ceil(chart.duration * zoomLevel);
		double preTriggerPercent = prePostRatioSlider.getValue() / 100.0;
		double postTriggerPercent = 1.0 - preTriggerPercent;
		int maxSampleNumber = triggerChannel.controller.getClosestSampleNumberAtOrBefore(endTimestamp, triggerChannel.connection.getSampleCount() - 1);
		long startTimestamp = triggerChannel.connection.getTimestamp(maxSampleNumber) - chartDomain;
		int minSampleNumber = triggerChannel.controller.getClosestSampleNumberAtOrBefore(startTimestamp, maxSampleNumber);
		if(minSampleNumber > previousMaxSampleNumber && previousMaxSampleNumber != -1)
			minSampleNumber = previousMaxSampleNumber;
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		previousMaxSampleNumber = maxSampleNumber;
		
		// don't trigger if already triggered, and it should still be on screen
		if(triggered && nextTriggerableTimestamp >= endTimestamp)
			return triggeredEndTimestamp;
		
		// not triggered, so reset if auto-trigger mode
		if(triggerModeAuto) {
			resetTrigger(false);
			if(affectsEveryChartButton.isSelected() && OpenGLChartsView.instance.isTriggeredView())
				OpenGLChartsView.instance.setLiveView();
		}
		
		// check for a new trigger
		minSampleNumber = Integer.max(minSampleNumber, triggerChannel.controller.getClosestSampleNumberAfter(nextTriggerableTimestamp));
		boolean risingEdgeArmed = false;
		boolean fallingEdgeArmed = false;
		FloatBuffer buffer = triggerChannel.getBuffer(minSampleNumber, maxSampleNumber);
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
				triggeredEndTimestamp = triggeredTimestamp + millisecondsAfterTrigger;
				if(affectsEveryChartButton.isSelected())
					OpenGLChartsView.instance.setTriggeredView(triggeredEndTimestamp, triggerChannel.connection, triggeredEndSampleNumber);
				return triggeredEndTimestamp;
			}
		}
		
		// done
		return triggered ? triggeredEndTimestamp :
		       triggerModeAuto ? endTimestamp :
		       triggerChannel.controller.getFirstTimestamp() - 1;
		
	}
	
	/**
	 * Checks for a new trigger event if the chart is showing sample numbers as the x-axis.
	 * 
	 * @param endSampleNumber    Sample number that should be at the right edge of the plot if not triggered.
	 * @param zoomLevel          Current zoom level.
	 * @return                   Sample number that should be at the right edge of the plot.
	 */
	public int checkForTriggerSampleCountMode(int endSampleNumber, double zoomLevel) {
		
		// don't trigger if the user is time-shifting
		if(OpenGLChartsView.instance.isPausedView())
			return endSampleNumber;
		
		// don't trigger if already triggered in single-trigger mode
		if(triggered && triggerModeSingle)
			return triggeredEndSampleNumber;
		
		// determine which samples to test
		int chartDomain = (int) (chart.duration * zoomLevel);
		if(chartDomain < 1)
			chartDomain = 1;
		double preTriggerPercent = prePostRatioSlider.getValue() / 100.0;
		double postTriggerPercent = 1.0 - preTriggerPercent;
		int maxSampleNumber = Integer.min(endSampleNumber, triggerChannel.connection.getSampleCount() - 1);
		int minSampleNumber = maxSampleNumber - chartDomain;
		if(minSampleNumber > previousMaxSampleNumber && previousMaxSampleNumber != -1)
			minSampleNumber = previousMaxSampleNumber;
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		previousMaxSampleNumber = maxSampleNumber;
		
		// don't trigger if already triggered, and it should still be on screen
		if(triggered && nextTriggerableSampleNumber >= maxSampleNumber)
			return triggeredEndSampleNumber;
		
		// not triggered, so reset if auto-trigger mode
		if(triggerModeAuto) {
			resetTrigger(false);
			if(affectsEveryChartButton.isSelected() && OpenGLChartsView.instance.isTriggeredView())
				OpenGLChartsView.instance.setLiveView();
		}
		
		// check for a new trigger
		minSampleNumber = Integer.max(minSampleNumber, nextTriggerableSampleNumber);
		boolean risingEdgeArmed = false;
		boolean fallingEdgeArmed = false;
		FloatBuffer buffer = triggerChannel.getBuffer(minSampleNumber, maxSampleNumber);
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
				triggeredEndSampleNumber = triggeredSampleNumber + (int) Math.round(chartDomain * postTriggerPercent);
				if(affectsEveryChartButton.isSelected())
					OpenGLChartsView.instance.setTriggeredView(triggeredEndTimestamp, triggerChannel.connection, triggeredEndSampleNumber);
				return triggeredEndSampleNumber;
			}
		}
		
		// done
		return triggered ? triggeredEndSampleNumber :
		       triggerModeAuto ? endSampleNumber :
		       -1;
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(ConnectionsController.QueueOfLines lines) {

		String mode = ChartUtils.parseString(lines.remove(), "trigger mode = %s");
		if(mode.equals(modeDisabledButton.getText())) {
			modeDisabledButton.setSelected(true);
			modeAutoButton.setSelected(false);
			modeNormalButton.setSelected(false);
			modeSingleButton.setSelected(false);
		} else if(mode.equals(modeAutoButton.getText())) {
			modeDisabledButton.setSelected(false);
			modeAutoButton.setSelected(true);
			modeNormalButton.setSelected(false);
			modeSingleButton.setSelected(false);
		} else if(mode.equals(modeNormalButton.getText())) {
			modeDisabledButton.setSelected(false);
			modeAutoButton.setSelected(false);
			modeNormalButton.setSelected(true);
			modeSingleButton.setSelected(false);
		} else if(mode.equals(modeSingleButton.getText())) {
			modeDisabledButton.setSelected(false);
			modeAutoButton.setSelected(false);
			modeNormalButton.setSelected(false);
			modeSingleButton.setSelected(true);
		} else {
			throw new AssertionError("Invalid trigger mode.");
		}
		
		String affects = ChartUtils.parseString(lines.remove(), "trigger affects = %s");
		if(affects.equals(affectsThisChartButton.getText())) {
			affectsThisChartButton.setSelected(true);
			affectsEveryChartButton.setSelected(false);
		} else if(affects.equals(affectsEveryChartButton.getText())) {
			affectsThisChartButton.setSelected(false);
			affectsEveryChartButton.setSelected(true);
		} else {
			throw new AssertionError("Invalid trigger affect.");
		}
		
		String type = ChartUtils.parseString(lines.remove(), "trigger type = %s");
		boolean found = false;
		for(int i = 0; i < typeCombobox.getItemCount(); i++) {
			if(typeCombobox.getItemAt(i).equals(type)) {
				typeCombobox.setSelectedIndex(i);
				found = true;
				break;
			}
		}
		if(!found)
			throw new AssertionError("Invalid trigger type.");
		
		String channel = ChartUtils.parseString(lines.remove(), "trigger channel = %s");
		Dataset dataset = null;
		if(channelCombobox.getItemCount() != 0 || !channel.equals("")) {
			try {
				int connectionN = Integer.parseInt(channel.split(" ")[1]);
				int datasetLocationN = Integer.parseInt(channel.split(" ")[3]);
				ConnectionTelemetry connection = (ConnectionTelemetry) ConnectionsController.allConnections.get(connectionN);
				dataset = connection.datasets.getByLocation(datasetLocationN);
				channelCombobox.setSelectedItem(dataset);
			} catch(Exception e) {
				throw new AssertionError("Invalid trigger channel.");
			}
		}
		
		String level = ChartUtils.parseString(lines.remove(), "trigger level = %s");
		try {
			Float.parseFloat(level);
			levelTextfield.setText(level + " " + dataset.unit);
		} catch(Exception e) {
			throw new AssertionError("Invalid trigger level.");
		}
		
		String hysteresis = ChartUtils.parseString(lines.remove(), "trigger hysteresis = %s");
		try {
			Float.parseFloat(hysteresis);
			hysteresisTextfield.setText(hysteresis + " " + dataset.unit);
		} catch(Exception e) {
			throw new AssertionError("Invalid trigger hysteresis.");
		}
		
		int ratio = ChartUtils.parseInteger(lines.remove(), "trigger pre/post ratio = %d");
		if(ratio < 0 || ratio > 100)
			throw new AssertionError("Invalid trigger pre/post ratio.");
		else
			prePostRatioSlider.setValue(ratio);
		
		// update the chart and reset the trigger
		update();
		resetTrigger(true);
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		Dataset dataset = (Dataset) channelCombobox.getSelectedItem();
		String channelString = dataset == null ? "" : "connection " + ConnectionsController.allConnections.indexOf(dataset.connection) + " location " + dataset.location;
		
		String mode = modeDisabledButton.isSelected() ? modeDisabledButton.getText() :
		                  modeAutoButton.isSelected() ? modeAutoButton.getText() :
		                modeNormalButton.isSelected() ? modeNormalButton.getText() :
		                                                modeSingleButton.getText();
		String affects = affectsThisChartButton.isSelected() ? affectsThisChartButton.getText() : affectsEveryChartButton.getText();
		
		return new String[] {
			"trigger mode = " + mode,
			"trigger affects = " + affects,
			"trigger type = " + typeCombobox.getSelectedItem().toString(),
			"trigger channel = " + channelString,
			"trigger level = " + levelTextfield.getText().split(" ")[0],
			"trigger hysteresis = " + hysteresisTextfield.getText().split(" ")[0],
			"trigger pre/post ratio = " + prePostRatioSlider.getValue()
		};
		
	}

}
