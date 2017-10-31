import java.nio.FloatBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;

/**
 * A class that performs DFTs, caches them, and renders them on screen.
 * All cache logic is implemented here, so you just specify what to visualize, and the cache will be updated as needed.
 */
public class OpenGLFrequencyDomainCache {

	float[][][] dfts;               // [datasetN][dftN][binN]
	int[][][] histogram;            // used by waveform view, [datasetN][binN][powerBinN]
	int[][] firstSampleNumberOfDft; // [datasetN][dftN]
	int previousDftWindowLength;
	int previousTotalSampleCount;
	Dataset[] previousDatasets;
	String previousChartType;
	
	float minHz;
	float maxHz;
	float minPower;
	float maxPower;
	
	double binSizeHz;
	int binCount;	
	
	int firstDft;
	int lastDft;

	FloatBuffer waterfallPixels;
	FloatBuffer waveformPixels;
	
	int[] fbHandle;
	int[] texHandle;
	
	/**
	 * Creates an off-screen framebuffer and an empty cache.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public OpenGLFrequencyDomainCache(GL2 gl) {
		
		dfts = new float[0][][];
		firstSampleNumberOfDft = new int[0][];
		previousDftWindowLength = 0;
		previousTotalSampleCount = 0;
		previousDatasets = new Dataset[0];
		
		minHz = 0;
		maxHz = 0;
		minPower = 0;
		maxPower = 0;
		
		firstDft = 0;
		lastDft = 0;
		
		// create and use a framebuffer
		fbHandle = new int[1];
		gl.glGenFramebuffers(1, fbHandle, 0);
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		
		// create and use a texture
		texHandle = new int[1];
		gl.glGenTextures(1, texHandle, 0);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, 512, 512, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null); // dummy 512x512 texture
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_TEXTURE_2D, texHandle[0], 0);
		gl.glDrawBuffers(1, new int[] {GL2.GL_COLOR_ATTACHMENT0}, 0);
		
		// check for errors
		if(gl.glCheckFramebufferStatus(GL2.GL_FRAMEBUFFER) != GL2.GL_FRAMEBUFFER_COMPLETE)
			System.err.println("Error while creating the frequency domain cache's framebuffer or texture.");
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
	}
	
	/**
	 * Updates the cache, calculating new DFTs as needed.
	 * If the type is Waveform View or Waterfall View, the DFTs will be aligned to their window size (e.g. a window size of 1000 will make DFTs of samples 0-999, 1000-1999, etc.)
	 * If the type is Live View, the DFTs will be of the most recent samples, not aligned to the window size (e.g. if the most recent sample is 1234, the DFTs would be of samples 235-1234.)
	 * 
	 * @param lastSampleNumber    The last sample number.
	 * @param dftWindowLength     How many samples make up each DFT.
	 * @param totalSampleCount    Total number of samples. Must be >= dftWindowLength.
	 * @param datasets            The datasets to visualize.
	 * @param chartType           "Live View" or "Waveform View" or "Waterfall View"
	 */
	public void calculateDfts(int lastSampleNumber, int dftWindowLength, int totalSampleCount, Dataset[] datasets, String chartType) {
		
		int datasetsCount = datasets.length;
		int dftsCount = totalSampleCount / dftWindowLength;
		
		// flush the cache if the DFT window length has changed, or the datasets have changed, or the chart type has changed
		if(previousDftWindowLength != dftWindowLength || previousDatasets != datasets || previousTotalSampleCount != totalSampleCount || !previousChartType.equals(chartType)) {
			
			dfts = new float[datasetsCount][dftsCount][];
			firstSampleNumberOfDft = new int[datasetsCount][dftsCount];
			for(int dataset = 0; dataset < datasetsCount; dataset++)
				for(int dft = 0; dft < dftsCount; dft++)
					firstSampleNumberOfDft[dataset][dft] = -1;
			previousDftWindowLength = dftWindowLength;
			previousTotalSampleCount = totalSampleCount;
			previousDatasets = datasets;
			previousChartType = chartType;
			
		}
		
		lastDft = lastSampleNumber / dftWindowLength - 1;
		firstDft = lastDft - dftsCount + 1;
		
		if(firstDft < 0)
			firstDft = 0;
		
		if(lastDft < 0)
			return;
			
		// calculate the DFTs as needed
		if(chartType.equals("Live View")) {
			
			int endX = lastSampleNumber;
			int startX = endX - dftWindowLength + 1;
			
			for(int dataset = 0; dataset < datasets.length; dataset++) {
				float[] samples = datasets[dataset].getSamplesArray(startX, endX);
				dfts[dataset][0] = calculateDFTxy(samples, CommunicationController.getSampleRate());
			}
			
			// calculate the DFT domain
			// the DFTs are currently calculated from DC to Nyquist
			// but the user can specify an arbitrary window length, so the max frequency may actually be a little below Nyquist
			minHz = 0;
			maxHz = dfts[0][0][dfts[0][0].length - 2];
			
			// calculate the DFT range
			minPower = dfts[0][0][1];
			maxPower = dfts[0][0][1];
			for(int dataset = 0; dataset < datasets.length; dataset++) {
				for(int i = 1; i < dfts[dataset][0].length; i += 2) {
					float y = dfts[dataset][0][i];
					if(y > maxPower) maxPower = y;
					if(y < minPower) minPower = y;
				}
			}
			
		} else {
			
			for(int dataset = 0; dataset < datasets.length; dataset++) {
				for(int dft = firstDft; dft <= lastDft; dft++) {
					
					int firstSampleNumber = dft * dftWindowLength;
					int rbIndex = dft % dftsCount;
					
					if(firstSampleNumberOfDft[dataset][rbIndex] != firstSampleNumber) {
						
						int startX = firstSampleNumber;
						int endX = startX + dftWindowLength - 1;
						float[] samples = datasets[dataset].getSamplesArray(startX, endX);
						dfts[dataset][rbIndex] = calculateDFT(samples, CommunicationController.getSampleRate());
						firstSampleNumberOfDft[dataset][rbIndex] = startX;
						
					}
					
				}
			}
			
			// calculate the DFT domain
			// the DFTs are currently calculated from DC to Nyquist
			// but the user can specify an arbitrary window length, so the max frequency may actually be a little below Nyquist
			minHz = 0;
			maxHz = (float) ((double) (dfts[0][0].length - 1) * (double) CommunicationController.getSampleRate() / (double) dftWindowLength);
			
			// calculate the DFT range
			minPower = dfts[0][firstDft % dftsCount][0];
			maxPower = dfts[0][firstDft % dftsCount][0];
			for(int dataset = 0; dataset < datasets.length; dataset++) {
				for(int dft = firstDft; dft <= lastDft; dft++) {
					for(int i = 0; i < dfts[dataset][dft % dftsCount].length; i++) {
						float y = dfts[dataset][dft % dftsCount][i];
						if(y > maxPower) maxPower = y;
						if(y < minPower) minPower = y;
					}
				}
			}
			
		}
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Minimum frequency of the DFTs.
	 */
	public float getMinHz() {
		
		return minHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Maximum frequency of the DFTs.
	 */
	public float getMaxHz() {
		
		return maxHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Minimum power of the DFTs.
	 */
	public float getMinPower() {
		
		return minPower;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Maximum power of the DFTs.
	 */
	public float getMaxPower() {
		
		return maxPower;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    The bin size, in Hertz.
	 */
	public double getBinSizeHz() {
		
		return binSizeHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    The number of bins.
	 */
	public int getBinCount() {
		
		return binCount;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @param binN    The bin number.
	 * @return        A float[] containing the bin values (one value per dataset) for that bin number.
	 */
	public float[] getBinValuesForLiveView(int binN) {
		
		float[] binValues = new float[dfts.length];
		for(int datasetN = 0; datasetN < binValues.length; datasetN++)
			binValues[datasetN] = dfts[datasetN][0][(2 * binN) + 1];
		
		return binValues;
		
	}
	
	/**
	 * This should only be called after calculateDfts() and renderWaveformView().
	 * 
	 * @param binN         The frequency bin number.
	 * @param powerBinN    The power bin number.
	 * @return             An int[] containing the number of DFTs that occupy that bin (one number per dataset.)
	 */
	public int[] getBinValuesForWaveformView(int binN, int powerBinN) {
		
		int[] binCounts = new int[dfts.length];
		for(int datasetN = 0; datasetN < binCounts.length; datasetN++)
			binCounts[datasetN] = histogram[datasetN][binN][powerBinN];
		
		return binCounts;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @param binN    The bin number.
	 * @param rowN    The waterfall row (DFT number) to query.
	 * @return        A float[] containing the bin values (one value per dataset) for that bin number / row number combination.
	 */
	public float[] getBinValuesForWaterfallView(int binN, int rowN) {
		
		// map rowN to a row in the dfts[][][] array because it is a ringbuffer
		int row = (lastDft - rowN) % dfts[0].length;
		
		float[] binValues = new float[dfts.length];
		for(int datasetN = 0; datasetN < binValues.length; datasetN++)
			binValues[datasetN] = dfts[datasetN][row][binN];
		
		return binValues;
		
	}
	
	/**
	 * Draws a Live View on screen. "Live View" is a line chart of a single DFT.
	 * The x-axis is frequency, the y-axis is power.
	 * 
	 * @param bottomLeftX    Lower-left coordinate of the region to draw in.
	 * @param bottomLeftY    Lower-left coordinate of the region to draw in.
	 * @param width          Width of region to draw in.
	 * @param height         Height of region to draw in.
	 * @param minPower       Y value at the bottom of the plot.
	 * @param maxPower       Y value at the top of the plot.
	 * @param gl             The OpenGL context.
	 * @param datasets       The datasets to visualize.
	 */
	public void renderLiveView(int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2 gl, Dataset[] datasets) {
		
		// save the viewport/scissor/point settings, modelview matrix, and projection matrix
		gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_SCISSOR_BIT | GL2.GL_POINT_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);

		// replace the existing texture
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, width, height, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL2.GL_SCISSOR_TEST);
		
		// set the projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, width, 0, height, -1, 1);
		
		// set the modelview matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// set the blend function
		gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
		// clear the texture and set the modelview matrix
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		
		// adjust so: x = (x - plotMinX) / domain * plotWidth + xPlotLeft;
		gl.glTranslatef(0, 0, 0);
		gl.glScalef(width, 1, 1);
		gl.glScalef(1.0f / (maxHz - minHz), 1, 1);
		gl.glTranslatef(-minHz, 0, 0);
		
		// adjust so y = (y - plotMinY) / plotRange * plotHeight + yPlotBottom;
		gl.glTranslatef(0, 0, 0);
		gl.glScalef(1, height, 1);
		gl.glScalef(1, 1.0f / (maxPower - minPower), 1);
		gl.glTranslatef(0, -minPower, 0);
		
		// draw the DFT line charts onto the texture
		for(int dataset = 0; dataset < datasets.length; dataset++) {
			
			int dftBinCount = dfts[dataset][0].length / 2;
			
			gl.glColor4f(datasets[dataset].color.getRed()/255.0f, datasets[dataset].color.getGreen()/255.0f, datasets[dataset].color.getBlue()/255.0f, 1);
			gl.glVertexPointer(2, GL2.GL_FLOAT, 0, Buffers.newDirectFloatBuffer(dfts[dataset][0]));
			gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, dftBinCount);
			
			// also draw points if there are relatively few bins on screen
			if(width / dftBinCount > 2 * Theme.pointSize)
				gl.glDrawArrays(GL2.GL_POINTS, 0, dftBinCount);
			
		}
		
		gl.glPopMatrix();
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
		// restore old viewport/scissor/point settings, projection matrix, and modelview matrix
		gl.glPopAttrib();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		
		
		// draw a textured quad on screen, with the texture replacing the color and opacity of the quad
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glBegin(GL2.GL_QUADS);
			gl.glTexCoord2f(0, 0);
			gl.glVertex2f(bottomLeftX, bottomLeftY);
			
			gl.glTexCoord2f(0, 1);
			gl.glVertex2f(bottomLeftX, bottomLeftY + height);
			
			gl.glTexCoord2f(1, 1);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY + height);
			
			gl.glTexCoord2f(1, 0);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY);
		gl.glEnd();
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
		// restore the normal blend function
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Draws a waveform view on screen. "Waveform View" is a 2D histogram and looks like a long-exposure photo of "Live View."
	 * Multiple DFTs are stacked on top of each other to get a feel for what regions of the spectrum have been occupied.
	 * The x-axis is frequency, the y-axis is power.
	 * 
	 * @param bottomLeftX    Lower-left coordinate of the region to draw in.
	 * @param bottomLeftY    Lower-left coordinate of the region to draw in.
	 * @param width          Width of region to draw in.
	 * @param height         Height of region to draw in.
	 * @param minPower       Y value at the bottom of the plot.
	 * @param maxPower       Y value at the top of the plot.
	 * @param gl             The OpenGL context.
	 * @param datasets       The datasets to visualize.
	 * @param rowCount       How many vertical bins to divide the plot into. (The number of horizontal bins is the DFT bin count.)
	 */
	public void renderWaveformView(int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2 gl, Dataset[] datasets, int rowCount) {
		
		// calculate a 2D histogram for each dataset
		int datasetsCount = datasets.length;
		int xBinCount = dfts[0][0].length;
		histogram = new int[datasetsCount][xBinCount][rowCount];
		for(int dataset = 0; dataset < datasetsCount; dataset++) {
			for(int dft = firstDft; dft <= lastDft; dft++) {
				for(int xBin = 0; xBin < xBinCount; xBin++) {
					int yBin = (int) ((dfts[dataset][dft % dfts[0].length][xBin] - minPower) / (maxPower - minPower) * rowCount);
					if(yBin >= 0 && yBin < rowCount)
						histogram[dataset][xBin][yBin]++;
				}
			}
		}
		
		// save the viewport/scissor/point settings, modelview matrix, and projection matrix
		gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_SCISSOR_BIT | GL2.GL_POINT_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);

		// replace the existing texture
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, xBinCount, rowCount, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);

		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, xBinCount, rowCount);
		gl.glDisable(GL2.GL_SCISSOR_TEST);
		
		// set the projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, xBinCount, 0, rowCount, -1, 1);
		
		// set the modelview matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// set the blend function
		gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
		// draw the 2D histogram onto the texture, one point (pixel) at a time
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		gl.glPointSize(1);
		gl.glDisable(GL2.GL_POINT_SMOOTH);
		
		int pixelCount = xBinCount * rowCount;
		float dftCount = lastDft - firstDft + 1;
		
		if(waveformPixels == null || waveformPixels.capacity() != pixelCount * 6)
			waveformPixels = Buffers.newDirectFloatBuffer(pixelCount * 6);
		
		for(int dataset = 0; dataset < datasetsCount; dataset++) {

			waveformPixels.position(0);
			
			float r = datasets[dataset].color.getRed()   / 255.0f;
			float g = datasets[dataset].color.getGreen() / 255.0f;
			float b = datasets[dataset].color.getBlue()  / 255.0f;
			
			for(int x = 0; x < xBinCount; x++) {
				for(int y = 0; y < rowCount; y++) {
					float a = (float) histogram[dataset][x][y] / dftCount;
					waveformPixels.put(x + 0.5f); // +0.5 because of the diamond-exit-rule
					waveformPixels.put(y + 0.5f);
					waveformPixels.put(r);
					waveformPixels.put(g);
					waveformPixels.put(b);
					waveformPixels.put(a);
				}
			}
			
			gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
			gl.glVertexPointer(2, GL2.GL_FLOAT, 6, waveformPixels.position(0));
			gl.glColorPointer(4, GL2.GL_FLOAT, 6, waveformPixels.position(2));
			gl.glDrawArrays(GL2.GL_POINTS, 0, pixelCount*4);
			gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
			
		}
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
		// restore old viewport/scissor/point settings, projection matrix, and modelview matrix
		gl.glPopAttrib();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		
		// draw a textured quad on screen, with the texture replacing the color and opacity of the quad
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glBegin(GL2.GL_QUADS);
			float offset = 1.0f / xBinCount / 2.0f; // to start and stop in the *middle* of the first and last bins
			gl.glTexCoord2f(0 + offset, 0);
			gl.glVertex2f(bottomLeftX, bottomLeftY);
			
			gl.glTexCoord2f(0 + offset, 1);
			gl.glVertex2f(bottomLeftX, bottomLeftY + height);
			
			gl.glTexCoord2f(1 - offset, 1);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY + height);
			
			gl.glTexCoord2f(1 - offset, 0);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY);
		gl.glEnd();
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
		// restore the normal blend function
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Draws a waterfall view on screen. Like the "Waveform View", a waterfall view shows the history of what regions of the spectrum have been occupied.
	 * But instead of just overlapping the DFTs, they are drawn as a stack of lines, where each line represents one DFT.
	 * This allows you to see what regions of the spectrum have been occupied *and* when they were occupied.
	 * The x-axis is frequency, the y-axis is time.
	 * 
	 * @param bottomLeftX    Lower-left coordinate of the region to draw in.
	 * @param bottomLeftY    Lower-left coordinate of the region to draw in.
	 * @param width          Width of region to draw in.
	 * @param height         Height of region to draw in.
	 * @param minPower       Lower limit, if a DFT bin is <= this it will be drawn as fully transparent. Units are log10(Watts).
	 * @param maxPower       Upper limit, if a DFT bin is >= this, it will be drawn as fully opaque. Units are log10(Watts).
	 * @param gl             The OpenGL context.
	 * @param datasets       The datasets to visualize.
	 */
	public void renderWaterfallView(int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2 gl, Dataset[] datasets) {
		
		int binCount = dfts[0][0].length;
		int dftsCount = dfts[0].length;
		int datasetsCount = datasets.length;
		
		// save the viewport/scissor/point settings, modelview matrix, and projection matrix
		gl.glPushAttrib(GL2.GL_VIEWPORT_BIT | GL2.GL_SCISSOR_BIT | GL2.GL_POINT_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();

		// switch to the off-screen framebuffer and corresponding texture
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbHandle[0]);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);

