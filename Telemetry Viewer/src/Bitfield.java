import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Describes one bitfield.
 */
public class Bitfield implements Comparable<Bitfield> {
	
	final int MSBit;
	final int LSBit;
	final int bitmask; // (raw dataset value >> LSBit) & bitmask = bitfield value
	final String[] names;
	
	// also storing GUI-related stuff in this object to keep things simple
	final JTextField[] textfields;
	final JLabel[] textfieldLabels;
	
	public Bitfield(int MSBit, int LSBit) {
		
		this.MSBit = MSBit;
		this.LSBit = LSBit;
		bitmask = (int) Math.pow(2, MSBit - LSBit + 1) - 1;
		names = new String[(int) Math.pow(2, MSBit - LSBit + 1)];
		textfields = new JTextField[(int) Math.pow(2, MSBit - LSBit + 1)];
		textfieldLabels = new JLabel[textfields.length];
		
		for(int i = 0; i < names.length; i++) {
			int index = i;
			JTextField field = new JTextField();
			field.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent e) {
					field.setText(field.getText().trim().replace(',', ' ')); // no commas allowed, because they will break import/export logic
					names[index] = field.getText();
				}
				
				@Override public void focusGained(FocusEvent e) {
					field.selectAll();
				}
			});
			textfields[i] = field;
			textfieldLabels[i] = new JLabel((MSBit != LSBit) ? "Bits [" + MSBit + ":" + LSBit + "] = " + i :
                                                               "Bit " + MSBit + " = " + i);
			names[i] = "";
		}
		
	}
	
	/**
	 * Gets the bitfield state from a Dataset value at one point in time.
	 * 
	 * @param rawValue    The dataset value.
	 * @return            The corresponding bitfield state.
	 */
	public int getValue(int rawValue) {
		
		return (rawValue >> LSBit) & bitmask;
		
	}

	/**
	 * For sorting a Collection of Bitfields so the fields occupying less-significant bits come first.
	 */
	@Override public int compareTo(Bitfield other) {

		return this.MSBit - other.LSBit;
		
	}

}
