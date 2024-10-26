import java.nio.FloatBuffer;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders an acceleration chart showing the value of the most recent sample.
 * 
 * User settings:
 *     DatasetX to visualize.
 *     DatasetY to visualize.
 *     Chart minimum value can be fixed or autoscaled.
 *     Chart maximum value can be fixed or autoscaled.
 *     Sample count (this is used for autoscaling and for statistics.)
 *     Current reading label can be displayed.
 *     Dataset label can be displayed.
 *     Chart minimum and maximum labels can be displayed.
 */
public class OpenGLAccelerationChart extends PositionedChart {
	
	final int   dialResolution = 400; // how many quads to draw
	//final float dialThickness = 0.4f; // percentage of the radius
	//
	final float accGuideAngle = 7 * (float)Math.PI / 4; // put the acceleration labels at 235 degrees
	final float[] white = new float[] {1, 1, 1, 1};
	final float[] black = new float[] {0, 0, 0, 1};

	// the size of the little circle that moves in the acceleration chart
	final int CircleSizeDefault = 3;
	final int CircleSizeMin = 1;
	final int CircleSizeMax = 10;

	float       chartMin; // what the value of the dataset y sample is if the acceleration circle is at the left edge of the acceleration guide
	float       chartMax; // what the value of the dataset x sample is if the acceleration circle is at the right edge of the acceleration guide
	int circleSize = CircleSizeDefault; // user configurable acceleration circle size
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	
	// min max labels
	boolean showMinMaxLabels;
	
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
	
	// control widgets
	WidgetDatasets datasetWidgetX;
	WidgetDatasets datasetWidgetY;
	WidgetTextfieldsOptionalMinMax minMaxWidgetX;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetCheckbox showReadingLabelWidget;
	WidgetCheckbox showDatasetLabelWidget;
	WidgetCheckbox showMinMaxLabelsWidget;
	WidgetTextfieldInteger circleSizeWidget;

	// second dataset for the y axis
	DatasetsInterface datasets2 = new DatasetsInterface();
	
	@Override public String toString() {
		
		return "Acceleration";
		
	}
	
	public OpenGLAccelerationChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		datasetWidgetX = new WidgetDatasets(newDatasets -> datasets.setNormals(newDatasets),
		                                   null,
		                                   null,
		                                   null,
		                                   false,
		                                   new String[] {"DatasetX"});

