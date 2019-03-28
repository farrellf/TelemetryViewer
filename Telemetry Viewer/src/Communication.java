import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class Communication {

	final static String PORT_UART = "UART"; // the DUT sends data over a serial port
	final static String PORT_TCP  = "TCP";  // the DUT is a TCP client, so we spawn a TCP server
	final static String PORT_UDP  = "UDP";  // the DUT sends UDP packets, so we listen for them
	final static String PORT_TEST = "Test"; // dummy mode that generates test waveforms
	final static String PORT_FILE = "File"; // dummy mode for importing CSV log files
	static String port = PORT_UART;
	
	static Packet packet = new CsvPacket();
	
	static int sampleRate = 10000; // how many samples per second the DUT sends, used for FFTs
	
	static int     uartBaudRate = 9600;
	static boolean uartConnected = false;
	
	static String localIp = "[Local IP Address Unknown]";
	static {
		try { 
			String ips = "";
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while(interfaces.hasMoreElements()) {
				Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
				while(addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					if(address.isSiteLocalAddress())
						ips += address.getHostAddress() + " or ";
				}
			}
			ips = ips.substring(0, ips.length() - 4);
			if(ips.length() > 0)
				localIp = ips;
		} catch(Exception e) {}
	}
	final static int PORT_NUMBER_MIN = 0;
	final static int PORT_NUMBER_MAX = 65535;
	
	static int tcpUdpPort = 8080;
	static boolean tcpConnected = false;
	static boolean udpConnected = false;
	final static int MAX_TCP_IDLE_MILLISECONDS = 10000; // if connected but no new samples after than much time, disconnect and wait for a new connection
	final static int MAX_UDP_PACKET_SIZE = 65507; // 65535 - (8byte UDP header) - (20byte IP header)
	
	static boolean testConnected = false;
	
	static boolean fileConnected = false;
	static String importFilePath = null;
	static String portUsedBeforeImport = null;
	
}
