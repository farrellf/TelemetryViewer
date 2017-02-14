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
	
	boolean serialPortConnected;
	
	public OpenGLChartsRegion(ControlsRegion controlsRegion) {
		
		super();
		
		columnCount = Controller.getGridColumns();
		rowCount    = Controller.getGridRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
		liveView = true;
		nonLiveViewSamplesCount = 0;
		zoomLevel = 1;
		
		mouseX = -1;
		mouseY = -1;
		chartToRemoveOnClick = null;
		
		serialPortConnected = false;
		
		GLCanvas glCanvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL2)));
		glCanvas.addGLEventListener(new GLEventListener() {

			@Override public void init(GLAutoDrawable drawable) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glEnable(GL2.GL_BLEND);
				gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
				gl.glEnable(GL2.GL_POINT_SMOOTH);
			    gl.glHint(GL2.GL_POINT_SMOOTH_HINT, GL2.GL_FASTEST);
				gl.glEnable(GL2.GL_LINE_SMOOTH);
			    gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_FASTEST);
//				gl.glEnable(GL2.GL_POLYGON_SMOOTH);
//			    gl.glHint(GL2.GL_POLYGON_SMOOTH_HINT, GL2.GL_FASTEST);
			    
				gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
			    
				gl.glLineWidth(Theme.lineWidth);
				gl.glPointSize(Theme.pointSize);
				
				gl.setSwapInterval(1);
				
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
				
				// if there are no charts and no serial port connection, tell the user to connect or open a layout
				if(!serialPortConnected && Controller.getCharts().size() == 0) {
					
					// draw the background
					gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(Theme.neutralColor, 0);
						gl.glVertex2f(0,           0);
						gl.glVertex2f(0,           canvasHeight);
						gl.glVertex2f(canvasWidth, canvasHeight);
						gl.glVertex2f(canvasWidth, 0);
					gl.glEnd();
					
					// draw the text
					String message = "Start by connecting to a serial port or opening a layout file.";
					float messageWidth = FontUtils.xAxisTextWidth(message);
					float messageHeight = FontUtils.xAxisTextHeight;
					float xMessageLeft = (canvasWidth / 2.0f) - (messageWidth / 2.0f);
					float yMessageBottom = (canvasHeight / 2.0f) - (messageHeight / 2.0f);
					FontUtils.setOffsets(0, 0);
					FontUtils.drawXaxisText(message, (int) xMessageLeft, (int) yMessageBottom);
					FontUtils.drawQueuedText(gl, canvasWidth, canvasHeight);
					
					// arrow settings
					float arrowHeight = 50 * Controller.getDisplayScalingFactor();
					float arrowWidth = arrowHeight * 0.6f;
					float arrowStemWidth = arrowWidth / 3;
					float arrowStemHeight = arrowHeight * 0.65f;
					float arrowYoffset = arrowHeight / 6;
					
					// arrow transparency fades in and out once per 2 seconds
					long time = System.currentTimeMillis();
					boolean even = (time / 2000) % 2 == 0;
					float transparency = (time % 2000) / 2000.0f;
					float arrowTransparency = even ? transparency : 1 - transparency;
					
					// draw an arrow above the "Open Layout" button
					float xCenterOfOpenLayoutButton = controlsRegion.getOpenLayoutButtonLocation();
					float xOpenLayoutArrowLeft = xCenterOfOpenLayoutButton - (arrowWidth / 2);
					float xOpenLayoutArrowRight = xCenterOfOpenLayoutButton + (arrowWidth / 2);
					float yOpenLayoutArrowBottom = arrowYoffset;
					float yOpenLayoutArrowTop = yOpenLayoutArrowBottom + arrowHeight;
					float yOpenLayoutArrowMiddle = yOpenLayoutArrowTop - arrowStemHeight;
					float xOpenLayoutArrowMiddle = xOpenLayoutArrowLeft + (arrowWidth / 2);
					float xOpenLayoutArrowStemLeft = xOpenLayoutArrowMiddle - (arrowStemWidth / 2);
					float xOpenLayoutArrowStemRight = xOpenLayoutArrowMiddle + (arrowStemWidth / 2);
					
					gl.glColor4f(1, 0, 0, arrowTransparency);
					gl.glBegin(GL2.GL_TRIANGLES);
						gl.glVertex2f(xOpenLayoutArrowMiddle, yOpenLayoutArrowBottom);
						gl.glVertex2f(xOpenLayoutArrowLeft,   yOpenLayoutArrowMiddle);
						gl.glVertex2f(xOpenLayoutArrowRight,  yOpenLayoutArrowMiddle);
					gl.glEnd();
					gl.glBegin(GL2.GL_QUADS);
						gl.glVertex2f(xOpenLayoutArrowStemLeft,  yOpenLayoutArrowMiddle);
						gl.glVertex2f(xOpenLayoutArrowStemLeft,  yOpenLayoutArrowTop);
						gl.glVertex2f(xOpenLayoutArrowStemRight, yOpenLayoutArrowTop);
						gl.glVertex2f(xOpenLayoutArrowStemRight, yOpenLayoutArrowMiddle);
					gl.glEnd();
					
					// draw an arrow above the "Connect" button
					float xCenterOfConnectButton = controlsRegion.getConnectButtonLocation();
					float xConnectArrowLeft = xCenterOfConnectButton - (arrowWidth / 2);
					float xConnectArrowRight = xCenterOfConnectButton + (arrowWidth / 2);
					float yConnectArrowBottom = arrowYoffset;
					float yConnectArrowTop = yConnectArrowBottom + arrowHeight;
					float yConnectArrowMiddle = yConnectArrowTop - arrowStemHeight;
					float xConnectArrowMiddle = xConnectArrowLeft + (arrowWidth / 2);
					float xConnectArrowStemLeft = xConnectArrowMiddle - (arrowStemWidth / 2);
					float xConnectArrowStemRight = xConnectArrowMiddle + (arrowStemWidth / 2);
					
					gl.glColor4f(1, 0, 0, arrowTransparency);
					gl.glBegin(GL2.GL_TRIANGLES);
						gl.glVertex2f(xConnectArrowMiddle, yConnectArrowBottom);
						gl.glVertex2f(xConnectArrowLeft,   yConnectArrowMiddle);
						gl.glVertex2f(xConnectArrowRight,  yConnectArrowMiddle);
					gl.glEnd();
					gl.glBegin(GL2.GL_QUADS);
						gl.glVertex2f(xConnectArrowStemLeft,  yConnectArrowMiddle);
						gl.glVertex2f(xConnectArrowStemLeft,  yConnectArrowTop);
						gl.glVertex2f(xConnectArrowStemRight, yConnectArrowTop);
						gl.glVertex2f(xConnectArrowStemRight, yConnectArrowMiddle);
					gl.glEnd();
					
					return;
					
				}
				
				// a serial port connection exists, or charts exist, so draw the tiles and any charts

				// draw a neutral background
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor4fv(Theme.neutralColor, 0);
					gl.glVertex2f(0,           0);
					gl.glVertex2f(0,           canvasHeight);
					gl.glVertex2f(canvasWidth, canvasHeight);
					gl.glVertex2f(canvasWidth, 0);
				gl.glEnd();
				
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
				
				// if there are no charts, tell the user how to add one
				List<PositionedChart> charts = Controller.getCharts();
				if(charts.size() == 0) {
				
					liveView = true;
					
					// draw the text
					String message = "Add a chart by clicking on a tile, or by clicking-and-dragging across multiple tiles.";
					float messageWidth = FontUtils.xAxisTextWidth(message);
					float messageHeight = FontUtils.xAxisTextHeight;
					float xMessageLeft = (canvasWidth / 2.0f) - (messageWidth / 2.0f);
					float xMessageRight = xMessageLeft + messageWidth;
					float yMessageBottom = (canvasHeight / 2.0f) - (messageHeight / 2.0f);
					float yMessageTop = yMessageBottom + messageHeight;
					
					gl.glBegin(GL2.GL_QUADS);
					gl.glColor4fv(Theme.transparentNeutralColor, 0);
						gl.glVertex2f(xMessageLeft  - Theme.legendTextPadding, yMessageBottom - Theme.legendTextPadding);
						gl.glVertex2f(xMessageLeft  - Theme.legendTextPadding, yMessageTop    + Theme.legendTextPadding);
						gl.glVertex2f(xMessageRight + Theme.legendTextPadding, yMessageTop    + Theme.legendTextPadding);
						gl.glVertex2f(xMessageRight + Theme.legendTextPadding, yMessageBottom - Theme.legendTextPadding);
					gl.glEnd();
					
					FontUtils.setOffsets(0, 0);
					FontUtils.drawXaxisText(message, (int) xMessageLeft, (int) yMessageBottom);
					FontUtils.drawQueuedText(gl, canvasWidth, canvasHeight);
					
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
				
				// draw the charts
				//
				// the modelview matrix is translated so the origin will be at the bottom-left for each chart.
				// the scissor test is used to clip rendering to the region allocated for each chart.
				// if charts will be using off-screen framebuffers, they need to disable the scissor test when (and only when) drawing off-screen.
				// after the chart is drawn with OpenGL, any text queued for rendering will be drawn on top.
				int lastSampleNumber = liveView ? Controller.getSamplesCount() - 1 : nonLiveViewSamplesCount;
				PositionedChart chartToClose = null;
				gl.glEnable(GL2.GL_SCISSOR_TEST);
				for(PositionedChart chart : charts) {
					int width = tileWidth * (chart.bottomRightX - chart.topLeftX + 1);
					int height = tileHeight * (chart.bottomRightY - chart.topLeftY + 1);
					int xOffset = chart.topLeftX * tileWidth;
					int yOffset = chart.topLeftY * tileHeight;
					yOffset = canvasHeight - yOffset - height;
					gl.glScissor(xOffset, yOffset, width, height);
					gl.glPushMatrix();
					gl.glTranslatef(xOffset, yOffset, 0);
					FontUtils.setOffsets(xOffset, yOffset);
					chart.drawChart(gl, width, height, lastSampleNumber, zoomLevel);
					FontUtils.drawQueuedText(gl, canvasWidth, canvasHeight);
					
					boolean mouseOverThisChart = mouseX >= xOffset && mouseX <= xOffset + width && mouseY >= yOffset && mouseY <= yOffset + height;
					if(mouseOverThisChart) {
						float iconWidth = 15f * Controller.getDisplayScalingFactor();
						float inset = iconWidth * 0.2f;
						float closeIconX1 = xOffset + width - iconWidth;
						float closeIconX2 = xOffset + width;
						float closeIconY1 = yOffset + height - iconWidth;
						float closeIconY2 = yOffset + height;
						boolean mouseOverCloseIcon = mouseX >= closeIconX1 && mouseX <= closeIconX2 && mouseY >= closeIconY1 && mouseY <= closeIconY2;
						if(mouseOverCloseIcon)
							chartToClose = chart;
						gl.glBegin(GL2.GL_QUADS);
							if(mouseOverCloseIcon)
								gl.glColor4f(0, 0, 0, 1);
							else
								gl.glColor4f(1, 1, 1, 1);
							gl.glVertex2f(width,             height);
							gl.glVertex2f(width,             height - iconWidth);
							gl.glVertex2f(width - iconWidth, height - iconWidth);
							gl.glVertex2f(width - iconWidth, height);
						gl.glEnd();
						gl.glBegin(GL2.GL_LINE_LOOP);
							if(mouseOverCloseIcon)
								gl.glColor4f(1, 1, 1, 1);
							else
								gl.glColor4f(0, 0, 0, 1);
							gl.glVertex2f(width,             height);
							gl.glVertex2f(width,             height - iconWidth);
							gl.glVertex2f(width - iconWidth, height - iconWidth);
							gl.glVertex2f(width - iconWidth, height);
						gl.glEnd();
						gl.glBegin(GL2.GL_LINES);
							if(mouseOverCloseIcon)
								gl.glColor4f(1, 1, 1, 1);
							else
								gl.glColor4f(0, 0, 0, 1);
							gl.glVertex2f(width - inset, height - inset);
							gl.glVertex2f(width - iconWidth + inset, height - iconWidth + inset);
							gl.glVertex2f(width - iconWidth + inset, height - inset);
							gl.glVertex2f(width - inset, height - iconWidth + inset);
						gl.glEnd();
					}
					
					gl.glPopMatrix();
				}
				gl.glDisable(GL2.GL_SCISSOR_TEST);
				chartToRemoveOnClick = chartToClose;
				
//				System.out.println(String.format("%2.2fFPS, %2dms", animator.getLastFPS(), animator.getLastFPSPeriod()));
				
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
			
			// the mouse was pressed, attempting to start a new chart region, or remove an existing chart
			@Override public void mousePressed(MouseEvent me) {
				
				if(!serialPortConnected && Controller.getCharts().size() == 0)
					return;
				
				if(chartToRemoveOnClick != null) {
					Controller.removeChart(chartToRemoveOnClick);
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
				
				if(!serialPortConnected && Controller.getCharts().size() == 0)
					return;

				if(endX == -1 || endY == -1)
					return;
			
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(OpenGLChartsRegion.this);
				new AddChartWindow(parentWindow, startX, startY, endX, endY);
				
				startX = startY = -1;
				endX   = endY   = -1;
				
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
				
				if(!serialPortConnected && Controller.getCharts().size() == 0)
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
				
				if(!serialPortConnected && Controller.getCharts().size() == 0)
					return;
				
				mouseX = me.getX();
				mouseY = glCanvas.getHeight() - me.getY();
				
			}
			
		});
		
		glCanvas.addMouseWheelListener(new MouseWheelListener() {
			
			// the mouse wheel was scrolled
			@Override public void mouseWheelMoved(MouseWheelEvent mwe) {

				double scrollAmount = mwe.getPreciseWheelRotation();
				double samplesPerScroll = Controller.getSampleRate() / 4;
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
						nonLiveViewSamplesCount = (Controller.getSamplesCount() - 1);
					}
					
					double delta = scrollAmount * samplesPerScroll * zoomLevel;
					if(delta < -0.5 || delta > 0.5)
						delta = Math.round(delta);
					else if(delta < 0)
						delta = -1;
					else if(delta >= 0)
						delta = 1;
					nonLiveViewSamplesCount += delta;
					
					if(nonLiveViewSamplesCount >= Controller.getSamplesCount() - 1)
						liveView = true;
				
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
		Controller.addGridChangedListener(new GridChangedListener() {
			@Override public void gridChanged(int columns, int rows) {
				columnCount = columns;
				rowCount = rows;
			}
		});
		
		// track if a serial port is connected
		Controller.addSerialPortListener(new SerialPortListener() {
			@Override public void connectionOpened(int sampleRate, Packet packet, String portName, int baudRate) {
				serialPortConnected = true;
			}
			
			@Override public void connectionLost() {
				serialPortConnected = false;
			}
			
			@Override public void connectionClosed() {
				serialPortConnected = false;
			}
		});
		
	}
	
}