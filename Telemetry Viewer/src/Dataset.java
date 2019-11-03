import java.awt.Color;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.jogamp.common.nio.Buffers;

/**
 * Defines all of the details about one CSV column or Binary packet field, stores all of its samples, and provides several ways to get the samples.
 */
public class Dataset {
	
	// constants defined at constructor-time
	final int location;
	final BinaryFieldProcessor processor;
	final String name;
	final Color color;
	final float[] glColor;
	final String unit;
	final float conversionFactorA;
	final float conversionFactorB;
	final float conversionFactor;

	/**
	 * Samples are stored in an array of Slots. Each Slot contains 1M values, and may be flushed to disk if RAM runs low.
	 * Slots are further divided into "blocks" that cache min/max values.
	 */
	private final int BLOCK_SIZE = 1024;
	class Slot {
		
		private String pathOnDisk = "cache/" + this.toString();
		private volatile boolean inRam = true;
		private volatile boolean flushing = false;
		
		private volatile float[] values = new float[DatasetsController.SLOT_SIZE];
		private volatile float[] minimumValueInBlock = new float[DatasetsController.SLOT_SIZE / BLOCK_SIZE];
		private volatile float[] maximumValueInBlock = new float[DatasetsController.SLOT_SIZE / BLOCK_SIZE];
		
		public void moveToDisk() {
			
			if(!inRam || flushing)
				return;
			
			flushing = true;
			
			new Thread(() -> {
				try {
					ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(pathOnDisk));
					stream.writeObject(values);
					stream.writeObject(minimumValueInBlock);
					stream.writeObject(maximumValueInBlock);
					stream.close();
					inRam = false;
					values = null;
					flushing = false;
				} catch(Exception e) {
					e.printStackTrace();
				}
			}).start();
			
		}
		
		private void copyToRam() {
			
			try {
				ObjectInputStream stream = new ObjectInputStream(new FileInputStream(pathOnDisk));
				values = (float[]) stream.readObject();
				minimumValueInBlock = (float[]) stream.readObject();
				maximumValueInBlock = (float[]) stream.readObject();
				stream.close();
				inRam = true;
			} catch(Exception e) {
				e.printStackTrace();
			}
			
		}
		
		public void removeFromDisk() {
			
			try { Files.deleteIfExists(Paths.get(pathOnDisk)); } catch (IOException e) {}
			
		}
		
		public void setValue(int index, float value) {
			
			if(!inRam)
				copyToRam();
			values[index] = value;
			
			int blockN = index / BLOCK_SIZE;
			if(index % BLOCK_SIZE == 0) {
				minimumValueInBlock[blockN] = value;
				maximumValueInBlock[blockN] = value;
			} else {
				if(value < minimumValueInBlock[blockN])
					minimumValueInBlock[blockN] = value;
				if(value > maximumValueInBlock[blockN])
					maximumValueInBlock[blockN] = value;
			}
			
		}
		
		public float getValue(int index) {
			
			if(!inRam)
				copyToRam();
			return values[index];
			
		}
		
		public float getMinimumInBlock(int blockIndex) {
			
			if(!inRam)
				copyToRam();
			return minimumValueInBlock[blockIndex];
			
		}
		
