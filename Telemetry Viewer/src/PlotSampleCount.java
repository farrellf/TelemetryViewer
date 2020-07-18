import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

public class PlotSampleCount extends Plot {
	
	// for non-cached mode
	FloatBuffer[] buffersY;
	
	// for cached mode
	CachedModeDraw draw1 = new CachedModeDraw();
	CachedModeDraw draw2 = new CachedModeDraw();
	int[]     fbHandle;
	int[]     texHandle;
	boolean   cacheIsValid;
	List<Dataset> previousDatasets;
	int           previousMinSampleNumber;
	int           previousMaxSampleNumber;
	float         previousPlotMinY;
	float         previousPlotMaxY;
	int           previousPlotWidth;
	int           previousPlotHeight;
	long          previousPlotDomain;
	float         previousLineWidth;
	
	private class CachedModeDraw {
		
		boolean enabled;
		int[] scissorArgs;
		int xOffset;
		int sampleCount;
		FloatBuffer[] buffersY;
		
		void enableAndAcquire(List<Dataset> datasets, int firstSampleNumber, int lastSampleNumber, int plotWidth, int plotHeight) {
			enabled = true;
			xOffset = (int) (firstSampleNumber % plotDomain);
			sampleCount = (int) (lastSampleNumber - firstSampleNumber + 1);
			scissorArgs = calculateScissorArgs(firstSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			
			// acquire extra samples before and after, to prevent aliasing
			xOffset -= calculateSamplesNeededBefore(firstSampleNumber, plotWidth);
			firstSampleNumber -= calculateSamplesNeededBefore(firstSampleNumber, plotWidth);
			lastSampleNumber += calculateSamplesNeededAfter(lastSampleNumber, plotWidth);
			if(firstSampleNumber < 0) {
				long delta = 0 - firstSampleNumber;
				firstSampleNumber += delta;
				xOffset += delta;
			}
			if(lastSampleNumber > maxSampleNumber)
				lastSampleNumber = maxSampleNumber;
			sampleCount = (int) (lastSampleNumber - firstSampleNumber + 1);
			
			// acquire the samples
			buffersY = new FloatBuffer[datasets.size()];
			for(int datasetN = 0; datasetN < datasets.size(); datasetN++)
				if(!datasets.get(datasetN).isBitfield)
					buffersY[datasetN] = datasets.get(datasetN).getBuffer(firstSampleNumber, lastSampleNumber);
		}
		
		void disable() {
			enabled = false;
		}
		
	}
	
	/**
	 * Step 1: (Required) Calculate the domain and range of the plot.
	 * 
	 * @param lastSampleNumber    The sample to render at the right edge of the plot.
	 * @param zoomLevel           Current zoom level. 1.0 = no zoom.
	 * @param datasets            Datasets to acquire from.
	 * @param bitfieldEdges       Bitfield states to show edge events from.
	 * @param bitfieldLevels      Bitfield states to show levels from.
	 * @param duration            The sample count, before applying the zoom factor.
	 * @param cachedMode          True to enable the cache.
	 * @param showTimestamps      Ignored. This is only used by PlotMilliseconds.
	 */
	@Override void initialize(int lastSampleNumber, double zoomLevel, List<Dataset> datasets, List<Dataset.Bitfield.State> bitfieldEdges, List<Dataset.Bitfield.State> bitfieldLevels, long duration, boolean cachedMode, boolean showTimestamps) {
		
		this.datasets = datasets;
		this.bitfieldEdges = bitfieldEdges;
		this.bitfieldLevels = bitfieldLevels;
		this.cachedMode = cachedMode;
		xAxisTitle = "Sample Number";
		
		// exit if there are no samples to acquire
		if(lastSampleNumber < 1) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			plotMaxX = 0;
			plotMinX = plotMaxX - (int) (duration * zoomLevel) + 1;
			plotDomain = plotMaxX - plotMinX;
			samplesMinY = -1;
			samplesMaxY =  1;
			return;
		}
		
		// determine which samples to acquire
		maxSampleNumber = lastSampleNumber;
		minSampleNumber = maxSampleNumber - (int) (duration * zoomLevel) + 1;
		
		if(maxSampleNumber - minSampleNumber < 1)
			minSampleNumber = maxSampleNumber - 1;
		
		// calculate the domain
		plotMaxX = maxSampleNumber;
		plotMinX = minSampleNumber;
		plotDomain = plotMaxX - plotMinX;
		
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		// calculate the range
		samplesMinY = Float.MAX_VALUE;
		samplesMaxY = -Float.MAX_VALUE;

