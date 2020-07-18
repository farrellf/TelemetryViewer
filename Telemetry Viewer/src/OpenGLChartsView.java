import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

/**
 * Manages the grid region and all charts on the screen.
 * 
 * Users can click-and-drag in this region to create new charts or interact with existing charts.
 */
@SuppressWarnings("serial")
public class OpenGLChartsView extends JPanel {
	
	static OpenGLChartsView instance = new OpenGLChartsView();
	
	static boolean firstRun = true;
	
	List<PositionedChart> chartsToDispose = new ArrayList<PositionedChart>();
	
	Animator animator;
	GLCanvas glCanvas;
	int canvasWidth;
	int canvasHeight;
	float displayScalingFactorJava9 = 1;
	float displayScalingFactor = 1;
	
	// grid size
	int tileColumns;
	int tileRows;
	
	// grid locations for the opposite corners of where a new chart will be placed
	int startX;
	int startY;
	int endX;
	int endY;
	
	// time and zoom settings
	boolean liveView;
	int nonLiveViewSampleNumber;
	double zoomLevel;
	
	// mouse pointer's current location (pixels, origin at bottom-left)
	int mouseX;
	int mouseY;
	EventHandler eventHandler;
	PositionedChart chartUnderMouse;
	
	boolean maximizing;
	boolean demaximizing;
	PositionedChart maximizedChart;
	long maximizingAnimationEndTime;
	final long maximizingAnimationDuration = 200; // milliseconds
	
	boolean removing;
	PositionedChart removingChart;
	long removingAnimationEndTime;
	final long removingAnimationDuration = 300; // milliseconds
	
	// chart benchmarking
	long cpuStartNanoseconds;
	long cpuStopNanoseconds;
	double previousCpuMilliseconds;
	double previousGpuMilliseconds;
	double cpuMillisecondsAccumulator;
	double gpuMillisecondsAccumulator;
	int count;
	final int SAMPLE_COUNT = 60;
	double averageCpuMilliseconds;
	double averageGpuMilliseconds;
	int[] gpuQueryHandles = new int[2];
	long[] gpuTimes = new long[2];
	boolean openGLES;
	
	JFrame parentWindow;
	
	float[] screenMatrix = new float[16];
	
