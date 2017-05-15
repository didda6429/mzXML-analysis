package lsi.sling;

/**
 * This class represents an MS2 peak. it is analagous to an MS2 'LocalPeak' object
 *
 * @author Adithya Diddapur
 */
public class MS2Fragment {

    private double intensity;
    private double MZ;
    private double RT;

    /**
     * Creates an MS2 Peak object with it's intensity, m/z, and RT. The precursor information is not stored here because it
     * is intended for this object to be stored within an instance of the LocalPeak class which will contain that information.
     * @param inten The intensity of this MS2Fragment
     * @param mz The m/z of this MS2Fragment (not of the precursor)
     * @param rt The RT of ths MS2Fragment
     */
    public MS2Fragment(double inten, double mz, double rt){
        intensity = inten;
        MZ = mz;
        RT = rt;
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public double getMZ() {
        return MZ;
    }

    public void setMZ(double MZ) {
        this.MZ = MZ;
    }

    public double getRT() {
        return RT;
    }

    public void setRT(double RT) {
        this.RT = RT;
    }
}
