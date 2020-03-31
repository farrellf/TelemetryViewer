import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.UIManager;

public class Main {

	static JFrame window = new JFrame("Telemetry Viewer v0.6");
	static LogitechSmoothScrolling mouse = new LogitechSmoothScrolling();
	
	/**
	 * Entry point for the program.
	 * This just creates and configures the main window.
	 * 
	 * @param args    Command line arguments (not currently used.)
	 */
	@SuppressWarnings("serial")
	public static void main(String[] args) {
		
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception e){}
		
		// populate the window
		window.setLayout(new BorderLayout());
		window.add(NotificationsView.instance,  BorderLayout.NORTH);
		window.add(OpenGLChartsRegion.instance, BorderLayout.CENTER);
		window.add(SettingsView.instance,       BorderLayout.WEST);
		window.add(ControlsRegion.instance,     BorderLayout.SOUTH);
		window.add(ConfigureView.instance,      BorderLayout.EAST);
		NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
		
		// size the window
		int settingsViewWidth = SettingsView.instance.getPreferredSize().width;
		int dataStructureViewWidth = Integer.max(PacketCsv.instance.getDataStructureGui().getPreferredSize().width, PacketBinary.instance.getDataStructureGui().getPreferredSize().width);
		int configureViewWidth = ConfigureView.instance.getPreferredSize().width;
		int notificationHeight = NotificationsView.instance.getPreferredSize().height;
		int settingsViewHeight = SettingsView.instance.preferredSize.height;
		int controlsViewHeight = ControlsRegion.instance.getPreferredSize().height;
		int width  = settingsViewWidth + dataStructureViewWidth + configureViewWidth + (4 * Theme.padding);
		int height = notificationHeight + settingsViewHeight + controlsViewHeight + (8 * Theme.padding);
		Dimension size = new Dimension(width, height);
		window.setSize(size);
		window.setMinimumSize(size);
		window.setLocationRelativeTo(null);
		window.setExtendedState(JFrame.MAXIMIZED_BOTH);
		
		// support smooth scrolling
		window.addWindowFocusListener(new WindowFocusListener() {
			@Override public void windowGainedFocus(WindowEvent we) { mouse.updateScrolling(); }
			@Override public void windowLostFocus(WindowEvent we)   { }
		});
		
		// allow the user to drag-n-drop a layout file, or a CSV log file, or both
		window.setDropTarget(new DropTarget() {			
			@Override public void drop(DropTargetDropEvent event) {
				try {
					event.acceptDrop(DnDConstants.ACTION_LINK);
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					
					if(files.size() == 1 && files.get(0).getAbsolutePath().endsWith(".txt")) {
						Controller.openLayout(files.get(0).getAbsolutePath(), true);
					} else if(files.size() == 1 && files.get(0).getAbsolutePath().endsWith(".csv")) {
						Controller.importCsvLogFile(files.get(0).getAbsolutePath());
					} else if(files.size() == 2 && files.get(0).getAbsolutePath().endsWith(".txt") && files.get(1).getAbsolutePath().endsWith(".csv")) {
						Controller.openLayout(files.get(0).getAbsolutePath(), false);
						Controller.importCsvLogFile(files.get(1).getAbsolutePath());
					} else if(files.size() == 2 && files.get(0).getAbsolutePath().endsWith(".csv") && files.get(1).getAbsolutePath().endsWith(".txt")) {
						Controller.openLayout(files.get(1).getAbsolutePath(), false);
						Controller.importCsvLogFile(files.get(0).getAbsolutePath());
					} else {
						NotificationsController.showFailureUntil("Error: Wrong file type or too many files selected. Select one layout file, or one CSV log file, or one of each.", () -> false, true);
					}
						
				} catch(Exception e) {}
			}
		});
		
		// create a directory for the cache, and remove it on exit
		Path cacheDir = Paths.get("cache");
		try { Files.createDirectory(cacheDir); } catch(FileAlreadyExistsException e) {} catch(Exception e) { e.printStackTrace(); }
		window.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				CommunicationController.disconnect();
				DatasetsController.removeAllDatasets();
				try { Files.deleteIfExists(cacheDir); } catch(Exception e) { }
			}
		});
		
		// show the window
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
		
	}
	
	/**
	 * Hides the charts and shows the data structure GUI in the middle of the main window.
	 */
	public static void showDataStructureGui() {
		
		OpenGLChartsRegion.instance.animator.pause();
		SettingsView.instance.setVisible(false);
		ConfigureView.instance.close();
		window.remove(OpenGLChartsRegion.instance);
		window.add(Communication.packet.getDataStructureGui(), BorderLayout.CENTER);
		window.revalidate();
		window.repaint();
		
	}
	
	/**
	 * Hides the data structure GUI and shows the charts in the middle of the main window.
	 */
	public static void hideDataStructureGui() {
		
		// do nothing if already hidden
		for(Component c : window.getContentPane().getComponents())
			if(c == OpenGLChartsRegion.instance)
				return;
				
		window.remove(PacketBinary.BinaryDataStructureGui.instance);
		window.remove(PacketCsv.CsvDataStructureGui.instance);
		window.add(OpenGLChartsRegion.instance, BorderLayout.CENTER);
		window.revalidate();
		window.repaint();
		OpenGLChartsRegion.instance.animator.resume();
		
		if(CommunicationController.isConnected() && Controller.getCharts().isEmpty())
			NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !Controller.getCharts().isEmpty(), true);
		
	}

}
