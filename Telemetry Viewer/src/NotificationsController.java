import java.awt.Color;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

public class NotificationsController {
	
	private static final boolean successEnabled = true;
	private static final boolean hintEnabled    = true;
	private static final boolean warningEnabled = true;
	private static final boolean failureEnabled = true;
	private static final boolean debugEnabled   = false;
	private static final boolean verboseEnabled = false;
	
	private static final Color SUCCESS = Color.GREEN;
	private static final Color HINT    = Color.GREEN;
	private static final Color WARNING = Color.YELLOW;
	private static final Color FAILURE = Color.RED;
	private static final Color DEBUG   = Color.BLUE;
	private static final Color VERBOSE = Color.BLUE;
	
	private static final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	/**
	 * Immediately removes all Notifications (without fade away animations) that should expire when connecting or disconnecting.
	 * This method is thread-safe.
	 */
	public static void removeIfConnectionRelated() {
		
		SwingUtilities.invokeLater(() -> {
			Component[] notifications = NotificationsView.instance.getComponents();
			for(Component notification : notifications) {
				NotificationsView.Notification n = (NotificationsView.Notification) notification;
				if(n.expireOnDisconnect)
					n.removeNow();
			}
		});
		
	}
	
	/**
	 * Shows a success message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showSuccessUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {
		
		System.out.println(timestamp.format(new Date()) + "   [SUCCESS]   " + message);
		if(successEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(SUCCESS, message, isExpired, autoExpire));	
		
	}
	
	/**
	 * Shows a success message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showSuccessForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [SUCCESS]   " + message);
		if(successEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(SUCCESS, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a hint message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showHintUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [HINT   ]   " + message);
		if(hintEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(HINT, message, isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a hint message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showHintForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [HINT   ]   " + message);
		if(hintEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(HINT, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a warning message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showWarningUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [WARNING]   " + message);
		if(warningEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(WARNING, message, isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a warning message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showWarningForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [WARNING]   " + message);
		if(warningEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(WARNING, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showFailureUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE]   " + message);
		if(failureEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(FAILURE, message, isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showFailureForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE]   " + message);
		if(failureEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(FAILURE, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a debug message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showDebugUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [DEBUG  ]   " + message);
		if(debugEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(DEBUG, message, isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a debug message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showDebugForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [DEBUG  ]   " + message);
		if(debugEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(DEBUG, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a verbose message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showVerboseUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [VERBOSE]   " + message);
		if(verboseEnabled)
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(VERBOSE, message, isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a verbose message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showVerboseForSeconds(String message, int durationSeconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [VERBOSE]   " + message);
		if(verboseEnabled) {
			long startTime = System.currentTimeMillis();
			SwingUtilities.invokeLater(() -> NotificationsView.instance.show(VERBOSE, message, () -> System.currentTimeMillis() - startTime >= durationSeconds * 1000, autoExpire));
		}
		
	}
	
	/**
	 * Shows a progress bar with message. The message will be printed to the console as a verbose log, and a progress bar will be shown in the GUI.
	 * Only one progress bar can be on screen at a time. (The previous progress bar will be removed.)
	 * This method is thread-safe.
	 * 
	 * @param message     The message to show.
	 */
	public static void showProgressBar(String message) {
		
		System.out.println(timestamp.format(new Date()) + "   [VERBOSE]   " + message);
		SwingUtilities.invokeLater(() -> NotificationsView.instance.showProgressBar(HINT, message));
		
	}
	
	private static double progress = 0;
	
	/**
	 * @param amount    The amount of progress to show in the progress bar. 1.0 = 100%, <0 triggers the progress bar to fade away.
	 */
	public static void setProgress(double amount) {
		
		progress = amount;
		
	}
	
	/**
	 * @return    The amount of progress to show in a progress bar.
	 */
	public static double getProgress() {
		
		return progress;
		
	}

}
