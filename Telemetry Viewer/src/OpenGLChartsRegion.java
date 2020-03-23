import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * Manages the grid region and all charts on the screen.
 * 
 * Users can click-and-drag in this region to create new charts or interact with existing charts.
 */
@SuppressWarnings("serial")
public class OpenGLChartsRegion extends JPanel {
	
	static OpenGLChartsRegion instance = new OpenGLChartsRegion();
	
	Animator animator;
	GLCanvas glCanvas;
	int canvasWidth;
	int canvasHeight;
	double dpiScalingFactor = 1;
	
	// grid size
	int columnCount;
	int rowCount;
	
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
	Consumer<MouseEvent> clickHandler;
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
	
	boolean antialiasing;
	
	JFrame parentWindow;
	
	float[] screenMatrix = new float[16];
	
	private OpenGLChartsRegion() {
		
		super();
		
		columnCount = SettingsController.getTileColumns();
		rowCount    = SettingsController.getTileRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
		liveView = true;
		nonLiveViewSampleNumber = 0;
		zoomLevel = 1;
		
		mouseX = -1;
		mouseY = -1;
		
		antialiasing = false;
		
		parentWindow = (JFrame) SwingUtilities.windowForComponent(this);
		
		GLCapabilities capabilities = null;
		try {
			capabilities = new GLCapabilities(GLProfile.get(GLProfile.GL2));
		} catch(Exception | InternalError e) {
			NotificationsController.showFailureForSeconds("Error: Unable to create the OpenGL context.\nThis may be due to a graphics driver problem, or an outdated graphics card.\n\"" + e.getMessage() + "\"", 999, false);
			return;
		}
		glCanvas = new GLCanvas(capabilities);
		glCanvas.addGLEventListener(new GLEventListener() {

			@Override public void init(GLAutoDrawable drawable) {
				
				GL2 gl = drawable.getGL().getGL2();
			
				gl.glEnable(GL2.GL_BLEND);
				gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
				
				if(SettingsController.getAntialiasing()) {
					gl.glEnable(GL2.GL_POINT_SMOOTH);
				    gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_FASTEST);
					gl.glEnable(GL2.GL_LINE_SMOOTH);
				    gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_FASTEST);
				    antialiasing = true;
				}
			    
				gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
			    
				gl.glLineWidth(Theme.lineWidth);
				gl.glPointSize(Theme.pointSize);
				
				gl.setSwapInterval(1);
				
				gl.glGenQueries(2, gpuQueryHandles, 0);
				
				OpenGL.makeAllPrograms(gl);
				
				// FIXME this is a dirty hack to work around the display-scaling-before-first-chart causes an exception problem
				// should probably do the FontUtil field init'ing in here instead
				System.out.println(FontUtils.tickTextHeight);
				
				FontUtils.tickTextRenderer   = new TextRenderer(Theme.tickFont, true, true);
				FontUtils.tickTextHeight     = Theme.tickFont.createGlyphVector(FontUtils.tickTextRenderer.getFontRenderContext(), "Test").getPixelBounds(FontUtils.tickTextRenderer.getFontRenderContext(), 0, 0).height;
				FontUtils.legendTextRenderer = new TextRenderer(Theme.legendFont, true, true);
				FontUtils.legendTextHeight   = Theme.legendFont.createGlyphVector(FontUtils.legendTextRenderer.getFontRenderContext(), "Test").getPixelBounds(FontUtils.legendTextRenderer.getFontRenderContext(), 0, 0).height;
				FontUtils.xAxisTextRenderer  = new TextRenderer(Theme.xAxisFont, true, true);
				FontUtils.xAxisTextHeight    = Theme.xAxisFont.createGlyphVector(FontUtils.xAxisTextRenderer.getFontRenderContext(), "Test").getPixelBounds(FontUtils.xAxisTextRenderer.getFontRenderContext(), 0, 0).height;
				FontUtils.yAxisTextRenderer  = new TextRenderer(Theme.yAxisFont, true, true);
				FontUtils.yAxisTextHeight    = Theme.yAxisFont.createGlyphVector(FontUtils.yAxisTextRenderer.getFontRenderContext(), "Test").getPixelBounds(FontUtils.yAxisTextRenderer.getFontRenderContext(), 0, 0).height;
				
			}
						
			@Override public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				// work around java 9+ dpi scaling problem with JOGL
				dpiScalingFactor = ((Graphics2D) getGraphics()).getTransform().getScaleX();
				width = (int) (width * dpiScalingFactor);
				height = (int) (height * dpiScalingFactor);
				gl.glViewport(0, 0, width, height);
				
				OpenGL.makeOrthoMatrix(screenMatrix, 0, width, 0, height, -100000, 100000);
				OpenGL.useMatrix(gl, screenMatrix);
				
				canvasWidth = width;
				canvasHeight = height;
				
				Controller.setDisplayScalingFactorJava9((float) dpiScalingFactor);
				
			}

