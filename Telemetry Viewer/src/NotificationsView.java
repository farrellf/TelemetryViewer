import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;

import java.util.function.BooleanSupplier;

@SuppressWarnings("serial")
public class NotificationsView extends JPanel {
	
	public static NotificationsView instance = new NotificationsView();
	
	/**
	 * Private constructor to enforce singleton usage.
	 */
	private NotificationsView() {
		
		super();
		setBorder(new EmptyBorder(0, Theme.padding, 0, Theme.padding));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		// hide until a message needs to be shown
		setPreferredSize(new Dimension(0, 0));
		
	}
	
	/**
	 * Displays a notification on screen, and removes the oldest notification if more than 5 are on screen.
	 * 
	 * @param color         Background color.
	 * @param message       The text message to display.
	 * @param isExpired     A BooleanSupplier that returns true when this Notification should be removed. This will be tested every 100ms.
	 * @param autoExpire    If this Notification should be expired when disconnecting.
	 */
	public void show(Color color, String message, BooleanSupplier isExpired, boolean autoExpire) {
		
		if(getComponentCount() > 5)
			remove(getComponent(0)); // remove oldest
		
		add(new Notification(color, message, isExpired, autoExpire));
		revalidate();
		setPreferredSize(null); // recalculate the preferred size
		setPreferredSize(getPreferredSize());
		
	}
	
	/**
	 * Displays a progress bar notification on screen, and removes the oldest notification if more than 5 are on screen.
	 * 
	 * @param color       Background color.
	 * @param message     The text message to display.
	 */
	public void showProgressBar(Color color, String message) {
		
		// remove any pre-existing progress bar
		Component[] components = getComponents();
		for(int i = 0; i < components.length; i++)
			if(components[i] instanceof Notification && ((Notification) components[i]).isProgressBar)
				remove(components[i]);
		
		if(getComponentCount() > 5)
			remove(getComponent(0)); // remove oldest
		
		add(new Notification(color, message));
		revalidate();
		setPreferredSize(null); // recalculate the preferred size
		setPreferredSize(getPreferredSize());
		
	}
	
	/**
	 * A JPanel that shows a message. A background color is used to indicate the type of message, and the message briefly blinks to draw the user's attention.
	 */
	public class Notification extends JPanel {
		
		final Color FADE_OUT_COLOR        = getBackground();
		final int   FADE_OUT_MILLISECONDS = 800;
		
		JLabel label;
		Color idleColor;
		boolean expireOnDisconnect;
		Timer blinkTimer;
		int blinkCount;
		int fadeOutCount;
		Timer autoRemoveTimer;
		boolean isProgressBar;
		long progressFinishedTimestamp;
		
		/**
		 * A message shown to the user.
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
			setLayout(new MigLayout("insets 0 " + Theme.padding + " " + Theme.padding + " " + Theme.padding, "[grow]"));
			add(label, "align center");
			
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
					fadeAway();
			});
			autoRemoveTimer.start();
			
			// let the user manually expire this by clicking anywhere
			addMouseListener(new MouseListener() {
				@Override public void mousePressed (MouseEvent e) { fadeAway(); }
				@Override public void mouseReleased(MouseEvent e) {}
				@Override public void mouseExited  (MouseEvent e) {}
				@Override public void mouseEntered (MouseEvent e) {}
				@Override public void mouseClicked (MouseEvent e) {}
			});
			
			isProgressBar = false;
			
		}
		
		/**
		 * A progress bar shown to the user.
		 * 
		 * @param color       Notification.SUCCESS or .HINT or .WARNING or .FAILURE or .DEBUG or .VERBOSE
		 * @param message     The text message to display.
		 */
		public Notification(Color color, String message) {
			
			// replace newlines with <br>'s so the JLabel will display them correctly
			// https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#lineending
			message = message.replaceAll("\\R", "<br>");
			
			label = new JLabel(message);
			label.setFont(new Font("Geneva", Font.BOLD, (int) (getFont().getSize() * 1.7)));
			
			idleColor = interpolate(color, FADE_OUT_COLOR, 0.80f);
			expireOnDisconnect = false;
			
			setBorder(BorderFactory.createMatteBorder(Theme.padding, 0, 0, 0, FADE_OUT_COLOR));
			setLayout(new MigLayout("insets 0 " + Theme.padding + " " + Theme.padding + " " + Theme.padding, "[grow]"));
			add(label, "align center");
			
			// periodically test for progress or expiration
			String text = message;
			autoRemoveTimer = new Timer(50, event -> {
				double percent = NotificationsController.getProgress();
				if(percent < 0) {
					label.setText(text + " Done.");
					setBackground(idleColor);
					if(progressFinishedTimestamp == 0)
						progressFinishedTimestamp = System.currentTimeMillis();
					if(System.currentTimeMillis() - progressFinishedTimestamp > 1000) // wait 1 second after 100% before fading away
						fadeAway();
				} else {
					label.setText(String.format("%s %1.1f%%", text, percent * 100.0));
					repaint();
				}
			});
			autoRemoveTimer.start();
			
			// let the user manually expire this by clicking anywhere
			addMouseListener(new MouseListener() {
				@Override public void mousePressed (MouseEvent e) { fadeAway(); }
				@Override public void mouseReleased(MouseEvent e) {}
				@Override public void mouseExited  (MouseEvent e) {}
				@Override public void mouseEntered (MouseEvent e) {}
				@Override public void mouseClicked (MouseEvent e) {}
			});
			
			isProgressBar = true;
			
		}
		
		@Override protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if(isProgressBar) {
				g.setColor(idleColor);
				double progress = NotificationsController.getProgress();
				if(progress < 0)
					progress = 1;
				int width = (int) (getWidth() * progress);
				g.fillRect(0, 0, width, getHeight());
			}
		}
		
		/**
		 * Remove the Notification immediately (no fade away.)
		 */
		public void removeNow() {
			
			if(blinkTimer != null)
				blinkTimer.stop();
			if(autoRemoveTimer != null)
				autoRemoveTimer.stop();
			
			NotificationsView.instance.remove(this);
			NotificationsView.instance.revalidate();
			NotificationsView.instance.setPreferredSize(null); // recalculate the preferred size
			NotificationsView.instance.setPreferredSize(NotificationsView.instance.getPreferredSize());
			
		}
		
		/**
		 * Remove the Notification by fading it away.
		 */
		private void fadeAway() {
		
			if(blinkTimer != null)
				blinkTimer.stop();
			if(autoRemoveTimer != null)
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
				NotificationsView.instance.remove(this);
				NotificationsView.instance.revalidate();
				NotificationsView.instance.setPreferredSize(null); // recalculate the preferred size
				NotificationsView.instance.setPreferredSize(NotificationsView.instance.getPreferredSize());
			});
			removeTimer.setRepeats(false);
			removeTimer.start();
			
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
	
}
