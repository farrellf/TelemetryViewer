import java.awt.Color;
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
	final Color  color;
	final String unit;
	final float conversionFactorA;
	final float conversionFactorB;
	final float conversionFactor;

	// samples are stored in an array of float[]'s, each containing 1M floats, and allocated as needed.
	// access to the samples is controlled with an atomic integer, providing lockless concurrency since there is only one writer.
	final int slotSize = (int) Math.pow(2, 20); // 1M floats per slot
	final int slotCount = (Integer.MAX_VALUE / slotSize) + 1;
	float[][] slot;
	
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
		this.unit              = unit;
		this.conversionFactorA = conversionFactorA;
		this.conversionFactorB = conversionFactorB;
		this.conversionFactor  = conversionFactorB / conversionFactorA;
		this.isBitfield        = false;
		
		slot = new float[slotCount][];
		
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
		
		for(int i = 1; i < sampleCount; i++) {
			int slotNumber = (i + startIndex) / slotSize;
			int slotIndex  = (i + startIndex) % slotSize;
			int currentState = (int) slot[slotNumber][slotIndex];
			slotNumber = (i - 1 + startIndex) / slotSize;
			slotIndex  = (i - 1 + startIndex) % slotSize;
			int previousState = (int) slot[slotNumber][slotIndex];
			
			if(currentState != previousState) {
				for(Bitfield bitfield: bitfields) {
					int currentValue = bitfield.getValue(currentState);
					int previousValue = bitfield.getValue(previousState);
					if(currentValue != previousValue)
						events.add(startIndex + i, bitfield.names[currentValue], color);
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
		
		int slotNumber = index / slotSize;
		int slotIndex  = index % slotSize;
		return slot[slotNumber][slotIndex];
		
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
		
		for(int i = 0; i < sampleCount; i++) {
			int slotNumber = (i + startIndex) / slotSize;
			int slotIndex  = (i + startIndex) % slotSize;
			samples[i] = slot[slotNumber][slotIndex];
		}
		
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
		
		samples.min = slot[startIndex / slotSize][startIndex % slotSize];
		samples.max = slot[startIndex / slotSize][startIndex % slotSize];
		
		for(int i = 0; i < sampleCount; i++) {
			int slotNumber = (i + startIndex) / slotSize;
			int slotIndex  = (i + startIndex) % slotSize;
			float value = slot[slotNumber][slotIndex];
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
		
		if(samples.color == null)
			samples.color = new float[4];
		samples.color[0] = (float) color.getRed()   / 255.0f;
		samples.color[1] = (float) color.getGreen() / 255.0f;
		samples.color[2] = (float) color.getBlue()  / 255.0f;
		samples.color[3] = 1.0f;
		
		samples.name = name;
		
		samples.unit = unit;
		
		samples.min = slot[startIndex / slotSize][startIndex % slotSize];
		samples.max = slot[startIndex / slotSize][startIndex % slotSize];

		int vertexCount = endIndex - startIndex + 1;
		samples.vertexCount = vertexCount;
		
		if(samples.buffer == null || samples.buffer.capacity() != 2 * vertexCount) {
			samples.buffer = Buffers.newDirectFloatBuffer(2 * vertexCount);
		}
		
		samples.buffer.rewind();
		
		for(int x = startIndex; x <= endIndex; x++) {
			float y = slot[x / slotSize][x % slotSize];
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
		
		int currentSize = DatasetsController.getSampleCount();
		int slotNumber = currentSize / slotSize;
		int slotIndex  = currentSize % slotSize;
		if(slotIndex == 0)
			slot[slotNumber] = new float[slotSize];
		slot[slotNumber][slotIndex] = value * conversionFactor;
		
	}
	
}
