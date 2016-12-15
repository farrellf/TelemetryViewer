import javax.swing.JFrame;

import com.fazecast.jSerialComm.SerialPort;

public interface Packet {
	
	public void clear();
	public void showDataStructureWindow(JFrame parentWindow, boolean testMode);
	public String toString();
	public void startReceivingData(SerialPort port);
	public void stopReceivingData();

}
