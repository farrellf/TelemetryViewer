import java.util.ArrayList;
import java.util.List;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

public abstract class PositionedChart {
	
	// grid coordinates, not pixels
	int topLeftX;
	int topLeftY;
	int bottomRightX;
	int bottomRightY;
	
	int duration;
	boolean sampleCountMode;
	List<Dataset> datasets                      = new ArrayList<Dataset>();
	List<Dataset.Bitfield.State> bitfieldEdges  = new ArrayList<Dataset.Bitfield.State>();
	List<Dataset.Bitfield.State> bitfieldLevels = new ArrayList<Dataset.Bitfield.State>();
	Widget[] widgets;
	
	public PositionedChart(int x1, int y1, int x2, int y2) {
		
		topLeftX     = x1 < x2 ? x1 : x2;
		topLeftY     = y1 < y2 ? y1 : y2;
		bottomRightX = x2 > x1 ? x2 : x1;
		bottomRightY = y2 > y1 ? y2 : y1;
		sampleCountMode = true;
			
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
	int[] gpuQueryHandles;
	long[] gpuTimes = new long[2];
	String line1;
	String line2;
	
	public final EventHandler draw(GL2ES3 gl, float[] chartMatrix, int width, int height, long nowTimestamp, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		boolean openGLES = OpenGLChartsView.instance.openGLES;
		if(!openGLES && gpuQueryHandles == null) {
			gpuQueryHandles = new int[2];
			gl.glGenQueries(2, gpuQueryHandles, 0);
			gl.glQueryCounter(gpuQueryHandles[0], GL3.GL_TIMESTAMP); // insert both queries to prevent a warning on the first time they are read
			gl.glQueryCounter(gpuQueryHandles[1], GL3.GL_TIMESTAMP);
		}
		
		// if benchmarking, calculate CPU/GPU time for the *previous frame*
		// GPU benchmarking is not possible with OpenGL ES
		if(SettingsController.getBenchmarking()) {
			previousCpuMilliseconds = (cpuStopNanoseconds - cpuStartNanoseconds) / 1000000.0;
			if(!openGLES) {
				gl.glGetQueryObjecti64v(gpuQueryHandles[0], GL3.GL_QUERY_RESULT, gpuTimes, 0);
				gl.glGetQueryObjecti64v(gpuQueryHandles[1], GL3.GL_QUERY_RESULT, gpuTimes, 1);
			}
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
			
			// start timers for *this frame*
			cpuStartNanoseconds = System.nanoTime();
			if(!openGLES)
				gl.glQueryCounter(gpuQueryHandles[0], GL3.GL_TIMESTAMP);
		}
		
		// draw the chart
		EventHandler handler = drawChart(gl, chartMatrix, width, height, nowTimestamp, lastSampleNumber, zoomLevel, mouseX, mouseY);
		
		// if benchmarking, draw the CPU/GPU benchmarks over this chart
		// GPU benchmarking is not possible with OpenGL ES
		if(SettingsController.getBenchmarking()) {
			// stop timers for *this frame*
			cpuStopNanoseconds = System.nanoTime();
			if(!openGLES)
				gl.glQueryCounter(gpuQueryHandles[1], GL3.GL_TIMESTAMP);
			
			// show times of *previous frame*
			line1 =             String.format("CPU = %.3fms (Average = %.3fms)", previousCpuMilliseconds, averageCpuMilliseconds);
			line2 = !openGLES ? String.format("GPU = %.3fms (Average = %.3fms)", previousGpuMilliseconds, averageGpuMilliseconds) :
			                                  "GPU = unknown";
			float textHeight = 2 * OpenGL.smallTextHeight + Theme.tickTextPadding;
			float textWidth = Float.max(OpenGL.smallTextWidth(gl, line1), OpenGL.smallTextWidth(gl, line2));
			OpenGL.drawBox(gl, Theme.neutralColor, Theme.tileShadowOffset, 0, textWidth + Theme.tickTextPadding*2, textHeight + Theme.tickTextPadding*2);
			OpenGL.drawSmallText(gl, line1, (int) (Theme.tickTextPadding + Theme.tileShadowOffset), (int) (2 * Theme.tickTextPadding + OpenGL.smallTextHeight), 0);
			OpenGL.drawSmallText(gl, line2, (int) (Theme.tickTextPadding + Theme.tileShadowOffset), (int) Theme.tickTextPadding, 0);
		}
		
		return handler;
		
	}
	
	/**
	 * Draws the chart on screen.
	 * 
	 * @param gl                  The OpenGL context.
	 * @param chartMatrix         The 4x4 matrix to use.
	 * @param width               Width of the chart, in pixels.
	 * @param height              Height of the chart, in pixels.
	 * @param endTimestamp        Timestamp corresponding with the right edge of a time-domain plot. NOTE: this might be in the future!
	 * @param endSampleNumber     Sample number corresponding with the right edge of a time-domain plot. NOTE: this sample might not exist yet!
	 * @param zoomLevel           Requested zoom level.
	 * @param mouseX              Mouse's x position, in pixels, relative to the chart.
	 * @param mouseY              Mouse's y position, in pixels, relative to the chart.
	 * @return                    An EventHandler if the mouse is over something that can be clicked or dragged.
	 */
	public abstract EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long endTimestamp, int endSampleNumber, double zoomLevel, int mouseX, int mouseY);
	
	public final void importChart(ConnectionsController.QueueOfLines lines) {

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
		
		if(gpuQueryHandles != null) {
			gl.glDeleteQueries(2, gpuQueryHandles, 0);
			gpuQueryHandles = null;
		}
		
	}
	
}