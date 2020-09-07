import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import com.jogamp.common.nio.Buffers;

public class StorageTimestamps {
	
	// timestamps are buffered into "slots" which each hold 1M values.
	// to speed up timestamp queries, the min and max value is tracked for smaller "blocks" of 1K values.
	private final int BLOCK_SIZE = 1024;    // 1K
	private final int SLOT_SIZE  = 1048576; // 1M
	private final int MAX_SAMPLE_NUMBER = Integer.MAX_VALUE;
	private final int BYTES_PER_VALUE = 8; // 8 bytes per long
	
	private volatile int sampleCount = 0;
	private volatile Slot[] slot                = new Slot[MAX_SAMPLE_NUMBER / SLOT_SIZE  + 1]; // +1 to round up
	private volatile long[] minimumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	private volatile long[] maximumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	
	// older slots can be swapped to disk when memory runs low.
	private final Path filePath;
	private final FileChannel file;
	
	// a cache is used to speed up calls to getRange()
	private final int CACHE_SIZE = 3 * SLOT_SIZE;
	private ByteBuffer cacheBytes = Buffers.newDirectByteBuffer(CACHE_SIZE * BYTES_PER_VALUE);
	private LongBuffer cacheLongs = cacheBytes.asLongBuffer();
	private int startOfCache = 0;
	private int firstCachedSampleNumber = startOfCache - 1;
	private int lastCachedSampleNumber  = startOfCache - 1;

	/**
	 * Prepares storage space for a sequence of timestamps.
	 * 
	 * @param filename    The filename to use, without a path or file extension.
	 */
	public StorageTimestamps(String filename) {
		
		filePath = Paths.get("cache/" + filename + ".bin");
		
		FileChannel temp = null;
		try {
			temp = FileChannel.open(filePath, StandardOpenOption.CREATE,
			                                  StandardOpenOption.TRUNCATE_EXISTING,
			                                  StandardOpenOption.READ,
			                                  StandardOpenOption.WRITE);
		} catch (IOException e) {
			NotificationsController.showFailureForSeconds("Critial Fault: Unable to create the cache file for \"" + filePath.toString() + "\"", 999, false);
			e.printStackTrace();
		}
		file = temp;
		
	}
	
	/**
	 * Adds a new value, and updates the min/max records.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param value    The new value.
	 */
	public void appendTimestamp(long value) {
		
		int slotN  = sampleCount / SLOT_SIZE;
		int valueN = sampleCount % SLOT_SIZE;
		int blockN = sampleCount / BLOCK_SIZE;
		
		if(valueN == 0)
			slot[slotN] = new Slot();
		slot[slotN].value[valueN] = value;
		
		if(sampleCount % BLOCK_SIZE == 0) {
			minimumValueInBlock[blockN] = value;
			maximumValueInBlock[blockN] = value;
		} else {
			if(value < minimumValueInBlock[blockN])
				minimumValueInBlock[blockN] = value;
			if(value > maximumValueInBlock[blockN])
				maximumValueInBlock[blockN] = value;
		}
		
		sampleCount++;
		
	}
	
	public int getClosestSampleNumberBefore(long timestamp, int maxSampleNumber) {
		
		int lastBlock = maxSampleNumber / BLOCK_SIZE;
		
		// check if all timestamps are younger
		if(maximumValueInBlock[lastBlock] < timestamp)
			return maxSampleNumber;
		
		// check the blocks
		for(int i = lastBlock; i >= 0; i--) {
			if(minimumValueInBlock[i] < timestamp) {
				int firstSampleNumber = i * BLOCK_SIZE;
				int lastSampleNumber = Integer.min((i+1) * BLOCK_SIZE, maxSampleNumber);
				for(int sampleN = lastSampleNumber; sampleN >= firstSampleNumber; sampleN--)
					if(getTimestamp(sampleN) < timestamp)
						return sampleN;
			}
		}
		
		// all timestamps are older
		return 0;
		
	}
	
