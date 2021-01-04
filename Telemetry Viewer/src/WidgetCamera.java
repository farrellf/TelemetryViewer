import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JLabel;

public class WidgetCamera extends Widget {
	
	JLabel            namesLabel;
	JComboBox<String> namesCombobox;
	Consumer<String>  handler;
	
	String selectedCamera;
	
	/**
	 * A widget that lets the user pick a camera and resolution from drop-down lists.
	 * 
	 * @param eventHandler    Will be notified when the chosen options change.
	 */
	public WidgetCamera(Consumer<String> eventHandler) {
		
		super();
		
		handler = eventHandler;
		update();
		
		
	}
	
	@Override public void update() {
		
		widgets.clear();
		
		namesLabel = new JLabel("Camera: ");
		namesCombobox = new JComboBox<String>();
		for(ConnectionCamera camera : ConnectionsController.cameraConnections)
			if(camera.connected || camera.getSampleCount() > 0)
				namesCombobox.addItem(camera.name);
		if(namesCombobox.getItemCount() == 0) {
			namesCombobox.addItem("[No cameras available]");
			namesCombobox.setEnabled(false);
		}
		for(int i = 0; i < namesCombobox.getItemCount(); i++)
			if(namesCombobox.getItemAt(i).equals(selectedCamera))
				namesCombobox.setSelectedIndex(i);
		namesCombobox.addActionListener(event -> {
			selectedCamera = namesCombobox.getSelectedItem().toString();
			handler.accept(selectedCamera);
		});
				
		widgets.put(namesLabel,    "");
		widgets.put(namesCombobox, "span 3, growx");

		handler.accept(namesCombobox.getSelectedItem().toString());
		
	}
	
	/**
	 * Updates the widget and chart based on settings from a layout file.
	 * 
	 * @param lines    A queue of remaining lines from the layout file.
	 */
	@Override public void importState(ConnectionsController.QueueOfLines lines) {

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
		
	}
	
	/**
	 * Saves the current state to one or more lines of text.
	 * 
	 * @return    A String[] where each element is a line of text.
	 */
	@Override public String[] exportState() {
		
		return new String[] { "camera name = " + namesCombobox.getSelectedItem().toString()};
		
	}

}
