import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;

public class TransmitController {
	
	public ConnectionTelemetry connection;
	
	private TransmitView gui;
	private boolean textMode = true;
	private boolean hexMode = false;
	private boolean binMode = false;
	private String dataText = "";
	private volatile byte[] dataBytes = new byte[0];
	private boolean appendCR = true;
	private boolean appendLF = true;
	private volatile boolean repeat = false;
	private volatile int repeatMilliseconds = 1000;
	
	private List<SavedPacket> savedPackets = new ArrayList<SavedPacket>();
	
	/**
	 * Prepares a GUI for transmitting data.
	 * 
	 * @param connection    The corresponding connection.
	 */
	public TransmitController(ConnectionTelemetry connection) {
		
		this.connection = connection;
		gui = new TransmitView(this);
		refreshGui();
		
	}
	
	/**
	 * Enables or disables the transmit widgets as needed.
	 * This should be called on connect/disconnect/textfield changes.
	 */
	public void refreshGui() {
		
		if(!connection.connected) {
			
			gui.typeCombobox.setEnabled(false);
			gui.dataTextfield.setEnabled(false);
			gui.appendCRcheckbox.setEnabled(false);
			gui.appendLFcheckbox.setEnabled(false);
			gui.repeatCheckbox.setEnabled(false);
			gui.repeatMillisecondsTextfield.setEnabled(false);
			gui.saveButton.setEnabled(false);
			gui.transmitButton.setEnabled(false);
			
			for(JButton button : gui.savedPacketButtons)
				button.setEnabled(false);

			gui.setBorder(BorderFactory.createTitledBorder(connection.name + " (disconnected)"));
			
		} else {
			
			boolean haveData = dataText.length() > 0;
			
			gui.typeCombobox.setEnabled(true);
			gui.dataTextfield.setEnabled(true);
			gui.appendCRcheckbox.setEnabled(textMode);
			gui.appendLFcheckbox.setEnabled(textMode);
			gui.repeatCheckbox.setEnabled(haveData);
			gui.repeatMillisecondsTextfield.setEnabled(haveData);
			gui.saveButton.setEnabled(haveData);
			gui.transmitButton.setEnabled(haveData);
			
			for(JButton button : gui.savedPacketButtons)
				button.setEnabled(true);
			
			gui.setBorder(BorderFactory.createTitledBorder(connection.name));
			
		}
		
	}
	
	/**
	 * Gets the GUI and enables/disables widgets as needed to match the current state.
	 * 
	 * @return    The GUI.
	 */
	public TransmitView getGui() {
		
		refreshGui();
		return gui;
		
	}

	/**
	 * Sets how the user wants to provide data: as text, hex bytes, or binary bytes.
	 * 
	 * Existing data will be converted to the new format (preserving it.)
	 * The append CR/LF checkboxes will be updated when switching to/from text mode.
	 * 
	 * @param newType    "Text" or "Hex" or "Bin".
	 */
	public void setTransmitType(String newType) {
		
		boolean wasTextMode = textMode;
		boolean wasHexMode = hexMode;
		boolean wasBinMode = binMode;
		
		textMode = newType.equals("Text");
		hexMode  = newType.equals("Hex");
		binMode  = newType.equals("Bin");
		
		if(wasTextMode == textMode && wasHexMode == hexMode && wasBinMode == binMode)
			return;
		
		if(dataText.equals("")) {
			if(textMode) {
				setAppendCR(true);
				setAppendLF(true);
			} else {
				setAppendCR(false);
				setAppendLF(false);
			}
			return;
		}
		
		if(wasTextMode) {
			
			byte[] bytes = ChartUtils.convertTextStringToBytes(dataText, appendLF, appendCR);
			if(hexMode)
				setTransmitText(ChartUtils.convertBytesToHexString(bytes), null);
			else if(binMode)
				setTransmitText(ChartUtils.convertBytesToBinString(bytes), null);
			
			setAppendCR(false);
			setAppendLF(false);
			
		} else if(wasHexMode) {
			
			byte[] bytes = ChartUtils.convertHexStringToBytes(dataText);
			if(textMode) {
				boolean lf = bytes[bytes.length - 1] == '\n';
				boolean cr = lf ? bytes[bytes.length - 2] == '\r' : bytes[bytes.length - 1] == '\r';
				setAppendCR(cr);
				setAppendLF(lf);
				setTransmitText(ChartUtils.convertBytesToTextString(bytes, false), null);
			} else if(binMode)
				setTransmitText(ChartUtils.convertBytesToBinString(bytes), null);
			
		} else if(wasBinMode) {
			
			byte[] bytes = ChartUtils.convertBinStringToBytes(dataText);
			if(textMode) {
				boolean lf = bytes[bytes.length - 1] == '\n';
				boolean cr = lf ? bytes[bytes.length - 2] == '\r' : bytes[bytes.length - 1] == '\r';
				setAppendCR(cr);
				setAppendLF(lf);
				setTransmitText(ChartUtils.convertBytesToTextString(bytes, false), null);
			} else if(hexMode)
				setTransmitText(ChartUtils.convertBytesToHexString(bytes), null);
			
		}
		
	}
	