	/**
	 * Reads the timestamp for a certain sample number.
	 * This method is thread-safe.
	 * 
	 * @param sampleNumber    Which sample number to read. This MUST be a valid sample number.
	 * @return                The corresponding timestamp.
	 */
	public long getTimestamp(int sampleNumber) {
		
		int slotN  = sampleNumber / SLOT_SIZE;
		int valueN = sampleNumber % SLOT_SIZE;
		
		// read from memory if possible
		if(!slot[slotN].flushing && slot[slotN].inRam)
			return slot[slotN].value[valueN];
		
		// read from cache if possible
		updateCacheIfAppropriate(sampleNumber, sampleNumber);
		if(sampleNumber >= firstCachedSampleNumber && sampleNumber <= lastCachedSampleNumber) {
			return cacheLongs.get(sampleNumber - startOfCache);
		}
		
		// read from disk
		while(slot[slotN].flushing);
		ByteBuffer buffer = Buffers.newDirectByteBuffer(BYTES_PER_VALUE);
		long offset = (long) sampleNumber * (long) BYTES_PER_VALUE;
		try {
			file.read(buffer, offset);
		} catch (IOException e) {
			NotificationsController.showFailureForSeconds("Critical Fault: Error while reading a value from the cache file at \"" + filePath.toString() + "\"", 999, false);
			e.printStackTrace();
		}
		return buffer.getLong(0);
		
	}
	
	/**
	 * Reads a sequence of timestamps.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param firstSampleNumber    The first sample number, inclusive. This MUST be a valid sample number.
	 * @param lastSampleNumber     The last sample number, inclusive. This MUST be a valid sample number.
	 */
	public FloatBuffer getTampstamps(int firstSampleNumber, int lastSampleNumber, long plotMinX) {
		
		FloatBuffer buffer = Buffers.newDirectFloatBuffer(lastSampleNumber - firstSampleNumber + 1);
		
		updateCacheIfAppropriate(firstSampleNumber, lastSampleNumber);
		
		// if the entire range is cached, provide it from the cache
		if(firstSampleNumber >= firstCachedSampleNumber && lastSampleNumber <= lastCachedSampleNumber) {
			for(int i = firstSampleNumber; i <= lastSampleNumber; i++)
				buffer.put(cacheLongs.get(i - startOfCache) - plotMinX);
			buffer.rewind();
			return buffer;
		}
		
		// if the entire range is not cached, provide it from the file and/or memory
		int firstSlot = firstSampleNumber / SLOT_SIZE;
		int lastSlot  = lastSampleNumber  / SLOT_SIZE;
		int start = firstSampleNumber;
		int end   = lastSampleNumber;
		for(int slotN = firstSlot; slotN <= lastSlot; slotN++) {
			int valueCount = Integer.min(end - start + 1, SLOT_SIZE - (start % SLOT_SIZE));
			int byteCount  = valueCount * BYTES_PER_VALUE;
			if(!slot[slotN].flushing && slot[slotN].inRam) {
				// fill buffer from slot in memory
				int offset = start % SLOT_SIZE;
				for(int i = offset; i < offset + valueCount; i++)
					buffer.put(slot[slotN].value[i] - plotMinX);
				start += valueCount;
			} else {
				// fill cache from slot on disk
				while(slot[slotN].flushing);
				long offset = (long) start * (long) BYTES_PER_VALUE;
				try {
					ByteBuffer temp = Buffers.newDirectByteBuffer(byteCount);
					file.read(temp, offset);
					for(int i = 0; i < valueCount; i++)
						buffer.put(temp.getLong(i) - plotMinX);
				} catch (IOException e) {
					NotificationsController.showFailureForSeconds("Critical Fault: Error while reading a value from the cache file at \"" + filePath.toString() + "\"", 999, false);
					e.printStackTrace();
				}
				start += valueCount;
			}
		}
		buffer.rewind();
		return buffer;
		
	}
	
