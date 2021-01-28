import java.awt.Component;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders the label showing the date, and/or an interactive timeline.
 * 
 * User settings:
 *     Time and/or timeline can be displayed.
 *     Bitfield events can be displayed.
 */
public class OpenGLTimelineChart extends PositionedChart {
	
	// timeline region
	boolean showTimeline;
	float yTimelineTextBaseline;
	float yTimelineTextTop;
	float yTimelineTickBottom;
	float yTimelineTickTop;
	float xTimelineLeft;
	float xTimelineRight;
	float timelineWidth;
	float yTimelineBottom;
	float yTimelineTop;
	float timelineHeight;
	
	long minTimestamp;
	long maxTimestamp;
	
	// time marker
	float markerWidth;
	
	// time label region
	boolean showTime;
	float yTimeTop;
	float yTimeBaseline1;
	float yTimeBaseline2; // only used if label uses 2 lines
	float timeHeight;
	float xTimeLeft1;
	float xTimeLeft2; // only used if label uses 2 lines
	float xTimeRight;
	float timeWidth;
	
	// control widgets
	WidgetCombobox showWidget;
	WidgetDatasets datasetsWidget;
	
	@Override public String toString() {
		
		return "Timeline";
		
	}
	
	public OpenGLTimelineChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		datasetsWidget = new WidgetDatasets(newBitfieldEdges  -> bitfieldEdges = newBitfieldEdges,
		                                    newBitfieldLevels -> bitfieldLevels = newBitfieldLevels);
		
		showWidget = new WidgetCombobox("Show",
		                                new String[] {"Timeline and Time", "Only Timeline", "Only Time"},
		                                newType -> {
		                                	if(newType.equals("Timeline and Time")) {
		                                		showTimeline = true;
		                                		showTime = true;
		                                		for(Component widget : datasetsWidget.widgets.keySet())
		                                			widget.setVisible(true);
		                                	} else if(newType.equals("Only Timeline")) {
		                                		showTimeline = true;
		                                		showTime = false;
		                                		for(Component widget : datasetsWidget.widgets.keySet())
		                                			widget.setVisible(true);
		                                	} else {
		                                		showTimeline = false;
		                                		showTime = true;
		                                		for(Component widget : datasetsWidget.widgets.keySet())
		                                			widget.setVisible(false);
		                                	}
		                                });
		
