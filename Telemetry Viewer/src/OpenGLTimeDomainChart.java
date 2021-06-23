import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders a time-domain line chart.
 * 
 * User settings:
 *     Datasets to visualize.
 *     Sample count.
 *     Y-axis minimum value can be fixed or autoscaled.
 *     Y-axis maximum value can be fixed or autoscaled.
 *     X-axis title can be displayed.
 *     X-axis scale can be displayed.
 *     Y-axis title can be displayed.
 *     Y-axis scale can be displayed.
 *     Legend can be displayed.
 */
public class OpenGLTimeDomainChart extends PositionedChart {
	
	// plot region
	float xPlotLeft;
	float xPlotRight;
	float plotWidth;
	float yPlotTop;
	float yPlotBottom;
	float plotHeight;
	float plotMaxY;
	float plotMinY;
	float plotRange;
	
	// x-axis title
	boolean showXaxisTitle;
	float yXaxisTitleTextBasline;
	float yXaxisTitleTextTop;
	String xAxisTitle;
	float xXaxisTitleTextLeft;
	
	// legend
	boolean showLegend;
	float xLegendBorderLeft;
	float yLegendBorderBottom;
	float yLegendTextBaseline;
	float yLegendTextTop;
	float yLegendBorderTop;
	float[][] legendMouseoverCoordinates;
	float[][] legendBoxCoordinates;
	float[] xLegendNameLeft;
	float xLegendBorderRight;
	
	// x-axis scale
	boolean showXaxisScale;
	float yXaxisTickTextBaseline;
	float yXaxisTickTextTop;
	float yXaxisTickBottom;
	float yXaxisTickTop;
	boolean isTimestampsMode;
	
	// y-axis title
	boolean showYaxisTitle;
	float xYaxisTitleTextTop;
	float xYaxisTitleTextBaseline;
	String yAxisTitle;
	float yYaxisTitleTextLeft;
	
	// y-axis scale
	boolean showYaxisScale;
	Map<Float, String> yDivisions;
	float xYaxisTickTextRight;
	float xYaxisTickLeft;
	float xYaxisTickRight;
	AutoScale autoscale;
	boolean autoscaleYmin;
	boolean autoscaleYmax;
	float manualYmin;
	float manualYmax;
	
	Plot plot;
	boolean cachedMode;
	List<Dataset> allDatasets; // normal and bitfields

	static final float yAxisMinimumDefault = -1.0f;
	static final float yAxisMaximumDefault =  1.0f;
	static final float yAxisLowerLimit     = -Float.MAX_VALUE;
	static final float yAxisUpperLimit     =  Float.MAX_VALUE;
	
	// trigger
	boolean triggerEnabled = false;
	boolean triggeringPaused = false;
	int earlierEndSampleNumber = -1;
	long earlierEndTimestamp = -1;
	float earlierPlotMaxY = 1;
	float earlierPlotMinY = -1;
	
	// control widgets
	WidgetDatasets datasetsAndDurationWidget;
	WidgetTextfieldsOptionalMinMax minMaxWidget;
	WidgetCheckbox showXaxisTitleWidget;
	WidgetCheckbox showXaxisScaleWidget;
	WidgetCheckbox showYaxisTitleWidget;
	WidgetCheckbox showYaxisScaleWidget;
	WidgetCheckbox showLegendWidget;
	WidgetCheckbox cachedWidget;
	WidgetTrigger triggerWidget;
	
	@Override public String toString() {
		
		return "Time Domain";
		
	}
	
	/**
	 * Updates the List of bitfield datasets, which is used by the legend and tooltip code.
	 */
	private void updateAllDatasetsList() {
		
		allDatasets = new ArrayList<Dataset>(datasets);
		
		if(bitfieldEdges != null)
			for(Dataset.Bitfield.State state : bitfieldEdges)
				if(!allDatasets.contains(state.dataset))
					allDatasets.add(state.dataset);
		
		if(bitfieldLevels != null)
			for(Dataset.Bitfield.State state : bitfieldLevels)
				if(!allDatasets.contains(state.dataset))
					allDatasets.add(state.dataset);
		
	}
	
