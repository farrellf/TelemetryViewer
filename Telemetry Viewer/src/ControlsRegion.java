import java.awt.Desktop;
import java.awt.Dimension;
import java.net.URI;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * The panel of controls located at the bottom of the main window.
 */
@SuppressWarnings("serial")
public class ControlsRegion extends JPanel {
	
	static ControlsRegion instance = new ControlsRegion();
	
	JButton openLayoutButton;	
	CommunicationView communicationView;
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private ControlsRegion() {
		
		super();
		communicationView = new CommunicationView();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(new EmptyBorder(Theme.padding, Theme.padding, Theme.padding, Theme.padding));
		
		JToggleButton settingsButton = new JToggleButton("Settings");
		settingsButton.setSelected(SettingsView.instance.isVisible());
		settingsButton.addActionListener(event -> SettingsView.instance.setVisible(settingsButton.isSelected()));
		
		openLayoutButton = new JButton("Open Layout");
		openLayoutButton.addActionListener(event -> {
			
			JFileChooser inputFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(inputFile.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = inputFile.getSelectedFile().getAbsolutePath();
				Controller.openLayout(filePath, true);
			}
			
		});
		
		JButton saveLayoutButton = new JButton("Save Layout");
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
		CommunicationController.addConnectionListener(newConnectionState -> saveLayoutButton.setEnabled(newConnectionState));
		
		JButton importCsvLogButton = new JButton("Import CSV Log");
		importCsvLogButton.addActionListener(event -> {
			
			JFileChooser inputFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(inputFile.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = inputFile.getSelectedFile().getAbsolutePath();
				Controller.importCsvLogFile(filePath);
			}

		});
		
		JButton exportCsvLogButton = new JButton("Export CSV Log");
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
		DatasetsController.addSampleCountListener(haveSamples -> exportCsvLogButton.setEnabled(haveSamples));
		
		JButton helpButton = new JButton("Help");
		helpButton.addActionListener(event -> {
			
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			String helpText = "<html><b>Telemetry Viewer v0.6 (2019-09-08)</b><br>" +
			                  "A fast and easy tool for visualizing data received over a UART/TCP/UDP connection.<br><br>" +
			                  "Step 1: Use the controls at the lower-right corner of the window to connect to a serial port or to start a TCP/UDP server.<br>" +
			                  "Step 2: A \"Data Structure\" window will pop up. Use it to specify how your data is laid out, then click \"Done.\"<br>" +
			                  "Step 3: Click-and-drag in the tiles region to place a chart.<br>" +
			                  "Step 4: A chart configuration panel will appear. Use it to specify the type of chart and its settings, then click \"Done.\"<br>" +
			                  "Repeat steps 3 and 4 to create more charts.<br><br>" +
			                  "Use your scroll wheel to rewind or fast forward.<br>" +
			                  "Use your scroll wheel while holding down Ctrl to zoom in or out.<br>" +
			                  "Use your scroll wheel while holding down Shift to adjust display scaling.<br><br>" +
			                  "Click the x icon at the top-right corner of any chart to remove it.<br>" +
			                  "Click the box icon at the top-right corner of any chart to maximize it.<br>" +
			                  "Click the gear icon at the top-right corner of any chart to change its settings.<br><br>" +
			                  "Click the \"Settings\" button to adjust options related to the GUI.<br>" +
			                  "Click the \"Open Layout\" button to open a previously saved layout file.<br>" +
			                  "Click the \"Save Layout\" button to save your current configuration (connection settings, data structure, and chart settings) to a file.<br>" +
			                  "Click the \"Import CSV Log\" button to import a previously saved CSV file.<br>" +
			                  "Click the \"Export CSV Log\" button to save all of your acquired samples to a CSV file.<br>" +
			                  "Click the \"Reset\" button to remove all charts.<br><br>" +
			                  "Layout files and CSV log files can also be opened via drag-n-drop.<br><br>" +
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
		
		// show the components
		add(settingsButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(openLayoutButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(saveLayoutButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(importCsvLogButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(exportCsvLogButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(helpButton);
		add(Box.createHorizontalStrut(Theme.padding));
		add(Box.createHorizontalGlue());
		add(communicationView);
		
	}
	
	@Override public Dimension getPreferredSize() {
		
		// set minimum panel width to 120% of the "preferred" width
		setPreferredSize(null);
		revalidate();
		Dimension size = super.getPreferredSize();
		size.width = (int) (size.width * 1.2);
		
		return size;
		
	}
	
	@Override public Dimension getMinimumSize() {
		
		return getPreferredSize();
		
	}

}
