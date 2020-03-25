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
	
	public abstract EventHandler drawChart(GL2 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY);
	
	public final void importChart(Controller.QueueOfLines lines) {

		for(Widget widget : widgets)
			if(widget != null)
				widget.importState(lines);
		
	}
	
	final public List<String> exportChart() {
		
		List<String> lines = new ArrayList<String>();
		
		for(Widget widget : widgets)
			if(widget != null)
				for(String line : widget.exportState())
					lines.add(line);
		
		return lines;
		
	}
	
	public abstract String toString();
	
	final public void dispose() {
		
		GL2 gl = null;
		try { gl = OpenGLChartsRegion.instance.glCanvas.getGL().getGL2(); } catch(Exception e) {}
		flush(gl);
		
	}
	
	/**
	 * Charts that cache anything or create OpenGL FBOs/textures must dispose of them when this method is called.
	 * The chart may be drawn after this call, so the chart must be able to automatically regenerate any needed caches/FBOs/textures.
	 * 
	 * @param gl    The OpenGL context. This might be null, in which case there is no need to dispose of OpenGL resources.
	 */
	public void flush(GL2 gl) {
		
	}
	
}