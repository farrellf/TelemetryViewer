import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
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
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;

/**
 * A class for describing and processing binary packets.
 * 
 * The data structure of a binary packet can be defined interactively by the user, or by loading a layout file.
 * 
 * 
 * When defined by loading a layout file, Controller.openLayout() will:
 * 1. Call DatasetsController.removeAllDatasets(); to start over fresh.
 * 2. Repeatedly call Communication.packet.insertField() to define each of the fields.
 * 3. Optionally call Communication.packet.insertChecksum() if a checksum field is used.
 * 4. Call CommunicationController.connect(), which will call PacketBinary.instance.startReceivingData().
 * 5. Create the charts.
 * 
 * 
 * When defined by user interaction:
 * 1. The CommunicationView gets the possible packet types from CommunicationController.getPacketTypes(), and one of them is "Binary".
 * 2. The user clicks Connect in the CommunicationView, which calls CommunicationController.connect().
 * 3. If a successful connection occurs, Communication.packet.getDataStructureGui() is called.
 * 4. That GUI lets the user define the binary packet data structure by calling methods of this class:
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
 * 5. When use user clicks Done in the DataStructureGui, PacketBinary.instance.startReceivingData() will be allowed to parse incoming data.
 * 6. The user can then interactively create the charts.
 */
public class PacketBinary extends Packet {
	
	static PacketBinary instance = new PacketBinary();