		datasetWidgetY = new WidgetDatasets(newDatasets -> datasets2.setNormals(newDatasets),
		                                   null,
		                                   null,
		                                   null,
		                                   false,
		                                   new String[] {"DatasetY"});
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Axis",
		                                                  false,
		                                                  -4,
		                                                  4,
		                                                  -Float.MAX_VALUE,
		                                                  Float.MAX_VALUE,
		                                                  (newAutoscaleMin, newManualMin) -> chartMin = newManualMin,
		                                                  (newAutoscaleMax, newManualMax) -> chartMax = newManualMax);

		circleSizeWidget = new WidgetTextfieldInteger("Acceleration Circle Size",
		                                            CircleSizeDefault,
		                                            CircleSizeMin,
		                                            CircleSizeMax,
		                                            newSize -> {
																									circleSize = newSize;
		                                            });
		
		showReadingLabelWidget = new WidgetCheckbox("Show Reading Label",
		                                            true,
		                                            newShowReadingLabel -> showReadingLabel = newShowReadingLabel);
		
		showDatasetLabelWidget = new WidgetCheckbox("Show Dataset Label",
		                                            true,
		                                            newShowDatasetLabel -> showDatasetLabel = newShowDatasetLabel);
		
		showMinMaxLabelsWidget = new WidgetCheckbox("Show Min/Max Labels",
		                                            true,
		                                            newShowMinMaxLabels -> showMinMaxLabels = newShowMinMaxLabels);

		widgets = new Widget[9];
		widgets[0] = datasetWidgetX;
		widgets[1] = datasetWidgetY;
		widgets[2] = null;
		widgets[3] = minMaxWidget;
		widgets[4] = null;
		widgets[5] = showDatasetLabelWidget;
		widgets[6] = showReadingLabelWidget;
		widgets[7] = showMinMaxLabelsWidget;
		widgets[8] = circleSizeWidget;
		
	}

	/**
	 * helper function to draw the acceleration guideline labels
	 * they will be displayed next to each guide subdivision
	 *
	 * @param gl             The OpenGL context.
	 * @param xCenter        The acceleration guide circles's center x value
	 * @param yCenter        The acceleration guide circles's center x value
	 * @param value          The value to display in the label
	 * @param radius         The radius at which you want to display the label, should match the acc guide subdivision's radius
	 */
	private void drawAccGuideLabel(GL2ES3 gl, float xCenter, float yCenter, double value, float radius) {
		String label = ChartUtils.formattedNumber(value, 2);
		float x = radius * (float) Math.cos(accGuideAngle) + xCenter;
		float y = radius * (float) Math.sin(accGuideAngle) + yCenter;
		float labelRadius = OpenGL.smallTextWidth(gl, label) / 3;
		float xLabelLeft = x + labelRadius * (float) Math.cos(accGuideAngle);
		float yLabelTop = y + labelRadius * (float) Math.sin(accGuideAngle);
		OpenGL.drawSmallText(gl, label, (int) xLabelLeft, (int) yLabelTop, 0);
	}

	/**
	 * helper function to draw the acceleration guideline circle lines
	 *
	 * @param gl             The OpenGL context.
	 * @param xCenter        The acceleration guide circles's center x value
	 * @param yCenter        The acceleration guide circles's center x value
	 * @param radius         The radius of the acceleration guide circle
	 * @param buffer         The buffer to use when storing the vertices
	 */
	private void drawAccelerationGuide(GL2ES3 gl, float xCenter, float yCenter, float radius, FloatBuffer buffer ) {
		buffer.rewind();
		int vertexCount = 0;

		for (float angle = 0; angle < 2 * Math.PI; angle += Math.PI / dialResolution) {
			float x = radius * (float) Math.cos(angle) + xCenter;
			float y = radius * (float) Math.sin(angle) + yCenter;
			buffer.put(x).put(y);
			vertexCount++;
		}

		buffer.rewind();
		OpenGL.drawLinesXy(gl, GL3.GL_LINE_LOOP, black, buffer, vertexCount);
	}

	/**
	 * helper function to draw a filled circle
	 *
	 * @param gl             The OpenGL context.
	 * @param buffer         The buffer to use when storing the vertices
	 * @param xCenter        The acceleration guide circles's center x value
	 * @param yCenter        The acceleration guide circles's center x value
	 * @param radius         The radius of the acceleration guide circle
	 * @param resolution     How many straight lines should the circle be made up of, higher means a smoother circle
	 * @param color          The color, as a float[] {r,g,b,a}.
	 */
	public static void drawFilledCircle(GL2ES3 gl, FloatBuffer buffer, float xCenter, float yCenter, float radius, int resolution, float[] color) {
		buffer.rewind();
		int vertexCount = 0;

		// Put the center
		buffer.put(xCenter).put(yCenter);
		vertexCount++;

		for (float angle = 0; angle < 2 * Math.PI; angle += Math.PI / resolution) {
			float x1 = radius * (float) Math.cos(angle) + xCenter;
			float y1 = radius * (float) Math.sin(angle) + yCenter;
			float x2 = radius * (float) Math.cos(angle + Math.PI / resolution) + xCenter;
			float y2 = radius * (float) Math.sin(angle + Math.PI / resolution) + yCenter;

			buffer.put(x1).put(y1);
			vertexCount++;
			buffer.put(x2).put(y2);
			vertexCount++;
		}

		buffer.rewind();
		OpenGL.drawTrianglesXY(gl, GL3.GL_TRIANGLE_FAN, color, buffer, vertexCount);
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long endTimestamp, int endSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		// sanity check
		if(datasets.normalsCount() != 1)
			return handler;
		
		// get the sample
		int lastSampleNumber = endSampleNumber;
		int trueLastSampleNumber = datasets.connection.getSampleCount() - 1;
		if(lastSampleNumber > trueLastSampleNumber)
			lastSampleNumber = trueLastSampleNumber;
		Dataset dataset = datasets.getNormal(0);
		Dataset datasetY = datasets2.getNormal(0);
		float sampleX = lastSampleNumber > 0 ? datasets.getSample(dataset, lastSampleNumber) : 0;
		float sampleY = lastSampleNumber > 0 ? datasets2.getSample(datasetY, lastSampleNumber) : 0;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;
		

		// used to scale the graphics in the center of the plot to ensure white spaces is around them
		float minPlotLength = Float.min(plotHeight, plotWidth);
		
		float accelerationGuideRadius = minPlotLength/3;								// the biggest outer guide
		float accelerationGuideRadius2 = 2*(accelerationGuideRadius/3); // the medium guide
		float accelerationGuideRadius3 = accelerationGuideRadius/3;			// the smallest guide

		float xCenter = plotWidth / 2 + Theme.tilePadding;
		float yCenter = plotHeight / 2 + Theme.tilePadding;
		float xNormalized = (sampleX - chartMin) / (chartMax - chartMin) * 2 - 1;
		float yNormalized = (sampleY - chartMin) / (chartMax - chartMin) * 2 - 1;
		float xCircleCenter = xCenter + xNormalized * accelerationGuideRadius + Theme.tilePadding;
		float yCircleCenter = yCenter + yNormalized * accelerationGuideRadius + Theme.tilePadding;

		float circleOuterRadius = minPlotLength * ((float)circleSize/100);

		// stop if the dial is too small
		if(circleOuterRadius < 0)
			return handler;
		

		// draw white inner circle
		drawFilledCircle(gl, OpenGL.buffer, xCenter, yCenter, accelerationGuideRadius, dialResolution, white);

		// show the readings label if checked
		if(showReadingLabel && lastSampleNumber >= 0) {
			double magnitude = Math.sqrt(((sampleX*sampleX) + (sampleY*sampleY)));
			readingLabel = ChartUtils.formattedNumber(magnitude, 6) + " " + dataset.unit;
			readingLabelWidth = OpenGL.largeTextWidth(gl, readingLabel);
			xReadingLabelLeft = xCenter - (readingLabelWidth / 2);
			yReadingLabelBaseline = yPlotBottom;
			yReadingLabelTop = yReadingLabelBaseline + OpenGL.largeTextHeight;
			readingLabelRadius = Math.abs(yReadingLabelTop - yCenter);
			
			// make sure the text tops are below the acceleration guide's bottom edge
			if(readingLabelRadius + Theme.tickTextPadding > accelerationGuideRadius)
				OpenGL.drawLargeText(gl, readingLabel, (int) xReadingLabelLeft, (int) yReadingLabelBaseline, 0);
		}
		
		// show the max number of acceleration on the accelerationGuide circle
		if(showMinMaxLabels) {
			drawAccGuideLabel(gl, xCenter, yCenter, chartMax, accelerationGuideRadius);
			drawAccGuideLabel(gl, xCenter, yCenter, 2*(chartMax/3), accelerationGuideRadius2);
			drawAccGuideLabel(gl, xCenter, yCenter, chartMax/3, accelerationGuideRadius3);
		}
		
		// show the dataset label if checked
		if(showDatasetLabel && lastSampleNumber >= 0) {
			datasetLabel = dataset.name + " and " + datasetY.name;
			datasetLabelWidth = OpenGL.largeTextWidth(gl, datasetLabel);
			yDatasetLabelBaseline = showReadingLabel ? yReadingLabelTop + Theme.tickTextPadding + Theme.legendTextPadding : yPlotBottom;
			yDatasetLabelTop = yDatasetLabelBaseline + OpenGL.largeTextHeight;
			xDatasetLabelLeft = xCenter - (datasetLabelWidth / 2);
			datasetLabelRadius = Math.abs(yDatasetLabelTop - yCenter);

			// make sure the text tops are below the acceleration guide's bottom edge
			if(datasetLabelRadius + Theme.tickTextPadding > accelerationGuideRadius) {
				float xMouseoverLeft = xDatasetLabelLeft - Theme.legendTextPadding;
				float xMouseoverRight = xDatasetLabelLeft + datasetLabelWidth + Theme.legendTextPadding;
				float yMouseoverBottom = yDatasetLabelBaseline - Theme.legendTextPadding;
				float yMouseoverTop = yDatasetLabelTop + Theme.legendTextPadding;
				if(mouseX >= xMouseoverLeft && mouseX <= xMouseoverRight && mouseY >= yMouseoverBottom && mouseY <= yMouseoverTop) {
					// not really sure what to do with this because technically we are using 2 datasets, so I guess we'll just show the first dataset
					OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xMouseoverLeft, yMouseoverBottom, xMouseoverRight, yMouseoverTop);
					OpenGL.drawQuadOutline2D(gl, Theme.tickLinesColor, xMouseoverLeft, yMouseoverBottom, xMouseoverRight, yMouseoverTop);
					handler = EventHandler.onPress(event -> ConfigureView.instance.forDataset(dataset));
				}
				OpenGL.drawLargeText(gl, datasetLabel, (int) xDatasetLabelLeft, (int) yDatasetLabelBaseline, 0);
			}
		}

		// draw the outside acceleration guide
		drawAccelerationGuide(gl, xCenter, yCenter, accelerationGuideRadius ,OpenGL.buffer);
		drawAccelerationGuide(gl, xCenter, yCenter, accelerationGuideRadius2 ,OpenGL.buffer);
		drawAccelerationGuide(gl, xCenter, yCenter, accelerationGuideRadius3 ,OpenGL.buffer);

		// draw the acceleration cross
		// first the horizontal
		OpenGL.buffer.rewind();
		float cx1 = xCenter - accelerationGuideRadius;
		float cy1 = yCenter;
		float cx2 = xCenter + accelerationGuideRadius;
		OpenGL.buffer.put(cx1); OpenGL.buffer.put(cy1);
		OpenGL.buffer.put(cx2); OpenGL.buffer.put(cy1);
		// now the vertical
		cx1 = xCenter;
		cy1 = yCenter - accelerationGuideRadius;
		float cy2 = yCenter + accelerationGuideRadius;
		OpenGL.buffer.put(cx1); OpenGL.buffer.put(cy1);
		OpenGL.buffer.put(cx1); OpenGL.buffer.put(cy2);
		OpenGL.buffer.rewind();
		OpenGL.drawLinesXy(gl, GL3.GL_LINES, black, OpenGL.buffer, 4);
		

		// draw the acceleration circle
		drawFilledCircle(gl, OpenGL.buffer, xCircleCenter, yCircleCenter, circleOuterRadius, dialResolution, dataset.glColor);
		
		return handler;
		
	}

}
