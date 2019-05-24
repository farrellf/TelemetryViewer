import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.UIManager;

public class Main {

	@SuppressWarnings("serial")
	public static void main(String[] args) {
		
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception e){}
		
		JFrame window = new JFrame("Telemetry Viewer v0.5");
		NotificationsView notificationsRegion = new NotificationsView();
		SettingsView settingsRegion = new SettingsView();
		ControlsRegion controlsRegion = new ControlsRegion(settingsRegion);
		OpenGLChartsRegion chartsRegion = new OpenGLChartsRegion(settingsRegion, controlsRegion);
		
		window.setLayout(new BorderLayout());
		window.add(notificationsRegion, BorderLayout.NORTH);
		window.add(chartsRegion, BorderLayout.CENTER);
		window.add(settingsRegion, BorderLayout.WEST);
		window.add(controlsRegion, BorderLayout.SOUTH);
		window.add(ConfigureView.instance, BorderLayout.EAST);
		
		window.setExtendedState(JFrame.MAXIMIZED_BOTH);
		window.setSize( (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width * 0.6), (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height * 0.6) );
		window.setLocationRelativeTo(null);
		
		window.setMinimumSize(window.getPreferredSize());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
		
		NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), true);
		
		LogitechSmoothScrolling mouse = new LogitechSmoothScrolling();
		
		window.addWindowFocusListener(new WindowFocusListener() {
			@Override public void windowGainedFocus(WindowEvent we) {
				mouse.updateScrolling();
			}
			@Override public void windowLostFocus(WindowEvent we) { }
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
		
	}

}
