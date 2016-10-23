import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Manages the grid region and all charts on the screen.
 * 
 * Users can click-and-drag in this region to create new charts.
 * When this panel is resized, all contained charts will be repositioned and resized accordingly.
 */
@SuppressWarnings("serial")
public class ChartsRegion extends JPanel {
	
	// grid size
	int columnCount;
	int rowCount;
	
	// grid locations for the opposite corners of where a new chart will be placed
	int startX;
	int startY;
	int endX;
	int endY;
	
	/**
	 * Create a new ChartsRegion.
	 */
	public ChartsRegion() {
		
		super();
		
		columnCount = Controller.getGridColumns();
		rowCount    = Controller.getGridRows();
		
		startX  = -1;
		startY  = -1;
		endX    = -1;
		endY    = -1;
		
		setLayout(null); // absolute layout
		setMinimumSize(new Dimension(200, 200));
		
		// update the column and row counts when they change
		Controller.addGridChangedListener(new GridChangedListener() {
			@Override public void gridChanged(int columns, int rows) {
				columnCount = columns;
				rowCount = rows;
				getComponentListeners()[0].componentResized(null);
			}
		});
		
		// add and remove charts as needed
		Controller.addChartsListener(new ChartListener() {
			@Override public void chartRemoved(PositionedChart chart) {
				remove(chart);
				revalidate();
				repaint();
			}
			
			@Override public void chartAdded(PositionedChart chart) {
				add(chart);
				revalidate();
				repaint();
			}
		});
		
		// listen for mouse presses and releases (the user clicking and dragging a region to place a new chart)
		addMouseListener(new MouseListener() {
			
			// the mouse was pressed, attempting to start a new chart region
			@Override public void mousePressed(MouseEvent me) {
				int proposedStartX = me.getX() * columnCount / getWidth();
				int proposedStartY = me.getY() * rowCount    / getHeight();
				
				if(proposedStartX < columnCount && proposedStartY < rowCount && Controller.gridRegionAvailable(proposedStartX, proposedStartY, proposedStartX, proposedStartY)) {
					startX = endX = proposedStartX;
					startY = endY = proposedStartY;
					repaint();
				}
			}
			
			// the mouse was released, attempting to create a new chart
			@Override public void mouseReleased(MouseEvent me) {
				
				if(endX == -1 || endY == -1)
					return;
				
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount    / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
				}
				
				JFrame parentWindow = (JFrame) SwingUtilities.windowForComponent(ChartsRegion.this);
				new NewChartWindow(parentWindow, startX, startY, endX, endY);
				
				startX = startY = -1;
				endX   = endY   = -1;
				repaint();
			}
			
			@Override public void mouseExited(MouseEvent me) { }
			@Override public void mouseEntered(MouseEvent me) { }
			@Override public void mouseClicked(MouseEvent me) { }
			
		});
		
		// listen for mouse drags (the user clicking and dragging a region for a new chart)
		addMouseMotionListener(new MouseMotionListener() {
			
			@Override public void mouseDragged(MouseEvent me) {
				
				if(endX == -1 || endY == -1)
					return;
				
				int proposedEndX = me.getX() * columnCount / getWidth();
				int proposedEndY = me.getY() * rowCount    / getHeight();
				
				if(proposedEndX < columnCount && proposedEndY < rowCount && Controller.gridRegionAvailable(startX, startY, proposedEndX, proposedEndY)) {
					endX = proposedEndX;
					endY = proposedEndY;
					repaint();
				}
				
			}
			
			@Override public void mouseMoved(MouseEvent me) { }
			
		});
		
		// listen for a change in size
		// this is required because paintComponent() will not be automatically called if the window is un-maximized and an existing chart would obscure the rest of the ChartsRegion
		addComponentListener(new ComponentListener() {
			
			@Override public void componentResized(ComponentEvent ce) {

				int width = getWidth();
				int height = getHeight();
				int columnWidth = width  / columnCount;
				int rowHeight   = height / rowCount;
				
				Controller.repositionCharts(columnWidth, rowHeight);
				revalidate();
				repaint();
				
			}
			
			@Override public void componentShown(ComponentEvent ce) { }
			@Override public void componentMoved(ComponentEvent ce) { }
			@Override public void componentHidden(ComponentEvent ce) { }
			
		});
		
	}
	
	/**
	 * Redraws the ChartsRegion based on the current width/height, and the number of columns/rows.
	 * 
	 * @param g    The graphics object.
	 */
	@Override public void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
				
		int width = getWidth();
		int height = getHeight();
		int columnWidth = width  / columnCount;
		int rowHeight   = height / rowCount;
		
		// resize and reposition all charts
		Controller.repositionCharts(columnWidth, rowHeight);
		revalidate();
		
		// draw a neutral background
		g2.setColor(getBackground());
		g2.fillRect(0, 0, width, height);
		
		width  = columnWidth * columnCount;
		height = rowHeight   * rowCount;
		
		// draw a white background for the grid
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, width, height);
		
		// set the stroke
		float factor = Controller.getDisplayScalingFactor();
		g2.setStroke(new BasicStroke(1.0f * factor, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[] {5.0f * factor}, 0.0f));
		g2.setColor(Color.BLACK);
		
		// draw vertical lines
		for(int i = 1; i < columnCount; i++)
			g2.drawLine(columnWidth * i, 0, columnWidth * i, height);
		
		// draw horizontal lines
		for(int i = 1; i < rowCount; i++)
			g2.drawLine(0, rowHeight * i, width, rowHeight * i);
		
		// draw active bounding box where the user is clicking-and-dragging to place a new chart
		g2.setColor(Color.GRAY);
		int x = startX < endX ? startX * columnWidth : endX * columnWidth;
		int y = startY < endY ? startY * rowHeight   : endY * rowHeight;
		int boxWidth  = (Math.abs(endX - startX) + 1) * columnWidth;
		int boxHeight = (Math.abs(endY - startY) + 1) * rowHeight;
		g2.fillRect(x, y, boxWidth, boxHeight);
		
	}
	
}
