import java.util.ArrayList;
import java.util.List;
import com.jogamp.opengl.GL2ES3;

public abstract class PositionedChart {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int sampleCount;
	List<Dataset> datasets;
	List<Dataset.Bitfield.State> bitfieldEdges;
	List<Dataset.Bitfield.State> bitfieldLevels;
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
	
	public abstract EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY);
	
	public final void importChart(CommunicationController.QueueOfLines lines) {

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
	
	/**
	 * Schedules the chart to be disposed.
	 * Non-GPU resources (cache files, etc.) will be released immediately.
	 * GPU resources will be released the next time the OpenGLChartsRegion is drawn. (the next vsync, if it's on screen.)
	 */
	final public void dispose() {
		
		disposeNonGpu();
		OpenGLChartsView.instance.chartsToDispose.add(this);
		
	}
	
	/**
	 * Charts that create cache files or other non-GPU resources must dispose of them when this method is called.
	 * The chart may be drawn after this call, so the chart must be able to automatically regenerate any needed caches.
	 */
	public void disposeNonGpu() {
		
	}
	
	/**
	 * Charts that create any OpenGL FBOs/textures/etc. must dispose of them when this method is called.
	 * The chart may be drawn after this call, so the chart must be able to automatically regenerate any needed FBOs/textures/etc.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public void disposeGpu(GL2ES3 gl) {
		
	}
	
}