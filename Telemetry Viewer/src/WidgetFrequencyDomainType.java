import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A widget that lets the user specify the type for a frequency domain chart.
 * 
 * The type can be "Live View" or "Waveform View" or "Waterfall View."
 * For all types, the user specifies the sample count (DFT window length).
 * For the waveform and waterfall types, the user also specifies the total sample length.
 * The total sample length must be greater than the sample length, and it must be a multiple of the sample length.
 */
@SuppressWarnings("serial")
public class WidgetFrequencyDomainType extends JPanel {
	
	JLabel typeLabel;
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
	int verticalBinCountUpperLimit;
	int verticalBinCountLowerLimit;
	int defaultVerticalBinCount;
	
	/**
	 * @param defaultSampleCount            Default sample count.
	 * @param defaultTotalSampleCount       Default total sample count.
	 * @param sampleCountLowerLimit         Minimum allowed sample count or total sample count.
	 * @param sampleCountUpperLimit         Maximum allowed sample count or total sample count.
	 * @param defaultWaveformRowCount       Default y-axis bin count for Waveform View.
	 * @param waveformRowCountLowerLimit    Minimum allowed y-axis bin count for Waveform View.
	 * @param waveformRowCountUpperLimit    Maximum allowed y-axis bin count for Waveform View.
	 */
	public WidgetFrequencyDomainType(int defaultSampleCount, int defaultTotalSampleCount, int sampleCountLowerLimit, int sampleCountUpperLimit, int defaultWaveformRowCount, int waveformRowCountLowerLimit, int waveformRowCountUpperLimit) {
		
		super();
		
		typeLabel = new JLabel("Type: ");
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
		this.verticalBinCountLowerLimit = waveformRowCountLowerLimit;
		this.verticalBinCountUpperLimit = waveformRowCountUpperLimit;
		this.defaultVerticalBinCount = defaultWaveformRowCount;
		
		setLayout(new GridLayout(4, 2, 10, 10));
		add(typeLabel);
		add(typeCombobox);
		add(sampleCountLabel);
		add(sampleCountTextfield);
		add(totalSampleCountLabel);
		add(totalSampleCountTextfield);
		add(rowCountLabel);
		add(rowCountTextfield);
		
		sampleCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				sanityCheckSampleCounts();
			}
			
			@Override public void focusGained(FocusEvent fe) {
				sampleCountTextfield.selectAll();
			}
		});
		
		totalSampleCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				sanityCheckSampleCounts();
			}
			
			@Override public void focusGained(FocusEvent fe) {
				totalSampleCountTextfield.selectAll();
			}
		});
		
		rowCountTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					// clip to limits
					rowCountTextfield.setText(rowCountTextfield.getText().trim());
					int count = Integer.parseInt(rowCountTextfield.getText());
					if(count > waveformRowCountUpperLimit) count = waveformRowCountUpperLimit;
					if(count < waveformRowCountLowerLimit) count = waveformRowCountLowerLimit;
					rowCountTextfield.setText(Integer.toString(count));
				} catch(Exception e) {
					// doesn't contain a valid integer, so reset to default
					rowCountTextfield.setText(Integer.toString(defaultWaveformRowCount));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				rowCountTextfield.selectAll();
			}
		});
		
		typeCombobox.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent ae) {

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
				
			}
		});
		
		typeCombobox.getActionListeners()[0].actionPerformed(null);
		
	}
	
	/**
	 * Ensures that the sample count and total sample count values are reasonable:
	 * 1. Both are within the allowed range.
	 * 2. sample count < total sample count
	 * 3. total sample count is a multiple of sample count.
	 */
	private void sanityCheckSampleCounts() {
		
		try {
			
			// clip to limits
			sampleCountTextfield.setText(sampleCountTextfield.getText().trim());
			totalSampleCountTextfield.setText(totalSampleCountTextfield.getText().trim());
			int sampleCount = Integer.parseInt(sampleCountTextfield.getText());
			int totalSampleCount = Integer.parseInt(totalSampleCountTextfield.getText());
			
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
			
		} catch(Exception e) {
			
			// one of the textfields doesn't contain a valid integer, so reset both to defaults
			sampleCountTextfield.setText(Integer.toString(defaultSampleCount));
			totalSampleCountTextfield.setText(Integer.toString(defaultTotalSampleCount));
			
		}
		
	}
	
	/**
	 * @return    "Live View" or "Waveform View" or "Waterfall View"
	 */
	public String getType() {
		
		return typeCombobox.getSelectedItem().toString();
		
	}
	
	/**
	 * @return    The sample count.
	 */
	public int getSampleCount() {
		
		return Integer.parseInt(sampleCountTextfield.getText());
		
	}
	
	/**
	 * @return    The total sample count.
	 */
	public int getTotalSampleCount() {
		
		return Integer.parseInt(totalSampleCountTextfield.getText());
		
	}
	
	/**
	 * @return    The row count for Waveform View.
	 */
	public int getRowCount() {
		
		return Integer.parseInt(rowCountTextfield.getText());
		
	}

}