		for(Dataset dataset : datasets) {
			if(!dataset.isBitfield) {
				Dataset.MinMax range = dataset.getRange(minSampleNumber, maxSampleNumber);
				if(range.min < samplesMinY)
					samplesMinY = range.min;
				if(range.max > samplesMaxY)
					samplesMaxY = range.max;
			}
		}
		
		if(samplesMinY == Float.MAX_VALUE && samplesMaxY == -Float.MAX_VALUE) {
			samplesMinY = -1;
			samplesMaxY = 1;
		} else if(samplesMinY == samplesMaxY) {
			float value = samplesMinY;
			samplesMinY = value - 0.001f;
			samplesMaxY = value + 0.001f;
		}
		
	}
	
	// steps 2 and 3 are handled by the Plot class
	
	/**
	 * Step 4: Get the x-axis divisions.
	 * 
	 * @param gl           The OpenGL context.
	 * @param plotWidth    The width of the plot region, in pixels.
	 * @return             A Map where each value is a string to draw on screen, and each key is the pixelX location for it (0 = left edge of the plot)
	 */
	@Override public Map<Float, String> getXdivisions(GL2ES3 gl, float plotWidth) {
			
		Map<Integer, String> mapOfSampleNumbers = ChartUtils.getXdivisions125(gl, plotWidth, (int) plotMinX, (int) plotMaxX);
		Map<Float, String> mapOfPixelLocations = new HashMap<Float, String>();
		
		for(Map.Entry<Integer, String> entry : mapOfSampleNumbers.entrySet()) {
			float pixel = (float) (entry.getKey() - plotMinX) / (float) plotDomain * plotWidth;
			String text = entry.getValue();
			mapOfPixelLocations.put(pixel, text);
		}
		
		return mapOfPixelLocations;
			
	}
	
	@Override void acquireSamplesNonCachedMode(float plotMinY, float plotMaxY, int plotWidth, int plotHeight) {
		
		events = new BitfieldEvents(true, false, bitfieldEdges, bitfieldLevels, minSampleNumber, maxSampleNumber);
		
		buffersY = new FloatBuffer[datasets.size()];
		for(int datasetN = 0; datasetN < datasets.size(); datasetN++)
			if(!datasets.get(datasetN).isBitfield)
				buffersY[datasetN] = datasets.get(datasetN).getBuffer(minSampleNumber, maxSampleNumber);
		
	}
	
