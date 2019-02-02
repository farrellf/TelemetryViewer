import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.JFrame;
import javax.swing.UIManager;

public class Main {

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
		
		NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> CommunicationController.isConnected() || !Controller.getCharts().isEmpty(), false);
		
		LogitechSmoothScrolling mouse = new LogitechSmoothScrolling();
		
		window.addWindowFocusListener(new WindowFocusListener() {
			@Override public void windowGainedFocus(WindowEvent we) {
				mouse.updateScrolling();
			}
			@Override public void windowLostFocus(WindowEvent we) { }
		});
		
	}

}
