import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJCompressor;
import org.libjpegturbo.turbojpeg.TJDecompressor;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.WebcamLockException;
import com.jogamp.common.nio.Buffers;

import net.miginfocom.swing.MigLayout;

public class ConnectionCamera extends Connection {
	
	static List<Webcam> cameras = Webcam.getWebcams();
	static List<String> names = new ArrayList<String>();
	static {
		for(Webcam camera : cameras)
			names.add(camera.getName());
		names.add("MJPEG over HTTP");
	}
	static Dimension[] resolutions = new Dimension[] {
		new Dimension(640, 480),
		new Dimension(1280, 720),
		new Dimension(1920, 1080),
		new Dimension(3840, 2160)
	};
	
	// threading
	private volatile int liveJpegThreads = 0; // number of currently live sub-threads encoding or decoding jpeg's

	// camera state
	private boolean isMjpeg;
	private Webcam camera;
	private Dimension requestedResolution = resolutions[0];
	
	// images in memory
	private volatile GLframe liveImage = new GLframe(null, true, 1, 1, "[waiting]", 0);
	private volatile GLframe oldImage  = new GLframe(null, true, 1, 1, "[waiting]", 0);
	
	// images archived to disk
	private class FrameInfo {
		long timestamp; // when this frame was captured, milliseconds since 1970-01-01
		long offset;    // byte offset in the file where the jpeg starts
		int  length;    // byte count of the jpeg
		public FrameInfo(long timestamp, long offset, int length) { this.timestamp = timestamp; this.offset = offset; this.length = length; }
	}
	private volatile List<FrameInfo> framesIndex = Collections.synchronizedList(new ArrayList<FrameInfo>()); // index = frame number
	private volatile Path pathOnDisk = Paths.get("cache/" + this.toString() + ".mjpg");
	private volatile FileChannel file;
	private volatile boolean fileIsImported;
	
