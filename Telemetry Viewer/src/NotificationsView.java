import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.util.function.BooleanSupplier;

@SuppressWarnings("serial")
public class NotificationsView extends JPanel {
	
	/**
	 * Creates a JPanel that shows Notifications to the user.
	 * Notifications are added with the NotificationsController.show*() methods.
	 */
	public NotificationsView() {
		
		super();
		setBorder(new EmptyBorder(0, Theme.guiThickPadding, 0, Theme.guiThickPadding));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		// hide until a message needs to be shown
		setPreferredSize(new Dimension(0, 0));
		
		// listen for new messages
		NotificationsController.setListener((Color color, String message, BooleanSupplier isExpired, boolean autoExpire) -> {
			
			// if we have more than 5 visible messages, immediately remove the oldest to make room
			if(getComponentCount() > 5)
				remove(getComponent(0));
			
			Notification panel = new Notification(color, message, isExpired, autoExpire);
			
			add(panel);
			revalidate();
			setPreferredSize(null); // recalculate the preferred size
			setPreferredSize(getPreferredSize());
			
		});
		
		NotificationsController.setNotificationsView(this);
		
	}
	
}
