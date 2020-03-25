import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class EventHandler {
	
	public Consumer<MouseEvent> handler;
	boolean forPressEvent = false;
	boolean forDragEvent = false;
	
	/**
	 * Force usage of the static methods to create an EventHandler.
	 */
	private EventHandler(Consumer<MouseEvent> handler) {
		this.handler = handler;
	}
	
	public void handle(MouseEvent me) {
		handler.accept(me);
	}
	
	public static EventHandler onPress(Consumer<MouseEvent> handler) {
		
		EventHandler obj = new EventHandler(handler);
		obj.forPressEvent = true;
		obj.forDragEvent = false;
		return obj;
		
	}
	
	public static EventHandler onPressOrDrag(Consumer<MouseEvent> handler) {
		
		EventHandler obj = new EventHandler(handler);
		obj.forPressEvent = true;
		obj.forDragEvent = true;
		return obj;
		
	}

}