	/**
	 * Ensures the cache contains the requested range of samples IF it would be logical for the cache to contain those values.
	 * 
	 * @param firstSampleNumber    The first sample number, inclusive. This MUST be a valid sample number.
	 * @param lastSampleNumber     The last sample number, inclusive. This MUST be a valid sample number.
	 */
	private void updateCacheIfAppropriate(int firstSampleNumber, int lastSampleNumber) {
		
		// don't bother caching if the range is larger than one slot
		if(lastSampleNumber - firstSampleNumber + 1 > SLOT_SIZE)
			return;
		
		// don't bother caching if the range would not be plotted on screen
		if(lastSampleNumber > OpenGLChartsView.instance.lastSampleNumber || firstSampleNumber < OpenGLChartsView.instance.lastSampleNumber - SLOT_SIZE)
			return;
		
		// flush cache if necessary
		if(firstSampleNumber < startOfCache || lastSampleNumber >= startOfCache + CACHE_SIZE) {
			startOfCache = (firstSampleNumber / SLOT_SIZE) * SLOT_SIZE - SLOT_SIZE; // at least one full slot "before" the requested range
			if(startOfCache < 0)
				startOfCache = 0;
			firstCachedSampleNumber = startOfCache - 1;
			lastCachedSampleNumber  = startOfCache - 1;
		}
		
		// new range starts before cached range
		if(firstSampleNumber < firstCachedSampleNumber) {
			int start = firstSampleNumber;
			int end   = firstCachedSampleNumber - 1;
			
			int firstSlot = start / SLOT_SIZE;
			int lastSlot  = end   / SLOT_SIZE;
			for(int slotN = firstSlot; slotN <= lastSlot; slotN++) {
				if(!slot[slotN].flushing && slot[slotN].inRam) {
					// fill cache from slot in memory
					int offset = start % SLOT_SIZE;
					int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
					cacheLongs.position(start - startOfCache);
					cacheLongs.put(slot[slotN].value, offset, length);
					start += length;
				} else {
					// fill cache from slot on disk
					while(slot[slotN].flushing);
					long offset = (long) start * (long) BYTES_PER_VALUE;
					int byteCount = Integer.min(end - start + 1, SLOT_SIZE - (start % SLOT_SIZE)) * BYTES_PER_VALUE;
					cacheBytes.position((start - startOfCache) * BYTES_PER_VALUE);
					ByteBuffer buffer = cacheBytes.slice();
					buffer.limit(byteCount);
					try {
						file.read(buffer, offset);
					} catch (IOException e) {
						NotificationsController.showFailureForSeconds("Critical Fault: Error while reading a value from the cache file at \"" + filePath.toString() + "\"", 999, false);
						e.printStackTrace();
					}
					start += byteCount / BYTES_PER_VALUE;
				}
			}
			
			firstCachedSampleNumber = firstSampleNumber;
		}
		
		// new range ends after cached range
		if(lastSampleNumber > lastCachedSampleNumber) {
			int start = lastCachedSampleNumber + 1;
			int end   = lastSampleNumber;
			
			int slotStart = start / SLOT_SIZE;
			int slotEnd   = end   / SLOT_SIZE;
			for(int slotN = slotStart; slotN <= slotEnd; slotN++) {
				if(!slot[slotN].flushing && slot[slotN].inRam) {
					// fill cache from slot in memory
					int offset = start % SLOT_SIZE;
					int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
					cacheLongs.position(start - startOfCache);
					cacheLongs.put(slot[slotN].value, offset, length);
					start += length;
				} else {
					// fill cache from slot on disk
					while(slot[slotN].flushing);
					long offset = (long) start * (long) BYTES_PER_VALUE;
					int byteCount = Integer.min(end - start + 1, SLOT_SIZE - (start % SLOT_SIZE)) * BYTES_PER_VALUE;
					ByteBuffer buffer = Buffers.newDirectByteBuffer(byteCount);
					try {
						file.read(buffer, offset);
					} catch (IOException e) {
						NotificationsController.showFailureForSeconds("Critical Fault: Error while reading a value from the cache file at \"" + filePath.toString() + "\"", 999, false);
						e.printStackTrace();
					}
					buffer.rewind();
					cacheLongs.position(start - startOfCache);
					cacheLongs.put(buffer.asLongBuffer());
					start += byteCount / BYTES_PER_VALUE;
				}
			}
			
			lastCachedSampleNumber = lastSampleNumber;
			if(firstCachedSampleNumber == -1)
				firstCachedSampleNumber = 0;
		}
		
	}
	
