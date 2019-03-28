import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
	
	JButton openLayoutButton;	
	CommunicationView communicationView;
	
	/**
	 * Creates the panel of controls and registers their event handlers.
	 */
	public ControlsRegion(SettingsView settingsView) {
		
		super();
		communicationView = new CommunicationView();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(new EmptyBorder(5, 5, 5, 5));
		
		JToggleButton settingsButton = new JToggleButton("Settings");
		settingsButton.setSelected(settingsView.isVisible());
		settingsButton.addActionListener(event -> settingsView.setVisible(settingsButton.isSelected()));
		
		openLayoutButton = new JButton("Open Layout");
		openLayoutButton.addActionListener(event -> {
			
			JFileChooser inputFile = new JFileChooser();
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			if(inputFile.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
				String filePath = inputFile.getSelectedFile().getAbsolutePath();
				Controller.openLayout(filePath);
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
		
		JButton resetButton = new JButton("Reset");
		resetButton.addActionListener(event -> Controller.removeAllCharts());
		
		JButton helpButton = new JButton("Help");
		helpButton.addActionListener(event -> {
			
			JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ControlsRegion.this);
			String helpText = "<html><b>Telemetry Viewer v0.5 (2018-08-20)</b><br>" +
			                  "A fast and easy tool for visualizing data received over a UART/TCP/UDP connection.<br><br>" +
			                  "Step 1: Use the controls at the lower-right corner of the main window to connect to a serial port or to start a TCP/UDP server.<br>" +
			                  "Step 2: A \"Data Structure\" window will pop up, use it to specify how your data is laid out, then click \"Done\"<br>" +
			                  "Step 3: Click-and-drag in the tiles region to place a chart.<br>" +
			                  "Step 4: A \"New Chart\" window will pop up, use it to specify the type of chart and its settings.<br>" +
			                  "Repeat steps 3 and 4 to create more charts.<br><br>" +
			                  "Use your scroll wheel to rewind or fast forward.<br>" +
			                  "Use your scroll wheel while holding down Ctrl to zoom in or out.<br>" +
			                  "Use your scroll wheel while holding down Shift to adjust display scaling.<br><br>" +
			                  "Click the x icon at the top-right corner of any chart to remove it.<br>" +
			                  "Click the gear icon at the top-right corner of any chart to change its settings.<br><br>" +
			                  "Click the \"Settings\" button to adjust options related to the GUI.<br>" +
			                  "Click the \"Open Layout\" button to open a layout file.<br>" +
			                  "Click the \"Save Layout\" button to save your current configuration (connection settings, data structure, and chart settings) to a file.<br>" +
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
		
		// show the components
		add(settingsButton);
		add(Box.createHorizontalStrut(5));
		add(openLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(saveLayoutButton);
		add(Box.createHorizontalStrut(5));
		add(importCsvLogButton);
		add(Box.createHorizontalStrut(5));
		add(exportCsvLogButton);
		add(Box.createHorizontalStrut(5));
		add(resetButton);
		add(Box.createHorizontalStrut(5));
		add(helpButton);
		add(Box.createHorizontalStrut(5));
		add(Box.createHorizontalGlue());
		add(communicationView);
		
		// set minimum panel width to 120% of the "preferred" width
		Dimension size = getPreferredSize();
		size.width = (int) (size.width * 1.2);
		setMinimumSize(size);
		setPreferredSize(size);
		
	}

}
