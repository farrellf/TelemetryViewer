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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

	static JFrame window = new JFrame("Telemetry Viewer v0.7");
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
		window.add(NotificationsView.instance, BorderLayout.NORTH);
		window.add(OpenGLChartsView.instance,  BorderLayout.CENTER);
		window.add(SettingsView.instance,      BorderLayout.WEST);
		window.add(CommunicationView.instance, BorderLayout.SOUTH);
		window.add(ConfigureView.instance,     BorderLayout.EAST);
		NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> false, true);
		
		// size the window
		int settingsViewWidth = SettingsView.instance.getPreferredSize().width;
		int dataStructureViewWidth = Integer.max(PacketCsv.instance.getDataStructureGui().getPreferredSize().width, PacketBinary.instance.getDataStructureGui().getPreferredSize().width);
		int configureViewWidth = ConfigureView.instance.getPreferredSize().width;
		int notificationHeight = NotificationsView.instance.getPreferredSize().height;
		int settingsViewHeight = SettingsView.instance.preferredSize.height;
		int controlsViewHeight = CommunicationView.instance.getPreferredSize().height;
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
		
		// allow the user to drag-n-drop settings/CSV/camera files
		window.setDropTarget(new DropTarget() {			
			@Override public void drop(DropTargetDropEvent event) {
				try {
					event.acceptDrop(DnDConstants.ACTION_LINK);
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					String[] filepaths = new String[files.size()];
					for(int i = 0; i < files.size(); i++)
						filepaths[i] = files.get(i).getAbsolutePath();
					CommunicationController.importFiles(filepaths);
				} catch(Exception e) {}
			}
		});
		
		// create a directory for the cache, and remove it on exit
		Path cacheDir = Paths.get("cache");
		try { Files.createDirectory(cacheDir); } catch(FileAlreadyExistsException e) {} catch(Exception e) { e.printStackTrace(); }
		window.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				CommunicationController.disconnect(null);
				DatasetsController.removeAllDatasets();
				try { Files.deleteIfExists(cacheDir); } catch(Exception e) { }
			}
		});
		
		// show the window
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
		
	}
	
	/**
	 * Hides the charts and settings panels, then shows the data structure screen in the middle of the main window.
	 * This method is thread-safe.
	 */
	public static void showDataStructureGui() {
		
		SwingUtilities.invokeLater(() -> {
			OpenGLChartsView.instance.animator.pause();
			CommunicationView.instance.showSettings(false);
			ConfigureView.instance.close();
			window.remove(OpenGLChartsView.instance);
			window.add(CommunicationController.getDataStructureGui(), BorderLayout.CENTER);
			window.revalidate();
			window.repaint();
		});
		
	}
	
	/**
	 * Hides the data structure screen and shows the charts in the middle of the main window.
	 * This method is thread-safe.
	 */
	public static void hideDataStructureGui() {
		
		SwingUtilities.invokeLater(() -> {
			// do nothing if already hidden
			for(Component c : window.getContentPane().getComponents())
				if(c == OpenGLChartsView.instance)
					return;
					
			window.remove(PacketBinary.BinaryDataStructureGui.instance);
			window.remove(PacketCsv.CsvDataStructureGui.instance);
			window.add(OpenGLChartsView.instance, BorderLayout.CENTER);
			window.revalidate();
			window.repaint();
			OpenGLChartsView.instance.animator.resume();
		});
		
	}

}