			@Override public void display(GLAutoDrawable drawable) {
				
				int tileWidth   = canvasWidth  / columnCount;
				int tileHeight  = canvasHeight / rowCount;
				int tilesYoffset = canvasHeight - (tileHeight * rowCount);
				
				// prepare OpenGL
				GL2 gl = drawable.getGL().getGL2();
				OpenGL.useMatrix(gl, screenMatrix);
				
				gl.glClearColor(Theme.neutralColor[0], Theme.neutralColor[1], Theme.neutralColor[2], Theme.neutralColor[3]);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				
				gl.glLineWidth(Theme.lineWidth);
				gl.glPointSize(Theme.pointSize);
				
				// enable or disable antialiasing as needed
				if(antialiasing != SettingsController.getAntialiasing()) {
					antialiasing = SettingsController.getAntialiasing();
					if(antialiasing) {
						gl.glEnable(GL2.GL_POINT_SMOOTH);
					    gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_FASTEST);
						gl.glEnable(GL2.GL_LINE_SMOOTH);
					    gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_FASTEST);
					} else {
						gl.glDisable(GL2.GL_POINT_SMOOTH);
						gl.glDisable(GL2.GL_LINE_SMOOTH);
					}
				}
				
				// if there is no connection and no charts, we're done, do not draw any tiles
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				// draw every tile
				for(int column = 0; column < columnCount; column++) {
					for(int row = 0; row < rowCount; row++) {
						int lowerLeftX = tileWidth * column;
						int lowerLeftY = tileHeight * row + tilesYoffset;
						drawTile(gl, lowerLeftX, lowerLeftY, tileWidth, tileHeight);
					}
				}
				
				// draw a bounding box where the user is actively clicking-and-dragging to place a new chart
				OpenGL.drawBox(gl,
				               Theme.tileSelectedColor,
				               startX < endX ? startX * tileWidth : endX * tileWidth,
				               startY < endY ? canvasHeight - (endY + 1)*tileHeight : canvasHeight - (startY + 1)*tileHeight,
				               (Math.abs(endX - startX) + 1) * tileWidth,
				               (Math.abs(endY - startY) + 1) * tileHeight);
				
				// if there are no charts, ensure we switch back to live view
				List<PositionedChart> charts = Controller.getCharts();
				if(charts.size() == 0)
					liveView = true;
				
				int lastSampleNumber = liveView ? DatasetsController.getSampleCount() - 1 : nonLiveViewSampleNumber;
				
				// ensure active slots don't get flushed to disk
				int lastSampleNumberOnScreen = lastSampleNumber;
				int firstSampleNumberOnScreen = lastSampleNumberOnScreen;
				for(PositionedChart c : Controller.getCharts()) {
					if(lastSampleNumberOnScreen - c.sampleCount < firstSampleNumberOnScreen)
						firstSampleNumberOnScreen = lastSampleNumberOnScreen - c.sampleCount;
				}
				DatasetsController.dontFlushRangeOnScreen(firstSampleNumberOnScreen, lastSampleNumberOnScreen);
				
				// if the maximized chart was removed, forget about it
				if(maximizedChart != null && !charts.contains(maximizedChart))
					maximizedChart = null;
				
				// draw the charts
				//
				// the modelview matrix is translated so the origin will be at the bottom-left for each chart.
				// the scissor test is used to clip rendering to the region allocated for each chart.
				// if charts will be using off-screen framebuffers, they need to disable the scissor test when (and only when) drawing off-screen.
				// after the chart is drawn with OpenGL, any text queued for rendering will be drawn on top.
				clickHandler = null;
				chartUnderMouse = null;
				for(PositionedChart chart : charts) {
					
					// if there is a maximized chart, only draw that chart
					if(maximizedChart != null && maximizedChart != removingChart && chart != maximizedChart && !maximizing && !demaximizing)
						continue;
					
					// calculate cpu/gpu benchmarks for this chart if benchmarking
					if(chart == SettingsController.getBenchmarkedChart()) {
						previousCpuMilliseconds = (cpuStopNanoseconds - cpuStartNanoseconds) / 1000000.0;
						gl.glGetQueryObjecti64v(gpuQueryHandles[0], GL2.GL_QUERY_RESULT, gpuTimes, 0);
						gl.glGetQueryObjecti64v(gpuQueryHandles[1], GL2.GL_QUERY_RESULT, gpuTimes, 1);
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
						cpuStartNanoseconds = System.nanoTime();
						gl.glQueryCounter(gpuQueryHandles[0], GL2.GL_TIMESTAMP);
					}
					
					// draw the tile
					int width = tileWidth * (chart.bottomRightX - chart.topLeftX + 1);
					int height = tileHeight * (chart.bottomRightY - chart.topLeftY + 1);
					int xOffset = chart.topLeftX * tileWidth;
					int yOffset = canvasHeight - (chart.topLeftY * tileHeight) - height;
					
					// size the maximized chart correctly
					if(chart == maximizedChart) {
						
						double animationPosition = 1.0 - (double) (maximizingAnimationEndTime - System.currentTimeMillis()) / maximizingAnimationDuration;
						animationPosition = smoothstep(animationPosition);
						
						int maximizedWidth = tileWidth * columnCount;
						int maximizedHeight = tileHeight * rowCount;
						int maximizedXoffset = 0;
						int maximizedYoffset = canvasHeight - maximizedHeight;

						if(maximizing) {
							
							width = (int) Math.round(width * (1.0 - animationPosition) + (maximizedWidth * animationPosition));
							height = (int) Math.round(height * (1.0 - animationPosition) + (maximizedHeight * animationPosition));
							xOffset = (int) Math.round(xOffset * (1.0 - animationPosition) + (maximizedXoffset * animationPosition));
							yOffset = (int) Math.round(yOffset * (1.0 - animationPosition) + (maximizedYoffset * animationPosition));
							
							if(animationPosition == 1.0)
								maximizing = false;
							
						} else if(demaximizing) {
							
							width = (int) Math.round((width * animationPosition) + (maximizedWidth * (1.0 - animationPosition)));
							height = (int) Math.round((height * animationPosition) + (maximizedHeight * (1.0 - animationPosition)));
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
						
						xOffset = (int) Math.round(xOffset + (0.5 * width * animationPosition));
						yOffset = (int) Math.round(yOffset + (0.5 * height * animationPosition));
						width = (int) Math.round(width * (1.0 - animationPosition));
						height = (int) Math.round(height * (1.0 - animationPosition));
						
					}
					
					drawTile(gl, xOffset, yOffset, width, height);
					
					// draw the chart
					xOffset += Theme.tilePadding;
					yOffset += Theme.tilePadding;
					width  -= 2 * Theme.tilePadding;
					height -= 2 * Theme.tilePadding;
					
					gl.glEnable(GL2.GL_SCISSOR_TEST);
					gl.glScissor(xOffset, yOffset, width, height);
					
					float[] chartMatrix = Arrays.copyOf(screenMatrix, 16);
					OpenGL.translateMatrix(chartMatrix, xOffset, yOffset, 0);
					OpenGL.useMatrix(gl, chartMatrix);
					
					FontUtils.setOffsets(xOffset, yOffset, canvasWidth, canvasHeight);
					Consumer<MouseEvent> eventHandler = chart.drawChart(gl, chartMatrix, width, height, lastSampleNumber, zoomLevel, mouseX - xOffset, mouseY - yOffset);
					FontUtils.drawQueuedText(gl);
					
					// draw the cpu/gpu benchmarks for this chart if benchmarking
					if(chart == SettingsController.getBenchmarkedChart()) {
						cpuStopNanoseconds = System.nanoTime();
						gl.glQueryCounter(gpuQueryHandles[1], GL2.GL_TIMESTAMP);
						gl.glScissor((int) (xOffset - Theme.tilePadding), (int) (yOffset - Theme.tilePadding), (int) (width + 2 * Theme.tilePadding), (int) (height + 2 * Theme.tilePadding));
						OpenGL.translateMatrix(chartMatrix, 0, -Theme.tilePadding, 0);
						OpenGL.useMatrix(gl, chartMatrix);
						String line1 = String.format("CPU = %.3fms (Average = %.3fms)", previousCpuMilliseconds, averageCpuMilliseconds);
						String line2 = String.format("GPU = %.3fms (Average = %.3fms)", previousGpuMilliseconds, averageGpuMilliseconds);
						float textHeight = 2 * FontUtils.tickTextHeight + Theme.tickTextPadding;
						float textWidth = Float.max(FontUtils.tickTextWidth(line1), FontUtils.tickTextWidth(line2));
						OpenGL.drawBox(gl, Theme.neutralColor, 0, 0, textWidth + Theme.tickTextPadding*2, textHeight + Theme.tickTextPadding*2);
						FontUtils.drawTickText(line1, (int) Theme.tickTextPadding, (int) (2 * Theme.tickTextPadding - Theme.tilePadding + FontUtils.tickTextHeight));
						FontUtils.drawTickText(line2, (int) Theme.tickTextPadding, (int) (Theme.tickTextPadding - Theme.tilePadding));
						FontUtils.drawQueuedText(gl);
						NotificationsController.showVerboseForSeconds(line1 + ", " + line2, 1, false);
					}
					
					OpenGL.useMatrix(gl, screenMatrix);
					gl.glDisable(GL2.GL_SCISSOR_TEST);

					// check if the mouse is over this chart
					width += (int) Theme.tileShadowOffset;
					if(mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height) {
						chartUnderMouse = chart;
						if(eventHandler != null)
							clickHandler = eventHandler;
						drawChartCloseButton(gl, xOffset, yOffset, width, height);
						drawChartMaximizeButton(gl, xOffset, yOffset, width, height);
						drawChartSettingsButton(gl, xOffset, yOffset, width, height);
					}
					
				}
				
				// remove a chart if necessary
				if(removing && removingAnimationEndTime <= System.currentTimeMillis()) {
					Controller.removeChart(removingChart);
					if(maximizedChart == removingChart)
						maximizedChart = null;
					removingChart = null;
					removing = false;
				}
				
				// show the FPS/period in the lower-left corner if enabled
				if(SettingsController.getFpsVisibility()) {
					String text = String.format("%2.1fFPS, %dms", animator.getLastFPS(), animator.getLastFPSPeriod());
					int padding = 10;
					float textHeight = FontUtils.xAxisTextHeight;
					float textWidth = FontUtils.xAxisTextWidth(text);
					OpenGL.drawBox(gl, Theme.neutralColor, 0, 0, textWidth + padding*2, textHeight + padding*2);
					FontUtils.setOffsets(0, 0, canvasWidth, canvasHeight);
					FontUtils.drawXaxisText(text, padding, padding);
					FontUtils.drawQueuedText(gl);
					NotificationsController.showVerboseForSeconds(text, 1, false);
				}
				
			}
			
			@Override public void dispose(GLAutoDrawable drawable) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				for(PositionedChart chart : Controller.getCharts())
					chart.dispose();
				
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
				
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				if(SettingsController.awaitingBenchmarkedChart()) {
					SettingsController.setBenchmarkedChart(chartUnderMouse);
					return;
				}
				
				if(clickHandler != null) {
					clickHandler.accept(me);
					return;
				}
				
				int proposedStartX = me.getX() * columnCount / getWidth();
				int proposedStartY = me.getY() * rowCount / getHeight();
				
				if(proposedStartX < columnCount && proposedStartY < rowCount && Controller.gridRegionAvailable(proposedStartX, proposedStartY, proposedStartX, proposedStartY)) {
					startX = endX = proposedStartX;
					startY = endY = proposedStartY;
				}
				
			}
			
			// the mouse was released, attempting to create a new chart
			@Override public void mouseReleased(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;

				if(endX == -1 || endY == -1)
					return;
			
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
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
				
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				if(endX == -1 || endY == -1)
					return;
				
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
			}
			
			// log the mouse position so a chart close icon can be drawn
			@Override public void mouseMoved(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				mouseX = (int) (me.getX() * dpiScalingFactor);
				mouseY = (int) ((glCanvas.getHeight() - me.getY()) * dpiScalingFactor);
				
			}
			
		});
		
		glCanvas.addMouseWheelListener(new MouseWheelListener() {
			
			// the mouse wheel was scrolled
			@Override public void mouseWheelMoved(MouseWheelEvent mwe) {

				double scrollAmount = mwe.getPreciseWheelRotation();
				double samplesPerScroll = CommunicationController.getSampleRate() / 4;
				double zoomPerScroll = 0.1;
				float  displayScalingPerScroll = 0.1f;
				
				if(Controller.getCharts().size() == 0 && mwe.isShiftDown() == false)
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
					float newFactor = Controller.getDisplayScalingFactorUser() * (1 - ((float)scrollAmount * displayScalingPerScroll));
					Controller.setDisplayScalingFactorUser(newFactor);
					
				}
				
			}
			
		});
		
		// update the column and row counts when they change
		SettingsController.addTileCountListener((columns, rows) -> {
			columnCount = columns;
			rowCount = rows;
		});
		
		// switch back to live view when samples are removed
		DatasetsController.addSampleCountListener(haveSamples -> {
			if(!haveSamples)
				liveView = true;
		});
		
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
	private void drawTile(GL2 gl, int lowerLeftX, int lowerLeftY, int width, int height) {
		
		// draw the background (margin)
		OpenGL.drawBox(gl,
		               Theme.neutralColor,
		               lowerLeftX,
		               lowerLeftY,
		               width,
		               height);
		
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
	private void drawChartCloseButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * Controller.getDisplayScalingFactor();
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
		OpenGL.drawLines2D(gl, mouseOverButton ? white : black, OpenGL.buffer, 4);
		
		// event handler
		if(mouseOverButton)
			clickHandler = event -> {
				removing = true;
				removingChart = chartUnderMouse;
				removingAnimationEndTime = System.currentTimeMillis() + removingAnimationDuration;
			};
		
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
	private void drawChartMaximizeButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * Controller.getDisplayScalingFactor();
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
			clickHandler = event -> {
				if(maximizedChart == null) {
					maximizing = true;
					maximizedChart = chartUnderMouse;
					maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
					Controller.drawChartLast(maximizedChart); // ensure the chart is drawn on top of the others during the maximize animation
				} else {
					demaximizing = true;
					maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
				}
			};
		
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
	private void drawChartSettingsButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		float buttonWidth = 15f * Controller.getDisplayScalingFactor();
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
		OpenGL.drawLineLoop2D(gl, mouseOverButton ? white : black, OpenGL.buffer, vertexCount);
		
		// draw the hole
		OpenGL.buffer.rewind();
		for(int vertex = 0; vertex < vertexCount; vertex++) {
			float x = gearCenterX + (float) Math.cos((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			float y = gearCenterY + (float) Math.sin((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			OpenGL.buffer.put(x);
			OpenGL.buffer.put(y);
		}
		OpenGL.buffer.rewind();
		OpenGL.drawLineLoop2D(gl, mouseOverButton ? white : black, OpenGL.buffer, vertexCount);
		
		// event handler
		if(mouseOverButton)
			clickHandler = event -> ConfigureView.instance.forExistingChart(chartUnderMouse);
		
	}
	
}