		public float getMaximumInBlock(int blockIndex) {
			
			if(!inRam)
				copyToRam();
			return maximumValueInBlock[blockIndex];
			
		}
		
	}
	Slot[] slots;
	
	// bitfields if this dataset represents a bitfield
	boolean isBitfield;
	List<Bitfield> bitfields;
	
	/**
	 * Creates a new object that describes one dataset and stores all of its samples.
	 * 
	 * @param location             CSV column number, or Binary byte offset.
	 * @param processor            Data processor for the raw samples in the Binary data packet. (Ignored in CSV mode.)
	 * @param name                 Descriptive name of what the samples represent.
	 * @param color                Color to use when visualizing the samples.
	 * @param unit                 Descriptive name of how the samples are quantified.
	 * @param conversionFactorA    This many unprocessed LSBs...
	 * @param conversionFactorB    ... equals this many units.
	 */
	public Dataset(int location, BinaryFieldProcessor processor, String name, Color color, String unit, float conversionFactorA, float conversionFactorB) {
		
		this.location          = location;
		this.processor         = processor;
		this.name              = name;
		this.color             = color;
		this.glColor           = new float[] {color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f};
		this.unit              = unit;
		this.conversionFactorA = conversionFactorA;
		this.conversionFactorB = conversionFactorB;
		this.conversionFactor  = conversionFactorB / conversionFactorA;
		this.isBitfield        = false;
		
		slots = new Slot[DatasetsController.SLOT_COUNT];
		
	}
	
	/**
	 * Configures this Dataset as a bitfield.
	 * 
	 * @param bitfields    A List of Bitfield objects that describe each bitfield in this Dataset.
	 */
	public void setBitfields(List<Bitfield> bitfields) {
		
		isBitfield = true;
		this.bitfields = bitfields;
		
	}
	
	/**
	 * Checks a range in this dataset for any bitfield events.
	 * 
	 * @param events        The object to append any events to.
	 * @param startIndex    The first sample number (inclusive) to check.
	 * @param endIndex      The last sample number (inclusive) to check. This must be > startIndex.
	 */
	public void appendBitfieldEvents(BitfieldEvents events, int startIndex, int endIndex) {
		
		if(!isBitfield)
			return;
		
		if(endIndex <= startIndex)
			return;
		
		int sampleCount = endIndex - startIndex + 1;
		
		for(int i = (startIndex == 0) ? 1 : 0; i < sampleCount; i++) {
			int currentState  = (int) getSample(i + startIndex);
			int previousState = (int) getSample(i - 1 + startIndex);
			
			if(currentState != previousState) {
				for(Bitfield bitfield: bitfields) {
					int currentValue = bitfield.getValue(currentState);
					int previousValue = bitfield.getValue(previousState);
					if(currentValue != previousValue)
						events.add(startIndex + i, bitfield.names[currentValue], glColor);
				}
			}
		}
		
	}
	
	/**
	 * @return    The name of this dataset.
	 */
	@Override public String toString() {
		
		return name;
		
	}
	
	/**
	 * Gets one specific sample.
	 * 
	 * @param index    Which sample to obtain.
	 * @return         The sample.
	 */
	public float getSample(int index) {
		
		int slotNumber = index / DatasetsController.SLOT_SIZE;
		int slotIndex  = index % DatasetsController.SLOT_SIZE;
		return slots[slotNumber].getValue(slotIndex);
		
	}
	
	/**
	 * Gets a String representation of one specific sample.
	 * 
	 * @param index    Which sample to obtain.
	 * @return         The sample formatted as a String.
	 */
	public String getSampleAsString(int index) {
		
		float value = getSample(index);
		
		if(isBitfield)
			return "0b" + String.format("%8s", Integer.toBinaryString((int) value)).replace(' ', '0');
		else
			return ChartUtils.formattedNumber(value, 5) + " " + unit;
		
	}
	
	/**
	 * Gets a series of samples as a float[].
	 * 
	 * @param startIndex    Index of the first sample (inclusive.)
	 * @param endIndex      Index of the last sample (inclusive.)
	 * @return              The samples as a float[].
	 */
	public float[] getSamplesArray(int startIndex, int endIndex) {
		
		int sampleCount = endIndex - startIndex + 1;
		float[] samples = new float[sampleCount];
		
		for(int i = 0; i < sampleCount; i++)
			samples[i] = getSample(i + startIndex);
		
		return samples;
		
	}
	
	/**
	 * Gets a series of samples by updating a existing Samples object:
	 * 
	 * The buffer[] is updated with the requested series of samples.
	 * The min/max values are updated to reflect the new series of samples.
	 * The color/name/unit are updated. 
	 * 
	 * @param startIndex    Index of the first sample (inclusive.)
	 * @param endIndex      Index of the last sample (inclusive.)
	 * @param samples       Samples object to be filled with samples.
	 */
	public void getSamples(int startIndex, int endIndex, Samples samples) {
		
		if(samples.color == null)
			samples.color = new float[4];
		samples.color[0] = (float) color.getRed()   / 255.0f;
		samples.color[1] = (float) color.getGreen() / 255.0f;
		samples.color[2] = (float) color.getBlue()  / 255.0f;
		samples.color[3] = 1.0f;
		
		samples.name = name;
		
		samples.unit = unit;
		
		int sampleCount = endIndex - startIndex + 1;
		
		if(samples.buffer == null || samples.buffer.length != sampleCount)
			samples.buffer = new float[sampleCount];
		
		samples.min = getSample(startIndex);
		samples.max = getSample(startIndex);
		
		for(int i = 0; i < sampleCount; i++) {
			float value = getSample(i + startIndex);
			samples.buffer[i] = value;
			if(value < samples.min) samples.min = value;
			if(value > samples.max) samples.max = value;
		}
		
	}
	
	/**
	 * Gets a series of samples by updating an existing SamplesGL object with an OpenGL-friendly vertex array.
	 * 
	 * The buffer FloatBuffer is updated with the requested series of samples.
	 * The min/max/vertexCount values are updated to reflect the new series of samples.
	 * The color/name/unit are updated. 
	 * 
	 * @param startIndex    The first index (inclusive.)
	 * @param endIndex      The last index (inclusive.)
	 * @param samples       A TimeDomainSamples object to populate. 
	 */
	public void getGLsamples(int startIndex, int endIndex, SamplesGL samples) {
		
		if(samples.glColor == null)
			samples.glColor = glColor;
		
		samples.name = name;
		
		samples.unit = unit;
		
		samples.min = getSample(startIndex);
		samples.max = getSample(startIndex);

		int vertexCount = endIndex - startIndex + 1;
		samples.vertexCount = vertexCount;
		
		if(samples.buffer == null || samples.buffer.capacity() != 2 * vertexCount) {
			samples.buffer = Buffers.newDirectFloatBuffer(2 * vertexCount);
		}
		
		samples.buffer.rewind();
		
		for(int x = startIndex; x <= endIndex; x++) {
			float y = getSample(x);
			samples.buffer.put(x);
			samples.buffer.put(y);
			if(y < samples.min) samples.min = y;
			if(y > samples.max) samples.max = y;
		}
		samples.buffer.rewind();
		
	}
	
	/**
	 * @param value    New raw sample to be converted and then appended to the dataset.
	 */
	public void add(float value) {
		
		value *= conversionFactor;
		
		int currentSize = DatasetsController.getSampleCount();
		int slotNumber = currentSize / DatasetsController.SLOT_SIZE;
		int slotIndex  = currentSize % DatasetsController.SLOT_SIZE;
		if(slotIndex == 0)
			slots[slotNumber] = new Slot();
		slots[slotNumber].setValue(slotIndex, value);
		
	}
	
	/**
	 * @param value    New sample to appended to the dataset. It will be added as-is, not converted.
	 */
	public void addConverted(float value) {
		
		int currentSize = DatasetsController.getSampleCount();
		int slotNumber = currentSize / DatasetsController.SLOT_SIZE;
		int slotIndex  = currentSize % DatasetsController.SLOT_SIZE;
		if(slotIndex == 0)
			slots[slotNumber] = new Slot();
		slots[slotNumber].setValue(slotIndex, value);
		
	}
	
	public MinMax getRange(int firstSampleNumber, int lastSampleNumber) {
		
		MinMax range = new MinMax();
		
		int firstBlock = firstSampleNumber / BLOCK_SIZE;
		int lastBlock = lastSampleNumber / BLOCK_SIZE;
		for(int block = firstBlock; block <= lastBlock; block++) {
			boolean entireBlockInRange = (firstSampleNumber <= block * BLOCK_SIZE) &&
			                             (lastSampleNumber >= (block + 1) * BLOCK_SIZE - 1);
			if(entireBlockInRange) {
				int slotN  = block / (DatasetsController.SLOT_SIZE / BLOCK_SIZE);
				int blockN = block % (DatasetsController.SLOT_SIZE / BLOCK_SIZE);
				float min = slots[slotN].getMinimumInBlock(blockN);
				float max = slots[slotN].getMaximumInBlock(blockN);
				if(min < range.min)
					range.min = min;
				if(max > range.max)
					range.max = max;
			} else {
				int firstSampleInBlock = Integer.max(firstSampleNumber, block * BLOCK_SIZE);
				int lastSampleInBlock = Integer.min(lastSampleNumber, (block + 1) * BLOCK_SIZE - 1);
				for(int sampleN = firstSampleInBlock; sampleN <= lastSampleInBlock; sampleN++) {
					float value = getSample(sampleN);
					if(value < range.min)
						range.min = value;
					if(value > range.max)
						range.max = value;
				}
			}
		}
		
		return range;
		
	}
	
	public FloatBuffer getBuffer(int firstSampleNumber, int lastSampleNumber) {
		
		FloatBuffer buffer = Buffers.newDirectFloatBuffer(lastSampleNumber - firstSampleNumber + 1);
		
		int firstSlot = firstSampleNumber / DatasetsController.SLOT_SIZE;
		int lastSlot = lastSampleNumber / DatasetsController.SLOT_SIZE;
		
		for(int slot = firstSlot; slot <= lastSlot; slot++) {
			
			int slotFirstSampleNumber = slot * DatasetsController.SLOT_SIZE;
			int slotLastSampleNumber = (slot + 1) * DatasetsController.SLOT_SIZE - 1;
			
			int offset = 0;
			if(firstSampleNumber > slotFirstSampleNumber)
				offset = firstSampleNumber - slotFirstSampleNumber;
			
			int length = DatasetsController.SLOT_SIZE;
			if(firstSampleNumber > slotFirstSampleNumber)
				length -= firstSampleNumber - slotFirstSampleNumber;
			if(lastSampleNumber < slotLastSampleNumber)
				length -= slotLastSampleNumber - lastSampleNumber;
			
			slots[slot].getValue(0); // just to ensure it's in RAM
			buffer.put(slots[slot].values, offset, length);
			
		}
		
		buffer.rewind();
		return buffer;
		
	}
	
	public static class MinMax {
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
	}
	
}
