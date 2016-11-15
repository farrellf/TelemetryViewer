import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL2;

/**
 * Renders a Fourier transformation line chart.
 */
@SuppressWarnings("serial")
public class OpenGLFrequencyDomainChart extends PositionedChart {
	
	AutoScale autoscale;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Frequency Domain Chart"; }
			@Override public int getMinimumDuration() { return 2; }
			@Override public int getDefaultDuration() { return 1000; }
			@Override public int getMaximumDuration() { return 50000; }
			@Override public String[] getInputNames() { return null; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new OpenGLFrequencyDomainChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Frequency Domain Chart";
		
	}
	
	public OpenGLFrequencyDomainChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		autoscale = new AutoScale(AutoScale.MODE_EXPONENTIAL, 90, 0.20f);

	}
	
	@Override public void drawChart(GL2 gl, int width, int height) {
		
		// draw background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.backgroundColor, 0);
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, 0);
		gl.glEnd();
		
		// draw perimeter outline
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.perimeterOutlineColor, 0);
			gl.glVertex2f(0,     0);
			gl.glVertex2f(0,     height);
			gl.glVertex2f(width, height);
			gl.glVertex2f(width, 0);
		gl.glEnd();
		
		// calculate the DFTs
		int maxX = Controller.getSamplesCount(datasets) - 1;
		int minX = maxX - duration + 1;
		if(minX < 0) minX = 0;
		
		if(maxX < duration)
			return;
		
		float[][] dfts = new float[datasets.length][];
		for(int i = 0; i < datasets.length; i++) {
			float[] samples = datasets[i].getSamplesArray(minX, maxX);
			dfts[i] = calculateDFT(samples, Controller.getSampleRate());
		}
		
		// calculate domain and range
		float dftMinY = dfts[0][1];
		float dftMaxY = dfts[0][1];
		for(int dft = 0; dft < dfts.length; dft++) {
			for(int i = 0; i < dfts[dft].length / 2; i++) {
				float y = dfts[dft][2*i + 1];
				if(y > dftMaxY) dftMaxY = y;
				if(y < dftMinY) dftMinY = y;
			}
		}
		float plotMinX = dfts[0][0];
		float plotMaxX = dfts[0][dfts[0].length - 2];
		float domain = plotMaxX - plotMinX;
		
		// ensure range is >0
		float plotMinY = dftMinY;
		float plotMaxY = dftMaxY;
		if(plotMinY == plotMaxY) {
			float value = plotMinY;
			plotMinY = value - 0.001f;
			plotMaxY = value + 0.001f;
		}
		autoscale.update(plotMinY, plotMaxY);
		plotMaxY = autoscale.getMax();
		plotMinY = autoscale.getMin();
		float plotRange = plotMaxY - plotMinY;
		
		// calculate x and y positions of everything
		String xAxisTitle = "Frequency (Hertz)";
		String yAxisTitle = "Power (Watts)";
		float xLegendBorderLeft = Theme.perimeterPadding;
		float yLegendBorderBottom = Theme.perimeterPadding;
		float yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
		float yLegendTextTop = yLegendTextBaseline + FontUtils.legendTextHeight;
		float yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
		
		float yXaxisTitleTextBasline = Theme.perimeterPadding;
		float yXaxisTitleTextTop = yXaxisTitleTextBasline + FontUtils.xAxisTextHeight;
		
		float yXaxisTickTextBaseline = Float.max(yLegendBorderTop + Theme.legendTextPadding, yXaxisTitleTextTop + Theme.legendTextPadding);
		float yXaxisTickTextTop = yXaxisTickTextBaseline + FontUtils.tickTextHeight;
		float yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
		float yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
		
		float yPlotBottom = yXaxisTickTop;
		float yPlotTop = height - Theme.perimeterPadding;
		float plotHeight = yPlotTop - yPlotBottom;
		if(plotHeight < 1.0f)
			return;
		
		Map<Float, String> yDivisions = ChartUtils.getLogYdivisions(plotHeight, plotMinY, plotMaxY);
		
		float maxYtickTextWidth = 0;
		for(String text : yDivisions.values()) {
			float w = FontUtils.tickTextWidth(text); 
			if(w > maxYtickTextWidth)
				maxYtickTextWidth = w;
		}
		
		float xYaxisTitleTextTop = Theme.perimeterPadding;
		float xYaxisTitleTextBaseline = xYaxisTitleTextTop + FontUtils.yAxisTextHeight;
		
		float xYaxisTickTextLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
		float xYaxisTickTextRight = xYaxisTickTextLeft + maxYtickTextWidth;
		float xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
		float xYaxisTickRight = xYaxisTickLeft + Theme.tickLength;
		
		float xPlotLeft = xYaxisTickRight;
		float xPlotRight = width - Theme.perimeterPadding;
		float plotWidth = xPlotRight - xPlotLeft;
		if(plotWidth < 1.0f)
			return;
		
		Map<Float, String> xDivisions = ChartUtils.getFloatXdivisions125(plotWidth, plotMinX, plotMaxX);
		
		float xXaxisTitleLeft = xPlotLeft +   (plotWidth  / 2.0f) - (FontUtils.xAxisTextWidth(xAxisTitle)  / 2.0f);
		float yYaxisTitleLeft = yPlotBottom + (plotHeight / 2.0f) - (FontUtils.yAxisTextWidth(yAxisTitle) / 2.0f);
		
		// draw plot background
		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(Theme.plotBackgroundColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
		// draw the vertical division lines
		gl.glBegin(GL2.GL_LINES);
		for(Float xValue : xDivisions.keySet()) {
			float x = (xValue - plotMinX) / domain * plotWidth + xPlotLeft;
			gl.glColor4fv(Theme.divisionLinesColor, 0);
			gl.glVertex2f(x, yPlotTop);
			gl.glVertex2f(x, yPlotBottom);
			gl.glColor4fv(Theme.tickLinesColor, 0);
			gl.glVertex2f(x, yXaxisTickTop);
			gl.glVertex2f(x, yXaxisTickBottom);
		}
		gl.glEnd();
		
		// draw the vertical division line text
		for(Map.Entry<Float,String> entry : xDivisions.entrySet()) {
			float x = (entry.getKey() - plotMinX) / domain * plotWidth + xPlotLeft - (FontUtils.tickTextWidth(entry.getValue()) / 2.0f);
			float y = yXaxisTickTextBaseline;
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw the horizontal division lines
		gl.glBegin(GL2.GL_LINES);
		for(Float entry : yDivisions.keySet()) {
			float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
			gl.glColor4fv(Theme.divisionLinesColor, 0);
			gl.glVertex2f(xPlotLeft,  y);
			gl.glVertex2f(xPlotRight, y);
			gl.glColor4fv(Theme.tickLinesColor, 0);
			gl.glVertex2f(xYaxisTickLeft,  y);
			gl.glVertex2f(xYaxisTickRight, y);
		}
		gl.glEnd();
		
		// draw the horizontal division line text
		for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
			float x = xYaxisTickTextRight - FontUtils.tickTextWidth(entry.getValue());
			float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (FontUtils.tickTextHeight / 2.0f);
			FontUtils.drawTickText(entry.getValue(), (int) x, (int) y);
		}
		
		// draw the legend
		float xOffset = xLegendBorderLeft + Theme.strokeWidth + Theme.legendTextPadding;
		for(Dataset dataset : datasets) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4f(dataset.color.getRed()/255.0f, dataset.color.getGreen()/255.0f, dataset.color.getBlue()/255.0f, 1);
				gl.glVertex2f(xOffset,                              yLegendTextBaseline);
				gl.glVertex2f(xOffset,                              yLegendTextTop);
				gl.glVertex2f(xOffset + FontUtils.legendTextHeight, yLegendTextTop);
				gl.glVertex2f(xOffset + FontUtils.legendTextHeight, yLegendTextBaseline);
			gl.glEnd();
			
			xOffset += FontUtils.legendTextHeight + Theme.legendTextPadding;
			
			FontUtils.drawLegendText(dataset.name, (int) xOffset, (int) yLegendTextBaseline);
			xOffset += FontUtils.legendTextWidth(dataset.name) + Theme.legendNamesPadding;
		}
		xOffset -= Theme.legendNamesPadding;
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.legendOutlineColor, 0);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderBottom);
			gl.glVertex2f(xLegendBorderLeft,           yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderTop);
			gl.glVertex2f(xLegendBorderLeft + xOffset, yLegendBorderBottom);
		gl.glEnd();
		
		// draw the x-axis title, shifting it to the right if the legend gets in the way
		{
			float x = xXaxisTitleLeft > xOffset + Theme.legendTextPadding ? xXaxisTitleLeft : xLegendBorderLeft + xOffset + Theme.legendTextPadding;
			float y = yXaxisTitleTextBasline;
			FontUtils.drawXaxisText(xAxisTitle, (int) x, (int) y);
		}
		
		// draw the y-axis title, if space is available
		if(yYaxisTitleLeft >= yPlotBottom) {
			float x = xYaxisTitleTextBaseline;
			float y = yYaxisTitleLeft;
			FontUtils.drawYaxisText(yAxisTitle, (int) x, (int) y, 90);
		}
		
		// draw the DFTs
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		
		// adjust so: x = (x - plotMinX) / domain * plotWidth + xPlotLeft;
		gl.glTranslatef(xPlotLeft, 0, 0);
		gl.glScalef(plotWidth, 1, 1);
		gl.glScalef(1.0f / domain, 1, 1);
		gl.glTranslatef(-plotMinX, 0, 0);
		
		// adjust so y = (y - plotMinY) / plotRange * plotHeight + yPlotBottom;
		gl.glTranslatef(0, yPlotBottom, 0);
		gl.glScalef(1, plotHeight, 1);
		gl.glScalef(1, 1.0f / plotRange, 1);
		gl.glTranslatef(0, -plotMinY, 0);
		
		for(int i = 0; i < datasets.length; i++) {
			gl.glColor4f(datasets[i].color.getRed()/255.0f, datasets[i].color.getGreen()/255.0f, datasets[i].color.getBlue()/255.0f, 1);
			gl.glVertexPointer(2, GL2.GL_FLOAT, 0, Buffers.newDirectFloatBuffer(dfts[i]));
			gl.glDrawArrays(GL2.GL_LINE_STRIP, 0, dfts[i].length / 2);
		}
		
		gl.glPopMatrix();

		// draw the plot border
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glColor4fv(Theme.plotOutlineColor, 0);
			gl.glVertex2f(xPlotLeft,  yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotTop);
			gl.glVertex2f(xPlotRight, yPlotBottom);
			gl.glVertex2f(xPlotLeft,  yPlotBottom);
		gl.glEnd();
		
	}

	double[][] sinLUT;
	double[][] cosLUT;
	float[] calculateDFT(float[] samples, int sampleRate) {
		
		// bin size (in Hertz) is the reciprocal of the window size (in seconds)
		// example: 500ms window -> 1/0.5 = 2 Hz bin size
		double samplesPerSecond = sampleRate;
		int sampleCount = samples.length;
		double binSizeHz = 1.0 / ((double) sampleCount / samplesPerSecond);
		
		// maximum frequency range (in Hertz) is from 0 to the sample rate (in Hertz), divided by 2
		// example: sampling at 1kHz -> 0 Hz to 1000/2 = 500 Hz
		double maxFrequencyHz = samplesPerSecond / 2.0;
		int binCount = (int) (maxFrequencyHz / binSizeHz) + 1;
		
		// generate the sine and cosine LUTs
		if(sinLUT == null || cosLUT == null) {
			sinLUT = new double[binCount][sampleCount];
			cosLUT = new double[binCount][sampleCount];
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
			
			powerLevels[bin*2]     = (float) frequencyHz;
			powerLevels[bin*2 + 1] = (float) Math.log10(powerW);
		}
		
		return powerLevels;
		
	}

}
