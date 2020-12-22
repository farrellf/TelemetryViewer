import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The user establishes one or more Connections, with each Connection providing a stream of data.
 * The data might be normal telemetry, or camera images, or ...
 * The data might be coming from a live connection, or imported from a file.
 * 
 * This parent class defines some abstract methods that must be implemented by each child class.
 * The rest of this class contains fields and methods that are used by some of the child classes.
 * Ideally there would be a deeper inheritance structure to divide some of this into even more classes, but for now everything just inherits from Connection.
 */
public abstract class Connection {
	
	Thread receiverThread;  // listens for the stream of data
	Thread processorThread; // processes the received data
	volatile boolean connected = false;
	
	/**
	 * @return    A GUI with widgets for controlling this connection.
	 */
	public abstract JPanel getGui();
	
	/**
	 * Connects and listens for incoming telemetry.
	 * 
	 * @param showGui    If true, show a configuration GUI after establishing the connection.
	 */
	public abstract void connect(boolean showGui);
	
	/**
	 * Configures this connection by reading from a settings file.
	 * 
	 * @param lines              Lines of text from the settings file.
	 * @throws AssertionError    If the settings file does not contain a valid configuration.
	 */
	public abstract void importSettings(ConnectionsController.QueueOfLines lines) throws AssertionError;
	
	/**
	 * Saves the configuration to a settings file.
	 * 
	 * @param file    Destination file.
	 */
	public abstract void exportSettings(PrintWriter file);
	
	/**
	 * Reads just enough from a data file to determine the timestamp of the first item.
	 * 
	 * @param path    Path to the file.
	 * @return        Timestamp for the first item, or Long.MAX_VALUE on error.
	 */
	public abstract long getFirstTimestamp(String path);
	
	/**
	 * Reads data (samples or images or ...) from a file, instead of a live connection.
	 * 
	 * @param path                  Path to the file.
	 * @param firstTimestamp        Timestamp when the first sample from ANY connection was acquired. This is used to allow importing to happen in real time.
	 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
	 */
	public abstract void importDataFile(String path, long firstTimestamp, AtomicLong completedByteCount);
	
	/**
	 * Writes data (samples or images or ...) to a file, so it can be replayed later on.
	 * 
	 * @param path                  Path to the file.
	 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
	 */
	public abstract void exportDataFile(String path, AtomicLong completedByteCount);
	
	/**
	 * Permanently closes the connection and removes any cached data in memory or on disk.
	 */
	public abstract void dispose();
	
	/**
	 * Disconnects from the device and removes any connection-related Notifications.
	 * This method blocks until disconnected, so it should not be called directly from the receiver thread.
	 * 
	 * @param errorMessage    If not null, show this as a Notification until a connection is attempted. 
	 */
	public void disconnect(String errorMessage) {
		
		NotificationsController.removeIfConnectionRelated();
		if(errorMessage != null)
			NotificationsController.showFailureUntil(errorMessage, () -> false, true);

		Main.hideConfigurationGui();
		
		if(connected) {
			
			// tell the receiver thread to terminate by setting the boolean AND interrupting the thread because
			// interrupting the thread might generate an IOException, but we don't want to report that as an error
			connected = false;
			if(receiverThread.isAlive()) {
				receiverThread.interrupt();
				while(receiverThread.isAlive()); // wait
			}

			SwingUtilities.invokeLater(() -> { // invokeLater so this if() fails when importing a layout that has charts
				if(ChartsController.getCharts().isEmpty() && !connected)
					NotificationsController.showHintUntil("Start by connecting to a device or opening a file by using the buttons below.", () -> !ChartsController.getCharts().isEmpty(), true);
			});
			
		}
		
		CommunicationView.instance.redraw();
		
	}
	
}
