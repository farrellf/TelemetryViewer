import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import net.miginfocom.swing.MigLayout;

/**
 * A class for describing and processing binary packets.
 * 
 * The data structure of a binary packet can be defined interactively by the user, or by loading a layout file.
 * 
 * 
 * When defined by loading a layout file, Controller.openLayout() will:
 * 1. Call BinaryPacket.clear() to start over fresh.
 * 2. Repeatedly call BinaryPacket.insertField() to define each of the fields.
 * 3. Optionally call BinaryPacket.insertChecksum() if a checksum field is used.
 * 4. Call Controller.connectToSerialPort(), which will call BinaryPacket.startReceivingData().
 * 5. Create the charts.
 * 
 * 
 * When defined by user interaction:
 * 1. The ControlsRegion gets the possible packet types from Controller.getPacketTypes(), and one of them is an object of this class.
 * 2. The user clicks Connect in the ControlsRegion, which calls Controller.connectToSerialPort().
 * 3. If a successful connection occurs, BinaryPacket.showDataStructureWindow() is called.
 * 4. That window lets the user define the binary packet data structure by calling methods of this class:
 * 
 *        To modify or query the data structure:
 *            insertField()
 *            removeField()
 *            insertChecksum()
 *            removeChecksum()
 *            clear()
 *            isFull()
 *            isEmpty()
 *         
 *        To visualize the data structure with a JTable:
 *            getRowCount()
 *            getCellContents()
 *         
 *        To list possibilities for the user:
 *            getFirstAvailableOffset()
 *            getBinaryFieldProcessors()
 *            getBinaryChecksumProcessors()
 *     
 * 5. When use user clicks Done in the BinaryDataStructureWindow, Controller.connectToSerialPort() will call BinaryPacket.startReceivingData().
 * 6. The user can then interactively create the charts.
 */
public class BinaryPacket extends Packet {

	private byte syncWord;
	private int packetSize; // total byte count: includes sync word, fields, and checksum field
	private Thread thread;
	
	/**
	 * Creates an object with a default sync word, but no fields, and no checksum processor.
	 */
	public BinaryPacket() {
		
		syncWord = (byte) 0xAA;
		DatasetsController.removeAllDatasets();
		checksumProcessor = null;
		checksumProcessorOffset = -1;
		packetSize = 1; // the syncWord
		
		thread = null;
		
	}
	
	/**
	 * @return    User-friendly name for this packet type.
	 */
	@Override public String toString() {
		
		return "Binary";
		
	}
	
	/**
	 * Adds a field to the data structure if possible.
	 * 
	 * @param byteOffset           Binary packet byte offset.
	 * @param processor            BinaryProcessor for the raw samples in the Binary packet.
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 * @return                     null on success, or a user-friendly String describing why the field could not be added.
	 */
	@Override public String insertField(int byteOffset, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {

		if(byteOffset == 0)
			return "Error: Can not place a field that overlaps the sync word.";
		
		if(isFull())
			return "Error: The packet is full.";
		
		if(checksumProcessor != null)
			if(byteOffset + processor.getByteCount() - 1 >= checksumProcessorOffset)
				return "Error: Can not place a field that overlaps the checksum or is placed after the checksum.";
		
		// check for overlap with existing fields
		int proposedStartByte = byteOffset;
		int proposedEndByte = proposedStartByte + processor.getByteCount() - 1;
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			int existingStartByte = dataset.location;
			int existingEndByte = existingStartByte + dataset.processor.getByteCount() - 1;
			if(proposedStartByte >= existingStartByte && proposedStartByte <= existingEndByte)
				return "Error: Can not place a field that overlaps an existing field."; // starting inside existing range
			if(proposedEndByte >= existingStartByte && proposedEndByte <= existingEndByte)
				return "Error: Can not place a field that overlaps an existing field."; // ending inside existing range
			if(existingStartByte >= proposedStartByte && existingEndByte <= proposedEndByte)
				return "Error: Can not place a field that overlaps an existing field."; // encompassing existing range
		}
		
		// add the field
		DatasetsController.insertDataset(byteOffset, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		// update packetSize
		int newPacketSize = 1;
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			int endByte = dataset.location + dataset.processor.getByteCount() - 1;
			if(endByte + 1 > newPacketSize)
				newPacketSize = endByte + 1;
		}
		if(newPacketSize > packetSize)
			packetSize = newPacketSize;

		// no errors
		return null;
		
	}
	