	/**
	 * @return    Format used when displaying data to the user: "Text" or "Hex" or "Bin".
	 */
	public String getTransmitType() {
		
		return textMode ? "Text" :
		       hexMode  ? "Hex"  :
		                  "Bin";
		
	}
	
	/**
	 * Sets the data that can be transmitted or saved for later use.
	 * 
	 * For hex or bin mode, spaces are automatically inserted between bytes to improve readability.
	 * The caret is automatically positioned as expected when those spaces are added/removed.
	 * When the user is done typing (focus is lost, or enter is pressed) the last byte will be filled if incomplete.
	 * Example: hex "01 AB C" will become "01 AB C0" because the user did not provide a complete third byte.
	 * When the user is done typing, dataBytes[] will also be updated. It is used when automatically transmitting data periodically.
	 * 
	 * @param newText    The data as a String.
	 * @param event      A KeyEvent if the user is typing, or null if the user has finished typing.
	 */
	public void setTransmitText(String newText, KeyEvent event) {
		
		// prepare
		String oldText = dataText;
		String sanitizedText = textMode ? newText :
		                       hexMode  ? ChartUtils.padStringWithSpaces(newText.replaceAll("[^0-9a-fA-F]", "").toUpperCase(), 2) :
		                                  ChartUtils.padStringWithSpaces(newText.replaceAll("[^01]", ""),                      8);
		int caretPosition = gui.dataTextfield.getCaretPosition();
		
		if(event != null) {
			
			// ignore non-printable key press events, so we don't interfere with "ctrl-c" etc.
			if(newText.equals(oldText))
				return;
			
		} else if(event == null && hexMode) {
			
			// a key was not pressed, so pad the last byte with a zero if incomplete
			sanitizedText = sanitizedText.trim();
			int nybbleCount = sanitizedText.replaceAll("[^0-9a-fA-F]", "").length();
			if(nybbleCount % 2 != 0)
				sanitizedText = sanitizedText + "0";
			
		} else if(event == null && binMode) {
			
			// a key was not pressed, so pad the last byte with zeros if incomplete
			sanitizedText = sanitizedText.trim();
			int bitCount = sanitizedText.replaceAll("[^01]", "").length();
			int remainingBits = 8 - (bitCount % 8);
			if(remainingBits == 8)
				remainingBits = 0;
			if(remainingBits != 0)
				for(int i = 0; i < remainingBits; i++)
					sanitizedText += "0";
			
		}
		
		// for hex/bin modes: position the caret
		if((hexMode || binMode) && !newText.equals(sanitizedText)) {
			String oldDigits = oldText.replaceAll(" ", "");
			String newDigits = sanitizedText.replaceAll(" ", "");
			if(newDigits.equals(oldDigits) && !newText.equals(sanitizedText)) {
				// new character(s) rejected, or existing character(s) removed, so rewind the caret if needed
				int rejectedCharCount = newText.length() - oldText.length();
				if(rejectedCharCount > 0)
					caretPosition -= rejectedCharCount;
			} else {
				// new character(s) accepted, reposition the caret if necessary
				for(int i = 0; i < newDigits.length(); i++) {
					char newChar = newDigits.charAt(newDigits.length() - 1 - i);
					int oldIndex = oldDigits.length() - 1 - i;
					if(oldIndex < 0) {
						caretPosition = newDigits.length() - i;
						break;
					} else {
						char oldChar = oldDigits.charAt(oldIndex);
						if(oldChar != newChar) {
							caretPosition = newDigits.length() - i;
							break;
						}
					}
				}
				int byteCount = hexMode ? (caretPosition / 2) : (caretPosition / 8);
				caretPosition += byteCount;
				if(sanitizedText.length() > caretPosition && sanitizedText.charAt(caretPosition) == ' ') // advance caret past a space
					caretPosition++;
			}
			
		}
		
		// update the model and view
		dataText = sanitizedText;
		if(event == null) {
			if(sanitizedText.equals(""))
				setRepeats(false);
			dataBytes = textMode ? ChartUtils.convertTextStringToBytes(sanitizedText, appendLF, appendCR) :
			            hexMode  ? ChartUtils.convertHexStringToBytes(sanitizedText) :
			                       ChartUtils.convertBinStringToBytes(sanitizedText);
		}
		gui.dataTextfield.setText(sanitizedText);
		
		if(caretPosition > sanitizedText.length())
			caretPosition = sanitizedText.length();
		gui.dataTextfield.setCaretPosition(caretPosition);
		
		refreshGui();
		
	}
	
