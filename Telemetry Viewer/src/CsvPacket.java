import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.font.FontRenderContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

/**
 * A class for describing and processing ASCII CSV packets.
 * 
 * The data structure of a ASCII CSV packet can be defined interactively by the user, or by loading a layout file.
 * 
 * 
 * When defined by loading a layout file, Controller.openLayout() will:
 * 1. Call CsvPacket.clear() to start over fresh.
 * 2. Repeatedly call CsvPacket.insertField() to define each of the fields.
 * 4. Call Controller.connectToSerialPort(), which will call CsvPacket.startReceivingData().
 * 5. Create the charts.
 * 
 * 
 * When defined by user interaction:
 * 1. The ControlsRegion gets the possible packet types from Controller.getPacketTypes(), and one of them is an object of this class.
 * 2. The user clicks Connect in the ControlsRegion, which calls Controller.connectToSerialPort().
 * 3. If a successful connection occurs, CsvPacket.showDataStructureWindow() is called.
 * 4. That window lets the user define the ASCII CSV packet data structure by calling methods of this class:
 * 
 *        To modify or query the data structure:
 *            insertField()
 *            removeField()
 *            clear()
 *            isEmpty()
 *         
 *        To visualize the data structure with a JTable:
 *            getRowCount()
 *            getCellContents()
 *         
 *        To list possibilities for the user:
 *            getFirstAvailableColumn()
 *     
 * 5. When use user clicks Done in the CsvDataStructureWindow, Controller.connectToSerialPort() will call CsvPacket.startReceivingData().
 * 6. The user can then interactively create the charts.
 */
public class CsvPacket implements Packet {

	private Thread thread;
	
	/**
	 * Creates an object with no fields.
	 */
	public CsvPacket() {

		Controller.removeAllDatasets();
		
		thread = null;
		
	}
	
	/**
	 * Description shown in the packetTypeCombobox in the ControlsRegion.
	 */
	@Override public String toString() {
		
		return Communication.PACKET_TYPE_CSV;
		
	}
	
	/**
	 * Adds a field to the data structure if possible.
	 * 
	 * @param column               CSV column number, starting at 0.
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 * @return                     null on success, or a user-friendly String describing why the field could not be added.
	 */
	public String insertField(int column, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		// check for overlap with existing fields
		for(Dataset dataset : Controller.getAllDatasets())
			if(dataset.location == column)
				return "Error: A field already exists at column " + column + ".";
		
		// add the field
		Controller.insertDataset(column, null, name, color, unit, conversionFactorA, conversionFactorB);

		// no errors
		return null;
		
	}
	
	/**
	 * Removes a field from the data structure if possible.
	 * 
	 * @param column    The field at this CSV column number will be removed.
	 * @return          null on success, or a user-friendly String describing why the field could not be added.
	 */
	public String removeField(int column) {
		
		boolean success = Controller.removeDataset(column);
		if(!success)
			return "Error: No field exists at column " + column + ".";
		
		// no errors
		return null;
		
	}
	
	/**
	 * Removes all fields from the data structure.
	 */
	@Override public void clear() {
		
		Controller.removeAllDatasets();
		
	}
	
	/**
	 * Check if the data structure is empty.
	 * 
	 * @return    True if there is just a sync word, false otherwise.
	 */
	public boolean isEmpty() {
		
		if(Controller.getAllDatasets().length == 0)
			return true;
		else
			return false;
		
	}
	
	/**
	 * Gets the number of rows that should be shown in the CsvDataStructureWindow's JTable.
	 * 
	 * @return    The number of rows.
	 */
	public int getRowCount() {
		
		return Controller.getDatasetsCount();
		
	}
	
	/**
	 * Gets the text to show in a specific cell in the CsvDataStructureWindow's JTable.
	 * 
	 * Column 0 = CSV column number
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
		
		Dataset dataset = Controller.getDatasetByIndex(row);
		if(column == 0)      return Integer.toString(dataset.location);
		else if(column == 1) return dataset.name;
		else if(column == 2) return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
		else if(column == 3) return dataset.unit;
		else if(column == 4) return String.format("%3.3f LSBs = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
		else                 return "";
		
	}
	
	/**
	 * @return    The first unoccupied CSV column number.
	 */
	public int getFirstAvailableColumn() {
		
		// the packet is empty
		if(Controller.getAllDatasets().length == 0)
			return 0;
		
		// check for an opening in a sparse layout
		int max = Controller.getAllDatasets().length;
		for(int i = 0; i < max; i++) {
			boolean used = false;
			for(Dataset dataset : Controller.getAllDatasets())
				if(dataset.location == i)
					used = true;
			if(!used)
				return i;
		}
		
		// 0 to max-1 are used, so max is available
		return max;
		
		
	}

