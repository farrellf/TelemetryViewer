import java.awt.Desktop;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class CommunicationView extends JPanel {
	
	static CommunicationView instance = new CommunicationView();
	
	private JToggleButton settingsButton;
	private JButton importButton;
	private JButton exportButton;
	private JButton helpButton;
	private JButton connectionButton;

	/**
	 * Private constructor to enforce singleton usage.
	 */
	private CommunicationView () {
		
		super();
		setLayout(new MigLayout("wrap 6, gap " + Theme.padding  + ", insets " + Theme.padding, "[][][][]push[]push[]"));
		
		// settings
		settingsButton = new JToggleButton("Settings");
		settingsButton.setSelected(SettingsView.instance.isVisible());
		settingsButton.addActionListener(event -> showSettings(settingsButton.isSelected()));
		
		// import
		importButton = new JButton("Import");
		importButton.addActionListener(event -> {
			
			JFileChooser inputFiles = new JFileChooser(System.getProperty("user.home") + "/Desktop/");
			inputFiles.setMultiSelectionEnabled(true);
			inputFiles.setFileFilter(new FileNameExtensionFilter("Files Exported from Telemetry Viewer", "txt", "csv", "mkv"));
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(this);
			if(inputFiles.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				File[] files = inputFiles.getSelectedFiles();
				String[] filepaths = new String[files.length];
				for(int i = 0; i < files.length; i++)
					filepaths[i] = files[i].getAbsolutePath();
				ConnectionsController.importFiles(filepaths);
			}
			
		});
		
		// export
		exportButton = new JButton("Export");
		exportButton.setEnabled(false);
		exportButton.addActionListener(event -> {
			
			JDialog exportWindow = new JDialog(Main.window, "Select Files to Export");
			exportWindow.setLayout(new MigLayout("wrap 1, insets " + Theme.padding));
			
			JCheckBox settingsFileCheckbox = new JCheckBox("Settings file (the data structures, chart settings, and GUI settings)", true);
			List<Map.Entry<JCheckBox, ConnectionTelemetry>> csvFileCheckboxes = new ArrayList<Map.Entry<JCheckBox, ConnectionTelemetry>>();
			List<Map.Entry<JCheckBox, ConnectionCamera>> cameraFileCheckboxes = new ArrayList<Map.Entry<JCheckBox, ConnectionCamera>>();
			
			for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections)
				if(connection.getSampleCount() > 0)
					csvFileCheckboxes.add(new AbstractMap.SimpleEntry<JCheckBox, ConnectionTelemetry>(new JCheckBox("CSV file for \"" + connection.name + "\" (the acquired samples and corresponding timestamps)", true), connection));
			for(ConnectionCamera camera : ConnectionsController.cameraConnections)
				if(camera.getSampleCount() > 0)
					cameraFileCheckboxes.add(new AbstractMap.SimpleEntry<JCheckBox, ConnectionCamera>(new JCheckBox("MKV file for \"" + camera.name + "\" (the acquired images and corresponding timestamps)", true), camera));
			
			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(event2 -> exportWindow.dispose());
			
			JButton confirmButton = new JButton("Export");
			confirmButton.addActionListener(event2 -> {
				
				// cancel if every checkbox is unchecked
				boolean nothingSelected = true;
				if(settingsFileCheckbox.isSelected())
					nothingSelected = false;
				for(Entry<JCheckBox, ConnectionTelemetry> entry : csvFileCheckboxes)
					if(entry.getKey().isSelected())
						nothingSelected = false;
				for(Entry<JCheckBox, ConnectionCamera> entry : cameraFileCheckboxes)
					if(entry.getKey().isSelected())
						nothingSelected = false;
				if(nothingSelected) {
					exportWindow.dispose();
					return;
				}
				
				JFileChooser saveFile = new JFileChooser(System.getProperty("user.home") + "/Desktop/");
				saveFile.setDialogTitle("Export as...");
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(this);
				if(saveFile.showSaveDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
					String absolutePath = saveFile.getSelectedFile().getAbsolutePath();
					// remove the file extension if the user specified one
					if(saveFile.getSelectedFile().getName().indexOf(".") != -1)
						absolutePath = absolutePath.substring(0, absolutePath.lastIndexOf("."));
					boolean exportSettingsFile = settingsFileCheckbox.isSelected();
					List<ConnectionTelemetry> connectionsList = new ArrayList<ConnectionTelemetry>();
					List<ConnectionCamera>    camerasList     = new ArrayList<ConnectionCamera>();
					for(Entry<JCheckBox, ConnectionTelemetry> entry : csvFileCheckboxes)
						if(entry.getKey().isSelected())
							connectionsList.add(entry.getValue());
					for(Entry<JCheckBox, ConnectionCamera> entry : cameraFileCheckboxes)
						if(entry.getKey().isSelected())
							camerasList.add(entry.getValue());
					ConnectionsController.exportFiles(absolutePath, exportSettingsFile, connectionsList, camerasList);
					exportWindow.dispose();
				}
				
			});
			
			JPanel buttonsPanel = new JPanel();
			buttonsPanel.setLayout(new MigLayout("insets " + (Theme.padding * 2) + " 0 0 0", "[33%!][grow][33%!]")); // space the buttons, and 3 equal columns
			buttonsPanel.add(cancelButton,  "growx, cell 0 0");
			buttonsPanel.add(confirmButton, "growx, cell 2 0");
			
			exportWindow.add(settingsFileCheckbox);
			for(Entry<JCheckBox, ConnectionTelemetry> entry : csvFileCheckboxes)
				exportWindow.add(entry.getKey());
			for(Entry<JCheckBox, ConnectionCamera> entry : cameraFileCheckboxes)
				exportWindow.add(entry.getKey());
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
			String helpText = "<html><b>" + Main.versionString + "</b><br>" +
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
		
		connectionButton = new JButton("New Connection");
		connectionButton.addActionListener(event -> ConnectionsController.addConnection(new ConnectionTelemetry()));
		
		// show the components
		redraw();
		
	}
	
	public void redraw() {
		
		SwingUtilities.invokeLater(() -> {
			
			boolean connectionsDefined = false;
			for(ConnectionTelemetry connection : ConnectionsController.telemetryConnections)
				if(connection.dataStructureDefined)
					connectionsDefined = true;
			for(ConnectionCamera connection : ConnectionsController.cameraConnections)
				if(connection.connected || connection.getSampleCount() > 0)
					connectionsDefined = true;
			
			importButton.setEnabled(!ConnectionsController.importing && !ConnectionsController.exporting);
			exportButton.setEnabled(!ConnectionsController.importing && !ConnectionsController.exporting && connectionsDefined);
			
			removeAll();
			add(settingsButton);
			add(importButton);
			add(exportButton);
			add(helpButton);
			add(connectionButton);
			for(int i = 0; i < ConnectionsController.allConnections.size(); i++)
				add(ConnectionsController.allConnections.get(i).getGui(), "align right, cell 5 " + i);
			
			if(!ConnectionsController.importing) {
				connectionButton.setVisible(true);
				connectionButton.setText("New Connection");
				for(ActionListener listener : connectionButton.getActionListeners())
					connectionButton.removeActionListener(listener);
				connectionButton.addActionListener(event -> ConnectionsController.addConnection(new ConnectionTelemetry()));
			} else if(ConnectionsController.importing && ConnectionsController.allConnections.size() < 2) {
				connectionButton.setVisible(false);
			} else if(ConnectionsController.importing && ConnectionsController.allConnections.size() > 1) {
				connectionButton.setVisible(true);
				connectionButton.setText(ConnectionsController.realtimeImporting ? "Finish Importing" : "Abort Importing");
				for(ActionListener listener : connectionButton.getActionListeners())
					connectionButton.removeActionListener(listener);
				connectionButton.addActionListener(event -> {
					for(Connection connection : ConnectionsController.allConnections)
						connection.finishImportingFile();
					CommunicationView.instance.redraw();
				});
			}
			
			revalidate();
			repaint();
		
			// also redraw the SettingsView because it contains the transmit GUIs
			SettingsView.instance.setVisible(SettingsView.instance.isVisible());
			
		});
		
	}
	
	/**
	 * @param isShown    True to show the settings panel.
	 */
	public void showSettings(boolean isShown) {

		SettingsView.instance.setVisible(isShown);
		settingsButton.setSelected(isShown);
			
	}
	
}
