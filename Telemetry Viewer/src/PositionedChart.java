import java.util.ArrayList;
import java.util.List;
import com.jogamp.opengl.GL2;

public abstract class PositionedChart {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int sampleCount;
	Dataset[] datasets;
	Widget[] widgets;
	
	public PositionedChart(int x1, int y1, int x2, int y2) {
		
		topLeftX     = x1 < x2 ? x1 : x2;
		topLeftY     = y1 < y2 ? y1 : y2;
		bottomRightX = x2 > x1 ? x2 : x1;
		bottomRightY = y2 > y1 ? y2 : y1;
			
	}
	
	public boolean regionOccupied(int startX, int startY, int endX, int endY) {
		
		if(endX < startX) {
			int temp = startX;
			startX = endX;
			endX = temp;
		}
		if(endY < startY) {
			int temp = startY;
			startY = endY;
			endY = temp;
		}

		for(int x = startX; x <= endX; x++)
			for(int y = startY; y <= endY; y++)
				if(x >= topLeftX && x <= bottomRightX && y >= topLeftY && y <= bottomRightY)
					return true;
		
		return false;
		
	}
	
	public abstract void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY);
	
	public final void importChart(Controller.QueueOfLines lines) {

		for(Widget widget : widgets)
			if(widget != null)
				widget.importState(lines);
		
	}
	
	public final List<String> exportChart() {
		
		List<String> lines = new ArrayList<String>();
		
		for(Widget widget : widgets)
			if(widget != null)
				for(String line : widget.exportState())
					lines.add(line);
		
		return lines;
		
	}
	
	public abstract String toString();
	
}