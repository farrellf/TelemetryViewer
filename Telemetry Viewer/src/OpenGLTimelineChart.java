import java.awt.Component;
import java.util.List;
import java.util.Map;

import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL3;

/**
 * Renders the label showing the date, and/or an interactive timeline.
 * 
 * User settings:
 *     Playback controls (play, pause, rewind, etc.) can be displayed.
 *     The time can be displayed.
 *     A timeline can be displayed.
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
	
	// playback controls
	boolean showControls = true;
	long previousFrameTimestamp;
	boolean paused;
	boolean playing;
	boolean rewinding;
	int playingSpeed;
	int rewindingSpeed;
	float buttonSize;
	float yButtonsBottom;
	int yButtonsTextBaseline;
	float yButtonsTop;
	float xBeginButtonLeft;
	float xBeginButtonRight;
	float xRewindButtonLeft;
	float xRewindButtonRight;
	float xPauseButtonLeft;
	float xPauseButtonRight;
	float xPlayButtonLeft;
	float xPlayButtonRight;
	float xEndButtonLeft;
	float xEndButtonRight;
	
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
	WidgetCheckbox showControlsWidget;
	WidgetCheckbox showTimeWidget;
	WidgetCheckbox showTimelineWidget;
	WidgetDatasets datasetsWidget;
	
	@Override public String toString() {
		
		return "Timeline";
		
	}
	
	public OpenGLTimelineChart(int x1, int y1, int x2, int y2) {
		
		super(x1, y1, x2, y2);
		
		datasetsWidget = new WidgetDatasets(null,
		                                    newBitfieldEdges  -> bitfieldEdges = newBitfieldEdges,
		                                    newBitfieldLevels -> bitfieldLevels = newBitfieldLevels,
		                                    null,
		                                    false,
		                                    null);
		
		showControlsWidget = new WidgetCheckbox("Show Controls", true, isSelected -> {
		                                                                                 showControls = isSelected;
		                                                                                 if(!showControls) {
		                                                                                	 paused = !OpenGLChartsView.instance.isLiveView();
		                                                                                	 playing = false;
		                                                                                	 rewinding = false;
		                                                                                 }
		                                                                             });
		
		showTimeWidget = new WidgetCheckbox("Show Time", true, isSelected -> showTime = isSelected);
		
		showTimelineWidget = new WidgetCheckbox("Show Timeline", true, isSelected -> {
		                                                                                 showTimeline = isSelected;
		                                                                                 for(Component widget : datasetsWidget.widgets.keySet())
		                                                                                     widget.setVisible(showTimeline);
		                                                                             });
		
		widgets = new Widget[4];
		widgets[0] = showControlsWidget;
		widgets[1] = showTimeWidget;
		widgets[2] = showTimelineWidget;
		widgets[3] = datasetsWidget;
		
	}
	
	public void userIsTimeshifting() {
		
		paused = true;
		playing = false;
		rewinding = false;
		
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
		String timeText = haveTelemetry ? SettingsController.formatTimestampToMilliseconds(nowTimestamp) : "[waiting for telemetry]";
		String[] timeTextLine = timeText.split("\n");
		boolean useTwoLines = haveTelemetry ? twoLineTimestamps && OpenGL.largeTextWidth(gl, timeText.replace('\n', ' ')) > (width - 2*Theme.tilePadding) : false;
		timeHeight = useTwoLines ? 2.3f * OpenGL.largeTextHeight : OpenGL.largeTextHeight;
		
		if(showControls) {
			
			// x and y locations
			buttonSize = OpenGL.largeTextHeight + 2 * Theme.tilePadding;
			yButtonsBottom = height - Theme.tilePadding - buttonSize;
			if(!showTimeline && !showTime)
				yButtonsBottom = (height / 2f) - (buttonSize / 2f);
			else if(!showTimeline && showTime)
				yButtonsBottom = (height / 2f) - (buttonSize / 2f) + (Theme.tilePadding / 2f) + (timeHeight / 2f);
				
			yButtonsTextBaseline = (int) (yButtonsBottom + Theme.tilePadding);
			yButtonsTop = yButtonsBottom + buttonSize;
			float xCenter = width / 2f;
			xBeginButtonLeft   = xCenter - (0.5f * buttonSize) - Theme.tilePadding - buttonSize - Theme.tilePadding - buttonSize;
			xBeginButtonRight  = xBeginButtonLeft + buttonSize;
			xRewindButtonLeft  = xBeginButtonRight + Theme.tilePadding;
			xRewindButtonRight = xRewindButtonLeft + buttonSize;
			xPauseButtonLeft   = xRewindButtonRight + Theme.tilePadding;
			xPauseButtonRight  = xPauseButtonLeft + buttonSize;
			xPlayButtonLeft    = xPauseButtonRight + Theme.tilePadding;
			xPlayButtonRight   = xPlayButtonLeft + buttonSize;
			xEndButtonLeft     = xPlayButtonRight + Theme.tilePadding;
			xEndButtonRight    = xEndButtonLeft + buttonSize;
			
			// draw the buttons
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xBeginButtonLeft,  yButtonsBottom, buttonSize, buttonSize);
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xRewindButtonLeft, yButtonsBottom, buttonSize, buttonSize);
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xPauseButtonLeft,  yButtonsBottom, buttonSize, buttonSize);
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xPlayButtonLeft,   yButtonsBottom, buttonSize, buttonSize);
			OpenGL.drawBox(gl, Theme.legendBackgroundColor, xEndButtonLeft,    yButtonsBottom, buttonSize, buttonSize);
			
			OpenGL.drawBox(gl, Theme.tickLinesColor, xBeginButtonLeft + buttonSize*3f/12f - buttonSize/10f, yButtonsBottom + buttonSize/5f, buttonSize/10f, buttonSize*3f/5f);
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xBeginButtonLeft + buttonSize*3f/12f,   yButtonsBottom + buttonSize/2f,
			                                                xBeginButtonLeft + buttonSize*6.5f/12f, yButtonsBottom + buttonSize/5f,
			                                                xBeginButtonLeft + buttonSize*6.5f/12f, yButtonsBottom + buttonSize*4f/5f);
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xBeginButtonLeft + buttonSize*6.5f/12f, yButtonsBottom + buttonSize/2f,
			                                                xBeginButtonLeft + buttonSize*10f/12f,  yButtonsBottom + buttonSize/5f,
			                                                xBeginButtonLeft + buttonSize*10f/12f,  yButtonsBottom + buttonSize*4f/5f);
			
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xRewindButtonLeft  + buttonSize/3f, yButtonsBottom + buttonSize/2f,
			                                                xRewindButtonRight - buttonSize/3f, yButtonsBottom + 4f/5f*buttonSize,
			                                                xRewindButtonRight - buttonSize/3f, yButtonsBottom + buttonSize/5f);
			
			OpenGL.drawBox(gl, Theme.tickLinesColor, xPauseButtonLeft  + buttonSize/3f, yButtonsBottom + buttonSize/4f, buttonSize/8f, buttonSize/2f);
			OpenGL.drawBox(gl, Theme.tickLinesColor, xPauseButtonRight - buttonSize/3f, yButtonsBottom + buttonSize/4f, -buttonSize/8f, buttonSize/2f);
			
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xPlayButtonLeft + buttonSize/3f,  yButtonsBottom + 4f/5f*buttonSize,
			                                                xPlayButtonRight - buttonSize/3f, yButtonsBottom + buttonSize/2f,
			                                                xPlayButtonLeft + buttonSize/3f,  yButtonsBottom + buttonSize/5f);
			
			OpenGL.drawBox(gl, Theme.tickLinesColor, xEndButtonRight - buttonSize*3f/12f + buttonSize/10f, yButtonsBottom + buttonSize/5f, -buttonSize/10f, buttonSize*3f/5f);
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xEndButtonRight - buttonSize*3f/12f,   yButtonsBottom + buttonSize/2f,
			                                                xEndButtonRight - buttonSize*6.5f/12f, yButtonsBottom + buttonSize/5f,
			                                                xEndButtonRight - buttonSize*6.5f/12f, yButtonsBottom + buttonSize*4f/5f);
			OpenGL.drawTriangle2D(gl, Theme.tickLinesColor, xEndButtonRight - buttonSize*6.5f/12f, yButtonsBottom + buttonSize/2f,
			                                                xEndButtonRight - buttonSize*10f/12f,  yButtonsBottom + buttonSize/5f,
			                                                xEndButtonRight - buttonSize*10f/12f,  yButtonsBottom + buttonSize*4f/5f);
			
			if(playing && playingSpeed > 1) {
				String s = Integer.toString(playingSpeed);
				float w = OpenGL.smallTextWidth(gl, s);
				OpenGL.drawSmallText(gl, s, (int) (xPlayButtonRight - Theme.lineWidth - w), (int) (yButtonsBottom + 2*Theme.lineWidth), 0);
			}
			
			if(rewinding && rewindingSpeed > 1) {
				String s = Integer.toString(rewindingSpeed);
				OpenGL.drawSmallText(gl, s, (int) (xRewindButtonLeft + Theme.lineWidth), (int) (yButtonsBottom + 2*Theme.lineWidth), 0);
			}
			
			// handle mouseover logic
			if(mouseX >= xBeginButtonLeft && mouseX <= xBeginButtonRight && mouseY >= yButtonsBottom && mouseY <= yButtonsTop) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xBeginButtonLeft, yButtonsBottom, buttonSize, buttonSize);
				handler = EventHandler.onPress(event -> {
					paused = true;
					playing = false;
					rewinding = false;
					OpenGLChartsView.instance.setPausedView(ConnectionsController.getFirstTimestamp(), null, 0, false);
				});
			}
			
			if(mouseX >= xRewindButtonLeft && mouseX <= xRewindButtonRight && mouseY >= yButtonsBottom && mouseY <= yButtonsTop) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xRewindButtonLeft, yButtonsBottom, buttonSize, buttonSize);
				handler = EventHandler.onPress(event -> {
					rewindingSpeed = !rewinding ? 1 : rewindingSpeed < 8 ? rewindingSpeed + 1 : rewindingSpeed;
					paused = false;
					playing = false;
					rewinding = true;
					previousFrameTimestamp = System.currentTimeMillis();
				});
			}
			
			if(mouseX >= xPauseButtonLeft && mouseX <= xPauseButtonRight && mouseY >= yButtonsBottom && mouseY <= yButtonsTop) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xPauseButtonLeft, yButtonsBottom, buttonSize, buttonSize);
				long now = nowTimestamp;
				handler = EventHandler.onPress(event -> {
					paused = true;
					playing = false;
					rewinding = false;
					OpenGLChartsView.instance.setPausedView(now, null, 0, false);
				});
			}
			
			if(mouseX >= xPlayButtonLeft && mouseX <= xPlayButtonRight && mouseY >= yButtonsBottom && mouseY <= yButtonsTop) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xPlayButtonLeft, yButtonsBottom, buttonSize, buttonSize);
				handler = EventHandler.onPress(event -> {
					playingSpeed = !playing ? 1 : playingSpeed < 8 ? playingSpeed + 1 : playingSpeed;
					paused = false;
					playing = true;
					rewinding = false;
					previousFrameTimestamp = System.currentTimeMillis();
				});
			}
			
			if(mouseX >= xEndButtonLeft && mouseX <= xEndButtonRight && mouseY >= yButtonsBottom && mouseY <= yButtonsTop) {
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xEndButtonLeft, yButtonsBottom, buttonSize, buttonSize);
				handler = EventHandler.onPress(event -> {
					paused = false;
					playing = false;
					rewinding = false;
					OpenGLChartsView.instance.setLiveView();
				});
			}
			
			// highlight the currently active button if the mouse is not already over a button
			if(paused && handler == null)
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xPauseButtonLeft,  yButtonsBottom, buttonSize, buttonSize);
			if(rewinding && handler == null)
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xRewindButtonLeft, yButtonsBottom, buttonSize, buttonSize);
			if(playing && handler == null)
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xPlayButtonLeft,   yButtonsBottom, buttonSize, buttonSize);
			if(OpenGLChartsView.instance.isLiveView() && handler == null)
				OpenGL.drawBoxOutline(gl, Theme.tickLinesColor, xEndButtonLeft,    yButtonsBottom, buttonSize, buttonSize);
			
			// perform the actual rewinding or playing if appropriate
			if(rewinding) {
				long now = System.currentTimeMillis();
				long delta = (now - previousFrameTimestamp) * rewindingSpeed;
				long newTimestamp = nowTimestamp - delta;
				long firstTimestamp = ConnectionsController.getFirstTimestamp();
				if(newTimestamp < firstTimestamp) {
					newTimestamp = firstTimestamp;
					paused = true;
					rewinding = false;
				}
				OpenGLChartsView.instance.setPausedView(newTimestamp, null, 0, false);
				previousFrameTimestamp = now;
			}
			
			if(playing) {
				long now = System.currentTimeMillis();
				long delta = (now - previousFrameTimestamp) * playingSpeed;
				long newTimestamp = nowTimestamp + delta;
				if(newTimestamp > ConnectionsController.getLastTimestamp()) {
					paused = false;
					rewinding = false;
					playing = false;
					OpenGLChartsView.instance.setLiveView();
				} else {
					OpenGLChartsView.instance.setPausedView(newTimestamp, null, 0, false);
				}
				previousFrameTimestamp = now;
			}
			
			if(paused && OpenGLChartsView.instance.isLiveView())
				paused = false;
			
		}
		
		if(showTime) {
			
			yTimeTop = showControls ? yButtonsBottom - Theme.tilePadding :
			           showTimeline ? height - Theme.tilePadding :
			                          (height / 2f) + (timeHeight / 2f);
			yTimeBaseline1 = yTimeTop - OpenGL.largeTextHeight;
			yTimeBaseline2 = useTwoLines ? yTimeBaseline1 - (1.3f * OpenGL.largeTextHeight) : yTimeBaseline1;
			if(yTimeBaseline2 > 0)
				if(useTwoLines) {
					xTimeLeft1 = (width / 2) - (OpenGL.largeTextWidth(gl, timeTextLine[0]) / 2);
					xTimeLeft2 = (width / 2) - (OpenGL.largeTextWidth(gl, timeTextLine[1]) / 2);
					timeWidth = Float.max(OpenGL.largeTextWidth(gl, timeTextLine[0]), OpenGL.largeTextWidth(gl, timeTextLine[1]));
					OpenGL.drawLargeText(gl, timeTextLine[0], (int) xTimeLeft1, (int) yTimeBaseline1, 0);
					OpenGL.drawLargeText(gl, timeTextLine[1], (int) xTimeLeft2, (int) yTimeBaseline2, 0);
				} else {
					timeText = timeText.replace('\n', ' ');
					xTimeLeft1 = (width / 2) - (OpenGL.largeTextWidth(gl, timeText) / 2);
					timeWidth = OpenGL.largeTextWidth(gl, timeText);
					OpenGL.drawLargeText(gl, timeText, (int) xTimeLeft1, (int) yTimeBaseline1, 0);
				}
			
		}
		
		if(showTimeline && width > 2*Theme.tilePadding) {
			
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
			
			// only draw the timelife if there's space for it
			if(yTimelineTop > height || (showTime && yTimelineTop > yTimeBaseline2) || (showControls && yTimelineTop > yButtonsBottom))
				return handler;
			
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
				EventHandler h = ChartUtils.drawMarkers(gl, edgeMarkers, levelMarkers, xTimelineLeft, showTime ? yTimeBaseline2 - Theme.tickTextPadding - Theme.lineWidth : height - Theme.lineWidth, xTimelineRight, y + 2*markerWidth, mouseX, mouseY);
				if(handler == null)
					handler = h;
				gl.glScissor(originalScissorArgs[0], originalScissorArgs[1], originalScissorArgs[2], originalScissorArgs[3]);
			}
			
			// draw a tooltip if the mouse is not over a button or bitfield event
			if(handler == null && mouseX >= xTimelineLeft && mouseX <= xTimelineRight && mouseY >= 0 && mouseY <= height && haveTelemetry) {
				
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

			OpenGLChartsView.instance.setPausedView(connection.datasets.getTimestamp(sampleNumber), connection, sampleNumber, true);
			
		} else {
			
			// cameras exist, so use the timestamp
			OpenGLChartsView.instance.setPausedView(mouseTimestamp, null, 0, true);
			
		}
		
	}

}
