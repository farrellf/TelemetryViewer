/**
 * The Dataset class provides four ways to get samples:
 * 
 * getSample()       to get one sample, as a float.                                   Helpful when you need one sample.
 * getSamplesArray() to get a series of samples, as a float[].                        Helpful when you need a series of samples.
 * getSamples()      to get a series of samples, as a Samples object (THIS CLASS!)    Helpful when you need a series of samples, and their min/max.
 * getGLdataset()    to get a series of samples, as a SamplesGL object.               Helpful when you need a series of samples, and will render them with OpenGL, like a line chart.
 */
public class Samples {
	
	public float[] buffer;
	public float min;
	public float max;
	
	public float[] color;
	public String name;
	public String unit;

}