	/**
	 * @return    The data that can be transmitted or saved for later use.
	 */
	public String getTransmitText() {
		
		return dataText;
		
	}
	
	/**
	 * @return    The data that can be transmitted or saved for later use.
	 */
	public byte[] getTransmitBytes() {
		
		return dataBytes;
		
	}
	
	/**
	 * @param cr    True if a \r should be appended to the text.
	 */
	public void setAppendCR(boolean cr) {
		appendCR = cr;
		gui.appendCRcheckbox.setSelected(cr);
		if(textMode)
			dataBytes = ChartUtils.convertTextStringToBytes(dataText, appendLF, appendCR);
	}
	
	/**
	 * @return    True if a \r should be appended to the text.
	 */
	public boolean getAppendCR() {
		return appendCR;
	}
	
	/**
	 * @param lf    True if a \n should be appended to the text.
	 */
	public void setAppendLF(boolean lf) {
		appendLF = lf;
		gui.appendLFcheckbox.setSelected(lf);
		if(textMode)
			dataBytes = ChartUtils.convertTextStringToBytes(dataText, appendLF, appendCR);
	}
	
	/**
	 * @return    True if a \n should be appended to the text.
	 */
	public boolean getAppendLF() {
		return appendLF;
	}
	
	/**
	 * Sets if the data in the textfield should be automatically transmitted periodically.
	 * 
	 * @param rep    True to automatically transmit. Transmission will begin immediately.
	 */
	public void setRepeats(boolean rep) {
		repeat = rep;
		gui.repeatCheckbox.setSelected(rep);
	}
	
	/**
	 * @return    True if the data in the textfield is being automatically transmitted periodically.
	 */
	public boolean getRepeats() {
		return repeat;
	}
	
	/**
	 * Sets the delay between each automatic transmission.
	 * 
	 * @param milliseconds    The delay between automatic transmissions.
	 */
	public void setRepititionInterval(int milliseconds) {
		if(milliseconds > 0)
			repeatMilliseconds = milliseconds;
		gui.repeatMillisecondsTextfield.setText(repeatMilliseconds + " ms");
	}
	
	/**
	 * @return    The delay between automatic transmissions.
	 */
	public int getRepititionInterval() {
		return repeatMilliseconds;
	}
	
	/**
	 * @param packet    The packet to save so it can be transmitted later by clicking on a button.
	 */
	public void savePacket(SavedPacket packet) {
		boolean hasData = packet.bytes.length > 0;
		boolean notAlreadySaved = true;
		for(SavedPacket existingData : savedPackets)
			if(Arrays.equals(existingData.bytes, packet.bytes) && existingData.escapedText.equals(packet.escapedText))
				notAlreadySaved = false;
		
		if(hasData && notAlreadySaved) {
			savedPackets.add(packet);
			gui.redrawSavedPackets(savedPackets);
		}
	}
	
	/**
	 * @param packet    The packet to remove.
	 */
	public void removePacket(SavedPacket packet) {
		savedPackets.remove(packet);
		gui.redrawSavedPackets(savedPackets);
	}
	
	/**
	 * @return    List of the saved packets.
	 */
	public List<SavedPacket> getSavedPackets() {
		
		return savedPackets;
		
	}
	
	/**
	 * A packet of data as raw bytes and also as user-friendly text.
	 */
	public static class SavedPacket {
		byte[] bytes;
		String escapedText;
	}
	
}
