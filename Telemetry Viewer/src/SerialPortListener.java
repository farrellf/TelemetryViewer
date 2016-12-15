
public interface SerialPortListener {
	
	public void connectionOpened(int sampleRate, Packet packet, String portName, int baudRate);
	
	public void connectionClosed();
	
	public void connectionLost();

}
