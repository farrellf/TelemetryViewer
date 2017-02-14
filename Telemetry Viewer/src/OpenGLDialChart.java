import javax.swing.JPanel;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import com.jogamp.opengl.GL2;

/**
 * Renders a dial showing the value of the most recent sample.
 * The range of the dial can be constant, or autoscaled by the min and max values from the chart's samples.
 * The mean and standard deviation of the chart's samples can be shown if desired.
 */
@SuppressWarnings("serial")
public class OpenGLDialChart extends PositionedChart {
	
	final int   dialResolution = 400; // how many quads to draw
	final float dialThickness = 0.4f; // percentage of the radius
	Samples     samples;
	boolean     showStatistics;
	boolean     autoscaleMin;
	boolean     autoscaleMax;
	float       manualMin;
	float       manualMax;
	
	public static ChartFactory getFactory() {
		
		return new ChartFactory() {
			
			WidgetDataset datasetWidget;
			WidgetTextfieldsOptionalMinMax minMaxWidget;
			WidgetEnumeration statisticsWidget;
			WidgetTextfieldInteger sampleCountWidget;
			
			@Override public String toString() { return "Dial Chart"; }
			
			@Override public JPanel[] getWidgets() {

				datasetWidget     = new WidgetDataset("Dataset");
				minMaxWidget      = new WidgetTextfieldsOptionalMinMax("Dial", -1,  1, -Float.MAX_VALUE, Float.MAX_VALUE);
				statisticsWidget  = new WidgetEnumeration("Show Statistics", new String[] {"Yes", "No"});
				sampleCountWidget = new WidgetTextfieldInteger("Sample Count", 1000, 1, Integer.MAX_VALUE);
				
				JPanel[] widgets = new JPanel[6];
				
				widgets[0] = datasetWidget;
				widgets[1] = null;
				widgets[2] = minMaxWidget;
				widgets[3] = null;
				widgets[4] = statisticsWidget;
				widgets[5] = sampleCountWidget;

				return widgets;
				
			}
			
			@Override public int getMinimumSampleCount() {
				
				return 1;
				
			}
			
			@Override public PositionedChart createChart(int x1, int y1, int x2, int y2) {

				int sampleCount      = sampleCountWidget.getValue();
				Dataset[] dataset    = datasetWidget.getDataset();
				boolean autoscaleMin = minMaxWidget.isMinimumAutomatic();
				float manualMin      = minMaxWidget.getMinimumValue();
				boolean autoscaleMax = minMaxWidget.isMaximumAutomatic();
				float manualMax      = minMaxWidget.getMaximumValue();
				boolean showStats    = statisticsWidget.getValue().equals("Yes");
				
				OpenGLDialChart chart = new OpenGLDialChart(x1, y1, x2, y2, sampleCount, dataset);
				chart.setDialRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				chart.showStatistics(showStats);
				
				return chart;
				
			}
			
			@Override public PositionedChart importChart(int x1, int y1, int x2, int y2, Dataset[] datasets, int sampleCount, String[] lines, int firstLineNumber) {
				
				if(lines.length != 5)
					throw new AssertionError("Line " + firstLineNumber + ": Invalid Dial Chart configuration section.");
				
				boolean autoscaleMin = (boolean) ChartUtils.parse(firstLineNumber + 0, lines[0], "autoscale minimum = %b");
				float manualMin      =   (float) ChartUtils.parse(firstLineNumber + 1, lines[1], "manual minimum = %f");
				boolean autoscaleMax = (boolean) ChartUtils.parse(firstLineNumber + 2, lines[2], "autoscale maximum = %b");
				float manualMax      =   (float) ChartUtils.parse(firstLineNumber + 3, lines[3], "manual maximum = %f");
				boolean showStats    = (boolean) ChartUtils.parse(firstLineNumber + 4, lines[4], "show statistics = %b");
				
				OpenGLDialChart chart = new OpenGLDialChart(x1, y1, x2, y2, sampleCount, datasets);
				chart.setDialRange(autoscaleMin, manualMin, autoscaleMax, manualMax);
				chart.showStatistics(showStats);
				
				return chart;
				
			}

		};
		
	}
	
	@Override public String[] exportChartSettings() {
		
		String[] lines = new String[5];
		
		lines[0] = "autoscale minimum = " + autoscaleMin;
		lines[1] = "manual minimum = " + manualMin;
		lines[2] = "autoscale maximum = " + autoscaleMax;
		lines[3] = "manual maximum = " + manualMax;
		lines[4] = "show statistics = " + showStatistics;
		
		return lines;
		
	}
	
