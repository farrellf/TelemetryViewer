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
	DrawCallData draw1 = new DrawCallData();
	DrawCallData draw2 = new DrawCallData();
	int[]     fbHandle;
	int[]     texHandle;
	boolean   cacheIsValid;
	List<Dataset> previousNormalDatasets;
	List<Dataset.Bitfield.State> previousEdgeStates;
	List<Dataset.Bitfield.State> previousLevelStates;
	long          previousMinSampleNumber;
	long          previousMaxSampleNumber;
	float         previousPlotMinY;
	float         previousPlotMaxY;
	int           previousPlotWidth;  // pixels
	int           previousPlotHeight; // pixels
	long          previousPlotDomain; // sample count
	float         previousLineWidth;
	
	/**
	 * Step 1: (Required) Calculate the domain and range of the plot.
	 * 
	 * @param endTimestamp      Ignored. This is only used by PlotMilliseconds.
	 * @param endSampleNumber   Sample number corresponding with the right edge of a time-domain plot. NOTE: this sample might not exist yet!
	 * @param zoomLevel         Current zoom level. 1.0 = no zoom.
	 * @param datasets          Normal/edge/level datasets to acquire from.
	 * @param timestampCache    Place to cache timestamps.
	 * @param duration          The sample count, before applying the zoom factor.
	 * @param cachedMode        True to enable the cache.
	 * @param showTimestamps    Ignored. This is only used by PlotMilliseconds.
	 */
	@Override void initialize(long endTimestamp, long endSampleNumber, double zoomLevel, DatasetsInterface datasets, StorageTimestamps.Cache timestampsCache, long duration, boolean cachedMode, boolean showTimestamps) {
		
		this.datasets = datasets;
		this.cachedMode = cachedMode;
		xAxisTitle = "Sample Number";
		
		// calculate the domain, ensuring it's >= 2
		plotDomain = Math.round(duration * zoomLevel);
		if(plotDomain < 2)
			plotDomain = 2;
		plotMaxX = endSampleNumber;
		plotMinX = plotMaxX - plotDomain + 1;
		
		// storing the domain as (domain - 1) because (domain - 1) is used for all of the math related to the domain
		plotDomain--;
		
		// exit if there are no samples to display
		long trueLastSampleNumber = datasets.hasAnyType() ? datasets.connection.getSampleCount() - 1 : -1;
		if(trueLastSampleNumber < 0 || endSampleNumber < 0) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			samplesMinY = -1;
			samplesMaxY =  1;
			return;
		}
		
		// determine which samples to display
		maxSampleNumber = Long.min(endSampleNumber, trueLastSampleNumber);
		minSampleNumber = maxSampleNumber - plotDomain;
		
		if(minSampleNumber < plotMinX)
			minSampleNumber = plotMinX;
		
		if(minSampleNumber < 0)
			minSampleNumber = 0;
		
		plotSampleCount = maxSampleNumber - minSampleNumber + 1;
		
		if(maxSampleNumber < plotMinX) {
			maxSampleNumber = -1;
			minSampleNumber = -1;
			plotSampleCount = 0;
			samplesMinY = -1;
			samplesMaxY =  1;
			return;
		}
		
		// get the range
		float[] range = datasets.getRange((int) minSampleNumber, (int) maxSampleNumber);
		samplesMinY = range[0];
		samplesMaxY = range[1];
		
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
	
	/**
	 * Step 5: Acquire the samples.
	 * 
	 * @param plotMinY      Y-axis value at the bottom of the plot.
	 * @param plotMaxY      Y-axis value at the top of the plot.
	 * @param plotWidth     Width of the plot region, in pixels.
	 * @param plotHeight    Height of the plot region, in pixels.
	 */
	@Override void acquireSamplesNonCachedMode(float plotMinY, float plotMaxY, int plotWidth, int plotHeight) {
		
		events = new BitfieldEvents(true, false, datasets, (int) minSampleNumber, (int) maxSampleNumber);
		
		buffersY = new FloatBuffer[datasets.normalsCount()];
		for(int datasetN = 0; datasetN < datasets.normalsCount(); datasetN++) {
			Dataset dataset = datasets.getNormal(datasetN);
			if(!dataset.isBitfield)
				buffersY[datasetN] = datasets.getSamplesBuffer(dataset, (int) minSampleNumber, (int) maxSampleNumber);
		}
		
	}
	
	/**
	 * Step 5: Acquire the samples.
	 * 
	 * @param plotMinY      Y-axis value at the bottom of the plot.
	 * @param plotMaxY      Y-axis value at the top of the plot.
	 * @param plotWidth     Width of the plot region, in pixels.
	 * @param plotHeight    Height of the plot region, in pixels.
	 */
	@Override void acquireSamplesCachedMode(float plotMinY, float plotMaxY, int plotWidth, int plotHeight) {
		
		events = new BitfieldEvents(true, false, datasets, (int) minSampleNumber, (int) maxSampleNumber);
		
		// check if the cache must be flushed
		cacheIsValid = datasets.normalDatasets.equals(previousNormalDatasets) &&
		               datasets.edgeStates.equals(previousEdgeStates) &&
		               datasets.levelStates.equals(previousLevelStates) &&
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
		long firstSampleNumber = minSampleNumber;
		long lastSampleNumber  = maxSampleNumber;
		if(cacheIsValid) {
			if(firstSampleNumber == previousMinSampleNumber && lastSampleNumber <= previousMaxSampleNumber) {
				// no change, nothing to draw
				firstSampleNumber = lastSampleNumber;
			} else if(firstSampleNumber > previousMinSampleNumber) {
				// moving forward in time
				firstSampleNumber = previousMaxSampleNumber;
			} else if(firstSampleNumber < previousMinSampleNumber) {
				// moving backwards in time
				lastSampleNumber = previousMinSampleNumber + calculateSamplesNeededAtEdge(plotWidth);
			} else if(firstSampleNumber == previousMinSampleNumber && lastSampleNumber > previousMaxSampleNumber) {
				// moving forward in time while x=0 is still on screen
				firstSampleNumber = previousMaxSampleNumber;
			} else {
				// moving backwards in time while x=0 is still on screen, nothing to draw
				firstSampleNumber = lastSampleNumber;
			}
		}
		
		// the framebuffer is used as a ring buffer. since the pixels may wrap around from the right edge back to the left edge,
		// we may need to split the rendering into 2 draw calls (splitting it at the right edge of the framebuffer)
		long splittingSampleNumber = plotMaxX - (plotMaxX % plotDomain);

		if(firstSampleNumber == lastSampleNumber) {
			
			// nothing to draw
			draw1.enabled = false;
			draw2.enabled = false;
			
		} else if(lastSampleNumber <= splittingSampleNumber || firstSampleNumber >= splittingSampleNumber) {
			
			// only 1 draw call required (no need to wrap around the ring buffer)
			draw1.enableAndAcquire(datasets, firstSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			draw2.enabled = false;
			
		} else {
			
			// to prevent a possible cache flush BETWEEN draw1 and draw2, first ask for the full range so the cache will be flushed if necessary BEFORE we prepare for draw1 and draw2
			draw1.enableAndAcquire(datasets, firstSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			
			// 2 draw calls required because we need to wrap around the ring buffer
			draw1.enableAndAcquire(datasets, firstSampleNumber, splittingSampleNumber, plotWidth, plotHeight);
			draw2.enableAndAcquire(datasets, splittingSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			
		}
		
		// save current state
		previousNormalDatasets = datasets.normalDatasets;
		previousEdgeStates = datasets.edgeStates;
		previousLevelStates = datasets.levelStates;
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
	 * Calculates the (x,y,w,h) arguments for glScissor() based on what region the samples will occupy on the framebuffer.
	 * 
	 * @param firstSampleNumber    The first sample number (inclusive.)
	 * @param lastSampleNumber     The last sample number (inclusive.)
	 * @param plotWidth            Width of the plot region, in pixels.
	 * @param plotHeight           Height of the plot region, in pixels.
	 * @return                     An int[4] of {x,y,w,h}
	 */
	private int[] calculateScissorArgs(long firstSampleNumber, long lastSampleNumber, int plotWidth, int plotHeight) {
		
		// convert the sample number into a pixel number on the framebuffer, keeping in mind that it's a ring buffer
		long rbSampleNumber = firstSampleNumber % plotDomain;
		int rbPixelX = (int) (rbSampleNumber * plotWidth / plotDomain);
		
		// convert the sample count into a pixel count
		int pixelWidth = (int) Math.ceil((double) (lastSampleNumber - firstSampleNumber) * (double) plotWidth / (double) plotDomain);
		
		int[] args = new int[4];
		args[0] = rbPixelX;
		args[1] = 0;
		args[2] = pixelWidth;
		args[3] = plotHeight;
		return args;
		
	}
	
	/**
	 * We need to draw slightly more samples than theoretically required because adjacent samples can affect the edges of the glScissor'd region.
	 * 
	 * @param plotWidth    Width of the plot region, in pixels.
	 * @return             Number of extra samples to draw.
	 */
	private long calculateSamplesNeededAtEdge(int plotWidth) {
		
		double samplesPerPixel = (double) (plotDomain + 1) / (double) plotWidth;
		long extraSamplesNeeded = (long) Math.ceil(samplesPerPixel * Theme.lineWidth);
		return extraSamplesNeeded;
		
	}
	
	/**
	 * Step 6: Render the plot on screen.
	 * 
	 * @param gl             The OpenGL context.
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param xPlotLeft      Bottom-left corner location, in pixels.
	 * @param yPlotBottom    Bottom-left corner location, in pixels.
	 * @param plotWidth      Width of the plot region, in pixels.
	 * @param plotHeight     Height of the plot region, in pixels.
	 * @param plotMinY       Y-axis value at the bottom of the plot.
	 * @param plotMaxY       Y-axis value at the top of the plot.
	 */
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
		OpenGL.translateMatrix(plotMatrix,                    xPlotLeft,                  yPlotBottom, 0);
		OpenGL.scaleMatrix    (plotMatrix, (float) plotWidth/plotDomain, (float) plotHeight/plotRange, 1);
		OpenGL.translateMatrix(plotMatrix,                            0,                    -plotMinY, 0);
		OpenGL.useMatrix(gl, plotMatrix);
		
		// draw each dataset
		if(plotSampleCount >= 2) {
			for(int i = 0; i < datasets.normalsCount(); i++) {
				
				Dataset dataset = datasets.getNormal(i);
				if(dataset.isBitfield)
					continue;

				OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, dataset.glColor, buffersY[i], (int) plotSampleCount, (int) (plotMinX >= 0 ? 0 : plotMinX * -1));
				
				// also draw points if there are relatively few samples on screen
				boolean fewSamplesOnScreen = (plotWidth / (float) plotDomain) > (2 * Theme.pointWidth);
				if(fewSamplesOnScreen)
					OpenGL.drawPointsY(gl, dataset.glColor, buffersY[i], (int) plotSampleCount, (int) (plotMinX >= 0 ? 0 : plotMinX * -1));
				
			}
		}
		
		OpenGL.useMatrix(gl, chartMatrix);
		
		// draw any bitfield changes
		if(plotSampleCount >= 2) {
			List<BitfieldEvents.EdgeMarker>  edgeMarkers  = events.getEdgeMarkers ((connection, sampleNumber) -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			List<BitfieldEvents.LevelMarker> levelMarkers = events.getLevelMarkers((connection, sampleNumber) -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xPlotLeft, yPlotBottom + plotHeight, xPlotLeft + plotWidth, yPlotBottom, -1, -1);
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
	}
	
	/**
	 * Step 6: Render the plot on screen.
	 * 
	 * @param gl             The OpenGL context.
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param xPlotLeft      Bottom-left corner location, in pixels.
	 * @param yPlotBottom    Bottom-left corner location, in pixels.
	 * @param plotWidth      Width of the plot region, in pixels.
	 * @param plotHeight     Height of the plot region, in pixels.
	 * @param plotMinY       Y-axis value at the bottom of the plot.
	 * @param plotMaxY       Y-axis value at the top of the plot.
	 */
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
			int[] args = calculateScissorArgs(plotMaxX, plotMaxX - plotMinX, plotWidth, plotHeight);
			gl.glScissor(args[0], args[1], args[2], args[3]);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			gl.glDisable(GL3.GL_SCISSOR_TEST);
		}
		if(plotMaxX > maxSampleNumber) {
			// if x>maxSampleNumber is on screen, we need to erase the x>maxSampleNumber region because it may have old data on it
			gl.glEnable(GL3.GL_SCISSOR_TEST);
			int[] args = calculateScissorArgs(maxSampleNumber, plotMaxX, plotWidth, plotHeight);
			gl.glScissor(args[0], args[1], args[2], args[3]);
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			if(plotMaxX % plotDomain < maxSampleNumber % plotDomain) {
				args = calculateScissorArgs(plotMaxX - (plotMaxX % plotDomain), plotMaxX, plotWidth, plotHeight);
				gl.glScissor(args[0], args[1], args[2], args[3]);
				gl.glClearColor(0, 0, 0, 0);
				gl.glClear(GL3.GL_COLOR_BUFFER_BIT);
			}
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
		OpenGL.scaleMatrix    (offscreenMatrix, (float) plotWidth / plotDomain, (float) plotHeight / plotRange, 1);
		OpenGL.translateMatrix(offscreenMatrix,                              0,                      -plotMinY, 0);
		OpenGL.useMatrix(gl, offscreenMatrix);
		
		// draw each dataset
		if(plotSampleCount >= 2) {
			for(int i = 0; i < datasets.normalsCount(); i++) {
				
				Dataset dataset = datasets.getNormal(i);
				if(dataset.isBitfield)
					continue;
				
				boolean fewSamplesOnScreen = (plotWidth / (float) plotDomain) > (2 * Theme.pointWidth);
				
				if(draw1.enabled) {
					gl.glEnable(GL3.GL_SCISSOR_TEST);
					gl.glScissor(draw1.scissorArgs[0], draw1.scissorArgs[1], draw1.scissorArgs[2], draw1.scissorArgs[3]);
					OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, dataset.glColor, draw1.buffersY[i], draw1.sampleCount, draw1.xOffset);
					if(fewSamplesOnScreen)
						OpenGL.drawPointsY(gl, dataset.glColor, draw1.buffersY[i], draw1.sampleCount, draw1.xOffset);
					gl.glDisable(GL3.GL_SCISSOR_TEST);
				}
				
				if(draw2.enabled) {
					gl.glEnable(GL3.GL_SCISSOR_TEST);
					gl.glScissor(draw2.scissorArgs[0], draw2.scissorArgs[1], draw2.scissorArgs[2], draw2.scissorArgs[3]);
					OpenGL.drawLinesY(gl, GL3.GL_LINE_STRIP, dataset.glColor, draw2.buffersY[i], draw2.sampleCount, draw2.xOffset);
					if(fewSamplesOnScreen)
						OpenGL.drawPointsY(gl, dataset.glColor, draw2.buffersY[i], draw2.sampleCount, draw2.xOffset);
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
		OpenGL.drawRingbufferTexturedBox(gl, texHandle, xPlotLeft, yPlotBottom, plotWidth, plotHeight, startX+0f);
		
		// clip to the plot region
		int[] originalScissorArgs = new int[4];
		gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
		gl.glScissor(originalScissorArgs[0] + (int) xPlotLeft, originalScissorArgs[1] + (int) yPlotBottom, plotWidth, plotHeight);
		
		// draw any bitfield changes
		if(plotSampleCount >= 2) {
			List<BitfieldEvents.EdgeMarker>  edgeMarkers  = events.getEdgeMarkers ((connection, sampleNumber) -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			List<BitfieldEvents.LevelMarker> levelMarkers = events.getLevelMarkers((connection, sampleNumber) -> (sampleNumber - plotMinX) / (float) plotDomain * plotWidth);
			ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xPlotLeft, yPlotBottom + plotHeight, xPlotLeft + plotWidth, yPlotBottom, -1, -1);
		}

		// stop clipping to the plot region
		gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
		
//		// draw the framebuffer without ringbuffer wrapping, 10 pixels above the plot
//		gl.glDisable(GL3.GL_SCISSOR_TEST);
//		OpenGL.drawTexturedBox(gl, texHandle, true, xPlotLeft, yPlotBottom + plotHeight + 10, plotWidth, plotHeight, 0, false);
//		gl.glEnable(GL3.GL_SCISSOR_TEST);
		
	}
	
	/**
	 * Checks if a tooltip should be drawn for the mouse's current location.
	 * 
	 * @param mouseX       The mouse's location along the x-axis, in pixels (0 = left edge of the plot.)
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
		float pixelX = getPixelXforSampleNumber(sampleNumber, plotWidth);
		return new TooltipInfo(true, sampleNumber, label, pixelX);
		
	}
	
	/**
	 * Gets the horizontal location, relative to the plot, for a sample number.
	 * 
	 * @param sampleNumber    The sample number.
	 * @param plotWidth       Width of the plot region, in pixels.
	 * @return                Corresponding horizontal location on the plot, in pixels (0 = left edge of the plot.)
	 */
	@Override float getPixelXforSampleNumber(long sampleNumber, float plotWidth) {
		
		return (float) (sampleNumber - plotMinX) / (float) plotDomain * plotWidth;
		
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
	
	private class DrawCallData {
		
		boolean enabled;        // if this object contains samples to draw
		int[] scissorArgs;      // {x,y,w,h} for glScissor() when drawing
		int xOffset;            // sample count offset from left edge of the plot
		int sampleCount;        // number of vertices
		FloatBuffer[] buffersY; // y-axis values for the vertices (one buffer per dataset.)
		
		/**
		 * Acquires samples and related data so it can be drawn later.
		 * 
		 * @param datasets             Datasets and corresponding caches to acquire from.
		 * @param firstSampleNumber    First sample number (inclusive.)
		 * @param lastSampleNumber     Last sample number (inclusive.)
		 * @param plotWidth            Width of the plot region, in pixels.
		 * @param plotHeight           Height of the plot region, in pixels.
		 */
		void enableAndAcquire(DatasetsInterface datasets, long firstSampleNumber, long lastSampleNumber, int plotWidth, int plotHeight) {
			
			enabled = true;
			xOffset = (int) (firstSampleNumber % plotDomain);
			sampleCount = (int) (lastSampleNumber - firstSampleNumber + 1);
			scissorArgs = calculateScissorArgs(firstSampleNumber, lastSampleNumber, plotWidth, plotHeight);
			
			// acquire extra samples before and after, because adjacent samples affect the edges of this region
			long extraSamplesNeeded = calculateSamplesNeededAtEdge(plotWidth);
			xOffset -= extraSamplesNeeded;
			firstSampleNumber -= extraSamplesNeeded;
			lastSampleNumber += extraSamplesNeeded;
			if(firstSampleNumber < 0) {
				xOffset -= firstSampleNumber;
				firstSampleNumber = 0;
			}
			if(lastSampleNumber > maxSampleNumber)
				lastSampleNumber = maxSampleNumber;
			sampleCount = (int) (lastSampleNumber - firstSampleNumber + 1);
			
			// acquire the samples
			buffersY = new FloatBuffer[datasets.normalsCount()];
			for(int datasetN = 0; datasetN < datasets.normalsCount(); datasetN++) {
				Dataset dataset = datasets.getNormal(datasetN);
				if(!dataset.isBitfield)
					buffersY[datasetN] = datasets.getSamplesBuffer(dataset, (int) firstSampleNumber, (int) lastSampleNumber);
			}
			
		}
		
	}

}
