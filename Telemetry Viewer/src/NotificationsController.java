import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BooleanSupplier;

public class NotificationsController {
	
	private static QuadConsumer notificationListener = null;
	private static NotificationsView notificationsView = null;
	
	private static final boolean successEnabled = true;
	private static final boolean hintEnabled    = true;
	private static final boolean warningEnabled = true;
	private static final boolean failureEnabled = true;
	private static final boolean debugEnabled   = false;
	private static final boolean verboseEnabled = false;
	
	private static final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	/**
	 * Registers a listener that will be notified when a message should be shown in the GUI.
	 * 
	 * @param listener    The listener to be notified.
	 */
	public static void setListener(QuadConsumer listener) {
		
		notificationListener = listener;
		
	}
	
	/**
	 * Registers the GUI element that contains the Notifications. This must be called before any notifications occur.
	 * 
	 * @param view    The NotificationView.
	 */
	public static void setNotificationsView(NotificationsView view) {
		
		notificationsView = view;
		
	}
	
	/**
	 * Immediately removes all Notifications (without fade away animations) that should expire when disconnecting.
	 */
	public static void removeAll() {
		
		if(notificationsView != null) {
			Component[] notifications = notificationsView.getComponents();
			for(Component notification : notifications) {
				Notification n = (Notification) notification;
				if(n.expireOnDisconnect)
					n.removeNow();
			}
		}
		
	}
	
	/**
	 * Shows a success message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showSuccessUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {
		
		System.out.println(timestamp.format(new Date()) + "   [SUCCESS]   " + message);
		if(successEnabled)
			notificationListener.accept(Notification.SUCCESS, message, isExpired, autoExpire);	
		
	}
	
	/**
	 * Shows a success message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showSuccessForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [SUCCESS]   " + message);
		if(successEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.SUCCESS, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}
	
	/**
	 * Shows a hint message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showHintUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [HINT   ]   " + message);
		if(hintEnabled)
			notificationListener.accept(Notification.HINT, message, isExpired, autoExpire);
		
	}
	
	/**
	 * Shows a hint message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showHintForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [HINT   ]   " + message);
		if(hintEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.HINT, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}
	
	/**
	 * Shows a warning message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showWarningUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [WARNING]   " + message);
		if(warningEnabled)
			notificationListener.accept(Notification.WARNING, message, isExpired, autoExpire);
		
	}
	
	/**
	 * Shows a warning message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showWarningForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [WARNING]   " + message);
		if(warningEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.WARNING, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showFailureUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE]   " + message);
		if(failureEnabled)
			notificationListener.accept(Notification.FAILURE, message, isExpired, autoExpire);
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showFailureForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE]   " + message);
		if(failureEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.FAILURE, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}
	
	/**
	 * Shows a debug message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showDebugUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [DEBUG  ]   " + message);
		if(debugEnabled)
			notificationListener.accept(Notification.DEBUG, message, isExpired, autoExpire);
		
	}
	
	/**
	 * Shows a debug message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showDebugForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [DEBUG  ]   " + message);
		if(debugEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.DEBUG, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}
	
	/**
	 * Shows a verbose message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public static void showVerboseUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [VERBOSE]   " + message);
		if(verboseEnabled)
			notificationListener.accept(Notification.VERBOSE, message, isExpired, autoExpire);
		
	}
	
	/**
	 * Shows a verbose message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when disconnecting.
	 */
	public static void showVerboseForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [VERBOSE]   " + message);
		if(verboseEnabled) {
			long startTime = System.currentTimeMillis();
			notificationListener.accept(Notification.VERBOSE, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire);
		}
		
	}

}