		// replace the existing texture
		gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, binCount, dftsCount, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);
		
		// set the viewport and disable the scissor test
		gl.glViewport(0, 0, binCount, dftsCount);
		gl.glDisable(GL2.GL_SCISSOR_TEST);
		
		// set the projection matrix
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, binCount, 0, dftsCount, -1, 1);
		
		// set the modelview matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// set the blend function
		gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
		// draw the waterfall onto the texture, one point (pixel) at a time
		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
		gl.glPointSize(1);
		gl.glDisable(GL2.GL_POINT_SMOOTH);
		
		int pixelCount = binCount * dftsCount;
		
		if(waterfallPixels == null || waterfallPixels.capacity() != pixelCount * 6)
			waterfallPixels = Buffers.newDirectFloatBuffer(pixelCount * 6);
		
		for(int dataset = 0; dataset < datasetsCount; dataset++) {

			waterfallPixels.position(0);
			
			float r = datasets[dataset].color.getRed()   / 255.0f;
			float g = datasets[dataset].color.getGreen() / 255.0f;
			float b = datasets[dataset].color.getBlue()  / 255.0f;
			
			for(int y = 0; y < dftsCount; y++) {
				int dft = lastDft - y;
				for(int x = 0; x < binCount; x++) {
					if(dft < 0) {						
						waterfallPixels.put(x + 0.5f); // +0.5 because of the diamond-exit-rule
						waterfallPixels.put(y + 0.5f);
						waterfallPixels.put(0);
						waterfallPixels.put(0);
						waterfallPixels.put(0);
						waterfallPixels.put(0);
					} else {
						float a = (dfts[dataset][dft % dftsCount][x] - minPower) / (maxPower - minPower);
						if(a < 0) a = 0;
						if(a > 1) a = 1;
						waterfallPixels.put(x + 0.5f);
						waterfallPixels.put(y + 0.5f);
						waterfallPixels.put(r);
						waterfallPixels.put(g);
						waterfallPixels.put(b);
						waterfallPixels.put(a);
					}
				}
			}
			
			gl.glEnableClientState(GL2.GL_COLOR_ARRAY);
			gl.glVertexPointer(2, GL2.GL_FLOAT, 6, waterfallPixels.position(0));
			gl.glColorPointer(4, GL2.GL_FLOAT, 6, waterfallPixels.position(2));
			gl.glDrawArrays(GL2.GL_POINTS, 0, pixelCount*4);
			gl.glDisableClientState(GL2.GL_COLOR_ARRAY);
			
		}
		
		// switch back to the screen framebuffer
		gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
		
		// restore old viewport/scissor/point settings, projection matrix, and modelview matrix
		gl.glPopAttrib();
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		
		// draw a textured quad on screen
		gl.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE_MINUS_SRC_ALPHA);
		gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_REPLACE);
		gl.glDisable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_TEXTURE_2D);
		gl.glBindTexture(GL2.GL_TEXTURE_2D, texHandle[0]);
		gl.glBegin(GL2.GL_QUADS);
			float offset = 1.0f / binCount / 2.0f; // to start and stop in the *middle* of the first and last bins
			gl.glTexCoord2f(0 + offset, 0);
			gl.glVertex2f(bottomLeftX, bottomLeftY);
			
			gl.glTexCoord2f(0 + offset, 1);
			gl.glVertex2f(bottomLeftX, bottomLeftY + height);
			
			gl.glTexCoord2f(1 - offset, 1);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY + height);
			
			gl.glTexCoord2f(1 - offset, 0);
			gl.glVertex2f(bottomLeftX + width, bottomLeftY);
		gl.glEnd();
		gl.glDisable(GL2.GL_TEXTURE_2D);
		
		// restore the normal blend function
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		
	}
	
	/**
	 * Deletes the off-screen framebuffer and texture.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public void freeResources(GL2 gl) {
		
		gl.glDeleteTextures(1, texHandle, 0);
		gl.glDeleteFramebuffers(1, fbHandle, 0);
		
	}
	
	private double[][] sinLUT;
	private double[][] cosLUT;
	
	/**
	 * Calculates a DFT, using look-up tables for sine and cosine.
	 * The returned DFT will contain a sequence of power levels. The corresponding frequencies are *not* included.
	 * 
	 * @param samples       A series of samples, as a float[].
	 * @param sampleRate    Sample rate, in Hz.
	 * @returns             The DFT. If the samples have units of Volts, these numbers will have units of log10(Watts).
	 */
	private float[] calculateDFT(float[] samples, int sampleRate) {
		
		// bin size (in Hertz) is the reciprocal of the window size (in seconds)
		// example: 500ms window -> 1/0.5 = 2 Hz bin size
		double samplesPerSecond = sampleRate;
		int sampleCount = samples.length;
		binSizeHz = 1.0 / ((double) sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		binCount = (int) (maxFrequencyHz / binSizeHz) + 1;
		
		// generate the sine and cosine LUTs
		if(sinLUT == null || cosLUT == null || sinLUT[0].length != sampleCount || cosLUT[0].length != sampleCount) {
			sinLUT = new double[binCount][sampleCount];
			cosLUT = new double[binCount][sampleCount];
			System.gc();
			for(int bin = 0; bin < binCount; bin++) {
				double frequencyHz  = (double) bin * binSizeHz;
				for(int sample = 0; sample < sampleCount; sample++) {
					double timeSec      = (double) sample / samplesPerSecond;
					sinLUT[bin][sample] = Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
					cosLUT[bin][sample] = Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
				}
			}
		}
		
		// calc the DFT, assuming the samples are in Volts, and assuming the load is a unit load (1 ohm)
		float[] powerLevels = new float[binCount];
		
		for(int bin = 0; bin < binCount; bin++) {
			double realV = 0.0;
			double imaginaryV = 0.0;
//			double frequencyHz = (double) bin * binSizeHz;
			for(int x = 0; x < sampleCount; x++) {
				double sample = samples[x];
//				double timeSec   = (double) x / samplesPerSecond;
//				realV      += sample * Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
//				imaginaryV += sample * Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
				realV      += sample * cosLUT[bin][x];
				imaginaryV += sample * sinLUT[bin][x];
			}
			realV      /= (double) sampleCount;
			imaginaryV /= (double) sampleCount;
			double powerW = (realV * realV) + (imaginaryV * imaginaryV);
			powerW *= 2; // because DFT is from -Fs to +Fs
			
			// ensure powerW != 0, which would cause the Math.log10() below to return -Infinity
			if(powerW == 0)
				powerW = Double.MIN_VALUE;
			
			powerLevels[bin] = (float) Math.log10(powerW);
		}
		
		return powerLevels;
		
	}
	
	/**
	 * Calculates a DFT, using look-up tables for sine and cosine.
	 * The returned DFT will contain a sequence of frequencies and their corresponding power levels.
	 * 
	 * @param samples       A series of samples, as a float[].
	 * @param sampleRate    Sample rate, in Hz.
	 * @returns             The DFT. If the samples have units of Volts, these numbers will have units of log10(Watts).
	 */
	float[] calculateDFTxy(float[] samples, int sampleRate) {
		
		// bin size (in Hertz) is the reciprocal of the window size (in seconds)
		// example: 500ms window -> 1/0.5 = 2 Hz bin size
		double samplesPerSecond = sampleRate;
		int sampleCount = samples.length;
		binSizeHz = 1.0 / ((double) sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		binCount = (int) (maxFrequencyHz / binSizeHz) + 1;
		
		// generate the sine and cosine LUTs
		if(sinLUT == null || cosLUT == null || sinLUT[0].length != sampleCount || cosLUT[0].length != sampleCount) {
			sinLUT = new double[binCount][sampleCount];
			cosLUT = new double[binCount][sampleCount];
			System.gc();
			for(int bin = 0; bin < binCount; bin++) {
				double frequencyHz  = (double) bin * binSizeHz;
				for(int sample = 0; sample < sampleCount; sample++) {
					double timeSec      = (double) sample / samplesPerSecond;
					sinLUT[bin][sample] = Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
					cosLUT[bin][sample] = Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
				}
			}
		}
		
		// calc the DFT, assuming the samples are in Volts, and assuming the load is a unit load (1 ohm)
		float[] powerLevels = new float[binCount*2];
		
		for(int bin = 0; bin < binCount; bin++) {
			double realV = 0.0;
			double imaginaryV = 0.0;
			double frequencyHz = (double) bin * binSizeHz;
			for(int x = 0; x < sampleCount; x++) {
				double sample = samples[x];
//				double timeSec   = (double) x / samplesPerSecond;
//				realV      += sample * Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
//				imaginaryV += sample * Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
				realV      += sample * cosLUT[bin][x];
				imaginaryV += sample * sinLUT[bin][x];
			}
			realV      /= (double) sampleCount;
			imaginaryV /= (double) sampleCount;
			double powerW = (realV * realV) + (imaginaryV * imaginaryV);
			powerW *= 2; // because DFT is from -Fs to +Fs
			
			// ensure powerW != 0, which would cause the Math.log10() below to return -Infinity
			if(powerW == 0)
				powerW = Double.MIN_VALUE;
			
			powerLevels[bin*2]     = (float) frequencyHz;
			powerLevels[bin*2 + 1] = (float) Math.log10(powerW);
		}
		
		return powerLevels;
		
	}
	
}
