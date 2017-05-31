package lsi.sling.FragmentHandling;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;

/**
 * This class represents the clustered MS2Fragments generated by each LCPeakCluster
 */
public class LCMS2Cluster {

    private ArrayList<LCMS2Fragment> fragments;
    private double medianMZ;
    private double medianRT;

    /**
     * Creates an LCMS2Cluster with the input list of MS2Fragments. The intention is for the input list to be generated
     * by the DBScan clustering algorithm (in the LCPeakCluster class).
     * @param inputFragments The input list of MS2Fragments which all belong to the same cluster
     */
    public LCMS2Cluster(ArrayList<LCMS2Fragment> inputFragments){
        fragments = inputFragments;
        medianMZ = new DescriptiveStatistics(inputFragments.stream().mapToDouble(LCMS2Fragment::getMZ).toArray()).getPercentile(50);
        medianRT = new DescriptiveStatistics(inputFragments.stream().mapToDouble(LCMS2Fragment::getRT).toArray()).getPercentile(50);
        //medianMZ = inputFragments.stream().mapToDouble(LCMS2Fragment::getMZ).summaryStatistics().getAverage();
        //medianRT = inputFragments.stream().mapToDouble(LCMS2Fragment::getRT).summaryStatistics().getAverage();
    }

    public ArrayList<LCMS2Fragment> getLCFragments(){
        return fragments;
    }

    double getMZ(){
        return medianMZ;
    }

    double getRT(){
        return medianRT;
    }
}
