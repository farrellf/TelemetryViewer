import java.nio.FloatBuffer;

/**
 * The Dataset class provides four ways to get samples:
 * 
 * getSample()       to get one sample, as a float.                                   Helpful when you need one sample.
 * getSamplesArray() to get a series of samples, as a float[].                        Helpful when you need a series of samples.
 * getSamples()      to get a series of samples, as a Samples object                  Helpful when you need a series of samples, and their min/max.
 * getGLdataset()    to get a series of samples, as a SamplesGL object. (THIS CLASS!) Helpful when you need a series of samples, and will render them with OpenGL, like a line chart.
 */
public class SamplesGL {
		
	public FloatBuffer buffer;
	public int vertexCount;
	public float min;
	public float max;
	
	public float[] color;
	public String name;
	public String unit;
	
}
