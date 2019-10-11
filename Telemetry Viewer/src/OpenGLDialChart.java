import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import com.jogamp.opengl.GL2;

/**
 * Renders a dial showing the value of the most recent sample.
 * 
 * User settings:
 *     Dataset to visualize.
 *     Dial minimum value can be fixed or autoscaled.
 *     Dial maximum value can be fixed or autoscaled.
 *     Sample count (this is used for autoscaling and for statistics.)
 *     Current reading label can be displayed.
 *     Dataset label can be displayed.
 *     Dial minimum and maximum labels can be displayed.
 *     Statistics (mean and standard deviation) can be displayed.
 */
public class OpenGLDialChart extends PositionedChart {
	
	final int   dialResolution = 400; // how many quads to draw
	final float dialThickness = 0.4f; // percentage of the radius
	Samples     samples;
	boolean     autoscaleMin;
	boolean     autoscaleMax;
	float       manualMin;
	float       manualMax;
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
	// statistics
	boolean showStatistics;
	String meanText;
	String stdDevText;
	float statsTextWidth;
	float xMeanTextLeft;
	float xStdDevTextLeft;
	float yStatsTextBaseline;
	
	// min max labels
	boolean showMinMaxLabels;
	float yMinMaxLabelsBaseline;
	float yMinMaxLabelsTop;
	String minLabel;
	String maxLabel;
	float minLabelWidth;
	float maxLabelWidth;
	float xMinLabelLeft;
	float xMaxLabelLeft;
	
	// reading label
	boolean showReadingLabel;
	String readingLabel;
	float readingLabelWidth;
	float xReadingLabelLeft;
	float yReadingLabelBaseline;
	float yReadingLabelTop;
	float readingLabelRadius;
	
	// dataset label
	boolean showDatasetLabel;
	String datasetLabel;
	float datasetLabelWidth;
	float yDatasetLabelBaseline;
	float yDatasetLabelTop;
	float xDatasetLabelLeft;
	float datasetLabelRadius;
	
	// constraints
	static final float DialMinimumDefault = -1;
	static final float DialMaximumDefault =  1;
	static final float DialLowerLimit     = -Float.MAX_VALUE;
	static final float DialUpperLimit     =  Float.MAX_VALUE;
	
	static final int SampleCountDefault    = 1000;
	static final int SampleCountLowerLimit = 1;
	static final int SampleCountUpperLimit = Integer.MAX_VALUE;
	
	// control widgets
	WidgetDatasets datasetWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetTextfieldInteger sampleCountWidget;
	WidgetCheckbox showReadingLabelWidget;
	WidgetCheckbox showDatasetLabelWidget;
	WidgetCheckbox showMinMaxLabelsWidget;
	WidgetCheckbox showStatisticsWidget;
	
	@Override public String toString() {
		
		return "Dial Chart";
		
	}
	
	public OpenGLDialChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		samples = new Samples();
		
