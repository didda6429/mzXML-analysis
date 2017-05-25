package lsi.sling.FragmentHandling;

import org.apache.commons.math3.ml.clustering.Clusterable;

/**
 * This class essentially acts as a wrapper class for the MS2Cluster class. It provides an interface for the DBSCAN
 * clustering algorithm to access the 'aligned' MZ and
 */
public class AlignedFragmentCluster implements Clusterable {

    private double alignedMZ;
    private double alignedRT;
    private MS2Cluster fragmentCluster;

    /**
     * Creates an AlignedFragmentCluster object given it's underlying MS2Cluster object.
     * @param inputCluster The MS2Cluster object
     * @param rtDifference The RT difference between the over-arching PeakCluster and AlignedPeakCluster (which contain
     *                     the input MS2Cluster)
     */
    public AlignedFragmentCluster(MS2Cluster inputCluster, double rtDifference){
        fragmentCluster = inputCluster;
        //subtraction to maintain ordering?
        alignedMZ = inputCluster.getMeanMZ();
        alignedRT = inputCluster.getMeanRT() - rtDifference;
    }

    public double getAlignedMZ() {
        return alignedMZ;
    }

    public double getAlignedRT() {
        return alignedRT;
    }

    public MS2Cluster getFragmentCluster() {
        return fragmentCluster;
    }

    /**
     * Required method by the Clusterable interface. The fragments are only intended to be clustered by M/Z (1 dimensionaly)
     * so the second value in the array is always 0 and the first value is the M/Z
     * @return An array containing the M/Z value and 0
     */
    @Override
    public double[] getPoint() {
        return new double[]{alignedMZ, 0};
    }
}
