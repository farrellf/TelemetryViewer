import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class DataStructureCsvView extends JPanel {
	
	private ConnectionTelemetry connection;
	private DatasetsController datasets;
	
	private JLabel      dsdLabel;
	private JTextField  columnTextfield;
	private JTextField  nameTextfield;
	private JButton     colorButton;
	private JTextField  unitTextfield;
	private JTextField  conversionFactorAtextfield;
	private JTextField  conversionFactorBtextfield;
	private JLabel      unitLabel;
	private JButton     addButton;
	private JButton     doneButton;
	private JTable      dataStructureTable;
	private JTabbedPane exampleCodePane;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	public DataStructureCsvView(ConnectionTelemetry connection) {
		
		super();
		
		this.connection = connection;
		this.datasets = connection.datasets;
		
		// all JTextFields let the user press enter to add the field
		ActionListener pressEnterToAddRow = event -> addButton.doClick();
		
		// column number of the field
		int column = datasets.getFirstAvailableLocation();
		columnTextfield = new JTextField(column == -1 ? " - " : Integer.toString(column), 3);
		columnTextfield.addActionListener(pressEnterToAddRow);
		columnTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					columnTextfield.setText(columnTextfield.getText().trim());
					Integer.parseInt(columnTextfield.getText());
				} catch(Exception e) {
					columnTextfield.setText(Integer.toString(datasets.getFirstAvailableLocation()));
				}
			}
			@Override public void focusGained(FocusEvent fe) {
				columnTextfield.selectAll();
			}
		});
		
		// name of the field
		nameTextfield = new JTextField("", 15);
		nameTextfield.addActionListener(pressEnterToAddRow);
		nameTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e)   { nameTextfield.setText(nameTextfield.getText().trim()); }
			@Override public void focusGained(FocusEvent e) { nameTextfield.selectAll(); }
		});
		
		// color of the field
		colorButton = new JButton("\u25B2");
		colorButton.setForeground(Theme.defaultDatasetColor);
		colorButton.addActionListener(event -> colorButton.setForeground(ColorPickerView.getColor(nameTextfield.getText(), colorButton.getForeground())));
		
		// unit of the field
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
		
		// conversion ratio of the field as "x = x [Units]"
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
		
		unitLabel = new JLabel("", JLabel.LEFT) {
			@Override public Dimension getMinimumSize()   { return unitTextfield.getPreferredSize(); }
			@Override public Dimension getPreferredSize() { return unitTextfield.getPreferredSize(); }
		};
		
		// add button to insert the new field into the data structure (if possible)
		addButton = new JButton("Add");
		addButton.addActionListener(event -> {
			
			int location = Integer.parseInt(columnTextfield.getText());
			String name = nameTextfield.getText().trim();
			Color color = colorButton.getForeground();
			String unit = unitTextfield.getText();
			float conversionFactorA = Float.parseFloat(conversionFactorAtextfield.getText());
			float conversionFactorB = Float.parseFloat(conversionFactorBtextfield.getText());
				
			if(name.equals("")) {
				JOptionPane.showMessageDialog(DataStructureCsvView.this, "Error: A name is required.", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			String errorMessage = datasets.insert(location, null, name, color, unit, conversionFactorA, conversionFactorB);
			if(errorMessage != null)
				JOptionPane.showMessageDialog(DataStructureCsvView.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
			
			updateGui(true);

		});
		
		// done button for when the data structure is complete
		doneButton = new JButton("Done");
		doneButton.addActionListener(event -> {
			if(datasets.getCount() == 0) {
				JOptionPane.showMessageDialog(DataStructureCsvView.this, "Error: Define at least one field, or disconnect.", "Error", JOptionPane.ERROR_MESSAGE);
			} else {
				connection.dataStructureDefined = true;
				if(ChartsController.getCharts().isEmpty())
					NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
				Main.hideConfigurationGui();
			}
		});
		
		// table to visualize the data structure
		dataStructureTable = new JTable(new AbstractTableModel() {
			@Override public String getColumnName(int column) {
				if(column == 0)      return "Column Number";
				else if(column == 1) return "Name";
				else if(column == 2) return "Color";
				else if(column == 3) return "Unit";
				else if(column == 4) return "Conversion Ratio";
				else if(column == 5) return "";
				else                 return "Error";
			}
			
			@Override public Object getValueAt(int row, int column) {
				Dataset dataset = datasets.getByIndex(row);
				if(column == 0)      return Integer.toString(dataset.location);
				else if(column == 1) return dataset.name;
				else if(column == 2) return "<html><font color=\"rgb(" + dataset.color.getRed() + "," + dataset.color.getGreen() + "," + dataset.color.getBlue() + ")\">\u25B2</font></html>";
				else if(column == 3) return dataset.unit;
				else if(column == 4) return String.format("%3.3f = %3.3f %s", dataset.conversionFactorA, dataset.conversionFactorB, dataset.unit);
				else                 return "";
			}
			
			@Override public int getRowCount() {
				return datasets.getCount();
			}
			
			@Override public int getColumnCount() {
				return 6;
			}
		});
		dataStructureTable.setRowHeight((int) (dataStructureTable.getFont().getStringBounds("Abcdefghijklmnopqrstuvwxyz", new FontRenderContext(null, true, true)).getHeight() * 1.5));
		dataStructureTable.getColumn("").setCellRenderer(new TableCellRenderer() {
			@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				JButton b = new JButton("Remove");
				if(connection.mode == ConnectionTelemetry.Mode.DEMO)
					b.setEnabled(false);
				return b;
			}
		});
		dataStructureTable.addMouseListener(new MouseListener() {
			
			// ask the user to confirm
			@Override public void mousePressed(MouseEvent e) {
				if(connection.mode == ConnectionTelemetry.Mode.DEMO)
					return;
				Dataset dataset = datasets.getByIndex(dataStructureTable.getSelectedRow());
				String title = "Remove " + dataset.name + "?";
				String message = "<html>Remove the " + dataset.name + " dataset?";
				if(datasets.getSampleCount() > 0)
					message += "<br>WARNING: This will also remove all acquired samples from EVERY dataset!</html>";
				boolean remove = JOptionPane.showConfirmDialog(DataStructureCsvView.this, message, title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
				if(remove) {
					columnTextfield.setText(Integer.toString(dataset.location));
					nameTextfield.setText(dataset.name);
					colorButton.setForeground(dataset.color);
					unitTextfield.setText(dataset.unit);
					unitLabel.setText(dataset.unit);
					conversionFactorAtextfield.setText(Float.toString(dataset.conversionFactorA));
					conversionFactorBtextfield.setText(Float.toString(dataset.conversionFactorB));
					datasets.removeAllData();
					datasets.remove(dataset.location);
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

		// tabs for displaying example code
		exampleCodePane = new JTabbedPane();
		
		// layout the panel
		Font titleFont = getFont().deriveFont(Font.BOLD, getFont().getSize() * 1.4f);
		setLayout(new MigLayout("fill, gap " + Theme.padding, Theme.padding + "[][][][][][][][][][][][][][][][]push[][][]" + Theme.padding, "[][][50%][][][50%]0"));
		dsdLabel = new JLabel("Data Structure Definition:");
		dsdLabel.setFont(titleFont);
		add(dsdLabel, "grow, span");
		add(new JLabel("Column Number"));
		add(columnTextfield);
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
		add(new JScrollPane(dataStructureTable), "grow, span");
		
		JLabel efLabel = new JLabel("Example Firmware / Software:");
		efLabel.setFont(titleFont);
		JLabel spacerLabel = new JLabel(" ");
		spacerLabel.setFont(titleFont);
		add(spacerLabel, "grow, span");
		add(efLabel, "grow, span");
		add(exampleCodePane, "grow, span");
		
		setMinimumSize(new Dimension(getPreferredSize().width, 32 * (int) (getFontMetrics(dataStructureTable.getFont())).getHeight()));
		
		SwingUtilities.invokeLater(() -> updateGui(true)); // invokeLater to ensure focus isn't taken away
		
	}
	
	private void updateGui(boolean updateColumnNumber) {
		
		if(connection.mode == ConnectionTelemetry.Mode.DEMO) {
			
			dsdLabel.setText("Data Structure Definition: (Not Editable in Demo Mode)");
			columnTextfield.setEnabled(false);
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
			
		} else {
			
			dsdLabel.setText("Data Structure Definition:");
			columnTextfield.setEnabled(true);
			nameTextfield.setEnabled(true);
			colorButton.setEnabled(true);
			unitTextfield.setEnabled(true);
			conversionFactorAtextfield.setEnabled(true);
			conversionFactorBtextfield.setEnabled(true);
			addButton.setEnabled(true);
			doneButton.setEnabled(true);
			
			if(updateColumnNumber) {
				int column = datasets.getFirstAvailableLocation();
				columnTextfield.setText(column == -1 ? " - " : Integer.toString(column));
			}
			
			SwingUtilities.invokeLater(() -> { // invokeLater to ensure a following revalidate/repaint does not take focus away
				nameTextfield.requestFocus();
				nameTextfield.selectAll();
			});
			
		}
		
		dataStructureTable.revalidate();
		dataStructureTable.repaint();
		
		updateExampleCode();
		
		revalidate();
		repaint();
		
	}
	
	private void updateExampleCode() {

		// get the commonly used data
		int baudRate = connection.baudRate;
		int datasetsCount = datasets.getCount();
		
		List<String> datasetNames = new ArrayList<String>(datasetsCount);
		String intPrintfVariables = new String();
		String floatPrintfVariables = new String();
		for(Dataset dataset : datasets.getList()) {
			String name = dataset.name.replace(' ', '_').toLowerCase();
			datasetNames.add(name);
			intPrintfVariables += name + ", ";
			floatPrintfVariables += name + "_text, ";
		}
		if(intPrintfVariables.length() > 0)
			intPrintfVariables = intPrintfVariables.substring(0, intPrintfVariables.length() - 2);
		if(floatPrintfVariables.length() > 0)
			floatPrintfVariables = floatPrintfVariables.substring(0, floatPrintfVariables.length() - 2);
		
		String printfFormatString = new String();
		int intPrintfLength = 1;
		int floatPrintfLength = 1;
		int i = 0;
		for(Dataset dataset : datasets.getList()) {
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
		if(printfFormatString.length() > 0)
			printfFormatString = printfFormatString.substring(0, printfFormatString.length() - 1);
		
		exampleCodePane.removeAll();
		
		// show basic arduino code for UART mode and demo mode
		if(connection.mode == ConnectionTelemetry.Mode.UART || connection.mode == ConnectionTelemetry.Mode.DEMO) {
			
			JTextArea code = new JTextArea();
			code.setEditable(false);
			code.setTabSize(4);
			code.setFont(new Font("Consolas", Font.PLAIN, dataStructureTable.getFont().getSize()));
			exampleCodePane.add("Arduino", new JScrollPane(code));
			
			if(datasetsCount == 0) {
				
					code.setText("[...waiting for at least one CSV column...]");
					
			} else {
			
				code.setText("// this code is a crude template\n");
				code.append("// you will need to edit this\n");
				code.append("\n");
				code.append("void setup() {\n");
				code.append("\tSerial.begin(" + baudRate + ");\n");
				code.append("}\n");
				code.append("\n");
				
				code.append("// use this loop if sending integers\n");
				code.append("void loop() {\n");
				for(String name : datasetNames)
					code.append("\tint " + name + " = ...;\n");
				code.append("\n");
				code.append("\tchar text[" + intPrintfLength + "];\n");
				code.append("\tsnprintf(text, " + intPrintfLength + ", \"" + printfFormatString + "\", " + intPrintfVariables + ");\n");
				code.append("\tSerial.println(text);\n");
				code.append("\n");
				code.append("\tdelay(...);\n");
				code.append("}\n");
				code.append("\n");
				
				code.append("// or use this loop if sending floats\n");
				code.append("void loop() {\n");
				for(String name : datasetNames)
					code.append("\tfloat " + name + " = ...;\n");
				code.append("\n");
				for(String name : datasetNames)
					code.append("\tchar " + name + "_text[30];\n");
				code.append("\n");
				for(String name : datasetNames)
					code.append("\tdtostrf(" + name + ", 10, 10, " + name + "_text);\n");
				code.append("\n");
				code.append("\tchar text[" + floatPrintfLength + "];\n");
				code.append("\tsnprintf(text, " + floatPrintfLength + ", \"" + printfFormatString.replace('d', 's') + "\", " + floatPrintfVariables + ");\n");
				code.append("\tSerial.println(text);\n");
				code.append("\n");
				code.append("\tdelay(...);\n");
				code.append("}\n");
				
				// scroll back to the top
				code.setCaretPosition(0);
			
			}
		
		}
		
		// show arduino/esp8266 code for udp mode
		if(connection.mode == ConnectionTelemetry.Mode.UDP) {
			
			JTextArea code = new JTextArea();
			code.setEditable(false);
			code.setTabSize(4);
			code.setFont(new Font("Consolas", Font.PLAIN, dataStructureTable.getFont().getSize()));
			exampleCodePane.add("Arduino + ESP8266", new JScrollPane(code));
			
			if(datasetsCount == 0) {
				
				code.setText("[...waiting for at least one CSV column...]");
					
			} else {
			
				code.setText("// this code is a crude template\n");
				code.append("// you will need to edit this\n");
				code.append("\n");
				code.append("void setup() {\n");
				code.append("\tpinMode(LED_BUILTIN, OUTPUT);\n");
				code.append("\tSerial.begin(" + baudRate + ");\n");
				code.append("\n");
				code.append("\tif(esp8266_test_communication() &&\n");
				code.append("\t   esp8266_reset() &&\n");
				code.append("\t   esp8266_client_mode() &&\n");
				code.append("\t   esp8266_join_ap(\"your_wifi_network_name_here\", \"your_wifi_password_here\") && // EDIT THIS LINE\n");
				code.append("\t   esp8266_start_udp(\"" + ConnectionTelemetry.localIp + "\", " + connection.portNumber + ")) { // EDIT THIS LINE\n");
				code.append("\n");
				code.append("\t\t// success, turn on LED\n");
				code.append("\t\tdigitalWrite(LED_BUILTIN, HIGH);\n");
				code.append("\n");
				code.append("\t} else {\n");
				code.append("\n");
				code.append("\t\t// failure, blink LED\n");
				code.append("\t\twhile(true) {\n");
				code.append("\t\t\tdigitalWrite(LED_BUILTIN, HIGH);\n");
				code.append("\t\t\tdelay(1000);\n");
				code.append("\t\t\tdigitalWrite(LED_BUILTIN, LOW);\n");
				code.append("\t\t\tdelay(1000);\n");
				code.append("\t\t}\n");
				code.append("\n");
				code.append("\t}\n");
				code.append("\n");
				code.append("}\n");
				code.append("\n");
				
				code.append("// use this loop if sending integers\n");
				code.append("void loop() {\n");
				for(String name : datasetNames)
					code.append("\tint " + name + " = ...; // EDIT THIS LINE\n");
				code.append("\n");
				code.append("\tchar text[" + intPrintfLength + "];\n");
				code.append("\tsnprintf(text, " + intPrintfLength + ", \"" + printfFormatString + "\\n\", " + intPrintfVariables + ");\n");
				code.append("\tesp8266_transmit_udp(text);\n");
				code.append("}\n");
				code.append("\n");
				
				code.append("// or use this loop if sending floats\n");
				code.append("void loop() {\n");
				for(String name : datasetNames)
					code.append("\tfloat " + name + " = ...; // EDIT THIS LINE\n");
				code.append("\n");
				for(String name : datasetNames)
					code.append("\tchar " + name + "_text[30];\n");
				code.append("\n");
				for(String name : datasetNames)
					code.append("\tdtostrf(" + name + ", 10, 10, " + name + "_text);\n");
				code.append("\n");
				code.append("\tchar text[" + floatPrintfLength + "];\n");
				code.append("\tsnprintf(text, " + floatPrintfLength + ", \"" + printfFormatString.replace('d', 's') + "\\n\", " + floatPrintfVariables + ");\n");
				code.append("\tesp8266_transmit_udp(text);\n");
				code.append("}\n");
				code.append("\n");
				
				code.append("#define MAX_COMMAND_TIME  10000 // milliseconds\n");
				code.append("\n");

				code.append("bool esp8266_test_communication(void) {\n");
				code.append("\tdelay(500); // wait for module to boot up\n");
				code.append("\tSerial.print(\"AT\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"OK\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				code.append("\n");

				code.append("bool esp8266_reset(void) {\n");
				code.append("\tSerial.print(\"AT+RST\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"ready\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				code.append("\n");

				code.append("bool esp8266_client_mode(void) {\n");
				code.append("\tSerial.print(\"AT+CWMODE=1\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"OK\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				code.append("\n");

				code.append("bool esp8266_join_ap(String ssid, String password) {\n");
				code.append("\tSerial.print(\"AT+CWJAP=\\\"\" + ssid + \"\\\",\\\"\" + password + \"\\\"\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"WIFI CONNECTED\"))\n");
				code.append("\t\t\tbreak;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"WIFI GOT IP\"))\n");
				code.append("\t\t\tbreak;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"OK\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				code.append("\n");

				code.append("bool esp8266_start_udp(String ip_address, int port_number) {\n");
				code.append("\tSerial.print(\"AT+CIPSTART=\\\"UDP\\\",\\\"\" + ip_address + \"\\\",\" + port_number + \"\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"CONNECT\"))\n");
				code.append("\t\t\tbreak;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"OK\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				code.append("\n");

				code.append("bool esp8266_transmit_udp(String text) {\n");
				code.append("\tSerial.print(\"AT+CIPSEND=\" + String(text.length()) + \"\\r\\n\");\n");
				code.append("\tunsigned long startTime = millis();\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"OK\"))\n");
				code.append("\t\t\tbreak;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\">\"))\n");
				code.append("\t\t\tbreak;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("\tSerial.print(text);\n");
				code.append("\twhile(true) {\n");
				code.append("\t\tif(Serial.find(\"SEND OK\"))\n");
				code.append("\t\t\treturn true;\n");
				code.append("\t\tif(millis() > startTime + MAX_COMMAND_TIME)\n");
				code.append("\t\t\treturn false;\n");
				code.append("\t}\n");
				code.append("}\n");
				
				// scroll back to the top
				code.setCaretPosition(0);
			
			}
			
		}
		
		// show java code for TCP mode
		if(connection.mode == ConnectionTelemetry.Mode.TCP) {
			
			JTextArea code = new JTextArea();
			code.setEditable(false);
			code.setTabSize(4);
			code.setFont(new Font("Consolas", Font.PLAIN, dataStructureTable.getFont().getSize()));
			exampleCodePane.add("Java", new JScrollPane(code));
			
			if(datasetsCount == 0) {
				
				code.setText("[...waiting for at least one CSV column...]");
					
			} else {
			
				code.setText("// this code is a crude template\n");
				code.append("// you will need to edit this\n");
				code.append("\n");
				code.append("import java.io.PrintWriter;\n");
				code.append("import java.net.Socket;\n");
				code.append("\n");
				code.append("public class Main {\n");
				code.append("\n");
				code.append("\tpublic static void main(String[] args) throws InterruptedException {\n");
				code.append("\n");
				code.append("\t\t// enter an infinite loop that tries to connect to the TCP server once every 3 seconds\n");
				code.append("\t\twhile(true) {\n");
				code.append("\n");
				code.append("\t\t\ttry(Socket socket = new Socket(\"" + ConnectionTelemetry.localIp + "\", " + connection.portNumber + ")) { // EDIT THIS LINE\n");
				code.append("\n");
				code.append("\t\t\t\t// enter another infinite loop that sends packets of telemetry\n");
				code.append("\t\t\t\tPrintWriter output = new PrintWriter(socket.getOutputStream(), true);\n");
				code.append("\t\t\t\twhile(true) {\n");
				for(String name : datasetNames)
					code.append("\t\t\t\t\tfloat " + name + " = ...; // EDIT THIS LINE\n");
				code.append("\t\t\t\t\toutput.println(String.format(\"" + printfFormatString.replace('d', 'f') + "\", " + intPrintfVariables + "));\n");
				code.append("\t\t\t\t\tif(output.checkError())\n");
				code.append("\t\t\t\t\t\tthrow new Exception();\n");
				code.append("\t\t\t\t\tThread.sleep(" + Math.round(1000.0 / connection.sampleRate) + ");\n");
				code.append("\t\t\t\t}\n");
				code.append("\n");
				code.append("\t\t\t} catch(Exception e) {\n");
				code.append("\n");
				code.append("\t\t\t\tThread.sleep(3000);\n");
				code.append("\n");
				code.append("\t\t\t}\n");
				code.append("\n");
				code.append("\t\t}\n");
				code.append("\n");
				code.append("\t}\n");
				code.append("\n");
				code.append("}\n");
				
				// scroll back to the top
				code.setCaretPosition(0);
				
			}
			
		}
		
		// show java code for UDP mode
		if(connection.mode == ConnectionTelemetry.Mode.UDP) {
			
			JTextArea code = new JTextArea();
			code.setEditable(false);
			code.setTabSize(4);
			code.setFont(new Font("Consolas", Font.PLAIN, dataStructureTable.getFont().getSize()));
			exampleCodePane.add("Java", new JScrollPane(code));
			
			if(datasetsCount == 0) {
				
				code.setText("[...waiting for at least one CSV column...]");
					
			} else {
			
				code.setText("// this code is a crude template\n");
				code.append("// you will need to edit this\n");
				code.append("\n");
				code.append("import java.net.DatagramPacket;\n");
				code.append("import java.net.DatagramSocket;\n");
				code.append("import java.net.InetAddress;\n");
				code.append("\n");
				code.append("public class Main {\n");
				code.append("\n");
				code.append("\tpublic static void main(String[] args) throws InterruptedException {\n");
				code.append("\n");
				code.append("\t\t// enter an infinite loop that binds a UDP socket\n");
				code.append("\t\twhile(true) {\n");
				code.append("\n");
				code.append("\t\t\ttry(DatagramSocket socket = new DatagramSocket()) {\n");
				code.append("\n");
				code.append("\t\t\t\t// enter another infinite loop that sends packets of telemetry\n");
				code.append("\t\t\t\twhile(true) {\n");
				for(String name : datasetNames)
					code.append("\t\t\t\t\tfloat " + name + " = ...; // EDIT THIS LINE\n");
				code.append("\t\t\t\t\tbyte[] buffer = String.format(\"" + printfFormatString.replace('d', 'f') + "\\n\", " + intPrintfVariables + ").getBytes();\n");
				code.append("\t\t\t\t\tDatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length, InetAddress.getByName(\"" + ConnectionTelemetry.localIp + "\"), " + connection.portNumber + "); // EDIT THIS LINE\n");
				code.append("\t\t\t\t\tsocket.send(packet);\n");
				code.append("\t\t\t\t\tThread.sleep(" + Math.round(1000.0 / connection.sampleRate) + ");\n");
				code.append("\t\t\t\t}\n");
				code.append("\n");
				code.append("\t\t\t} catch(Exception e) {\n");
				code.append("\n");
				code.append("\t\t\t\tThread.sleep(3000);\n");
				code.append("\n");
				code.append("\t\t\t}\n");
				code.append("\n");
				code.append("\t\t}\n");
				code.append("\n");
				code.append("\t}\n");
				code.append("\n");
				code.append("}\n");
				
				// scroll back to the top
				code.setCaretPosition(0);
				
			}
			
		}
	
	}

}
