import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * A class that performs DFTs, caches them, and renders them on screen.
 * All cache logic is implemented here, so you just specify what to visualize, and the cache will be updated as needed.
 */
public class OpenGLFrequencyDomainCache {

	int[][][] histogram; // [datasetN][freqBinN][powerBinN]
	
	int previousDftWindowLength;
	int previousDftsCount;
	DatasetsInterface previousDatasets;
	String previousChartMode;
	
	int datasetsCount;
	
	int[] liveViewFbHandle;
	int[] liveViewTexHandle;
	int[] waveformViewTexHandle;
	int[] waterfallViewTexHandle;
	
	private static class DFT {
		
		static double binSizeHz;
		static int binCount;
		static float minHz;
		static float maxHz;
		static float minPower;
		static float maxPower;
		static int windowLength;
		static int firstDft;
		static int lastDft;
		
		List<float[]> forDataset = new ArrayList<>(); // .get(datasetN)[binN]
		int firstSampleNumber = -1;
		boolean populated;
		
	}
	private DFT[] dft; // ring buffer
	
	/**
	 * Prepares the cache.
	 */
	public OpenGLFrequencyDomainCache() {
		
		dft = new DFT[0];
		
		DFT.binSizeHz = 0;
		DFT.binCount = 0;
		DFT.minHz = 0;
		DFT.maxHz = 1;
		DFT.minPower = 0;
		DFT.maxPower = 1;
		DFT.windowLength = 0;
		DFT.firstDft = 0;
		DFT.lastDft = 0;
		
		previousDftWindowLength = 0;
		previousDftsCount = 0;
		previousDatasets = new DatasetsInterface();
		previousChartMode = "";
		
	}
	