	/**
	 * Spawns a new thread that listens for incoming data, processes it, and populates the datasets.
	 * This method should only be called after the data structure has been defined and a connection has been made.
	 * 
	 * @param stream    The data to process.
	 */
	@Override public void startReceivingData(InputStream stream) {
		
		thread = new Thread(() -> {
				
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			
			while(true) {
				
				try {
					
					// wait for text to arrive
					while(!reader.ready())
						Thread.sleep(1);
					
					// parse received text
					String line = reader.readLine();
					String[] tokens = line.split(",");
					// ensure they can all be parsed as floats before populating the datasets
					for(Dataset dataset : Controller.getAllDatasets())
						Float.parseFloat(tokens[dataset.location]);
					for(Dataset dataset : Controller.getAllDatasets())
						dataset.add(Float.parseFloat(tokens[dataset.location]));
					
				} catch(NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException | SocketTimeoutException e1) {
					
					NotificationsController.showVerboseForSeconds("A corrupt or incomplete line was received.", 5, false);
					
				} catch(IOException | InterruptedException e2) {
					
					// stop and end this thread
					NotificationsController.showVerboseForSeconds("The CSV Packet Processor thread is stopping.", 5, false);
					try { reader.close(); } catch(Exception e) { }
					return;
					
				}
				
			}
		
		});
		
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.setName("CSV Packet Processor");
		thread.start();
		
	}
	
	/**
	 * Stops the CSV Packet Processor thread.
	 */
	@Override public void stopReceivingData() {
		
		if(thread != null && thread.isAlive()) {
			thread.interrupt();
			while(thread.isAlive()); // wait
		}
		
	}
	
	/**
	 * Displays a window for the user to define the ASCII CSV packet's data structure.
	 * 
	 * @param parentWindow    Window to center over.
	 * @param testMode        True for test mode (disables editing), false for normal mode.
	 */
	@Override public void showDataStructureWindow(JFrame parentWindow, boolean testMode) {
		
		new CsvDataStructureWindow(parentWindow, this, testMode);
		
	}

	/**
	 * The window where the user specifies details about the ASCII CSV packet data structure.
	 */
	@SuppressWarnings("serial")
	private class CsvDataStructureWindow extends JDialog {
		
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
		
		JTabbedPane exampleCodePane;
		JTextArea arduinoCode;
		
		/**
		 * Creates a new window where the user can define the ASCII CSV data structure.
		 * 
		 * @param parentWindow    The window to center this CsvDataStructureWindow over.
		 * @param packet          A CsvPacket representing the data structure.
		 * @param testMode        If true, the user will only be able to view, not edit, the data structure.
		 */
		public CsvDataStructureWindow(JFrame parentWindow, CsvPacket packet, boolean testMode) {
			
			super();
			
			setTitle(testMode ? "CSV Packet Data Structure (Not Editable in Test Mode)" : "CSV Packet Data Structure");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setLayout(new BorderLayout());
			
			// all JTextFields let the user press enter to add the row
			ActionListener pressEnterToAddRow = new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					addButton.doClick();
				}
			};
			