	/**
	 * Removes a field from the data structure if possible.
	 * 
	 * @param location    The field at this byte offset will be removed.
	 * @return            null on success, or a user-friendly String describing why the field could not be removed.
	 */
	@Override public String removeField(int location) {
		
		if(location == 0)
			return "Error: Can not remove the sync word.";
		
		boolean success = DatasetsController.removeDataset(location);
		if(!success)
			return "Error: No field exists at that location.";

		// update packetSize if there is no checksum
		if(checksumProcessor == null) {
			int newPacketSize = 1;
			for(Dataset dataset : DatasetsController.getAllDatasets()) {
				int endByte = dataset.location + dataset.processor.getByteCount() - 1;
				if(endByte + 1 > newPacketSize)
					newPacketSize = endByte + 1;
			}
			packetSize = newPacketSize;
		}
		
		// no errors
		return null;
		
	}
	
	
	/**
	 * Removes the checksum and all fields from the data structure, leaving just the sync word.
	 */
	@Override public void reset() {
		
		DatasetsController.removeAllDatasets();
		checksumProcessor = null;
		packetSize = 1; // the syncWord
		
	}

	/**
	 * Adds a checksum field to the data structure if possible.
	 * 
	 * @param location     Binary packet byte offset.
	 * @param processor    The type of checksum field.
	 * @return             null on success, or a user-friendly String describing why the checksum field could not be added.
	 */
	@Override public String insertChecksum(int location, BinaryChecksumProcessor processor) {

		if(checksumProcessor != null)
			return "Error: A checksum field already exists.";
		
		if(location == 0)
			return "Error: A checksum field can not overlap with the sync word.";
		
		if(location < packetSize)
			return "Error: A checksum field can not be placed in front of existing fields.";
		
		if((location - 1) % processor.getByteCount() != 0)
			return "Error: The checksum must be aligned. The number of bytes before the checksum, not counting the sync word, must be a multiple of " + processor.getByteCount() + " for this checksum type.";
		
		// add the checksum processor
		checksumProcessor = processor;
		checksumProcessorOffset = location;
		
		// update packetSize
		packetSize = location + processor.getByteCount();
		
		// no errors
		return null;
		
	}
	
	/**
	 * Removes the checksum field from the data structure if possible.
	 * 
	 * @return    null on success, or a user-friendly String describing why the checksum field could not be removed.
	 */
	@Override public String removeChecksum() {
		
		if(checksumProcessor == null)
			return "Error: There was no checksum processor to remove.";
		
		// remove the checksum processor
		checksumProcessor = null;
		checksumProcessorOffset = -1;
		
		// update packetSize
		int newPacketSize = 1;
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			int endByte = dataset.location + dataset.processor.getByteCount() - 1;
			if(endByte + 1 > newPacketSize)
				newPacketSize = endByte + 1;
		}
		packetSize = newPacketSize;
		
