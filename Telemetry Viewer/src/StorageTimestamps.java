import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import com.jogamp.common.nio.Buffers;

public class StorageTimestamps {
	
	// timestamps are buffered into "slots" which each hold 1M values.
	// to speed up timestamp queries, the min and max value is tracked for smaller "blocks" of 1K values.
	private final int BLOCK_SIZE = StorageFloats.BLOCK_SIZE;
	private final int SLOT_SIZE  = StorageFloats.SLOT_SIZE;
	private final int MAX_SAMPLE_NUMBER = Integer.MAX_VALUE;
	private final int BYTES_PER_VALUE = 8; // 8 bytes per long
	
	private volatile int sampleCount = 0;
	private volatile Slot[] slot                = new Slot[MAX_SAMPLE_NUMBER / SLOT_SIZE  + 1]; // +1 to round up
	private volatile long[] minimumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	private volatile long[] maximumValueInBlock = new long[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	
	// older slots can be swapped to disk when memory runs low.
	private final Path filePath;
	private final FileChannel file;
	
	private ConnectionTelemetry connection;

	/**
	 * Prepares storage space for a sequence of timestamps.
	 * 
	 * @param connection    The corresponding connection.
	 */
	public StorageTimestamps(ConnectionTelemetry connection) {
		
		this.connection = connection;
		
		filePath = Paths.get("cache/" + this.toString() + ".bin");
		
		FileChannel temp = null;
		try {
			temp = FileChannel.open(filePath, StandardOpenOption.CREATE,
			                                  StandardOpenOption.TRUNCATE_EXISTING,
			                                  StandardOpenOption.READ,
			                                  StandardOpenOption.WRITE);
		} catch (IOException e) {
			NotificationsController.showCriticalFault("Unable to create the cache file for \"" + filePath.toString() + "\"");
			e.printStackTrace();
		}
		file = temp;
		
	}
	
	/**
	 * @return    A place to cache timestamps.
	 */
	public Cache createCache() {
		
		return new Cache();
		
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
		
		if(valueN == 0) {
			slot[slotN] = new Slot();
			if(slotN > 1)
				slot[slotN - 2].flushToDisk(slotN - 2);
		}
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
	
	/**
	 * Set the timestamp for an entire block, and sets the min/max records.
	 * This method must only be called if the current sample count is a multiple of the block size.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param value    The new value.
	 */
	public void fillBlock(long value) {
		
		int slotN      = sampleCount / SLOT_SIZE;
		int slotOffset = sampleCount % SLOT_SIZE;
		int blockN     = sampleCount / BLOCK_SIZE;
		
		if(slotOffset == 0) {
			slot[slotN] = new Slot();
			if(slotN > 1)
				slot[slotN - 2].flushToDisk(slotN - 2);
		}
		
		Arrays.fill(slot[slotN].value, slotOffset, slotOffset + BLOCK_SIZE, value);
		minimumValueInBlock[blockN] = value;
		maximumValueInBlock[blockN] = value;
		
		sampleCount += BLOCK_SIZE;
		
	}
	
	public int getClosestSampleNumberAtOrBefore(long timestamp, int maxSampleNumber) {
		
		int lastBlock = maxSampleNumber / BLOCK_SIZE;
		
		// check if all timestamps are younger
		if(maximumValueInBlock[lastBlock] < timestamp)
			return maxSampleNumber;
		
		// check the blocks
		for(int i = lastBlock; i >= 0; i--) {
			if(minimumValueInBlock[i] <= timestamp) {
				int firstSampleNumber = i * BLOCK_SIZE;
				int lastSampleNumber = Integer.min((i+1) * BLOCK_SIZE, maxSampleNumber);
				for(int sampleN = lastSampleNumber; sampleN >= firstSampleNumber; sampleN--)
					if(getTimestamp(sampleN) <= timestamp)
						return sampleN;
			}
		}
		
		// all timestamps are older
		return -1;
		
	}
	
	public int getClosestSampleNumberAfter(long timestamp) {
		
		// abort if no samples
		if(sampleCount == 0)
			return -1;
		
		int maxSampleNumber = sampleCount - 1;
		int lastBlock = maxSampleNumber / BLOCK_SIZE;
		
		// check if all timestamps are older
		if(minimumValueInBlock[0] > timestamp)
			return 0;
		
		// check the blocks
		for(int i = 0; i <= lastBlock; i++) {
			if(maximumValueInBlock[i] > timestamp) {
				int firstSampleNumber = i * BLOCK_SIZE;
				int lastSampleNumber = Integer.min((i+1) * BLOCK_SIZE, maxSampleNumber);
				for(int sampleN = firstSampleNumber; sampleN <= lastSampleNumber; sampleN++)
					if(getTimestamp(sampleN) > timestamp)
						return sampleN;
			}
		}
		
		// all timestamps are younger
		return maxSampleNumber;
		
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
		long[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
		if(!slot[slotN].flushing && slot[slotN].inRam)
			return array[valueN];
		
		// read from disk
		while(slot[slotN].flushing);
		ByteBuffer buffer = Buffers.newDirectByteBuffer(BYTES_PER_VALUE);
		long offset = (long) sampleNumber * (long) BYTES_PER_VALUE;
		try {
			file.read(buffer, offset);
		} catch (IOException e) {
			NotificationsController.showCriticalFault("Error while reading a value from the cache file at \"" + filePath.toString() + "\"");
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
	 * @param cache                Cache to use, or null to not use a cache.
	 * @param plotMinX             Timestamp at the left edge of the plot.
	 */
	public FloatBuffer getTampstamps(int firstSampleNumber, int lastSampleNumber, Cache cache, long plotMinX) {
		
		FloatBuffer buffer = Buffers.newDirectFloatBuffer(lastSampleNumber - firstSampleNumber + 1);
		
		// if using a cache, update it and provide from the cache
		if(cache != null) {
			cache.update(firstSampleNumber, lastSampleNumber);
			for(int i = firstSampleNumber; i <= lastSampleNumber; i++)
				buffer.put(cache.cacheLongs.get(i - cache.startOfCache) - plotMinX);
			buffer.rewind();
			return buffer;
		}
		
		// if not using a cache, provide it from the file and/or memory
		int firstSlot = firstSampleNumber / SLOT_SIZE;
		int lastSlot  = lastSampleNumber  / SLOT_SIZE;
		int start = firstSampleNumber;
		int end   = lastSampleNumber;
		for(int slotN = firstSlot; slotN <= lastSlot; slotN++) {
			int valueCount = Integer.min(end - start + 1, SLOT_SIZE - (start % SLOT_SIZE));
			int byteCount  = valueCount * BYTES_PER_VALUE;
			long[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
			if(!slot[slotN].flushing && slot[slotN].inRam) {
				// fill buffer from slot in memory
				int offset = start % SLOT_SIZE;
				for(int i = offset; i < offset + valueCount; i++)
					buffer.put(array[i] - plotMinX);
				start += valueCount;
			} else {
				// fill cache from slot on disk
				while(slot[slotN].flushing);
				long offset = (long) start * (long) BYTES_PER_VALUE;
				try {
					ByteBuffer temp = Buffers.newDirectByteBuffer(byteCount);
					file.read(temp, offset);
					for(int i = 0; i < valueCount; i++)
						buffer.put(temp.getLong(i * BYTES_PER_VALUE) - plotMinX);
				} catch (IOException e) {
					NotificationsController.showCriticalFault("Error while reading a value from the cache file at \"" + filePath.toString() + "\"");
					e.printStackTrace();
				}
				start += valueCount;
			}
		}
		buffer.rewind();
		return buffer;
		
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
			NotificationsController.showCriticalFault("Unable to clear the cache file at \"" + filePath.toString() + "\"");
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
			NotificationsController.showCriticalFault("Unable to delete the cache file at \"" + filePath.toString() + "\"");
			e.printStackTrace();
		}
		
	}
	
	public class Cache {
		
		private int cacheSize = 1024;
		private ByteBuffer cacheBytes = Buffers.newDirectByteBuffer(cacheSize * BYTES_PER_VALUE);
		private LongBuffer cacheLongs = cacheBytes.asLongBuffer();
		private int startOfCache = 0;
		private int cachedCount = 0;
		
		/**
		 * Updates the contents of the cache.
		 * The requested range MUST be small enough to fit inside this cache!
		 * 
		 * @param firstSampleNumber    Start of range, inclusive. This MUST be a valid sample number.
		 * @param lastSampleNumber     End of range, inclusive. This MUST be a valid sample number.
		 */
		public void update(int firstSampleNumber, int lastSampleNumber) {
			
			// grow the cache to 300% if it can't hold 200% the requested range
			if(cacheSize < 2 * (lastSampleNumber - firstSampleNumber + 1)) {
				cacheSize = 3 * (lastSampleNumber - firstSampleNumber + 1);
				cacheBytes = Buffers.newDirectByteBuffer(cacheSize * BYTES_PER_VALUE);
				cacheLongs = cacheBytes.asLongBuffer();
				startOfCache = 0;
				cachedCount = 0;
			}
			
			// flush cache if necessary
			if(firstSampleNumber < startOfCache || lastSampleNumber >= startOfCache + cacheSize) {
				startOfCache = firstSampleNumber - (cacheSize / 3); // reserve a third of the cache before the currently requested range, so the user can rewind a little without needing to flush the cache
				if(startOfCache < 0)
					startOfCache = 0;
				cachedCount = 0;
				// try to fill the new cache with adjacent samples too
				firstSampleNumber = startOfCache;
				lastSampleNumber = startOfCache + cacheSize - 1;
				int max = connection.getSampleCount() - 1;
				if(lastSampleNumber > max)
					lastSampleNumber = max;
			}
			
			// new range starts before cached range
			if(firstSampleNumber < startOfCache) {
				int start = firstSampleNumber;
				int end   = startOfCache - 1;
				
				int firstSlot = start / SLOT_SIZE;
				int lastSlot  = end   / SLOT_SIZE;
				for(int slotN = firstSlot; slotN <= lastSlot; slotN++) {
					long[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
					if(!slot[slotN].flushing && slot[slotN].inRam) {
						// fill cache from slot in memory
						int offset = start % SLOT_SIZE;
						int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
						cacheLongs.position(start - startOfCache);
						cacheLongs.put(array, offset, length);
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
							NotificationsController.showCriticalFault("Error while reading a value from the cache file at \"" + filePath.toString() + "\"");
							e.printStackTrace();
						}
						start += byteCount / BYTES_PER_VALUE;
					}
				}
				
				startOfCache = firstSampleNumber;
				cachedCount += end - firstSampleNumber + 1;
			}
			
			// new range ends after cached range
			if(lastSampleNumber > startOfCache + cachedCount - 1) {
				int start = startOfCache + cachedCount;
				int end   = lastSampleNumber;
				
				int slotStart = start / SLOT_SIZE;
				int slotEnd   = end   / SLOT_SIZE;
				for(int slotN = slotStart; slotN <= slotEnd; slotN++) {
					long[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
					if(!slot[slotN].flushing && slot[slotN].inRam) {
						// fill cache from slot in memory
						int offset = start % SLOT_SIZE;
						int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
						cacheLongs.position(start - startOfCache);
						cacheLongs.put(array, offset, length);
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
							NotificationsController.showCriticalFault("Error while reading a value from the cache file at \"" + filePath.toString() + "\"");
							e.printStackTrace();
						}
						buffer.rewind();
						cacheLongs.position(start - startOfCache);
						cacheLongs.put(buffer.asLongBuffer());
						start += byteCount / BYTES_PER_VALUE;
					}
				}
				
				cachedCount += lastSampleNumber - (startOfCache + cachedCount) + 1;
			}
			
		}
		
	}

	private class Slot {
		
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		private volatile long[] value = new long[SLOT_SIZE];
		
		public void flushToDisk(int slotN) {
			
			// in stress test mode just delete the data
			// because even high-end SSDs will become the bottleneck
			if(connection.mode == ConnectionTelemetry.Mode.STRESS_TEST) {
				slot[slotN].inRam = false;
				slot[slotN].value = null;
				slot[slotN].flushing = false;
				return;
			}
			
			// move this slot to disk
			flushing = true;
			
			new Thread(() -> {
				try {
					ByteBuffer buffer = Buffers.newDirectByteBuffer(SLOT_SIZE * BYTES_PER_VALUE);
					LongBuffer temp = buffer.asLongBuffer();
					temp.put(value);
					long offset = (long) slotN * (long) SLOT_SIZE * (long) BYTES_PER_VALUE;
					file.write(buffer, offset);
					file.force(true);
					
					inRam = false;
					value = null;
					flushing = false;
				} catch(Exception e) {
					NotificationsController.showCriticalFault("Error while moving values to the cache file at \"" + filePath.toString() + "\"");
					e.printStackTrace();
				}
			}).start();
			
		}
		
	}
	
}