	@Override void acquireSamplesCachedMode(float plotMinY, float plotMaxY, int plotWidth, int plotHeight) {
		
		events = new BitfieldEvents(true, false, bitfieldEdges, bitfieldLevels, minSampleNumber, maxSampleNumber);
		
		// check if the cache must be flushed
		cacheIsValid = (datasets == previousDatasets) &&
		               (plotMinY == previousPlotMinY) &&
		               (plotMaxY == previousPlotMaxY) &&
		               (plotWidth == previousPlotWidth) &&
		               (plotHeight == previousPlotHeight) &&
		               (minSampleNumber < previousMaxSampleNumber) &&
		               (maxSampleNumber > previousMinSampleNumber) &&
		               (plotDomain == previousPlotDomain) &&
		               (Theme.lineWidth == previousLineWidth) &&
		               (fbHandle != null) &&
		               (texHandle != null);
		
		// of the samples to display, some might already be in the framebuffer, so determine what subset actually needs to be drawn
		int firstSampleNumber = minSampleNumber;
		int lastSampleNumber  = maxSampleNumber;
		if(cacheIsValid) {
			if(firstSampleNumber == previousMinSampleNumber && lastSampleNumber <= previousMaxSampleNumber) {
				// nothing to draw
				firstSampleNumber = lastSampleNumber;
			} else if(firstSampleNumber > previousMinSampleNumber) {
				// moving forward in time
				firstSampleNumber = previousMaxSampleNumber;
			} else if(firstSampleNumber < previousMinSampleNumber) {
				// moving backwards in time
				lastSampleNumber = previousMinSampleNumber + calculateSamplesNeededAfter(previousMinSampleNumber, plotWidth);
			} else {
				// new data but x=0 is still on screen
				firstSampleNumber = previousMaxSampleNumber;
			}
		}
		
		// the framebuffer is used as a ring buffer. since the pixels may wrap around from the right edge back to the left edge,
		// we may need to split the rendering into 2 draw calls (splitting it at the right edge of the framebuffer)
		int fbRightSampleNumber = (int) (plotMaxX - (plotMaxX % (plotSampleCount - 1)));

		if(firstSampleNumber == lastSampleNumber) {
			
			// nothing to draw
			draw1.disable();
			draw2.disable();
			
		} else if(lastSampleNumber <= fbRightSampleNumber || firstSampleNumber >= fbRightSampleNumber) {
			
			// only 1 draw call required (no need to wrap around the ring buffer)
			draw1.enableAndAcquire(datasets, firstSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			draw2.disable();
			
		} else {
			
			// 2 draw calls required because we need to wrap around the ring buffer
			int midX = fbRightSampleNumber;
			draw1.enableAndAcquire(datasets, firstSampleNumber, midX, plotWidth, plotHeight);
			draw2.enableAndAcquire(datasets, midX, lastSampleNumber, plotWidth, plotHeight);
			
		}
		
		// save current state
		previousDatasets = datasets;
		previousPlotMinY = plotMinY;
		previousPlotMaxY = plotMaxY;
		previousPlotWidth = plotWidth;
		previousPlotHeight = plotHeight;
		previousMaxSampleNumber = maxSampleNumber;
		previousMinSampleNumber = minSampleNumber;
		previousPlotDomain = plotDomain;
		previousLineWidth = Theme.lineWidth;
		
	}
	
	/**
	 * Calculates the (x,y,w,h) arguments for glScissor() based on what region the samples will occupy.
	 * 
	 * @param firstX        The first sample number.
	 * @param lastX         The last sample number.
	 * @param plotWidth     Width of the plot region, in pixels.
	 * @param plotHeight    Height of the plot region, in pixels.
	 * @return              An int[4] of {x,y,w,h}
	 */
	private int[] calculateScissorArgs(long firstX, long lastX, int plotWidth, int plotHeight) {
		
		// convert the sample number into a pixel number on the framebuffer, keeping in mind that it's a ring buffer
		long ringBufferedX = firstX % plotDomain;
		int pixelX = (int) (ringBufferedX * plotWidth / plotDomain);
		
		// convert the sample count into a pixel count
		int pixelWidth = (int) Math.ceil((double) (lastX - firstX) * (double) plotWidth / (double) plotDomain);
		
		int[] args = new int[4];
		args[0] = pixelX;
		args[1] = 0;
		args[2] = pixelWidth;
		args[3] = plotHeight;
		return args;
		
	}
	
	/**
	 * glScissor() is quantized to pixels, so we need to draw slightly more samples than theoretically needed to prevent aliasing at the edges.
	 * 
	 * @param sampleNumber    Sample number.
	 * @param plotWidth       Width of the plot region, in pixels.
	 * @return                Number of extra samples to draw before x.
	 */
	private int calculateSamplesNeededBefore(int x, int plotWidth) {
		
		double samplesPerPixel = (double) plotDomain / (double) plotWidth;
		int extraSamplesNeeded = (int) Math.ceil(samplesPerPixel * Theme.lineWidth);
		return extraSamplesNeeded;
		
	}
	
	/**
	 * glScissor() is quantized to pixels, so we need to draw slightly more samples than theoretically needed to prevent aliasing at the edges.
	 * 
	 * @param sampleNumber    Sample number.
	 * @param plotWidth       Width of the plot region, in pixels.
	 * @return                Number of extra samples to draw after x.
	 */
	private int calculateSamplesNeededAfter(int x, int plotWidth) {
		
		return calculateSamplesNeededBefore(x, plotWidth);
		
	}
	
	@Override void drawNonCachedMode(GL2ES3 gl, float[] chartMatrix, int xPlotLeft, int yPlotBottom, int plotWidth, int plotHeight, float plotMinY, float plotMaxY) {
		
		float plotRange = plotMaxY - plotMinY;
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, plotWidth, plotHeight);
		
		float[] plotMatrix = Arrays.copyOf(chartMatrix, 16);
		// adjust so: x = (x - plotMinX) / domain * plotWidth + xPlotLeft;
		// adjust so: y = (y - plotMinY) / plotRange * plotHeight + yPlotBottom;
		// edit: now doing the "x - plotMinX" part before putting data into the buffers, to improve float32 precision when x is very large
		OpenGL.translateMatrix(plotMatrix, xPlotLeft,            yPlotBottom,          0);
		OpenGL.scaleMatrix    (plotMatrix, (float) plotWidth/plotDomain, (float) plotHeight/plotRange, 1);
		OpenGL.translateMatrix(plotMatrix,                            0,                    -plotMinY, 0);
		OpenGL.useMatrix(gl, plotMatrix);
		
		// draw each dataset
		if(!datasets.isEmpty() && plotSampleCount >= 2) {
			for(int i = 0; i < datasets.size(); i++) {
				
				// do not draw bitfields
				if(datasets.get(i).isBitfield)
					continue;

				OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, datasets.get(i).glColor, buffersY[i], plotSampleCount, (int) (plotMinX >= 0 ? 0 : plotMinX * -1));
				
				// also draw points if there are relatively few samples on screen
				boolean fewSamplesOnScreen = (plotWidth / (float) plotDomain) > (2 * Theme.pointWidth);
				if(fewSamplesOnScreen)
					OpenGL.drawPointsY(gl, datasets.get(i).glColor, buffersY[i], plotSampleCount, (int) (plotMinX >= 0 ? 0 : plotMinX * -1));
				
			}
		}
		