		// no errors
		return null;
		
	}
	
	/**
	 * Checks if more fields can be added.
	 * 
	 * @return    True if a checksum processor exists and every byte before it is occupied, false if more fields or a checksum processor can be added
	 */
	public boolean isFull() {
		
		if(checksumProcessor == null)
			return false;

		// check which bytes before the checksum are occupied
		boolean[] byteUsed = new boolean[packetSize - checksumProcessor.getByteCount()];
		byteUsed[0] = true; // the syncWord
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			int start = dataset.location;
			int end = start + dataset.processor.getByteCount() - 1;
			for (int i = start; i <= end; i++)
				byteUsed[i] = true;
		}
		
		// check if all of those bytes are occupied
		boolean everyByteUsed = true;
		for(int i = 0; i < byteUsed.length; i++)
			if(byteUsed[i] == false)
				everyByteUsed = false;
		
		if(everyByteUsed)
			return true;
		else
			return false;
		
	}
	
	/**
	 * Check if the data structure is empty.
	 * 
	 * @return    True if there is just a sync word, false otherwise.
	 */
	public boolean isEmpty() {
		
		if(packetSize == 1)
			return true;
		else
			return false;
		
	}
	
	/**
	 * Gets the number of rows that should be shown in the BinaryDataStructureWindow's JTable.
	 * This would be the number of fields, +1 for the sync word, +1 if a checksum field has been defined.
	 * 
	 * @return    The number of rows.
	 */
	public int getRowCount() {
		
		int count = 1; // the syncWord
		count += DatasetsController.getDatasetsCount();
		if(checksumProcessor != null)
			count++;
		
		return count;
		
	}
	
	/**
	 * Gets the text to show in a specific cell in the BinaryDataStructureWindow's JTable.
	 * 
	 * Column 0 = byte offset, data type
	 * Column 1 = name
	 * Column 2 = color
	 * Column 3 = unit
	 * Column 4 = conversion ratio
	 * 
	 * @param column    The column.
	 * @param row       The row.
	 * @return          The contents of the cell.
	 */
	public String getCellContents(int column, int row) {
		
		// the first row is always the sync word
		if(row == 0) {
			if(column == 0)      return "0, [Sync Word]";
			else if(column == 1) return String.format("0x%02X", syncWord);
			else                 return "";
		}
		
		// subsequent rows are the fields
		row--;
		if(row < DatasetsController.getDatasetsCount()) {
			Dataset dataset = DatasetsController.getDatasetByIndex(row);
			if(column == 0)      return dataset.location + ", " + dataset.processor.toString();
			else if(column == 1) return dataset.name;
			else if(column == 2) return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
			else if(column == 3) return dataset.isBitfield ? "" : dataset.unit;
			else if(column == 4) return dataset.isBitfield ? "" : String.format("%3.3f LSBs = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
			else                 return "";
		}
		
		// last row is the checksum if it exists
		if(checksumProcessor != null) {
			if(column == 0)      return checksumProcessorOffset + ", [Checksum]";
			else if(column == 1) return checksumProcessor.toString();
			else                 return "";
		}
		
		// this should never happen
		return "";
		
	}
	
	/**
	 * @return    The first unoccupied byte offset, or -1 if they are all occupied.
	 */
	public int getFirstAvailableOffset() {
		
		// the packet is empty
		if(packetSize == 1)
			return 1;
		
		// the packet is full
		if(isFull())
			return -1;
		
		// check which bytes before the checksum are occupied
		int size = packetSize;
		if(checksumProcessor != null)
			size -= checksumProcessor.getByteCount();
		boolean[] byteUsed = new boolean[size];
		byteUsed[0] = true; // the syncWord
		for(Dataset dataset : DatasetsController.getAllDatasets()) {
			int start = dataset.location;
			int end = start + dataset.processor.getByteCount() - 1;
			for (int i = start; i <= end; i++)
				byteUsed[i] = true;
		}
		
		// if the packet is sparse, return the first unused byte
		for(int i = 0; i < byteUsed.length; i++)
			if(byteUsed[i] == false)
				return i;
		
		// if the packet is not sparse, return the current packet size
		return packetSize;
		
	}
	
	/**
	 * @return    An array of BinaryFieldProcessors that each describe their data type and can convert raw bytes into a number.
	 */
	static public BinaryFieldProcessor[] getBinaryFieldProcessors() {
		
		BinaryFieldProcessor[] processors = new BinaryFieldProcessor[5];
		
		processors[0] = new BinaryFieldProcessor() {
			
			@Override public String toString()                   { return "uint16 LSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float) (((0xFF & rawBytes[0]) << 0) |
					                                                               ((0xFF & rawBytes[1]) << 8));}
			
		};
		
		processors[1] = new BinaryFieldProcessor() {
			
			@Override public String toString()                   { return "uint16 MSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float) (((0xFF & rawBytes[1]) << 0) |
			                                                                       ((0xFF & rawBytes[0]) << 8));}
			
		};
		
		processors[2] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "float32 LSB First"; }
			@Override public int getByteCount()               { return 4; }
			@Override public float extractValue(byte[] bytes) { return Float.intBitsToFloat(((0xFF & bytes[0]) <<  0) |
			                                                                                ((0xFF & bytes[1]) <<  8) |
			                                                                                ((0xFF & bytes[2]) << 16) |
			                                                                                ((0xFF & bytes[3]) << 24));}
			
		};
		
		processors[3] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "float32 MSB First"; }
			@Override public int getByteCount()               { return 4; }
			@Override public float extractValue(byte[] bytes) { return Float.intBitsToFloat(((0xFF & bytes[3]) <<  0) |
                                                                                            ((0xFF & bytes[2]) <<  8) |
                                                                                            ((0xFF & bytes[1]) << 16) |
                                                                                            ((0xFF & bytes[0]) << 24));}
			
		};
		
		processors[4] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "Bitfield: 8 Bits"; }
			@Override public int getByteCount()               { return 1; }
			@Override public float extractValue(byte[] bytes) { return (float) (0xFF & bytes[0]);}
			
		};
		
		return processors;
		
	}
	
	/**
	 * @return    An array of BinaryChecksumProcessors that each describe their type and can test for a valid checksum.
	 */
	static public BinaryChecksumProcessor[] getBinaryChecksumProcessors() {
		
		BinaryChecksumProcessor[] processors = new BinaryChecksumProcessor[1];
		
		processors[0] = new BinaryChecksumProcessor() {
			
			@Override public String toString()                              { return "uint16 Checksum LSB First"; }
			@Override public int getByteCount()                             { return 2; }
			@Override public boolean testChecksum(byte[] bytes, int length) {
				
				// sanity check: a 16bit checksum requires an even number of bytes
				if(length % 2 != 0)
					return false;
				
				// calculate the sum
				int wordCount = (length - getByteCount()) / 2; // 16bit words
				
				int sum = 0;
				int lsb = 0;
				int msb = 0;
				for(int i = 0; i < wordCount; i++) {
					lsb = 0xFF & bytes[i*2];
					msb = 0xFF & bytes[i*2 + 1];
					sum += (msb << 8 | lsb);
				}
				
				// extract the reported checksum
				lsb = 0xFF & bytes[wordCount*2];
				msb = 0xFF & bytes[wordCount*2 + 1];
				int checksum = (msb << 8 | lsb);
				
				// test
				sum %= 65536;
				if(sum == checksum)
					return true;
				else
					return false;
			}
			
		};
		
		return processors;
		
	}
	
	/**
	 * Spawns a new thread that listens for incoming data, processes it, and populates the datasets.
	 * This method should only be called after the data structure has been defined and a connection has been made.
	 * 
	 * @param stream    The data to process.
	 */
	@Override public void startReceivingData(InputStream stream) {
		
		thread = new Thread(() -> {
				
			byte[] rx_buffer = new byte[packetSize];
			BufferedInputStream bStream = new BufferedInputStream(stream, 2 * packetSize);
			
			while(true) {
				
				try {
				
					// wait for data to arrive
					while(bStream.available() < packetSize)
						Thread.sleep(1);
					
					// wait for the sync word
					bStream.read(rx_buffer, 0, 1);
					while(rx_buffer[0] != syncWord)
						bStream.read(rx_buffer, 0, 1);
					
					// get rest of packet after the sync word
					bStream.read(rx_buffer, 0, packetSize - 1); // -1 for syncWord
					
					// test checksum if enabled
					boolean checksumPassed = true;
					if(checksumProcessor != null)
						checksumPassed = checksumProcessor.testChecksum(rx_buffer, packetSize - 1); // -1 for syncWord
					if(!checksumPassed) {
						NotificationsController.showVerboseForSeconds("Checksum failed.", 1, false);
						continue;
					}
					
					// extract raw numbers and insert them into the datasets
					for(Dataset dataset : DatasetsController.getAllDatasets()) {
						BinaryFieldProcessor processor = dataset.processor;
						int byteOffset = dataset.location;
						int byteCount  = processor.getByteCount();
						
						byte[] buffer = new byte[byteCount];
						for(int i = 0; i < byteCount; i++)
							buffer[i] = rx_buffer[byteOffset + i - 1]; // -1 for syncWord
						
						float rawNumber = processor.extractValue(buffer);
						dataset.add(rawNumber);
					}
					DatasetsController.incrementSampleCount();
				
				} catch(IOException | InterruptedException e) {
					
					// stop and end this thread
					try { bStream.close(); } catch(IOException e2) { }
					NotificationsController.showVerboseForSeconds("The Binary Packet Processor thread is stopping.", 5, false);
					return;
					
				}
				
			}
			
		});
		
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.setName("Binary Packet Processor");
		thread.start();
		
	}
	
	/**
	 * Stops the Binary Packet Processor thread.
	 */
	@Override public void stopReceivingData() {
		
		if(thread != null && thread.isAlive()) {
			thread.interrupt();
			while(thread.isAlive()); // wait
		}
		
	}
	
	/**
	 * Displays a window for the user to interactively define the binary packet's data structure.
	 * 
	 * @param parentWindow    Window to center over.
	 * @param testMode        True for test mode (disables editing), false for normal mode.
	 */
	@Override public void showDataStructureWindow(JFrame parentWindow, boolean testMode) {
		
		new BinaryDataStructureWindow(parentWindow, this, testMode);
		
	}

	/**
	 * Window where the user defines the binary packet data structure.
	 */
	@SuppressWarnings("serial")
	private class BinaryDataStructureWindow extends JDialog {
		
		JTextField        offsetTextfield;
		JComboBox<Object> processorCombobox;
		JTextField        nameTextfield;
		JButton           colorButton;
		JTextField        unitTextfield;
		JTextField        conversionFactorAtextfield;
		JTextField        conversionFactorBtextfield;
		JLabel            unitLabel;
		JButton           addButton;
		JButton           resetButton;
		JButton           doneButton;
		
		JTable dataStructureTable;
		JScrollPane scrollableDataStructureTable;
		JPanel tablePanel;
		
		/**
		 * Creates a new window where the user can define the binary data structure.
		 * 
		 * @param parentWindow    The window to center this BinaryDataStructureWindow over.
		 * @param packet          A BinaryPacket representing the data structure.
		 * @param testMode        If true, the user will only be able to view, not edit, the data structure.
		 */
		public BinaryDataStructureWindow(JFrame parentWindow, BinaryPacket packet, boolean testMode) {
			
			super();
			
			setTitle(testMode ? "Binary Packet Data Structure (Not Editable in Test Mode)" : "Binary Packet Data Structure");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setLayout(new BorderLayout());
			
			// all JTextFields let the user press enter to add the row
			ActionListener pressEnterToAddRow = new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					addButton.doClick();
				}
			};
			
			// user specifies the offset (first byte) of a field
			offsetTextfield = new JTextField(Integer.toString(packet.getFirstAvailableOffset()), 3);
			offsetTextfield.addActionListener(pressEnterToAddRow);
			offsetTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent fe) {
					try {
						offsetTextfield.setText(offsetTextfield.getText().trim());
						Integer.parseInt(offsetTextfield.getText());
					} catch(Exception e) {
						offsetTextfield.setText(Integer.toString(packet.getFirstAvailableOffset()));
						if(packet.isFull())
							addButton.setEnabled(false);
					}
				}
				@Override public void focusGained(FocusEvent fe) {
					offsetTextfield.selectAll();
				}
			});

			// user specifies the processor of a field (the processor converts raw bytes into numbers, or evaluates checksums) 
			processorCombobox = new JComboBox<Object>();
			for(BinaryFieldProcessor processor : BinaryPacket.getBinaryFieldProcessors())
				processorCombobox.addItem(processor);
			for(BinaryChecksumProcessor processor : BinaryPacket.getBinaryChecksumProcessors())
				processorCombobox.addItem(processor);
			processorCombobox.addActionListener(event -> {
				if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield"))
					updateWidgetsBitfieldMode();
				else if(processorCombobox.getSelectedItem() instanceof BinaryFieldProcessor)
					updateWidgetsNormalMode();
				else if(processorCombobox.getSelectedItem() instanceof BinaryChecksumProcessor)
					updateWidgetsChecksumMode();
			});
			
			// user specifies the name of a field
			nameTextfield = new JTextField("", 15);
			nameTextfield.addActionListener(pressEnterToAddRow);
			nameTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent e) {
					nameTextfield.setText(nameTextfield.getText().trim());
				}
				@Override public void focusGained(FocusEvent e) {
					nameTextfield.selectAll();
				}
			});
			
			// user specifies the color of a field
			colorButton = new JButton("\u25B2");
			colorButton.setForeground(Controller.getDefaultLineColor());
			colorButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					Color color = JColorChooser.showDialog(BinaryDataStructureWindow.this, "Pick a Color for " + nameTextfield.getText(), Color.BLACK);
					if(color != null)
						colorButton.setForeground(color);
				}
			});
			
			// user specifies the unit of a field
			unitTextfield = new JTextField("", 15);
			unitTextfield.addActionListener(pressEnterToAddRow);
			unitTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent arg0) {
					unitTextfield.setText(unitTextfield.getText().trim());
					unitLabel.setText(unitTextfield.getText());
				}
				@Override public void focusGained(FocusEvent arg0) {
					unitTextfield.selectAll();
				}
			});
			unitTextfield.addKeyListener(new KeyListener() {
				@Override public void keyReleased(KeyEvent ke) {
					unitTextfield.setText(unitTextfield.getText().trim());
					unitLabel.setText(unitTextfield.getText());
				}
				@Override public void keyPressed(KeyEvent ke) { }
				@Override public void keyTyped(KeyEvent ke) { }
			});
			
			// user specifies the conversion ratio of a field as "x LSBs = x [Units]"
			conversionFactorAtextfield = new JTextField("1.0", 4);
			conversionFactorAtextfield.addActionListener(pressEnterToAddRow);
			conversionFactorAtextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent arg0) {
					try {
						conversionFactorAtextfield.setText(conversionFactorAtextfield.getText().trim());
						double value = Double.parseDouble(conversionFactorAtextfield.getText());
						if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
					} catch(Exception e) {
						conversionFactorAtextfield.setText("1.0");
					}
				}
				@Override public void focusGained(FocusEvent arg0) {
					conversionFactorAtextfield.selectAll();
				}
			});
			
			conversionFactorBtextfield = new JTextField("1.0", 4);
			conversionFactorBtextfield.addActionListener(pressEnterToAddRow);
			conversionFactorBtextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent arg0) {
					try {
						conversionFactorBtextfield.setText(conversionFactorBtextfield.getText().trim());
						double value = Double.parseDouble(conversionFactorBtextfield.getText());
						if(value == 0.0 || value == Double.NaN || value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY) throw new Exception();
					} catch(Exception e) {
						conversionFactorBtextfield.setText("1.0");
					}
				}
				@Override public void focusGained(FocusEvent arg0) {
					conversionFactorBtextfield.selectAll();
				}
			});
			
			unitLabel = new JLabel("_______________");
			unitLabel.setMinimumSize(unitLabel.getPreferredSize());
			unitLabel.setPreferredSize(unitLabel.getPreferredSize());
			unitLabel.setHorizontalAlignment(JLabel.LEFT);
			unitLabel.setText("");		
			
			// user clicks Add to insert a new field into the data structure if possible
			addButton = new JButton("Add");
			addButton.addActionListener(event -> {
					
				int location = Integer.parseInt(offsetTextfield.getText());
				Object processor = processorCombobox.getSelectedItem();
				String name = nameTextfield.getText().trim();
				Color color = colorButton.getForeground();
				String unit = unitTextfield.getText();
				float conversionFactorA = Float.parseFloat(conversionFactorAtextfield.getText());
				float conversionFactorB = Float.parseFloat(conversionFactorBtextfield.getText());
				
				if(processor instanceof BinaryFieldProcessor) {
					
					if(name.equals("")) {
						JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, "A name is required.", "Error: Name Required", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					String errorMessage = packet.insertField(location, (BinaryFieldProcessor) processor, name, color, unit, conversionFactorA, conversionFactorB);
					dataStructureTable.revalidate();
					dataStructureTable.repaint();
					
					if(errorMessage != null) {
						JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					if(((BinaryFieldProcessor) processor).toString().startsWith("Bitfield")) {

						BitfieldPanel bitfieldPanel = new BitfieldPanel(((BinaryFieldProcessor) processor).getByteCount() * 8, DatasetsController.getDatasetByLocation(location));
						bitfieldPanel.eventHandler = event2 -> {
							
							remove(bitfieldPanel);
							add(tablePanel, BorderLayout.CENTER);
							dataStructureTable.revalidate();
							dataStructureTable.repaint();
							if(packet.isFull())
								updateWidgetsFullPacket();
							else
								updateWidgetsBitfieldMode();
							revalidate();
							repaint();
							
						};
						
						remove(tablePanel);
						add(bitfieldPanel, BorderLayout.CENTER);
						updateWidgetsBitfieldInProgress();					
						
						revalidate();
						repaint();
						return;
						
					}
					
					if(packet.isFull())
						updateWidgetsFullPacket();
					else
						updateWidgetsNormalMode();
					
				} else if(processor instanceof BinaryChecksumProcessor) {
					
					String errorMessage = packet.insertChecksum(location, (BinaryChecksumProcessor) processor);
					dataStructureTable.revalidate();
					dataStructureTable.repaint();
					
					if(errorMessage != null) {
						JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					if(packet.isFull())
						updateWidgetsFullPacket();
					else
						updateWidgetsNormalMode();
					
				}
				
			});
			
			// user clicks Reset to remove all fields from the data structure
			resetButton = new JButton("Reset");
			resetButton.addActionListener(event -> {
				packet.reset();
				dataStructureTable.revalidate();
				dataStructureTable.repaint();
				
				updateWidgetsNormalMode();
			});
			
			// user clicks Done when the data structure is complete
			doneButton = new JButton("Done");
			doneButton.addActionListener(event -> {
				if(packet.isEmpty())
					JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, "Error: At least one field is required.", "Error", JOptionPane.ERROR_MESSAGE);
				else
					dispose();
			});
			
			JPanel dataEntryPanel = new JPanel();
			dataEntryPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
			dataEntryPanel.add(new JLabel("Byte Offset"));
			dataEntryPanel.add(offsetTextfield);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(processorCombobox);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(new JLabel("Name"));
			dataEntryPanel.add(nameTextfield);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(new JLabel("Color"));
			dataEntryPanel.add(colorButton);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(new JLabel("Unit"));
			dataEntryPanel.add(unitTextfield);
			dataEntryPanel.add(Box.createHorizontalStrut(80));
			dataEntryPanel.add(conversionFactorAtextfield);
			dataEntryPanel.add(new JLabel(" LSBs = "));
			dataEntryPanel.add(conversionFactorBtextfield);
			dataEntryPanel.add(unitLabel);
			dataEntryPanel.add(Box.createHorizontalStrut(80));
			dataEntryPanel.add(addButton);		
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(resetButton);
			dataEntryPanel.add(Box.createHorizontalStrut(20));
			dataEntryPanel.add(doneButton);
			
			dataStructureTable = new JTable(new AbstractTableModel() {
				@Override public String getColumnName(int column) {
					if(column == 0)      return "Byte Offset, Data Type";
					else if(column == 1) return "Name";
					else if(column == 2) return "Color";
					else if(column == 3) return "Unit";
					else if(column == 4) return "Conversion Ratio";
					else                 return "Error";
				}
				
				@Override public Object getValueAt(int row, int column) {
					return packet.getCellContents(column, row);
				}
				
				@Override public int getRowCount() {
					return packet.getRowCount();
				}
				
				@Override public int getColumnCount() {
					return 5;
				}
				
			});
			scrollableDataStructureTable = new JScrollPane(dataStructureTable);
			
			tablePanel = new JPanel(new GridLayout(1, 1));
			tablePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
			tablePanel.add(scrollableDataStructureTable, BorderLayout.CENTER);
			dataStructureTable.setRowHeight((int) tablePanel.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight()); // fixes display scaling issue
			
			add(dataEntryPanel, BorderLayout.NORTH);
			add(tablePanel, BorderLayout.CENTER);
			
			pack();
			setMinimumSize(new Dimension(getPreferredSize().width, 500));
			setLocationRelativeTo(parentWindow);
			
			nameTextfield.requestFocus();
			
			if(packet.isFull())
				updateWidgetsFullPacket();
			
			if(testMode)
				updateWidgetsTestMode();
			
			setModal(true);
			setVisible(true);
			
		}
		
		private void updateWidgetsNormalMode() {
			
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(true);
			colorButton.setEnabled(true);
			unitTextfield.setEnabled(true);
			conversionFactorAtextfield.setEnabled(true);
			conversionFactorBtextfield.setEnabled(true);
			addButton.setEnabled(true);
			resetButton.setEnabled(true);
			doneButton.setEnabled(true);
			
			offsetTextfield.setText(Integer.toString(getFirstAvailableOffset()));
			if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield") || processorCombobox.getSelectedItem() instanceof BinaryChecksumProcessor)
				processorCombobox.setSelectedIndex(0);
			
			nameTextfield.requestFocus();
			nameTextfield.selectAll();
			
		}
		
		private void updateWidgetsChecksumMode() {
			
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(true);
			resetButton.setEnabled(true);
			doneButton.setEnabled(true);
			
			nameTextfield.setText("");
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			
			nameTextfield.requestFocus();
			nameTextfield.selectAll();
			
		}
		
		private void updateWidgetsBitfieldMode() {
			
			offsetTextfield.setEnabled(true);
			processorCombobox.setEnabled(true);
			nameTextfield.setEnabled(true);
			colorButton.setEnabled(true);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(true);
			resetButton.setEnabled(true);
			doneButton.setEnabled(true);
			
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			offsetTextfield.setText(Integer.toString(getFirstAvailableOffset()));
			
			nameTextfield.requestFocus();
			nameTextfield.selectAll();
			
		}
		
		private void updateWidgetsBitfieldInProgress() {
			
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			resetButton.setEnabled(false);
			doneButton.setEnabled(false);
			
			unitTextfield.setText("");
			unitLabel.setText("");
			conversionFactorAtextfield.setText("1.0");
			conversionFactorBtextfield.setText("1.0");
			
		}
		
		private void updateWidgetsTestMode() {
			
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			resetButton.setEnabled(false);
			doneButton.setEnabled(true);

     		doneButton.requestFocus();
			
		}
		
		private void updateWidgetsFullPacket() {
				
			offsetTextfield.setEnabled(false);
			processorCombobox.setEnabled(false);
			nameTextfield.setEnabled(false);
			colorButton.setEnabled(false);
			unitTextfield.setEnabled(false);
			conversionFactorAtextfield.setEnabled(false);
			conversionFactorBtextfield.setEnabled(false);
			addButton.setEnabled(false);
			resetButton.setEnabled(true);
			doneButton.setEnabled(true);

     		doneButton.requestFocus();
			
		}

	}
	
	/**
	 * The GUI for defining a Bitfield Dataset.
	 */
	@SuppressWarnings("serial")
	private class BitfieldPanel extends JPanel {
		
		List<Bitfield> fields = new ArrayList<Bitfield>();
		JPanel widgets = new JPanel(new MigLayout("wrap 2", "[pref][grow]"));
		Consumer<Integer> eventHandler;
		
		public BitfieldPanel(int bitCount, Dataset dataset) {
			
			super();
			setLayout(new BorderLayout());
			
			JPanel bottom = new JPanel();
			bottom.setLayout(new GridLayout(1, 3, 10, 10));
			bottom.setBorder(new EmptyBorder(5, 5, 5, 5));
			bottom.add(new JLabel(""));
			bottom.add(new JLabel(""));
			JButton doneButton = new JButton("Done");
			doneButton.addActionListener(event -> {
				dataset.setBitfields(fields);
				if(eventHandler != null)
					eventHandler.accept(fields.size());
			});
			bottom.add(doneButton);
			
			add(new Visualization(bitCount), BorderLayout.NORTH);
			add(new JScrollPane(widgets), BorderLayout.CENTER);
			add(bottom, BorderLayout.SOUTH);
			
		}
		
		/**
		 * The user added a new bitfield, so we add a new Bitfield object, and redraw the panel.
		 * 
		 * @param MSBit    The most-significant bit of the bitfield.
		 * @param LSBit    The least-significant bit of the bitfield.
		 */
		public void addField(int MSBit, int LSBit) {
			
			fields.add(new Bitfield(MSBit, LSBit));
			Collections.sort(fields);
			
			// update the GUI
			widgets.removeAll();
			widgets.add(new JLabel("<html><b>State&nbsp;&nbsp;&nbsp;</b></html>"));
			widgets.add(new JLabel("<html><b>Name&nbsp;&nbsp;&nbsp;</b></html>"));
			
			for(Bitfield field : fields) {
				for(int i = 0; i < field.textfieldLabels.length; i++) {
					widgets.add(field.textfieldLabels[i]);
					widgets.add(field.textfields[i], "growx"); // stretch to fill column width
				}
				widgets.add(new JLabel(" "));
				widgets.add(new JLabel(" "));
			}
			widgets.remove(widgets.getComponentCount() - 1);
			widgets.remove(widgets.getComponentCount() - 1);
			
			revalidate();
			repaint();
			
		}
		
		/**
		 * This panel contains the "tiles" that represent each bit in the bitfield.
		 * The user clicks or click-and-drags on the bits to specify a range for each bitfield.
		 */
		private class Visualization extends JPanel {
			
			int bitCount;
			
			final int padding = (int) (2 * Theme.tilePadding); // space inside each button, between the edges and the text label
			final int spacing = (int) Theme.tilePadding; // space between each button
			final Color tileColor = new Color(Theme.tileColor[0], Theme.tileColor[1], Theme.tileColor[2], Theme.tileColor[3]);
			final Color tileSelectedColor = new Color(Theme.tileSelectedColor[0], Theme.tileSelectedColor[1], Theme.tileSelectedColor[2], Theme.tileSelectedColor[3]);
			
			int maxTextWidth;
			int textHeight;
			int buttonWidth;
			int buttonHeight;
			
			int firstBit = -1;
			int lastBit = -1;
			
			public Visualization(int bitCount) {
				
				super();
				this.bitCount = bitCount;
				
				// determine the required size of this panel
				maxTextWidth = getFontMetrics(getFont()).stringWidth(bitCount - 1 + "");
				textHeight = getFontMetrics(getFont()).getAscent();
				buttonWidth = padding + maxTextWidth + padding;
				buttonHeight = padding + textHeight + padding;
				int totalWidth = (bitCount * buttonWidth) + (spacing * (bitCount + 1));
				int totalHeight = spacing + buttonHeight + spacing;
				Dimension size = new Dimension(totalWidth, totalHeight);
				setMinimumSize(size);
				setPreferredSize(size);
				setMaximumSize(size);
				
				addMouseListener(new MouseListener() {
					
					@Override public void mousePressed(MouseEvent e) {
						firstBit = -1;
						lastBit = -1;
						
						// if the user clicked on a button, mark it as the first and last bit *if* this bit is not already being used
						int x = e.getX();
						int y = e.getY();
						for(int i = 0; i < bitCount; i++) {
							int minX = spacing + (i * (spacing + buttonWidth));
							int maxX = minX + buttonWidth;
							int minY = spacing;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitAvailable = true;
								for(Bitfield field : fields)
									if(bit >= field.LSBit && bit <= field.MSBit)
										bitAvailable = false;
								if(bitAvailable) {
									firstBit = bit;
									lastBit = bit;
								}
							}
						}
						repaint();
					}
					
					@Override public void mouseReleased(MouseEvent e) {
						if(firstBit == -1 || lastBit == -1)
							return;
						
						// the user released the mouse, so add the field
						addField(Integer.max(firstBit, lastBit), Integer.min(firstBit, lastBit));
						firstBit = -1;
						lastBit = -1;
						repaint();
					}
					
					@Override public void mouseExited(MouseEvent e) { }
					@Override public void mouseEntered(MouseEvent e) { }
					@Override public void mouseClicked(MouseEvent e) { }
				});
				
				addMouseMotionListener(new MouseMotionListener() {
					
					@Override public void mouseMoved(MouseEvent e) { }
					
					@Override public void mouseDragged(MouseEvent e) {
						if(firstBit == -1 || lastBit == -1)
							return;
						
						// the user moved the mouse, so update the proposed bit range *if* the entire range is available
						int x = e.getX();
						int y = e.getY();
						for(int i = 0; i < bitCount; i++) {
							int minX = spacing + (i * (spacing + buttonWidth));
							int maxX = minX + buttonWidth;
							int minY = spacing;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitRangeAvailable = true;
								for(int b = Integer.min(bit, firstBit); b <= Integer.max(bit, firstBit); b++)
									for(Bitfield field : fields)
										if(b >= field.LSBit && b <= field.MSBit)
											bitRangeAvailable = false;
								if(bitRangeAvailable)
									lastBit = bit;
							}
						}
						repaint();
					}
				});
				
			}
			
			@Override protected void paintComponent(Graphics g) {
				
				super.paintComponent(g);
				
				// draw the background
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				
				// draw each button
				g.setColor(tileColor);
				for(int i = 0; i < bitCount; i++) {
					int x = spacing + (i * (spacing + buttonWidth));
					int y = spacing;
					g.fillRect(x, y, buttonWidth, buttonHeight);
				}
				
				// draw existing fields
				g.setColor(getBackground());
				for(Bitfield field : fields) {
					int width = padding + maxTextWidth + padding + (field.MSBit - field.LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = spacing + ((bitCount - 1 - field.MSBit) * (spacing + buttonWidth));
					int y = spacing;
					g.fillRect(x, y, width, height);
				}
				
				// draw the proposed new field
				if(firstBit >= 0 && lastBit >= 0) {
					g.setColor(tileSelectedColor);
					int MSBit = Integer.max(firstBit, lastBit);
					int LSBit = Integer.min(firstBit, lastBit);
					int width = padding + maxTextWidth + padding + (MSBit - LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = spacing + ((bitCount - 1 - MSBit) * (spacing + buttonWidth));
					int y = spacing;
					g.fillRect(x, y, width, height);
				}
				
				// draw each text label
				g.setColor(Color.BLACK);
				for(int i = 0; i < bitCount; i++) {
					int x = spacing + (i * (spacing + buttonWidth)) + padding;
					int y = spacing + padding + textHeight;
					String text = bitCount - 1 - i + "";
					x += (maxTextWidth - getFontMetrics(getFont()).stringWidth(text)) / 2; // adjust x to center the text
					g.drawString(text, x, y);
				}
				
			}
			
		}
		
	}

	
}
