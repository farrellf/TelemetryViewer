import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.UIManager;

public class Main {
	
	JFrame window;
	ChartsRegion chartsRegion;
	ControlsRegion controlsRegion;
	
	public Main() {
		
		window = new JFrame("Telemetry Viewer");
		chartsRegion = new ChartsRegion();
		controlsRegion = new ControlsRegion();
		
		window.setLayout(new BorderLayout());
		window.add(chartsRegion, BorderLayout.CENTER);
		window.add(controlsRegion, BorderLayout.SOUTH);
		
		window.setExtendedState(JFrame.MAXIMIZED_BOTH);
		window.setSize( (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width * 0.6), (int) (GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height * 0.6) );
		window.setLocationRelativeTo(null);
		
		window.setMinimumSize(window.getPreferredSize());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);

	}

	public static void main(String[] args) {
		
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception e){}
		
		new Main();
		
	}

}
