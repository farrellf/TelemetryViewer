import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import javax.swing.JOptionPane;

public class NotificationsController {
	
	private static final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	
	public static class Notification {
		String level;
		String[] lines;
		float[] glColor;
		long creationTimestamp;
		
		boolean isProgressBar;
		AtomicLong currentAmount;
		long totalAmount;
		
		boolean expiresAtTimestamp;  // fades away
		long expirationTimestamp;
		boolean expiresAtEvent;
		BooleanSupplier event;
		boolean expiresOnConnection; // no fade, immediate removal
		
		public static Notification forMilliseconds(String level, String[] message, long milliseconds, boolean expiresOnConnection) {
			Notification notification = new Notification();
			Color color = level.equals("hint")    ? SettingsController.getHintNotificationColor() :
			              level.equals("warning") ? SettingsController.getWarningNotificationColor() :
			              level.equals("failure") ? SettingsController.getFailureNotificationColor() :
			                                        SettingsController.getVerboseNotificationColor();
			notification.level = level;
			notification.lines = message;
			notification.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1.0f};
			notification.creationTimestamp = System.currentTimeMillis();
			notification.isProgressBar = false;
			notification.expiresAtTimestamp = true;
			notification.expirationTimestamp = notification.creationTimestamp + milliseconds;
			notification.expiresAtEvent = false;
			notification.expiresOnConnection = expiresOnConnection;
			return notification;
		}
		
