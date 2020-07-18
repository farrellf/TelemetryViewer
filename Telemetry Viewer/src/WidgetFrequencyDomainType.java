import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class WidgetFrequencyDomainType extends Widget {
	
	JComboBox<String> typeCombobox;
	JLabel sampleCountLabel;
	JLabel totalSampleCountLabel;
	JLabel rowCountLabel;
	JTextField sampleCountTextfield;
	JTextField totalSampleCountTextfield;
	JTextField rowCountTextfield;
	int sampleCountUpperLimit;
	int sampleCountLowerLimit;
	int defaultSampleCount;
	int defaultTotalSampleCount;
	int waveformRowCountUpperLimit;
	int waveformRowCountLowerLimit;
	int defaultWaveformRowCount;
	Consumer<String> typeHandler;
	Consumer<Integer> sampleCountHandler;
	Consumer<Integer> totalSampleCountHandler;
	Consumer<Integer> rowCountHandler;
	
	/**
	 * A widget that lets the user specify the type for a frequency domain chart.
	 * 
	 * The type can be "Live View" or "Waveform View" or "Waterfall View".
	 * For all types, the user specifies the sample count (DFT window length).
	 * For the waveform and waterfall types, the user also specifies the total sample count.
	 * The total sample count must be greater than the sample count, and it must be a multiple of the sample count.
	 * 
	 * @param defaultSampleCount              Default sample count.
	 * @param defaultTotalSampleCount         Default total sample count.
	 * @param sampleCountLowerLimit           Minimum allowed sample count or total sample count.
	 * @param sampleCountUpperLimit           Maximum allowed sample count or total sample count.
	 * @param defaultWaveformRowCount         Default y-axis bin count for Waveform View.
	 * @param waveformRowCountLowerLimit      Minimum allowed y-axis bin count for Waveform View.
	 * @param waveformRowCountUpperLimit      Maximum allowed y-axis bin count for Waveform View.
	 * @param typeEventHandler                Will be notified when the value changes.
	 * @param sampleCountEventHandler         Will be notified when the value changes.
	 * @param totalSampleCountEventHandler    Will be notified when the value changes.
	 * @param rowCountEventHandler            Will be notified when the value changes.
	 */
	public WidgetFrequencyDomainType(int defaultSampleCount, int defaultTotalSampleCount, int sampleCountLowerLimit, int sampleCountUpperLimit, int defaultWaveformRowCount, int waveformRowCountLowerLimit, int waveformRowCountUpperLimit, Consumer<String> typeEventHandler, Consumer<Integer> sampleCountEventHandler, Consumer<Integer> totalSampleCountEventHandler, Consumer<Integer> rowCountEventHandler) {
		
		super();
		
		typeCombobox = new JComboBox<String>(new String[] {"Live View", "Waveform View", "Waterfall View"});
		sampleCountLabel = new JLabel("Sample Count: ");
		totalSampleCountLabel = new JLabel("Total Sample Count: ");
		sampleCountTextfield = new JTextField(Integer.toString(defaultSampleCount));
		totalSampleCountTextfield = new JTextField(Integer.toString(defaultTotalSampleCount));
		rowCountLabel = new JLabel("Row Count: ");
		rowCountTextfield = new JTextField(Integer.toString(defaultWaveformRowCount));

		this.sampleCountUpperLimit = sampleCountUpperLimit;
		this.sampleCountLowerLimit = sampleCountLowerLimit;
		this.defaultSampleCount = defaultSampleCount;
		this.defaultTotalSampleCount = defaultTotalSampleCount;
		this.waveformRowCountLowerLimit = waveformRowCountLowerLimit;
		this.waveformRowCountUpperLimit = waveformRowCountUpperLimit;
		this.defaultWaveformRowCount = defaultWaveformRowCount;
		this.typeHandler = typeEventHandler;
		this.sampleCountHandler = sampleCountEventHandler;
		this.totalSampleCountHandler = totalSampleCountEventHandler;
		this.rowCountHandler = rowCountEventHandler;
		
		widgets.put(new JLabel("Type: "), "");
		widgets.put(typeCombobox, "span 3, growx");
		widgets.put(sampleCountLabel, "");
		widgets.put(sampleCountTextfield, "span 3, growx");
		widgets.put(totalSampleCountLabel, "");
		widgets.put(totalSampleCountTextfield, "span 3, growx");
		widgets.put(rowCountLabel, "");
		widgets.put(rowCountTextfield, "span 3, growx");
		
		sampleCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { sampleCountTextfield.selectAll(); }
		});
		
		totalSampleCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { totalSampleCountTextfield.selectAll(); }
		});
		
		rowCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent fe) { rowCountTextfield.selectAll(); }
		});
		
		typeCombobox.addActionListener(event -> sanityCheck());
		
		sanityCheck();
		
	}
	
	/**
	 * Ensures the sample count and total sample count values are reasonable:
	 *     1. Both are within the allowed range.
	 *     2. sample count < total sample count
	 *     3. total sample count is a multiple of sample count.
	 * 
	 * Ensures the waveform row count is within the allowed range.
	 * 
	 * Shows/hides/renames widgets based on the selected chart type ("Live View" or "Waveform View" or "Waterfall View".)
	 * 
	 * Notifies all handlers.
	 */
	public void sanityCheck() {
		
		// sanity check the sample count and total sample count
		try {
			
			// clip to limits
			int sampleCount = Integer.parseInt(sampleCountTextfield.getText().trim());
			int totalSampleCount = Integer.parseInt(totalSampleCountTextfield.getText().trim());
			
			if(sampleCount > sampleCountUpperLimit) sampleCount = sampleCountUpperLimit;
			if(sampleCount < sampleCountLowerLimit) sampleCount = sampleCountLowerLimit;
			if(totalSampleCount > sampleCountUpperLimit) totalSampleCount = sampleCountUpperLimit;
			if(totalSampleCount < sampleCountLowerLimit) totalSampleCount = sampleCountLowerLimit;
			
			// ensure sample count < total sample count
			if(sampleCount == totalSampleCount) {
				if(sampleCount * 2 < sampleCountUpperLimit)
					totalSampleCount = sampleCount * 2;
				else
					sampleCount = totalSampleCount / 2;
			} else if(sampleCount > totalSampleCount) {
				int temp = totalSampleCount;
				totalSampleCount = sampleCount;
				sampleCount = temp;
			}
			
			// ensure total sample count is a multiple of sample count
			if(totalSampleCount % sampleCount != 0) {
				
				totalSampleCount -= totalSampleCount % sampleCount;
				
				// ensure sample count < total sample count
				if(sampleCount == totalSampleCount) {
					if(sampleCount * 2 < sampleCountUpperLimit)
						totalSampleCount = sampleCount * 2;
					else
						sampleCount = totalSampleCount / 2;
				} else if(sampleCount > totalSampleCount) {
					int temp = totalSampleCount;
					totalSampleCount = sampleCount;
					sampleCount = temp;
				}
				
			}
			
			// update textfields
			sampleCountTextfield.setText(Integer.toString(sampleCount));
			totalSampleCountTextfield.setText(Integer.toString(totalSampleCount));
			sampleCountHandler.accept(sampleCount);
			totalSampleCountHandler.accept(totalSampleCount);
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid integer, so reset both to defaults
			sampleCountTextfield.setText(Integer.toString(defaultSampleCount));
			totalSampleCountTextfield.setText(Integer.toString(defaultTotalSampleCount));
			sampleCountHandler.accept(defaultSampleCount);
			totalSampleCountHandler.accept(defaultTotalSampleCount);
			
		}
		
		// sanity check the waveform row count
		try {
			
			// clip to limits
			int count = Integer.parseInt(rowCountTextfield.getText().trim());
			if(count > waveformRowCountUpperLimit) count = waveformRowCountUpperLimit;
			if(count < waveformRowCountLowerLimit) count = waveformRowCountLowerLimit;
			rowCountTextfield.setText(Integer.toString(count));
			rowCountHandler.accept(count);
			
		} catch(Exception e) {
			
			// doesn't contain a valid integer, so reset to default
			rowCountTextfield.setText(Integer.toString(defaultWaveformRowCount));
			rowCountHandler.accept(defaultWaveformRowCount);
			
		}
		
		// show/hide/rename widgets based on the selected chart type
		if(typeCombobox.getSelectedItem().toString().equals("Live View")) {
			
			sampleCountLabel.setText("Sample Count: ");
			totalSampleCountLabel.setVisible(false);
			totalSampleCountTextfield.setVisible(false);
			rowCountLabel.setVisible(false);
			rowCountTextfield.setVisible(false);
			
		} else if(typeCombobox.getSelectedItem().toString().equals("Waveform View")) {
			
			sampleCountLabel.setText("Sample Count per DFT: ");
			totalSampleCountLabel.setVisible(true);
			totalSampleCountTextfield.setVisible(true);
			rowCountLabel.setVisible(true);
			rowCountTextfield.setVisible(true);
			
		} else if(typeCombobox.getSelectedItem().toString().equals("Waterfall View")) {
			
			sampleCountLabel.setText("Sample Count per DFT: ");
			totalSampleCountLabel.setVisible(true);
			totalSampleCountTextfield.setVisible(true);
			rowCountLabel.setVisible(false);
			rowCountTextfield.setVisible(false);
			
		}
		
		typeHandler.accept(typeCombobox.getSelectedItem().toString());
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {

		// parse the text
		int sampleCount      = ChartUtils.parseInteger(lines.remove(), "sample count = %d");
		int totalSampleCount = ChartUtils.parseInteger(lines.remove(), "total sample count = %d");
		String chartType     = ChartUtils.parseString (lines.remove(), "type = %s");
		int waveformRowCount = ChartUtils.parseInteger(lines.remove(), "waveform view row count = %d");
		
		if(!chartType.equals("Live View") && !chartType.equals("Waveform View") && !chartType.equals("Waterfall View"))
			throw new AssertionError("Invalid Frequency Domain Chart type.");
		
		// update the widget
		sampleCountTextfield.setText(Integer.toString(sampleCount));
		
		totalSampleCountTextfield.setText(Integer.toString(totalSampleCount));
		
		for(int i = 0; i < typeCombobox.getItemCount(); i++)
			if(typeCombobox.getItemAt(i).equals(chartType))
				typeCombobox.setSelectedIndex(i);
		
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
		
		return new String[] {
			"sample count = " + sampleCountTextfield.getText(),
			"total sample count = " + totalSampleCountTextfield.getText(),
			"type = " + typeCombobox.getSelectedItem().toString(),
			"waveform view row count = " + rowCountTextfield.getText()
		};
		
	}

}
