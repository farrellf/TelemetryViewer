import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import com.jogamp.common.nio.Buffers;

public class StorageFloats {
	
	// floats are buffered into "slots" which each hold 1M values.
	// to speed up min/max calculations, the min and max value is tracked for smaller "blocks" of 1K values.
	public static final int BLOCK_SIZE = 1024; // 1K
	public static final int SLOT_SIZE  = 1048576; // 1M
	private final int MAX_SAMPLE_NUMBER = Integer.MAX_VALUE;
	private final int BYTES_PER_VALUE = 4; // 4 bytes per float
	
	private volatile Slot[] slot                 = new Slot [MAX_SAMPLE_NUMBER / SLOT_SIZE  + 1]; // +1 to round up
	private volatile float[] minimumValueInBlock = new float[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	private volatile float[] maximumValueInBlock = new float[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
	
	// older slots can be swapped to disk when memory runs low.
	private final Path filePath;
	private final FileChannel file;
	
	// a cache is used to speed up calls to getRange()
	private final int CACHE_SIZE = 3 * SLOT_SIZE;
	private ByteBuffer cacheBytes = Buffers.newDirectByteBuffer(CACHE_SIZE * BYTES_PER_VALUE);
	private FloatBuffer cacheFloats = cacheBytes.asFloatBuffer();
	private int startOfCache = 0;
	private int firstCachedSampleNumber = startOfCache - 1;
	private int lastCachedSampleNumber  = startOfCache - 1;
	
	private ConnectionTelemetry connection;

	/**
	 * Prepares storage space for a sequence of floats.
	 * 
	 * @param connection    The corresponding connection.
	 */
	public StorageFloats(ConnectionTelemetry connection) {
		
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
	 * Sets a value, and updates the min/max records.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param value    The new value.
	 */
	public void setValue(int sampleNumber, float value) {
		
		int slotN  = sampleNumber / SLOT_SIZE;
		int valueN = sampleNumber % SLOT_SIZE;
		int blockN = sampleNumber / BLOCK_SIZE;
		
		if(valueN == 0)
			slot[slotN] = new Slot();
		slot[slotN].value[valueN] = value;
		
		if(sampleNumber % BLOCK_SIZE == 0) {
			minimumValueInBlock[blockN] = value;
			maximumValueInBlock[blockN] = value;
		} else {
			if(value < minimumValueInBlock[blockN])
				minimumValueInBlock[blockN] = value;
			if(value > maximumValueInBlock[blockN])
				maximumValueInBlock[blockN] = value;
		}
		
	}
	
	/**
	 * Obtains the samples buffer so that multiple Parser threads may write directly into it (in parallel.)
	 * 
	 * @param sampleNumber    The sample number whose buffer is wanted.
	 * @return                Corresponding buffer.
	 */
	public float[] getSlot(int sampleNumber) {
		
		int slotN = sampleNumber / SLOT_SIZE;

		if(slot[slotN] == null)
			slot[slotN] = new Slot();
		
		return slot[slotN].value;
		
	}
	
	/**
	 * Specifies the minimum and maximum values found in a block.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param firstSampleNumber    First sample number of the block.
	 * @param minValue             Minimum value in the block.
	 * @param maxValue             Maximum value in the block.
	 */
	public void setRangeOfBlock(int firstSampleNumber, float minValue, float maxValue) {

		int blockN      = firstSampleNumber / BLOCK_SIZE;

		minimumValueInBlock[blockN] = minValue;
		maximumValueInBlock[blockN] = maxValue;
		
	}
	
	/**
	 * Reads one value.
	 * This method is thread-safe.
	 * 
	 * @param sampleNumber    Which sample number to read. This MUST be a valid sample number.
	 * @return                The corresponding value.
	 */
	public float getValue(int sampleNumber) {
		
		int slotN  = sampleNumber / SLOT_SIZE;
		int valueN = sampleNumber % SLOT_SIZE;
		
		// read from memory if possible
		if(!slot[slotN].flushing && slot[slotN].inRam)
			return slot[slotN].value[valueN];
		
		// read from cache if possible
		updateCacheIfAppropriate(sampleNumber, sampleNumber);
		if(sampleNumber >= firstCachedSampleNumber && sampleNumber <= lastCachedSampleNumber) {
			return cacheFloats.get(sampleNumber - startOfCache);
		}
		
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
		return buffer.getFloat(0);
		
	}
	
	/**
	 * Reads a sequence of values.
	 * This method is NOT reentrant! Only one thread may call this at a time.
	 * 
	 * @param firstSampleNumber    The first sample number, inclusive. This MUST be a valid sample number.
	 * @param lastSampleNumber     The last sample number, inclusive. This MUST be a valid sample number.
	 * @param calculateMinMix      If true, also calculate the minimum and maximum value in this range.
	 */
	public Values getValues(int firstSampleNumber, int lastSampleNumber, boolean calculateMinMix) {
		
		updateCacheIfAppropriate(firstSampleNumber, lastSampleNumber);
		
		// if the entire range is cached, provide it from the cache
		if(firstSampleNumber >= firstCachedSampleNumber && lastSampleNumber <= lastCachedSampleNumber) {
			cacheFloats.position(firstSampleNumber - startOfCache);
			if(calculateMinMix) {
				MinMax mm = getRange(firstSampleNumber, lastSampleNumber);
				return new Values(cacheFloats.slice(), mm.min, mm.max); // FIXME shouldn't need to slice if OpenGL.*() doesn't set positions!
			} else {
				return new Values(cacheFloats.slice(), 0, 0);
			}
		}
		
		// if the entire range is not cached, provide it from the file and/or memory
		ByteBuffer buffer = Buffers.newDirectByteBuffer((lastSampleNumber - firstSampleNumber + 1) * BYTES_PER_VALUE);
		FloatBuffer floats = buffer.asFloatBuffer();
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
				floats.put(slot[slotN].value, offset, valueCount);
				buffer.position(buffer.position() + byteCount);
				start += valueCount;
			} else {
				// fill cache from slot on disk
				while(slot[slotN].flushing);
				long offset = (long) start * (long) BYTES_PER_VALUE;
				try {
					ByteBuffer temp = buffer.slice();
					temp.limit(temp.position() + byteCount);
					file.read(temp, offset);
				} catch (IOException e) {
					NotificationsController.showCriticalFault("Error while reading a value from the cache file at \"" + filePath.toString() + "\"");
					e.printStackTrace();
				}
				floats.position(floats.position() + valueCount);
				buffer.position(buffer.position() + byteCount);
				start += valueCount;
			}
		}
		floats.rewind();
		if(calculateMinMix) {
			MinMax mm = getRange(firstSampleNumber, lastSampleNumber);
			return new Values(floats, mm.min, mm.max);
		} else {
			return new Values(floats, 0, 0);
		}
		
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
					cacheFloats.position(start - startOfCache);
					cacheFloats.put(slot[slotN].value, offset, length);
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
					cacheFloats.position(start - startOfCache);
					cacheFloats.put(slot[slotN].value, offset, length);
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
					cacheFloats.position(start - startOfCache);
					cacheFloats.put(buffer.asFloatBuffer());
					start += byteCount / BYTES_PER_VALUE;
				}
			}
			