	/**
	 * Moves all but the newest Slot to disk, freeing up space in memory.
	 * This method should be called periodically (depending on how fast new values arrive, and how desperate you are for memory.)
	 * 
	 * TO PREVENT RACE CONDITIONS, THIS METHOD MUST NOT BE CALLED WHEN getValue() or getValues() ARE IN PROGRESS.
	 * Any charts on screen might call getValue() or getValues(), and charts are drawn by the Swing EDT.
	 * Therefore, this method must only be called from the Swing EDT, and only BEFORE or AFTER a chart is being drawn.
	 * To improve efficiency it is best to call this method AFTER all charts are drawn, because that allows the cache to be updated with values from memory.
	 */
	public void moveOldValuesToDisk() {
		
		int lastSlotN = (sampleCount - SLOT_SIZE) / SLOT_SIZE; // keep the most recent slot in memory
		
		for(int slotN = 0; slotN < lastSlotN; slotN++) {
			
			// skip this slot if it is already moved to disk
			if(slot[slotN].flushing || !slot[slotN].inRam)
				continue;
			
			// move this slot to disk
			final int SLOT_N = slotN;
			slot[SLOT_N].flushing = true;
			
			new Thread(() -> {
				try {
					ByteBuffer buffer = Buffers.newDirectByteBuffer(SLOT_SIZE * BYTES_PER_VALUE);
					LongBuffer temp = buffer.asLongBuffer();
					temp.put(slot[SLOT_N].value);
					long offset = (long) SLOT_N * (long) SLOT_SIZE * (long) BYTES_PER_VALUE;
					file.write(buffer, offset);
					file.force(true);
					
					slot[SLOT_N].inRam = false;
					slot[SLOT_N].value = null;
					slot[SLOT_N].flushing = false;
				} catch(Exception e) {
					NotificationsController.showFailureForSeconds("Critical Fault: Error while moving values to the cache file at \"" + filePath.toString() + "\"", 999, false);
					e.printStackTrace();
				}
			}).start();
			
		}
		
	}
	
	/**
	 * Empties the file on disk and empties the slots in memory.
	 * 
	 * TO PREVENT RACE CONDITIONS, THIS METHOD MUST ONLY BE CALLED WHEN NO OTHER METHODS OF THIS CLASS ARE IN PROGRESS.
	 * When connected (UART/TCP/UDP/etc.) a thread could be appending new values.
	 * And regardless of connection status, the charts could be reading existing values.
	 * Therefore: this method must only be called when disconnected AND when no charts are on screen.
	 */
	public void clear() {
		
		// slots may be flushing to disk, so wait for that to finish
		for(Slot s : slot) {
			
			if(s == null)
				break; // reached the end
			
			while(s.flushing)
				; // wait
			
		}
		
		// empty the file
		try {
			file.truncate(0);
		} catch (IOException e) {
			NotificationsController.showFailureForSeconds("Critical Fault: Unable to clear the cache file at \"" + filePath.toString() + "\"", 999, false);
			e.printStackTrace();
		}
		
		// empty the slots
		sampleCount = 0;
		slot                = new Slot[MAX_SAMPLE_NUMBER / SLOT_SIZE  + 1]; // +1 to round up
		minimumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
		maximumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
		
	}
	
	/**
	 * Deletes the file from disk.
	 * This method should be called immediately before removing a Dataset.
	 * 
	 * TO PREVENT RACE CONDITIONS, THIS METHOD MUST ONLY BE CALLED WHEN NO OTHER METHODS OF THIS CLASS ARE IN PROGRESS.
	 * When connected (UART/TCP/UDP/etc.) a thread could be appending new values.
	 * And regardless of connection status, the charts could be reading existing values.
	 * Therefore: this method must only be called when disconnected AND when no charts are on screen.
	 */
	public void dispose() {
		
		// slots may be flushing to disk, so wait for that to finish
		for(Slot s : slot) {
			
			if(s == null)
				break; // reached the end
			
			while(s.flushing)
				; // wait
			
		}
		
		// remove the file from disk
		try {
			file.close();
			Files.deleteIfExists(filePath);
		} catch (IOException e) {
			NotificationsController.showFailureForSeconds("Critical Fault: Unable to delete the cache file at \"" + filePath.toString() + "\"", 999, false);
			e.printStackTrace();
		}
		
	}

	private class Slot {
		
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		private volatile long[] value = new long[SLOT_SIZE];
		
	}
	
}
