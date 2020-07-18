import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.github.sarxos.webcam.Webcam;

public class WidgetCamera extends Widget {
	
	static List<Webcam> cameras = Webcam.getWebcams();
	static Dimension[] resolutions = new Dimension[] {
		new Dimension(640, 480),
		new Dimension(1280, 720),
		new Dimension(1920, 1080),
		new Dimension(3840, 2160)
	};
	
	JLabel            namesLabel;
	JComboBox<String> namesCombobox;
	JLabel            mjpegUrlLabel;
	JTextField        mjpegUrlTextfield;
	JLabel            resolutionsLabel;
	JComboBox<String> resolutionsCombobox;
	CameraConsumer    handler;
	
	/**
	 * A widget that lets the user pick a camera and resolution from drop-down lists.
	 * 
	 * @param eventHandler    Will be notified when the chosen options change.
	 */
	public WidgetCamera(CameraConsumer eventHandler) {
		
		super();
		
		namesLabel = new JLabel("Camera: ");
		namesCombobox = new JComboBox<String>();
		for(Webcam camera : cameras)
			namesCombobox.addItem(camera.getName());
		namesCombobox.addItem("MJPEG over HTTP");
		
		mjpegUrlLabel = new JLabel("URL: ");
		mjpegUrlTextfield = new JTextField("http://example.com:8080/video");
		// crude attempt to make the default URL be the IP address of the default gateway
		String ip = CommunicationController.getLoclIpAddress();
		if(ip.split("\\.").length == 4)
			mjpegUrlTextfield.setText("http://" + ip.substring(0, ip.lastIndexOf(".")) + ".1:8080/video");

		resolutionsLabel = new JLabel("Resolution: ");
		resolutionsCombobox = new JComboBox<String>();
		for(Dimension resolution : resolutions)
			resolutionsCombobox.addItem(resolution.width + " x " + resolution.height);
		
		mjpegUrlTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent e)   { sanityCheck(); }
			@Override public void focusGained(FocusEvent e) { }
		});
				
		widgets.put(namesLabel,          "");
		widgets.put(namesCombobox,       "span 3, growx");
		widgets.put(mjpegUrlLabel,       "");
		widgets.put(mjpegUrlTextfield,   "span 3, growx");
		widgets.put(resolutionsLabel,    "");
		widgets.put(resolutionsCombobox, "span 3, growx");
		
		handler = eventHandler;
		sanityCheck();
		
	}
	
	/**
	 * Removes ActionListeners so that programmatic changes to the GUI do not trigger recursive events.
	 */
	private void removeActionListeners() {
		
		for(ActionListener l : namesCombobox.getActionListeners())
			namesCombobox.removeActionListener(l);
		for(ActionListener l : mjpegUrlTextfield.getActionListeners())
			mjpegUrlTextfield.removeActionListener(l);
		for(ActionListener l : resolutionsCombobox.getActionListeners())
			resolutionsCombobox.removeActionListener(l);
		
	}
	
	/**
	 * Adds ActionListeners so the GUI can trigger events.
	 */
	private void addActionListeners() {
		
		namesCombobox.addActionListener(event -> sanityCheck());
		mjpegUrlTextfield.addActionListener(event -> sanityCheck());
		resolutionsCombobox.addActionListener(event -> sanityCheck());
		
	}
	
	/**
	 * Notifies the handler.
	 */
	public void sanityCheck() {
		
		// pause events
		removeActionListeners();
		
		// update the GUI
		mjpegUrlTextfield.setText(mjpegUrlTextfield.getText().trim());
		if(namesCombobox.getSelectedItem().toString().equals("MJPEG over HTTP")) {
			mjpegUrlLabel.setVisible(true);
			mjpegUrlTextfield.setVisible(true);
			resolutionsLabel.setVisible(false);
			resolutionsCombobox.setVisible(false);
		} else {
			mjpegUrlLabel.setVisible(false);
			mjpegUrlTextfield.setVisible(false);
			resolutionsLabel.setVisible(true);
			resolutionsCombobox.setVisible(true);
		}
		
		// update the chart
		boolean isMjpeg = namesCombobox.getSelectedItem().toString().equals("MJPEG over HTTP");
		String name = isMjpeg ? mjpegUrlTextfield.getText() : namesCombobox.getSelectedItem().toString();
		handler.accept(name, isMjpeg, resolutions[resolutionsCombobox.getSelectedIndex()]);
		
		// resume events
		addActionListeners();
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(CommunicationController.QueueOfLines lines) {
		
		// pause events
		removeActionListeners();

		// update the GUI
		String name = ChartUtils.parseString(lines.remove(), "camera name = %s");
		int cameraN = -1;
		for(int i = 0; i < namesCombobox.getItemCount(); i++)
			if(namesCombobox.getItemAt(i).equals(name))
				cameraN = i;
		if(cameraN >= 0) {
			namesCombobox.setSelectedIndex(cameraN);
		} else {
			namesCombobox.addItem(name);
			namesCombobox.setSelectedItem(name);
		}
		
		String url = ChartUtils.parseString(lines.remove(), "mjpeg url = %s");
		if(url.length() == 0 || url.startsWith("http"))
			mjpegUrlTextfield.setText(url);
		else
			throw new AssertionError("Invalid MJPEG URL.");
		
		String resolution = ChartUtils.parseString(lines.remove(), "requested resolution = %s");
		resolutionsCombobox.setSelectedItem(resolution);
		if(!resolutionsCombobox.getSelectedItem().equals(resolution))
			throw new AssertionError("Invalid camera resolution.");
		
		// process the new state
		sanityCheck();
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		return new String[] { "camera name = "          + namesCombobox.getSelectedItem().toString(),
		                      "mjpeg url = "            + mjpegUrlTextfield.getText(),
		                      "requested resolution = " + resolutionsCombobox.getSelectedItem().toString()};
		
	}
	
	/**
	 * A functional interface, similar to Java's BiConsumer. Used for sending a camera to a chart.
	 */
	public interface CameraConsumer {

		public void accept(String name, boolean isMjpeg, Dimension resolution);
		
	}

}
