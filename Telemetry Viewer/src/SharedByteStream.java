/**
 * Inspired by PipedOutputStream/PipedInputStream, but optimized for my use cases.
 * This is a thread-safe way to share a buffer of telemetry packets between two threads (one reader and one writer.)
 * 
 * This class supports two different packet modes: CSV (text) and binary packets.
 * In CSV mode, a single ring buffer is used. The reader receives a COPY of each line of text. This isn't very efficient, but I don't expect people to use CSV mode for massive data streams.
 * In binary mode, two ping-pong buffers are used. The reader receives the ORIGINAL buffer, along with a corresponding offset and byte count. This is much more efficient.
 */
public class SharedByteStream {
	
	private boolean ready;
	private int packetByteCount;
	
	private byte[][] buffer;    // [0 or 1][byteN]
	private int   bufferSize;
	private int[] writeIndex;   // [0 or 1]
	private int[] readIndex;    // [0 or 1]
	private int[] occupiedSize; // [0 or 1]
	private boolean writeIntoA;
	
	private ConnectionTelemetry connection;
	
	/**
	 * Creates a placeholder for sharing data between one reading thread and one writing thread.
	 * Before data can be written or read, the setPacketSize() method must be called.
	 */
	public SharedByteStream(ConnectionTelemetry connection) {
		
		ready = false;
		this.connection = connection;
		
	}
	
	/**
	 * Prepares the buffers to receive data.
	 * 
	 * @param byteCount    Number of bytes per packet (binary mode), or 0 for CSV mode.
	 */
	public synchronized void setPacketSize(int byteCount) {
			
		if(byteCount == 0) {
			
			// CSV mode
			bufferSize   = 8388608; // 8MB each
			buffer       = new byte[2][bufferSize];
			writeIndex   = new int[] {0, 0};
			readIndex    = new int[] {0, 0};
			occupiedSize = new int[] {0, 0};
			
			writeIntoA = true;
			packetByteCount = 0;
			
		} else {
			
			// binary mode
			bufferSize   = 8388608 + byteCount - 1; // 8MB each + enough room to prepend an incomplete packet
			buffer       = new byte[2][bufferSize];
			writeIndex   = new int[] {byteCount - 1, byteCount - 1};
			readIndex    = new int[] {byteCount - 1, byteCount - 1};
			occupiedSize = new int[] {0, 0};
			
			writeIntoA = true;
			packetByteCount = byteCount;
			
		}
		
		ready = true;
		
	}
	
	/**
	 * Appends bytes to the buffer.
	 * 
	 * @param bytes                    Data to write.
	 * @param byteCount                Amount of data.
	 * @throws InterruptedException    If the thread is interrupted while waiting for free space in the buffer.
	 */
	public synchronized void write(byte[] bytes, int byteCount) throws InterruptedException {
		
		// ignore if the buffers are not ready
		if(!ready)
			return;
		
		int writeBuffer = writeIntoA ? 0 : 1;
		
		// wait for free space if necessary
		int availableBufferSpace = bufferSize;
		if(packetByteCount != 0)
			availableBufferSpace -= packetByteCount - 1;
		while(occupiedSize[writeBuffer] + byteCount > availableBufferSpace) {
			notifyAll();
			wait(1);
			writeBuffer = writeIntoA ? 0 : 1;
		}
		
		// write into the buffer
		int startIndex = writeIndex[writeBuffer];
		int endIndex = (writeIndex[writeBuffer] + byteCount - 1) % bufferSize;
		if(endIndex >= startIndex) {
			// no need to wrap around the ring buffer
			System.arraycopy(bytes, 0, buffer[writeBuffer], startIndex, byteCount);
			writeIndex[writeBuffer] += byteCount;
			occupiedSize[writeBuffer] += byteCount;
		} else {
			// must wrap around the ring buffer
			int firstByteCount = bufferSize - writeIndex[writeBuffer];
			int secondByteCount = byteCount - firstByteCount;
			System.arraycopy(bytes,              0, buffer[writeBuffer], startIndex, firstByteCount);
			System.arraycopy(bytes, firstByteCount, buffer[writeBuffer],          0, secondByteCount);
			writeIndex[writeBuffer] = (endIndex + 1) % bufferSize;
			occupiedSize[writeBuffer] += byteCount;
		}
		
		// inform reading thread that new data is available
		notifyAll();
		
	}
	
