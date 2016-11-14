import java.util.LinkedList;
import java.util.Queue;

/**
 * Auto-scales an axis.
 */
public class AutoScale {
	
	public static final int MODE_STICKY = 0;
	public static final int MODE_EXPONENTIAL = 1;
	
	Queue<Float> minSequence;
	Queue<Float> maxSequence;
	
	float min;
	float max;
	
	int mode;
	int frameCount;
	float hysteresis;
	
	/**
	 * Creates an object that takes the true min/max values for an axis, and outputs new min/max values that re-scale the axis based on some settings.
	 * 
	 * @param mode          MODE_STICKY or MODE_EXPONENTIAL. Sticky mode will only rescale the axis when required, while Exponential mode will constantly rescale to keep things centered.
	 * @param frameCount    How many frames for animating the transition to a new scale. 1 = immediate jump (no animation.)
	 * @param hysteresis    When hitting an existing min/max, re-scale to leave this much extra room (relative to the current range),
	 *                      When 1.5*this far from an existing min/max (relative to the current range), re-scale to leave only this much room.
	 */
	public AutoScale(int mode, int frameCount, float hysteresis) {
		
		minSequence = new LinkedList<Float>();
		maxSequence = new LinkedList<Float>();
		
		minSequence.add(Float.MAX_VALUE);
		maxSequence.add(Float.MIN_VALUE);
		
		min = Float.MAX_VALUE;
		max = Float.MIN_VALUE;
		
		this.mode = mode;
		this.frameCount = frameCount;
		this.hysteresis = hysteresis;
		
	}
	
	/**
	 * Updates state with the current min and max values. This method should be called every frame, before calling getMin() or getMax().
	 * 
	 * @param newMin    Current minimum.
	 * @param newMax    Current maximum.
	 */
	public void update(float newMin, float newMax) {
		
		if(mode == MODE_STICKY) {
		
			float newRange = Math.abs(newMax - newMin);
			float oldMin = minSequence.peek();
			float oldMax = maxSequence.peek();
			
			if(newMin < oldMin) {
				// minimum got more extreme
				minSequence.clear();
				for(int i = 1; i <= frameCount; i++) {
					float adjustment = newRange * (float) (Math.sin(Math.PI/2/frameCount*i) * hysteresis);
					minSequence.add(newMin - adjustment);
				}
			} else if(newMin > oldMin + 1.5f*hysteresis*newRange) {
				// minimum got less extreme
				minSequence.clear();
				for(int i = 1; i <= frameCount; i++) {
					float adjustment = newRange * (float) (Math.sin(Math.PI/2/frameCount*i) * hysteresis);
					minSequence.add(oldMin + adjustment);
				}
			}
			
			if(newMax > oldMax) {
				// maximum got more extreme
				maxSequence.clear();
				for(int i = 1; i <= frameCount; i++) {
					float adjustment = newRange * (float) (Math.sin(Math.PI/2/frameCount*i) * hysteresis);
					maxSequence.add(newMax + adjustment);
				}
			} else if(newMax < oldMax - 1.5f*hysteresis*newRange) {
				// maximum got less extreme
				maxSequence.clear();
				for(int i = 1; i <= frameCount; i++) {
					float adjustment = newRange * (float) (Math.sin(Math.PI/2/frameCount*i) * hysteresis);
					maxSequence.add(oldMax - adjustment);
				}
			}
			
		} else if(mode == MODE_EXPONENTIAL) {
			
			float newRange = Math.abs(newMax - newMin);
			
			if(newMin < min) {
				min = newMin;
			} else {
				float goal = newMin - (newRange * hysteresis);
				float error = goal - min;
				min = min + error * 2 / (float) frameCount;
			}
			
			if(newMax > max) {
				max = newMax;
			} else {
				float goal = newMax + (newRange * hysteresis);
				float error = goal - max;
				max = max + error * 2 / (float) frameCount;
			}
			
		}
		
	}
	
	/**
	 * @return    The auto-scaled minimum value.
	 */
	public float getMin() {
		
		if(mode == MODE_STICKY) {
			
			if(minSequence.size() > 1)
				return minSequence.remove();
			else
				return minSequence.peek();
			
		} else {
			
			return min;
			
		}

	}
	
	/**
	 * @return    The auto-scaled maximum value.
	 */
	public float getMax() {
		
		if(mode == MODE_STICKY) {
			
			if(maxSequence.size() > 1)
				return maxSequence.remove();
			else
				return maxSequence.peek();			
			
		} else {
			
			return max;
			
		}
		
	}

}