		OpenGL.useMatrix(gl, chartMatrix);
		
		// draw any bitfield changes
		if(!datasets.isEmpty() && plotSampleCount >= 2) {
			List<BitfieldEvents.EdgeMarker>  edgeMarkers  = events.getEdgeMarkers (sampleNumber -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			List<BitfieldEvents.LevelMarker> levelMarkers = events.getLevelMarkers(sampleNumber -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xPlotLeft, yPlotBottom + plotHeight, xPlotLeft + plotWidth, yPlotBottom, -1, -1);
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
	}
	
	@Override void drawCachedMode(GL2ES3 gl, float[] chartMatrix, int xPlotLeft, int yPlotBottom, int plotWidth, int plotHeight, float plotMinY, float plotMaxY) {
		
		// create the off-screen framebuffer if this is the first draw call
		if(fbHandle == null) {
			fbHandle = new int[1];
			texHandle = new int[1];
			OpenGL.createOffscreenFramebuffer(gl, fbHandle, texHandle);
		}
		
		// draw on the off-screen framebuffer
		float[] offscreenMatrix = new float[16];
		OpenGL.makeOrthoMatrix(offscreenMatrix, 0, plotWidth, 0, plotHeight, -1, 1);
		if(cacheIsValid)
			OpenGL.continueDrawingOffscreen(gl, offscreenMatrix, fbHandle, texHandle, plotWidth, plotHeight);
		else
			OpenGL.startDrawingOffscreen(gl, offscreenMatrix, fbHandle, texHandle, plotWidth, plotHeight);

		// erase the invalid parts of the framebuffer
		if(plotMinX < 0) {
			// if x<0 is on screen, we need to erase the x<0 region because it may have old data on it
			gl.glEnable(GL3.GL_SCISSOR_TEST);
			int[] args = calculateScissorArgs(plotMaxX, plotMaxX + plotDomain, plotWidth, plotHeight);
			gl.glScissor(args[0], args[1], args[2], args[3]);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			gl.glDisable(GL3.GL_SCISSOR_TEST);
		}
		if(draw1.enabled) {
			gl.glEnable(GL3.GL_SCISSOR_TEST);
			gl.glScissor(draw1.scissorArgs[0], draw1.scissorArgs[1], draw1.scissorArgs[2], draw1.scissorArgs[3]);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			gl.glDisable(GL3.GL_SCISSOR_TEST);
		}
		if(draw2.enabled) {
			gl.glEnable(GL3.GL_SCISSOR_TEST);
			gl.glScissor(draw2.scissorArgs[0], draw2.scissorArgs[1], draw2.scissorArgs[2], draw2.scissorArgs[3]);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			gl.glDisable(GL3.GL_SCISSOR_TEST);
		}
		
		// adjust so: x = (x - plotMinX) / domain * plotWidth;
		// adjust so: y = (y - plotMinY) / plotRange * plotHeight;
		// edit: now doing the "x - plotMinX" part before putting data into the buffers, to improve float32 precision when x is very large
		float plotRange = plotMaxY - plotMinY;
		OpenGL.scaleMatrix    (offscreenMatrix, (float) plotWidth/plotDomain, (float) plotHeight/plotRange, 1);
		OpenGL.translateMatrix(offscreenMatrix,                            0,                    -plotMinY, 0);
		OpenGL.useMatrix(gl, offscreenMatrix);
		
		// draw each dataset
		if(!datasets.isEmpty() && plotSampleCount >= 2) {
			for(int i = 0; i < datasets.size(); i++) {
				
				// do not draw bitfields
				if(datasets.get(i).isBitfield)
					continue;
				
				boolean fewSamplesOnScreen = (plotWidth / (float) plotDomain) > (2 * Theme.pointWidth);
				
				if(draw1.enabled) {
					gl.glEnable(GL3.GL_SCISSOR_TEST);
					gl.glScissor(draw1.scissorArgs[0], draw1.scissorArgs[1], draw1.scissorArgs[2], draw1.scissorArgs[3]);
					OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, datasets.get(i).glColor, draw1.buffersY[i], draw1.sampleCount, draw1.xOffset);
					if(fewSamplesOnScreen)
						OpenGL.drawPointsY(gl, datasets.get(i).glColor, draw1.buffersY[i], draw1.sampleCount, draw1.xOffset);
					gl.glDisable(GL3.GL_SCISSOR_TEST);
				}
				
				if(draw2.enabled) {
					gl.glEnable(GL3.GL_SCISSOR_TEST);
					gl.glScissor(draw2.scissorArgs[0], draw2.scissorArgs[1], draw2.scissorArgs[2], draw2.scissorArgs[3]);
					OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, datasets.get(i).glColor, draw2.buffersY[i], draw2.sampleCount, draw2.xOffset);
					if(fewSamplesOnScreen)
						OpenGL.drawPointsY(gl, datasets.get(i).glColor, draw2.buffersY[i], draw2.sampleCount, draw2.xOffset);
					gl.glDisable(GL3.GL_SCISSOR_TEST);
				}
				
			}
		}
		
//		// draw color bars at the bottom edge of the plot to indicate draw call regions
//		OpenGL.makeOrthoMatrix(offscreenMatrix, 0, plotWidth, 0, plotHeight, -1, 1);
//		OpenGL.useMatrix(gl, offscreenMatrix);
//		float[] randomColor1 = new float[] {(float) Math.random(), (float) Math.random(), (float) Math.random(), 0.5f};
//		float[] randomColor2 = new float[] {(float) Math.random(), (float) Math.random(), (float) Math.random(), 0.5f};
//		if(draw1.enabled)
//			OpenGL.drawBox(gl, randomColor1, draw1.scissorArgs[0] + 0.5f, 0, draw1.scissorArgs[2], 10);
//		if(draw2.enabled)
//			OpenGL.drawBox(gl, randomColor2,  draw2.scissorArgs[0] + 0.5f, 0, draw2.scissorArgs[2], 10);
		
