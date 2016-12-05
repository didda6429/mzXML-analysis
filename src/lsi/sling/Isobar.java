package lsi.sling;

import java.util.ArrayList;
import java.lang.Math;

/**
 * This class represents a isobar within a chromatogram. In normal usage, this should only ever be called from within
 * the Chromatogram class.
 * NOTE: This class is VERY similar to the Chromatogram class but contains much less functionality (The assumption
 * for this class is that any necessary computation is done in the Chromatogram class)
 * @author Adithya Diddapur
 */
public class Isobar {

    private ArrayList<LocalPeak> intensityScanPairs;
    private double meanMZ;
    private double tolerance;
    private double threshold; //used to define noise to signal ratio
    private int maxIntensityIndex; //index of the max peak within the ArrayList (max intensity)
    private double maxIntensityRT;
    private double maxIntensity;
    private double[] smoothData;
    private boolean inCluster;
    private boolean isValid;

    /**
     * This constructor creates Isobar objects which represent isobars in the origin Chromatogram. By definition
     * these isobars are subsets of the original chromatograms. In normal use, this should only ever be called from within
     * the Chromatogram constructor.
     * @param scanPairs The ArrayList of LocalPeak objects which are used in this isobar
     * @param mz the m/z value (passed from the parent Chromatogram)
     * @param tol the m/z tolerance used to extract the peak (passed from the parent Chromatogram)
     * @param thresh the threshold used to extract the peak (passed from the parent Chromatogram)
     * @param smooth the smoothed data points corresponding to this isobar
     * @param cluster flag representing whether or not it is part of a cluster (passed from the parent Chromatogram)
     */
    public Isobar(ArrayList<LocalPeak> scanPairs, double mz, double tol, double thresh, double[] smooth, boolean cluster){
        intensityScanPairs = scanPairs;
        meanMZ = mz;
        tolerance = tol;
        threshold = thresh;
        int index = 0;
        double maxInt = 0;
        for(int i=0; i<scanPairs.size(); i++){
            if(scanPairs.get(i).getIntensity()>maxInt){
                index = i;
                maxInt = scanPairs.get(i).getIntensity();
            }
        }
        maxIntensityIndex = index;
        maxIntensityRT = scanPairs.get(index).getRT();
        maxIntensity = scanPairs.get(index).getIntensity();
        smoothData = smooth;
        inCluster = cluster;
        isValid = calculateIsValid(5); //play around with this constant
    }

    /**
     * Method to determine if a isobar contains a valid peak. It does this by comparing the maxima with the minimum value
     * of the endpoints to (try to) determine if the peak is just noise or not.
     * @param threshold
     * @return
     */
    private boolean calculateIsValid(double threshold){
        double max = maxIntensity;
        double min = Math.min(intensityScanPairs.get(0).getIntensity(),intensityScanPairs.get(intensityScanPairs.size()-1).getIntensity());
        double temp = max/min;
        if(temp>threshold){
            return true;
        } else {
            return false;
        }
    }

    public ArrayList<LocalPeak> getIntensityScanPairs() { return intensityScanPairs;}

    public double getMeanMZ() { return meanMZ;}

    public double getTolerance() { return tolerance;}

    public double getThreshold() { return threshold;}

    public int getMaxIntensityIndex() { return maxIntensityIndex;}

    public double getMaxIntensityRT() { return maxIntensityRT;}

    public double getMaxIntensity() { return maxIntensity;}

    public double[] getSmoothData() { return smoothData;}

    public boolean getInCluster() { return inCluster;}
}
