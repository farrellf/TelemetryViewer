import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

@SuppressWarnings("serial")
public abstract class PositionedChart extends JPanel {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int duration;
	Dataset[] datasets;
	
	public PositionedChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super();
		
		topLeftX     = x1 < x2 ? x1 : x2;
		topLeftY     = y1 < y2 ? y1 : y2;
		bottomRightX = x2 > x1 ? x2 : x1;
		bottomRightY = y2 > y1 ? y2 : y1;

		duration = chartDuration;
		datasets = chartInputs;
		
		setBorder(BorderFactory.createLineBorder(Color.BLACK));
			
	}
	
	public boolean regionOccupied(int startX, int startY, int endX, int endY) {

		for(int x = startX; x <= endX; x++)
			for(int y = startY; y <= endY; y++)
				if(x >= topLeftX && x <= bottomRightX && y >= topLeftY && y <= bottomRightY)
					return true;
		
		return false;
		
	}
	
	public abstract void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel);
	
}