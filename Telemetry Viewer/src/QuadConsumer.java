import java.awt.Color;
import java.util.function.BooleanSupplier;

/**
 * A functional interface for the Notification system, similar to Java's BiConsumer.
 */
public interface QuadConsumer {

	public void accept(Color color, String message, BooleanSupplier isExpired, boolean autoExpire);
	
}