		widgets = new Widget[2];
		widgets[0] = showWidget;
		widgets[1] = datasetsWidget;
		
	}
	
	@Override public EventHandler drawChart(GL2ES3 gl, float[] chartMatrix, int width, int height, long nowTimestamp, int lastSampleNumber, double zoomLevel, int mouseX, int mouseY) {
		
		EventHandler handler = null;

		boolean haveTelemetry = ConnectionsController.telemetryExists();
		if(!haveTelemetry)
			nowTimestamp = 0;
		minTimestamp = haveTelemetry ? ConnectionsController.getFirstTimestamp() : 0;
		maxTimestamp = haveTelemetry ? ConnectionsController.getLastTimestamp()  : 0;
		if(OpenGLChartsView.instance.isLiveView())
			maxTimestamp = nowTimestamp;
		
		boolean twoLineTimestamps = SettingsController.isTimeFormatTwoLines();
		
		// x and y locations of the timeline
		yTimelineTextBaseline = Theme.tilePadding;
		yTimelineTextTop = yTimelineTextBaseline + OpenGL.smallTextHeight;
		if(twoLineTimestamps)
			yTimelineTextTop += 1.3 * OpenGL.smallTextHeight;
		yTimelineTickBottom = yTimelineTextTop + Theme.tickTextPadding;
		yTimelineTickTop = yTimelineTickBottom + Theme.tickLength;
		xTimelineLeft = Theme.tilePadding;
		xTimelineRight = width - Theme.tilePadding;
		timelineWidth = xTimelineRight - xTimelineLeft;
		yTimelineBottom = yTimelineTickTop;
		yTimelineTop = yTimelineBottom + (Theme.lineWidth * 2);
		timelineHeight = yTimelineTop - yTimelineBottom;
		markerWidth = 6 * ChartsController.getDisplayScalingFactor();
		
		// draw the time label if enabled, and if space is available
		if(showTime) {
			String timeText = haveTelemetry ? SettingsController.formatTimestampToMilliseconds(nowTimestamp) :
			                                  "[waiting for telemetry]";
			String[] timeTextLine = timeText.split("\n");
			boolean useTwoLines = haveTelemetry ? twoLineTimestamps && OpenGL.largeTextWidth(gl, timeText.replace('\n', ' ')) > (width - 2*Theme.tilePadding) :
			                                      false;
			if(showTimeline)
				yTimeTop = height - Theme.tilePadding; // label at the top of the chart region
			else if(useTwoLines)
				yTimeTop = (height / 2) + (OpenGL.largeTextHeight * 2.3f / 2); // 2 line label centered in the chart region
			else
				yTimeTop = (height / 2) + (OpenGL.largeTextHeight / 2); // 1 line label centered in the chart region
			yTimeBaseline1 = yTimeTop - OpenGL.largeTextHeight;
			yTimeBaseline2 = useTwoLines ? yTimeBaseline1 - (1.3f * OpenGL.largeTextHeight) : yTimeBaseline1;
			timeHeight = useTwoLines ? yTimelineTop - yTimeBaseline2 : yTimelineTop - yTimeBaseline1;
			if(useTwoLines) {
				xTimeLeft1 = (width / 2) - (OpenGL.largeTextWidth(gl, timeTextLine[0]) / 2);
				xTimeLeft2 = (width / 2) - (OpenGL.largeTextWidth(gl, timeTextLine[1]) / 2);
				timeWidth = Float.max(OpenGL.largeTextWidth(gl, timeTextLine[0]), OpenGL.largeTextWidth(gl, timeTextLine[1]));
			} else {
				timeText = timeText.replace('\n', ' ');
				xTimeLeft1 = (width / 2) - (OpenGL.largeTextWidth(gl, timeText) / 2);
				timeWidth = OpenGL.largeTextWidth(gl, timeText);
			}
			boolean roomForTimeLineAndTimestamp = yTimeBaseline2 > yTimelineTop + Theme.tickTextPadding + 2*markerWidth && timeWidth < width - 2*Theme.tilePadding;
			boolean roomForTimestampOnly = yTimeBaseline2 > Theme.tilePadding && timeWidth < width - 2*Theme.tilePadding;
			if((showTimeline && roomForTimeLineAndTimestamp) || (!showTimeline && roomForTimestampOnly)) {
				if(useTwoLines) {
					OpenGL.drawLargeText(gl, timeTextLine[0], (int) xTimeLeft1, (int) yTimeBaseline1, 0);
					OpenGL.drawLargeText(gl, timeTextLine[1], (int) xTimeLeft2, (int) yTimeBaseline2, 0);
				} else {
					OpenGL.drawLargeText(gl, timeText, (int) xTimeLeft1, (int) yTimeBaseline1, 0);
				}
			}
		}
		
		// x and y locations of the live view button
		boolean showLiveViewButton = !OpenGLChartsView.instance.isLiveView();
		String buttonText = "\u23ED";
		float xButtonText = width - Theme.tilePadding - OpenGL.largeTextWidth(gl, buttonText) - Theme.legendTextPadding;
		float yButtonText = Theme.tilePadding + Theme.legendTextPadding;
		if(showTimeline)
			yButtonText += yTimelineTop;
		float xButtonLeft = xButtonText - Theme.legendTextPadding;
		float xButtonRight = width - Theme.tilePadding;
		float yButtonBottom = yButtonText - Theme.legendTextPadding;
		float yButtonTop = yButtonBottom + OpenGL.largeTextHeight + 2*Theme.legendTextPadding;
		boolean mouseOverButton = mouseX >= xButtonLeft && mouseX <= xButtonRight && mouseY >= yButtonBottom && mouseY <= yButtonTop;
		
		// draw the timeline if enabled, and if space is available
		if(showTimeline && timelineWidth > 0) {
			
			// get the divisions
			Map<Float, String> divisions = ChartUtils.getTimestampDivisions(gl, timelineWidth, minTimestamp, maxTimestamp);
			
			// draw the tick lines
			OpenGL.buffer.rewind();
			for(Float pixelX : divisions.keySet()) {
				float x = pixelX + xTimelineLeft;
				OpenGL.buffer.put(x); OpenGL.buffer.put(yTimelineTickTop);    OpenGL.buffer.put(Theme.tickLinesColor);
				OpenGL.buffer.put(x); OpenGL.buffer.put(yTimelineTickBottom); OpenGL.buffer.put(Theme.tickLinesColor);
			}
			OpenGL.buffer.rewind();
			int vertexCount = divisions.keySet().size() * 2;
			OpenGL.drawLinesXyrgba(gl, GL3.GL_LINES, OpenGL.buffer, vertexCount);
			
			// draw the tick text
			for(Map.Entry<Float,String> entry : divisions.entrySet()) {
				if(twoLineTimestamps) {
					String[] line = entry.getValue().split("\n");
					float x1 = entry.getKey() + xTimelineLeft - (OpenGL.smallTextWidth(gl, line[0]) / 2.0f);
					float x2 = entry.getKey() + xTimelineLeft - (OpenGL.smallTextWidth(gl, line[1]) / 2.0f);
					float y1 = yTimelineTextBaseline + 1.3f * OpenGL.smallTextHeight;
					float y2 = yTimelineTextBaseline;
					OpenGL.drawSmallText(gl, line[0], (int) x1, (int) y1, 0);
					OpenGL.drawSmallText(gl, line[1], (int) x2, (int) y2, 0);
				} else {
					float x = entry.getKey() + xTimelineLeft - (OpenGL.smallTextWidth(gl, entry.getValue()) / 2.0f);
					float y = yTimelineTextBaseline;
					OpenGL.drawSmallText(gl, entry.getValue(), (int) x, (int) y, 0);
				}
			}
			
			// draw the timeline
			OpenGL.drawBox(gl, Theme.tickLinesColor, xTimelineLeft, yTimelineBottom, timelineWidth, timelineHeight);
			
			// draw a marker at the current timestamp
			float x = (float) (nowTimestamp - minTimestamp) / (float) (maxTimestamp - minTimestamp) * timelineWidth + xTimelineLeft;
			float y = yTimelineTop;
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, x, y, x + markerWidth/2, y+markerWidth, x - markerWidth/2, y+markerWidth);
			OpenGL.drawBox(gl, Theme.tickLinesColor, x - markerWidth/2, y+markerWidth, markerWidth, markerWidth);
			
			// draw any bitfield events
			if(haveTelemetry && (!bitfieldEdges.isEmpty() || !bitfieldLevels.isEmpty())) {
				int[] originalScissorArgs = new int[4];
				gl.glGetIntegerv(GL3.GL_SCISSOR_BOX, originalScissorArgs, 0);
				gl.glScissor(originalScissorArgs[0] + (int) xTimelineLeft, originalScissorArgs[1] + (int) (y + 2*markerWidth), (int) timelineWidth, (int) (height - yTimelineBottom));
				int trueLastSampleNumber = bitfieldEdges.isEmpty() ? bitfieldLevels.get(0).connection.getSampleCount() - 1 :
				                                                     bitfieldEdges.get(0).connection.getSampleCount() - 1;
				BitfieldEvents events = new BitfieldEvents(false, false, bitfieldEdges, bitfieldLevels, 0, trueLastSampleNumber);
				long min = minTimestamp;
				long max = maxTimestamp;
				List<BitfieldEvents.EdgeMarker>  edgeMarkers  = events.getEdgeMarkers ((connection, sampleNumber) -> (float) (connection.datasets.getTimestamp(sampleNumber) - min) / (float) (max - min) * timelineWidth);
				List<BitfieldEvents.LevelMarker> levelMarkers = events.getLevelMarkers((connection, sampleNumber) -> (float) (connection.datasets.getTimestamp(sampleNumber) - min) / (float) (max - min) * timelineWidth);
				handler = ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xTimelineLeft, showTime ? yTimeBaseline2 - Theme.tickTextPadding - Theme.lineWidth : height - Theme.lineWidth, xTimelineRight, y + 2*markerWidth, mouseX, mouseY);
				gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
			}
			
			// draw a tooltip if the mouse is not over the live view button and not over a bitfield event
			if(!mouseOverButton && handler == null && mouseX >= xTimelineLeft && mouseX <= xTimelineRight && mouseY >= 0 && mouseY <= height && haveTelemetry) {
				
				double mousePercentage = (mouseX - xTimelineLeft) / timelineWidth;
				long mouseTimestamp = minTimestamp + (long) (mousePercentage * (double) (maxTimestamp - minTimestamp));
				
				if(!ConnectionsController.telemetryConnections.isEmpty() && ConnectionsController.cameraConnections.isEmpty()) {
					
					// only telemetry connections exist, so find the closest sample number
					long[] connectionErrors       = new long[ConnectionsController.telemetryConnections.size()];
					int[] connectionSampleNumbers = new  int[ConnectionsController.telemetryConnections.size()];
					for(int i = 0; i < ConnectionsController.telemetryConnections.size(); i++) {
						ConnectionTelemetry connection = ConnectionsController.telemetryConnections.get(i);
						int trueLastSampleNumber = connection.getSampleCount() - 1;
						int closestSampleNumberBefore = connection.datasets.getClosestSampleNumberAtOrBefore(mouseTimestamp, trueLastSampleNumber);
						int closestSampleNumberAfter = closestSampleNumberBefore + 1;
						if(closestSampleNumberAfter > trueLastSampleNumber)
							closestSampleNumberAfter = trueLastSampleNumber;
						
						long beforeError = mouseTimestamp - connection.datasets.getTimestamp(closestSampleNumberBefore);
						long afterError  = connection.datasets.getTimestamp(closestSampleNumberAfter) - mouseTimestamp;
						beforeError = Math.abs(beforeError);
						afterError = Math.abs(afterError);
						
						connectionErrors[i] = Long.min(beforeError, afterError);
						connectionSampleNumbers[i] = beforeError < afterError ? closestSampleNumberBefore : closestSampleNumberAfter;
					}
					
					int n = 0;
					for(int i = 1; i < connectionErrors.length; i++)
						if(connectionErrors[i] < connectionErrors[n])
							n = i;
					ConnectionTelemetry connection = ConnectionsController.telemetryConnections.get(n);
					int sampleNumber = connectionSampleNumbers[n];

					mouseTimestamp = connection.datasets.getTimestamp(sampleNumber);
					float tooltipX = (float) (mouseTimestamp - minTimestamp) / (float) (maxTimestamp - minTimestamp) * timelineWidth + xTimelineLeft;
					String[] text = new String[twoLineTimestamps ? 3 : 2];
					text[0] = "Sample " + sampleNumber;
					if(twoLineTimestamps) {
						String[] timestampLine = SettingsController.formatTimestampToMilliseconds(mouseTimestamp).split("\n");
						text[1] = timestampLine[0];
						text[2] = timestampLine[1];
					} else {
						text[1] = SettingsController.formatTimestampToMilliseconds(mouseTimestamp);
					}
					ChartUtils.drawTooltip(gl, text, null, tooltipX, (yTimelineTop + yTimelineBottom)/2, 0, height, width, 0);
					
				} else {
					
					// cameras exist, so find the closest timestamp
					float tooltipX = (float) (mouseTimestamp - minTimestamp) / (float) (maxTimestamp - minTimestamp) * timelineWidth + xTimelineLeft;
					String[] text = new String[twoLineTimestamps ? 2 : 1];
					if(twoLineTimestamps) {
						String[] timestampLine = SettingsController.formatTimestampToMilliseconds(mouseTimestamp).split("\n");
						text[0] = timestampLine[0];
						text[1] = timestampLine[1];
					} else {
						text[0] = SettingsController.formatTimestampToMilliseconds(mouseTimestamp);
					}
					ChartUtils.drawTooltip(gl, text, null, tooltipX, (yTimelineTop + yTimelineBottom)/2, 0, height, width, 0);
					
				}
				
				handler = EventHandler.onPressOrDrag(null, newMouseLocation -> pauseAtMouseX(newMouseLocation.x), null, this, Theme.clickableCursor);

			}
			
		}
		
		// draw a live view button if not in live view
		if(showLiveViewButton) {
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xButtonLeft, yButtonBottom, (xButtonRight - xButtonLeft), (yButtonTop - yButtonBottom));
			if(mouseOverButton) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xButtonLeft, yButtonBottom, (xButtonRight - xButtonLeft), (yButtonTop - yButtonBottom));
				handler = EventHandler.onPress(event -> OpenGLChartsView.instance.setLiveView());
			}
			OpenGL.drawLargeText(gl, buttonText, (int) xButtonText, (int) yButtonText, 0);
		}
		
		return handler;
		
	}
	
	/**
	 * Pauses the view because the user has clicked at some location along the timeline.
	 * 
	 * @param mouseX    Location of the mouse, in pixels, relative to this chart.
	 */
	private void pauseAtMouseX(int mouseX) {
		
		if(mouseX < xTimelineLeft)
			mouseX = (int) xTimelineLeft;
		if(mouseX > xTimelineRight)
			mouseX = (int) xTimelineRight;
			
		double mousePercentage = (mouseX - xTimelineLeft) / timelineWidth;
		long mouseTimestamp = minTimestamp + (long) (mousePercentage * (double) (maxTimestamp - minTimestamp));
		
		if(!ConnectionsController.telemetryConnections.isEmpty() && ConnectionsController.cameraConnections.isEmpty()) {
			
			// only telemetry connections exist, so find the closest sample number
			long[] connectionErrors       = new long[ConnectionsController.telemetryConnections.size()];
			int[] connectionSampleNumbers = new  int[ConnectionsController.telemetryConnections.size()];
			for(int i = 0; i < ConnectionsController.telemetryConnections.size(); i++) {
				ConnectionTelemetry connection = ConnectionsController.telemetryConnections.get(i);
				int trueLastSampleNumber = connection.getSampleCount() - 1;
				int closestSampleNumberBefore = connection.datasets.getClosestSampleNumberAtOrBefore(mouseTimestamp, trueLastSampleNumber);
				int closestSampleNumberAfter = closestSampleNumberBefore + 1;
				if(closestSampleNumberAfter > trueLastSampleNumber)
					closestSampleNumberAfter = trueLastSampleNumber;
				
				long beforeError = mouseTimestamp - connection.datasets.getTimestamp(closestSampleNumberBefore);
				long afterError  = connection.datasets.getTimestamp(closestSampleNumberAfter) - mouseTimestamp;
				beforeError = Math.abs(beforeError);
				afterError = Math.abs(afterError);
				
				connectionErrors[i] = Long.min(beforeError, afterError);
				connectionSampleNumbers[i] = beforeError < afterError ? closestSampleNumberBefore : closestSampleNumberAfter;
			}
			
			int n = 0;
			for(int i = 1; i < connectionErrors.length; i++)
				if(connectionErrors[i] < connectionErrors[n])
					n = i;
			ConnectionTelemetry connection = ConnectionsController.telemetryConnections.get(n);
			int sampleNumber = connectionSampleNumbers[n];

			OpenGLChartsView.instance.setPausedView(connection.datasets.getTimestamp(sampleNumber), connection, sampleNumber);
			
		} else {
			
			// cameras exist, so use the timestamp
			OpenGLChartsView.instance.setPausedView(mouseTimestamp, null, 0);
			
		}
		
	}

}
