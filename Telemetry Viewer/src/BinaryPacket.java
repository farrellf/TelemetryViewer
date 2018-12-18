import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.font.FontRenderContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

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
public class BinaryPacket implements Packet {

	private byte syncWord;
	public BinaryChecksumProcessor checksumProcessor;
	public int checksumProcessorOffset;
	private int packetSize; // total byte count: includes sync word, fields, and checksum field
	private Thread thread;
	
	/**
	 * Creates an object with a default sync word, but no fields, and no checksum processor.
	 */
	public BinaryPacket() {
		
		syncWord = (byte) 0xAA;
		Controller.removeAllDatasets();
		checksumProcessor = null;
		checksumProcessorOffset = -1;
		packetSize = 1; // the syncWord
		
		thread = null;
		
	}
	
	/**
	 * Description shown in the packetTypeCombobox in the ControlsRegion.
	 */
	@Override public String toString() {
		
		return Communication.PACKET_TYPE_BINARY;
		
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
	public String insertField(int byteOffset, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {

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
		for(Dataset dataset : Controller.getAllDatasets()) {
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
		Controller.insertDataset(byteOffset, processor, name, color, unit, conversionFactorA, conversionFactorB);
		
		// update packetSize
		int newPacketSize = 1;
		for(Dataset dataset : Controller.getAllDatasets()) {
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
	 * @param byteOffset    The field at this offset will be removed.
	 * @return              null on success, or a user-friendly String describing why the field could not be added.
	 */
	public String removeField(int byteOffset) {
		
		if(byteOffset == 0)
			return "Error: Can not remove the sync word.";
		
		boolean success = Controller.removeDataset(byteOffset);
		if(!success)
			return "Error: No field exists at that location.";

		// update packetSize if there is no checksum
		if(checksumProcessor == null) {
			int newPacketSize = 1;
			for(Dataset dataset : Controller.getAllDatasets()) {
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
	 * Adds a checksum field to the data structure if possible.
	 * 
	 * @param byteOffset    Binary packet byte offset.
	 * @param processor     The type of checksum field.
	 * @return              null on success, or a user-friendly String describing why the checksum field could not be added.
	 */
	public String insertChecksum(int byteOffset, BinaryChecksumProcessor processor) {

		if(checksumProcessor != null)
			return "Error: A checksum field already exists.";
		
		if(byteOffset == 0)
			return "Error: A checksum field can not overlap with the sync word.";
		
		if(byteOffset < packetSize)
			return "Error: A checksum field can not be placed in front of existing fields.";
		
		if((byteOffset - 1) % processor.getByteCount() != 0)
			return "Error: The checksum must be aligned. The number of bytes before the checksum, not counting the sync word, must be a multiple of " + processor.getByteCount() + " for this checksum type.";
		
		// add the checksum processor
		checksumProcessor = processor;
		checksumProcessorOffset = byteOffset;
		
		// update packetSize
		packetSize = byteOffset + processor.getByteCount();
		
		// no errors
		return null;
		
	}
	
	/**
	 * Removes the checksum field from the data structure if possible.
	 * 
	 * @return    null on success, or a user-friendly String describing why the checksum field could not be removed.
	 */
	public String removeChecksum() {
		
		if(checksumProcessor == null)
			return "Error: There was no checksum processor to remove.";
		
		// remove the checksum processor
		checksumProcessor = null;
		checksumProcessorOffset = -1;
		
		// update packetSize
		int newPacketSize = 1;
		for(Dataset dataset : Controller.getAllDatasets()) {
			int endByte = dataset.location + dataset.processor.getByteCount() - 1;
			if(endByte + 1 > newPacketSize)
				newPacketSize = endByte + 1;
		}
		packetSize = newPacketSize;
		
		// no errors
		return null;
		
	}
	
	
	/**
	 * Removes the checksum and all fields from the data structure, leaving just the sync word.
	 */
	@Override public void clear() {
		
		Controller.removeAllDatasets();
		checksumProcessor = null;
		packetSize = 1; // the syncWord
		
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
		for(Dataset dataset : Controller.getAllDatasets()) {
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
		count += Controller.getDatasetsCount();
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
		int count = Controller.getDatasetsCount();
		if(row < count) {
			Dataset dataset = Controller.getDatasetByIndex(row);
			if(column == 0)      return dataset.location + ", " + dataset.processor.toString();
			else if(column == 1) return dataset.name;
			else if(column == 2) return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
			else if(column == 3) return dataset.unit;
			else if(column == 4) return String.format("%3.3f LSBs = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
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
		for(Dataset dataset : Controller.getAllDatasets()) {
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
		
		BinaryFieldProcessor[] processors = new BinaryFieldProcessor[8];
		
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
			@Override public String toString()                   { return "uint32 LSB First"; }
			@Override public int getByteCount()                  { return 4; }
			@Override public float extractValue(byte[] rawBytes) { return (float) (((0xFF & rawBytes[0]) <<  0) |
												((0xFF & rawBytes[1]) <<  8) |
												((0xFF & rawBytes[2]) << 16) |
												((0xFF & rawBytes[3]) << 24));}
		};
		
		processors[5] = new BinaryFieldProcessor() {
			@Override public String toString()                   { return "uint32 MSB First"; }
			@Override public int getByteCount()                  { return 4; }
			@Override public float extractValue(byte[] rawBytes) { return (float) (((0xFF & rawBytes[3]) <<  0) |
												((0xFF & rawBytes[2]) <<  8) |
												((0xFF & rawBytes[1]) << 16) |
												((0xFF & rawBytes[0]) << 24));}
		};

		processors[6] = new BinaryFieldProcessor() {
			@Override public String toString()                   { return "int16 LSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float) ((rawBytes[0] << 0) |
												(rawBytes[1] << 8));}
		};
		
		processors[7] = new BinaryFieldProcessor() {
			@Override public String toString()                   { return "int16 MSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float) ((rawBytes[1] << 0) |
			                                                                       (rawBytes[0] << 8));}
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
					for(Dataset dataset : Controller.getAllDatasets()) {
						BinaryFieldProcessor processor = dataset.processor;
						int byteOffset = dataset.location;
						int byteCount  = processor.getByteCount();
						
						byte[] buffer = new byte[byteCount];
						for(int i = 0; i < byteCount; i++)
							buffer[i] = rx_buffer[byteOffset + i - 1]; // -1 for syncWord
						
						float rawNumber = processor.extractValue(buffer);
						dataset.add(rawNumber);
					}
				
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
		
		JTextField nameTextfield;
		JButton    colorButton;
		JTextField unitTextfield;
		JTextField conversionFactorAtextfield;
		JTextField conversionFactorBtextfield;
		JLabel     unitLabel;
		
		JButton addButton;
		JButton resetButton;
		JButton doneButton;
		
		JTable dataStructureTable;
		JScrollPane scrollableDataStructureTable;
		
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
			JTextField offsetTextfield = new JTextField(Integer.toString(packet.getFirstAvailableOffset()), 3);
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
			JComboBox<Object> processorCombobox = new JComboBox<Object>();
			for(BinaryFieldProcessor processor : BinaryPacket.getBinaryFieldProcessors())
				processorCombobox.addItem(processor);
			for(BinaryChecksumProcessor processor : BinaryPacket.getBinaryChecksumProcessors())
				processorCombobox.addItem(processor);
			processorCombobox.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent ae) {
					if(processorCombobox.getSelectedItem() instanceof BinaryFieldProcessor) {
						nameTextfield.setEnabled(true);
						colorButton.setEnabled(true);
						unitTextfield.setEnabled(true);
						conversionFactorAtextfield.setEnabled(true);
						conversionFactorBtextfield.setEnabled(true);
					} else if(processorCombobox.getSelectedItem() instanceof BinaryChecksumProcessor) {
						nameTextfield.setEnabled(false);
						nameTextfield.setText("");
						colorButton.setEnabled(false);
						unitTextfield.setEnabled(false);
						unitTextfield.setText("");
						unitLabel.setText("");
						conversionFactorAtextfield.setEnabled(false);
						conversionFactorAtextfield.setText("1.0");
						conversionFactorBtextfield.setEnabled(false);
						conversionFactorBtextfield.setText("1.0");
					}
				}
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
			addButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					
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
						
						if(packet.isFull()) {
							offsetTextfield.setEnabled(false);
							processorCombobox.setEnabled(false);
							nameTextfield.setEnabled(false);
							colorButton.setEnabled(false);
							unitTextfield.setEnabled(false);
							conversionFactorAtextfield.setEnabled(false);
							conversionFactorBtextfield.setEnabled(false);
							addButton.setEnabled(false);
						} else {
							int newLocation = packet.getFirstAvailableOffset();
							offsetTextfield.setText(Integer.toString(newLocation));
							nameTextfield.requestFocus();
							nameTextfield.selectAll();
						}
						
					} else if(processor instanceof BinaryChecksumProcessor) {
						
						String errorMessage = packet.insertChecksum(location, (BinaryChecksumProcessor) processor);
						dataStructureTable.revalidate();
						dataStructureTable.repaint();
						
						if(errorMessage != null) {
							JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
							return;
						}
						
						if(packet.isFull()) {
							offsetTextfield.setEnabled(false);
							processorCombobox.setEnabled(false);
							nameTextfield.setEnabled(false);
							colorButton.setEnabled(false);
							unitTextfield.setEnabled(false);
							conversionFactorAtextfield.setEnabled(false);
							conversionFactorBtextfield.setEnabled(false);
							addButton.setEnabled(false);
						} else {
							int newLocation = packet.getFirstAvailableOffset();
							offsetTextfield.setText(Integer.toString(newLocation));
							nameTextfield.requestFocus();
							nameTextfield.selectAll();
							processorCombobox.setSelectedIndex(0);
						}
						
					}
					
				}
			});
			
			// user clicks Reset to remove all fields from the data structure
			resetButton = new JButton("Reset");
			resetButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent arg0) {
					packet.clear();
					dataStructureTable.revalidate();
					dataStructureTable.repaint();
					
					offsetTextfield.setEnabled(true);
					processorCombobox.setEnabled(true);
					nameTextfield.setEnabled(true);
					colorButton.setEnabled(true);
					unitTextfield.setEnabled(true);
					conversionFactorAtextfield.setEnabled(true);
					conversionFactorBtextfield.setEnabled(true);
					addButton.setEnabled(true);
					
					offsetTextfield.setText(Integer.toString(packet.getFirstAvailableOffset()));
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
					processorCombobox.setSelectedIndex(0);
				}
			});
			
			// user clicks Done when the data structure is complete
			doneButton = new JButton("Done");
			doneButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					if(packet.isEmpty()) {
						JOptionPane.showMessageDialog(BinaryDataStructureWindow.this, "Error: At least one field is required.", "Error", JOptionPane.ERROR_MESSAGE);
					} else {
						dispose();
					}
				}
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
			
			JPanel tablePanel = new JPanel(new GridLayout(1, 1));
			tablePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
			tablePanel.add(scrollableDataStructureTable, BorderLayout.CENTER);
			dataStructureTable.setRowHeight((int) tablePanel.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight()); // fixes display scaling issue
			
			add(dataEntryPanel, BorderLayout.NORTH);
			add(tablePanel, BorderLayout.CENTER);
			
			pack();
			setMinimumSize(new Dimension(getPreferredSize().width, 500));
			setLocationRelativeTo(parentWindow);
			
			nameTextfield.requestFocus();
			
			if(packet.isFull()) {
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
			}
			
			if(testMode) {
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
			}
			
			setModal(true);
			setVisible(true);
			
		}

	}
	
}
