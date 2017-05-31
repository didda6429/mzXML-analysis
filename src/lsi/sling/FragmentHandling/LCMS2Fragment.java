package lsi.sling.FragmentHandling;

import org.apache.commons.math3.ml.clustering.Clusterable;

/**
 * This class represents an MS2 peak. it is analagous to an MS2 'LocalPeak' object
 *
 * @author Adithya Diddapur
 */
public class LCMS2Fragment implements Clusterable {

    private double intensity;
    private double MZ;
    private double RT;

    /**
     * Creates an MS2 Peak object with it's intensity, m/z, and RT. The precursor information is not stored here because it
     * is intended for this object to be stored within an instance of the LocalPeak class which will contain that information.
     * @param inten The intensity of this LCMS2Fragment
     * @param mz The m/z of this LCMS2Fragment (not of the precursor)
     * @param rt The RT of ths LCMS2Fragment
     */
    public LCMS2Fragment(double inten, double mz, double rt){
        intensity = inten;
        MZ = mz;
        RT = rt;
    }

    public double getIntensity() {
        return intensity;
    }

    public double getMZ() {
        return MZ;
    }

    public double getRT() {
        return RT;
    }

    /**
     * Implements the required method for the clusterable interface. This is used when clustering the fragments in
     * each LCPeakCluster in order to identify the characteristic fragments.
     * @return An array containing the m/z and rt values of the current LCMS2Fragment
     */
    @Override
    public double[] getPoint() {
        return new double[]{MZ, RT};
    }
}
