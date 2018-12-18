import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class ConfigureChartWindow extends JDialog {
	
	/**
	 * A dialog box where the user can configure an existing chart's settings.
	 *  
	 * @param parentWindow    The JFrame that this dialog box should be tied to (and centered over.)
	 * @param chart           The chart to configure.
	 */
	public ConfigureChartWindow(JFrame parentWindow, PositionedChart chart) {
		
		setTitle("Configure Chart");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JPanel windowContents = new JPanel();
		windowContents.setBorder(new EmptyBorder(10, 10, 10, 10));
		windowContents.setLayout(new BoxLayout(windowContents, BoxLayout.Y_AXIS));
		JScrollPane scrollPane = new JScrollPane(windowContents, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);  //Let all scrollPanel has scroll bars
		add(scrollPane);
		
		JPanel doneButtonPanel = new JPanel();
		JButton doneButton = new JButton("Done");
		doneButton.addActionListener(event -> dispose());
		doneButtonPanel.setLayout(new GridLayout(1, 3, 10, 10));
		doneButtonPanel.add(new JLabel(""));
		doneButtonPanel.add(new JLabel(""));
		doneButtonPanel.add(doneButton);
		
		// show the control widgets
		for(JPanel widget : chart.getWidgets()) {
			windowContents.add(widget != null ? widget : Box.createVerticalStrut(10));
			windowContents.add(Box.createVerticalStrut(10));
		}
		
		// leave some room, then show the done button
		windowContents.add(Box.createVerticalStrut(40));
		windowContents.add(doneButtonPanel);
				
		// size and position the window
		setResizable(true);
		pack();
		setSize((int) (getWidth() * 1.3), (int) (getWidth() * 1.3));
		setLocationRelativeTo(parentWindow);
		
		setModal(true);
		setVisible(true);
		
	}

}
