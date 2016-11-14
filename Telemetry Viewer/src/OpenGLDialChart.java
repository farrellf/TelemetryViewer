import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.jogamp.opengl.GL2;

/**
 * Renders a dial showing the most recent sample's value.
 * The range of the dial is determined by the min and max values in the chart's duration.
 * The mean and standard deviation of that chart's duration is also shown.
 */
@SuppressWarnings("serial")
public class OpenGLDialChart extends PositionedChart {
	
	final int   dialResolution = 200; // how many quads to draw
	final float dialThickness = 0.4f; // percentage of the radius
	Samples     samples;
	
	public static ChartDescriptor getDescriptor() {
		
		return new ChartDescriptor() {
			
			@Override public String toString()        { return "Dial Chart"; }
			@Override public int getMinimumDuration() { return 2; }
			@Override public int getDefaultDuration() { return 150000; }
			@Override public int getMaximumDuration() { return Integer.MAX_VALUE; }
			@Override public String[] getInputNames() { return new String[] {"Data"}; }
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
				return new OpenGLDialChart(x1, y1, x2, y2, chartDuration, chartInputs);
			}
			
		};
		
	}
	
	@Override public String toString() {
		
		return "Dial Chart";
		
	}

	
	public OpenGLDialChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		samples = new Samples();

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
		
		// get the samples
		int endIndex = Controller.getSamplesCount(datasets) - 1;
		int startIndex = endIndex - duration + 1;
		if(startIndex < 0)
			startIndex = 0;
		int sampleCount = endIndex - startIndex + 1;
		if(sampleCount < 1)
			return;
		datasets[0].getSamples(startIndex, endIndex, samples);

		// prepare
		float dialMin = samples.min;
		float dialMax = samples.max;
		float range = dialMax - dialMin;
		
		double[] doubles = new double[samples.buffer.length];
		for(int i = 0; i < samples.buffer.length; i++)
			doubles[i] = (double) samples.buffer[i];
		DescriptiveStatistics stats = new DescriptiveStatistics(doubles);
		
		float reading      = samples.buffer[samples.buffer.length - 1];
		String minText     = ChartUtils.formattedNumber(dialMin, 6);
		String maxText     = ChartUtils.formattedNumber(dialMax, 6);
		String readingText = ChartUtils.formattedNumber(reading, 6) + " " + datasets[0].unit;
		String meanText    = "Mean: " +    ChartUtils.formattedNumber(stats.getMean(), 6);
		String stdDevText  = "Std Dev: " + ChartUtils.formattedNumber(stats.getStandardDeviation(), 6);
		String titleText   = datasets[0].name;
		
		// calculate x and y positions of everything
		float xPlotLeft = Theme.perimeterPadding;
		float xPlotRight = width - Theme.perimeterPadding;
		float plotWidth = xPlotRight - xPlotLeft;

		float yStatsTextBaseline = height - Theme.perimeterPadding - FontUtils.tickTextHeight;
		float yMinMaxTextBaseline = Theme.perimeterPadding;
		float yDialBottom = yMinMaxTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
		
		float xCircleCenter = plotWidth / 2f + Theme.perimeterPadding;
		float yCircleCenter = yDialBottom;
		float circleRadius = Float.min(yStatsTextBaseline - Theme.tickTextPadding - yCircleCenter, plotWidth / 2f);
		if(circleRadius < 0)
			return;
		
		float xMinXtextLeft = xCircleCenter - circleRadius;
		float xMaxXtextLeft = xCircleCenter + circleRadius - FontUtils.tickTextWidth(maxText);
		float xMeanTextLeft = xPlotLeft;
		float xStdDevTextLeft = xPlotRight - FontUtils.tickTextWidth(stdDevText);
		float xReadingTextLeft = xCircleCenter - FontUtils.xAxisTextWidth(readingText) / 2f;
		float yTitleBaseline = yMinMaxTextBaseline + FontUtils.xAxisTextHeight + Theme.tickTextPadding;
		float xTitleLeft = (width / 2f) - (FontUtils.xAxisTextWidth(titleText) / 2f);
		
		// draw the dial
		float dialPercentage = (reading - dialMin) / range;
		gl.glBegin(GL2.GL_QUADS);
			for(float angle = 0; angle < Math.PI; angle += Math.PI / dialResolution) {
				
				if(angle > Math.PI * dialPercentage)
					gl.glColor4fv(Theme.plotBackgroundColor, 0);
				else
					gl.glColor4fv(samples.color, 0);
				
				float x1 = -1f * circleRadius * (float) Math.cos(angle) + xCircleCenter;                                                  // top-left
				float y1 =       circleRadius * (float) Math.sin(angle) + yCircleCenter;
				float x2 = -1f * circleRadius * (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter;                       // top-right
				float y2 =       circleRadius * (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
				float x4 = -1f * circleRadius * (1 - dialThickness) * (float) Math.cos(angle) + xCircleCenter;                            // bottom-left
				float y4 =       circleRadius * (1 - dialThickness) * (float) Math.sin(angle) + yCircleCenter;
				float x3 = -1f * circleRadius * (1 - dialThickness) * (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter; // bottom-right
				float y3 =       circleRadius * (1 - dialThickness) * (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
				
				gl.glVertex2f(x1, y1);
				gl.glVertex2f(x2, y2);
				gl.glVertex2f(x3, y3);
				gl.glVertex2f(x4, y4);
				
			}
		gl.glEnd();
		
		// draw the text
		FontUtils.drawTickText(minText,      (int) xMinXtextLeft,    (int) yMinMaxTextBaseline);
		FontUtils.drawTickText(maxText,      (int) xMaxXtextLeft,    (int) yMinMaxTextBaseline);
		FontUtils.drawTickText(meanText,     (int) xMeanTextLeft,    (int) yStatsTextBaseline);
		FontUtils.drawTickText(stdDevText,   (int) xStdDevTextLeft,  (int) yStatsTextBaseline);
		FontUtils.drawXaxisText(titleText,   (int) xTitleLeft,       (int) yTitleBaseline);
		FontUtils.drawXaxisText(readingText, (int) xReadingTextLeft, (int) yMinMaxTextBaseline);
		
	}

}