			lastCachedSampleNumber = lastSampleNumber;
			if(firstCachedSampleNumber == -1)
				firstCachedSampleNumber = 0;
		}
		
	}
	
	public MinMax getRange(int firstSampleNumber, int lastSampleNumber) {
		
		MinMax range = new MinMax();
		
		int firstBlock = firstSampleNumber / BLOCK_SIZE;
		int lastBlock = lastSampleNumber / BLOCK_SIZE;
		for(int block = firstBlock; block <= lastBlock; block++) {
			boolean entireBlockInRange = (firstSampleNumber <= block * BLOCK_SIZE) &&
			                             (lastSampleNumber >= (block + 1) * BLOCK_SIZE - 1);
			if(entireBlockInRange) {
				float min = minimumValueInBlock[block];
				float max = maximumValueInBlock[block];
				if(min < range.min)
					range.min = min;
				if(max > range.max)
					range.max = max;
			} else {
				int firstSampleInBlock = Integer.max(firstSampleNumber, block * BLOCK_SIZE);
				int lastSampleInBlock = Integer.min(lastSampleNumber, (block + 1) * BLOCK_SIZE - 1);
				for(int sampleN = firstSampleInBlock; sampleN <= lastSampleInBlock; sampleN++) {
					float value = getValue(sampleN);
					if(value < range.min)
						range.min = value;
					if(value > range.max)
						range.max = value;
				}
			}
		}
		
		return range;
		
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
		
		int lastSlotN = (connection.getSampleCount() - SLOT_SIZE) / SLOT_SIZE; // keep the most recent slot in memory
		
		for(int slotN = 0; slotN < lastSlotN; slotN++) {
			
			// skip this slot if it is already moved to disk
			if(slot[slotN].flushing || !slot[slotN].inRam)
				continue;
			
			// in stress test mode just delete the data
			// because even high-end SSDs will become the bottleneck
			if(connection.mode == ConnectionTelemetry.Mode.STRESS_TEST) {
				slot[slotN].inRam = false;
				slot[slotN].value = null;
				slot[slotN].flushing = false;
				continue;
			}
			
			// move this slot to disk
			final int SLOT_N = slotN;
			slot[SLOT_N].flushing = true;
			
			new Thread(() -> {
				try {
					ByteBuffer buffer = Buffers.newDirectByteBuffer(SLOT_SIZE * BYTES_PER_VALUE);
					FloatBuffer temp = buffer.asFloatBuffer();
					temp.put(slot[SLOT_N].value);
					long offset = (long) SLOT_N * (long) SLOT_SIZE * (long) BYTES_PER_VALUE;
					file.write(buffer, offset);
					file.force(true);
					
					slot[SLOT_N].inRam = false;
					slot[SLOT_N].value = null;
					slot[SLOT_N].flushing = false;
				} catch(Exception e) {
					NotificationsController.showCriticalFault("Error while moving values to the cache file at \"" + filePath.toString() + "\"");
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
			NotificationsController.showCriticalFault("Unable to clear the cache file at \"" + filePath.toString() + "\"");
			e.printStackTrace();
		}
		
		// empty the slots
		slot                = new Slot [MAX_SAMPLE_NUMBER / SLOT_SIZE  + 1]; // +1 to round up
		minimumValueInBlock = new float[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
		maximumValueInBlock = new float[MAX_SAMPLE_NUMBER / BLOCK_SIZE + 1]; // +1 to round up
		
		// flush the cache
		startOfCache = 0;
		firstCachedSampleNumber = -1;
		lastCachedSampleNumber = -1;
		
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

	private class Slot {
		
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		private volatile float[] value = new float[SLOT_SIZE];
		
	}
	
	public class Values {
		
		public FloatBuffer buffer;
		public float min = 0;
		public float max = 0;
		public Values(FloatBuffer buffer, float min, float max) {this.buffer = buffer; this.min = min; this.max = max;}
		
	}
	
	public static class MinMax {
		float min;
		float max;
		public MinMax()                     {this.min = Float.MAX_VALUE; this.max = -Float.MAX_VALUE;}
		public MinMax(float min, float max) {this.min = min;             this.max = max;}
	}
	
}
