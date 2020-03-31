import java.net.URI;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * This class allows Logitech mice with high-precision scroll wheels to actually use their high-precision mode.
 * 
 * The Logitech driver seems to rarely expose the high-precision scrolling mode.
 * I'm tempted to call this a poorly-written driver, since high-precision scrolling works fine in most programs when using a touchpad or touchscreen.
 * 
 * Logitech offers a Chrome Extension that enables high-precision scrolling for Chrome. Looking at their Javascript code revealed how simple it is:
 *     1. Connect to a websocket at 127.0.0.1:59243 (this is a server run by Logitech's SetPoint.exe)
 *     2. Any time the window gains focus, you send a JSON text message to the websocket:
 *        {"hiRes":true,"reason":"content looks good for scrolling"}
 * 
 * It seems that the Logitech driver tracks window focus events, and disables high-precision scrolling for everything except Windows 10 tablet-style programs.
 * So just send the above JSON text message every time you gain focus, because if you loose focus, you will be returned to normal scrolling mode.
 * 
 * The Chrome extension can also be reverse engineered without looking at it's Javascript:
 *     1. Install Chrome and the "Logitech Smooth Scrolling" Chrome extension.
 *     2. Close Chrome.
 *     3. Install Wireshark and Npcap. Npcap is needed so Wireshark can monitor the loopback network interface.
 *     4. Run Wireshark.
 *     5. Capture from "Npcap Loopback Adapter"
 *     6. Set the filter to "tcp.port == 59243"
 *     7. Open Chrome.
 *     8. You should see the connection and subsequent JSON text message.
 *     9. If you switch tabs in Chrome, or click on links, or if Chrome loses and then regains focus, you should see the text message being resent.
 *     10. Sometimes the message is not resent, and Chrome looses the high-precision scrolling mode.
 *         This appears to be a bug in the Chrome extension. Making a new tab (or clicking a link) will cause the message to be sent and high-precision scrolling to return.
 *         
 * The WebSocket server is run by Logitech's SetPoint.exe, this can be determined by:
 *     1. Open Chrome.
 *     2. Open cmd.exe as an administrator
 *     3. "netstat -a -b -n"
 *     4. You should see something like:
 *        ...
 *        [chrome.exe]
 *        TCP    127.0.0.1:59243        0.0.0.0:0              LISTENING
 *        [SetPoint.exe]
 *        TCP    127.0.0.1:59243        127.0.0.1:53459        ESTABLISHED
 *        ...
 */
@WebSocket(maxTextMessageSize = 64 * 1024)
public class LogitechSmoothScrolling {
	
	WebSocketClient client;
	Session session;

	/**
	 * Establishes a WebSocket connection to localhost:59243
	 */
	public LogitechSmoothScrolling() {

		try {

			client = new WebSocketClient();
			client.start();
			client.setConnectTimeout(100); // milliseconds
			client.connect(this, new URI("ws://127.0.0.1:59243"), new ClientUpgradeRequest());

		} catch(Exception e) {

//			e.printStackTrace();

		}
		
	}

	/**
	 * After a successful connection, this method will automatically be called. Smooth scrolling is enabled or disabled here.
	 * 
	 * @param session    The WebSocket session.
	 */
	@OnWebSocketConnect public void onConnect(Session session) {

		try {

			this.session = session;
			if(SettingsController.getSmoothScrolling())
				session.getRemote().sendStringByFuture("{\"hiRes\":true,\"reason\":\"content looks good for scrolling\"}");
			else
				session.getRemote().sendStringByFuture("{\"hiRes\":false,\"reason\":\"content looks bad for scrolling\"}"); // made this up but it seems to work

		} catch (Exception e) {

//			e.printStackTrace();

		}

	}

	/**
	 * Re-enables or re-disables smooth scrolling.
	 * This method should be called every time a JFrame gets focus, or whenever the smooth scrolling setting is changed.
	 * 
	 * If the WebSocket connection has been lost, it will be reestablished.
	 */
	public void updateScrolling() {

		try {

			if(session != null && session.isOpen())
				onConnect(session);
			else
				client.connect(this, new URI("ws://127.0.0.1:59243"), new ClientUpgradeRequest());

		} catch(Exception e) {

//			e.printStackTrace();

		}

	}

}