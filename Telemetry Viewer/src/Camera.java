import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
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
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJCompressor;
import org.libjpegturbo.turbojpeg.TJDecompressor;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.WebcamLockException;
import com.jogamp.common.nio.Buffers;

public class Camera {
	
	// threading
	private Thread thread;
	private volatile boolean threadInterrupted; // can't use Thread.interrupt() because the webcam library catches it and deadlocks
	private volatile int liveJpegThreads = 0; // number of currently live sub-threads encoding or decoding jpeg's

	// camera state
	public String name;
	private boolean isMjpeg;
	private Webcam camera;
	private Dimension requestedResolution = new Dimension(0, 0);
	
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
	private volatile List<FrameInfo> frames = Collections.synchronizedList(new ArrayList<FrameInfo>()); // index = frame number
	private volatile Path pathOnDisk = Paths.get("cache/" + this.toString() + ".mjpg");
	private volatile FileChannel file;
	private volatile boolean fileIsImported;
	
	/**
	 * Prepares a Camera object, but does not actually connect to it.
	 * This object may be backed by a local camera (USB, etc.) or an MJPEG-over-HTTP stream.
	 * 
	 * @param name       The camera name or URL.
	 * @param isMjpeg    True if using MJPEG-over-HTTP.
	 */
	public Camera(String name, boolean isMjpeg) {
		
		this.name = name;
		this.isMjpeg = isMjpeg;
		if(!isMjpeg)
			for(Webcam cam : WidgetCamera.cameras)
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
			NotificationsController.showFailureForSeconds("Unable the create the cache file for " + name + "\n" + e.getMessage(), 10, false);
			e.printStackTrace();
		}
		
	}

	/**
	 * Connects to the camera and starts acquiring images.
	 * This has no effect during file imports.
	 * 
	 * @param requestedResolution    The image resolution. Ignored if using MJPEG-over-HTTP.
	 */
	public void connect(Dimension requestedResolution) {
		
		disconnect();
		
		// don't actually connect if importing from a file
		if(CommunicationController.getPort().equals(CommunicationController.PORT_FILE))
			return;
		
		// if we previously imported a file, close it and create a new cache file
		// it's also possible that the file is closed if we imported a CSV file without importing the corresponding camera files.
		if(fileIsImported || !file.isOpen()) {
			try {
				file.close();
				frames.clear();
				file = FileChannel.open(pathOnDisk, StandardOpenOption.CREATE,
				                                    StandardOpenOption.TRUNCATE_EXISTING,
				                                    StandardOpenOption.READ,
				                                    StandardOpenOption.WRITE);
				fileIsImported = false;
			} catch(Exception e) {
				NotificationsController.showFailureForSeconds("Unable the create the cache file for " + name + "\n" + e.getMessage(), 10, false);
				e.printStackTrace();
				return;
			}
		}
		
		// connect to the camera
		if(isMjpeg) {
		
			thread = new Thread(() -> {
				
				try {
					
					this.requestedResolution = null;
					
					// connect to the stream
					liveImage = new GLframe(null, true, 1, 1, "[connecting...]", 0);
					URLConnection stream = new URL(name).openConnection();
					stream.setConnectTimeout(5000);
					stream.setReadTimeout(5000);
					stream.connect();
					InputStream is = stream.getInputStream();
					
					StringWriter buffer = null;
					int i = 0;
					
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
						if(threadInterrupted)
							throw new InterruptedException();
						
					}
					
				} catch (Exception e) {
					
					while(liveJpegThreads > 0); // wait
					
					     if(e instanceof ConnectException)       liveImage = new GLframe(null, true, 1, 1, "[invalid mjpeg stream]", 0);
					else if(e instanceof SocketTimeoutException) liveImage = new GLframe(null, true, 1, 1, "[unable to connect]",    0);
					else if(e instanceof InterruptedException)   liveImage = new GLframe(null, true, 1, 1, "[stopped]",              0);
					else                                         liveImage = new GLframe(null, true, 1, 1, "[stream ended]",         0);
					
					threadInterrupted = false;
					
				}
				
			});
			
			thread.setName("MJPEG Camera Thread for " + name);
			thread.start();
			
		} else {
		
			thread = new Thread(() -> {
				
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
				
				// save the requested resolution so it can be queried in the future
				this.requestedResolution = requestedResolution;
				
				try {
					// the webcam library requires the requested resolution to be one of the predefined "custom view sizes"
					// so we must predefine all of the options shown by WidgetCamera
					camera.setCustomViewSizes(WidgetCamera.resolutions);
					
					// request a resolution, open the camera, then get the actual resolution
					camera.setViewSize(requestedResolution);
					camera.open();
					Dimension resolution = camera.getViewSize();
		
					// enter an infinite loop that acquires images
					while(true) {
						
						// acquire a new image
						ByteBuffer buffer = Buffers.newDirectByteBuffer(resolution.width * resolution.height * 3);
						camera.getImageBytes(buffer);
						
						// save and show the image
						long timestamp = System.currentTimeMillis();
						saveImage(buffer, resolution, timestamp);
						showImage(buffer, resolution, timestamp);
						
						// stop if requested
						if(threadInterrupted)
							throw new InterruptedException();
						
					}
					
				} catch(Exception e) {
					
					while(liveJpegThreads > 0); // wait
					camera.close();
					
					     if(e instanceof WebcamException)     liveImage = new GLframe(null, true, 1, 1, "[unable to connect]", 0);
					else if(e instanceof WebcamLockException) liveImage = new GLframe(null, true, 1, 1, "[unable to connect]", 0);
					else						              liveImage = new GLframe(null, true, 1, 1, "[stopped]", 0);
					
					threadInterrupted = false;
					
				}
				
			});
			
			thread.setName("Camera Thread for " + name);
			thread.start();
			
		}
		
	}
	
	/**
	 * Disconnects from the camera, but retains the acquired images.
	 */
	public void disconnect() {
		
		// stop the camera thread
		if(thread != null && thread.isAlive()) {
			threadInterrupted = true;
			while(thread.isAlive()); // wait
		}
		
		liveImage = new GLframe(null, true, 1, 1, "[stopped]", 0);
		
	}
	
	/**
	 * @return    True if the camera is connected or in the process of connecting.
	 */
	public boolean isConnected() {
		
		return (thread != null) && thread.isAlive();
		
	}
	
	/**
	 * Gets the resolution that the user requested. This may be different from the resolution provided from the camera.
	 * Call this method to check if you need to disconnect/reconnect because the user is requesting a different resolution.
	 * 
	 * @return    The resolution requested by the user the last time connect() was called.
	 */
	public Dimension getRequestedResolution() {
		
		return requestedResolution;
		
	}
	
	/**
	 * @return    The number of frames that have been acquired.
	 */
	public int getFrameCount() {
		
		return frames.size();
		
	}
	
	/**
	 * Disconnects from the camera and deletes all acquired images.
	 */
	public void dispose() {
		
		disconnect();
		
		try {
			file.close();
			Files.deleteIfExists(pathOnDisk);
			frames.clear();
		} catch(Exception e) {
			NotificationsController.showFailureForSeconds("Unable the delete the cache file for " + name + "\n" + e.getMessage(), 10, false);
			e.printStackTrace();
		}
		
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
	 * Gets the closest image from just before a certain moment in time.
	 * 
	 * @param timestamp    The moment in time (milliseconds since 1970-01-01.)
	 * @return             The image and related information, as a GLframe object.
	 */
	public GLframe getImageBeforeTimestamp(long timestamp) {
		
		// give up if there's no images
		if(frames.isEmpty())
			return new GLframe(null, true, 1, 1, "[no image]", 0);
		
		// determine the frame index
		int frameIndex = frames.size() - 1;
		long frameTimestamp = frames.get(frameIndex).timestamp;
		
		for(int i = frameIndex - 1; i >= 0; i--) {
			long timestamp2 = frames.get(i).timestamp;
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
		FrameInfo info = frames.get(frameIndex);
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
	
	/**
	 * Saves all images to a MJPG file, and saves the corresponding index data to a BIN file.
	 * 
	 * @param filepath           An absolute path, including the first part of the file name. The camera name, and .mjpg/.bin, will be appended to this.
	 * @param progressTracker    Consumer<Double> that will be notified as progress is made. (0.0 = 0%, 1.0 = 100%.)
	 */
	public void exportFiles(String filepath, Consumer<Double> progressTracker) {
		
		filepath += " " + name.replaceAll("[^a-zA-Z0-9.-]", "_");
		
		int frameCount = frames.size();
		if(frameCount == 0)
			return;
		
		// write the index to a BIN file
		try {
			ObjectOutputStream binFile = new ObjectOutputStream(new FileOutputStream(filepath + ".bin"));
			for(int i = 0; i < frameCount; i++) {
				FrameInfo info = frames.get(i);
				binFile.writeLong(info.timestamp);
				binFile.writeLong(info.offset);
				binFile.writeInt(info.length);
			}
			binFile.close();
		} catch(Exception e) {
			NotificationsController.showFailureForSeconds("Error while exporting the BIN file for " + name + "\n" + e.getMessage(), 10, false);
			e.printStackTrace();
			return;
		}
		
		// write the images to a MJPG file
		try {
			FileChannel mjpgFile = FileChannel.open(Paths.get(filepath + ".mjpg"), StandardOpenOption.CREATE,
			                                                                       StandardOpenOption.TRUNCATE_EXISTING,
			                                                                       StandardOpenOption.WRITE);
			// copy to the mjpg file with FileChannel.transferTo(), do not use FileChannel.map() because:
			// https://bugs.openjdk.java.net/browse/JDK-4715154
			long offset = 0;
			long remainingBytes = frames.get(frameCount - 1).offset + frames.get(frameCount - 1).length;
			long fileSize = remainingBytes;
			while(remainingBytes > 0) {
				long amount = Long.min(remainingBytes, 16777216); // 16MB chunks
				file.transferTo(offset, amount, mjpgFile);
				mjpgFile.force(true);
				remainingBytes -= amount;
				offset += amount;
				progressTracker.accept((double) offset / (double) fileSize);
			}
			mjpgFile.close();
		} catch(Exception e) {
			NotificationsController.showFailureForSeconds("Error while exporting the MJPG file for " + name + "\n" + e.getMessage(), 10, false);
			e.printStackTrace();
			return;
		}
		
	}
	
	/**
	 * Imports all images from a MJPG file and corresponding index data from a BIN file.
	 * 
	 * @param mjpgFilepath    Absolute path of the MJPG file.
	 * @param binFilepath     Absolute path of the BIN file.
	 */
	public void importFiles(String mjpgFilepath, String binFilepath) {
		
		dispose();
		
		try {
			file = FileChannel.open(Paths.get(mjpgFilepath), StandardOpenOption.READ);
			fileIsImported = true;
		} catch(Exception e) {
			NotificationsController.showFailureForSeconds("Unable the open the MJPG file for " + name + "\n" + e.getMessage(), 10, false);
			e.printStackTrace();
			return;
		}
		
		new Thread(() -> {
			try {
				// import the index data
				ObjectInputStream binFile = new ObjectInputStream(new FileInputStream(binFilepath));
				try {
					while(true) {
						long timestamp = binFile.readLong();
						long offset = binFile.readLong();
						int length = binFile.readInt();
						frames.add(new FrameInfo(timestamp, offset, length));
					}
				} catch(Exception e) {
					// reached the end of file
				}
				binFile.close();
			} catch(Exception e) {
				NotificationsController.showFailureForSeconds("Error while importing data from " + binFilepath + "\n" + e.getMessage(), 10, false);
				e.printStackTrace();
				return;
			}
		}).start();
		
	}
	
	/**
	 * Appends a new image to the dataset.
	 * 
	 * @param jpegBytes    The image, as a JPEG.
	 * @param timestamp    When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void saveJpeg(byte[] jpegBytes, long timestamp) {
		
		try {
			frames.add(new FrameInfo(timestamp, file.size(), jpegBytes.length));
			file.write(ByteBuffer.wrap(jpegBytes));
			file.force(true);
		} catch(Exception e) {
			NotificationsController.showFailureForSeconds("Unable the save to the cache file for " + name + "\n" + e.getMessage(), 10, false);
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
				int frameCount = frames.size();
				double fps = 0;
				if(frameCount > 30)
					fps = 30000.0 / (double) (frames.get(frameCount - 1).timestamp - frames.get(frameCount - 30).timestamp);
				String label = String.format("%s (%d x %d, %01.1f FPS)", name, width, height, fps);
				liveImage = new GLframe(bgrBytes, true, width, height, label, timestamp);
				
				liveJpegThreads--;
				
			} catch(Exception e) {
				
				NotificationsController.showFailureForSeconds("Unable to decode one of the frames from " + name + "\n" + e.getMessage(), 10, true);
				e.printStackTrace();
				liveJpegThreads--;
				
			}
		}).start();
		
	}
	
	/**
	 * Spawns a new thread that will encode a raw image into a JPEG and store that in the dataset.
	 * 
	 * @param image         The image.
	 * @param resolution    Size of the image, in pixels.
	 * @param timestamp     When the image was captured (milliseconds since 1970-01-01.)
	 */
	private void saveImage(ByteBuffer image, Dimension resolution, long timestamp) {
		
		byte[] bytes = new byte[image.capacity()];
		image.get(bytes);
		liveJpegThreads++;
		
		int frameNumber = frames.size();
		
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
					NotificationsController.showFailureForSeconds("Unable to encode one of the frames from " + name + "\n" + e.getMessage(), 10, true);
					e.printStackTrace();
					liveJpegThreads--;
					return;
				}
			}
			
			// wait for previous encoding threads to finish before inserting this frame
			while(frames.size() < frameNumber);
			
			// save to disk
			try {
				frames.add(new FrameInfo(timestamp, file.size(), jpegBytesLength));
				file.write(ByteBuffer.wrap(jpegBytes, 0, jpegBytesLength));
				file.force(true);
			} catch (Exception e) {
				NotificationsController.showFailureForSeconds("Unable to save one of the frames from " + name + "\n" + e.getMessage(), 10, false);
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
		
		int frameCount = frames.size();
		double fps = 0;
		if(frameCount > 30)
			fps = 30000.0 / (double) (frames.get(frameCount - 1).timestamp - frames.get(frameCount - 30).timestamp);
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
}
