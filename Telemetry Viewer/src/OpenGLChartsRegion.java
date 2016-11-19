import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
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
	
	public OpenGLChartsRegion() {
		
		super();
		
		columnCount = Controller.getGridColumns();
		rowCount    = Controller.getGridRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
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
			    
				gl.glLineWidth(Theme.strokeWidth);
				
				gl.setSwapInterval(1);
				
			}
						
			@Override public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_PROJECTION);
				gl.glLoadIdentity();
				gl.glOrtho(0, width, 0, height, -1, 1);
				
				canvasWidth = width;
				canvasHeight = height;
				
			}

			@Override public void display(GLAutoDrawable drawable) {
				
				int columnWidth = canvasWidth  / columnCount;
				int rowHeight   = canvasHeight / rowCount;
				int gridWidth   = columnWidth * columnCount;
				int gridHeight  = rowHeight   * rowCount;
				int gridYoffset = canvasHeight - gridHeight;
				
				// prepare OpenGL
				GL2 gl = drawable.getGL().getGL2();
				
				gl.glMatrixMode(GL2.GL_MODELVIEW);
				gl.glLoadIdentity();
				
				gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
				gl.glClear(GL2.GL_COLOR_BUFFER_BIT);

				// draw a neutral background
				float[] backgroundColor = new float[] {getBackground().getRed() / 255.0f, getBackground().getGreen() / 255.0f, getBackground().getBlue() / 255.0f};
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3fv(backgroundColor, 0);
					gl.glVertex2f(0,           0);
					gl.glVertex2f(0,           canvasHeight);
					gl.glVertex2f(canvasWidth, canvasHeight);
					gl.glVertex2f(canvasWidth, 0);
				gl.glEnd();
				
				// draw the grid background
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3fv(Theme.gridBackgroundColor, 0);
					gl.glVertex2f(0,         0 + gridYoffset);
					gl.glVertex2f(0,         gridHeight + gridYoffset);
					gl.glVertex2f(gridWidth, gridHeight + gridYoffset);
					gl.glVertex2f(gridWidth, 0 + gridYoffset);
				gl.glEnd();
				
				// draw vertical grid lines
				gl.glBegin(GL2.GL_LINES);
				gl.glColor3fv(Theme.gridLinesColor, 0);
					for(int i = 1; i < columnCount; i++) {
						gl.glVertex2f(columnWidth * i, 0 + gridYoffset);
						gl.glVertex2f(columnWidth * i, gridHeight + gridYoffset);
					}
				gl.glEnd();
				
				// draw horizontal grid lines
				gl.glBegin(GL2.GL_LINES);
				gl.glColor3fv(Theme.gridLinesColor, 0);
					for(int i = 1; i < rowCount; i++) {
						gl.glVertex2f(0,         rowHeight * i + gridYoffset);
						gl.glVertex2f(gridWidth, rowHeight * i + gridYoffset);
					}
				gl.glEnd();
				
				// draw bounding box where the user is actively clicking-and-dragging to place a new chart
				gl.glBegin(GL2.GL_QUADS);
				gl.glColor3fv(Theme.gridBoxColor, 0);
					int x1 = startX < endX ? startX * columnWidth : endX * columnWidth;
					int y1 = startY < endY ? startY * rowHeight   : endY * rowHeight;
					int x2 = x1 + (Math.abs(endX - startX) + 1) * columnWidth;
					int y2 = y1 + (Math.abs(endY - startY) + 1) * rowHeight;
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
				List<PositionedChart> charts = Controller.getCharts();
				gl.glEnable(GL2.GL_SCISSOR_TEST);
				for(PositionedChart chart : charts) {
					int width = columnWidth * (chart.bottomRightX - chart.topLeftX + 1);
					int height = rowHeight * (chart.bottomRightY - chart.topLeftY + 1);
					int xOffset = chart.topLeftX * columnWidth;
					int yOffset = chart.topLeftY * rowHeight;
					yOffset = canvasHeight - yOffset - height;
					gl.glScissor(xOffset, yOffset, width, height);
					gl.glPushMatrix();
					gl.glTranslatef(xOffset, yOffset, 0);
					FontUtils.setOffsets(xOffset, yOffset);
					chart.drawChart(gl, width, height);
					FontUtils.drawQueuedText(gl, canvasWidth, canvasHeight);
					gl.glPopMatrix();
				}
				gl.glDisable(GL2.GL_SCISSOR_TEST);
				
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
			
			// the mouse was pressed, attempting to start a new chart region
			@Override public void mousePressed(MouseEvent me) {
				
				int proposedStartX = me.getX() * columnCount / getWidth();
				int proposedStartY = me.getY() * rowCount / getHeight();
				
				if(proposedStartX < columnCount && proposedStartY < rowCount && Controller.gridRegionAvailable(proposedStartX, proposedStartY, proposedStartX, proposedStartY)) {
					startX = endX = proposedStartX;
					startY = endY = proposedStartY;
				}
				
			}
			
			// the mouse was released, attempting to create a new chart
			@Override public void mouseReleased(MouseEvent me) {

				if(endX == -1 || endY == -1)
					return;
			
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(OpenGLChartsRegion.this);
				new NewChartWindow(parentWindow, startX, startY, endX, endY);
				
				startX = startY = -1;
				endX   = endY   = -1;
				
			}

			@Override public void mouseExited (MouseEvent e) { }
			@Override public void mouseEntered(MouseEvent e) { }
			@Override public void mouseClicked(MouseEvent e) { }
			
		});
		
		glCanvas.addMouseMotionListener(new MouseMotionListener() {
			
			// the mouse was dragged while attempting to create a new chart
			@Override public void mouseDragged(MouseEvent me) {
				
				if(endX == -1 || endY == -1)
					return;
				
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
			}
			
			@Override public void mouseMoved(MouseEvent me) { }
			
		});
		
		// update the column and row counts when they change
		Controller.addGridChangedListener(new GridChangedListener() {
			@Override public void gridChanged(int columns, int rows) {
				columnCount = columns;
				rowCount = rows;
			}
		});
		
	}
	
}