		// switch back to the screen framebuffer
		OpenGL.stopDrawingOffscreen(gl, chartMatrix);
		
		// draw the framebuffer on screen
		float startX = (float) (plotMaxX % plotDomain) / plotDomain;
		OpenGL.drawRingbufferTexturedBox(gl, texHandle, xPlotLeft, yPlotBottom, plotWidth, plotHeight, startX);
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, plotWidth, plotHeight);
		
		// draw any bitfield changes
		if(!datasets.isEmpty() && plotSampleCount >= 2) {
			List<BitfieldEvents.EdgeMarker>  edgeMarkers  = events.getEdgeMarkers (sampleNumber -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			List<BitfieldEvents.LevelMarker> levelMarkers = events.getLevelMarkers(sampleNumber -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xPlotLeft, yPlotBottom + plotHeight, xPlotLeft + plotWidth, yPlotBottom, -1, -1);
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
//		// draw the framebuffer without ringbuffer wrapping, 10 pixels above the plot
//		gl.glDisable(GL2.GL_SCISSOR_TEST);
//		OpenGL.drawTexturedBox(gl, texHandle, xPlotLeft, yPlotBottom + plotHeight + 10, plotWidth, plotHeight, 0);
//		gl.glEnable(GL2.GL_SCISSOR_TEST);
		
	}
	
	/**
	 * Checks if a tooltip should be drawn for the mouse's current location.
	 * 
	 * @param mouseX       The mouse's location along the x-axis, in pixels (0 = left edge of the plot)
	 * @param plotWidth    Width of the plot region, in pixels.
	 * @return             An object indicating if the tooltip should be drawn, for what sample number, with what label, and at what location on screen.
	 */
	@Override public TooltipInfo getTooltip(int mouseX, float plotWidth) {
		
		if(plotSampleCount == 0)
			return new TooltipInfo(false, 0, "", 0);
			
		long sampleNumber = Math.round((float) mouseX / plotWidth * plotDomain) + plotMinX;
		if(sampleNumber < 0)
			return new TooltipInfo(false, 0, "", 0);
		
		if(sampleNumber > maxSampleNumber)
			sampleNumber = maxSampleNumber;
		
		String label = "Sample " + sampleNumber;
		float pixelX = (float) (sampleNumber - plotMinX) / (float) plotDomain * plotWidth;
		return new TooltipInfo(true, sampleNumber, label, pixelX);
		
	}
	
	/**
	 * Deletes the off-screen framebuffer and texture.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public void freeResources(GL2ES3 gl) {
		
		if(texHandle != null)
			gl.glDeleteTextures(1, texHandle, 0);
		if(fbHandle != null)
			gl.glDeleteFramebuffers(1, fbHandle, 0);
		
		texHandle = null;
		fbHandle = null;
		
	}

}