		public static Notification untilEvent(String level, String[] message, BooleanSupplier isExpired, boolean expiresOnConnection) {
			Notification notification = new Notification();
			Color color = level.equals("hint")    ? SettingsController.getHintNotificationColor() :
			              level.equals("warning") ? SettingsController.getWarningNotificationColor() :
			              level.equals("failure") ? SettingsController.getFailureNotificationColor() :
			                                        SettingsController.getVerboseNotificationColor();
			notification.level = level;
			notification.lines = message;
			notification.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1.0f};
			notification.creationTimestamp = System.currentTimeMillis();
			notification.isProgressBar = false;
			notification.expiresAtTimestamp = false;
			notification.expiresAtEvent = true;
			notification.event = isExpired;
			notification.expiresOnConnection = expiresOnConnection;
			return notification;
		}
		
		public static Notification progressBar(String level, String[] message, AtomicLong currentAmount, long totalAmount) {
			Notification notification = new Notification();
			Color color = level.equals("hint")    ? SettingsController.getHintNotificationColor() :
			              level.equals("warning") ? SettingsController.getWarningNotificationColor() :
			              level.equals("failure") ? SettingsController.getFailureNotificationColor() :
			                                        SettingsController.getVerboseNotificationColor();
			notification.level = level;
			notification.lines = message;
			notification.glColor = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1.0f};
			notification.creationTimestamp = System.currentTimeMillis();
			notification.isProgressBar = true;
			notification.currentAmount = currentAmount;
			notification.totalAmount = totalAmount;
			notification.expiresAtTimestamp = false;
			notification.expiresAtEvent = false;
			notification.expiresOnConnection = false;
			return notification;
		}
		
		private Notification() {}
	}
	private static List<Notification> notifications = Collections.synchronizedList(new ArrayList<Notification>());
	
	/**
	 * @return    Notifications to show the user. If >5 exist, the oldest ones will fade away.
	 */
	public static List<Notification> getNotifications() {
		
		notifications.removeIf(item -> item.expiresAtTimestamp && item.expirationTimestamp + Theme.animationMilliseconds <= System.currentTimeMillis());
		notifications.removeIf(item -> item.expiresAtEvent && item.event.getAsBoolean() == true);
		notifications.forEach(item -> {
			if(item.isProgressBar && item.currentAmount.get() >= item.totalAmount) {
				item.expiresAtTimestamp = true;
				item.expirationTimestamp = System.currentTimeMillis() + 2000; // fade away 2 seconds after done
				item.lines[0] += " Done";
				item.isProgressBar = false;
				NotificationsController.showDebugMessage(item.lines[0]);
			}
		});
		if(notifications.size() > 5) {
			long now = System.currentTimeMillis();
			for(int i = 0; i < notifications.size() - 5; i++) {
				Notification n = notifications.get(i);
				if(!n.expiresAtTimestamp || n.expirationTimestamp > now) {
					n.expiresAtTimestamp = true;
					n.expirationTimestamp = now;
				}
			}
		}
		
		return notifications;
		
	}
	
	/**
	 * Immediately removes all Notifications (without fade away animations) that should expire when connecting or disconnecting.
	 * This method is thread-safe.
	 */
	public static void removeIfConnectionRelated() {
		
		notifications.removeIf(item -> item.expiresOnConnection);
		
	}
	
	/**
	 * Shows a hint message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested periodically.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showHintUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [HINT    ]   " + message);
		if(SettingsController.getHintNotificationVisibility())
			notifications.add(Notification.untilEvent("hint", message.split("\\R"), isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a warning message. It will be printed to the console, and if enabled, it will be shown in the GUI for an amount of time.
	 * This method is thread-safe.
	 * 
	 * @param message         The message to show.
	 * @param milliseconds    How long to show this message for.
	 * @param autoExpire      If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showWarningForMilliseconds(String message, long milliseconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [WARNING ]   " + message);
		if(SettingsController.getWarningNotificationVisibility())
			notifications.add(Notification.forMilliseconds("warning", message.split("\\R"), milliseconds, autoExpire));
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI until an expiration condition is met.
	 * This method is thread-safe.
	 * 
	 * @param message       The message to show.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested periodically.
	 * @param autoExpire    If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showFailureUntil(String message, BooleanSupplier isExpired, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE ]   " + message);
		if(SettingsController.getFailureNotificationVisibility())
			notifications.add(Notification.untilEvent("failure", message.split("\\R"), isExpired, autoExpire));
		
	}
	
	/**
	 * Shows a failure message. It will be printed to the console, and if enabled, it will be shown in the GUI for an amount of time.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 * @param durationSeconds    How long to show this message for.
	 * @param autoExpire         If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showFailureForMilliseconds(String message, long milliseconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [FAILURE ]   " + message);
		if(SettingsController.getFailureNotificationVisibility())
			notifications.add(Notification.forMilliseconds("failure", message.split("\\R"), milliseconds, autoExpire));
		
	}
	
	/**
	 * Shows a verbose message. It will be printed to the console, and if enabled, it will be shown in the GUI for a number of seconds.
	 * This method is thread-safe.
	 * 
	 * @param message         The message to show.
	 * @param milliseconds    How long to show this message for.
	 * @param autoExpire      If this Notification should be expired when connecting or disconnecting.
	 */
	public static void showVerboseForMilliseconds(String message, long milliseconds, boolean autoExpire) {

		System.out.println(timestamp.format(new Date()) + "   [VERBOSE ]   " + message);
		if(SettingsController.getVerboseNotificationVisibility())
			notifications.add(Notification.forMilliseconds("verbose", message.split("\\R"), milliseconds, autoExpire));
		
	}
	
	/**
	 * Shows a debug message. It will be printed to the console, and if enabled, it will be shown in the GUI for 10 seconds.
	 * This method is thread-safe.
	 * 
	 * @param message            The message to show.
	 */
	public static void showDebugMessage(String message) {

		System.out.println(timestamp.format(new Date()) + "   [DEBUG   ]   " + message);
		
	}
	
	/**
	 * Shows a progress bar with message. The message will be printed to the console as a verbose log, and a progress bar will be shown in the GUI.
	 * This method is thread-safe.
	 * 
	 * @param message     The message to show.
	 */
	public static AtomicLong showProgressBar(String message, long totalAmount) {
		
		System.out.println(timestamp.format(new Date()) + "   [HINT    ]   " + message);
		
		AtomicLong currentAmount = new AtomicLong(0);
		notifications.add(Notification.progressBar("hint", message.split("\\R"), currentAmount, totalAmount));
		
		return currentAmount;
		
	}
	
	public static void showCriticalFault(String message) {
		
		System.out.println(timestamp.format(new Date()) + "   [CRITICAL]   " + message);
		JOptionPane.showMessageDialog(null, "<html><b>CRITICAL FAULT</b><br><br>If you continue to use the software it may crash or become unresponsive.<br>The error message is below, but more details may have been printed to the console.<br><br>" + message + "</pre><br></html>", "CRITICAL FAULT", JOptionPane.ERROR_MESSAGE);
		
	}

}