	/**
	 * Blocks until at least one packet is available.
	 * 
	 * @return    The buffer to read from.
	 */
	private synchronized int awaitPacket() throws InterruptedException {
		
		int readBuffer  = writeIntoA ? 1 : 0;
		int writeBuffer = writeIntoA ? 0 : 1;
		
		// if this buffer contains <1 complete packet,
		// prepend the remaining bytes to the other buffer,
		// then wait for the other buffer to contain >=1 packet
		// then swap buffers
		int remainingByteCount = occupiedSize[readBuffer];
		if(remainingByteCount < packetByteCount) {
			if(remainingByteCount > 0) {
				for(int i = 0; i < remainingByteCount; i++) {
					int writeBufferIndex = packetByteCount - 1 - remainingByteCount + i;
					buffer[writeBuffer][writeBufferIndex] = buffer[readBuffer][readIndex[readBuffer]];
					readIndex[readBuffer]++;
				}
				readIndex[writeBuffer] = packetByteCount - 1 - remainingByteCount;
				occupiedSize[writeBuffer] += remainingByteCount;
			}
			
			while(occupiedSize[writeBuffer] < packetByteCount) {
				notifyAll();
				wait(1);
			}
			
			writeIndex[readBuffer] = packetByteCount - 1;
			readIndex[readBuffer] = packetByteCount - 1;
			occupiedSize[readBuffer] = 0;
			
			writeIntoA = !writeIntoA;
			readBuffer = writeIntoA ? 1 : 0;
		}
		
		return readBuffer;
		
	}
	
	/**
	 * Reads at least one binary packet from the buffer.
	 * 
	 * The returned packets are guaranteed to have correct sync words and valid checksums (if using checksums.)
	 * This method will provide all of the currently available packets, or stop early if a loss of sync or bad checksum is detected.
	 * Stopping early is intentional, so that error messages can be printed in the correct order even if packets are processed by parallel threads.
	 * 
	 * @param syncWord                 The first byte that marks the beginning of each telemetry packet.
	 * @return                         A BufferObject containing the buffer, offset and length.
	 * @throws InterruptedException    If the thread is interrupted while waiting for at least one packet to arrive.
	 */
	public PacketsBuffer readPackets(byte syncWord) throws InterruptedException {
		
		int readBuffer = awaitPacket();
		
		// align with the sync word
		boolean lostSync = false;
		while(buffer[readBuffer][readIndex[readBuffer]] != syncWord) {
			lostSync = true;
			readIndex[readBuffer] = (readIndex[readBuffer] + 1) % bufferSize;
			occupiedSize[readBuffer]--;
			readBuffer = awaitPacket();
		}
		
		// show an error message if sync was lost, unless this is the first packet (because we may have connected in the middle of a packet)
		if(lostSync && connection.datasets.getSampleCount() > 0)
			NotificationsController.showWarningForSeconds("Lost sync with the telemetry packet stream.", 5, true);
		
		// stop at the first loss of sync or failed checksum
		int packetCount = occupiedSize[readBuffer] / packetByteCount;
		int index = readIndex[readBuffer];
		for(int i = 0; i < packetCount; i++) {
			if(buffer[readBuffer][index] != syncWord) {
				packetCount = i;
				break;
			}
			if(!connection.datasets.checksumPassed(buffer[readBuffer], index, packetByteCount)) {
				packetCount = i;
				break;
			}
			index += packetByteCount;
		}
		
		// prepare buffer
		PacketsBuffer packets = new PacketsBuffer();
		packets.buffer = buffer[readBuffer];
		packets.offset = readIndex[readBuffer];
		packets.count = packetCount;

		// update state
		int byteCount = packetCount * packetByteCount;
		readIndex[readBuffer] = (readIndex[readBuffer] + byteCount) % bufferSize;
		occupiedSize[readBuffer] -= byteCount;
		
		return packets;
		
	}
	
	/**
	 * Reads one line of text from the buffer.
	 * 
	 * @return    The text, without a CR/LF.
	 * @throws InterruptedException
	 */
	public synchronized String readLine() throws InterruptedException {
		
		StringBuilder text = new StringBuilder(16 * connection.datasets.getCount());
		
		// skip past any line terminators
		while(true) {
			
			// wait for data if necessary
			while(occupiedSize[0] < 1) {
				notifyAll();
				wait(1);
			}
			
			// read from buffer
			byte b = buffer[0][readIndex[0]];
			if(b == '\r' || b == '\n') {
				readIndex[0] = (readIndex[0] + 1) % bufferSize;
				occupiedSize[0]--;
			} else {
				break;
			}
			
		}
		
		// build up the line of text
		while(true) {
			
			// wait for data if necessary
			while(occupiedSize[0] < 1) {
				notifyAll();
				wait(1);
			}
			
			// read from buffer
			byte b = buffer[0][readIndex[0]];
			readIndex[0] = (readIndex[0] + 1) % bufferSize;
			occupiedSize[0]--;
			if(b != '\r' && b != '\n') {
				text.append((char) b);
			} else {
				break;
			}
			
		}
		
		return text.toString();
		
	}
	
	public static class PacketsBuffer {
		byte[] buffer;
		int offset;
		int count;
	}

}
