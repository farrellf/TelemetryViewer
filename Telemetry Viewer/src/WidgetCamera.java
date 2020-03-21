import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.github.sarxos.webcam.Webcam;

@SuppressWarnings("serial")
public class WidgetCamera extends Widget {
	
	static List<Webcam> cameras = Webcam.getWebcams();
	static Dimension[] resolutions = new Dimension[] {
		new Dimension(640, 480),
		new Dimension(1280, 720),
		new Dimension(1920, 1080),
		new Dimension(3840, 2160)
	};
	
	JComboBox<String> namesCombobox;
	JTextField mjpegUrlTextfield;
	JComboBox<String> resolutionsCombobox;
	CameraConsumer handler;
	
	/**
	 * A widget that lets the user pick a camera and resolution from drop-down lists.
	 * 
	 * @param eventHandler    Will be notified when the chosen options change.
	 */
	public WidgetCamera(CameraConsumer eventHandler) {
		
		super();
		
		namesCombobox = new JComboBox<String>();
		for(Webcam camera : cameras)
			namesCombobox.addItem(camera.getName());
		namesCombobox.addItem("MJPEG over HTTP");
		
		mjpegUrlTextfield = new JTextField("http://example.com:8080/video");
		// crude attempt to make the default URL be the IP address of the default gateway
		if(Communication.localIp.split("\\.").length == 4)
			mjpegUrlTextfield.setText("http://" + Communication.localIp.substring(0, Communication.localIp.lastIndexOf(".")) + ".1:8080/video");

		resolutionsCombobox = new JComboBox<String>();
		for(Dimension resolution : resolutions)
			resolutionsCombobox.addItem(resolution.width + " x " + resolution.height);
		
		namesCombobox.addActionListener(event -> notifyHandler());
		mjpegUrlTextfield.addActionListener(event -> notifyHandler());
		resolutionsCombobox.addActionListener(event -> notifyHandler());
		
		setLayout(new GridLayout(2, 2, 10, 10));
		
		handler = eventHandler;
		notifyHandler();
		
	}
	
	/**
	 * Notifies the handler.
	 */
	public void notifyHandler() {
		
		// update the GUI
		mjpegUrlTextfield.setText(mjpegUrlTextfield.getText().trim());
		removeAll();
		add(new JLabel("Camera: "));
		add(namesCombobox);
		if(namesCombobox.getSelectedItem().toString().equals("MJPEG over HTTP")) {
			add(new JLabel("URL: "));
			add(mjpegUrlTextfield);
		} else {
			add(new JLabel("Resolution: "));
			add(resolutionsCombobox);
		}
		revalidate();
		repaint();
		
		boolean isMjpeg = namesCombobox.getSelectedItem().toString().equals("MJPEG over HTTP");
		String name = isMjpeg ? mjpegUrlTextfield.getText() : namesCombobox.getSelectedItem().toString();
		handler.accept(name, isMjpeg, resolutions[resolutionsCombobox.getSelectedIndex()]);
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(Controller.QueueOfLines lines) {
		
		// disable the event listeners
		for(ActionListener l : namesCombobox.getActionListeners())
			namesCombobox.removeActionListener(l);
		for(ActionListener l : mjpegUrlTextfield.getActionListeners())
			mjpegUrlTextfield.removeActionListener(l);
		for(ActionListener l : resolutionsCombobox.getActionListeners())
			resolutionsCombobox.removeActionListener(l);

		// update the widgets
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
		
		String res = ChartUtils.parseString(lines.remove(), "requested resolution = %s");
		int resN = -1;
		for(int i = 0; i < resolutionsCombobox.getItemCount(); i++)
			if(resolutionsCombobox.getItemAt(i).equals(res))
				resN = i;
		if(resN >= 0)
			resolutionsCombobox.setSelectedIndex(resN);
		else
			throw new AssertionError("Invalid camera resolution.");
		
		// enable the event listeners
		namesCombobox.addActionListener(event -> notifyHandler());
		mjpegUrlTextfield.addActionListener(event -> notifyHandler());
		resolutionsCombobox.addActionListener(event -> notifyHandler());
		
		// update the chart
		notifyHandler();
		
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