	private OpenGLChartsView() {
		
		super();
		
		tileColumns = SettingsController.getTileColumns();
		tileRows    = SettingsController.getTileRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
		liveView = true;
		nonLiveViewSampleNumber = 0;
		zoomLevel = 1;
		
		mouseX = -1;
		mouseY = -1;
		
		parentWindow = (JFrame) SwingUtilities.windowForComponent(this);
		
//		System.out.println(GLProfile.glAvailabilityToString());
//		System.setProperty("jogl.debug.GLSLCode", "");
//		System.setProperty("jogl.debug.DebugGL", "");
		GLCapabilities capabilities = null;
		try {
			// try to get normal OpenGL
			capabilities = new GLCapabilities(GLProfile.get(GLProfile.GL3));
			openGLES = false;
			if(SettingsController.getAntialiasingLevel() > 1) {
				capabilities.setSampleBuffers(true);
				capabilities.setNumSamples(SettingsController.getAntialiasingLevel());
			}
		} catch(Error | Exception e) {
			try {
				// fall back to OpenGL ES
				capabilities = new GLCapabilities(GLProfile.get(GLProfile.GLES3));
				openGLES = true;
				if(SettingsController.getAntialiasingLevel() > 1) {
					capabilities.setSampleBuffers(true);
					capabilities.setNumSamples(SettingsController.getAntialiasingLevel());
				}
			} catch(Error | Exception e2) {
				NotificationsController.showFailureForSeconds("Unable to create the OpenGL context.\nThis may be due to a graphics driver problem, or an outdated graphics card.\n\"" + e.getMessage() + "\n\n" + e2.getMessage() + "\"", 999, false);
				return;
			}
		}
		glCanvas = new GLCanvas(capabilities);
		glCanvas.addGLEventListener(new GLEventListener() {

			@Override public void init(GLAutoDrawable drawable) {
				
				GL2ES3 gl = drawable.getGL().getGL2ES3();
			
				gl.glEnable(GL3.GL_BLEND);
				gl.glBlendFunc(GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA);
				
				// disable antialiasing when using OpenGL ES, because rendering to off-screen framebuffers doesn't seem to support MSAA in OpenGL ES 3.1
				if(!gl.isGL3() && SettingsController.getAntialiasingLevel() > 1) {
					SettingsController.setAntialiasingLevel(1);
					return;
				}
				
				// ensure the requested AA level is supported 
				if(SettingsController.getAntialiasingLevel() > 1) {
					int[] number = new int[1];
					gl.glGetIntegerv(GL3.GL_MAX_SAMPLES, number, 0);
					if(number[0] < SettingsController.getAntialiasingLevel())
						SettingsController.setAntialiasingLevel(number[0]);
				}
				
				gl.setSwapInterval(1);
				
				// GPU benchmarking is not possible with OpenGL ES
				if(!openGLES) {
					gl.glGenQueries(2, gpuQueryHandles, 0);
					gl.glQueryCounter(gpuQueryHandles[0], GL3.GL_TIMESTAMP); // insert both queries to prevent a warning on the first time they are read
					gl.glQueryCounter(gpuQueryHandles[1], GL3.GL_TIMESTAMP);
				}
				
				OpenGL.makeAllPrograms(gl);
				
				displayScalingFactor = ChartsController.getDisplayScalingFactor();
				Theme.initialize(gl, displayScalingFactor);
				
				if(firstRun) {
					
					firstRun = false;
					int[] number = new int[2];
					StringBuilder text = new StringBuilder(65536);
					                                                               text.append("GL_VENDOR                    = " + gl.glGetString(GL3.GL_VENDOR) + "\n");
					                                                               text.append("GL_RENDERER                  = " + gl.glGetString(GL3.GL_RENDERER) + "\n");
					                                                               text.append("GL_VERSION                   = " + gl.glGetString(GL3.GL_VERSION) + "\n");
					                                                               text.append("GL_SHADING_LANGUAGE_VERSION  = " + gl.glGetString(GL3.GL_SHADING_LANGUAGE_VERSION) + "\n");
					gl.glGetIntegerv(GL3.GL_MAJOR_VERSION, number, 0);             text.append("GL_MAJOR_VERSION             = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MINOR_VERSION, number, 0);             text.append("GL_MINOR_VERSION             = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_SAMPLES, number, 0);               text.append("GL_MAX_SAMPLES               = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_TEXTURE_SIZE, number, 0);          text.append("GL_MAX_TEXTURE_SIZE          = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_RENDERBUFFER_SIZE, number, 0);     text.append("GL_MAX_RENDERBUFFER_SIZE     = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_VIEWPORT_DIMS, number, 0);         text.append("GL_MAX_VIEWPORT_DIMS         = " + number[0] + " x " + number[1] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_DRAW_BUFFERS, number, 0);          text.append("GL_MAX_DRAW_BUFFERS          = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_MAX_COLOR_TEXTURE_SAMPLES, number, 0); text.append("GL_MAX_COLOR_TEXTURE_SAMPLES = " + number[0] + "\n");
					gl.glGetIntegerv(GL3.GL_NUM_EXTENSIONS, number, 0);            text.append(number[0] + " EXTENSIONS: " + gl.glGetStringi(GL3.GL_EXTENSIONS, 0));
					for(int i = 1; i < number[0]; i++)                             text.append(", " + gl.glGetStringi(GL3.GL_EXTENSIONS, i));
					NotificationsController.showVerboseForSeconds("OpenGL Information:\n" + text.toString(), 999, true);
					
				}
				
			}
						
			@Override public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				
				GL2ES3 gl = drawable.getGL().getGL2ES3();
				
				// work around java 9+ dpi scaling problem with JOGL
				displayScalingFactorJava9 = (float) ((Graphics2D) getGraphics()).getTransform().getScaleX();
				width = (int) (width * displayScalingFactorJava9);
				height = (int) (height * displayScalingFactorJava9);
				gl.glViewport(0, 0, width, height);
				
				OpenGL.makeOrthoMatrix(screenMatrix, 0, width, 0, height, -100000, 100000);
				OpenGL.useMatrix(gl, screenMatrix);
				
				canvasWidth = width;
				canvasHeight = height;
				
				ChartsController.setDisplayScalingFactorJava9(displayScalingFactorJava9);
				
			}

			@Override public void display(GLAutoDrawable drawable) {
				
				int tileWidth   = canvasWidth  / tileColumns;
				int tileHeight  = canvasHeight / tileRows;
				int tilesYoffset = canvasHeight - (tileHeight * tileRows);
				
				// prepare OpenGL
				GL2ES3 gl = drawable.getGL().getGL2ES3();
				OpenGL.useMatrix(gl, screenMatrix);
				
				gl.glClearColor(Theme.neutralColor[0], Theme.neutralColor[1], Theme.neutralColor[2], Theme.neutralColor[3]);
				gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
				
				// dispose of any charts that were just removed
				for(PositionedChart chart : chartsToDispose)
					chart.disposeGpu(gl);
				chartsToDispose.clear();
				
				// update the theme if the display scaling factor has changed
				float newDisplayScalingFactor = ChartsController.getDisplayScalingFactor();
				if(displayScalingFactor != newDisplayScalingFactor) {
					Theme.initialize(gl, newDisplayScalingFactor);
					displayScalingFactor = newDisplayScalingFactor;
				}
				
				List<PositionedChart> charts = ChartsController.getCharts();
				
				// if there is no connection and no charts, we're done, do not draw any tiles
				if(!(CommunicationController.isConnected() && CommunicationController.isDataStructureDefined()) && charts.isEmpty())
					return;
				
				// if there are no charts, ensure we switch back to live view
				if(charts.isEmpty())
					liveView = true;
				
				// if the maximized chart was removed, forget about it
				if(maximizedChart != null && !charts.contains(maximizedChart))
					maximizedChart = null;
				
				// draw empty tiles if necessary
				if(maximizing || demaximizing || maximizedChart == null) {
					boolean[][] tileOccupied = ChartsController.getTileOccupancy();
					for(int column = 0; column < tileColumns; column++) {
						for(int row = 0; row < tileRows; row++) {
							if(!tileOccupied[column][row]) {
								int lowerLeftX = tileWidth * column;
								int lowerLeftY = tileHeight * row + tilesYoffset;
								drawTile(gl, lowerLeftX, lowerLeftY, tileWidth, tileHeight);
							}
						}
					}
				}
				
				// draw a bounding box where the user is actively clicking-and-dragging to place a new chart
				OpenGL.drawBox(gl,
				               Theme.tileSelectedColor,
				               startX < endX ? startX * tileWidth : endX * tileWidth,
				               startY < endY ? canvasHeight - (endY + 1)*tileHeight : canvasHeight - (startY + 1)*tileHeight,
				               (Math.abs(endX - startX) + 1) * tileWidth,
				               (Math.abs(endY - startY) + 1) * tileHeight);
				
				int lastSampleNumber = liveView ? DatasetsController.getSampleCount() - 1 : nonLiveViewSampleNumber;
				
				// ensure active slots don't get flushed to disk
				int lastSampleNumberOnScreen = lastSampleNumber;
				int firstSampleNumberOnScreen = lastSampleNumberOnScreen;
				for(PositionedChart c : ChartsController.getCharts()) {
					if(lastSampleNumberOnScreen - c.sampleCount < firstSampleNumberOnScreen)
						firstSampleNumberOnScreen = lastSampleNumberOnScreen - c.sampleCount;
				}
				DatasetsController.dontFlushRangeOnScreen(firstSampleNumberOnScreen, lastSampleNumberOnScreen);
				
				// draw the charts
				//
				// the modelview matrix is translated so the origin will be at the bottom-left for each chart.
				// the scissor test is used to clip rendering to the region allocated for each chart.
				// if charts will be using off-screen framebuffers, they need to disable the scissor test when (and only when) drawing off-screen.
				// after the chart is drawn with OpenGL, any text queued for rendering will be drawn on top.
				eventHandler = null;
				chartUnderMouse = null;
				for(PositionedChart chart : charts) {
					
					// if there is a maximized chart, only draw that chart
					if(maximizedChart != null && maximizedChart != removingChart && chart != maximizedChart && !maximizing && !demaximizing)
						continue;
					
					// calculate CPU/GPU time for the *previous frame*
					// GPU benchmarking is not possible with OpenGL ES
					if(chart == SettingsController.getBenchmarkedChart()) {
						previousCpuMilliseconds = (cpuStopNanoseconds - cpuStartNanoseconds) / 1000000.0;
						if(!openGLES) {
							gl.glGetQueryObjecti64v(gpuQueryHandles[0], GL3.GL_QUERY_RESULT, gpuTimes, 0);
							gl.glGetQueryObjecti64v(gpuQueryHandles[1], GL3.GL_QUERY_RESULT, gpuTimes, 1);
						}
						previousGpuMilliseconds = (gpuTimes[1] - gpuTimes[0]) / 1000000.0;
						if(count < SAMPLE_COUNT) {
							cpuMillisecondsAccumulator += previousCpuMilliseconds;
							gpuMillisecondsAccumulator += previousGpuMilliseconds;
							count++;
						} else {
							averageCpuMilliseconds = cpuMillisecondsAccumulator / 60.0;
							averageGpuMilliseconds = gpuMillisecondsAccumulator / 60.0;
							cpuMillisecondsAccumulator = 0;
							gpuMillisecondsAccumulator = 0;
							count = 0;
						}
						
						// start timers for *this frame*
						cpuStartNanoseconds = System.nanoTime();
						if(!openGLES)
							gl.glQueryCounter(gpuQueryHandles[0], GL3.GL_TIMESTAMP);
					}
					
					// size the chart
					int width = tileWidth * (chart.bottomRightX - chart.topLeftX + 1);
					int height = tileHeight * (chart.bottomRightY - chart.topLeftY + 1);
					int xOffset = chart.topLeftX * tileWidth;
					int yOffset = canvasHeight - (chart.topLeftY * tileHeight) - height;
					
					// size the maximized chart correctly
					if(chart == maximizedChart) {
						
						double animationPosition = 1.0 - (double) (maximizingAnimationEndTime - System.currentTimeMillis()) / maximizingAnimationDuration;
						animationPosition = smoothstep(animationPosition);
						
						int maximizedWidth = tileWidth * tileColumns;
						int maximizedHeight = tileHeight * tileRows;
						int maximizedXoffset = 0;
						int maximizedYoffset = canvasHeight - maximizedHeight;

						if(maximizing) {
							
							width   = (int) Math.round(width   * (1.0 - animationPosition) + (maximizedWidth   * animationPosition));
							height  = (int) Math.round(height  * (1.0 - animationPosition) + (maximizedHeight  * animationPosition));
							xOffset = (int) Math.round(xOffset * (1.0 - animationPosition) + (maximizedXoffset * animationPosition));
							yOffset = (int) Math.round(yOffset * (1.0 - animationPosition) + (maximizedYoffset * animationPosition));
							
							if(animationPosition == 1.0)
								maximizing = false;
							
						} else if(demaximizing) {
							
							width   = (int) Math.round((width   * animationPosition) + (maximizedWidth   * (1.0 - animationPosition)));
							height  = (int) Math.round((height  * animationPosition) + (maximizedHeight  * (1.0 - animationPosition)));
							xOffset = (int) Math.round((xOffset * animationPosition) + (maximizedXoffset * (1.0 - animationPosition)));
							yOffset = (int) Math.round((yOffset * animationPosition) + (maximizedYoffset * (1.0 - animationPosition)));

							if(animationPosition == 1.0) {
								demaximizing = false;
								maximizedChart = null;
							}
							
						} else {
							
							width = maximizedWidth;
							height = maximizedHeight;
							xOffset = maximizedXoffset;
							yOffset = maximizedYoffset;
							
						}
					}
					
					// size the closing chart correctly
					if(chart == removingChart) {
						
						double animationPosition = 1.0 - (double) (removingAnimationEndTime - System.currentTimeMillis()) / removingAnimationDuration;
						animationPosition = smoothstep(animationPosition);
						
						xOffset = (int) Math.round(xOffset + (0.5 * width  * animationPosition));
						yOffset = (int) Math.round(yOffset + (0.5 * height * animationPosition));
						width   = (int) Math.round(width  * (1.0 - animationPosition));
						height  = (int) Math.round(height * (1.0 - animationPosition));
						
					}
					
					drawTile(gl, xOffset, yOffset, width, height);
					
					// draw the chart
					xOffset += Theme.tilePadding;
					yOffset += Theme.tilePadding;
					width  -= 2 * Theme.tilePadding;
					height -= 2 * Theme.tilePadding;
					
					if(width < 1 || height < 1)
						continue;
					
					gl.glEnable(GL3.GL_SCISSOR_TEST);
					gl.glScissor(xOffset, yOffset, width, height);
					
					float[] chartMatrix = Arrays.copyOf(screenMatrix, 16);
					OpenGL.translateMatrix(chartMatrix, xOffset, yOffset, 0);
					OpenGL.useMatrix(gl, chartMatrix);
					
					EventHandler handler = chart.drawChart(gl, chartMatrix, width, height, lastSampleNumber, zoomLevel, mouseX - xOffset, mouseY - yOffset);
					
					// draw the CPU/GPU benchmarks for this chart if benchmarking
					// GPU benchmarking is not possible with OpenGL ES
					if(chart == SettingsController.getBenchmarkedChart()) {
						// stop timers for *this frame*
						cpuStopNanoseconds = System.nanoTime();
						if(!openGLES)
							gl.glQueryCounter(gpuQueryHandles[1], GL3.GL_TIMESTAMP);
						
						// show times of *previous frame*
						gl.glScissor((int) (xOffset - Theme.tilePadding), (int) (yOffset - Theme.tilePadding), (int) (width + 2 * Theme.tilePadding), (int) (height + 2 * Theme.tilePadding));
						OpenGL.translateMatrix(chartMatrix, 0, -Theme.tilePadding, 0);
						OpenGL.useMatrix(gl, chartMatrix);
						String line1 =             String.format("CPU = %.3fms (Average = %.3fms)", previousCpuMilliseconds, averageCpuMilliseconds);
						String line2 = !openGLES ? String.format("GPU = %.3fms (Average = %.3fms)", previousGpuMilliseconds, averageGpuMilliseconds) :
						                                         "GPU = unknown";
						float textHeight = 2 * OpenGL.smallTextHeight + Theme.tickTextPadding;
						float textWidth = Float.max(OpenGL.smallTextWidth(gl, line1), OpenGL.smallTextWidth(gl, line2));
						OpenGL.drawBox(gl, Theme.neutralColor, 0, 0, textWidth + Theme.tickTextPadding*2, textHeight + Theme.tickTextPadding*2);
						OpenGL.drawSmallText(gl, line1, (int) Theme.tickTextPadding, (int) (2 * Theme.tickTextPadding + OpenGL.smallTextHeight), 0);
						OpenGL.drawSmallText(gl, line2, (int) Theme.tickTextPadding, (int) Theme.tickTextPadding, 0);
						NotificationsController.showVerboseForSeconds(line1 + ", " + line2, 1, false);
					}
					
					OpenGL.useMatrix(gl, screenMatrix);
					gl.glDisable(GL3.GL_SCISSOR_TEST);

					// check if the mouse is over this chart
					width += (int) Theme.tileShadowOffset;
					if(mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height) {
						chartUnderMouse = chart;
						if(handler != null)
							eventHandler = handler;
						drawChartCloseButton(gl, xOffset, yOffset, width, height);
						drawChartMaximizeButton(gl, xOffset, yOffset, width, height);
						drawChartSettingsButton(gl, xOffset, yOffset, width, height);
					}
					
				}
				
				// remove a chart if necessary
				if(removing && removingAnimationEndTime <= System.currentTimeMillis()) {
					ChartsController.removeChart(removingChart);
					if(maximizedChart == removingChart)
						maximizedChart = null;
					removingChart = null;
					removing = false;
				}
				
				// show the FPS/period in the lower-left corner if enabled
				if(SettingsController.getFpsVisibility()) {
					String text = String.format("%2.1fFPS, %dms", animator.getLastFPS(), animator.getLastFPSPeriod());
					int padding = 10;
					float textHeight = OpenGL.largeTextHeight;
					float textWidth = OpenGL.largeTextWidth(gl, text);
					OpenGL.drawBox(gl, Theme.neutralColor, 0, 0, textWidth + padding*2, textHeight + padding*2);
					OpenGL.drawLargeText(gl, text, padding, padding, 0);
					NotificationsController.showVerboseForSeconds(text, 1, false);
				}
				
			}
			
			@Override public void dispose(GLAutoDrawable drawable) {
				
				GL2ES3 gl = drawable.getGL().getGL2ES3();
				
				for(PositionedChart chart : ChartsController.getCharts())
					chart.disposeGpu(gl);
				
				if(!openGLES)
					gl.glDeleteQueries(2, gpuQueryHandles, 0);
				
			}
			
		});
		
