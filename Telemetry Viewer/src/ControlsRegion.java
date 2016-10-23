import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.concurrent.atomic.AtomicBoolean;

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
	JButton resetButton;
	
	JTextField columnsTextfield;
	JTextField rowsTextfield;
	
	JTextField sampleRateTextfield;
	JComboBox<String> packetTypeCombobox;
	JComboBox<String> portNamesCombobox;
	JComboBox<Integer> baudRatesCombobox;
	JButton connectButton;
	
	AtomicBoolean waitingForSerialConnection;
	AtomicBoolean waitingForSerialDisconnection;
	
	/**
	 * Creates the panel of controls and registers their event handlers.
	 */
	public ControlsRegion() {
		
		super();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(new EmptyBorder(5, 5, 5, 5));
		
		waitingForSerialConnection = new AtomicBoolean(false);
		waitingForSerialDisconnection = new AtomicBoolean(false);
		
		openLayoutButton = new JButton("Open Layout");
		openLayoutButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				JFileChooser inputFile = new JFileChooser();
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
				if(inputFile.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
					String filePath = inputFile.getSelectedFile().getAbsolutePath();
					Controller.openLayout(filePath);
				}
			}
		});
		
		saveLayoutButton = new JButton("Save Layout");
		saveLayoutButton.setEnabled(false);
		saveLayoutButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent e) {
				JFileChooser saveFile = new JFileChooser();
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
				if(saveFile.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
					String filePath = saveFile.getSelectedFile().getAbsolutePath();
					if(!filePath.endsWith(".txt"))
						filePath += ".txt";
					Controller.saveLayout(filePath);
				}
			}
		});
		
		resetButton = new JButton("Reset");
		resetButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent arg0) {
				Controller.removeAllPositionedCharts();
			}
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
		
		packetTypeCombobox = new JComboBox<String>();
		for(String packetType : Controller.getPacketTypes())
			packetTypeCombobox.addItem(packetType);
		packetTypeCombobox.setMaximumSize(packetTypeCombobox.getPreferredSize());
		
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
			@Override public void connectionOpened(int sampleRate, String packetType, String portName, int baudRate) {
				
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
				for(int i = 0; i < packetTypeCombobox.getItemCount(); i++)
					if(packetTypeCombobox.getItemAt(i).equals(packetType)) {
						packetTypeCombobox.setSelectedIndex(i);
						break;
					}
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

				// show the DataStructureWindow if the user initiated the connection
				if(waitingForSerialConnection.compareAndSet(true, false)) {
					JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
					new DataStructureWindow(parentWindow, packetType, portName.equals("Test"));
				}
				
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
				
				if(waitingForSerialDisconnection.compareAndSet(true, false)){
					// do nothing, this was expected
				} else {
					// notify the user because they did not initiate the disconnection
					JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
					JOptionPane.showMessageDialog(parentWindow, "Serial connection lost.", "Serial Connection Lost", JOptionPane.WARNING_MESSAGE);
				}
				
			}
		});
		
		connectButton = new JButton("Connect");
		connectButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent arg0) {
				if(connectButton.getText().equals("Connect")) {
					
					if(portNamesCombobox.getSelectedItem() == null) {
						JOptionPane.showMessageDialog(null, "Error: No port name specified.", "No Port Name Specified", JOptionPane.ERROR_MESSAGE);
						return;
					}
					
					int sampleRate = Integer.parseInt(sampleRateTextfield.getText());
					String packetType = packetTypeCombobox.getSelectedItem().toString();
					String portName = portNamesCombobox.getSelectedItem().toString();
					int baudRate = (int) baudRatesCombobox.getSelectedItem();
					
					openLayoutButton.setEnabled(false);
					saveLayoutButton.setEnabled(false);
					sampleRateTextfield.setEnabled(false);
					packetTypeCombobox.setEnabled(false);
					portNamesCombobox.setEnabled(false);
					baudRatesCombobox.setEnabled(false);
					connectButton.setEnabled(false);
					
					waitingForSerialConnection.set(true);
					Controller.connectToSerialPort(sampleRate, packetType, portName, baudRate);
					
				} else if(connectButton.getText().equals("Disconnect")) {
					
					openLayoutButton.setEnabled(false);
					saveLayoutButton.setEnabled(false);
					sampleRateTextfield.setEnabled(false);
					packetTypeCombobox.setEnabled(false);
					portNamesCombobox.setEnabled(false);
					baudRatesCombobox.setEnabled(false);
					connectButton.setEnabled(false);
					
					waitingForSerialDisconnection.set(true);
					Controller.disconnectFromSerialPort();
					
				}
					
			}
		});
		
		// show the components
		add(openLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(saveLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(resetButton);
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

}
