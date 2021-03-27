import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

public class WidgetFrequencyDomainType extends Widget {
	
	final int waveformRowCountUpperLimit = 1000;
	final int waveformRowCountLowerLimit = 2;
	final int waveformRowCountDefault    = 60;
	final int dftCountUpperLimit = 100;
	final int dftCountLowerLimit = 2;
	final int dftCountDefault    = 20;
	
	JToggleButton singleMode;
	JToggleButton multipleMode;
	JToggleButton waterfallMode;
	
	JLabel dftCountLabel;
	JTextField dftCountTextfield;
	JLabel rowCountLabel;
	JTextField rowCountTextfield;
	
	Consumer<String> modeHandler;
	Consumer<Integer> dftCountHandler;
	Consumer<Integer> rowCountHandler;
	
	/**
	 * A widget that lets the user specify the mode for a frequency domain chart.
	 * 
	 * The mode can be "Single" or "Multiple" or "Waterfall".
	 * For the waterfall type, the user specifies a DFT count.
	 * For the multiple type, the user specifies a DFT count and row count.
	 * 
	 * @param modeEventHandler        Will be notified when the value changes.
	 * @param dftCountEventHandler    Will be notified when the value changes.
	 * @param rowCountEventHandler    Will be notified when the value changes.
	 */
	public WidgetFrequencyDomainType(Consumer<String> modeEventHandler, Consumer<Integer> dftCountEventHandler, Consumer<Integer> rowCountEventHandler) {
		
		super();
		
		singleMode = new JToggleButton("Single", true);
		singleMode.setBorder(Theme.narrowButtonBorder);
		singleMode.addActionListener(event -> sanityCheck());
		multipleMode = new JToggleButton("Multiple", false);
		multipleMode.setBorder(Theme.narrowButtonBorder);
		multipleMode.addActionListener(event -> sanityCheck());
		waterfallMode = new JToggleButton("Waterfall", false);
		waterfallMode.setBorder(Theme.narrowButtonBorder);
		waterfallMode.addActionListener(event -> sanityCheck());
		ButtonGroup group = new ButtonGroup();
		group.add(singleMode);
		group.add(multipleMode);
		group.add(waterfallMode);
		
		dftCountLabel = new JLabel("DFT Count: ");
		dftCountTextfield = new JTextField(Integer.toString(dftCountDefault));
		dftCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { dftCountTextfield.selectAll(); }
		});
		dftCountTextfield.addActionListener(event -> sanityCheck());
		
		rowCountLabel = new JLabel("Row Count: ");
		rowCountTextfield = new JTextField(Integer.toString(waveformRowCountDefault));
		rowCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { rowCountTextfield.selectAll(); }
		});
		rowCountTextfield.addActionListener(event -> sanityCheck());

		modeHandler = modeEventHandler;
		dftCountHandler = dftCountEventHandler;
		rowCountHandler = rowCountEventHandler;
		
		widgets.put(new JLabel("Mode: "), "");
		widgets.put(singleMode, "span 3, split 3, growx");
		widgets.put(multipleMode, "growx");
		widgets.put(waterfallMode, "growx");
		widgets.put(dftCountLabel, "");
		widgets.put(dftCountTextfield, "span 3, growx");
		widgets.put(rowCountLabel, "");
		widgets.put(rowCountTextfield, "span 3, growx");
		
		sanityCheck();
		
	}
	
	/**
	 * Ensures the DFT count and waveform row count are within the allowed ranges.
	 * Shows or hides widgets based on the selected chart mode.
	 * Notifies all handlers.
	 */
	public void sanityCheck() {
		
		// DFT count
		try {
			int dftCount = Integer.parseInt(dftCountTextfield.getText().trim());
			if(dftCount > dftCountUpperLimit) dftCount = dftCountUpperLimit;
			if(dftCount < dftCountLowerLimit) dftCount = dftCountLowerLimit;
			dftCountTextfield.setText(Integer.toString(dftCount));
			dftCountHandler.accept(dftCount);
		} catch(Exception e) {
			dftCountTextfield.setText(Integer.toString(dftCountDefault));
			dftCountHandler.accept(dftCountDefault);
		}
		
		// waveform row count
		try {
			int count = Integer.parseInt(rowCountTextfield.getText().trim());
			if(count > waveformRowCountUpperLimit) count = waveformRowCountUpperLimit;
			if(count < waveformRowCountLowerLimit) count = waveformRowCountLowerLimit;
			rowCountTextfield.setText(Integer.toString(count));
			rowCountHandler.accept(count);
		} catch(Exception e) {
			rowCountTextfield.setText(Integer.toString(waveformRowCountDefault));
			rowCountHandler.accept(waveformRowCountDefault);
		}
		
		// show/hide widgets as needed
		boolean multipleViewMode  = multipleMode.isSelected();
		boolean waterfallViewMode = waterfallMode.isSelected();
		dftCountLabel.setVisible(multipleViewMode || waterfallViewMode);
		dftCountTextfield.setVisible(multipleViewMode || waterfallViewMode);
		rowCountLabel.setVisible(multipleViewMode);
		rowCountTextfield.setVisible(multipleViewMode);
		
		modeHandler.accept(singleMode.isSelected()   ? "Single" :
		                   multipleMode.isSelected() ? "Multiple" :
		                	                           "Waterfall");
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(ConnectionsController.QueueOfLines lines) {

		// parse the text
		String mode          = ChartUtils.parseString (lines.remove(), "mode = %s");
		int dftCount         = ChartUtils.parseInteger(lines.remove(), "dft count = %d");
		int waveformRowCount = ChartUtils.parseInteger(lines.remove(), "waveform view row count = %d");
		
		if(!mode.equals("Single") && !mode.equals("Multiple") && !mode.equals("Waterfall"))
			throw new AssertionError("Invalid Frequency Domain Chart mode.");
		
		// update the widgets
		if(mode.equals("Single"))
			singleMode.setSelected(true);
		else if(mode.equals("Multiple"))
			multipleMode.setSelected(true);
		else if(mode.equals("Waterfall"))
			waterfallMode.setSelected(true);
		dftCountTextfield.setText(Integer.toString(dftCount));
		rowCountTextfield.setText(Integer.toString(waveformRowCount));
		
		// update the chart
		sanityCheck();
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		String mode = singleMode.isSelected()   ? "Single"   :
		              multipleMode.isSelected() ? "Multiple" :
		                                          "Waterfall";
		
		return new String[] {
			"mode = "                    + mode,
			"dft count = "               + dftCountTextfield.getText(),
			"waveform view row count = " + rowCountTextfield.getText()
		};
		
	}

}