	/**
	 * Updates the cache, calculating new DFTs as needed.
	 * If the mode is Multiple or Waterfall, the DFTs will be aligned to their window size (e.g. a window size of 1000 will make DFTs of samples 0-999, 1000-1999, etc.)
	 * If the mode is Single, the DFT will be of the most recent samples, not aligned to the window size (e.g. if the most recent sample is 1234, the DFT would be of samples 235-1234.)
	 * 
	 * @param endSampleNumber    Sample number corresponding with the right edge of a time-domain plot. NOTE: this sample might not exist yet!
	 * @param windowLength       How many samples make up each DFT.
	 * @param dftsCount          Number of DFTs to use for Multiple/Waterfall modes. Should be 1 for Single mode.
	 * @param datasets           Datasets to visualize, along with their sample caches.
	 * @param chartMode          "Single" or "Multiple" or "Waterfall"
	 */
	public void calculateDfts(int endSampleNumber, int windowLength, int dftsCount, DatasetsInterface datasets, String chartMode) {
		
		datasetsCount = datasets.normalsCount();
		
		// flush the cache if necessary
		if(previousDftWindowLength != windowLength || !previousDatasets.equals(datasets) || previousDftsCount != dftsCount || !previousChartMode.equals(chartMode)) {
			
			dft = new DFT[dftsCount];
			for(int dftN = 0; dftN < dftsCount; dftN++)
				dft[dftN] = new DFT();

			previousDftWindowLength = windowLength;
			previousDftsCount = dftsCount;
			previousDatasets = datasets;
			previousChartMode = chartMode;
			
		}
		
		// calculate the DFTs
		if(chartMode.equals("Single")) {
			
			int trueLastSampleNumber = datasets.hasNormals() ? datasets.connection.getSampleCount() - 1 : 0;
			int lastSampleNumber = Integer.min(endSampleNumber, trueLastSampleNumber);
			int firstSampleNumber = lastSampleNumber - windowLength + 1;
			if(firstSampleNumber < 0)
				firstSampleNumber = 0;
			if(lastSampleNumber < firstSampleNumber)
				lastSampleNumber = firstSampleNumber;
			
			DFT theDft = dft[0];
			
			// stop if nothing to do
			if(!datasets.hasNormals() || lastSampleNumber - firstSampleNumber < 1) {
				theDft.firstSampleNumber = -1;
				theDft.populated = false;
				DFT.binSizeHz = 0;
				DFT.binCount = 0;
				DFT.minHz = 0;
				DFT.maxHz = !datasets.hasNormals() ? 1 : datasets.connection.sampleRate / 2;
				DFT.minPower = 0;
				DFT.maxPower = 1;
				DFT.windowLength = 0;
				DFT.firstDft = 0;
				DFT.lastDft = 0;
				return;
			}
			
			// calculate the DFT for each dataset
			int sampleRate = datasets.connection.sampleRate;
			final int first = firstSampleNumber;
			final int last = lastSampleNumber;
			theDft.forDataset.clear();
			datasets.forEachNormal((dataset, cache) -> {
				float[] samples = dataset.getSamplesArray(first, last, cache);
				theDft.forDataset.add(calculateDFTxy(samples, sampleRate));
			});
			theDft.firstSampleNumber = firstSampleNumber;
			theDft.populated = true;
			
			// calculate the domain and range
			// the DFTs are currently calculated from DC to Nyquist
			// but the user can specify an arbitrary window length, so the max frequency may actually be a little below Nyquist
			float[] firstDft = theDft.forDataset.get(0);
			DFT.minHz    = 0;
			DFT.maxHz    = firstDft[firstDft.length - 2];
			DFT.minPower = firstDft[1];
			DFT.maxPower = firstDft[1];
			DFT.windowLength = lastSampleNumber - firstSampleNumber + 1;
			
			theDft.forDataset.forEach(datasetsDft -> {
				for(int i = 1; i < datasetsDft.length; i += 2) {
					float y = datasetsDft[i];
					if(y > DFT.maxPower) DFT.maxPower = y;
					if(y < DFT.minPower) DFT.minPower = y;
				}
			});
			
		} else {
			
			DFT.lastDft = (endSampleNumber + 1) / windowLength - 1;
			DFT.firstDft = DFT.lastDft - dftsCount + 1;
			if(DFT.firstDft < 0)
				DFT.firstDft = 0;
			if(DFT.lastDft < 0)
				return;

			// calculate the DFTs for each dataset
			int sampleRate = datasets.connection.sampleRate;
			int trueLastSampleNumber = datasets.connection.getSampleCount() - 1;
			for(int dftN = DFT.firstDft; dftN <= DFT.lastDft; dftN++) {
				int firstSampleNumber = dftN * windowLength;
				int lastSampleNumber = firstSampleNumber + windowLength - 1;
				DFT theDft = dft[dftN % dftsCount];
				if(theDft.firstSampleNumber != firstSampleNumber || !theDft.populated) {
					theDft.firstSampleNumber = firstSampleNumber;
					theDft.populated = false;
					if(lastSampleNumber <= trueLastSampleNumber) {
						theDft.forDataset.clear();
						datasets.forEachNormal((dataset, cache) -> {
							float[] samples = dataset.getSamplesArray(firstSampleNumber, lastSampleNumber, cache);
							theDft.forDataset.add(calculateDFT(samples, sampleRate));
						});
						theDft.populated = true;
					}
				}
			}
			
			// calculate the domain and range
			// the DFTs are currently calculated from DC to Nyquist
			// but the user can specify an arbitrary window length, so the max frequency may actually be a little below Nyquist
			DFT.minHz    = 0;
			DFT.maxHz    = 1;
			DFT.minPower = 0;
			DFT.maxPower = 1;
			DFT.windowLength = windowLength;
			DFT theDft = dft[DFT.firstDft % dftsCount];
			float[] firstDft = theDft.forDataset.get(0);
			if(theDft.populated) {
				DFT.maxHz    = (float) ((double) (firstDft.length - 1) * (double) sampleRate / (double) windowLength);
				DFT.minPower = firstDft[0];
				DFT.maxPower = firstDft[0];
				for(int dftN = DFT.firstDft; dftN <= DFT.lastDft; dftN++) {
					theDft = dft[dftN % dftsCount];
					if(theDft.populated)
						theDft.forDataset.forEach(datasetsDft -> {
							for(int i = 0; i < datasetsDft.length; i++) {
								float y = datasetsDft[i];
								if(y > DFT.maxPower) DFT.maxPower = y;
								if(y < DFT.minPower) DFT.minPower = y;
							}
						});
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
		
		return DFT.minHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Maximum frequency of the DFTs.
	 */
	public float getMaxHz() {
		
		return DFT.maxHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Minimum power of the DFTs.
	 */
	public float getMinPower() {
		
		return DFT.minPower;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Maximum power of the DFTs.
	 */
	public float getMaxPower() {
		
		return DFT.maxPower;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    Window length of the DFTs.
	 */
	public int getWindowLength() {
		
		return DFT.windowLength;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    The number of DFTs that can be displayed. This will be less than the full amount if not enough data exists.
	 */
	public int getActualWindowCount() {
		
		return DFT.lastDft - DFT.firstDft + 1;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    The bin size, in Hertz.
	 */
	public double getBinSizeHz() {
		
		return DFT.binSizeHz;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @return    The number of bins.
	 */
	public int getBinCount() {
		
		return DFT.binCount;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @param binN    The bin number.
	 * @return        A float[] containing the bin values (one value per dataset) for that bin number, or null if a DFT does not exist.
	 */
	public float[] getPowerLevelsForLiveViewBin(int binN) {
		
		DFT theDft = dft[0];
		
		if(!theDft.populated)
			return null;
		
		float[] binValueForDataset = new float[datasetsCount];
		for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
			binValueForDataset[datasetN] = theDft.forDataset.get(datasetN)[(2 * binN) + 1];
		
		return binValueForDataset;
		
	}
	
	/**
	 * This should only be called after calculateDfts() and renderWaveformView().
	 * 
	 * @param freqBinN     The frequency bin number.
	 * @param powerBinN    The power bin number.
	 * @return             An int[] containing the DFT counts (one count per dataset) for that bin, or null if no DFTs currently exist.
	 */
	public int[] getWaveformCountsForBin(int freqBinN, int powerBinN) {
		
		if(getActualWindowCount() < 1)
			return null;
		
		int[] waveformCountForDataset = new int[datasetsCount];
		for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
			waveformCountForDataset[datasetN] = histogram[datasetN][freqBinN][powerBinN];
		
		return waveformCountForDataset;
		
	}
	
	/**
	 * This should only be called after calculateDfts().
	 * 
	 * @param binN    The bin number.
	 * @param rowN    The waterfall row (DFT number) to query.
	 * @return        A float[] containing the bin values (one value per dataset) for that bin number / row number combination, or null if a DFT does not exist there.
	 */
	public float[] getWaterfallPowerLevelsForBin(int binN, int rowN) {
		
		// map rowN to the ringbuffer
		DFT theDft = dft[(DFT.lastDft - rowN) % dft.length];
		if(!theDft.populated)
			return null;
		
		float[] binValuesForDataset = new float[datasetsCount];
		for(int datasetN = 0; datasetN < datasetsCount; datasetN++)
			binValuesForDataset[datasetN] = theDft.forDataset.get(datasetN)[binN];
		
		return binValuesForDataset;
		
	}
	
	/**
	 * Draws a single DFT on screen.
	 * The x-axis is frequency, the y-axis is power.
	 * 
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param bottomLeftX    Lower-left coordinate of the region to draw in.
	 * @param bottomLeftY    Lower-left coordinate of the region to draw in.
	 * @param width          Width of region to draw in.
	 * @param height         Height of region to draw in.
	 * @param minPower       Y value at the bottom of the plot.
	 * @param maxPower       Y value at the top of the plot.
	 * @param gl             The OpenGL context.
	 * @param datasets       The datasets to visualize.
	 */
	public void renderSingle(float[] chartMatrix, int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2ES3 gl, List<Dataset> datasets) {
		
		DFT theDft = dft[0];
		if(!theDft.populated)
			return;
		
		float[] offscreenMatrix = new float[16];
		OpenGL.makeOrthoMatrix(offscreenMatrix, 0, width, 0, height, -1, 1);
		// adjust so: x = (x - plotMinX) / domain * plotWidth;
		// adjust so: y = (y - plotMinY) / plotRange * plotHeight;
		OpenGL.scaleMatrix    (offscreenMatrix, width,                      height,                   1);
		OpenGL.scaleMatrix    (offscreenMatrix, 1f/(DFT.maxHz - DFT.minHz), 1f/(maxPower - minPower), 1);
		OpenGL.translateMatrix(offscreenMatrix, -DFT.minHz,                 -minPower,                0);
		
		if(liveViewFbHandle == null || liveViewTexHandle == null) {
			liveViewFbHandle = new int[1];
			liveViewTexHandle = new int[1];
			OpenGL.createOffscreenFramebuffer(gl, liveViewFbHandle, liveViewTexHandle);
		}
		
		// draw the DFT line charts onto the texture
		OpenGL.startDrawingOffscreen(gl, offscreenMatrix, liveViewFbHandle, liveViewTexHandle, width, height);
		for(int datasetN = 0; datasetN < datasets.size(); datasetN++) {
			
			int dftBinCount = theDft.forDataset.get(datasetN).length / 2;
			FloatBuffer buffer = Buffers.newDirectFloatBuffer(theDft.forDataset.get(datasetN));
			OpenGL.drawLinesXy(gl, GL3.GL_LINE_STRIP, datasets.get(datasetN).glColor, buffer, dftBinCount);
			
			// also draw points if there are relatively few bins on screen
			if(width / dftBinCount > 2 * Theme.pointWidth)
				OpenGL.drawPointsXy(gl, datasets.get(datasetN).glColor, buffer, dftBinCount);
			
		}
		OpenGL.stopDrawingOffscreen(gl, chartMatrix);
		
		OpenGL.drawTexturedBox(gl, liveViewTexHandle, true, bottomLeftX, bottomLeftY, width, height, 0, false);
		
	}
	
	/**
	 * Draws multiple overlaid DFTs on screen. This is a 2D histogram and looks like a long-exposure photo of renderSingle().
	 * Multiple DFTs are stacked on top of each other to get a feel for what regions of the spectrum have been occupied.
	 * The x-axis is frequency, the y-axis is power.
	 * 
	 * @param chartMatrix    The current 4x4 matrix.
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
	public void renderMultiple(float[] chartMatrix, int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2ES3 gl, List<Dataset> datasets, int rowCount) {
		
		if(!dft[0].populated)
			return;
		
		// calculate a 2D histogram for each dataset
		int xBinCount = dft[0].forDataset.get(0).length;
		histogram = new int[datasetsCount][xBinCount][rowCount];
		for(int dftN = DFT.firstDft; dftN <= DFT.lastDft; dftN++) {
			DFT theDft = dft[dftN % dft.length];
			if(theDft.populated) {
				for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
					float[] dft = theDft.forDataset.get(datasetN);
					for(int xBin = 0; xBin < xBinCount; xBin++) {
						int yBin = (int) ((dft[xBin] - minPower) / (maxPower - minPower) * rowCount);
						if(yBin >= 0 && yBin < rowCount)
							histogram[datasetN][xBin][yBin]++;
					}
				}
			}
		}

		float dftCount = getActualWindowCount();
		int pixelCount = xBinCount * rowCount;
		
		ByteBuffer bytes = Buffers.newDirectByteBuffer(pixelCount * 4 * 4); // 4 bytes per: r,g,b,a
		FloatBuffer pixels = bytes.asFloatBuffer();
		
		// populate the pixels, simulating glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
		for(int dataset = 0; dataset < datasetsCount; dataset++) {
			float newR = datasets.get(dataset).glColor[0];
			float newG = datasets.get(dataset).glColor[1];
			float newB = datasets.get(dataset).glColor[2];
			
			for(int y = 0; y < rowCount; y++) {
				for(int x = 0; x < xBinCount; x++) {
					int index = (x + (y * xBinCount)) * 4; // 4 floats per pixel
					
					float r = pixels.get(index + 0);
					float g = pixels.get(index + 1);
					float b = pixels.get(index + 2);
					float a = pixels.get(index + 3);
					
					float newA = (float) histogram[dataset][x][y] / dftCount;
					
					r = (newR * newA) + (r * (1f - newA));
					g = (newG * newA) + (g * (1f - newA));
					b = (newB * newA) + (b * (1f - newA));
					a = (newA * 1f)   + (a * (1f - newA));
					
					pixels.put(index + 0, r);
					pixels.put(index + 1, g);
					pixels.put(index + 2, b);
					pixels.put(index + 3, a);
				}
			}
		}
		
		if(waveformViewTexHandle == null) {
			waveformViewTexHandle = new int[1];
			OpenGL.createTexture(gl, waveformViewTexHandle, xBinCount, rowCount, GL3.GL_RGBA, GL3.GL_FLOAT, false);
		}
		OpenGL.writeTexture(gl, waveformViewTexHandle, xBinCount, rowCount, GL3.GL_RGBA, GL3.GL_FLOAT, bytes);
		OpenGL.drawTexturedBox(gl, waveformViewTexHandle, false, bottomLeftX, bottomLeftY, width, height, 1f/xBinCount/2f, false);
		
	}
	
	/**
	 * Draws a DFT waterfall on screen. Like the multiple DFT mode, the waterfall mode shows the history of what regions of the spectrum have been occupied.
	 * But instead of just overlapping the DFTs, they are drawn as a stack of lines, where each line represents one DFT.
	 * This allows you to see what regions of the spectrum have been occupied *and* when they were occupied.
	 * The x-axis is frequency, the y-axis is time.
	 * 
	 * @param chartMatrix    The current 4x4 matrix.
	 * @param bottomLeftX    Lower-left coordinate of the region to draw in.
	 * @param bottomLeftY    Lower-left coordinate of the region to draw in.
	 * @param width          Width of region to draw in.
	 * @param height         Height of region to draw in.
	 * @param minPower       Lower limit, if a DFT bin is <= this it will be drawn as fully transparent. Units are log10(Watts).
	 * @param maxPower       Upper limit, if a DFT bin is >= this, it will be drawn as fully opaque. Units are log10(Watts).
	 * @param gl             The OpenGL context.
	 * @param datasets       The datasets to visualize.
	 */
	public void renderWaterfall(float[] chartMatrix, int bottomLeftX, int bottomLeftY, int width, int height, float minPower, float maxPower, GL2ES3 gl, List<Dataset> datasets) {
		
		int binCount = dft[0].forDataset.get(0).length;
		int dftsCount = dft.length; // but some DFTs might not be populated
		int datasetsCount = datasets.size();
		
		int pixelCount = binCount * dftsCount;
		
		ByteBuffer bytes = Buffers.newDirectByteBuffer(pixelCount * 4 * 4); // 4 bytes per: r,g,b,a
		FloatBuffer pixels = bytes.asFloatBuffer();
		
		// populate the pixels, simulating glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
		for(int datasetN = 0; datasetN < datasetsCount; datasetN++) {
			float newR = datasets.get(datasetN).glColor[0];
			float newG = datasets.get(datasetN).glColor[1];
			float newB = datasets.get(datasetN).glColor[2];
			
			for(int y = 0; y < dftsCount; y++) {
				
				int dftN = DFT.lastDft - y;
				DFT theDft = dftN >= 0 ? dft[dftN % dftsCount] : null;
				if(theDft == null || !theDft.populated)
					continue;
				
				float[] dft = theDft.forDataset.get(datasetN);
				
				for(int x = 0; x < binCount; x++) {
					int index = (x + (y * binCount)) * 4; // 4 floats per pixel
					
					float r = pixels.get(index + 0);
					float g = pixels.get(index + 1);
					float b = pixels.get(index + 2);
					float a = pixels.get(index + 3);
					
					float newA = (dft[x] - minPower) / (maxPower - minPower);
					
					r = (newR * newA) + (r * (1f - newA));
					g = (newG * newA) + (g * (1f - newA));
					b = (newB * newA) + (b * (1f - newA));
					a = (newA * 1f)   + (a * (1f - newA));
					
					pixels.put(index + 0, r);
					pixels.put(index + 1, g);
					pixels.put(index + 2, b);
					pixels.put(index + 3, a);
				}
				
			}
		}
		
		if(waterfallViewTexHandle == null) {
			waterfallViewTexHandle = new int[1];
			OpenGL.createTexture(gl, waterfallViewTexHandle, binCount, dftsCount, GL3.GL_RGBA, GL3.GL_FLOAT, false);
		}
		OpenGL.writeTexture(gl, waterfallViewTexHandle, binCount, dftsCount, GL3.GL_RGBA, GL3.GL_FLOAT, bytes);
		OpenGL.drawTexturedBox(gl, waterfallViewTexHandle, false, bottomLeftX, bottomLeftY, width, height, 1f/binCount/2f, false);
		
	}
	
	/**
	 * Deletes the off-screen framebuffer and texture.
	 * 
	 * @param gl    The OpenGL context.
	 */
	public void freeResources(GL2ES3 gl) {
		
		if(liveViewTexHandle != null)
			gl.glDeleteTextures(1, liveViewTexHandle, 0);
		if(liveViewFbHandle != null)
			gl.glDeleteFramebuffers(1, liveViewFbHandle, 0);
		if(waveformViewTexHandle != null)
			gl.glDeleteTextures(1, waveformViewTexHandle, 0);
		if(waterfallViewTexHandle != null)
			gl.glDeleteTextures(1, waterfallViewTexHandle, 0);
		
		liveViewTexHandle = null;
		waveformViewTexHandle = null;
		waterfallViewTexHandle = null;
		liveViewFbHandle = null;
		
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
		DFT.binSizeHz = 1.0 / ((double) sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		DFT.binCount = (int) (maxFrequencyHz / DFT.binSizeHz) + 1;
		
		// generate the sine and cosine LUTs
		if(sinLUT == null || cosLUT == null || sinLUT[0].length != sampleCount || cosLUT[0].length != sampleCount) {
			sinLUT = new double[DFT.binCount][sampleCount];
			cosLUT = new double[DFT.binCount][sampleCount];
			System.gc();
			for(int bin = 0; bin < DFT.binCount; bin++) {
				double frequencyHz  = (double) bin * DFT.binSizeHz;
				for(int sample = 0; sample < sampleCount; sample++) {
					double timeSec      = (double) sample / samplesPerSecond;
					sinLUT[bin][sample] = Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
					cosLUT[bin][sample] = Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
				}
			}
		}
		
		// calc the DFT, assuming the samples are in Volts, and assuming the load is a unit load (1 ohm)
		float[] powerLevels = new float[DFT.binCount];
		
		for(int bin = 0; bin < DFT.binCount; bin++) {
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
				powerW = Math.pow(10, -36); // arbitrarily picked because it looks like a reasonable min
			
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
		DFT.binSizeHz = 1.0 / ((double) sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		DFT.binCount = (int) (maxFrequencyHz / DFT.binSizeHz) + 1;
		
		// generate the sine and cosine LUTs
		if(sinLUT == null || cosLUT == null || sinLUT[0].length != sampleCount || cosLUT[0].length != sampleCount) {
			sinLUT = new double[DFT.binCount][sampleCount];
			cosLUT = new double[DFT.binCount][sampleCount];
			System.gc();
			for(int bin = 0; bin < DFT.binCount; bin++) {
				double frequencyHz  = (double) bin * DFT.binSizeHz;
				for(int sample = 0; sample < sampleCount; sample++) {
					double timeSec      = (double) sample / samplesPerSecond;
					sinLUT[bin][sample] = Math.sin(2.0 * Math.PI * frequencyHz * timeSec);
					cosLUT[bin][sample] = Math.cos(2.0 * Math.PI * frequencyHz * timeSec);
				}
			}
		}
		
		// calc the DFT, assuming the samples are in Volts, and assuming the load is a unit load (1 ohm)
		float[] powerLevels = new float[DFT.binCount*2];
		
		for(int bin = 0; bin < DFT.binCount; bin++) {
			double realV = 0.0;
			double imaginaryV = 0.0;
			double frequencyHz = (double) bin * DFT.binSizeHz;
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
				powerW = Math.pow(10, -36); // arbitrarily picked because it looks like a reasonable min
			
			powerLevels[bin*2]     = (float) frequencyHz;
			powerLevels[bin*2 + 1] = (float) Math.log10(powerW);
		}
		
		return powerLevels;
		
	}
	
}
