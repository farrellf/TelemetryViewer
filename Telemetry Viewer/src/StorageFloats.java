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
		
		if(valueN == 0) {
			slot[slotN] = new Slot();
			if(slotN > 1)
				slot[slotN - 2].flushToDisk(slotN - 2);
		}
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

		if(slot[slotN] == null) {
			slot[slotN] = new Slot();
			if(slotN > 1)
				slot[slotN - 2].flushToDisk(slotN - 2);
		}
		
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

		int blockN = firstSampleNumber / BLOCK_SIZE;

		minimumValueInBlock[blockN] = minValue;
		maximumValueInBlock[blockN] = maxValue;
		
	}
	
	/**
	 * @return    A place to cache samples.
	 */
	public Cache createCache() {
		
		return new Cache();
		
	}
	
	/**
	 * Gets one sample, as a float.
	 * 
	 * @param sampleNumber    Which sample number to read. This MUST be a valid sample number.
	 * @param cache           Place to cache samples.
	 * @return                The corresponding value.
	 */
	public float getSample(int sampleNumber, Cache cache) {
		
		cache.update(sampleNumber, sampleNumber);
		cache.cacheFloats.position(sampleNumber - cache.startOfCache);
		return cache.cacheFloats.get();
		
	}
	
	/**
	 * Gets a sequence of samples, as a FloatBuffer.
	 * 
	 * @param firstSampleNumber    First sample number to obtain, inclusive.
	 * @param lastSampleNumber     Last sample number to obtain, inclusive.
	 * @param cache                Place to cache samples.
	 * @return                     The samples, as a FloatBuffer, positioned at the first sample number.
	 */
	public FloatBuffer getSamplesBuffer(int firstSampleNumber, int lastSampleNumber, Cache cache) {

		cache.update(firstSampleNumber, lastSampleNumber);
		cache.cacheFloats.position(firstSampleNumber - cache.startOfCache);
		return cache.cacheFloats.slice(); // must slice, to prevent the position() from changing if getSample() or getSamplesBuffer() is called again before "using" this buffer
		
	}
	
	/**
	 * Gets the minimum and maximum of a sequence of samples.
	 * 
	 * @param firstSampleNumber    First sample number to consider, inclusive.
	 * @param lastSampleNumber     Last sample number to consider, inclusive.
	 * @param cache                Place to cache samples.
	 * @return                     A MinMax object, which has "min" and "max" fields.
	 */
	public MinMax getRange(int firstSampleNumber, int lastSampleNumber, Cache cache) {
		
		// save the cache's current position, and restore it when done
		int oldPosition = cache.cacheFloats.position();
		
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
					float value = getSample(sampleN, cache);
					if(value < range.min)
						range.min = value;
					if(value > range.max)
						range.max = value;
				}
			}
		}
		
		
		// restore cache position
		cache.cacheFloats.position(oldPosition);
		
		return range;
		
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
		private FloatBuffer cacheFloats = cacheBytes.asFloatBuffer();
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
				cacheFloats = cacheBytes.asFloatBuffer();
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
					float[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
					if(!slot[slotN].flushing && slot[slotN].inRam) {
						// fill cache from slot in memory
						int offset = start % SLOT_SIZE;
						int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
						cacheFloats.position(start - startOfCache);
						cacheFloats.put(array, offset, length);
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
					float[] array = slot[slotN].value; // save a reference to the array BEFORE checking if the array is in memory, to prevent a race condition
					if(!slot[slotN].flushing && slot[slotN].inRam) {
						// fill cache from slot in memory
						int offset = start % SLOT_SIZE;
						int length = Integer.min(end - start + 1, SLOT_SIZE - offset);
						cacheFloats.position(start - startOfCache);
						cacheFloats.put(array, offset, length);
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
				
				cachedCount += lastSampleNumber - (startOfCache + cachedCount) + 1;
			}
			
		}
		
	}

	private class Slot {
		
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		private volatile float[] value = new float[SLOT_SIZE];
		
		/**
		 * Moves this slot's data from memory to disk.
		 * 
		 * @param slotN    Which slot number this object represents.
		 */
		public void flushToDisk(int slotN) {
						
			// in stress test mode just delete the data
			// because even high-end SSDs will become the bottleneck
			if(connection.mode == ConnectionTelemetry.Mode.STRESS_TEST) {
				inRam = false;
				value = null;
				flushing = false;
				return;
			}
			
			// move this slot to disk
			flushing = true;
			
			new Thread(() -> {
				try {
					ByteBuffer buffer = Buffers.newDirectByteBuffer(SLOT_SIZE * BYTES_PER_VALUE);
					FloatBuffer temp = buffer.asFloatBuffer();
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