	@Override public String toString() {
		
		return "Dial Chart";
		
	}

	
	public OpenGLDialChart(int x1, int y1, int x2, int y2, int chartDuration, Dataset[] chartInputs) {
		
		super(x1, y1, x2, y2, chartDuration, chartInputs);
		
		samples = new Samples();
		
		showStatistics = true;
		
		autoscaleMin = true;
		autoscaleMax = true;
		manualMin = -1.0f;
		manualMax =  1.0f;

	}
	
	public void showStatistics(boolean visibility) {
		
		showStatistics = visibility;
		
	}
	
	public void setDialRange(boolean autoscaleMinimum, float manualMinimum, boolean autoscaleMaximum, float manualMaximum) {
		
		autoscaleMin = autoscaleMinimum;
		autoscaleMax = autoscaleMaximum;
		manualMin = manualMinimum;
		manualMax = manualMaximum;
		
	}
	
	@Override public void drawChart(GL2 gl, int width, int height, int lastSampleNumber, double zoomLevel) {
		
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
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (duration * zoomLevel) + 1;
		int minDomain = OpenGLDialChart.getFactory().getMinimumSampleCount() - 1;
		if(endIndex - startIndex < minDomain) startIndex = endIndex - minDomain;
		if(startIndex < 0) startIndex = 0;
		
		if(endIndex - startIndex < minDomain)
			return;
		
		datasets[0].getSamples(startIndex, endIndex, samples);

		// calculate range
		float dialMin = autoscaleMin ? samples.min : manualMin;
		float dialMax = autoscaleMax ? samples.max : manualMax;
		float range = dialMax - dialMin;
		
		// generate text
		float reading      = samples.buffer[samples.buffer.length - 1];
		String minText     = ChartUtils.formattedNumber(dialMin, 6);
		String maxText     = ChartUtils.formattedNumber(dialMax, 6);
		String readingText = ChartUtils.formattedNumber(reading, 6) + " " + datasets[0].unit;
		String titleText   = datasets[0].name;
		
		// calculate x and y positions of everything except the statistics
		float xPlotLeft = Theme.tilePadding;
		float xPlotRight = width - Theme.tilePadding;
		float plotWidth = xPlotRight - xPlotLeft;

		float yStatsTextBaseline = height - Theme.tilePadding;
		if(showStatistics)
			yStatsTextBaseline -= FontUtils.tickTextHeight;
		float yMinMaxTextBaseline = Theme.tilePadding;
		float yDialBottom = yMinMaxTextBaseline + FontUtils.tickTextHeight + Theme.tickTextPadding;
		
		float xCircleCenter = plotWidth / 2f + Theme.tilePadding;
		float yCircleCenter = yDialBottom;
		float circleRadius = Float.min(yStatsTextBaseline - Theme.tickTextPadding - yCircleCenter, plotWidth / 2f);
		if(circleRadius < 0)
			return;
		
		float xMinXtextLeft = xCircleCenter - circleRadius;
		float xMaxXtextLeft = xCircleCenter + circleRadius - FontUtils.tickTextWidth(maxText);
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
		FontUtils.drawXaxisText(titleText,   (int) xTitleLeft,       (int) yTitleBaseline);
		FontUtils.drawXaxisText(readingText, (int) xReadingTextLeft, (int) yMinMaxTextBaseline);
		
		// draw the statistics if enabled
		if(showStatistics) {
			
			double[] doubles = new double[samples.buffer.length];
			for(int i = 0; i < samples.buffer.length; i++)
				doubles[i] = (double) samples.buffer[i];
			DescriptiveStatistics stats = new DescriptiveStatistics(doubles);
			
			String meanText    = "Mean: " +    ChartUtils.formattedNumber(stats.getMean(), 6);
			String stdDevText  = "Std Dev: " + ChartUtils.formattedNumber(stats.getStandardDeviation(), 6);
			
			float xMeanTextLeft = xPlotLeft;
			float xStdDevTextLeft = xPlotRight - FontUtils.tickTextWidth(stdDevText);
			FontUtils.drawTickText(meanText,     (int) xMeanTextLeft,    (int) yStatsTextBaseline);
			FontUtils.drawTickText(stdDevText,   (int) xStdDevTextLeft,  (int) yStatsTextBaseline);
			
		}
		
	}

}
