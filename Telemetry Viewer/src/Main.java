import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

import javax.swing.JFrame;
import javax.swing.UIManager;

public class Main {

	public static void main(String[] args) {
		
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception e){}
		
		JFrame window = new JFrame("Telemetry Viewer v0.4");
		ControlsRegion controlsRegion = new ControlsRegion();
		OpenGLChartsRegion chartsRegion = new OpenGLChartsRegion(controlsRegion);
		
		window.setLayout(new BorderLayout());
		window.add(chartsRegion, BorderLayout.CENTER);
		window.add(controlsRegion, BorderLayout.SOUTH);
		
		window.setExtendedState(JFrame.MAXIMIZED_BOTH);
		window.setSize( (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width * 0.6), (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height * 0.6) );
		window.setLocationRelativeTo(null);
		
		window.setMinimumSize(window.getPreferredSize());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
		
		LogitechSmoothScrolling mouse = new LogitechSmoothScrolling();
		
		window.addWindowFocusListener(new WindowFocusListener() {
			@Override public void windowGainedFocus(WindowEvent we) {
				mouse.enableSmoothScrolling();
			}
			@Override public void windowLostFocus(WindowEvent we) { }
		});
		
	}

}
