import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;

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

/**
 * Manages the grid region and all charts on the screen.
 * 
 * Users can click-and-drag in this region to create new charts or interact with existing charts.
 */
@SuppressWarnings("serial")
public class OpenGLChartsRegion extends JPanel {
	
	Animator animator;
	int canvasWidth;
	int canvasHeight;
	
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
	int nonLiveViewSamplesCount;
	double zoomLevel;
	
	// mouse pointer's current location (pixels, origin at bottom-left)
	int mouseX;
	int mouseY;
	PositionedChart chartToRemoveOnClick;
	PositionedChart chartToMaximizeOnClick;
	PositionedChart chartToConfigureOnClick;
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
	
	public OpenGLChartsRegion(SettingsView settingsRegion, ControlsRegion controlsRegion) {
		
		super();
		
		columnCount = SettingsController.getTileColumns();
		rowCount    = SettingsController.getTileRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
		liveView = true;
		nonLiveViewSamplesCount = 0;
		zoomLevel = 1;
		
		mouseX = -1;
		mouseY = -1;
		
		antialiasing = false;
		
		parentWindow = (JFrame) SwingUtilities.windowForComponent(this);
		
		GLCanvas glCanvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL2)));
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
				
			}
						
			@Override public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_PROJECTION);
				gl.glLoadIdentity();
				gl.glOrtho(0, width, 0, height, -100000, 100000);
				
				canvasWidth = width;
				canvasHeight = height;
				
			}

			@Override public void display(GLAutoDrawable drawable) {
				
				int tileWidth   = canvasWidth  / columnCount;
				int tileHeight  = canvasHeight / rowCount;
				int tilesYoffset = canvasHeight - (tileHeight * rowCount);
				
				// prepare OpenGL
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glLoadIdentity();
				
				gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
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
				
				// draw a neutral background
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor4fv(Theme.neutralColor, 0);
					gl.glVertex2f(0,           0);
					gl.glVertex2f(0,           canvasHeight);
					gl.glVertex2f(canvasWidth, canvasHeight);
					gl.glVertex2f(canvasWidth, 0);
				gl.glEnd();
				
				// if there is no connection and no charts, we're done, do not draw any tiles
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				// draw every tile
				for(int column = 0; column < columnCount; column++) {
					for(int row = 0; row < rowCount; row++) {
						
						gl.glColor4fv(Theme.tileShadowColor, 0);
						gl.glBegin(GL2.GL_QUADS);
							gl.glVertex2f(tileWidth * column       + Theme.tilePadding + Theme.tileShadowOffset, tilesYoffset + tileHeight * row       + Theme.tilePadding - Theme.tileShadowOffset);
							gl.glVertex2f(tileWidth * column       + Theme.tilePadding + Theme.tileShadowOffset, tilesYoffset + tileHeight * (row + 1) - Theme.tilePadding - Theme.tileShadowOffset);
							gl.glVertex2f(tileWidth * (column + 1) - Theme.tilePadding + Theme.tileShadowOffset, tilesYoffset + tileHeight * (row + 1) - Theme.tilePadding - Theme.tileShadowOffset);
							gl.glVertex2f(tileWidth * (column + 1) - Theme.tilePadding + Theme.tileShadowOffset, tilesYoffset + tileHeight * row       + Theme.tilePadding - Theme.tileShadowOffset);
						gl.glEnd();
						
						gl.glColor4fv(Theme.tileColor, 0);
						gl.glBegin(GL2.GL_QUADS);
							gl.glVertex2f(tileWidth * column       + Theme.tilePadding, tilesYoffset + tileHeight * row       + Theme.tilePadding);
							gl.glVertex2f(tileWidth * column       + Theme.tilePadding, tilesYoffset + tileHeight * (row + 1) - Theme.tilePadding);
							gl.glVertex2f(tileWidth * (column + 1) - Theme.tilePadding, tilesYoffset + tileHeight * (row + 1) - Theme.tilePadding);
							gl.glVertex2f(tileWidth * (column + 1) - Theme.tilePadding, tilesYoffset + tileHeight * row       + Theme.tilePadding);
						gl.glEnd();
						
					}
				}
				
				// draw a bounding box where the user is actively clicking-and-dragging to place a new chart
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor4fv(Theme.tileSelectedColor, 0);
					int x1 = startX < endX ? startX * tileWidth : endX * tileWidth;
					int y1 = startY < endY ? startY * tileHeight   : endY * tileHeight;
					int x2 = x1 + (Math.abs(endX - startX) + 1) * tileWidth;
					int y2 = y1 + (Math.abs(endY - startY) + 1) * tileHeight;
					y1 = canvasHeight - y1;
					y2 = canvasHeight - y2;
					gl.glVertex2f(x1, y1);
					gl.glVertex2f(x1, y2);
					gl.glVertex2f(x2, y2);
					gl.glVertex2f(x2, y1);
				gl.glEnd();	
				
				// if there are no charts, ensure we switch back to live view
				List<PositionedChart> charts = Controller.getCharts();
				if(charts.size() == 0)
					liveView = true;	
				
				// draw the charts
				//
				// the modelview matrix is translated so the origin will be at the bottom-left for each chart.
				// the scissor test is used to clip rendering to the region allocated for each chart.
				// if charts will be using off-screen framebuffers, they need to disable the scissor test when (and only when) drawing off-screen.
				// after the chart is drawn with OpenGL, any text queued for rendering will be drawn on top.
				chartToRemoveOnClick = null;
				chartToMaximizeOnClick = null;
				chartToConfigureOnClick = null;
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
					gl.glPushMatrix();
					gl.glTranslatef(xOffset, yOffset, 0);
					
					FontUtils.setOffsets(xOffset, yOffset, canvasWidth, canvasHeight);
					int lastSampleNumber = liveView ? DatasetsController.getSampleCount() - 1 : nonLiveViewSamplesCount;
					chart.drawChart(gl, width, height, lastSampleNumber, zoomLevel, mouseX - xOffset, mouseY - yOffset);
					FontUtils.drawQueuedText(gl);
					
					// draw the cpu/gpu benchmarks for this chart if benchmarking
					if(chart == SettingsController.getBenchmarkedChart()) {
						cpuStopNanoseconds = System.nanoTime();
						gl.glQueryCounter(gpuQueryHandles[1], GL2.GL_TIMESTAMP);
						gl.glScissor((int) (xOffset - Theme.tilePadding), (int) (yOffset - Theme.tilePadding), (int) (width + 2 * Theme.tilePadding), (int) (height + 2 * Theme.tilePadding));
						gl.glTranslatef(0, -Theme.tilePadding, 0);
						String line1 = String.format("CPU = %.3fms (Average = %.3fms)", previousCpuMilliseconds, averageCpuMilliseconds);
						String line2 = String.format("GPU = %.3fms (Average = %.3fms)", previousGpuMilliseconds, averageGpuMilliseconds);
						float textHeight = 2 * FontUtils.tickTextHeight + Theme.tickTextPadding;
						float textWidth = Float.max(FontUtils.tickTextWidth(line1), FontUtils.tickTextWidth(line2));
						gl.glColor4fv(Theme.neutralColor, 0);
						gl.glBegin(GL2.GL_QUADS);
							gl.glVertex2f(0, 0);
							gl.glVertex2f(0, textHeight + Theme.tickTextPadding * 2);
							gl.glVertex2f(textWidth + Theme.tickTextPadding * 2, textHeight + Theme.tickTextPadding * 2);
							gl.glVertex2f(textWidth + Theme.tickTextPadding * 2, 0);
						gl.glEnd();
						FontUtils.drawTickText(line1, (int) Theme.tickTextPadding, (int) (2 * Theme.tickTextPadding - Theme.tilePadding + FontUtils.tickTextHeight));
						FontUtils.drawTickText(line2, (int) Theme.tickTextPadding, (int) (Theme.tickTextPadding - Theme.tilePadding));
						FontUtils.drawQueuedText(gl);
						NotificationsController.showVerboseForSeconds(line1 + ", " + line2, 1, false);
					}
					
					gl.glPopMatrix();
					gl.glDisable(GL2.GL_SCISSOR_TEST);
					
					// draw the chart's configure / maximize / close buttons
					width += (int) Theme.tileShadowOffset;
					boolean mouseOverCloseButton = drawChartCloseButton(gl, xOffset, yOffset, width, height);
					if(mouseOverCloseButton)
						chartToRemoveOnClick = chart;
					
					boolean mouseOverMaximimizeButton = drawChartMaximizeButton(gl, xOffset, yOffset, width, height);
					if(mouseOverMaximimizeButton)
						chartToMaximizeOnClick = chart;
					
					boolean mouseOverConfigureButton = drawChartSettingsButton(gl, xOffset, yOffset, width, height);
					if(mouseOverConfigureButton)
						chartToConfigureOnClick = chart;
					
					if(mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height)
						chartUnderMouse = chart;
					
					// periodically check if running out of heap space
					int lastSampleNumberOnScreen = lastSampleNumber;
					int firstSampleNumberOnScreen = lastSampleNumberOnScreen;
					for(PositionedChart c : Controller.getCharts()) {
						if(lastSampleNumberOnScreen - c.sampleCount < firstSampleNumberOnScreen)
							firstSampleNumberOnScreen = lastSampleNumberOnScreen - c.sampleCount;
					}
					DatasetsController.flushIfNecessaryExcept(firstSampleNumberOnScreen, lastSampleNumberOnScreen);
					
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
					gl.glColor4fv(Theme.neutralColor, 0);
					gl.glBegin(GL2.GL_QUADS);
						gl.glVertex2f(0, 0);
						gl.glVertex2f(0, textHeight + padding * 2);
						gl.glVertex2f(textWidth + padding * 2, textHeight + padding * 2);
						gl.glVertex2f(textWidth + padding * 2, 0);
					gl.glEnd();
					FontUtils.setOffsets(0, 0, canvasWidth, canvasHeight);
					FontUtils.drawXaxisText(text, padding, padding);
					FontUtils.drawQueuedText(gl);
					NotificationsController.showVerboseForSeconds(text, 1, false);
				}
				
			}
			
			@Override public void dispose(GLAutoDrawable drawable) {
				
			}
			
		});
		
		setLayout(new BorderLayout());
		add(glCanvas, BorderLayout.CENTER);
	
		animator = new Animator(glCanvas);
		animator.setUpdateFPSFrames(1, null);
		animator.start();
		
		glCanvas.addMouseListener(new MouseListener() {
			
			// the mouse was pressed, attempting to start a new chart region, or to configure/remove an existing chart
			@Override public void mousePressed(MouseEvent me) {
				
				if(!CommunicationController.isConnected() && Controller.getCharts().isEmpty())
					return;
				
				if(SettingsController.awaitingBenchmarkedChart()) {
					SettingsController.setBenchmarkedChart(chartUnderMouse);
					return;
				}
				
				if(chartToRemoveOnClick != null) {
					removing = true;
					removingChart = chartToRemoveOnClick;
					removingAnimationEndTime = System.currentTimeMillis() + removingAnimationDuration;
					return;
				}
				
				if(chartToMaximizeOnClick != null) {
					if(maximizedChart == null) {
						maximizing = true;
						maximizedChart = chartToMaximizeOnClick;
						maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
						Controller.drawChartLast(maximizedChart); // ensure the chart is drawn on top of the others during the maximize animation
					} else {
						demaximizing = true;
						maximizingAnimationEndTime = System.currentTimeMillis() + maximizingAnimationDuration;
					}
					return;
				}
				
				if(chartToConfigureOnClick != null) {
					ConfigureView.existingChart(chartToConfigureOnClick);
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
				
				ConfigureView.newChart(x1, y1, x2, y2);
				
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
				
				mouseX = me.getX();
				mouseY = glCanvas.getHeight() - me.getY();
				
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
						nonLiveViewSamplesCount = (DatasetsController.getSampleCount() - 1);
					}
					
					double delta = scrollAmount * samplesPerScroll * zoomLevel;
					if(delta < -0.5 || delta > 0.5)
						delta = Math.round(delta);
					else if(delta < 0)
						delta = -1;
					else if(delta >= 0)
						delta = 1;
					nonLiveViewSamplesCount += delta;
					
					if(nonLiveViewSamplesCount >= DatasetsController.getSampleCount() - 1)
						liveView = true;
					
					if(nonLiveViewSamplesCount < 0)
						nonLiveViewSamplesCount = -1;
				
				} else if(mwe.isControlDown() == true) {
					
					// ctrl is down, so we're zooming
					zoomLevel *= 1 + (scrollAmount * zoomPerScroll);
					
					if(zoomLevel > 1)
						zoomLevel = 1;
					else if(zoomLevel < 0)
						zoomLevel = Double.MIN_VALUE;
					
				} else if(mwe.isShiftDown() == true) {
					
					// shift is down, so we're setting the display scaling factor
					float newFactor = Controller.getDisplayScalingFactor() * (1 - ((float)scrollAmount * displayScalingPerScroll));
					Controller.setDisplayScalingFactor(newFactor);
					
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
	 * @param gl         The OpenGL context.
	 * @param xOffset    Lower-left x location.
	 * @param yOffset    Lower-left y location.
	 * @param width      Total region width, including the tile, drop-shadow and margin.
	 * @param height     Total region height, including the tile, drop-shadow and margin.
	 */
	private void drawTile(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		// draw the background (margin)
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.neutralColor, 0);
			gl.glVertex2f(xOffset,         yOffset);
			gl.glVertex2f(xOffset,         yOffset + height);
			gl.glVertex2f(xOffset + width, yOffset + height);
			gl.glVertex2f(xOffset + width, yOffset);
		gl.glEnd();
		
		// draw the tile's drop-shadow
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.tileShadowColor, 0);
			gl.glVertex2f(xOffset         + Theme.tilePadding + Theme.tileShadowOffset, yOffset          + Theme.tilePadding - Theme.tileShadowOffset);
			gl.glVertex2f(xOffset         + Theme.tilePadding + Theme.tileShadowOffset, yOffset + height - Theme.tilePadding - Theme.tileShadowOffset);
			gl.glVertex2f(xOffset + width - Theme.tilePadding + Theme.tileShadowOffset, yOffset + height - Theme.tilePadding - Theme.tileShadowOffset);
			gl.glVertex2f(xOffset + width - Theme.tilePadding + Theme.tileShadowOffset, yOffset          + Theme.tilePadding - Theme.tileShadowOffset);
		gl.glEnd();

		// draw the tile
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.tileColor, 0);
			gl.glVertex2f(xOffset         + Theme.tilePadding, yOffset          + Theme.tilePadding);
			gl.glVertex2f(xOffset         + Theme.tilePadding, yOffset + height - Theme.tilePadding);
			gl.glVertex2f(xOffset + width - Theme.tilePadding, yOffset + height - Theme.tilePadding);
			gl.glVertex2f(xOffset + width - Theme.tilePadding, yOffset          + Theme.tilePadding);
		gl.glEnd();
		
	}
	
	/**
	 * Draws an "X" close chart button for the user to click on if the mouse is over this chart.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 * @return           True if the mouse cursor is over this button, false if not.
	 */
	private boolean drawChartCloseButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		// only draw if necessary
		boolean mouseOverChart = mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height;
		if(!mouseOverChart)
			return false;
		
		float buttonWidth = 15f * Controller.getDisplayScalingFactor();
		float inset = buttonWidth * 0.2f;
		float buttonXleft = xOffset + width - buttonWidth;
		float buttonXright = xOffset + width;
		float buttonYtop = yOffset + height;
		float buttonYbottom = yOffset + height - buttonWidth;
		boolean mouseOverButton = mouseX >= buttonXleft && mouseX <= buttonXright && mouseY >= buttonYbottom && mouseY <= buttonYtop;
		float[] white = new float[] {1, 1, 1, 1};
		float[] black = new float[] {0, 0, 0, 1};
		
		// draw button background
		gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(mouseOverButton ? black : white, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw button outline
		gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw the "X"
		gl.glBegin(GL2.GL_LINES);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			gl.glVertex2f(buttonXleft  + inset, buttonYtop    - inset);
			gl.glVertex2f(buttonXright - inset, buttonYbottom + inset);
			gl.glVertex2f(buttonXleft  + inset, buttonYbottom + inset);
			gl.glVertex2f(buttonXright - inset, buttonYtop    - inset);
		gl.glEnd();
		
		return mouseOverButton;
		
	}
	
	/**
	 * Draws a chart maximize button (rectangle icon) for the user to click on if the mouse is over this chart.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 * @return           True if the mouse cursor is over this button, false if not.
	 */
	private boolean drawChartMaximizeButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		// only draw if necessary
		boolean mouseOverChart = mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height;
		if(!mouseOverChart)
			return false;
		
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
		
		// draw button background
		gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(mouseOverButton ? black : white, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw button outline
		gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw the rectangle
		gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			gl.glVertex2f(buttonXleft + inset, buttonYbottom + inset);
			gl.glVertex2f(buttonXleft + inset, buttonYtop - inset);
			gl.glVertex2f(buttonXright - inset, buttonYtop - inset);
			gl.glVertex2f(buttonXright - inset, buttonYbottom + inset);
		gl.glEnd();
		gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2f(buttonXleft + inset, buttonYtop - inset);
			gl.glVertex2f(buttonXright - inset, buttonYtop - inset);
			gl.glVertex2f(buttonXright - inset, buttonYtop - inset - (inset / 1.5f));
			gl.glVertex2f(buttonXleft + inset, buttonYtop - inset - (inset / 1.5f));
		gl.glEnd();
		
		return mouseOverButton;
		
	}
	
	/**
	 * Draws a chart settings button (gear icon) for the user to click on if the mouse is over this chart.
	 * 
	 * @param gl         The OpenGL context.
	 * @param xOffset    Chart region lower-left x location.
	 * @param yOffset    Chart region lower-left y location.
	 * @param width      Chart region width.
	 * @param height     Chart region height.
	 * @return           True if the mouse cursor is over this button, false if not.
	 */
	private boolean drawChartSettingsButton(GL2 gl, int xOffset, int yOffset, int width, int height) {
		
		// only draw if necessary
		boolean mouseOverChart = mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height;
		if(!mouseOverChart)
			return false;
		
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
		
		// draw button background
		gl.glBegin(GL2.GL_QUADS);
			gl.glColor4fv(mouseOverButton ? black : white, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw button outline
		gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			gl.glVertex2f(buttonXleft, buttonYbottom);
			gl.glVertex2f(buttonXleft, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYtop);
			gl.glVertex2f(buttonXright, buttonYbottom);
		gl.glEnd();
		
		// draw the gear teeth
		gl.glBegin(GL2.GL_LINE_LOOP);
			gl.glColor4fv(mouseOverButton ? white : black, 0);
			for(int vertex = 0; vertex < vertexCount; vertex++) {
				float x = gearCenterX + (float) Math.cos((double) vertex / (double)vertexCount * 2 * Math.PI) * (vertex % 4 < 2 ? outerRadius : innerRadius);
				float y = gearCenterY + (float) Math.sin((double) vertex / (double)vertexCount * 2 * Math.PI) * (vertex % 4 < 2 ? outerRadius : innerRadius);
				gl.glVertex2f(x, y);
			}
		gl.glEnd();
		
		// draw the hole
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(mouseOverButton ? white : black, 0);
		for(int vertex = 0; vertex < vertexCount; vertex++) {
			float x = gearCenterX + (float) Math.cos((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			float y = gearCenterY + (float) Math.sin((double) vertex / (double)vertexCount * 2 * Math.PI) * holeRadius;
			gl.glVertex2f(x, y);
		}
		gl.glEnd();
		
		return mouseOverButton;
		
	}
	
}