			// user specifies the column number of a field
			JTextField columnTextfield = new JTextField(Integer.toString(packet.getFirstAvailableColumn()), 3);
			columnTextfield.addActionListener(pressEnterToAddRow);
			columnTextfield.addFocusListener(new FocusListener() {
				@Override public void focusLost(FocusEvent fe) {
					try {
						columnTextfield.setText(columnTextfield.getText().trim());
						Integer.parseInt(columnTextfield.getText());
					} catch(Exception e) {
						columnTextfield.setText(Integer.toString(packet.getFirstAvailableColumn()));
					}
				}
				@Override public void focusGained(FocusEvent fe) {
					columnTextfield.selectAll();
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
					Color color = JColorChooser.showDialog(CsvDataStructureWindow.this, "Pick a Color for " + nameTextfield.getText(), Color.BLACK);
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
					
					int location = Integer.parseInt(columnTextfield.getText());
					String name = nameTextfield.getText().trim();
					Color color = colorButton.getForeground();
					String unit = unitTextfield.getText();
					float conversionFactorA = Float.parseFloat(conversionFactorAtextfield.getText());
					float conversionFactorB = Float.parseFloat(conversionFactorBtextfield.getText());
						
					if(name.equals("")) {
						JOptionPane.showMessageDialog(CsvDataStructureWindow.this, "Error: A name is required.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					String errorMessage = packet.insertField(location, name, color, unit, conversionFactorA, conversionFactorB);
					updateDataStructureTable();
					updateExampleCode();
					
					if(errorMessage != null) {
						JOptionPane.showMessageDialog(CsvDataStructureWindow.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					int newColumn = packet.getFirstAvailableColumn();
					columnTextfield.setText(Integer.toString(newColumn));
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
					
				}
			});
			
			// user clicks Reset to remove all fields from the data structure
			resetButton = new JButton("Reset");
			resetButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent arg0) {
					packet.clear();
					updateDataStructureTable();
					updateExampleCode();

					int newColumn = packet.getFirstAvailableColumn();
					columnTextfield.setText(Integer.toString(newColumn));
					nameTextfield.requestFocus();
					nameTextfield.selectAll();
				}
			});
			
			// user clicks Done when the data structure is complete
			doneButton = new JButton("Done");
			doneButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					if(packet.isEmpty()) {
						JOptionPane.showMessageDialog(CsvDataStructureWindow.this, "Error: At least one field is required.", "Error", JOptionPane.ERROR_MESSAGE);
					} else {
						dispose();
					}
				}
			});
			
			JPanel dataEntryPanel = new JPanel();
			dataEntryPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
			dataEntryPanel.add(new JLabel("Column Number"));
			dataEntryPanel.add(columnTextfield);
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
					if(column == 0)      return "Column Number";
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

			arduinoCode = new JTextArea();
			arduinoCode.setEditable(false);
			arduinoCode.setTabSize(4);
			arduinoCode.setFont(new Font("Consolas", Font.PLAIN, 12 * (int) Math.round((double) Toolkit.getDefaultToolkit().getScreenResolution() / 100.0)));
			exampleCodePane = new JTabbedPane();
			
			if(CommunicationController.getPort().startsWith("UART") || CommunicationController.getPort().startsWith("Test"))
				exampleCodePane.add("Arduino Code", new JScrollPane(arduinoCode));
			else if(CommunicationController.getPort().startsWith("UDP"))
				exampleCodePane.add("Arduino + ESP8266 Code", new JScrollPane(arduinoCode));
			exampleCodePane.setPreferredSize(new Dimension(exampleCodePane.getPreferredSize().width, 16 * (int) (getFontMetrics(arduinoCode.getFont())).getHeight()));

			add(dataEntryPanel, BorderLayout.NORTH);
			add(tablePanel, BorderLayout.CENTER);
			add(exampleCodePane, BorderLayout.SOUTH);
			
			pack();
			setMinimumSize(new Dimension(getPreferredSize().width, 32 * (int) (getFontMetrics(arduinoCode.getFont())).getHeight()));
			setLocationRelativeTo(parentWindow);
			
			nameTextfield.requestFocus();

			if(testMode) {
				columnTextfield.setEnabled(false);
				nameTextfield.setEnabled(false);
				colorButton.setEnabled(false);
				unitTextfield.setEnabled(false);
				conversionFactorAtextfield.setEnabled(false);
				conversionFactorBtextfield.setEnabled(false);
				addButton.setEnabled(false);
				resetButton.setEnabled(false);
				doneButton.setEnabled(true);
			}
			
			updateDataStructureTable();
			updateExampleCode();
			
			setModal(true);
			setVisible(true);
			
		}
		
		private void updateDataStructureTable() {
			
			dataStructureTable.revalidate();
			dataStructureTable.repaint();
			
		}
		
		private void updateExampleCode() {
			
			int baudRate = CommunicationController.getBaudRate();
			int datasetsCount = Controller.getDatasetsCount();
			Dataset[] datasets = Controller.getAllDatasets();
			
			if(datasetsCount == 0) {
				arduinoCode.setText("[...waiting for at least one CSV column...]");
				return;
			}
			
			List<String> datasetNames = new ArrayList<String>(datasetsCount);
			String intPrintfVariables = new String();
			String floatPrintfVariables = new String();
			for(Dataset dataset : datasets) {
				String name = dataset.name.replace(' ', '_').toLowerCase();
				datasetNames.add(name);
				intPrintfVariables += name + ", ";
				floatPrintfVariables += name + "_text, ";
			}
			intPrintfVariables = intPrintfVariables.substring(0, intPrintfVariables.length() - 2);
			floatPrintfVariables = floatPrintfVariables.substring(0, floatPrintfVariables.length() - 2);
			
			String printfFormatString = new String();
			int intPrintfLength = 1;
			int floatPrintfLength = 1;
			int i = 0;
			for(Dataset dataset : datasets) {
				while(i < dataset.location) {
					printfFormatString += "0,";
					intPrintfLength += 2;
					floatPrintfLength += 2;
					i++;
				}
				printfFormatString += "%d,";
				intPrintfLength += 7;
				floatPrintfLength += 31;
				i++;
			}
			printfFormatString = printfFormatString.substring(0, printfFormatString.length() - 1);
			
			if(CommunicationController.getPort().startsWith("UART") || CommunicationController.getPort().startsWith("Test")) {
				
				arduinoCode.setText("// this code is a crude template\n");
				arduinoCode.append("// you will need to edit this\n");
				arduinoCode.append("\n");
				arduinoCode.append("void setup() {\n");
				arduinoCode.append("\tSerial.begin(" + baudRate + ");\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");
				
				arduinoCode.append("// use this loop if sending integers\n");
				arduinoCode.append("void loop() {\n");
				for(String name : datasetNames)
					arduinoCode.append("\tint " + name + " = ...;\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tchar text[" + intPrintfLength + "];\n");
				arduinoCode.append("\tsnprintf(text, " + intPrintfLength + ", \"" + printfFormatString + "\", " + intPrintfVariables + ");\n");
				arduinoCode.append("\tSerial.println(text);\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tdelay(...);\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");
				
				arduinoCode.append("// or use this loop if sending floats\n");
				arduinoCode.append("void loop() {\n");
				for(String name : datasetNames)
					arduinoCode.append("\tfloat " + name + " = ...;\n");
				arduinoCode.append("\n");
				for(String name : datasetNames)
					arduinoCode.append("\tchar " + name + "_text[30];\n");
				arduinoCode.append("\n");
				for(String name : datasetNames)
					arduinoCode.append("\tdtostrf(" + name + ", 10, 10, " + name + "_text);\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tchar text[" + floatPrintfLength + "];\n");
				arduinoCode.append("\tsnprintf(text, " + floatPrintfLength + ", \"" + printfFormatString.replace('d', 's') + "\", " + floatPrintfVariables + ");\n");
				arduinoCode.append("\tSerial.println(text);\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tdelay(...);\n");
				arduinoCode.append("}\n");
			
			} else if(CommunicationController.getPort().startsWith("UDP")) {
				
				arduinoCode.setText("// this code is a crude template\n");
				arduinoCode.append("// you will need to edit this\n");
				arduinoCode.append("\n");
				arduinoCode.append("void setup() {\n");
				arduinoCode.append("\tpinMode(LED_BUILTIN, OUTPUT);\n");
				arduinoCode.append("\tSerial.begin(" + baudRate + ");\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tif(esp8266_test_communication() &&\n");
				arduinoCode.append("\t   esp8266_reset() &&\n");
				arduinoCode.append("\t   esp8266_client_mode() &&\n");
				arduinoCode.append("\t   esp8266_join_ap(\"your_wifi_network_name_here\", \"your_wifi_password_here\") && // EDIT THIS LINE\n");
				arduinoCode.append("\t   esp8266_start_udp(\"" + Communication.localIp + "\", " + CommunicationController.getPortNumber() + ")) { // EDIT THIS LINE\n");
				arduinoCode.append("\n");
				arduinoCode.append("\t\t// success, turn on LED\n");
				arduinoCode.append("\t\tdigitalWrite(LED_BUILTIN, HIGH);\n");
				arduinoCode.append("\n");
				arduinoCode.append("\t} else {\n");
				arduinoCode.append("\n");
				arduinoCode.append("\t\t// failure, blink LED\n");
				arduinoCode.append("\t\twhile(true) {\n");
				arduinoCode.append("\t\t\tdigitalWrite(LED_BUILTIN, HIGH);\n");
				arduinoCode.append("\t\t\tdelay(1000);\n");
				arduinoCode.append("\t\t\tdigitalWrite(LED_BUILTIN, LOW);\n");
				arduinoCode.append("\t\t\tdelay(1000);\n");
				arduinoCode.append("\t\t}\n");
				arduinoCode.append("\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");
				
				arduinoCode.append("// use this loop if sending integers\n");
				arduinoCode.append("void loop() {\n");
				for(String name : datasetNames)
					arduinoCode.append("\tint " + name + " = ...; // EDIT THIS LINE\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tchar text[" + intPrintfLength + "];\n");
				arduinoCode.append("\tsnprintf(text, " + intPrintfLength + ", \"" + printfFormatString + "\\n\", " + intPrintfVariables + ");\n");
				arduinoCode.append("\tesp8266_transmit_udp(text);\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");
				
				arduinoCode.append("// or use this loop if sending floats\n");
				arduinoCode.append("void loop() {\n");
				for(String name : datasetNames)
					arduinoCode.append("\tfloat " + name + " = ...; // EDIT THIS LINE\n");
				arduinoCode.append("\n");
				for(String name : datasetNames)
					arduinoCode.append("\tchar " + name + "_text[30];\n");
				arduinoCode.append("\n");
				for(String name : datasetNames)
					arduinoCode.append("\tdtostrf(" + name + ", 10, 10, " + name + "_text);\n");
				arduinoCode.append("\n");
				arduinoCode.append("\tchar text[" + floatPrintfLength + "];\n");
				arduinoCode.append("\tsnprintf(text, " + floatPrintfLength + ", \"" + printfFormatString.replace('d', 's') + "\\n\", " + floatPrintfVariables + ");\n");
				arduinoCode.append("\tesp8266_transmit_udp(text);\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");
				
				arduinoCode.append("#define MAX_COMMAND_TIME  10000 // milliseconds\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_test_communication(void) {\n");
				arduinoCode.append("\tdelay(500); // wait for module to boot up\n");
				arduinoCode.append("\tSerial.print(\"AT\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"OK\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_reset(void) {\n");
				arduinoCode.append("\tSerial.print(\"AT+RST\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"ready\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_client_mode(void) {\n");
				arduinoCode.append("\tSerial.print(\"AT+CWMODE=1\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"OK\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_join_ap(String ssid, String password) {\n");
				arduinoCode.append("\tSerial.print(\"AT+CWJAP=\\\"\" + ssid + \"\\\",\\\"\" + password + \"\\\"\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"WIFI CONNECTED\"))\n");
				arduinoCode.append("\t\t\tbreak;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"WIFI GOT IP\"))\n");
				arduinoCode.append("\t\t\tbreak;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"OK\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_start_udp(String ip_address, int port_number) {\n");
				arduinoCode.append("\tSerial.print(\"AT+CIPSTART=\\\"UDP\\\",\\\"\" + ip_address + \"\\\",\" + port_number + \"\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"CONNECT\"))\n");
				arduinoCode.append("\t\t\tbreak;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"OK\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				arduinoCode.append("\n");

				arduinoCode.append("bool esp8266_transmit_udp(String text) {\n");
				arduinoCode.append("\tSerial.print(\"AT+CIPSEND=\" + String(text.length()) + \"\\r\\n\");\n");
				arduinoCode.append("\tunsigned long startTime = millis();\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"OK\"))\n");
				arduinoCode.append("\t\t\tbreak;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\">\"))\n");
				arduinoCode.append("\t\t\tbreak;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("\tSerial.print(text);\n");
				arduinoCode.append("\twhile(true) {\n");
				arduinoCode.append("\t\tif(Serial.find(\"SEND OK\"))\n");
				arduinoCode.append("\t\t\treturn true;\n");
				arduinoCode.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				arduinoCode.append("\t\t\treturn false;\n");
				arduinoCode.append("\t}\n");
				arduinoCode.append("}\n");
				
			}
			
		}

	}
	
}
