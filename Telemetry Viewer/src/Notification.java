import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A JPanel that shows a message. A background color is used to indicate the type of message, and the message briefly blinks to draw the user's attention.
 */
@SuppressWarnings("serial")
public class Notification extends JPanel {
	
	public static final Color SUCCESS = Color.GREEN;
	public static final Color HINT    = Color.GREEN;
	public static final Color WARNING = Color.YELLOW;
	public static final Color FAILURE = Color.RED;
	public static final Color DEBUG   = Color.BLUE;
	public static final Color VERBOSE = Color.BLUE;
	
	final Color FADE_OUT_COLOR        = getBackground();
	final int   FADE_OUT_MILLISECONDS = 800;
	
	JLabel label;
	Color idleColor;
	boolean expireOnDisconnect;
	Timer blinkTimer;
	int blinkCount;
	int fadeOutCount;
	Timer autoRemoveTimer;
	
	/**
	 * A message shown to the user in the NotificationsView.
	 * 
	 * @param color         Notification.SUCCESS or .HINT or .WARNING or .FAILURE or .DEBUG or .VERBOSE
	 * @param message       The text message to display.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public Notification(Color color, String message, BooleanSupplier isExpired, boolean autoExpire) {
		
		// replace newlines with <br>'s so the JLabel will display them correctly
		// https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#lineending
		message = message.replaceAll("\\R", "<br>");
		
		label = new JLabel("<html>" + message + "</html>");
		label.setFont(new Font("Geneva", Font.BOLD, (int) (getFont().getSize() * 1.7)));
		
		idleColor = interpolate(color, FADE_OUT_COLOR, 0.80f);
		expireOnDisconnect = autoExpire;
		
		setBorder(BorderFactory.createMatteBorder(Theme.padding, 0, 0, 0, FADE_OUT_COLOR));
		setBackground(color);
		add(label);
		
		// animate a short blink of color to get the user's attention
		blinkCount = 0;
		blinkTimer = new Timer(250, event -> {
			setBackground((blinkCount % 2 == 0) ? idleColor : color);
			blinkCount++;
			if(blinkCount == 3)
				blinkTimer.stop();
		});
		blinkTimer.start();
		
		// periodically test for the expiration condition
		autoRemoveTimer = new Timer(100, event -> {
			if(isExpired.getAsBoolean())
				remove();
		});
		autoRemoveTimer.start();
		
		// let the user manually expire this by clicking anywhere
		addMouseListener(new MouseListener() {
			@Override public void mousePressed (MouseEvent e) { remove(); }
			@Override public void mouseReleased(MouseEvent e) {}
			@Override public void mouseExited  (MouseEvent e) {}
			@Override public void mouseEntered (MouseEvent e) {}
			@Override public void mouseClicked (MouseEvent e) {}
		});
		
	}
	
	/**
	 * Remove the Notification by fading it away.
	 */
	public void remove() {
	
		blinkTimer.stop();
		autoRemoveTimer.stop();
		
		// animate a 40-step fade out before this panel is removed
		int fadeOutSteps = 40;
		fadeOutCount = 0;
		Timer fadeOutTimer = new Timer(FADE_OUT_MILLISECONDS / fadeOutSteps, event -> {
			setBackground(interpolate(idleColor, FADE_OUT_COLOR, (float) fadeOutCount / (float) fadeOutSteps));
			label.setForeground(interpolate(Color.BLACK, FADE_OUT_COLOR, (float) fadeOutCount / (float) fadeOutSteps));
			fadeOutCount++;
		});
		fadeOutTimer.start();
		
		// remove this panel when its time is up
		Timer removeTimer = new Timer(FADE_OUT_MILLISECONDS, event -> {
			JPanel notificationsView = (JPanel) getParent();
			if(notificationsView != null) { // yes, null is actually possible if the user manually expires this Notification while it is already fading out
				notificationsView.remove(this);
				notificationsView.revalidate();
				notificationsView.setPreferredSize(null); // recalculate the preferred size
				notificationsView.setPreferredSize(notificationsView.getPreferredSize());
			}
		});
		removeTimer.setRepeats(false);
		removeTimer.start();
		
	}
	
	/**
	 * Remove the Notification immediately (no fade away.)
	 */
	public void removeNow() {
		
		blinkTimer.stop();
		autoRemoveTimer.stop();
		
		JPanel notificationsView = (JPanel) getParent();
		if(notificationsView != null) { // yes, null is actually possible if the user manually expires this Notification while it is already fading out
			notificationsView.remove(this);
			notificationsView.revalidate();
			notificationsView.setPreferredSize(null); // recalculate the preferred size
			notificationsView.setPreferredSize(notificationsView.getPreferredSize());
		}
		
	}
	
	/**
	 * Interpolates between two colors.
	 * 
	 * @param a             First color.
	 * @param b             Second color.
	 * @param percentage    0.0 = pure a, 1.0 = pure b.
	 * @return              The resulting color.
	 */
	private Color interpolate(Color a, Color b, float percentage) {
		
		float red   = (a.getRed()   / 255f * (1f - percentage)) + (b.getRed()   / 255f * percentage);
		float green = (a.getGreen() / 255f * (1f - percentage)) + (b.getGreen() / 255f * percentage);
		float blue  = (a.getBlue()  / 255f * (1f - percentage)) + (b.getBlue()  / 255f * percentage);
		
		if(red < 0)   red   = 0;
		if(green < 0) green = 0;
		if(blue < 0)  blue  = 0;
		if(red > 1)   red   = 1;
		if(green > 1) green = 1;
		if(blue > 1)  blue  = 1;
		
		return new Color(red, green, blue);
		
	}
	
}