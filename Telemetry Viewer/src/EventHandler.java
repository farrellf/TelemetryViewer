import java.awt.Cursor;
import java.awt.Point;
import java.util.function.Consumer;

public class EventHandler {
	
	public Consumer<Point> mouseLocationHandler;
	public Consumer<Boolean> dragStartedHandler;
	public Consumer<Boolean> dragEndedHandler;
	boolean forPressEvent = false;
	boolean forDragEvent = false;
	boolean dragInProgress = false;
	PositionedChart chart = null;
	Cursor cursor = null;
	
	/**
	 * Force usage of the static methods to create an EventHandler.
	 */
	private EventHandler() { }
	
	/**
	 * Called by the Swing MouseListener/MouseMotionListener when the user has clicked or dragged.
	 * 
	 * @param mouseCoordinates    X and Y pixel locations, relative to the chart, with (0,0) at the bottom-left.
	 */
	public void handleMouseLocation(Point mouseCoordinates) {
		mouseLocationHandler.accept(mouseCoordinates);
	}
	
	/**
	 * Called by the Swing MouseListener when the user has pressed the mouse button.
	 */
	public void handleDragStarted() {
		if(dragStartedHandler != null)
			dragStartedHandler.accept(true);
		if(forDragEvent)
			dragInProgress = true;
	}
	
	/**
	 * Called by the Swing MouseListener when the user has released the mouse button.
	 */
	public void handleDragEnded() {
		if(dragEndedHandler != null)
			dragEndedHandler.accept(true);
		if(forDragEvent)
			dragInProgress = false;
	}
	
	/**
	 * Creates an event handler for click events.
	 * 
	 * @param mouseLocationHandler    Will be called if the user clicks. The location will be a dummy (-1,-1) value.
	 * @return                        The event handler.
	 */
	public static EventHandler onPress(Consumer<Point> mouseLocationHandler) {
		
		EventHandler obj = new EventHandler();
		obj.mouseLocationHandler = mouseLocationHandler;
		obj.forPressEvent = true;
		obj.forDragEvent = false;
		obj.dragInProgress = false;
		obj.chart = null;
		obj.dragStartedHandler = null;
		obj.dragEndedHandler = null;
		obj.cursor = Theme.clickableCursor;
		return obj;
		
	}
	
	/**
	 * Creates an event handler for click events.
	 * 
	 * @param chart                   Chart owning this event handler.
	 * @param mouseLocationHandler    Will be called if the user clicks. The location is in pixels, relative to the chart, with (0,0) at the bottom-left.
	 * @return                        The event handler.
	 */
	public static EventHandler onPress(PositionedChart chart, Consumer<Point> mouseLocationHandler) {
		
		EventHandler obj = new EventHandler();
		obj.mouseLocationHandler = mouseLocationHandler;
		obj.forPressEvent = true;
		obj.forDragEvent = false;
		obj.dragInProgress = false;
		obj.chart = chart;
		obj.dragStartedHandler = null;
		obj.dragEndedHandler = null;
		obj.cursor = Theme.clickableCursor;
		return obj;
		
	}
	
	/**
	 * Creates an event handler for clicking and dragging.
	 * 
	 * @param dragStartedHandler      Will be called when the mouse is pressed.
	 * @param mouseLocationHandler    Will be called when the mouse is pressed or dragged. The location is in pixels, relative to the chart, with (0,0) at the bottom-left.
	 * @param dragEndedHandler        Will be called when the mouse is released.
	 * @param chart                   Chart owning this event handler.
	 * @param cursor                  Mouse cursor to draw.
	 * @return                        The event handler.
	 */
	public static EventHandler onPressOrDrag(Consumer<Boolean> dragStartedHandler, Consumer<Point> mouseLocationHandler, Consumer<Boolean> dragEndedHandler, PositionedChart chart, Cursor cursor) {
		
		EventHandler obj = new EventHandler();
		obj.mouseLocationHandler = mouseLocationHandler;
		obj.forPressEvent = true;
		obj.forDragEvent = true;
		obj.dragInProgress = false;
		obj.chart = chart;
		obj.dragStartedHandler = dragStartedHandler;
		obj.dragEndedHandler = dragEndedHandler;
		obj.cursor = cursor;
		return obj;
		
	}

}
