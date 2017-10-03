import javax.swing.JPanel;

import com.jogamp.opengl.GL2;

public abstract class PositionedChart {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int sampleCount;
	Dataset[] datasets;
	
	public PositionedChart(int x1, int y1, int x2, int y2) {
		
		topLeftX     = x1 < x2 ? x1 : x2;
		topLeftY     = y1 < y2 ? y1 : y2;
		bottomRightX = x2 > x1 ? x2 : x1;
		bottomRightY = y2 > y1 ? y2 : y1;
			
	}
	
	public boolean regionOccupied(int startX, int startY, int endX, int endY) {

		for(int x = startX; x <= endX; x++)
			for(int y = startY; y <= endY; y++)
				if(x >= topLeftX && x <= bottomRightX && y >= topLeftY && y <= bottomRightY)
					return true;
		
		return false;
		
	}
	
	public String exportDatasets() {
		
		if(datasets == null || datasets.length == 0) {
			return "";
		} else {
			String s = Integer.toString(datasets[0].location);
			for(int i = 1; i < datasets.length; i++)
				s += "," + datasets[i].location;
			return s;
		}
		
	}
	
	public void importDatasets(int lineNumber, String s) {
		
		try {
			
			String[] tokens = s.split(",");
			for(String t : tokens)
				Integer.parseInt(t);
			
			datasets = new Dataset[tokens.length];
			for(int i = 0; i < tokens.length; i++)
				datasets[i] = Controller.getDatasetByLocation(Integer.parseInt(tokens[i]));
			
		} catch(Exception e) {
			
			throw new AssertionError("Line " + lineNumber + ": Invalid datasets list.");
			
		}
		
	}
	
	public abstract void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY);
	
	public abstract String[] exportChart();
	
	public abstract void importChart(String[] lines, int firstLineNumber);
	
	public abstract JPanel[] getWidgets();
	
	public abstract String toString();
	
}