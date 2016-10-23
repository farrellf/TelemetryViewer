
public interface SerialPortListener {
	
	public void connectionOpened(int sampleRate, String packetType, String portName, int baudRate);
	
	public void connectionClosed();

}
