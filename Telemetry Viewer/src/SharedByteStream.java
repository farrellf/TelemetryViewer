/**
 * Similar to PipedOutputStream/PipedInputStream, but optimized for my use case.
 * This is a thread-safe way to share a buffer of bytes between two threads.
 */
public class SharedByteStream {
	
	byte[] buffer;
	int bufferSize;
	int writeIndex;
	int readIndex;
	int occupiedSize;
	
	/**
	 * Creates a shared buffer for bytes. One thread writes to it, and another thread reads from it.
	 */
	public SharedByteStream() {
		
		buffer = new byte[1024];
		bufferSize = 1024;
		writeIndex = 0;
		readIndex = 0;
		occupiedSize = 0;
		
	}
	
	/**
	 * Appends bytes to the buffer.
	 * 
	 * @param bytes        Data to write.
	 * @param byteCount    Amount of data.
	 * @throws InterruptedException
	 */
	public synchronized void write(byte[] bytes, int byteCount) throws InterruptedException {
		
		// ensure the buffer is large enough for at least 128 of these
		if(byteCount * 128 > bufferSize) {
			int newSize = (int) Math.pow(2, Math.ceil(Math.log(byteCount * 128) / Math.log(2))); // round up to nearest power of 2
			byte[] newBuffer = new byte[newSize];
			for(int i = 0; i < occupiedSize; i++) {
				newBuffer[i] = buffer[readIndex];
				readIndex = (readIndex + 1) % bufferSize;
			}
			buffer = newBuffer;
			bufferSize = newSize;
			writeIndex = occupiedSize;
			readIndex = 0;
		}
		
		// wait for free space if necessary
		while(occupiedSize + byteCount > bufferSize) {
			notifyAll();
			wait(1);
		}
		
		// write into the buffer
		for(int i = 0; i < byteCount; i++) {
			buffer[writeIndex] = bytes[i];
			writeIndex = (writeIndex + 1) % bufferSize;
		}
		occupiedSize += byteCount;
		
	}
	
	/**
	 * Skips over bytes, stopping after the specified value.
	 * 
	 * @param value    Skip past this value.
	 * @throws InterruptedException
	 */
	public synchronized void skipPast(byte value) throws InterruptedException {
		
		while(true) {
			
			// wait for data if necessary
			while(occupiedSize < 1) {
				notifyAll();
				wait(1);
			}
			
			// read from buffer
			byte b = buffer[readIndex];
			readIndex = (readIndex + 1) % bufferSize;
			occupiedSize--;
			
			if(b == value)
				return;
			
		}
		
	}
	
	/**
	 * Reads a specific number of bytes from the buffer.
	 * 
	 * @param byteCount    Number of bytes to read.
	 * @return             The byte[] of data.
	 * @throws InterruptedException
	 */
	public synchronized byte[] readBytes(int byteCount) throws InterruptedException {
		
		// wait for data if necessary
		while(occupiedSize < byteCount) {
			notifyAll();
			wait(1);
		}
		
		// read from buffer
		byte[] temp = new byte[byteCount];
		for(int i = 0; i < byteCount; i++) {
			temp[i] = buffer[readIndex];
			readIndex = (readIndex + 1) % bufferSize;
		}
		occupiedSize -= byteCount;
		
		return temp;
		
	}
	
	/**
	 * Reads one line of text from the buffer.
	 * 
	 * @return    The text, without a CR/LF.
	 * @throws InterruptedException
	 */
	public synchronized String readLine() throws InterruptedException {
		
		StringBuilder text = new StringBuilder(16 * DatasetsController.getDatasetsCount());
		
		// skip past any line terminators
		while(true) {
			
			// wait for data if necessary
			while(occupiedSize < 1) {
				notifyAll();
				wait(1);
			}
			
			// read from buffer
			byte b = buffer[readIndex];
			if(b == '\r' || b == '\n') {
				readIndex = (readIndex + 1) % bufferSize;
				occupiedSize--;
			} else {
				break;
			}
			
		}
		
		// build up the line of text
		while(true) {
			
			// wait for data if necessary
			while(occupiedSize < 1) {
				notifyAll();
				wait(1);
			}
			
			// read from buffer
			byte b = buffer[readIndex];
			readIndex = (readIndex + 1) % bufferSize;
			occupiedSize--;
			if(b != '\r' && b != '\n') {
				text.append((char) b);
			} else {
				break;
			}
			
		}
		
		return text.toString();
		
	}

}
