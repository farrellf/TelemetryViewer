import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.URI;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * The panel of controls located at the bottom of the main window.
 */
@SuppressWarnings("serial")
public class ControlsRegion extends JPanel {
	
	JButton openLayoutButton;
	JButton saveLayoutButton;
	JButton exportCsvLogButton;
	JButton resetButton;
	JButton helpButton;
	
	JTextField columnsTextfield;
	JTextField rowsTextfield;
	
	JTextField sampleRateTextfield;
	JComboBox<Packet> packetTypeCombobox;
	JComboBox<String> portNamesCombobox;
	JComboBox<Integer> baudRatesCombobox;
	JButton connectButton;
	
	/**
	 * Creates the panel of controls and registers their event handlers.
	 */
	public ControlsRegion() {
		
		super();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(new EmptyBorder(5, 5, 5, 5));
		
		openLayoutButton = new JButton("Open Layout");
		openLayoutButton.addActionListener(event -> {
			
			JFileChooser inputFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(inputFile.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = inputFile.getSelectedFile().getAbsolutePath();
				Controller.openLayout(filePath);
			}
			
		});
		
		saveLayoutButton = new JButton("Save Layout");
		saveLayoutButton.setEnabled(false);
		saveLayoutButton.addActionListener(event -> {
			
			JFileChooser saveFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(saveFile.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = saveFile.getSelectedFile().getAbsolutePath();
				if(!filePath.endsWith(".txt"))
					filePath += ".txt";
				Controller.saveLayout(filePath);
			}

		});
		
		exportCsvLogButton = new JButton("Export CSV Log");
		exportCsvLogButton.addActionListener(event -> {
			
			JFileChooser saveFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(saveFile.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = saveFile.getSelectedFile().getAbsolutePath();
				if(!filePath.endsWith(".csv"))
					filePath += ".csv";
				Controller.exportCsvLogFile(filePath);
			}

		});
		
		resetButton = new JButton("Reset");
		resetButton.addActionListener(event -> Controller.removeAllCharts());
		
		helpButton = new JButton("Help");
		helpButton.addActionListener(event -> {
			
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			String helpText = "<html><b>Telemetry Viewer v0.4 (2017-07-21)</b><br>" +
			                  "A fast and easy tool for visualizing data received over a UART.<br><br>" +
			                  "Step 1: Use the controls at the lower-right corner of the main window to connect to a serial port.<br>" +
			                  "Step 2: A \"Data Structure\" window will pop up, use it to specify how your data is laid out, then click \"Done\"<br>" +
			                  "Step 3: Click-and-drag in the tiles region to place a chart.<br>" +
			                  "Step 4: A \"New Chart\" window will pop up, use it to specify the type of chart and its settings.<br>" +
			                  "Repeat steps 3 and 4 to create more charts.<br><br>" +
			                  "Use your scroll wheel to rewind or fast forward.<br>" +
			                  "Use your scroll wheel while holding down Ctrl to zoom in or out.<br>" +
			                  "Use your scroll wheel while holding down Shift to adjust display scaling.<br><br>" +
			                  "Click the x icon at the top-right corner of any chart to remove it.<br>" +
			                  "Click the gear icon at the top-right corner of any chart to change its settings.<br><br>" +
			                  "Click the \"Open Layout\" button to open a layout file.<br>" +
			                  "Click the \"Save Layout\" button to save your current configuration (port settings, data structure, and chart settings) to a file.<br>" +
			                  "Click the \"Export CSV Log\" button to save all of your acquired samples to a CSV file.<br>" +
			                  "Click the \"Reset\" button to remove all charts.<br><br>" +
			                  "This software is free and open source.<br>" +
			                  "Author: Farrell Farahbod</html>";
			JLabel helpLabel = new JLabel(helpText);
			JButton websiteButton = new JButton("<html><a href=\"http://www.farrellf.com/TelemetryViewer/\">http://www.farrellf.com/TelemetryViewer/</a></html>");
			websiteButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(ActionEvent e) {
					try { Desktop.getDesktop().browse(new URI("http://www.farrellf.com/TelemetryViewer/")); } catch(Exception ex) {}
				}
			});
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(helpLabel);
			panel.add(websiteButton);
			panel.add(new JLabel(" "));
			JOptionPane.showMessageDialog(parentWindow, panel, "Help", JOptionPane.PLAIN_MESSAGE);

		});
		
		columnsTextfield = new JTextField(Integer.toString(Controller.getGridColumns()), 3);
		columnsTextfield.setMinimumSize(columnsTextfield.getPreferredSize());
		columnsTextfield.setMaximumSize(columnsTextfield.getPreferredSize());
		columnsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					Controller.setGridColumns(Integer.parseInt(columnsTextfield.getText().trim()));
				} catch(Exception e) {
					columnsTextfield.setText(Integer.toString(Controller.getGridColumns()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				columnsTextfield.selectAll();
			}
		});
		
		rowsTextfield = new JTextField(Integer.toString(Controller.getGridRows()), 3);
		rowsTextfield.setMinimumSize(rowsTextfield.getPreferredSize());
		rowsTextfield.setMaximumSize(rowsTextfield.getPreferredSize());
		rowsTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					Controller.setGridRows(Integer.parseInt(rowsTextfield.getText().trim()));
				} catch(Exception e) {
					rowsTextfield.setText(Integer.toString(Controller.getGridRows()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				rowsTextfield.selectAll();
			}
		});
		
		Controller.addGridChangedListener(new GridChangedListener() {
			@Override public void gridChanged(int columns, int rows) {
				columnsTextfield.setText(Integer.toString(columns));
				rowsTextfield.setText(Integer.toString(rows));
			}
		});
		
		sampleRateTextfield = new JTextField(Integer.toString(Controller.getSampleRate()), 4);
		sampleRateTextfield.setMinimumSize(sampleRateTextfield.getPreferredSize());
		sampleRateTextfield.setMaximumSize(sampleRateTextfield.getPreferredSize());
		sampleRateTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					Controller.setSampleRate(Integer.parseInt(sampleRateTextfield.getText().trim()));
				} catch(Exception e) {
					sampleRateTextfield.setText(Integer.toString(Controller.getSampleRate()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				sampleRateTextfield.selectAll();
			}
		});
		
		packetTypeCombobox = new JComboBox<Packet>();
		for(Packet packet : Controller.getPacketTypes())
			packetTypeCombobox.addItem(packet);
		packetTypeCombobox.setMaximumSize(packetTypeCombobox.getPreferredSize());
		packetTypeCombobox.addActionListener(event -> {
		
			if(packetTypeCombobox.getSelectedItem() != Model.packet)
				Controller.removeAllDatasets();
			
		});
		
		portNamesCombobox = new JComboBox<String>();
		for(String portName : Controller.getSerialPortNames())
			portNamesCombobox.addItem(portName);
		portNamesCombobox.setMaximumSize(portNamesCombobox.getPreferredSize());
		
		baudRatesCombobox = new JComboBox<Integer>();
		for(int baudRate : Controller.getBaudRates())
			baudRatesCombobox.addItem(baudRate);
		baudRatesCombobox.setMaximumRowCount(baudRatesCombobox.getItemCount());
		baudRatesCombobox.setMaximumSize(baudRatesCombobox.getPreferredSize());
		
		Controller.addSerialPortListener(new SerialPortListener() {
			
			@Override public void connectionOpened(int sampleRate, Packet packet, String portName, int baudRate) {
				
				// enable or disable UI elements
				openLayoutButton.setEnabled(true);
				saveLayoutButton.setEnabled(true);
				sampleRateTextfield.setEnabled(false);
				packetTypeCombobox.setEnabled(false);
				portNamesCombobox.setEnabled(false);
				baudRatesCombobox.setEnabled(false);
				connectButton.setEnabled(true);
				
				// update UI state
				sampleRateTextfield.setText(Integer.toString(sampleRate));
				packetTypeCombobox.setSelectedItem(packet);
				for(int i = 0; i < portNamesCombobox.getItemCount(); i++)
					if(portNamesCombobox.getItemAt(i).equals(portName)) {
						portNamesCombobox.setSelectedIndex(i);
						break;
					}
				for(int i = 0; i < baudRatesCombobox.getItemCount(); i++)
					if(baudRatesCombobox.getItemAt(i).equals(baudRate)) {
						baudRatesCombobox.setSelectedIndex(i);
						break;
					}
				connectButton.setText("Disconnect");
				
			}
			
			@Override public void connectionClosed() {
				
				// enable or disable UI elements
				openLayoutButton.setEnabled(true);
				saveLayoutButton.setEnabled(true);
				sampleRateTextfield.setEnabled(true);
				packetTypeCombobox.setEnabled(true);
				portNamesCombobox.setEnabled(true);
				baudRatesCombobox.setEnabled(true);
				connectButton.setEnabled(true);

				// update UI state
				connectButton.setText("Connect");
				
				// ensure a packet type is selected
				if(packetTypeCombobox.getSelectedIndex() < 0)
					packetTypeCombobox.setSelectedIndex(0);
				
			}

			@Override public void connectionLost() {

				connectionClosed();

				// notify the user because they did not initiate the disconnection
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
				JOptionPane.showMessageDialog(parentWindow, "Warning: Serial connection lost.", "Warning", JOptionPane.WARNING_MESSAGE);
				
			}
		});
		
		connectButton = new JButton("Connect");
		connectButton.addActionListener(event -> {
			
			if(connectButton.getText().equals("Connect")) {
				
				if(portNamesCombobox.getSelectedItem() == null) {
					JOptionPane.showMessageDialog(null, "Error: No port name specified.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				int sampleRate = Integer.parseInt(sampleRateTextfield.getText());
				Packet packet = (Packet) packetTypeCombobox.getSelectedItem();
				String portName = portNamesCombobox.getSelectedItem().toString();
				int baudRate = (int) baudRatesCombobox.getSelectedItem();
				
				openLayoutButton.setEnabled(false);
				saveLayoutButton.setEnabled(false);
				sampleRateTextfield.setEnabled(false);
				packetTypeCombobox.setEnabled(false);
				portNamesCombobox.setEnabled(false);
				baudRatesCombobox.setEnabled(false);
				connectButton.setEnabled(false);
				
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
				Controller.connectToSerialPort(sampleRate, packet, portName, baudRate, parentWindow);
				
			} else if(connectButton.getText().equals("Disconnect")) {
				
				openLayoutButton.setEnabled(false);
				saveLayoutButton.setEnabled(false);
				sampleRateTextfield.setEnabled(false);
				packetTypeCombobox.setEnabled(false);
				portNamesCombobox.setEnabled(false);
				baudRatesCombobox.setEnabled(false);
				connectButton.setEnabled(false);
				
				Controller.disconnectFromSerialPort();
				
			}

		});
		
		// show the components
		add(openLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(saveLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(exportCsvLogButton);
		add(Box.createHorizontalStrut(5));
		add(resetButton);
		add(Box.createHorizontalStrut(5));
		add(helpButton);
		add(Box.createHorizontalStrut(5));
		add(Box.createHorizontalGlue());
		add(new JLabel("Grid size:"));
		add(Box.createHorizontalStrut(5));
		add(columnsTextfield);
		add(Box.createHorizontalStrut(5));
		add(new JLabel("x"));
		add(Box.createHorizontalStrut(5));
		add(rowsTextfield);
		add(Box.createHorizontalGlue());
		add(Box.createHorizontalStrut(5));
		add(new JLabel("Sample Rate (Hz)"));
		add(Box.createHorizontalStrut(5));
		add(sampleRateTextfield);
		add(Box.createHorizontalStrut(5));
		add(packetTypeCombobox);
		add(Box.createHorizontalStrut(5));
		add(portNamesCombobox);
		add(Box.createHorizontalStrut(5));
		add(baudRatesCombobox);
		add(Box.createHorizontalStrut(5));
		add(connectButton);
		
		// set minimum panel width to 120% of the "preferred" width
		Dimension size = getPreferredSize();
		size.width = (int) (size.width * 1.2);
		setMinimumSize(size);
		setPreferredSize(size);
		
	}
	
	/**
	 * @return    The x value of openLayoutButton's center.
	 */
	public int getOpenLayoutButtonLocation() {
		
		return openLayoutButton.getLocation().x + (openLayoutButton.getWidth() / 2);
		
	}
	
	/**
	 * @return    The x value of connectButton's center.
	 */
	public int getConnectButtonLocation() {
		
		return connectButton.getLocation().x + (connectButton.getWidth() / 2);
		
	}

}
