import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class CommunicationView extends JPanel {
	
	static CommunicationView instance = new CommunicationView();
	
	private JToggleButton settingsButton;
	private JButton importButton;
	private JButton exportButton;
	private JButton helpButton;
	private JTextField sampleRateTextfield;
	private JComboBox<String> packetTypeCombobox;
	private JComboBox<String> portCombobox;
	private Component baudRatePadding;
	private JComboBox<String> baudRateCombobox;
	private JComboBox<String> portNumberCombobox;
	private JButton connectButton;

	/**
	 * Private constructor to enforce singleton usage.
	 */
	private CommunicationView () {
		
		super();
		setLayout(new MigLayout("hidemode 2, gap 0, insets 0", "[][][][][][][]push[][][][][][][][][][][][][]"));
		setBorder(new EmptyBorder(Theme.padding, Theme.padding, Theme.padding, Theme.padding));
		
		// settings
		settingsButton = new JToggleButton("Settings");
		settingsButton.setSelected(SettingsView.instance.isVisible());
		settingsButton.addActionListener(event -> showSettings(settingsButton.isSelected()));
		
		// import
		importButton = new JButton("Import");
		importButton.addActionListener(event -> {
			
			JFileChooser inputFiles = new JFileChooser(System.getProperty("user.home") + "/Desktop/");
			inputFiles.setMultiSelectionEnabled(true);
			inputFiles.setFileFilter(new FileNameExtensionFilter("Files Exported from Telemetry Viewer", "txt", "csv", "mjpg", "bin"));
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(CommunicationView.instance);
			if(inputFiles.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				File[] files = inputFiles.getSelectedFiles();
				String[] filepaths = new String[files.length];
				for(int i = 0; i < files.length; i++)
					filepaths[i] = files[i].getAbsolutePath();
				CommunicationController.importFiles(filepaths);
			}
			
		});
		
		// export
		exportButton = new JButton("Export");
		exportButton.setEnabled(false);
		exportButton.addActionListener(event -> {
			
			JDialog exportWindow = new JDialog(Main.window, "Select Files to Export");
			exportWindow.setLayout(new MigLayout("wrap 1, insets " + Theme.padding));
			
			List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();
			checkboxes.add(new JCheckBox("Settings file (the data structure, chart settings, and GUI settings)", true));
			if(DatasetsController.getSampleCount() > 0) {
				checkboxes.add(new JCheckBox("CSV file (the acquired samples and corresponding timestamps)", true));
				for(Camera camera : DatasetsController.getExistingCameras())
					checkboxes.add(new JCheckBox("Camera files for \"" + camera.name + "\"", true));
			}
			
			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(event2 -> exportWindow.dispose());
			
			JButton confirmButton = new JButton("Export");
			confirmButton.addActionListener(event2 -> {
				
				// cancel if every checkbox is unchecked
				boolean nothingSelected = true;
				for(JCheckBox cb : checkboxes)
					if(cb.isSelected())
						nothingSelected = false;
				if(nothingSelected) {
					exportWindow.dispose();
					return;
				}
				
				JFileChooser saveFile = new JFileChooser(System.getProperty("user.home") + "/Desktop/");
				saveFile.setDialogTitle("Export as...");
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(CommunicationView.instance);
				if(saveFile.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
					String absolutePath = saveFile.getSelectedFile().getAbsolutePath();
					// remove the file extension if the user specified one
					if(saveFile.getSelectedFile().getName().indexOf(".") != -1)
						absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf("."));
					boolean exportSettingsFile = false;
					boolean exportCsvFile = false;
					List<String> exportCameraNames = new ArrayList<String>();
					for(JCheckBox checkbox : checkboxes) {
						if(checkbox.isSelected() && checkbox.getText().startsWith("Settings file"))
							exportSettingsFile = true;
						else if(checkbox.isSelected() && checkbox.getText().startsWith("CSV file"))
							exportCsvFile = true;
						else if(checkbox.isSelected()) {
							String cameraName = checkbox.getText().substring(18, checkbox.getText().length() - 1);
							exportCameraNames.add(cameraName);
						}
					}
					CommunicationController.exportFiles(absolutePath, exportSettingsFile, exportCsvFile, exportCameraNames);
					exportWindow.dispose();
				}
				
			});
			
			JPanel buttonsPanel = new JPanel();
			buttonsPanel.setLayout(new MigLayout("insets " + (Theme.padding * 2) + " 0 0 0", "[33%!][grow][33%!]")); // space the buttons, and 3 equal columns
			buttonsPanel.add(cancelButton,  "growx, cell 0 0");
			buttonsPanel.add(confirmButton, "growx, cell 2 0");
			
			for(JCheckBox checkbox : checkboxes)
				exportWindow.add(checkbox);
			exportWindow.add(buttonsPanel, "grow x");
			exportWindow.pack();
			exportWindow.setModal(true);
			exportWindow.setLocationRelativeTo(Main.window);
			exportWindow.setVisible(true);
			
		});
		
		// help
		helpButton = new JButton("Help");
		helpButton.addActionListener(event -> {
			
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(CommunicationView.instance);
			String helpText = "<html><b>Telemetry Viewer v0.7 (2020-07-17)</b><br>" +
			                  "A fast and easy tool for visualizing data received over a UART/TCP/UDP connection.<br><br>" +
			                  "Step 1: Use the controls at the lower-right corner of the window to connect to a serial port or to start a TCP/UDP server.<br>" +
			                  "Step 2: A \"Data Structure Definition\" screen will appear. Use it to specify how your data is laid out, then click \"Done.\"<br>" +
			                  "Step 3: Click-and-drag in the tiles region to place a chart.<br>" +
			                  "Step 4: A chart configuration panel will appear. Use it to specify the type of chart and its settings, then click \"Done.\"<br>" +
			                  "Repeat steps 3 and 4 to create more charts if desired.<br><br>" +
			                  "Use your scroll wheel to rewind or fast forward.<br>" +
			                  "Use your scroll wheel while holding down Ctrl to zoom in or out.<br>" +
			                  "Use your scroll wheel while holding down Shift to adjust display scaling.<br><br>" +
			                  "Click the x icon at the top-right corner of any chart to remove it.<br>" +
			                  "Click the box icon at the top-right corner of any chart to maximize it.<br>" +
			                  "Click the gear icon at the top-right corner of any chart to change its settings.<br><br>" +
			                  "Click the \"Settings\" button to adjust options related to the GUI.<br>" +
			                  "Click the \"Import\" button to open previously saved files.<br>" +
			                  "Click the \"Export\" button to save your settings and/or data to files.<br>" +
			                  "Files can also be imported via drag-n-drop.<br><br>" +
			                  "Author: Farrell Farahbod<br>" +
			                  "This software is free and open source.</html>";
			JLabel helpLabel = new JLabel(helpText);
			JButton websiteButton = new JButton("<html><a href=\"http://www.farrellf.com/TelemetryViewer/\">http://www.farrellf.com/TelemetryViewer/</a></html>");
			websiteButton.addActionListener(click -> { try { Desktop.getDesktop().browse(new URI("http://www.farrellf.com/TelemetryViewer/")); } catch(Exception ex) {} });
			JButton paypalButton = new JButton("<html><a href=\"https://paypal.me/farrellfarahbod/\">https://paypal.me/farrellfarahbod/</a></html>");
			paypalButton.addActionListener(click -> { try { Desktop.getDesktop().browse(new URI("https://paypal.me/farrellfarahbod/")); } catch(Exception ex) {} });
			
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(helpLabel);
			panel.add(websiteButton);
			panel.add(new JLabel("<html><br>If you find this software useful and want to \"buy me a coffee\" that would be awesome!</html>"));
			panel.add(paypalButton);
			panel.add(new JLabel(" "));
			JOptionPane.showMessageDialog(parentWindow, panel, "Help", JOptionPane.PLAIN_MESSAGE);

		});

		// sample rate
		sampleRateTextfield = new JTextField(Integer.toString(CommunicationController.getSampleRate()), 7);
		sampleRateTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				try {
					CommunicationController.setSampleRate(Integer.parseInt(sampleRateTextfield.getText().trim()));
				} catch(Exception e) {
					sampleRateTextfield.setText(Integer.toString(CommunicationController.getSampleRate()));
				}
			}
			
			@Override public void focusGained(FocusEvent fe) {
				sampleRateTextfield.selectAll();
			}
		});
		CommunicationController.setSampleRate(Integer.parseInt(sampleRateTextfield.getText()));
		
		// packet type
		packetTypeCombobox = new JComboBox<String>(CommunicationController.getPacketTypes());
		packetTypeCombobox.addActionListener(event -> CommunicationController.setPacketType(packetTypeCombobox.getSelectedItem().toString()));
		CommunicationController.setPacketType(packetTypeCombobox.getSelectedItem().toString());
		
		// port
		portCombobox = new JComboBox<String>(CommunicationController.getPorts());
		portCombobox.setMaximumRowCount(portCombobox.getItemCount() + 2);
		portCombobox.addActionListener(event -> CommunicationController.setPort(portCombobox.getSelectedItem().toString()));
		CommunicationController.setPort(portCombobox.getSelectedItem().toString());
		
		// UART baud rate
		baudRatePadding = Box.createHorizontalStrut(Theme.padding);
		baudRateCombobox = new JComboBox<String>(CommunicationController.getBaudRateDefaults());
		baudRateCombobox.setMaximumRowCount(baudRateCombobox.getItemCount());
		baudRateCombobox.setPreferredSize(baudRateCombobox.getPreferredSize());
		baudRateCombobox.setEditable(true);
		baudRateCombobox.addActionListener(event -> {
			try {
				CommunicationController.setBaudRate(Integer.parseInt(baudRateCombobox.getSelectedItem().toString().trim()));
			} catch(Exception e) {
				String baudRate = Integer.toString(CommunicationController.getBaudRate());
				baudRateCombobox.setSelectedItem(baudRate);
			}
		});
		CommunicationController.setBaudRate(Integer.parseInt(baudRateCombobox.getSelectedItem().toString()));
		
		// TCP/UDP port number
		portNumberCombobox = new JComboBox<String>(CommunicationController.getPortNumberDefaults());
		portNumberCombobox.setMaximumRowCount(portNumberCombobox.getItemCount());
		portNumberCombobox.setPreferredSize(baudRateCombobox.getPreferredSize()); // force same size as baudRateCombobox
		portNumberCombobox.setMaximumSize(baudRateCombobox.getPreferredSize()); // force same size as baudRateCombobox
		portNumberCombobox.setEditable(true);
		portNumberCombobox.addActionListener(event -> {
			try {
				String portNumberString = portNumberCombobox.getSelectedItem().toString().trim();
				if(portNumberString.startsWith(":"))
					portNumberString = portNumberString.substring(1); // skip past the leading ":"
				CommunicationController.setPortNumber(Integer.parseInt(portNumberString));
			} catch(Exception e) {
				String portNumberString = ":" + CommunicationController.getPortNumber();
				portNumberCombobox.setSelectedItem(portNumberString);
			}
		});
		CommunicationController.setPortNumber(Integer.parseInt(portNumberCombobox.getSelectedItem().toString().substring(1)));
		
		// connect/disconnect
		connectButton = new JButton("Connect");
		connectButton.addActionListener(event -> {
			packetTypeCombobox.setEnabled(false);
			portCombobox.setEnabled(false);
			baudRateCombobox.setEnabled(false);
			portNumberCombobox.setEnabled(false);
			connectButton.setEnabled(false);
			
			if(connectButton.getText().equals("Connect"))
				CommunicationController.connect(false);
			else if(connectButton.getText().equals("Disconnect"))
				CommunicationController.disconnect(null);
			else if(connectButton.getText().equals("Finish") || connectButton.getText().equals("Abort")) {
				CommunicationController.finishImportingFile();
				connectButton.setText("Abort");
				connectButton.setEnabled(true);
			}
		});
		
		// show the components
		add(settingsButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(importButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(exportButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(helpButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(new JLabel("Sample Rate (Hz)"));
		add(Box.createHorizontalStrut(Theme.padding));
		add(sampleRateTextfield);
		add(Box.createHorizontalStrut(Theme.padding));
		add(packetTypeCombobox);
		add(Box.createHorizontalStrut(Theme.padding));
		add(portCombobox);
		add(baudRatePadding);
		add(baudRateCombobox);
		add(portNumberCombobox);
		add(Box.createHorizontalStrut(Theme.padding));
		add(connectButton);
		
		// ensure the correct widgets are shown/hidden
		setPort(portCombobox.getSelectedItem().toString());

	}
	
	/**
	 * @param isShown    True to show the settings panel.
	 */
	public void showSettings(boolean isShown) {

		SettingsView.instance.setVisible(isShown);
		settingsButton.setSelected(isShown);
			
	}
	
	/**
	 * Enables or disables the import button.
	 * This method is thread-safe.
	 * 
	 * @param isAllowed    True to allow importing.
	 */
	public void allowImporting(boolean isAllowed) {
		
		SwingUtilities.invokeLater(() -> importButton.setEnabled(isAllowed));
		
	}
	
	/**
	 * Enables or disables the export button.
	 * This method is thread-safe.
	 * 
	 * @param isAllowed    True to allow importing.
	 */
	public void allowExporting(boolean isAllowed) {
		
		SwingUtilities.invokeLater(() -> exportButton.setEnabled(isAllowed));
		
	}
	
	/**
	 * Enables or disables the connect button. Use this to prevent editing of the data structure while exporting is in progress.
	 * This method is thread-safe.
	 * 
	 * @param isAllowed    True to enable the connect button.
	 */
	public void allowConnecting(boolean isAllowed) {
		
		SwingUtilities.invokeLater(() -> connectButton.setEnabled(isAllowed));
		
	}
	
	/**
	 * Updates the GUI to indicate the connection status.
	 * This method is thread-safe.
	 * 
	 * @param isConnected    True if connected.
	 */
	public void setConnected(boolean isConnected) {
		
		SwingUtilities.invokeLater(() -> {
			if(isConnected) {
				packetTypeCombobox.setEnabled(false);
				portCombobox.setEnabled(false);
				baudRateCombobox.setEnabled(false);
				portNumberCombobox.setEnabled(false);
				connectButton.setEnabled(true);
				if(CommunicationController.getPort().equals(CommunicationController.PORT_FILE))
					connectButton.setText("Finish");
				else
					connectButton.setText("Disconnect");
			} else {
				packetTypeCombobox.setEnabled(true);
				portCombobox.setEnabled(true);
				baudRateCombobox.setEnabled(true);
				portNumberCombobox.setEnabled(true);
				connectButton.setEnabled(true);
				connectButton.setText("Connect");
			}
		});

	}
	
	/**
	 * Updates the GUI to indicate which port is selected.
	 * This method is thread-safe.
	 * 
	 * @param port    The port.
	 */
	public void setPort(String newPort) {
		
		SwingUtilities.invokeLater(() -> {
			
			// prevent this update from triggering an event
			ActionListener[] listeners = portCombobox.getActionListeners();
			for(ActionListener listener : listeners)
				portCombobox.removeActionListener(listener);

			boolean portInCombobox = false;
			for(int i = 0; i < portCombobox.getItemCount(); i++)
				if(portCombobox.getItemAt(i).equals(newPort)) {
					portCombobox.setSelectedIndex(i);
					portInCombobox = true;
					break;
				}
			if(!portInCombobox) {
				portCombobox.addItem(newPort);
				portCombobox.setSelectedItem(newPort);
			}
			if(newPort.startsWith(CommunicationController.PORT_UART)) {
				baudRatePadding.setVisible(true);
				baudRateCombobox.setVisible(true);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setEditable(true);
				packetTypeCombobox.setVisible(true);
			} else if(newPort.equals(CommunicationController.PORT_TEST)) {
				baudRatePadding.setVisible(false);
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setText("10000");
				sampleRateTextfield.setEditable(false);
				packetTypeCombobox.setVisible(true);
			} else if(newPort.equals(CommunicationController.PORT_FILE)) {
				baudRatePadding.setVisible(false);
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(false);
				sampleRateTextfield.setEditable(false);
				packetTypeCombobox.setVisible(false);
			} else {
				baudRatePadding.setVisible(true);
				baudRateCombobox.setVisible(false);
				portNumberCombobox.setVisible(true);
				sampleRateTextfield.setEditable(true);
				packetTypeCombobox.setVisible(true);
			}
			
			// do not show "File" if we are not currently importing a file
			if(!newPort.equals(CommunicationController.PORT_FILE)) {
				int index = -1;
				for(int i = 0; i < portCombobox.getItemCount(); i++)
					if(portCombobox.getItemAt(i).equals(CommunicationController.PORT_FILE))
						index = i;
				if(index >= 0)
					portCombobox.removeItemAt(index);
			}
			
			// restore event handlers
			for(ActionListener listener : listeners)
				portCombobox.addActionListener(listener);
		});
		
	}
	
	/**
	 * Updates the GUI to indicate the selected packet type.
	 * This method is thread-safe.
	 * 
	 * @param newType    The packet type.
	 */
	public void setPacketType(String newType) {
		
		SwingUtilities.invokeLater(() -> {
			
			// prevent this update from triggering an event
			ActionListener[] listeners = packetTypeCombobox.getActionListeners();
			for(ActionListener listener : listeners)
				packetTypeCombobox.removeActionListener(listener);
			
			packetTypeCombobox.setSelectedItem(newType);
			
			// restore event handlers
			for(ActionListener listener : listeners)
				packetTypeCombobox.addActionListener(listener);
			
		});
		
	}
	
	/**
	 * Updates the GUI to indicate the sample rate.
	 * This method is thread-safe.
	 * 
	 * @param newRate    The sample rate.
	 */
	public void setSampleRate(int newRate) {
		
		SwingUtilities.invokeLater(() -> sampleRateTextfield.setText(Integer.toString(newRate)));
		
	}
	
	/**
	 * Updates the GUI to indicate the baud rate.
	 * This method is thread-safe.
	 * 
	 * @param newRate    The baud rate.
	 */
	public void setBaudRate(int newRate) {
		
		SwingUtilities.invokeLater(() -> {
			
			// prevent this update from triggering an event
			ActionListener[] listeners = baudRateCombobox.getActionListeners();
			for(ActionListener listener : listeners)
				baudRateCombobox.removeActionListener(listener);
			
			baudRateCombobox.setSelectedItem(Integer.toString(newRate));
			
			// restore event handlers
			for(ActionListener listener : listeners)
				baudRateCombobox.addActionListener(listener);
			
		});
		
	}
	
	/**
	 * Updates the GUI to indicate the TCP/UDP port number.
	 * This method is thread-safe.
	 * 
	 * @param newNumber    The port number.
	 */
	public void setPortNumber(int newNumber) {
		
		SwingUtilities.invokeLater(() -> {
			
			// prevent this update from triggering an event
			ActionListener[] listeners = portNumberCombobox.getActionListeners();
			for(ActionListener listener : listeners)
				portNumberCombobox.removeActionListener(listener);
			
			portNumberCombobox.setSelectedItem(":" + newNumber);
			
			// restore event handlers
			for(ActionListener listener : listeners)
				portNumberCombobox.addActionListener(listener);
			
		});
		
	}
	
}
