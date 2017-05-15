import java.io.InputStream;

import javax.swing.JFrame;

public interface Packet {
	
	public void clear();
	public void showDataStructureWindow(JFrame parentWindow, boolean testMode);
	public String toString();
	public void startReceivingData(InputStream stream);
	public void stopReceivingData();

}