	public ConnectionCamera() {
		
		name = names.get(0);
		
		// ensure this connection doesn't already exist, but allow multiple MJPEG over HTTP connections
		for(int i = 1; i < names.size(); i++)
			for(Connection connection : ConnectionsController.allConnections)
				if(name.equals(connection.name) && !name.equals("MJPEG over HTTP"))
					name = getNames().get(i);
		
		isMjpeg = name.startsWith("MJPEG over HTTP");
		if(!isMjpeg)
			for(Webcam cam : cameras)
				if(cam.getName().equals(name))
					camera = cam;
		
		// create the cache file
		try {
			file = FileChannel.open(pathOnDisk, StandardOpenOption.CREATE,
			                                    StandardOpenOption.TRUNCATE_EXISTING,
			                                    StandardOpenOption.READ,
			                                    StandardOpenOption.WRITE);
			fileIsImported = false;
		} catch(Exception e) {
			NotificationsController.showCriticalFault("Unable the create the cache file for " + name + "\n" + e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public ConnectionCamera(String name) {
		
		this.name = name;
		isMjpeg = name.startsWith("MJPEG over HTTP");
		if(!isMjpeg)
			for(Webcam cam : cameras)
				if(cam.getName().equals(name))
					camera = cam;
		
		// create the cache file
		try {
			file = FileChannel.open(pathOnDisk, StandardOpenOption.CREATE,
			                                    StandardOpenOption.TRUNCATE_EXISTING,
			                                    StandardOpenOption.READ,
			                                    StandardOpenOption.WRITE);
			fileIsImported = false;
		} catch(Exception e) {
			NotificationsController.showCriticalFault("Unable the create the cache file for " + name + "\n" + e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	/**
	 * @return List of possible telemetry connections to show the user.
	 */
	public static List<String> getNames() {
		
		return names;
		
	}

	@Override public JPanel getGui() {
		
		JPanel panel = new JPanel();
		panel.setLayout(new MigLayout("hidemode 3, gap " + Theme.padding  + ", insets 0 " + Theme.padding + " 0 0"));
			
		// resolution
		JComboBox<String> resolutionsCombobox = new JComboBox<String>();
		for(Dimension resolution : resolutions)
			resolutionsCombobox.addItem(resolution.width + " x " + resolution.height);
		resolutionsCombobox.setMinimumSize(resolutionsCombobox.getPreferredSize());
		resolutionsCombobox.addActionListener(event -> {
			String resolution = resolutionsCombobox.getSelectedItem().toString();
			for(Dimension res : resolutions)
				if(resolution.equals(res.width + " x " + res.height))
					requestedResolution = res;
		});
		resolutionsCombobox.setSelectedItem(requestedResolution.width + " x " + requestedResolution.height);
		
		// MJPEG over HTTP address
		JTextField urlTextfield = new JTextField("http://example.com:8080/video", 20);
		// crude attempt to make the default URL be the IP address of the default gateway
		String ip = ConnectionTelemetry.localIp;
		if(ip.split("\\.").length == 4)
			urlTextfield.setText("http://" + ip.substring(0, ip.lastIndexOf(".")) + ".1:8080/video");
		urlTextfield.setMinimumSize(urlTextfield.getPreferredSize());
		urlTextfield.addFocusListener(new FocusListener() {
			@Override public void focusLost(FocusEvent fe) {
				name = "MJPEG over HTTP " + urlTextfield.getText().trim();
			}
			@Override public void focusGained(FocusEvent fe) {
				urlTextfield.selectAll();
			}
		});
		
		// connections list
		JComboBox<String> connectionNamesCombobox = new JComboBox<String>();
		for(String name : ConnectionsController.getNames())
			connectionNamesCombobox.addItem(name);
		connectionNamesCombobox.setMaximumRowCount(connectionNamesCombobox.getItemCount());
		connectionNamesCombobox.setSelectedItem(name.startsWith("MJPEG over HTTP") ? "MJPEG over HTTP" : name);
		if(!connectionNamesCombobox.getSelectedItem().equals(name.startsWith("MJPEG over HTTP") ? "MJPEG over HTTP" : name)) {
			connectionNamesCombobox.addItem(name);
			connectionNamesCombobox.setSelectedItem(name);
		}
		if(ConnectionsController.importing) {
			String importName = "Importing [" + (name.startsWith("MJPEG over HTTP") ? "MJPEG over HTTP" : name) + "]";
			connectionNamesCombobox.addItem(importName);
			connectionNamesCombobox.setSelectedItem(importName);
		}
		connectionNamesCombobox.setMinimumSize(connectionNamesCombobox.getPreferredSize());
		connectionNamesCombobox.addActionListener(event -> {
			
			String newConnectionName = connectionNamesCombobox.getSelectedItem().toString();
			if(newConnectionName.equals(name.startsWith("MJPEG over HTTP") ? "MJPEG over HTTP" : name))
				return;
			
			// ignore change if the connection already exists, but allow multiple TCP/UDP/Demo Mode/MJPEG over HTTP connections
			for(Connection connection : ConnectionsController.allConnections)
				if(connection.name.equals(newConnectionName) && !newConnectionName.equals("TCP") && !newConnectionName.equals("UDP") && !newConnectionName.equals("Demo Mode") && !newConnectionName.equals("MJPEG over HTTP")) {
					connectionNamesCombobox.setSelectedItem(name);
					return;
				}

			// change to the new connection
			ConnectionsController.replaceConnection(ConnectionCamera.this, names.contains(newConnectionName) ? new ConnectionCamera(newConnectionName) :
			                                                                                                   new ConnectionTelemetry(newConnectionName));
			
		});
		
		// connect/disconnect button
		@SuppressWarnings("serial")
		JButton connectButton = new JButton("Connect") {
			@Override public Dimension getPreferredSize() { // giving this button a fixed size so the GUI lines up nicely
				return new JButton("Disconnect").getPreferredSize();
			}
		};
		if(connected)
			connectButton.setText("Disconnect");
		if(ConnectionsController.importing && ConnectionsController.realtimeImporting)
			connectButton.setText("Finish");
		if(ConnectionsController.importing && !ConnectionsController.realtimeImporting)
			connectButton.setText("Abort");
		
		connectButton.addActionListener(event -> {
			if(connectButton.getText().equals("Connect"))
				connect(true);
			else if(connectButton.getText().equals("Disconnect"))
				disconnect(null);
			else if(connectButton.getText().equals("Finish") || connectButton.getText().equals("Abort"))
				finishImportingFile();
		});
		if(ConnectionsController.importing && ConnectionsController.allConnections.size() > 1)
			connectButton.setVisible(false);
		
		// remove connection button
		JButton removeButton = new JButton(Theme.removeSymbol);
		removeButton.setBorder(Theme.narrowButtonBorder);
		removeButton.addActionListener(event -> ConnectionsController.removeConnection(ConnectionCamera.this));
		if(ConnectionsController.allConnections.size() < 2 || ConnectionsController.importing)
			removeButton.setVisible(false);
		
		// populate the panel
		panel.add(resolutionsCombobox);
		panel.add(urlTextfield);
		panel.add(connectionNamesCombobox);
		panel.add(connectButton);
		panel.add(removeButton);
		
		if(connectionNamesCombobox.getSelectedItem().toString().equals("MJPEG over HTTP")) {
			resolutionsCombobox.setVisible(false);
		} else {
			urlTextfield.setVisible(false);
		}
		
		// disable widgets if appropriate
		resolutionsCombobox.setEnabled(!ConnectionsController.importing && !connected);
		urlTextfield.setEnabled(!ConnectionsController.importing && !connected);
		connectionNamesCombobox.setEnabled(!ConnectionsController.importing && !connected);
		connectButton.setEnabled(!ConnectionsController.exporting);
		
		return panel;
		
	}

	@Override public void connect(boolean showGui) {

		if(connected)
			disconnect(null);
		
		NotificationsController.removeIfConnectionRelated();
		
		if(ConnectionsController.previouslyImported) {
			for(Connection connection : ConnectionsController.allConnections)
				connection.removeAllData();
			ConnectionsController.previouslyImported = false;
		}
		
		// don't actually connect if importing from a file
		if(ConnectionsController.importing)
			return;
		
		// if we previously imported a file, close it and create a new cache file
		// it's also possible that the file is closed if we imported a CSV file without importing the corresponding camera files.
		if(fileIsImported || !file.isOpen()) {
			try {
				file.close();
				framesIndex.clear();
				CommunicationView.instance.redraw();
				file = FileChannel.open(pathOnDisk, StandardOpenOption.CREATE,
				                                    StandardOpenOption.TRUNCATE_EXISTING,
				                                    StandardOpenOption.READ,
				                                    StandardOpenOption.WRITE);
				fileIsImported = false;
			} catch(Exception e) {
				NotificationsController.showCriticalFault("Unable the create the cache file for " + name + "\n" + e.getMessage());
				e.printStackTrace();
				return;
			}
		}
		
		// connect to the camera
		if(isMjpeg) {
		
			receiverThread = new Thread(() -> {
				
				try {
					
					// connect to the stream
					liveImage = new GLframe(null, true, 1, 1, "[connecting...]", 0);
					URLConnection stream = new URL(name.substring(16)).openConnection(); // trim off leading "MJPEG over HTTP "
					stream.setConnectTimeout(5000);
					stream.setReadTimeout(5000);
					stream.connect();
					InputStream is = stream.getInputStream();
					
					StringWriter buffer = null;
					int i = 0;
					
					connected = true;
					CommunicationView.instance.redraw();
					
					if(ChartsController.getCharts().isEmpty())
						NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
					
					// enter an infinite loop that gets the frames
					while(true) {
						
						// wait for (and skip over) the content-type and content-length fields
						buffer = new StringWriter();
						while(true) {
							i = is.read();
							if(i == -1)
								throw new Exception();
							if(buffer.getBuffer().length() > 5000)
								throw new ConnectException();
							buffer.write(i);
							String text = buffer.toString().toLowerCase();
							if(text.contains("content-type: image/jpeg") && text.endsWith("content-length: "))
								break;
						}
						
						// get the content-length
						buffer = new StringWriter();
						while(true) {
							i = is.read();
							if(i == -1)
								throw new Exception();
							if(buffer.getBuffer().length() > 5000)
								throw new ConnectException();
							buffer.write(i);
							if(buffer.toString().endsWith("\r\n\r\n"))
								break;
						}
						int contentLength = Integer.parseInt(buffer.toString().trim());
						
						// get the jpeg bytes
						byte[] jpegBytes = new byte[contentLength];
						int bytesReceivedCount = 0;
						while(bytesReceivedCount < contentLength)
							bytesReceivedCount += is.read(jpegBytes, bytesReceivedCount, contentLength - bytesReceivedCount);
						
						// save and show the jpeg
						long timestamp = System.currentTimeMillis();
						saveJpeg(jpegBytes, timestamp);
						showJpeg(jpegBytes, timestamp);
						
						// stop if requested
						if(!connected)
							throw new InterruptedException();
						
					}
					
				} catch (Exception e) {
					
					while(liveJpegThreads > 0); // wait
					
					     if(e instanceof ConnectException)       liveImage = new GLframe(null, true, 1, 1, "[invalid mjpeg stream]", 0);
					else if(e instanceof SocketTimeoutException) liveImage = new GLframe(null, true, 1, 1, "[unable to connect]",    0);
					else if(e instanceof InterruptedException)   liveImage = new GLframe(null, true, 1, 1, "[stopped]",              0);
					else                                         liveImage = new GLframe(null, true, 1, 1, "[stream ended]",         0);
					
					connected = false;
					CommunicationView.instance.redraw();
					
				}
				
			});
			
			receiverThread.setName("MJPEG Camera Thread for " + name);
			receiverThread.start();
			
		} else {
		
			receiverThread = new Thread(() -> {
				
				liveImage = new GLframe(null, true, 1, 1, "[connecting...]", 0);
				
				// check if the camera exists
				if(camera == null) {
					liveImage = new GLframe(null, true, 1, 1, "[camera does not exist]", 0);
					return;
				}
				
				// check if the camera is already being used
				if(camera.isOpen()) {
					liveImage = new GLframe(null, true, 1, 1, "[camera already in use]", 0);
					return;
				}
				
				try {
					// the webcam library requires the requested resolution to be one of the predefined "custom view sizes"
					// so we must predefine all of the options shown by WidgetCamera
					camera.setCustomViewSizes(resolutions);
					
					// request a resolution, open the camera, then get the actual resolution
					camera.setViewSize(requestedResolution);
					camera.open();
					Dimension resolution = camera.getViewSize();
					
					connected = true;
					CommunicationView.instance.redraw();
					
					if(ChartsController.getCharts().isEmpty())
						NotificationsController.showHintUntil("Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.", () -> !ChartsController.getCharts().isEmpty(), true);
		
					// enter an infinite loop that acquires images
					int frameCount = framesIndex.size();
					while(true) {
						
						// acquire a new image
						ByteBuffer buffer = Buffers.newDirectByteBuffer(resolution.width * resolution.height * 3);
						camera.getImageBytes(buffer);
						
						// save and show the image
						long timestamp = System.currentTimeMillis();
						saveImage(frameCount, buffer, resolution, timestamp);
						showImage(buffer, resolution, timestamp);
						frameCount++;
						
						// stop if requested
						if(!connected)
							throw new InterruptedException();
						
					}
					
				} catch(Exception e) {
					
					while(liveJpegThreads > 0); // wait
					camera.close();
					
					     if(e instanceof WebcamException)     liveImage = new GLframe(null, true, 1, 1, "[unable to connect]", 0);
					else if(e instanceof WebcamLockException) liveImage = new GLframe(null, true, 1, 1, "[unable to connect]", 0);
					else						              liveImage = new GLframe(null, true, 1, 1, "[stopped]", 0);
					
					connected = false;
					CommunicationView.instance.redraw();
					
				}
				
			});
			
			receiverThread.setName("Camera Thread for " + name);
			receiverThread.start();
			
		}
		
	}
	
	@Override public void disconnect(String errorMessage) {
		
		super.disconnect(errorMessage);
		liveImage = new GLframe(null, true, 1, 1, "[stopped]", 0);
		
	}
	
	/**
	 * @return    Approximate number of bytes that can be exported to a file. If connected, more bytes may be ready by the time exporting begins.
	 */
	public long getFileSize() {
		
		int frameCount = getSampleCount();
		return framesIndex.get(frameCount - 1).offset + framesIndex.get(frameCount - 1).length;
		
	}

	@Override public void dispose() {
		
		if(connected)
			disconnect(null);
		
		// remove charts containing the dataset
		List<PositionedChart> chartsToRemove = new ArrayList<PositionedChart>();
		for(PositionedChart chart : ChartsController.getCharts()) {
			if(chart instanceof OpenGLCameraChart)
				if(((OpenGLCameraChart) chart).camera == this)
					chartsToRemove.add(chart);
		}
		for(PositionedChart chart : chartsToRemove)
			ChartsController.removeChart(chart);
		
		try {
			file.close();
			Files.deleteIfExists(pathOnDisk);
			framesIndex.clear();
			CommunicationView.instance.redraw();
		} catch(Exception e) {
			NotificationsController.showCriticalFault("Unable the delete the cache file for " + name + "\n" + e.getMessage());
			e.printStackTrace();
		}
		
	}

	@Override public void removeAllData() {
		
		if(!fileIsImported) {
			try {
				file.truncate(0);
				file.force(true);
				framesIndex.clear();
				CommunicationView.instance.redraw();
			} catch(Exception e) {
				NotificationsController.showCriticalFault("Unable the clear the cache file for " + name + "\n" + e.getMessage());
				e.printStackTrace();
			}
		}
		
		OpenGLChartsView.instance.switchToLiveView();
		
	}
	
	/**
	 * Gets the most recent image.
	 * 
	 * @return    A GLframe object containing the most recent image and related information.
	 */
	public GLframe getLiveImage() {
		
		return liveImage;
		
	}
	
	private static final SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	/**
	 * Gets the closest image at or just before a certain moment in time.
	 * 
	 * @param timestamp    The moment in time (milliseconds since 1970-01-01.)
	 * @return             The image and related information, as a GLframe object.
	 */
	public GLframe getImageAtOrBeforeTimestamp(long timestamp) {
		
		// give up if there's no images
		if(framesIndex.isEmpty())
			return new GLframe(null, true, 1, 1, "[no image]", 0);
		
		// determine the frame index
		int frameIndex = framesIndex.size() - 1;
		long frameTimestamp = framesIndex.get(frameIndex).timestamp;
		
		for(int i = frameIndex - 1; i >= 0; i--) {
			long timestamp2 = framesIndex.get(i).timestamp;
			if(timestamp2 <= timestamp) {
				frameIndex = i;
				frameTimestamp = timestamp2;
				break;
			}
		}
		
		// give up if there's no frame before the specified timestamp
		if(frameTimestamp > timestamp)
			return new GLframe(null, true, 1, 1, "[no image]", 0);
		
		// return cached image if appropriate
		if(frameTimestamp == oldImage.timestamp)
			return oldImage;
		
		// obtain and decompress the jpeg
		FrameInfo info = framesIndex.get(frameIndex);
		byte[] jpegBytes = new byte[info.length];
		try {
			file.read(ByteBuffer.wrap(jpegBytes), info.offset);
		} catch(Exception e) {
			e.printStackTrace();
			return new GLframe(null, true, 1, 1, "[error reading image from disk]", 0);
		}
		
		int width = 0;
		int height = 0;
		byte[] bgrBytes = null;
		String label = String.format("%s (%s)", name, timestampFormatter.format(new Date(info.timestamp)));
		
		try {
			// try to use the libjpeg-turbo library
			TJDecompressor tjd = new TJDecompressor(jpegBytes);
			width = tjd.getWidth();
			height = tjd.getHeight();
			bgrBytes = new byte[width * height * 3];
			tjd.decompress(bgrBytes, 0, 0, width, 0, height, TJ.PF_BGR, 0);
			tjd.close();
			oldImage = new GLframe(bgrBytes, true, width, height, label, info.timestamp);
			return oldImage;
		} catch(Error | Exception e) {
			// fallback to the JRE library
			try {
				BufferedImage bi = ImageIO.read(new ByteArrayInputStream(jpegBytes));
				width = bi.getWidth();
				height = bi.getHeight();
				bgrBytes = ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
				oldImage = new GLframe(bgrBytes, true, width, height, label, info.timestamp);
				return oldImage;
			} catch(Exception e2) {
				e.printStackTrace();
				return new GLframe(null, true, 1, 1, "[error decoding image]", 0);
			}
		}
		
	}

	@Override public void importSettings(ConnectionsController.QueueOfLines lines) throws AssertionError {

		ChartUtils.parseExact(lines.remove(), "connection type = Camera");
		
		String cameraName = ChartUtils.parseString (lines.remove(), "camera name = %s");
		if(cameraName.length() < 1)
			throw new AssertionError("Invalid camera name.");
		name = cameraName;
		isMjpeg = name.startsWith("MJPEG over HTTP");
		if(!isMjpeg)
			for(Webcam cam : cameras)
				if(cam.getName().equals(name))
					camera = cam;
		
		String resolution = ChartUtils.parseString (lines.remove(), "requested resolution = %s");
		String[] tokens = resolution.split(" x ");
		if(tokens.length != 2)
			throw new AssertionError("Invalid camera resolution.");
		
		Dimension d = null;
		for(Dimension dim : resolutions)
			if(dim.width == Integer.parseInt(tokens[0]) && dim.height == Integer.parseInt(tokens[1]))
				d = dim;
		if(d == null)
			throw new AssertionError("Invalid camera resolution.");
		requestedResolution = d;
		
		CommunicationView.instance.redraw();
		
	}

	@Override public void exportSettings(PrintWriter file) {

		file.println("\tconnection type = Camera");
		file.println("\tcamera name = " + name);
		file.println("\trequested resolution = " + requestedResolution.width + " x " + requestedResolution.height);
		
	}

	@Override public long readFirstTimestamp(String path) {
		
		long timestamp = Long.MAX_VALUE;
		
		try {
			timestamp = new Mkv().getFramesIndex(path).getLong();
		} catch(AssertionError | Exception e) {
			NotificationsController.showFailureForMilliseconds("Error while parsing the MKV file " + path + "\n" + e.getMessage(), 5000, true);
		}
		
		return timestamp;
		
	}

	@Override public long getTimestamp(int sampleNumber) {

		return framesIndex.get(sampleNumber).timestamp;
		
	}
	
	@Override public int getSampleCount() {
		
		return framesIndex.size();
		
	}

	/**
	 * Reads frames from an MKV file instead of a live camera connection.
	 * 
	 * @param path                  Path to the file.
	 * @param firstTimestamp        Timestamp when the first sample from ANY connection was acquired. This is used to allow importing to happen in real time.
	 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
	 */
	@Override public void importDataFile(String path, long firstTimestamp, AtomicLong completedByteCount) {

		// remove any existing frames and the cache file
		try {
			removeAllData();
			file.close();
			Files.deleteIfExists(pathOnDisk);
		} catch(Exception e) {
			NotificationsController.showCriticalFault("Unable to delete the cache file for " + name + "\n" + e.getMessage());
			e.printStackTrace();
		}
		
		// open the MKV file and read the frames index from it
		ByteBuffer framesIndexBuffer;
		try {
			framesIndexBuffer = new Mkv().getFramesIndex(path);
			file = FileChannel.open(Paths.get(path), StandardOpenOption.READ);
			fileIsImported = true;
		} catch(Exception e) {
			NotificationsController.showFailureForMilliseconds("Unable to import the MKV file for " + name + "\n" + e.getMessage(), 5000, false);
			e.printStackTrace();
			return;
		}
		
		int frameCount = framesIndexBuffer.capacity() / 20;
		
		receiverThread = new Thread(() -> {
			try {
				
				connected = true;
				CommunicationView.instance.redraw();
				
				long startTimeThread = System.currentTimeMillis();
				
				for(int i = 0; i < frameCount; i++) {
					
					long timestamp = framesIndexBuffer.getLong();
					long offset = framesIndexBuffer.getLong();
					int length = framesIndexBuffer.getInt();
					
					if(ConnectionsController.realtimeImporting) {
						if(Thread.interrupted()) {
							ConnectionsController.realtimeImporting = false;
						} else {
							long delay = (timestamp - firstTimestamp) - (System.currentTimeMillis() - startTimeThread);
							if(delay > 0)
								try { Thread.sleep(delay); } catch(Exception e) { ConnectionsController.realtimeImporting = false; }
						}
					} else if(Thread.interrupted()) {
						break; // not real-time, and interrupted again, so abort
					}
					
					framesIndex.add(new FrameInfo(timestamp, offset, length));
					if(getSampleCount() == 1)
						CommunicationView.instance.redraw();
					completedByteCount.addAndGet(length);
				}
				
				// done
				SwingUtilities.invokeLater(() -> disconnect(null));
				
			} catch(Exception e) {
				
				NotificationsController.showFailureForMilliseconds("Error while importing the MKV file for " + name + "\n" + e.getMessage(), 5000, false);
				e.printStackTrace();
				SwingUtilities.invokeLater(() -> disconnect(null));
				return;
				
			}
		});
		
		receiverThread.setPriority(Thread.MAX_PRIORITY);
		receiverThread.setName("MKV File Import Thread");
		receiverThread.start();
		
	}

	/**
	 * Saves all images to a MJPG file, and saves the corresponding index data to a BIN file.
	 * 
	 * @param path                  Path to the file.
	 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
	 */
	@Override public void exportDataFile(String path, AtomicLong completedByteCount) {
		
		new Mkv().exportFile(framesIndex.size(), path, completedByteCount);
		
	}
	
	/**
	 * Appends a new image to the dataset.
	 * 
	 * @param jpegBytes    The image, as a JPEG.
	 * @param timestamp    When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void saveJpeg(byte[] jpegBytes, long timestamp) {
		
		try {
			framesIndex.add(new FrameInfo(timestamp, file.size(), jpegBytes.length));
			if(getSampleCount() == 1)
				CommunicationView.instance.redraw();
			file.write(ByteBuffer.wrap(jpegBytes));
			file.force(true);
		} catch(Exception e) {
			NotificationsController.showCriticalFault("Unable the save to the cache file for " + name + "\n" + e.getMessage());
			e.printStackTrace();
			return;
		}
		
	}
	
	/**
	 * Spawns a new thread that will decode a JPEG image and update the liveImage object.
	 * If there is a backlog of images to decode, this image may be skipped.
	 * 
	 * @param jpegBytes    The image to decode.
	 * @param timestamp    When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void showJpeg(byte[] jpegBytes, long timestamp) {
		
		// skip this frame if backlogged
		if(liveJpegThreads > 5)
			return;
		
		new Thread(() -> {
			try {
				
				liveJpegThreads++;
				int width = 0;
				int height = 0;
				byte[] bgrBytes = null;
				
				try {
					// try to use the libjpeg-turbo library
					TJDecompressor tjd = new TJDecompressor(jpegBytes);
					width = tjd.getWidth();
					height = tjd.getHeight();
					bgrBytes = new byte[width * height * 3];
					tjd.decompress(bgrBytes, 0, 0, width, 0, height, TJ.PF_BGR, 0);
					tjd.close();
				} catch(Error | Exception e) {
					// fallback to the JRE library
					BufferedImage bi = ImageIO.read(new ByteArrayInputStream(jpegBytes));
					width = bi.getWidth();
					height = bi.getHeight();
					bgrBytes = ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
				}
				
				// update the liveImage object
				int frameCount = framesIndex.size();
				double fps = 0;
				if(frameCount > 30)
					fps = 30000.0 / (double) (framesIndex.get(frameCount - 1).timestamp - framesIndex.get(frameCount - 30).timestamp);
				String label = String.format("%s (%d x %d, %01.1f FPS)", name, width, height, fps);
				liveImage = new GLframe(bgrBytes, true, width, height, label, timestamp);
				
				liveJpegThreads--;
				
			} catch(Exception e) {
				
				NotificationsController.showFailureForMilliseconds("Unable to decode one of the frames from " + name + "\n" + e.getMessage(), 5000, true);
				e.printStackTrace();
				liveJpegThreads--;
				
			}
		}).start();
		
	}
	
	/**
	 * Spawns a new thread that will encode a raw image into a JPEG and store that in the dataset.
	 * 
	 * @param frameNumber    The frame number.
	 * @param image          The image.
	 * @param resolution     Size of the image, in pixels.
	 * @param timestamp      When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void saveImage(int frameNumber, ByteBuffer image, Dimension resolution, long timestamp) {
		
		byte[] bytes = new byte[image.capacity()];
		image.get(bytes);
		liveJpegThreads++;
		
		new Thread(() -> {
			
			byte[] jpegBytes = null;
			int jpegBytesLength = 0;
			
			try {
				// try to use the libjpeg-turbo library
				TJCompressor tjc = new TJCompressor(bytes, 0, 0, resolution.width, 0, resolution.height, TJ.PF_RGB);
				tjc.setJPEGQuality(80);
				tjc.setSubsamp(TJ.SAMP_422);
				jpegBytes = tjc.compress(0);
				jpegBytesLength = tjc.getCompressedSize();
				tjc.close();
			} catch(Error | Exception e) {
				// fallback to the JRE library
				try {
					// convert rgb to bgr
					for(int i = 0; i < bytes.length; i +=3) {
						byte red  = bytes[i];
						byte blue = bytes[i+2];
						bytes[i]   = blue;
						bytes[i+2] = red;
					}
					BufferedImage bi = new BufferedImage(resolution.width, resolution.height, BufferedImage.TYPE_3BYTE_BGR);
					bi.setData(Raster.createRaster(bi.getSampleModel(), new DataBufferByte(bytes, bytes.length), new Point()));
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ImageIO.write(bi, "jpg", baos);
					jpegBytes = baos.toByteArray();
					jpegBytesLength = jpegBytes.length;
					baos.close();
				} catch(Exception e2) {
					NotificationsController.showFailureForMilliseconds("Unable to encode one of the frames from " + name + "\n" + e.getMessage(), 5000, true);
					e.printStackTrace();
					liveJpegThreads--;
					return;
				}
			}
			
			// wait for previous encoding threads to finish before inserting this frame
			while(framesIndex.size() < frameNumber);
			
			// save to disk
			try {
				framesIndex.add(new FrameInfo(timestamp, file.size(), jpegBytesLength));
				if(getSampleCount() == 1)
					CommunicationView.instance.redraw();
				file.write(ByteBuffer.wrap(jpegBytes, 0, jpegBytesLength));
				file.force(true);
			} catch (Exception e) {
				NotificationsController.showCriticalFault("Unable to save one of the frames from " + name + "\n" + e.getMessage());
				e.printStackTrace();
				liveJpegThreads--;
				return;
			}
			
			liveJpegThreads--;
			
		}).start();
		
	}
	
	/**
	 * Updates the liveImage object with a new image.
	 * 
	 * @param image         The image.
	 * @param resolution    Size of the image, in pixels.
	 * @param timestamp     When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void showImage(ByteBuffer image, Dimension resolution, long timestamp) {
		
		int frameCount = framesIndex.size();
		double fps = 0;
		if(frameCount > 30)
			fps = 30000.0 / (double) (framesIndex.get(frameCount - 1).timestamp - framesIndex.get(frameCount - 30).timestamp);
		String label = String.format("%s (%d x %d, %01.1f FPS)", name, resolution.width, resolution.height, fps);
		
		image.rewind();
		liveImage = new GLframe(image, resolution.width, resolution.height, label, timestamp);
		
	}
	
	/**
	 * Frames to be shown on screen are stored in GLframe objects.
	 */
	public static class GLframe {
		ByteBuffer buffer;
		boolean isBgr; // if buffer uses the BGR or RGB pixel format
		int width;
		int height;
		String label;
		long timestamp;
		
		public GLframe(byte[] bytes, boolean isBgr, int width, int height, String label, long timestamp) {
			this.buffer = Buffers.newDirectByteBuffer(width * height * 3);
			this.isBgr = isBgr;
			this.width = width;
			this.height = height;
			this.label = label;
			this.timestamp = timestamp;
			if(bytes != null) {
				buffer.put(bytes);
				buffer.rewind();
			} else {
				byte black = 0;
				for(int i = 0; i < width*height*3; i++)
					buffer.put(black);
				buffer.rewind();
			}
		}
		
		public GLframe(ByteBuffer bytes, int width, int height, String label, long timestamp) {
			this.buffer = bytes;
			this.isBgr = false;
			this.width = width;
			this.height = height;
			this.label = label;
			this.timestamp = timestamp;
		}
	}
	
	private class Mkv {
		
		private FileChannel inputFile;
		private FileChannel outputFile;
		private Stack<Map.Entry<Long, Long>> exportTagSizes = new Stack<Map.Entry<Long, Long>>(); // keys are byte offsets into the MKV file
		                                                                                          // values are tag sizes (byte counts, not EBML encoded.)
		private Stack<Long> importTagSizes = new Stack<Long>();
		
		// EBML tags
		private final byte[] EBML                 = new byte[] {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
		private final byte[] EBML_VERSION         = new byte[] {(byte) 0x42, (byte) 0x86};
		private final byte[] EBML_READ_VERSION    = new byte[] {(byte) 0x42, (byte) 0xF7};
		private final byte[] EBML_MAX_ID_LENGTH   = new byte[] {(byte) 0x42, (byte) 0xF2};
		private final byte[] EBML_MAX_SIZE_LENGTH = new byte[] {(byte) 0x42, (byte) 0xF3};
		private final byte[] DOCTYPE              = new byte[] {(byte) 0x42, (byte) 0x82};
		private final byte[] DOCTYPE_VERSION      = new byte[] {(byte) 0x42, (byte) 0x87};
		private final byte[] DOCTYPE_READ_VERSION = new byte[] {(byte) 0x42, (byte) 0x85};
		private final byte[] SEGMENT              = new byte[] {(byte) 0x18, (byte) 0x53, (byte) 0x80, (byte) 0x67};
		private final byte[] SEEK_HEAD            = new byte[] {(byte) 0x11, (byte) 0x4D, (byte) 0x9B, (byte) 0x74};
		private final byte[] SEEK                 = new byte[] {(byte) 0x4D, (byte) 0xBB};
		private final byte[] SEEK_ID              = new byte[] {(byte) 0x53, (byte) 0xAB};
		private final byte[] SEEK_POSITION        = new byte[] {(byte) 0x53, (byte) 0xAC};
		private final byte[] INFO                 = new byte[] {(byte) 0x15, (byte) 0x49, (byte) 0xA9, (byte) 0x66};
		private final byte[] TIMPSTAMP_SCALE      = new byte[] {(byte) 0x2A, (byte) 0xD7, (byte) 0xB1};
		private final byte[] MUXING_APP           = new byte[] {(byte) 0x4D, (byte) 0x80};
		private final byte[] WRITING_APP          = new byte[] {(byte) 0x57, (byte) 0x41};
		private final byte[] DURATION             = new byte[] {(byte) 0x44, (byte) 0x89};
		private final byte[] TRACKS               = new byte[] {(byte) 0x16, (byte) 0x54, (byte) 0xAE, (byte) 0x6B};
		private final byte[] TRACK_ENTRY          = new byte[] {(byte) 0xAE};
		private final byte[] TRACK_NUMBER         = new byte[] {(byte) 0xD7};
		private final byte[] TRACK_UID            = new byte[] {(byte) 0x73, (byte) 0xC5};
		private final byte[] TRACK_TYPE           = new byte[] {(byte) 0x83};
		private final byte[] FLAG_LACING          = new byte[] {(byte) 0x9C};
		private final byte[] CODEC_ID             = new byte[] {(byte) 0x86};
		private final byte[] VIDEO                = new byte[] {(byte) 0xE0};
		private final byte[] FLAG_INTERLACED      = new byte[] {(byte) 0x9A};
		private final byte[] FIELD_ORDER          = new byte[] {(byte) 0x9D};
		private final byte[] PIXEL_WIDTH          = new byte[] {(byte) 0xB0};
		private final byte[] PIXEL_HEIGHT         = new byte[] {(byte) 0xBA};
		private final byte[] ATTACHMENTS          = new byte[] {(byte) 0x19, (byte) 0x41, (byte) 0xA4, (byte) 0x69};
		private final byte[] ATTACHED_FILE        = new byte[] {(byte) 0x61, (byte) 0xA7};
		private final byte[] FILE_DESCRIPTION     = new byte[] {(byte) 0x46, (byte) 0x7E};
		private final byte[] FILE_NAME            = new byte[] {(byte) 0x46, (byte) 0x6E};
		private final byte[] MIME_TYPE            = new byte[] {(byte) 0x46, (byte) 0x60};
		private final byte[] FILE_UID             = new byte[] {(byte) 0x46, (byte) 0xAE};
		private final byte[] FILE_DATA            = new byte[] {(byte) 0x46, (byte) 0x5C};
		private final byte[] CLUSTER              = new byte[] {(byte) 0x1F, (byte) 0x43, (byte) 0xB6, (byte) 0x75};
		private final byte[] TIMESTAMP            = new byte[] {(byte) 0xE7};
		private final byte[] CUES                 = new byte[] {(byte) 0x1C, (byte) 0x53, (byte) 0xBB, (byte) 0x6B};
		private final byte[] CUE_POINT            = new byte[] {(byte) 0xBB};
		private final byte[] CUE_TIME             = new byte[] {(byte) 0xB3};
		private final byte[] CUE_TRACK_POSITIONS  = new byte[] {(byte) 0xB7};
		private final byte[] CUE_TRACK            = new byte[] {(byte) 0xF7};
		private final byte[] CUE_CLUSTER_POSITION = new byte[] {(byte) 0xF1};
		
		/**
		 * Exports the acquired images to an MKV file (containing an MJPEG video stream with no audio.)
		 * 
		 * @param frameCount            How many frames to export.
		 * @param path                  Destination file path.
		 * @param completedByteCount    Variable to increment as progress is made (this is periodically queried by a progress bar.)
		 */
		public void exportFile(int frameCount, String path, AtomicLong completedByteCount) {
			
			// dev notes:
			//
			// use "mkvalidator.exe file.mkv" to test files:
			// https://www.matroska.org/downloads/mkvalidator.html
			//
			// use "mkvinfo.exe file.mkv -a -p" to print a tree interpretation of a file:
			// https://www.matroska.org/downloads/mkvtoolnix.html
			//
			// information about the MKV file format:
			// https://matroska-org.github.io/libebml/specs.html
			// https://www.matroska.org/technical/diagram.html
			// https://www.matroska.org/technical/elements.html
			
			// sanity check
			if(frameCount < 1 || framesIndex.size() < frameCount)
				return;
			
			long duration = framesIndex.get(frameCount - 1).timestamp - framesIndex.get(0).timestamp;
			path += ".mkv";
			
			// general purpose buffer used for several things
			ByteBuffer buffer = Buffers.newDirectByteBuffer(frameCount * 20);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			// write the file
			try {
			
				outputFile = FileChannel.open(Paths.get(path), StandardOpenOption.CREATE,
				                                               StandardOpenOption.TRUNCATE_EXISTING,
				                                               StandardOpenOption.WRITE);
				
				// trying to visually separate the Java logic from the MKV logic, so bear with the weird whitespace below
				// --- Java logic here ----------- | --- MKV logic here --------------------------------------------------
				                                     openTag(EBML);
				                                         putTag(EBML_VERSION, 1);
				                                         putTag(EBML_READ_VERSION, 1);
				                                         putTag(EBML_MAX_ID_LENGTH, 4);
				                                         putTag(EBML_MAX_SIZE_LENGTH, 8);
				                                         putTag(DOCTYPE, "matroska".getBytes());
				                                         putTag(DOCTYPE_VERSION, 4);
				                                         putTag(DOCTYPE_READ_VERSION, 2);
				                                     closeTag();
				
				long segmentContentsOffset =         openTag(SEGMENT);
				                                         openTag(SEEK_HEAD);
				                                             openTag(SEEK);
				                                                 putTag(SEEK_ID, INFO);
				long infoSeekPositionOffset =                    putTag(SEEK_POSITION, 0xFFFFFFFFFFFFFFFFL); // placeholder
				                                             closeTag();
				                                             openTag(SEEK);
				                                                 putTag(SEEK_ID, TRACKS);
				long tracksSeekPositionOffset =                  putTag(SEEK_POSITION, 0xFFFFFFFFFFFFFFFFL); // placeholder
				                                             closeTag();
				                                             openTag(SEEK);
				                                                 putTag(SEEK_ID, ATTACHMENTS);
				long attachmentsSeekPositionOffset =             putTag(SEEK_POSITION, 0xFFFFFFFFFFFFFFFFL); // placeholder
				                                             closeTag();
				                                             openTag(SEEK);
				                                                 putTag(SEEK_ID, CUES);
				long cuesSeekPositionOffset =                    putTag(SEEK_POSITION, 0xFFFFFFFFFFFFFFFFL); // placeholder
				                                             closeTag();
				long infoOffset =                        closeTag();
				                                         openTag(INFO);
				                                             putTag(TIMPSTAMP_SCALE, 1000000);
				                                             putTag(MUXING_APP, Main.versionString.getBytes());
				                                             putTag(WRITING_APP, Main.versionString.getBytes());
				                                             putTag(DURATION, buffer.putFloat((float) duration).flip());
				long tracksOffset =                      closeTag();
				                                         openTag(TRACKS);
				                                             openTag(TRACK_ENTRY);
				                                                 putTag(TRACK_NUMBER, 1);
				                                                 putTag(TRACK_UID, 1);
				                                                 putTag(TRACK_TYPE, 1);
				                                                 putTag(FLAG_LACING, 0);
				                                                 putTag(CODEC_ID, "V_MJPEG".getBytes());
				                                                 openTag(VIDEO);
				                                                     putTag(FLAG_INTERLACED, 2);
				                                                     putTag(FIELD_ORDER, 0);
				                                                     putTag(PIXEL_WIDTH,  liveImage.width);
				                                                     putTag(PIXEL_HEIGHT, liveImage.height);
				                                                 closeTag();
				                                             closeTag();
				long attachmentsOffset =                 closeTag();
				                                         openTag(ATTACHMENTS);
				                                             openTag(ATTACHED_FILE);
				                                                 putTag(FILE_DESCRIPTION, "frames index data".getBytes());
				                                                 putTag(FILE_NAME, "index.bin".getBytes());
				                                                 putTag(MIME_TYPE, "application/x-telemetryviewer".getBytes());
				                                                 putTag(FILE_UID, 1);
				long framesIndexOffset =                         putTag(FILE_DATA, buffer.limit(frameCount * 20).position(frameCount * 20).flip()); // placeholder
				                                             closeTag();
				                                         closeTag();
				
				long firstTimestamp = framesIndex.get(0).timestamp;
					
				// using one Cluster per frame, and one CuePoint per Cluster
				long[] clusterOffset  = new long[frameCount];
				long[] frameTimestamp = new long[frameCount];
				int frameN = 0;
				long cuesOffset = 0;
				buffer.flip();
				while(frameN < frameCount) {
					FrameInfo frame = framesIndex.get(frameN);
					clusterOffset[frameN] = outputFile.size() - segmentContentsOffset;
					frameTimestamp[frameN] = frame.timestamp - firstTimestamp;
					
					                                     openTag(CLUSTER);
					                                         putTag(TIMESTAMP, frameTimestamp[frameN]);
					long mkvFileOffset =                     putSimpleBlock(0, frame.offset, frame.length);
					cuesOffset =                         closeTag();
					
					buffer.putLong(frame.timestamp);
					buffer.putLong(mkvFileOffset);
					buffer.putInt(frame.length);
					
					completedByteCount.addAndGet(frame.length);
					frameN++;
					if(frameN % 30 == 0)
						outputFile.force(true);
				}
					
				                                         openTag(CUES);
				frameN = 0;
				while(frameN < frameCount) {
				                                             openTag(CUE_POINT);
				                                                 putTag(CUE_TIME, frameTimestamp[frameN]);
				                                                 openTag(CUE_TRACK_POSITIONS);
				                                                     putTag(CUE_TRACK, 1);
				                                                     putTag(CUE_CLUSTER_POSITION, clusterOffset[frameN++]);
				                                                 closeTag();
				                                             closeTag();
				}
				                                         closeTag();
				                                     closeTag();
				
				// update the placeholders for framesIndex and SeekHead
				outputFile.write(buffer.flip(),                                                           framesIndexOffset);
				outputFile.write(buffer.flip().putLong(infoOffset        - segmentContentsOffset).flip(), infoSeekPositionOffset);
				outputFile.write(buffer.flip().putLong(tracksOffset      - segmentContentsOffset).flip(), tracksSeekPositionOffset);
				outputFile.write(buffer.flip().putLong(attachmentsOffset - segmentContentsOffset).flip(), attachmentsSeekPositionOffset);
				outputFile.write(buffer.flip().putLong(cuesOffset        - segmentContentsOffset).flip(), cuesSeekPositionOffset);
				
				// done
				outputFile.close();
			
			} catch(IOException e) {
				
				NotificationsController.showFailureForMilliseconds("Error while exporting file " + path + "\n" + e.getMessage(), 5000, false);
				e.printStackTrace();
				try { outputFile.close(); } catch(Exception e2) { }
				
			}
			
		}
		
		/**
		 * Imports the frames index from an MKV file that was previously exported from TelemetryViewer.
		 * This is NOT a general-purpose importer, it is only intended to be used with files generated by TelemetryViewer.
		 */
		public ByteBuffer getFramesIndex(String path) throws AssertionError, IOException {
			
			inputFile = FileChannel.open(Paths.get(path), StandardOpenOption.READ);
			
			// trying to visually separate the Java logic from the MKV logic, so bear with the weird whitespace below
			// --- Java logic here ----------- | --- MKV logic here --------------------------------------------------
			                                     assertTagOpened(EBML);
			                                         assertTagFound(EBML_VERSION, 1);
			                                         assertTagFound(EBML_READ_VERSION, 1);
			                                         assertTagFound(EBML_MAX_ID_LENGTH, 4);
			                                         assertTagFound(EBML_MAX_SIZE_LENGTH, 8);
			                                         assertTagFound(DOCTYPE, "matroska".getBytes());
			                                         assertTagFound(DOCTYPE_VERSION, 4);
			                                         assertTagFound(DOCTYPE_READ_VERSION, 2);
			                                     assertTagClosed();
			                                     
			                                     assertTagOpened(SEGMENT);
			                                         assertTagOpened(SEEK_HEAD); // 168
			                                             assertTagOpened(SEEK); // 10
			                                                 assertTagFound(SEEK_ID, INFO); // 14
			                                                 assertTagFound(SEEK_POSITION); // 18
			                                             assertTagClosed();
			                                             assertTagOpened(SEEK);
			                                                 assertTagFound(SEEK_ID, TRACKS);
			                                                 assertTagFound(SEEK_POSITION);
			                                             assertTagClosed();
			                                             assertTagOpened(SEEK);
			                                                 assertTagFound(SEEK_ID, ATTACHMENTS);
			                                                 assertTagFound(SEEK_POSITION);
			                                             assertTagClosed();
			                                             assertTagOpened(SEEK);
			                                                 assertTagFound(SEEK_ID, CUES);
			                                                 assertTagFound(SEEK_POSITION);
			                                             assertTagClosed();
			                                         assertTagClosed();
			                                         assertTagOpened(INFO);
			                                             assertTagFound(TIMPSTAMP_SCALE, 1000000);
			                                             assertTagFound(MUXING_APP, Main.versionString.getBytes());
			                                             assertTagFound(WRITING_APP, Main.versionString.getBytes());
			                                             assertTagFound(DURATION);
			                                         assertTagClosed();
			                                         assertTagOpened(TRACKS);
			                                             assertTagOpened(TRACK_ENTRY);
			                                                 assertTagFound(TRACK_NUMBER, 1);
			                                                 assertTagFound(TRACK_UID, 1);
			                                                 assertTagFound(TRACK_TYPE, 1);
			                                                 assertTagFound(FLAG_LACING, 0);
			                                                 assertTagFound(CODEC_ID, "V_MJPEG".getBytes());
			                                                 assertTagOpened(VIDEO);
			                                                     assertTagFound(FLAG_INTERLACED, 2);
			                                                     assertTagFound(FIELD_ORDER, 0);
			                                                     assertTagFound(PIXEL_WIDTH);
			                                                     assertTagFound(PIXEL_HEIGHT);
			                                                 assertTagClosed();
			                                             assertTagClosed();
			                                         assertTagClosed();
			                                         assertTagOpened(ATTACHMENTS);
			                                             assertTagOpened(ATTACHED_FILE);
			                                                 assertTagFound(FILE_DESCRIPTION, "frames index data".getBytes());
			                                                 assertTagFound(FILE_NAME, "index.bin".getBytes());
			                                                 assertTagFound(MIME_TYPE, "application/x-telemetryviewer".getBytes());
			                                                 assertTagFound(FILE_UID, 1);
			ByteBuffer buffer =                              assertTagFoundData(FILE_DATA);
			
			inputFile.close();
			
			return buffer;
			
		}
		
		/**
		 * "Opens" a tag. This writes the tag ID to the file, and an empty placeholder where the size will be written after the tag is "closed."
		 * 
		 * @param tagId    The tag ID.
		 * @return         File offset for where the the NEXT tag starts.
		 */
		private long openTag(byte[] tagId) throws IOException {
			
			int idByteCount = tagId.length;
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			buffer.put(tagId); // tag ID
			buffer.putLong(0); // placeholder for tag size
			outputFile.write(buffer.flip());
			
			// if any parent tags exist, update their sizes
			if(!exportTagSizes.isEmpty())
				for(Map.Entry<Long, Long> entry : exportTagSizes)
					entry.setValue(entry.getValue() + idByteCount + 8);
			
			// start tracking this tag's size
			exportTagSizes.push(new SimpleEntry<Long, Long>((long) outputFile.size() - 8, 0L));
			
			return outputFile.size();
			
		}
		
		/**
		 * "Closes" a tag. This just writes the tag's size into the placeholder created when the tag was "opened."
		 * 
		 * @return    File offset for where the NEXT tag starts.
		 */
		private long closeTag() throws IOException {
			
			// get the file offset for this tag's size, and the size to write into that location
			Map.Entry<Long, Long> entry = exportTagSizes.pop();
			long offset = entry.getKey();
			long byteCount = entry.getValue();
			
			ByteBuffer buffer = Buffers.newDirectByteBuffer(8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			// tag size
			buffer.putLong(byteCount | (1L << 56L));
			
			// write to file
			outputFile.write(buffer.flip(), offset);
			
			return outputFile.size();
			
		}
		
		/**
		 * Appends a tag to the file.
		 * 
		 * @param tagId    The tag ID.
		 * @param data     The tag data, as a long.
		 * @return         File offset for where the data portion of THIS tag starts.
		 */
		private long putTag(byte[] tagId, long data) throws IOException {
			
			int idByteCount = tagId.length;
			int dataByteCount = (data & 0xFF00000000000000L) != 0 ? 8 :
			                    (data & 0x00FF000000000000L) != 0 ? 7 :
			                    (data & 0x0000FF0000000000L) != 0 ? 6 :
			                    (data & 0x000000FF00000000L) != 0 ? 5 :
			                    (data & 0x00000000FF000000L) != 0 ? 4 :
			                    (data & 0x0000000000FF0000L) != 0 ? 3 :
			                    (data & 0x000000000000FF00L) != 0 ? 2 :
			                                                        1;
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8 + dataByteCount);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			buffer.put(tagId); // tag ID
			buffer.putLong(dataByteCount | (1L << 56L)); // tag size
			
			// tag data
			for(int i = 0; i < dataByteCount; i++) {
				int bits = (int) (dataByteCount - i - 1) * 8;
				buffer.put((byte) ((data >> bits) & 0xFF));
			}
			
			// write to file
			outputFile.write(buffer.flip());
			
			// if any parent tags exist, update their sizes
			if(!exportTagSizes.isEmpty())
				for(Map.Entry<Long, Long> entry : exportTagSizes)
					entry.setValue(entry.getValue() + idByteCount + 8 + dataByteCount);
			
			return outputFile.size() - dataByteCount;
			
		}
		
		/**
		 * Appends a tag to the file.
		 * 
		 * @param tagId        The tag ID.
		 * @param dataBytes    The tag data, as a byte[].
		 */
		private void putTag(byte[] tagId, byte[] dataBytes) throws IOException {
			
			int idByteCount = tagId.length;
			int dataBytesCount = dataBytes.length;
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8 + dataBytesCount);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			buffer.put(tagId); // tag ID
			buffer.putLong(dataBytesCount | (1L << 56L)); // tag size
			buffer.put(dataBytes); // tag data
			outputFile.write(buffer.flip());
			
			// if any parent tags exist, update their sizes
			if(!exportTagSizes.isEmpty())
				for(Map.Entry<Long, Long> entry : exportTagSizes)
					entry.setValue(entry.getValue() + idByteCount + 8 + dataBytesCount);
			
		}
		
		/**
		 * Appends a tag to the file.
		 * 
		 * @param tagId        The tag ID.
		 * @param dataBytes    The tag data, as a ByteBuffer.
		 * @return             File offset for where the data portion of THIS tag starts.
		 */
		private long putTag(byte[] tagId, ByteBuffer dataBytes) throws IOException {
			
			int idByteCount = tagId.length;
			int dataBytesCount = dataBytes.limit();
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			buffer.put(tagId); // tag ID
			buffer.putLong(dataBytesCount | (1L << 56L)); // tag size
			outputFile.write(buffer.flip());
			outputFile.write(dataBytes); // tag data
			
			// if any parent tags exist, update their sizes
			if(!exportTagSizes.isEmpty())
				for(Map.Entry<Long, Long> entry : exportTagSizes)
					entry.setValue(entry.getValue() + idByteCount + 8 + dataBytesCount);
			
			return outputFile.size() - dataBytesCount;
			
		}
		
		/**
		 * Appends one frame to the file.
		 * 
		 * @param relativeTimestamp    Milliseconds since the enclosing Cluster's timestamp. This is stored as an int16.
		 * @param jpegBytes            The frame to write.
		 * @return                     File offset where the image of this tag starts.
		 */
		private long putSimpleBlock(long relativeTimestamp, long fileOffset, long imageByteCount) throws IOException {
			
			ByteBuffer buffer = Buffers.newDirectByteBuffer(13);
			buffer.order(ByteOrder.BIG_ENDIAN);
			
			// tag ID
			buffer.put((byte) 0xA3);
			
			// tag size
			long byteCount = 4 + imageByteCount; // SimpleBlock has a 4-byte header (if track number < 128)
			buffer.putLong(byteCount | (1L << 56L));
			
			// SimpleBlock header
			buffer.put((byte) 0x81); // track number, EBML encoded, but this can NOT simply be padded to 8-bytes like the tag size
			buffer.put((byte) ((relativeTimestamp >> 8) & 0xFF)); // relative timestamp (int16 relative to the enclosing cluster's timestamp)
			buffer.put((byte) ((relativeTimestamp >> 0) & 0xFF));
			buffer.put((byte) 0); // lacing option (no lacing)
			
			// write to file
			outputFile.write(buffer.flip());
			file.transferTo(fileOffset, imageByteCount, outputFile);
			
			// if any parent tags exist, update their sizes
			if(!exportTagSizes.isEmpty())
				for(Map.Entry<Long, Long> entry : exportTagSizes)
					entry.setValue(entry.getValue() + 13 + imageByteCount);
			
			return outputFile.size() - imageByteCount;
			
		}
		
		private void assertTagOpened(byte[] tagId) throws AssertionError, IOException {
			
			int idByteCount = tagId.length;
			
			// read enough of the file for this tag and its size
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// ensure the tag id matches
			for(int i = 0; i < idByteCount; i++)
				if(buffer.get(i) != tagId[i]) {
					String message = "Expected tag: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", tagId[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", buffer.get(j));
					message = message.trim();
					throw new AssertionError(message);
				}
			
			// if any parent tags exist, update their sizes
			if(!importTagSizes.isEmpty())
				for(int i = 0; i < importTagSizes.size(); i++)
					importTagSizes.set(i, importTagSizes.get(i) - idByteCount - 8);
			
			// read the tag size
			long byteCount = buffer.position(idByteCount).getLong() & ~(1L << 56L);
			importTagSizes.push(byteCount);
			
		}
		
		private void assertTagClosed() throws AssertionError {
			
			if(importTagSizes.isEmpty())
				throw new AssertionError("Expected the end of a tag, but no tags are currently open.");
			
			long remainingByteCount = importTagSizes.pop();
			if(remainingByteCount != 0)
				throw new AssertionError("Expected the end of a tag, but " + remainingByteCount + " bytes of remain.");
			
		}
		
		private void assertTagFound(byte[] tagId, long tagData) throws AssertionError, IOException {
			
			int idByteCount = tagId.length;
			int dataByteCount = (tagData & 0xFF00000000000000L) != 0 ? 8 :
			                    (tagData & 0x00FF000000000000L) != 0 ? 7 :
			                    (tagData & 0x0000FF0000000000L) != 0 ? 6 :
			                    (tagData & 0x000000FF00000000L) != 0 ? 5 :
			                    (tagData & 0x00000000FF000000L) != 0 ? 4 :
			                    (tagData & 0x0000000000FF0000L) != 0 ? 3 :
			                    (tagData & 0x000000000000FF00L) != 0 ? 2 :
			                                                           1;
			
			// read enough of the file for this id/size/data
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8 + dataByteCount);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// ensure the tag id matches
			for(int i = 0; i < idByteCount; i++)
				if(buffer.get(i) != tagId[i]) {
					String message = "Expected tag: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", tagId[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", buffer.get(j));
					message = message.trim();
					throw new AssertionError(message);
				}
			
			// read the tag size
			long byteCount = buffer.position(idByteCount).getLong() & ~(1L << 56L);
			if(byteCount != dataByteCount) {
				String message = "Expected tag ";
				for(int i = 0; i < idByteCount; i++)
					message += String.format("%02X ", tagId[i]);
				message = message.trim();
				message += " to contain " + dataByteCount + " bytes, but it contains " + byteCount + " bytes.";
				throw new AssertionError(message);
			}
			
			// ensure the tag data matches
			for(int i = 0; i < dataByteCount; i++) {
				int bits = (int) (dataByteCount - i - 1) * 8;
				if(buffer.get(idByteCount + 8 + i) != (byte) ((tagData >> bits) & 0xFF)) {
					String message = "Expected value: " + tagData + ", found: ";
					for(int j = 0; j < dataByteCount; j++)
						message += String.format("%02X ", buffer.get(idByteCount + 8 + j));
					message = message.trim();
					throw new AssertionError(message);
				}
			}
			
			// if any parent tags exist, update their sizes
			if(!importTagSizes.isEmpty())
				for(int i = 0; i < importTagSizes.size(); i++)
					importTagSizes.set(i, importTagSizes.get(i) - idByteCount - 8 - dataByteCount);
			
		}
		
		private long assertTagFound(byte[] tagId) throws AssertionError, IOException {
			
			int idByteCount = tagId.length;
			
			// read enough of the file for this id/size
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// ensure the tag id matches
			for(int i = 0; i < idByteCount; i++)
				if(buffer.get(i) != tagId[i]) {
					String message = "Expected tag: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", tagId[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", buffer.get(j));
					message = message.trim();
					throw new AssertionError(message);
				}
			
			// read the tag size
			long dataByteCount = buffer.position(idByteCount).getLong() & ~(1L << 56L);
			
			// read the tag data
			buffer.clear();
			buffer.limit((int) dataByteCount);
			inputFile.read(buffer);
			
			buffer.flip();
			long data = 0;
			for(int i = 0; i < dataByteCount; i++)
				data = (data << 8) | buffer.get();
			
			// if any parent tags exist, update their sizes
			if(!importTagSizes.isEmpty())
				for(int i = 0; i < importTagSizes.size(); i++)
					importTagSizes.set(i, importTagSizes.get(i) - idByteCount - 8 - dataByteCount);
			
			return data;
			
		}
		
		private void assertTagFound(byte[] tagId, byte[] tagData) throws AssertionError, IOException {
			
			int idByteCount = tagId.length;
			int dataByteCount = tagData.length;
			
			// read enough of the file for this id/size/data
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8 + dataByteCount);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// ensure the tag id matches
			for(int i = 0; i < idByteCount; i++)
				if(buffer.get(i) != tagId[i]) {
					String message = "Expected tag: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", tagId[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", buffer.get(j));
					message = message.trim();
					throw new AssertionError(message);
				}
			
			// read the tag size
			long byteCount = buffer.position(idByteCount).getLong() & ~(1L << 56L);
			if(byteCount != dataByteCount) {
				String message = "Expected tag ";
				for(int i = 0; i < idByteCount; i++)
					message += String.format("%02X ", tagId[i]);
				message = message.trim();
				message += " to contain " + dataByteCount + " bytes, but it contains " + byteCount + " bytes.";
				throw new AssertionError(message);
			}
			
			// ensure the tag data matches
			for(int i = 0; i < dataByteCount; i++) {
				if(buffer.get(idByteCount + 8 + i) != tagData[i]) {
					String message = "Expected value: ";
					for(int j = 0; j < dataByteCount; j++)
						message += String.format("%02X ", tagData[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < dataByteCount; j++)
						message += String.format("%02X ", buffer.get(idByteCount + 8 + j));
					message = message.trim();
					throw new AssertionError(message);
				}
			}
			
			// if any parent tags exist, update their sizes
			if(!importTagSizes.isEmpty())
				for(int i = 0; i < importTagSizes.size(); i++)
					importTagSizes.set(i, importTagSizes.get(i) - idByteCount - 8 - dataByteCount);
			
		}
		
		private ByteBuffer assertTagFoundData(byte[] tagId) throws AssertionError, IOException {
			
			int idByteCount = tagId.length;
			
			// read enough of the file for this id/size
			ByteBuffer buffer = Buffers.newDirectByteBuffer(idByteCount + 8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// ensure the tag id matches
			for(int i = 0; i < idByteCount; i++)
				if(buffer.get(i) != tagId[i]) {
					String message = "Expected tag: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", tagId[j]);
					message = message.trim();
					message += ", found: ";
					for(int j = 0; j < idByteCount; j++)
						message += String.format("%02X ", buffer.get(j));
					message = message.trim();
					throw new AssertionError(message);
				}
			
			// read the tag size
			long dataByteCount = buffer.position(idByteCount).getLong() & ~(1L << 56L);
			
			// read the data
			buffer = Buffers.newDirectByteBuffer((int) dataByteCount);
			buffer.order(ByteOrder.BIG_ENDIAN);
			inputFile.read(buffer);
			
			// if any parent tags exist, update their sizes
			if(!importTagSizes.isEmpty())
				for(int i = 0; i < importTagSizes.size(); i++)
					importTagSizes.set(i, importTagSizes.get(i) - idByteCount - 8 - dataByteCount);
			
			return buffer.flip();
			
		}
		
	}

}