		datasetWidget = new WidgetDatasets(1,
		                                   new String[] {"Dataset"},
		                                   false,
		                                   newDataset -> datasets = newDataset);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Dial",
		                                                  DialMinimumDefault,
		                                                  DialMaximumDefault,
		                                                  DialLowerLimit,
		                                                  DialUpperLimit,
		                                                  (newAutoscaleMin, newManualMin) -> { autoscaleMin = newAutoscaleMin; manualMin = newManualMin; },
		                                                  (newAutoscaleMax, newManualMax) -> { autoscaleMax = newAutoscaleMax; manualMax = newManualMax; });
		
		sampleCountWidget = new WidgetTextfieldInteger("Sample Count",
		                                               SampleCountDefault,
		                                               SampleCountLowerLimit,
		                                               SampleCountUpperLimit,
		                                               newSampleCount -> sampleCount = newSampleCount);
		
		showReadingLabelWidget = new WidgetCheckbox("Show Reading Label",
		                                            true,
		                                            newShowReadingLabel -> showReadingLabel = newShowReadingLabel);
		
		showDatasetLabelWidget = new WidgetCheckbox("Show Dataset Label",
		                                            true,
		                                            newShowDatasetLabel -> showDatasetLabel = newShowDatasetLabel);
		
		showMinMaxLabelsWidget = new WidgetCheckbox("Show Min/Max Labels",
		                                            true,
		                                            newShowMinMaxLabels -> showMinMaxLabels = newShowMinMaxLabels);
		
		showStatisticsWidget = new WidgetCheckbox("Show Statistics",
		                                          true,
		                                          newShowStatistics -> showStatistics = newShowStatistics);

		widgets = new Widget[10];
		widgets[0] = datasetWidget;
		widgets[1] = null;
		widgets[2] = minMaxWidget;
		widgets[3] = null;
		widgets[4] = sampleCountWidget;
		widgets[5] = null;
		widgets[6] = showDatasetLabelWidget;
		widgets[7] = showReadingLabelWidget;
		widgets[8] = showMinMaxLabelsWidget;
		widgets[9] = showStatisticsWidget;
		
	}
	
	@Override public void drawChart(GL2 gl, float[] chartMatrix, int width, int height, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		// get the samples
		int endIndex = lastSampleNumber;
		int startIndex = endIndex - (int) (sampleCount * zoomLevel) + 1;
		int minDomain = SampleCountLowerLimit - 1;
		if(endIndex - startIndex < minDomain) startIndex = endIndex - minDomain;
		if(startIndex < 0) startIndex = 0;
		
		if(endIndex - startIndex < minDomain)
			return;
		
		datasets[0].getSamples(startIndex, endIndex, samples);
		float lastSample = samples.buffer[samples.buffer.length - 1];

		// calculate range
		float dialMin = autoscaleMin ? samples.min : manualMin;
		float dialMax = autoscaleMax ? samples.max : manualMax;
		float range = dialMax - dialMin;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;

		if(showStatistics) {
			double[] doubles = new double[samples.buffer.length];
			for(int i = 0; i < samples.buffer.length; i++)
				doubles[i] = (double) samples.buffer[i];
			DescriptiveStatistics stats = new DescriptiveStatistics(doubles);
			
			meanText    = "Mean: " +    ChartUtils.formattedNumber(stats.getMean(), 6);
			stdDevText  = "Std Dev: " + ChartUtils.formattedNumber(stats.getStandardDeviation(), 6);
			
			statsTextWidth = FontUtils.tickTextWidth(meanText) + Theme.tickTextPadding + FontUtils.tickTextWidth(stdDevText);
			xMeanTextLeft = xPlotLeft;
			xStdDevTextLeft = xPlotRight - FontUtils.tickTextWidth(stdDevText);
			yStatsTextBaseline = yPlotTop - FontUtils.tickTextHeight;
			
			if(statsTextWidth < plotWidth) {
				FontUtils.drawTickText(meanText,   (int) xMeanTextLeft,   (int) yStatsTextBaseline);
				FontUtils.drawTickText(stdDevText, (int) xStdDevTextLeft, (int) yStatsTextBaseline);
			}
			
			yPlotTop = yStatsTextBaseline - Theme.tickTextPadding;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		if(showMinMaxLabels) {
			yMinMaxLabelsBaseline = Theme.tilePadding;
			yMinMaxLabelsTop = yMinMaxLabelsBaseline + FontUtils.tickTextHeight;
			minLabel = ChartUtils.formattedNumber(dialMin, 6);
			maxLabel = ChartUtils.formattedNumber(dialMax, 6);
			minLabelWidth = FontUtils.tickTextWidth(minLabel);
			maxLabelWidth = FontUtils.tickTextWidth(maxLabel);
			
			yPlotBottom = yMinMaxLabelsTop + Theme.tickTextPadding;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		float xCircleCenter = plotWidth / 2 + Theme.tilePadding;
		float yCircleCenter = yPlotBottom;
		float circleOuterRadius = Float.min(plotHeight, plotWidth / 2);
		float circleInnerRadius = circleOuterRadius * (1 - dialThickness);
		
		// stop if the dial is too small
		if(circleOuterRadius < 0)
			return;
		
		if(showReadingLabel) {
			readingLabel = ChartUtils.formattedNumber(lastSample, 6) + " " + datasets[0].unit;
			readingLabelWidth = FontUtils.xAxisTextWidth(readingLabel);
			xReadingLabelLeft = xCircleCenter - (readingLabelWidth / 2);
			yReadingLabelBaseline = yPlotBottom;
			yReadingLabelTop = yReadingLabelBaseline + FontUtils.xAxisTextHeight;
			readingLabelRadius = (float) Math.sqrt((readingLabelWidth / 2) * (readingLabelWidth / 2) + (yReadingLabelTop - yCircleCenter) * (yReadingLabelTop - yCircleCenter));
			
			if(readingLabelRadius + Theme.tickTextPadding < circleInnerRadius)
				FontUtils.drawXaxisText(readingLabel, (int) xReadingLabelLeft, (int) yReadingLabelBaseline);
		}
		
		if(showMinMaxLabels) {
			xMinLabelLeft = xCircleCenter - circleOuterRadius;
			xMaxLabelLeft = xCircleCenter + circleOuterRadius - maxLabelWidth;
			
			if(xMinLabelLeft + minLabelWidth + Theme.tickTextPadding < xMaxLabelLeft - Theme.tickTextPadding) {
				FontUtils.drawTickText(minLabel, (int) xMinLabelLeft, (int) yMinMaxLabelsBaseline);
				FontUtils.drawTickText(maxLabel, (int) xMaxLabelLeft, (int) yMinMaxLabelsBaseline);
			}
		}
		
		if(showDatasetLabel) {
			datasetLabel = datasets[0].name;
			datasetLabelWidth = FontUtils.xAxisTextWidth(datasetLabel);
			yDatasetLabelBaseline = showReadingLabel ? yReadingLabelTop + Theme.tickTextPadding : yPlotBottom;
			yDatasetLabelTop = yDatasetLabelBaseline + FontUtils.xAxisTextHeight;
			xDatasetLabelLeft = xCircleCenter - (datasetLabelWidth / 2);
			datasetLabelRadius = (float) Math.sqrt((datasetLabelWidth / 2) * (datasetLabelWidth / 2) + (yDatasetLabelTop - yCircleCenter) * (yDatasetLabelTop - yCircleCenter));
			
			if(datasetLabelRadius + Theme.tickTextPadding < circleInnerRadius)
				FontUtils.drawXaxisText(datasetLabel, (int) xDatasetLabelLeft, (int) yDatasetLabelBaseline);
		}
		
		// draw the dial
		float dialPercentage = (lastSample - dialMin) / range;
		for(float angle = 0; angle < Math.PI; angle += Math.PI / dialResolution) {
			
			float x1 = -1f * circleOuterRadius *                       (float) Math.cos(angle)                            + xCircleCenter; // top-left
			float y1 =       circleOuterRadius *                       (float) Math.sin(angle)                            + yCircleCenter;
			float x2 = -1f * circleOuterRadius *                       (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter; // top-right
			float y2 =       circleOuterRadius *                       (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
			float x4 = -1f * circleOuterRadius * (1 - dialThickness) * (float) Math.cos(angle)                            + xCircleCenter; // bottom-left
			float y4 =       circleOuterRadius * (1 - dialThickness) * (float) Math.sin(angle)                            + yCircleCenter;
			float x3 = -1f * circleOuterRadius * (1 - dialThickness) * (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter; // bottom-right
			float y3 =       circleOuterRadius * (1 - dialThickness) * (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
			
			OpenGL.buffer.rewind();
			OpenGL.buffer.put(x1); OpenGL.buffer.put(y1);
			OpenGL.buffer.put(x4); OpenGL.buffer.put(y4);
			OpenGL.buffer.put(x2); OpenGL.buffer.put(y2);
			OpenGL.buffer.put(x3); OpenGL.buffer.put(y3);
			OpenGL.buffer.rewind();
			OpenGL.drawTriangleStrip2D(gl, angle > Math.PI * dialPercentage ? Theme.plotBackgroundColor : samples.color, OpenGL.buffer, 4);
			
		}
		
	}

}