	public OpenGLTimeDomainChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		autoscale = new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.10f);
		
		// create the control widgets and event handlers
		datasetsAndDurationWidget = new WidgetDatasets(newDatasets -> {
		                                                   boolean previouslyNoDatasets = datasets.isEmpty();
		                                                   datasets = newDatasets;
		                                                   updateAllDatasetsList();
		                                                   if(previouslyNoDatasets && triggerWidget != null)
		                                                	   triggerWidget.setDefaultChannel(datasets.get(0));
		                                               },
		                                               newBitfieldEdges -> {
		                                                   bitfieldEdges = newBitfieldEdges;
		                                                   updateAllDatasetsList();
		                                               },
		                                               newBitfieldLevels -> {
		                                                   bitfieldLevels = newBitfieldLevels;
		                                                   updateAllDatasetsList();
		                                               },
		                                               (newDurationType, newDuration) -> {
		                                                   sampleCountMode  = newDurationType == WidgetDatasets.AxisType.SAMPLE_COUNT;
		                                                   isTimestampsMode = newDurationType == WidgetDatasets.AxisType.TIMESTAMPS;
		                                                   duration = (int) (long) newDuration;
		                                                   plot = sampleCountMode ? new PlotSampleCount() : new PlotMilliseconds();
		                                                   if(triggerWidget != null)
		                                                       triggerWidget.resetTrigger(true);
		                                                   return newDuration;
		                                               },
		                                               true,
		                                               null);
		
		minMaxWidget = new WidgetTextfieldsOptionalMinMax("Y-Axis",
		                                                  true,
		                                                  yAxisMinimumDefault,
		                                                  yAxisMaximumDefault,
		                                                  yAxisLowerLimit,
		                                                  yAxisUpperLimit,
		                                                  (newAutoscaleYmin, newManualYmin) -> { autoscaleYmin = newAutoscaleYmin; manualYmin = newManualYmin; },
		                                                  (newAutoscaleYmax, newManualYmax) -> { autoscaleYmax = newAutoscaleYmax; manualYmax = newManualYmax; });
		
		showXaxisTitleWidget = new WidgetCheckbox("Show X-Axis Title",
		                                          true,
		                                          newShowXaxisTitle -> showXaxisTitle = newShowXaxisTitle);
		
		showXaxisScaleWidget = new WidgetCheckbox("Show X-Axis Scale",
		                                          true,
		                                          newShowXaxisScale -> showXaxisScale = newShowXaxisScale);
		
		showYaxisTitleWidget = new WidgetCheckbox("Show Y-Axis Title",
		                                          true,
		                                          newShowYaxisTitle -> showYaxisTitle = newShowYaxisTitle);
		
		showYaxisScaleWidget = new WidgetCheckbox("Show Y-Axis Scale",
		                                          true,
		                                          newShowYaxisScale -> showYaxisScale = newShowYaxisScale);
		
		showLegendWidget = new WidgetCheckbox("Show Legend",
		                                      true,
		                                      newShowLegend -> showLegend = newShowLegend);
		
		cachedWidget = new WidgetCheckbox("Cached Mode",
		                                  false,
		                                  newCachedMode -> {
		                                      cachedMode = newCachedMode;
		                                      autoscale = cachedMode ? new AutoScale(AutoScale.MODE_STICKY,       1, 0.10f) :
		                                                               new AutoScale(AutoScale.MODE_EXPONENTIAL, 30, 0.10f);
		                                  });
		
		triggerWidget = new WidgetTrigger(this,
		                                  isEnabled -> triggerEnabled = isEnabled);

		widgets = new Widget[15];
		
		widgets[0]  = datasetsAndDurationWidget;
		widgets[1]  = null;
		widgets[2]  = minMaxWidget;
		widgets[3]  = null;
		widgets[4]  = showXaxisTitleWidget;
		widgets[5]  = showXaxisScaleWidget;
		widgets[6]  = null;
		widgets[7]  = showYaxisTitleWidget;
		widgets[8]  = showYaxisScaleWidget;
		widgets[9]  = null;
		widgets[10] = showLegendWidget;
		widgets[11] = null;
		widgets[12] = cachedWidget;
		widgets[13] = null;
		widgets[14] = triggerWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long endTimestamp, int endSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;
		
		// trigger logic
		if(triggerEnabled && !datasets.isEmpty()) {
			if(sampleCountMode && triggeringPaused) {
				endSampleNumber = triggerWidget.checkForTriggerSampleCountMode(earlierEndSampleNumber, zoomLevel, true);
			} else if(sampleCountMode && !triggeringPaused) {
				if(!OpenGLChartsView.instance.isPausedView())
					endSampleNumber = datasets.get(0).connection.getSampleCount() - 1;
				endSampleNumber = triggerWidget.checkForTriggerSampleCountMode(endSampleNumber, zoomLevel, false);
				earlierEndSampleNumber = endSampleNumber;
			} else if(!sampleCountMode && triggeringPaused) {
				endTimestamp = triggerWidget.checkForTriggerMillisecondsMode(earlierEndTimestamp, zoomLevel, true);
			} else {
				if(!OpenGLChartsView.instance.isPausedView())
					endTimestamp = datasets.get(0).connection.getTimestamp(datasets.get(0).connection.getSampleCount() - 1);
				endTimestamp = triggerWidget.checkForTriggerMillisecondsMode(endTimestamp, zoomLevel, false);
				earlierEndTimestamp = endTimestamp;
			}
		}
		
		boolean haveDatasets = allDatasets != null && !allDatasets.isEmpty();
		int datasetsCount = haveDatasets ? allDatasets.size() : 0;
		
		plot.initialize(endTimestamp, endSampleNumber, zoomLevel, datasets, bitfieldEdges, bitfieldLevels, duration, cachedMode, isTimestampsMode);
		
		// calculate the plot range
		StorageFloats.MinMax requiredRange = plot.getRange();
		autoscale.update(requiredRange.min, requiredRange.max);
		plotMaxY = autoscaleYmax ? autoscale.getMax() : manualYmax;
		plotMinY = autoscaleYmin ? autoscale.getMin() : manualYmin;
		if(triggerEnabled) {
			if(triggeringPaused) {
				plotMaxY = earlierPlotMaxY;
				plotMinY = earlierPlotMinY;
			} else {
				earlierPlotMaxY = plotMaxY;
				earlierPlotMinY = plotMinY;
			}
		}
		plotRange = plotMaxY - plotMinY;
		
		// calculate x and y positions of everything
		xPlotLeft = Theme.tilePadding;
		xPlotRight = width - Theme.tilePadding;
		plotWidth = xPlotRight - xPlotLeft;
		yPlotTop = height - Theme.tilePadding;
		yPlotBottom = Theme.tilePadding;
		plotHeight = yPlotTop - yPlotBottom;
		
		if(showXaxisTitle) {
			yXaxisTitleTextBasline = Theme.tilePadding;
			yXaxisTitleTextTop = yXaxisTitleTextBasline + OpenGL.largeTextHeight;
			xAxisTitle = plot.getTitle();
			xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			float temp = yXaxisTitleTextTop + Theme.tickTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showLegend && haveDatasets) {
			xLegendBorderLeft = Theme.tilePadding;
			yLegendBorderBottom = Theme.tilePadding;
			yLegendTextBaseline = yLegendBorderBottom + Theme.legendTextPadding;
			yLegendTextTop = yLegendTextBaseline + OpenGL.mediumTextHeight;
			yLegendBorderTop = yLegendTextTop + Theme.legendTextPadding;
			
			legendMouseoverCoordinates = new float[datasetsCount][4];
			legendBoxCoordinates = new float[datasetsCount][4];
			xLegendNameLeft = new float[datasetsCount];
			
			float xOffset = xLegendBorderLeft + (Theme.lineWidth / 2) + Theme.legendTextPadding;
			
			for(int i = 0; i < datasetsCount; i++) {
				legendMouseoverCoordinates[i][0] = xOffset - Theme.legendTextPadding;
				legendMouseoverCoordinates[i][1] = yLegendBorderBottom;
				
				legendBoxCoordinates[i][0] = xOffset;
				legendBoxCoordinates[i][1] = yLegendTextBaseline;
				legendBoxCoordinates[i][2] = xOffset + OpenGL.mediumTextHeight;
				legendBoxCoordinates[i][3] = yLegendTextTop;
				
				xOffset += OpenGL.mediumTextHeight + Theme.legendTextPadding;
				xLegendNameLeft[i] = xOffset;
				xOffset += OpenGL.mediumTextWidth(gl, allDatasets.get(i).name) + Theme.legendNamesPadding;
				
				legendMouseoverCoordinates[i][2] = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding;
				legendMouseoverCoordinates[i][3] = yLegendBorderTop;
			}
			
			xLegendBorderRight = xOffset - Theme.legendNamesPadding + Theme.legendTextPadding + (Theme.lineWidth / 2);
			if(showXaxisTitle)
				xXaxisTitleTextLeft = xLegendBorderRight + ((xPlotRight - xLegendBorderRight) / 2) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
			
			float temp = yLegendBorderTop + Theme.legendTextPadding;
			if(yPlotBottom < temp) {
				yPlotBottom = temp;
				plotHeight = yPlotTop - yPlotBottom;
			}
		}
		
		if(showXaxisScale) {
			yXaxisTickTextBaseline = yPlotBottom;
			yXaxisTickTextTop = yXaxisTickTextBaseline + OpenGL.smallTextHeight;
			if(isTimestampsMode && SettingsController.isTimeFormatTwoLines())
				yXaxisTickTextTop += 1.3 * OpenGL.smallTextHeight;
			yXaxisTickBottom = yXaxisTickTextTop + Theme.tickTextPadding;
			yXaxisTickTop = yXaxisTickBottom + Theme.tickLength;
			
			yPlotBottom = yXaxisTickTop;
			plotHeight = yPlotTop - yPlotBottom;
		}
		
		if(showYaxisTitle) {
			xYaxisTitleTextTop = xPlotLeft;
			xYaxisTitleTextBaseline = xYaxisTitleTextTop + OpenGL.largeTextHeight;
			yAxisTitle = haveDatasets ? allDatasets.get(0).unit : "";
			yYaxisTitleTextLeft = yPlotBottom + (plotHeight / 2.0f) - (OpenGL.largeTextWidth(gl, yAxisTitle) / 2.0f);
			
			xPlotLeft = xYaxisTitleTextBaseline + Theme.tickTextPadding;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
		}
		
		if(showYaxisScale) {
			yDivisions = ChartUtils.getYdivisions125(plotHeight, plotMinY, plotMaxY);
			float maxTextWidth = 0;
			for(String text : yDivisions.values()) {
				float textWidth = OpenGL.smallTextWidth(gl, text);
				if(textWidth > maxTextWidth)
					maxTextWidth = textWidth;
					
			}
			
			xYaxisTickTextRight = xPlotLeft + maxTextWidth;
			xYaxisTickLeft = xYaxisTickTextRight + Theme.tickTextPadding;
			xYaxisTickRight = xYaxisTickLeft + Theme.tickLength;
			
			xPlotLeft = xYaxisTickRight;
			plotWidth = xPlotRight - xPlotLeft;
			
			if(showXaxisTitle && !showLegend)
				xXaxisTitleTextLeft = xPlotLeft + (plotWidth  / 2.0f) - (OpenGL.largeTextWidth(gl, xAxisTitle)  / 2.0f);
		}
		
		// stop if the plot is too small
		if(plotWidth < 1 || plotHeight < 1)
			return handler;
		
		// force the plot to be an integer number of pixels
		xPlotLeft = (int) xPlotLeft;
		xPlotRight = (int) xPlotRight;
		yPlotBottom = (int) yPlotBottom;
		yPlotTop = (int) yPlotTop;
		plotWidth = xPlotRight - xPlotLeft;
		plotHeight = yPlotTop - yPlotBottom;
		
		// draw plot background
		OpenGL.drawQuad2D(gl, Theme.plotBackgroundColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		// draw the x-axis scale
		if(showXaxisScale) {
			Map<Float, String> divisions = plot.getXdivisions(gl, (int) plotWidth);
			
			OpenGL.buffer.rewind();
			for(Float divisionLocation : divisions.keySet()) {
				float x = divisionLocation + xPlotLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotTop);    OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yPlotBottom); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yXaxisTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = divisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : divisions.entrySet()) {
				String[] lines = entry.getValue().split("\n");
				float x = 0;
				float y = yXaxisTickTextBaseline + ((lines.length - 1) * 1.3f * OpenGL.smallTextHeight);
				for(String line : lines) {
					x = entry.getKey() + xPlotLeft - (OpenGL.smallTextWidth(gl, line) / 2.0f);
					OpenGL.drawSmallText(gl, line, (int) x, (int) y, 0);
					y -= 1.3f * OpenGL.smallTextHeight;
				}
			}
		}
		
		// draw the y-axis scale
		if(showYaxisScale) {
			OpenGL.buffer.rewind();
			for(Float entry : yDivisions.keySet()) {
				float y = (entry - plotMinY) / plotRange * plotHeight + yPlotBottom;
				OpenGL.buffer.put(xPlotLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
				OpenGL.buffer.put(xPlotRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.divisionLinesColor);
				
				OpenGL.buffer.put(xYaxisTickLeft);  OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(xYaxisTickRight); OpenGL.buffer.put(y); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = yDivisions.keySet().size() * 4;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			for(Map.Entry<Float,String> entry : yDivisions.entrySet()) {
				float x = xYaxisTickTextRight - OpenGL.smallTextWidth(gl, entry.getValue());
				float y = (entry.getKey() - plotMinY) / plotRange * plotHeight + yPlotBottom - (OpenGL.smallTextHeight / 2.0f);
				OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
			}
		}
		
		// draw the legend, if space is available
		if(showLegend && haveDatasets && xLegendBorderRight < width - Theme.tilePadding) {
			OpenGL.drawQuad2D(gl, Theme.legendBackgroundColor, xLegendBorderLeft, yLegendBorderBottom, xLegendBorderRight, yLegendBorderTop);
			
			for(int i = 0; i < datasetsCount; i++) {
				if(mouseX >= legendMouseoverCoordinates[i][0] && mouseX <= legendMouseoverCoordinates[i][2] && mouseY >= legendMouseoverCoordinates[i][1] && mouseY <= legendMouseoverCoordinates[i][3]) {
					OpenGL.drawQuadOutline2D(gl, Theme.tickLinesColor, legendMouseoverCoordinates[i][0], legendMouseoverCoordinates[i][1], legendMouseoverCoordinates[i][2], legendMouseoverCoordinates[i][3]);
					Dataset d = allDatasets.get(i);
					handler = EventHandler.onPress(event -> ConfigureView.instance.forDataset(d));
				}
				OpenGL.drawQuad2D(gl, allDatasets.get(i).glColor, legendBoxCoordinates[i][0], legendBoxCoordinates[i][1], legendBoxCoordinates[i][2], legendBoxCoordinates[i][3]);
				OpenGL.drawMediumText(gl, allDatasets.get(i).name, (int) xLegendNameLeft[i], (int) yLegendTextBaseline, 0);
			}
		}
		
		// draw the x-axis title, if space is available
		if(showXaxisTitle)
			if((!showLegend && xXaxisTitleTextLeft > xPlotLeft) || (showLegend && xXaxisTitleTextLeft > xLegendBorderRight + Theme.legendTextPadding))
				OpenGL.drawLargeText(gl, xAxisTitle, (int) xXaxisTitleTextLeft, (int) yXaxisTitleTextBasline, 0);
		
		// draw the y-axis title, if space is available
		if(showYaxisTitle && yYaxisTitleTextLeft > yPlotBottom)
			OpenGL.drawLargeText(gl, yAxisTitle, (int) xYaxisTitleTextBaseline, (int) yYaxisTitleTextLeft, 90);
		
		// acquire the samples
		plot.acquireSamples(plotMinY, plotMaxY, (int) plotWidth, (int) plotHeight);
		
		// draw the plot
		plot.draw(gl, chartMatrix, (int) xPlotLeft, (int) yPlotBottom, (int) plotWidth, (int) plotHeight, plotMinY, plotMaxY);
		
		// draw the trigger level and trigger point markers
		if(triggerEnabled) {
			
			float scalar = ChartsController.getDisplayScalingFactor();
			float markerThickness = 3*scalar;
			float markerLength = 5*scalar;
			float triggerLevel = triggerWidget.getTriggerLevel();
			float yTriggerLevel = (triggerLevel - plotMinY) / plotRange * plotHeight + yPlotBottom;
			
			int triggeredSampleNumber = triggerWidget.getTriggeredSampleNumber();
			float triggerPoint = triggeredSampleNumber >= 0 ? plot.getPixelXforSampleNumber(triggeredSampleNumber, plotWidth) : 0;
			float xTriggerPoint = xPlotLeft + triggerPoint;
			
			boolean mouseOver = false;
			
			// trigger level marker
			if(yTriggerLevel >= yPlotBottom && yTriggerLevel <= yPlotTop) {
				if(mouseX >= xPlotLeft && mouseX <= xPlotLeft + markerLength*1.5 && mouseY >= yTriggerLevel - markerThickness*1.5 && mouseY <= yTriggerLevel + markerThickness*1.5) {
					mouseOver = true;
					handler = EventHandler.onPressOrDrag(dragStarted -> triggeringPaused = true,
					                                     newLocation -> {
					                                         float newTriggerLevel = (newLocation.y - yPlotBottom) / plotHeight * plotRange + plotMinY;
					                                         if(newTriggerLevel < plotMinY)
					                                         	 newTriggerLevel = plotMinY;
					                                         if(newTriggerLevel > plotMaxY)
					                                        	 newTriggerLevel = plotMaxY;
					                                         triggerWidget.setLevel(newTriggerLevel, false);
					                                     },
					                                     dragEnded -> triggeringPaused = false,
					                                     this,
					                                     Theme.upDownCursor);
					OpenGL.drawTriangle2D(gl, Theme.plotOutlineColor, xPlotLeft, yTriggerLevel + markerThickness*1.5f,
					                                                  xPlotLeft + markerLength*1.5f, yTriggerLevel,
					                                                  xPlotLeft, yTriggerLevel - markerThickness*1.5f);
				} else {
					OpenGL.drawTriangle2D(gl, Theme.plotOutlineColor, xPlotLeft, yTriggerLevel + markerThickness,
					                                                  xPlotLeft + markerLength, yTriggerLevel,
					                                                  xPlotLeft, yTriggerLevel - markerThickness);
				}
			}
			
			// trigger point marker
			if(triggeredSampleNumber >= 0) {
				if(xTriggerPoint >= xPlotLeft && xTriggerPoint <= xPlotRight) {
					if(mouseX >= xTriggerPoint - 1.5*markerThickness && mouseX <= xTriggerPoint + 1.5*markerThickness && mouseY >= yPlotTop - 1.5*markerLength && mouseY <= yPlotTop) {
						mouseOver = true;
						handler = EventHandler.onPressOrDrag(dragStarted -> triggeringPaused = true,
						                                     newLocation -> {
						                                         float newPrePostRatio = (newLocation.x - xPlotLeft) / plotWidth;
						                                         if(newPrePostRatio < 0)
						                                        	 newPrePostRatio = 0;
						                                         if(newPrePostRatio > 1)
						                                        	 newPrePostRatio = 1;
						                                         triggerWidget.setPrePostRatio(Math.round(newPrePostRatio * 100), false);
						                                     },
						                                     dragEnded -> triggeringPaused = false,
						                                     this,
						                                     Theme.leftRigthCursor);
						OpenGL.drawTriangle2D(gl, Theme.plotOutlineColor, xTriggerPoint - markerThickness*1.5f, yPlotTop,
						                                                  xTriggerPoint + markerThickness*1.5f, yPlotTop,
						                                                  xTriggerPoint, yPlotTop - markerLength*1.5f);
					} else {
						OpenGL.drawTriangle2D(gl, Theme.plotOutlineColor, xTriggerPoint - markerThickness, yPlotTop,
						                                                  xTriggerPoint + markerThickness, yPlotTop,
						                                                  xTriggerPoint, yPlotTop - markerLength);
					}
				}
			}
			
			// draw lines to the trigger level and trigger point when the user is interacting with the markers
			if(mouseOver || triggeringPaused) {
				float xLeft = xPlotLeft;
				float xRight = xTriggerPoint > xPlotLeft ? xTriggerPoint : xPlotRight;
				float yTop = yPlotTop;
				float yBottom = yTriggerLevel;
				OpenGL.buffer.rewind();
				OpenGL.buffer.put(xLeft);   OpenGL.buffer.put(yBottom);  OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(xRight);  OpenGL.buffer.put(yBottom);  OpenGL.buffer.put(Theme.tickLinesColor, 0, 3);  OpenGL.buffer.put(0.2f);
				OpenGL.buffer.put(xRight);  OpenGL.buffer.put(yTop);     OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(xRight);  OpenGL.buffer.put(yBottom);  OpenGL.buffer.put(Theme.tickLinesColor, 0, 3);  OpenGL.buffer.put(0.2f);
				OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, 4);
			}
			
		}
		
		// draw the tooltip if the mouse is in the plot region and not over something clickable
		if(!allDatasets.isEmpty() && SettingsController.getTooltipVisibility() && mouseX >= xPlotLeft && mouseX <= xPlotRight && mouseY >= yPlotBottom && mouseY <= yPlotTop && handler == null) {
			Plot.TooltipInfo tooltip = plot.getTooltip(mouseX - (int) xPlotLeft, plotWidth);
			if(tooltip.draw) {
				String[] tooltipLines = tooltip.label.split("\n");
				String[] text = new String[datasetsCount + tooltipLines.length];
				Color[] colors = new Color[datasetsCount + tooltipLines.length];
				for(int i = 0; i < tooltipLines.length; i++) {
					text[i] = tooltipLines[i];
					colors[i] = null;
				}
				for(int i = 0; i < datasetsCount; i++) {
					text[i + tooltipLines.length] = allDatasets.get(i).getSampleAsString(tooltip.sampleNumber);
					colors[i + tooltipLines.length] = allDatasets.get(i).color;
				}
				float anchorX = tooltip.pixelX + xPlotLeft;
				if(anchorX >= 0 && datasetsCount > 1) {
					OpenGL.buffer.rewind();
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotTop);
					OpenGL.buffer.put(anchorX); OpenGL.buffer.put(yPlotBottom);
					OpenGL.buffer.rewind();
					OpenGL.drawLinesXy(gl, GL3.GL_LINES, Theme.tooltipVerticalBarColor, OpenGL.buffer, 2);
					ChartUtils.drawTooltip(gl, text, colors, anchorX, mouseY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				} else if(anchorX >= 0) {
					float anchorY = (allDatasets.get(0).getSample(tooltip.sampleNumber) - plotMinY) / plotRange * plotHeight + yPlotBottom;
					ChartUtils.drawTooltip(gl, text, colors, anchorX, anchorY, xPlotLeft, yPlotTop, xPlotRight, yPlotBottom);
				}
			}
		}
		
		// draw the plot border
		OpenGL.drawQuadOutline2D(gl, Theme.plotOutlineColor, xPlotLeft, yPlotBottom, xPlotRight, yPlotTop);
		
		return handler;
		
	}
	
	@Override public void disposeGpu(GL2ES3 gl) {
		
		super.disposeGpu(gl);
		plot.freeResources(gl);
		
	}

}