		setLayout(new BorderLayout());
		add(glCanvas, BorderLayout.CENTER);
	
		animator = new Animator(glCanvas);
		animator.setUpdateFPSFrames(1, null);
		animator.start();
		
		glCanvas.addMouseListener(new MouseListener() {
			
			// the mouse was pressed, attempting to start a new chart region, or to interact with an existing chart
			@Override public void mousePressed(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && ChartsController.getCharts().isEmpty())
					return;
				
				if(SettingsController.awaitingBenchmarkedChart()) {
					SettingsController.setBenchmarkedChart(chartUnderMouse);
					return;
				}
				
				if(eventHandler != null && eventHandler.forPressEvent) {
					eventHandler.handle(me);
					return;
				}
				
				int proposedStartX = me.getX() * tileColumns / getWidth();
				int proposedStartY = me.getY() * tileRows / getHeight();
				
				if(proposedStartX < tileColumns && proposedStartY < tileRows && ChartsController.gridRegionAvailable(proposedStartX, proposedStartY, proposedStartX, proposedStartY)) {
					startX = endX = proposedStartX;
					startY = endY = proposedStartY;
				}
				
			}
			
			// the mouse was released, attempting to create a new chart
			@Override public void mouseReleased(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && ChartsController.getCharts().isEmpty())
					return;

				if(endX == -1 || endY == -1)
					return;
			
				int proposedEndX = me.getX() * tileColumns / getWidth();
				int proposedEndY = me.getY() * tileRows / getHeight();
				
				if(proposedEndX < tileColumns && proposedEndY < tileRows && ChartsController.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
				int x1 = startX;
				int y1 = startY;
				int x2 = endX;
				int y2 = endY;
				
				startX = startY = -1;
				endX   = endY   = -1;
				
				ConfigureView.instance.forNewChart(x1, y1, x2, y2);
				
			}

			// the mouse left the canvas, no longer need to show the chart close icon
			@Override public void mouseExited (MouseEvent me) {
				
				mouseX = -1;
				mouseY = -1;
				
			}
			
			@Override public void mouseClicked(MouseEvent me) { }
			
			@Override public void mouseEntered(MouseEvent me) { }
			
		});
		
		glCanvas.addMouseMotionListener(new MouseMotionListener() {
			
			// the mouse was dragged while attempting to create a new chart
			@Override public void mouseDragged(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && ChartsController.getCharts().isEmpty())
					return;
				
				mouseX = (int) (me.getX() * displayScalingFactorJava9);
				mouseY = (int) ((glCanvas.getHeight() - me.getY()) * displayScalingFactorJava9);
				
				if(eventHandler != null && eventHandler.forDragEvent) {
					eventHandler.handle(me);
					return;
				}
				
				if(endX == -1 || endY == -1)
					return;
				
				int proposedEndX = me.getX() * tileColumns / getWidth();
				int proposedEndY = me.getY() * tileRows / getHeight();
				
				if(proposedEndX < tileColumns && proposedEndY < tileRows && ChartsController.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
			}
			
			// log the mouse position so a chart close icon can be drawn
			@Override public void mouseMoved(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && ChartsController.getCharts().isEmpty())
					return;
				
				mouseX = (int) (me.getX() * displayScalingFactorJava9);
				mouseY = (int) ((glCanvas.getHeight() - me.getY()) * displayScalingFactorJava9);
				
			}
			
		});
		
		glCanvas.addMouseWheelListener(new MouseWheelListener() {
			
			// the mouse wheel was scrolled
			@Override public void mouseWheelMoved(MouseWheelEvent mwe) {

				double scrollAmount = mwe.getPreciseWheelRotation();
				double samplesPerScroll = CommunicationController.getSampleRate() / 4;
				double zoomPerScroll = 0.1;
				float  displayScalingPerScroll = 0.1f;
				
				if(ChartsController.getCharts().size() == 0 && mwe.isShiftDown() == false)
					return;
				
				if(scrollAmount == 0)
					return;
				
				if(mwe.isControlDown() == false && mwe.isShiftDown() == false) {
					
					// no modifiers held down, so we're timeshifting
					if(liveView == true) {
						liveView = false;
						nonLiveViewSampleNumber = (DatasetsController.getSampleCount() - 1);
					}
					
					double delta = scrollAmount * samplesPerScroll * zoomLevel;
					if(delta < -0.5 || delta > 0.5)
						delta = Math.round(delta);
					else if(delta < 0)
						delta = -1;
					else if(delta >= 0)
						delta = 1;
					nonLiveViewSampleNumber += delta;
					
					if(nonLiveViewSampleNumber >= DatasetsController.getSampleCount() - 1)
						liveView = true;
					
					if(nonLiveViewSampleNumber < 0)
						nonLiveViewSampleNumber = -1;
				
				} else if(mwe.isControlDown() == true) {
					
					// ctrl is down, so we're zooming
					zoomLevel *= 1 + (scrollAmount * zoomPerScroll);
					
					if(zoomLevel > 1)
						zoomLevel = 1;
					else if(zoomLevel < 0)
						zoomLevel = Double.MIN_VALUE;
					
				} else if(mwe.isShiftDown() == true) {
					
					// shift is down, so we're adjusting the display scaling factor
					float newFactor = ChartsController.getDisplayScalingFactorUser() * (1 - ((float)scrollAmount * displayScalingPerScroll));
					ChartsController.setDisplayScalingFactorUser(newFactor);
					
				}
				
			}
			
		});
		
	}
	
	/**
	 * Called by DatasetsController when all data is removed.
	 */
	public void switchToLiveView() {
		
		liveView = true;
		
	}
	
	/**
	 * Replaces the glCanvas. This method must be called when the antialiasing level changes.
	 */
	public static void regenerate() {
		
		boolean updateWindow = false;
		for(Component c : Main.window.getContentPane().getComponents())
			if(c == instance)
				updateWindow = true;
		
		if(updateWindow)
			Main.window.remove(instance);
		
		// save state
		boolean liveView = instance.liveView;
		int nonLiveViewSampleNumber = instance.nonLiveViewSampleNumber;
		double zoomLevel = instance.zoomLevel;
		PositionedChart maximizedChart = instance.maximizedChart;

		// regenerate
		instance.animator.stop();
		instance = new OpenGLChartsView();
		
		// restore state
		instance.liveView = liveView;
		instance.nonLiveViewSampleNumber = nonLiveViewSampleNumber;
		instance.zoomLevel = zoomLevel;
		instance.maximizedChart = maximizedChart;
		
		if(updateWindow) {
			Main.window.add(instance, BorderLayout.CENTER);
			Main.window.revalidate();
			Main.window.repaint();
		}
		
	}
	
	public boolean isLiveView() { return liveView; }
	public void setLiveView()   { liveView = true; }
	public void setNonLiveView(int sampleNumber) {
		
		liveView = false;
		nonLiveViewSampleNumber = sampleNumber;
		
	}
	
	/**
	 * Implements the smoothstep algorithm, with a "left edge" of 0 and a "right edge" of 1.
	 * 
	 * @param x    Input, in the range of 0-1 inclusive.
	 * @return     Output, in the range of 0-1 inclusive.
	 */
	private double smoothstep(double x) {
		
		if(x < 0) {
			return 0;
		} else if(x > 1) {
			return 1;
		} else {
			return x * x * (3 - 2 * x);
		}
		
	}
	
	/**
	 * Draws a tile, the tile's drop-shadow, and a margin around the tile.
	 * 
	 * @param gl            The OpenGL context.
	 * @param lowerLeftX    Lower-left x location.
	 * @param lowerLeftY    Lower-left y location.
	 * @param width         Total region width, including the tile, drop-shadow and margin.
	 * @param height        Total region height, including the tile, drop-shadow and margin.
	 */
	private void drawTile(GL2ES3 gl, int lowerLeftX, int lowerLeftY, int width, int height) {
		
		// draw the tile's drop-shadow
		OpenGL.drawBox(gl,
		               Theme.tileShadowColor,
		               lowerLeftX + Theme.tilePadding + Theme.tileShadowOffset,
		               lowerLeftY + Theme.tilePadding - Theme.tileShadowOffset,
		               width - 2*Theme.tilePadding,
		               height - 2*Theme.tilePadding);

		// draw the tile
		OpenGL.drawBox(gl,
		               Theme.tileColor,
		               lowerLeftX + Theme.tilePadding,
		               lowerLeftY + Theme.tilePadding,
		               width - 2*Theme.tilePadding,
		               height - 2*Theme.tilePadding);
		
	}
	
	/**
	 * Draws an "X" close chart button for the user to click on.
	 * If the mouse is over the button, also registers a click handler.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 */
	private void drawChartCloseButton(GL2ES3 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * ChartsController.getDisplayScalingFactor();
		float inset = buttonWidth * 0.2f;
		float buttonXleft = xOffset + width - buttonWidth;
		float buttonXright = xOffset + width;
		float buttonYtop = yOffset + height;
		float buttonYbottom = yOffset + height - buttonWidth;
		boolean mouseOverButton = mouseX >= buttonXleft && mouseX <= buttonXright && mouseY >= buttonYbottom && mouseY <= buttonYtop;
		float[] white = new float[] {1, 1, 1, 1};
		float[] black = new float[] {0, 0, 0, 1};
		
		// draw button background and outline
		OpenGL.drawBox       (gl, mouseOverButton ? black : white, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		OpenGL.drawBoxOutline(gl, mouseOverButton ? white : black, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		
		// draw the "X"
		OpenGL.buffer.rewind();
		OpenGL.buffer.put(buttonXleft  + inset); OpenGL.buffer.put(buttonYtop    - inset);
		OpenGL.buffer.put(buttonXright - inset); OpenGL.buffer.put(buttonYbottom + inset);
		OpenGL.buffer.put(buttonXleft  + inset); OpenGL.buffer.put(buttonYbottom + inset);
		OpenGL.buffer.put(buttonXright - inset); OpenGL.buffer.put(buttonYtop    - inset);
		OpenGL.buffer.rewind();
		OpenGL.drawLinesXy(gl, GL3.GL_LINES, mouseOverButton ? white : black, OpenGL.buffer, 4);
		
		// event handler
		if(mouseOverButton)
			eventHandler = EventHandler.onPress(event -> {
				removing = true;
				removingChart = chartUnderMouse;
				removingAnimationEndTime = System.currentTimeMillis() + removingAnimationDuration;
				ChartsController.updateTileOccupancy(removingChart);
			});
		
	}
	
	/**
	 * Draws a chart maximize button (rectangle icon) for the user to click on.
	 * If the mouse is over the button, also registers a click handler.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 */
	private void drawChartMaximizeButton(GL2ES3 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * ChartsController.getDisplayScalingFactor();
		float inset = buttonWidth * 0.2f;
		float offset = buttonWidth + 1;
		float buttonXleft = xOffset + width - buttonWidth - offset;
		float buttonXright = xOffset + width - offset;
		float buttonYtop = yOffset + height;
		float buttonYbottom = yOffset + height - buttonWidth;
		boolean mouseOverButton = mouseX >= buttonXleft && mouseX <= buttonXright && mouseY >= buttonYbottom && mouseY <= buttonYtop;
		float[] white = new float[] {1, 1, 1, 1};
		float[] black = new float[] {0, 0, 0, 1};
		
		// draw button background and outline
		OpenGL.drawBox       (gl, mouseOverButton ? black : white, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		OpenGL.drawBoxOutline(gl, mouseOverButton ? white : black, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		
		// draw the rectangle
		OpenGL.drawBoxOutline(gl, mouseOverButton ? white : black, buttonXleft + inset, buttonYbottom + inset, buttonWidth - 2*inset, buttonWidth - 2*inset);
		OpenGL.drawBox(gl, mouseOverButton ? white : black, buttonXleft + inset, buttonYtop - inset - (inset / 1.5f), buttonWidth - 2*inset, inset / 1.5f);
		
		// event handler
		if(mouseOverButton)
			eventHandler = EventHandler.onPress(event -> {
				if(maximizedChart == null) {
					maximizing = true;
					maximizedChart = chartUnderMouse;
					maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
					ChartsController.drawChartLast(maximizedChart); // ensure the chart is drawn on top of the others during the maximize animation
				} else {
					demaximizing = true;
					maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
				}
			});
		
	}
	
	/**
	 * Draws a chart settings button (gear icon) for the user to click on.
	 * If the mouse is over the button, also registers a click handler.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 */
	private void drawChartSettingsButton(GL2ES3 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * ChartsController.getDisplayScalingFactor();
		float offset = (buttonWidth + 1) * 2;
		float buttonXleft = xOffset + width - buttonWidth - offset;
		float buttonXright = xOffset + width - offset;
		float buttonYtop = yOffset + height;
		float buttonYbottom = yOffset + height - buttonWidth;
		boolean mouseOverButton = mouseX >= buttonXleft && mouseX <= buttonXright && mouseY >= buttonYbottom && mouseY <= buttonYtop;
		float[] white = new float[] {1, 1, 1, 1};
		float[] black = new float[] {0, 0, 0, 1};
		
		int teethCount = 7;
		int vertexCount = teethCount * 4;
		float gearCenterX = buttonXright - (buttonWidth / 2);
		float gearCenterY = buttonYtop - (buttonWidth / 2);
		float outerRadius = buttonWidth * 0.35f;
		float innerRadius = buttonWidth * 0.25f;
		float holeRadius  = buttonWidth * 0.10f;
		
		// draw button background and outline
		OpenGL.drawBox       (gl, mouseOverButton ? black : white, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		OpenGL.drawBoxOutline(gl, mouseOverButton ? white : black, buttonXleft, buttonYbottom, buttonWidth, buttonWidth);
		
		// draw the gear teeth
		OpenGL.buffer.rewind();
		for(int vertex = 0; vertex < vertexCount; vertex++) {
			float x = gearCenterX + (float) Math.cos((double) vertex / (double)vertexCount * 2 * Math.PI) * (vertex % 4 < 2 ? outerRadius : innerRadius);
			float y = gearCenterY + (float) Math.sin((double) vertex / (double)vertexCount * 2 * Math.PI) * (vertex % 4 < 2 ? outerRadius : innerRadius);
			OpenGL.buffer.put(x);
			OpenGL.buffer.put(y);
		}
		OpenGL.buffer.rewind();
		OpenGL.drawLinesXy(gl, GL3.GL_LINE_LOOP, mouseOverButton ? white : black, OpenGL.buffer, vertexCount);
		
		// draw the hole
		OpenGL.buffer.rewind();
		for(int vertex = 0; vertex < vertexCount; vertex++) {
			float x = gearCenterX + (float) Math.cos((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			float y = gearCenterY + (float) Math.sin((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			OpenGL.buffer.put(x);
			OpenGL.buffer.put(y);
		}
		OpenGL.buffer.rewind();
		OpenGL.drawLinesXy(gl, GL3.GL_LINE_LOOP, mouseOverButton ? white : black, OpenGL.buffer, vertexCount);
		
		// event handler
		if(mouseOverButton)
			eventHandler = EventHandler.onPress(event -> ConfigureView.instance.forExistingChart(chartUnderMouse));
		
	}
	
}