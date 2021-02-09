import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

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
	float       dialMin;
	float       dialMax;
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
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
	WidgetCheckbox showReadingLabelWidget;
	WidgetCheckbox showDatasetLabelWidget;
	WidgetCheckbox showMinMaxLabelsWidget;
	
	@Override public String toString() {
		
		return "Dial";
		
	}
	
	public OpenGLDialChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		datasetWidget = new WidgetDatasets(1,
		                                   new String[] {"Dataset"},
		                                   newDataset -> datasets = newDataset);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Dial",
		                                                  false,
		                                                  DialMinimumDefault,
		                                                  DialMaximumDefault,
		                                                  DialLowerLimit,
		                                                  DialUpperLimit,
		                                                  (newAutoscaleMin, newManualMin) -> dialMin = newManualMin,
		                                                  (newAutoscaleMax, newManualMax) -> dialMax = newManualMax);
		
		showReadingLabelWidget = new WidgetCheckbox("Show Reading Label",
		                                            true,
		                                            newShowReadingLabel -> showReadingLabel = newShowReadingLabel);
		
		showDatasetLabelWidget = new WidgetCheckbox("Show Dataset Label",
		                                            true,
		                                            newShowDatasetLabel -> showDatasetLabel = newShowDatasetLabel);
		
		showMinMaxLabelsWidget = new WidgetCheckbox("Show Min/Max Labels",
		                                            true,
		                                            newShowMinMaxLabels -> showMinMaxLabels = newShowMinMaxLabels);

		widgets = new Widget[7];
		widgets[0] = datasetWidget;
		widgets[1] = null;
		widgets[2] = minMaxWidget;
		widgets[3] = null;
		widgets[4] = showDatasetLabelWidget;
		widgets[5] = showReadingLabelWidget;
		widgets[6] = showMinMaxLabelsWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long endTimestamp, int endSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		// get the sample
		int lastSampleNumber = endSampleNumber;
		int trueLastSampleNumber = datasets.get(0).connection.getSampleCount() - 1;
		if(lastSampleNumber > trueLastSampleNumber)
			lastSampleNumber = trueLastSampleNumber;
		float sample = lastSampleNumber > 0 ? datasets.get(0).getSample(lastSampleNumber) : 0;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;
		
		if(showMinMaxLabels) {
			yMinMaxLabelsBaseline = Theme.tilePadding;
			yMinMaxLabelsTop = yMinMaxLabelsBaseline + OpenGL.smallTextHeight;
			minLabel = ChartUtils.formattedNumber(dialMin, 6);
			maxLabel = ChartUtils.formattedNumber(dialMax, 6);
			minLabelWidth = OpenGL.smallTextWidth(gl, minLabel);
			maxLabelWidth = OpenGL.smallTextWidth(gl, maxLabel);
			
			yPlotBottom = yMinMaxLabelsTop + Theme.tickTextPadding;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		float xCircleCenter = plotWidth / 2 + Theme.tilePadding;
		float yCircleCenter = yPlotBottom;
		float circleOuterRadius = Float.min(plotHeight, plotWidth / 2);
		float circleInnerRadius = circleOuterRadius * (1 - dialThickness);
		
		// stop if the dial is too small
		if(circleOuterRadius < 0)
			return handler;
		
		if(showReadingLabel && lastSampleNumber >= 0) {
			readingLabel = ChartUtils.formattedNumber(sample, 6) + " " + datasets.get(0).unit;
			readingLabelWidth = OpenGL.largeTextWidth(gl, readingLabel);
			xReadingLabelLeft = xCircleCenter - (readingLabelWidth / 2);
			yReadingLabelBaseline = yPlotBottom;
			yReadingLabelTop = yReadingLabelBaseline + OpenGL.largeTextHeight;
			readingLabelRadius = (float) Math.sqrt((readingLabelWidth / 2) * (readingLabelWidth / 2) + (yReadingLabelTop - yCircleCenter) * (yReadingLabelTop - yCircleCenter));
			
			if(readingLabelRadius + Theme.tickTextPadding < circleInnerRadius)
				OpenGL.drawLargeText(gl, readingLabel, (int) xReadingLabelLeft, (int) yReadingLabelBaseline, 0);
		}
		
		if(showMinMaxLabels && lastSampleNumber >= 0) {
			xMinLabelLeft = xCircleCenter - circleOuterRadius;
			xMaxLabelLeft = xCircleCenter + circleOuterRadius - maxLabelWidth;
			
			if(xMinLabelLeft + minLabelWidth + Theme.tickTextPadding < xMaxLabelLeft - Theme.tickTextPadding) {
				OpenGL.drawSmallText(gl, minLabel, (int) xMinLabelLeft, (int) yMinMaxLabelsBaseline, 0);
				OpenGL.drawSmallText(gl, maxLabel, (int) xMaxLabelLeft, (int) yMinMaxLabelsBaseline, 0);
			}
		}
		
		if(showDatasetLabel && lastSampleNumber >= 0) {
			datasetLabel = datasets.get(0).name;
			datasetLabelWidth = OpenGL.largeTextWidth(gl, datasetLabel);
			yDatasetLabelBaseline = showReadingLabel ? yReadingLabelTop + Theme.tickTextPadding + Theme.legendTextPadding : yPlotBottom;
			yDatasetLabelTop = yDatasetLabelBaseline + OpenGL.largeTextHeight;
			xDatasetLabelLeft = xCircleCenter - (datasetLabelWidth / 2);
			datasetLabelRadius = (float) Math.sqrt((datasetLabelWidth / 2) * (datasetLabelWidth / 2) + (yDatasetLabelTop - yCircleCenter) * (yDatasetLabelTop - yCircleCenter)) + Theme.legendTextPadding;
			
			if(datasetLabelRadius + Theme.tickTextPadding < circleInnerRadius) {
				float xMouseoverLeft = xDatasetLabelLeft - Theme.legendTextPadding;
				float xMouseoverRight = xDatasetLabelLeft + datasetLabelWidth + Theme.legendTextPadding;
				float yMouseoverBottom = yDatasetLabelBaseline - Theme.legendTextPadding;
				float yMouseoverTop = yDatasetLabelTop + Theme.legendTextPadding;
				if(mouseX >= xMouseoverLeft && mouseX <= xMouseoverRight && mouseY >= yMouseoverBottom && mouseY <= yMouseoverTop) {
					OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xMouseoverLeft, yMouseoverBottom, xMouseoverRight, yMouseoverTop);
					OpenGL.drawQuadOutline2D(gl, Theme.tickLinesColor, xMouseoverLeft, yMouseoverBottom, xMouseoverRight, yMouseoverTop);
					handler = EventHandler.onPress(event -> ConfigureView.instance.forDataset(datasets.get(0)));
				}
				OpenGL.drawLargeText(gl, datasetLabel, (int) xDatasetLabelLeft, (int) yDatasetLabelBaseline, 0);
			}
		}
		
		// draw the dial
		float dialPercentage = (sample - dialMin) / (dialMax - dialMin);
		OpenGL.buffer.rewind();
		for(float angle = 0; angle < Math.PI; angle += Math.PI / dialResolution) {
			
			float x1 = -1f * circleOuterRadius *                       (float) Math.cos(angle)                            + xCircleCenter; // top-left
			float y1 =       circleOuterRadius *                       (float) Math.sin(angle)                            + yCircleCenter;
			float x2 = -1f * circleOuterRadius *                       (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter; // top-right
			float y2 =       circleOuterRadius *                       (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
			float x4 = -1f * circleOuterRadius * (1 - dialThickness) * (float) Math.cos(angle)                            + xCircleCenter; // bottom-left
			float y4 =       circleOuterRadius * (1 - dialThickness) * (float) Math.sin(angle)                            + yCircleCenter;
			float x3 = -1f * circleOuterRadius * (1 - dialThickness) * (float) Math.cos(angle + Math.PI / dialResolution) + xCircleCenter; // bottom-right
			float y3 =       circleOuterRadius * (1 - dialThickness) * (float) Math.sin(angle + Math.PI / dialResolution) + yCircleCenter;
			
			float[] color = angle >= Math.PI * dialPercentage ? Theme.plotBackgroundColor : datasets.get(0).glColor;
			OpenGL.buffer.put(x1); OpenGL.buffer.put(y1); OpenGL.buffer.put(color);
			OpenGL.buffer.put(x2); OpenGL.buffer.put(y2); OpenGL.buffer.put(color);
			OpenGL.buffer.put(x4); OpenGL.buffer.put(y4); OpenGL.buffer.put(color);
			
			OpenGL.buffer.put(x4); OpenGL.buffer.put(y4); OpenGL.buffer.put(color);
			OpenGL.buffer.put(x2); OpenGL.buffer.put(y2); OpenGL.buffer.put(color);
			OpenGL.buffer.put(x3); OpenGL.buffer.put(y3); OpenGL.buffer.put(color);
			
		}
		OpenGL.buffer.rewind();
		OpenGL.drawTrianglesXYRGBA(gl, GL3.GL_TRIANGLES, OpenGL.buffer, 6 * dialResolution);
		
		return handler;
		
	}

}