	private byte syncWord = (byte) 0xAA;
	private int packetSize = 1; // total byte count: includes sync word, normal fields, and optional checksum field
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private PacketBinary() { }
	
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
			else if(column == 4) return dataset.isBitfield ? "" : String.format("%3.3f = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
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
			
			@Override public String toString()                   { return "int16 LSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float)(short) (((0xFF & rawBytes[0]) << 0) |
					                                                                      ((0xFF & rawBytes[1]) << 8));}
			
		};
		
		processors[3] = new BinaryFieldProcessor() {
			
			@Override public String toString()                   { return "int16 MSB First"; }
			@Override public int getByteCount()                  { return 2; }
			@Override public float extractValue(byte[] rawBytes) { return (float)(short) (((0xFF & rawBytes[1]) << 0) |
			                                                                              ((0xFF & rawBytes[0]) << 8));}
			
		};
		
		processors[4] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "float32 LSB First"; }
			@Override public int getByteCount()               { return 4; }
			@Override public float extractValue(byte[] bytes) { return Float.intBitsToFloat(((0xFF & bytes[0]) <<  0) |
			                                                                                ((0xFF & bytes[1]) <<  8) |
			                                                                                ((0xFF & bytes[2]) << 16) |
			                                                                                ((0xFF & bytes[3]) << 24));}
			
		};
		
		processors[5] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "float32 MSB First"; }
			@Override public int getByteCount()               { return 4; }
			@Override public float extractValue(byte[] bytes) { return Float.intBitsToFloat(((0xFF & bytes[3]) <<  0) |
                                                                                            ((0xFF & bytes[2]) <<  8) |
                                                                                            ((0xFF & bytes[1]) << 16) |
                                                                                            ((0xFF & bytes[0]) << 24));}
			
		};
		
		processors[6] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "Bitfield: 8 Bits"; }
			@Override public int getByteCount()               { return 1; }
			@Override public float extractValue(byte[] bytes) { return (float) (0xFF & bytes[0]);}
			
		};
		
		processors[7] = new BinaryFieldProcessor() {
			
			@Override public String toString()                { return "uint8"; }
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
	 * This method should only be called after a connection has been made.
	 * 
	 * @param stream    The data to process.
	 */
	@Override public void startReceivingData(InputStream stream) {
		
		thread = new Thread(() -> {
			
			// wait for the data structure to be defined
			while(!dataStructureDefined) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// stop and end this thread
					NotificationsController.showVerboseForSeconds("The Binary Packet Processor thread is stopping.", 5, false);
					return;
				}
			}
				
			byte[] rx_buffer = new byte[packetSize];
			BufferedInputStream bStream = new BufferedInputStream(stream, 2 * packetSize);
			
			// tell the user we're connected
			String waitingForTelemetry = CommunicationController.getPort().startsWith(CommunicationController.PORT_UART) ? CommunicationController.getPort().substring(6) + " is connected. Send telemetry." :
			                             CommunicationController.getPort().equals(CommunicationController.PORT_TCP)      ? "The TCP server is running. Send telemetry to " + CommunicationController.getLoclIpAddress() :
			                             CommunicationController.getPort().equals(CommunicationController.PORT_UDP)      ? "The UDP server is running. Send telemetry to " + CommunicationController.getLoclIpAddress() : "";
			String receivingTelemetry  = CommunicationController.getPort().startsWith(CommunicationController.PORT_UART) ? CommunicationController.getPort().substring(6) + " is connected and receiving telemetry." :
			                             CommunicationController.getPort().equals(CommunicationController.PORT_TCP)      ? "The TCP server is running and receiving telemetry." :
			                             CommunicationController.getPort().equals(CommunicationController.PORT_UDP)      ? "The UDP server is running and receiving telemetry." : "";
			int oldSampleCount = DatasetsController.getSampleCount();
			Timer t = new Timer(100, event -> {
				if(CommunicationController.isConnected()) {
					if(DatasetsController.getSampleCount() == oldSampleCount)
						NotificationsController.showSuccessUntil(waitingForTelemetry, () -> DatasetsController.getSampleCount() > oldSampleCount, true);
					else
						NotificationsController.showVerboseForSeconds(receivingTelemetry, 5, true);
				}
			});
			t.setRepeats(false);
			t.start();
			
			// parse the telemetry
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
	 * Prepares a GUI for the user to interactively define the binary packet's data structure.
	 */
	@Override public JPanel getDataStructureGui() {
		
		// reset the GUI
		if(PacketBinary.instance.getFirstAvailableOffset() != -1)
			BinaryDataStructureGui.instance.offsetTextfield.setText(Integer.toString(PacketBinary.instance.getFirstAvailableOffset()));
		BinaryDataStructureGui.instance.processorCombobox.setSelectedIndex(0);
		BinaryDataStructureGui.instance.nameTextfield.setText("");
		BinaryDataStructureGui.instance.colorButton.setForeground(Theme.defaultDatasetColor);
		BinaryDataStructureGui.instance.unitTextfield.setText("");
		BinaryDataStructureGui.instance.conversionFactorAtextfield.setText("1.0");
		BinaryDataStructureGui.instance.conversionFactorBtextfield.setText("1.0");
		BinaryDataStructureGui.instance.unitLabel.setText("");
		if(BinaryDataStructureGui.instance.bitfieldDefinitionInProgress) {
			for(Component c : BinaryDataStructureGui.instance.getComponents())
				if(c instanceof BitfieldPanel) {
					BinaryDataStructureGui.instance.remove(c);
					break;
				}
			BinaryDataStructureGui.instance.add(BinaryDataStructureGui.instance.scrollableDataStructureTable, "grow, span, cell 0 2");
			BinaryDataStructureGui.instance.bitfieldDefinitionInProgress = false;
		}
		
		SwingUtilities.invokeLater(() -> BinaryDataStructureGui.instance.updateGui(true)); // invokeLater to ensure focus isn't taken away
		
		return BinaryDataStructureGui.instance;
		
	}

	/**
	 * Window where the user defines the binary packet data structure.
	 */
	@SuppressWarnings("serial")
	public static class BinaryDataStructureGui extends JPanel {
		
		static BinaryDataStructureGui instance = new BinaryDataStructureGui();
		
		JLabel            dsdLabel;
		JTextField        offsetTextfield;
		JComboBox<Object> processorCombobox;
		JTextField        nameTextfield;
		JButton           colorButton;
		JTextField        unitTextfield;
		JTextField        conversionFactorAtextfield;
		JTextField        conversionFactorBtextfield;
		JLabel            unitLabel;
		JButton           addButton;
		JButton           doneButton;
		
		JTable dataStructureTable;
		JScrollPane scrollableDataStructureTable;
		
		boolean bitfieldDefinitionInProgress;
		
		/**
		 * Private constructor to enforce singleton usage.
		 */
		private BinaryDataStructureGui() {
			
			super();
			
			// all JTextFields let the user press enter to add the field
			ActionListener pressEnterToAddField = event -> addButton.doClick();
			
			// offset (first byte) of the field
			offsetTextfield = new JTextField(Integer.toString(PacketBinary.instance.getFirstAvailableOffset()), 3);
			offsetTextfield.addActionListener(pressEnterToAddField);
			offsetTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent fe) {
					try {
						offsetTextfield.setText(offsetTextfield.getText().trim());
						Integer.parseInt(offsetTextfield.getText());
					} catch(Exception e) {
						offsetTextfield.setText(Integer.toString(PacketBinary.instance.getFirstAvailableOffset()));
						if(PacketBinary.instance.isFull())
							addButton.setEnabled(false);
					}
				}
				@Override public void focusGained(FocusEvent fe) {
					offsetTextfield.selectAll();
				}
			});

			// processor of the field (the processor converts raw bytes into numbers, or evaluates checksums) 
			processorCombobox = new JComboBox<Object>();
			for(BinaryFieldProcessor processor : PacketBinary.getBinaryFieldProcessors())
				processorCombobox.addItem(processor);
			for(BinaryChecksumProcessor processor : PacketBinary.getBinaryChecksumProcessors())
				processorCombobox.addItem(processor);
			processorCombobox.addActionListener(event -> updateGui(true));
			
			// name of the field
			nameTextfield = new JTextField("", 15);
			nameTextfield.addActionListener(pressEnterToAddField);
			nameTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent e)   { nameTextfield.setText(nameTextfield.getText().trim()); }
				@Override public void focusGained(FocusEvent e) { nameTextfield.selectAll(); }
			});
			
			// color of the field
			colorButton = new JButton("\u25B2");
			colorButton.setForeground(Theme.defaultDatasetColor);
			colorButton.addActionListener(event -> {
				Color color = JColorChooser.showDialog(BinaryDataStructureGui.this, "Pick a Color for " + nameTextfield.getText(), Color.BLACK);
				if(color != null)
					colorButton.setForeground(color);
			});
			
			// unit of the field
			unitTextfield = new JTextField("", 15);
			unitTextfield.addActionListener(pressEnterToAddField);
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
			
			// conversion ratio of the field as "x = x [Units]"
			conversionFactorAtextfield = new JTextField("1.0", 4);
			conversionFactorAtextfield.addActionListener(pressEnterToAddField);
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
			conversionFactorBtextfield.addActionListener(pressEnterToAddField);
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
			
			unitLabel = new JLabel("", JLabel.LEFT) {
				@Override public Dimension getMinimumSize()   { return unitTextfield.getPreferredSize(); }
				@Override public Dimension getPreferredSize() { return unitTextfield.getPreferredSize(); }
			};	
			
			// add button to insert the new field into the data structure (if possible)
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
						JOptionPane.showMessageDialog(BinaryDataStructureGui.this, "A name is required.", "Error: Name Required", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					String errorMessage = PacketBinary.instance.insertField(location, (BinaryFieldProcessor) processor, name, color, unit, conversionFactorA, conversionFactorB);
					if(errorMessage != null)
						JOptionPane.showMessageDialog(BinaryDataStructureGui.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
					
					if(((BinaryFieldProcessor) processor).toString().startsWith("Bitfield") && errorMessage == null) {

						BitfieldPanel bitfieldPanel = new BitfieldPanel(((BinaryFieldProcessor) processor).getByteCount() * 8, DatasetsController.getDatasetByLocation(location));
						
						remove(scrollableDataStructureTable);
						add(bitfieldPanel, "grow, span, cell 0 2");
						bitfieldDefinitionInProgress = true;
						
					}
					
				} else if(processor instanceof BinaryChecksumProcessor) {
					
					String errorMessage = PacketBinary.instance.insertChecksum(location, (BinaryChecksumProcessor) processor);
					if(errorMessage != null)
						JOptionPane.showMessageDialog(BinaryDataStructureGui.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
					
				}
				
				updateGui(true);
				
			});
			
			// done button for when the data structure is complete
			doneButton = new JButton("Done");
			doneButton.addActionListener(event -> {
				if(PacketBinary.instance.isEmpty()) {
					JOptionPane.showMessageDialog(BinaryDataStructureGui.this, "Error: Define at least one field, or disconnect.", "Error", JOptionPane.ERROR_MESSAGE);
				} else {
					PacketBinary.instance.dataStructureDefined = true;
					if(ChartsController.getCharts().isEmpty())
						NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
					Main.hideDataStructureGui();
				}
			});
			
			// table to visualize the data structure
			dataStructureTable = new JTable(new AbstractTableModel() {
				@Override public String getColumnName(int column) {
					if(column == 0)      return "Byte Offset, Data Type";
					else if(column == 1) return "Name";
					else if(column == 2) return "Color";
					else if(column == 3) return "Unit";
					else if(column == 4) return "Conversion Ratio";
					else if(column == 5) return "";
					else                 return "Error";
				}
				
				@Override public Object getValueAt(int row, int column) {
					return column < 5 ? PacketBinary.instance.getCellContents(column, row) : "";
				}
				
				@Override public int getRowCount() {
					return PacketBinary.instance.getRowCount();
				}
				
				@Override public int getColumnCount() {
					return 6;
				}
				
			});
			dataStructureTable.setRowHeight((int) (dataStructureTable.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight() * 1.5));
			dataStructureTable.getColumn("").setCellRenderer(new TableCellRenderer() {
				@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
					JButton b = new JButton("Remove");
					if(CommunicationController.getPort().equals(CommunicationController.PORT_TEST))
						b.setEnabled(false);
					return row != 0 ? b : new JLabel("");
				}
			});
			dataStructureTable.addMouseListener(new MouseListener() {
				
				// ask the user to confirm
				@Override public void mousePressed(MouseEvent e) {
					if(CommunicationController.getPort().equals(CommunicationController.PORT_TEST))
						return;
					int datasetNumber = dataStructureTable.getSelectedRow() - 1; // -1 because of the syncword
					if(datasetNumber < 0) {
						// syncword clicked, do nothing
					} else if(datasetNumber < DatasetsController.getDatasetsCount()) {
						// remove button for a dataset was clicked
						Dataset dataset = DatasetsController.getDatasetByIndex(datasetNumber);
						String title = "Remove " + dataset.name + "?";
						String message = "<html>Remove the " + dataset.name + " dataset?";
						if(DatasetsController.getSampleCount() > 0)
							message += "<br>WARNING: This will also remove all acquired samples from EVERY dataset!</html>";
						boolean remove = JOptionPane.showConfirmDialog(BinaryDataStructureGui.this, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
						if(remove) {
							offsetTextfield.setText(Integer.toString(dataset.location));
							for(ActionListener al : processorCombobox.getActionListeners())
								processorCombobox.removeActionListener(al);
							for(int i = 0; i < processorCombobox.getItemCount(); i++)
								if(processorCombobox.getItemAt(i).toString().equals(dataset.processor.toString()))
									processorCombobox.setSelectedIndex(i);
							processorCombobox.addActionListener(event -> updateGui(true));
							nameTextfield.setText(dataset.name);
							colorButton.setForeground(dataset.color);
							unitTextfield.setText(dataset.unit);
							unitLabel.setText(dataset.unit);
							conversionFactorAtextfield.setText(Float.toString(dataset.conversionFactorA));
							conversionFactorBtextfield.setText(Float.toString(dataset.conversionFactorB));
							DatasetsController.removeAllData();
							PacketBinary.instance.removeField(dataset.location);
						}
					} else {
						// remove button for the checksum was clicked
						String title = "Remove checksum?";
						String message = "<html>Remove the " + PacketBinary.instance.checksumProcessor + "?";
						if(DatasetsController.getSampleCount() > 0)
							message += "<br>WARNING: This will also remove all acquired samples from EVERY dataset!</html>";
						boolean remove = JOptionPane.showConfirmDialog(BinaryDataStructureGui.this, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
						if(remove) {
							offsetTextfield.setText(Integer.toString(PacketBinary.instance.checksumProcessorOffset));
							processorCombobox.setSelectedItem(PacketBinary.instance.checksumProcessor);
							DatasetsController.removeAllData();
							PacketBinary.instance.removeChecksum();
						}
					}
					dataStructureTable.clearSelection();
					updateGui(false);
				}
				
				// clear the selection again, in case the user click-and-dragged over the table
				@Override public void mouseReleased(MouseEvent e) {
					dataStructureTable.clearSelection();
					updateGui(false);
				}
				
				@Override public void mouseExited(MouseEvent e) { }
				@Override public void mouseEntered(MouseEvent e) { }
				@Override public void mouseClicked(MouseEvent e) { }
			});
			
			// layout the panel
			Font titleFont = getFont().deriveFont(Font.BOLD, getFont().getSize() * 1.4f);
			setLayout(new MigLayout("fill, gap " + Theme.padding, Theme.padding + "[][][][][][][][][][][][][][][][][][]push[][][]" + Theme.padding, "[][][100%]0"));
			dsdLabel = new JLabel("Data Structure Definition:");
			dsdLabel.setFont(titleFont);
			add(dsdLabel, "grow, span");
			add(new JLabel("Byte Offset"));
			add(offsetTextfield);
			add(Box.createHorizontalStrut(Theme.padding));
			add(processorCombobox);
			add(Box.createHorizontalStrut(Theme.padding));
			add(new JLabel("Name"));
			add(nameTextfield);
			add(Box.createHorizontalStrut(Theme.padding));
			add(new JLabel("Color"));
			add(colorButton);
			add(Box.createHorizontalStrut(Theme.padding));
			add(new JLabel("Unit"));
			add(unitTextfield);
			add(Box.createHorizontalStrut(Theme.padding * 4));
			add(conversionFactorAtextfield);
			add(new JLabel(" = "));
			add(conversionFactorBtextfield);
			add(unitLabel);
			add(Box.createHorizontalStrut(Theme.padding * 4));
			add(addButton);
			add(doneButton, "wrap");
			scrollableDataStructureTable = new JScrollPane(dataStructureTable);
			add(scrollableDataStructureTable, "grow, span");
			
			updateGui(true);
			setMinimumSize(new Dimension(getPreferredSize().width, 500));
			setVisible(true);
			
		}
		
		/**
		 * Updates the GUI to reflect the current state (enables/disables/configures widgets as needed.)
		 */
		private void updateGui(boolean updateOffsetNumber) {
			
			if(CommunicationController.getPort().equals(CommunicationController.PORT_TEST)) {
				
				dsdLabel.setText("Data Structure Definition: (Not Editable in Test Mode)");
				offsetTextfield.setEnabled(false);
				processorCombobox.setEnabled(false);
				nameTextfield.setEnabled(false);
				colorButton.setEnabled(false);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(false);
				doneButton.setEnabled(true);
				SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
					doneButton.requestFocus();
				});
	     		
			} else if(bitfieldDefinitionInProgress) {
				
				dsdLabel.setText("Data Structure Definition:");
				offsetTextfield.setEnabled(false);
				processorCombobox.setEnabled(false);
				nameTextfield.setEnabled(false);
				colorButton.setEnabled(false);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(false);
				doneButton.setEnabled(false);
				unitTextfield.setText("");
				unitLabel.setText("");
				conversionFactorAtextfield.setText("1.0");
				conversionFactorBtextfield.setText("1.0");
				
			} else if(PacketBinary.instance.isFull()) {
				
				dsdLabel.setText("Data Structure Definition:");
				offsetTextfield.setEnabled(false);
				processorCombobox.setEnabled(false);
				nameTextfield.setEnabled(false);
				colorButton.setEnabled(false);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(false);
				doneButton.setEnabled(true);
				SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
					doneButton.requestFocus();
				});
	     		
			} else if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield")) {
				
				dsdLabel.setText("Data Structure Definition:");
				offsetTextfield.setEnabled(true);
				processorCombobox.setEnabled(true);
				nameTextfield.setEnabled(true);
				colorButton.setEnabled(true);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(true);
				doneButton.setEnabled(true);
				unitTextfield.setText("");
				unitLabel.setText("");
				conversionFactorAtextfield.setText("1.0");
				conversionFactorBtextfield.setText("1.0");
				if(updateOffsetNumber)
					offsetTextfield.setText(Integer.toString(PacketBinary.instance.getFirstAvailableOffset()));
				SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
				});
				
			} else if(processorCombobox.getSelectedItem() instanceof BinaryFieldProcessor) {
				
				dsdLabel.setText("Data Structure Definition:");
				offsetTextfield.setEnabled(true);
				processorCombobox.setEnabled(true);
				nameTextfield.setEnabled(true);
				colorButton.setEnabled(true);
				unitTextfield.setEnabled(true);
				conversionFactorAtextfield.setEnabled(true);
				conversionFactorBtextfield.setEnabled(true);
				addButton.setEnabled(true);
				doneButton.setEnabled(true);
				if(updateOffsetNumber) {
					offsetTextfield.setText(Integer.toString(PacketBinary.instance.getFirstAvailableOffset()));
					if(processorCombobox.getSelectedItem().toString().startsWith("Bitfield") || processorCombobox.getSelectedItem() instanceof BinaryChecksumProcessor)
						processorCombobox.setSelectedIndex(0);
				}
				SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
				});
				
				
			} else if(processorCombobox.getSelectedItem() instanceof BinaryChecksumProcessor) {
				
				dsdLabel.setText("Data Structure Definition:");
				offsetTextfield.setEnabled(true);
				processorCombobox.setEnabled(true);
				nameTextfield.setEnabled(false);
				colorButton.setEnabled(false);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(true);
				doneButton.setEnabled(true);
				nameTextfield.setText("");
				unitTextfield.setText("");
				unitLabel.setText("");
				conversionFactorAtextfield.setText("1.0");
				conversionFactorBtextfield.setText("1.0");
				SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
				});
				
			}
			
			dataStructureTable.revalidate();
			dataStructureTable.repaint();
			revalidate();
			repaint();
			
		}

	}
	
	/**
	 * The GUI for defining a Bitfield Dataset.
	 */
	@SuppressWarnings("serial")
	private static class BitfieldPanel extends JPanel {
		
		JPanel widgets = new JPanel(new MigLayout("wrap 3, gap " + Theme.padding, "[pref][pref][grow]"));
		Dataset dataset;
		
		public BitfieldPanel(int bitCount, Dataset dataset) {
			
			super();
			setLayout(new BorderLayout());
			
			JButton doneButton = new JButton("Done With Bitfield");
			doneButton.addActionListener(event -> {
				BinaryDataStructureGui.instance.remove(this);
				BinaryDataStructureGui.instance.add(BinaryDataStructureGui.instance.scrollableDataStructureTable, "grow, span, cell 0 2");
				BinaryDataStructureGui.instance.bitfieldDefinitionInProgress = false;
				BinaryDataStructureGui.instance.updateGui(true);
			});
			JPanel bottom = new JPanel();
			bottom.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			bottom.setBorder(new EmptyBorder(Theme.padding, 0, 0, 0));
			bottom.add(doneButton);
			
			add(new Visualization(bitCount), BorderLayout.NORTH);
			add(new JScrollPane(widgets), BorderLayout.CENTER);
			add(bottom, BorderLayout.SOUTH);
			
			this.dataset = dataset;
			
		}
		
		/**
		 * The user added a new bitfield, so update and redraw the panel.
		 * 
		 * @param MSBit    The most-significant bit of the bitfield.
		 * @param LSBit    The least-significant bit of the bitfield.
		 */
		public void addField(int MSBit, int LSBit) {
			
			dataset.addBitfield(MSBit, LSBit);
			
			List<Dataset.Bitfield> bitfields = dataset.getBitfields();
			Collections.sort(bitfields);
			
			// update the GUI
			widgets.removeAll();
			widgets.add(new JLabel("<html><b>State&nbsp;&nbsp;&nbsp;</b></html>"));
			widgets.add(new JLabel("<html><b>Color&nbsp;&nbsp;&nbsp;</b></html>"));
			widgets.add(new JLabel("<html><b>Name&nbsp;&nbsp;&nbsp;</b></html>"));
			
			for(Dataset.Bitfield bitfield : bitfields) {
				for(Dataset.Bitfield.State state : bitfield.states) {
					JTextField textfield = new JTextField(state.name);
					textfield.addKeyListener(new KeyListener() {
						@Override public void keyTyped(KeyEvent e)    { }
						@Override public void keyPressed(KeyEvent e)  { }
						@Override public void keyReleased(KeyEvent e) {
							textfield.setText(textfield.getText().replace(',', ' ')); // no commas allowed, because they will break import/export logic
							state.name = textfield.getText();
						}
					});
					textfield.addFocusListener(new FocusListener() {
						@Override public void focusLost(FocusEvent e)   { }
						@Override public void focusGained(FocusEvent e) {
							textfield.selectAll();
						}
					});
					
					JButton colorButton = new JButton("\u25B2");
					colorButton.setForeground(state.color);
					colorButton.addActionListener(event -> {
						Color color = JColorChooser.showDialog(BitfieldPanel.this, "Pick a Color for " + state.name, state.color);
						if(color != null) {
							colorButton.setForeground(color);
							state.color = color;
							state.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1};
						}
					});
					
					widgets.add(new JLabel(state.label));
					widgets.add(colorButton);
					widgets.add(textfield, "growx"); // stretch to fill column width
				}
				widgets.add(new JLabel(" "));
				widgets.add(new JLabel(" "));
				widgets.add(new JLabel(" "));
			}
			widgets.remove(widgets.getComponentCount() - 1);
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
			
			final int padding = Theme.padding * 2; // space inside each button, between the edges and the text label
			final int spacing = Theme.padding; // space between each button
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
				int totalWidth = (bitCount * buttonWidth) + (spacing * bitCount);
				int totalHeight = buttonHeight + spacing;
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
							int minX = i * (spacing + buttonWidth);
							int maxX = minX + buttonWidth;
							int minY = 0;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitAvailable = true;
								for(Dataset.Bitfield bitfield : dataset.getBitfields())
									if(bit >= bitfield.LSBit && bit <= bitfield.MSBit)
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
							int minX = i * (spacing + buttonWidth);
							int maxX = minX + buttonWidth;
							int minY = 0;
							int maxY = minY + buttonHeight;
							if(x >= minX && x <= maxX && y >= minY && y <= maxY) {
								int bit = bitCount - 1 - i;
								boolean bitRangeAvailable = true;
								for(int b = Integer.min(bit, firstBit); b <= Integer.max(bit, firstBit); b++)
									for(Dataset.Bitfield bitfield : dataset.getBitfields())
										if(b >= bitfield.LSBit && b <= bitfield.MSBit)
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
					int x = i * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, buttonWidth, buttonHeight);
				}
				
				// draw existing fields
				g.setColor(getBackground());
				for(Dataset.Bitfield bitfield : dataset.getBitfields()) {
					int width = padding + maxTextWidth + padding + (bitfield.MSBit - bitfield.LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = (bitCount - 1 - bitfield.MSBit) * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, width, height);
				}
				
				// draw the proposed new field
				if(firstBit >= 0 && lastBit >= 0) {
					g.setColor(tileSelectedColor);
					int MSBit = Integer.max(firstBit, lastBit);
					int LSBit = Integer.min(firstBit, lastBit);
					int width = padding + maxTextWidth + padding + (MSBit - LSBit) * (spacing + buttonWidth);
					int height = padding + textHeight + padding;
					int x = (bitCount - 1 - MSBit) * (spacing + buttonWidth);
					int y = 0;
					g.fillRect(x, y, width, height);
				}
				
				// draw each text label
				for(int i = 0; i < bitCount; i++) {
					g.setColor(Color.BLACK);
					for(Dataset.Bitfield bitfield : dataset.getBitfields())
						if((bitCount - 1 - i) >= bitfield.LSBit && (bitCount - 1 - i) <= bitfield.MSBit)
							g.setColor(Color.LIGHT_GRAY);
					int x = i * (spacing + buttonWidth) + padding;
					int y = padding + textHeight;
					String text = bitCount - 1 - i + "";
					x += (maxTextWidth - getFontMetrics(getFont()).stringWidth(text)) / 2; // adjust x to center the text
					g.drawString(text, x, y);
				}
				
			}
			
		}
		
	}

	
}
