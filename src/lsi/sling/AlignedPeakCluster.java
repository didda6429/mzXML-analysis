package lsi.sling;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class represents an aligned peak-cluster
 * @author Adithya Diddapur
 */
public class AlignedPeakCluster {

    private ArrayList<PeakCluster> possibleClusters;
    private ArrayList<Adduct> adducts;
    private double medianMZ;
    private double medianRT;
    private int charge;
    private double ppm;
    private double RTWindow;

    /**
     * This method is intended to be the access point into this class. The intention is for this method to be called
     * from the main method before handling everything to do with the alignment, including choosing candidate clusters
     * and creating the objects themselves.
     * <br/>
     * Note that for the input parameters, files should not include the file containing the startingPoint cluster.
     * @param files A list of mzXML files to use when looking for candidate clusters
     * @param startingPoint The starting cluster to use
     * @param ppm the m/z tolerance to use (when determining candidate clusters)
     * @param RTWindow the RT tolerance to use (whend determining candidate clusters)
     * @return The AlignedPeakClusters formed around the starting point
     */
    public static AlignedPeakCluster alignPeaks(ArrayList<MzXMLFile> files, PeakCluster startingPoint, double ppm, double RTWindow){
        //sets the inAlignedCluster flag to true so that it isn't reused in any other clusters
        startingPoint.setInAlignedCluster();
        //reads the MZ and RT values from the first PeakCluster and uses those as the initial values of the "population" parameters
        double medianMZ = startingPoint.getChromatograms().get(0).getMeanMZ();
        double medianRT = startingPoint.getChromatograms().get(0).getStartingPointRT();
        ArrayList<PeakCluster> potentialClusters = new ArrayList<>();
        ArrayList<PeakCluster> clustersToAdd = new ArrayList<>();
        clustersToAdd.add(startingPoint); //so that the starting point is included in the list of clusters
        double[] distances;
        Median mzMedian = new Median();
        Median RTMedian = new Median();
        for(MzXMLFile file : files){
            for(PeakCluster cluster : file.getPeakClusters()){
                //collates all potential clusters from the files
                if(checkPeakInTolerance(cluster, medianMZ, medianRT, ppm, RTWindow))
                    potentialClusters.add(cluster);
            }
            //only executes the following logic if there exist candidate clusters (which meet the tolerance requirements)
            if(potentialClusters.size()>0) {
                //finds the cluster closest to the theoretical values based on euclidean distance (with m/z and RT)
                //the closest cluster is the one used for the alignment
                double finalMeanMZ = medianMZ; //used in the lambda expression
                double finalMeanRT = medianRT; //used in the lambda expression
                distances = potentialClusters.stream()
                        .map(cluster -> euclideanDistance(finalMeanMZ, finalMeanRT, cluster.getChromatograms().get(0).getMeanMZ(), cluster.getChromatograms().get(0).getStartingPointRT()))
                        .mapToDouble(x -> x)
                        .toArray();
                List distance = Arrays.asList(distances);
                clustersToAdd.add(potentialClusters.get(distance.indexOf(Collections.min(distance))));
                potentialClusters.clear();
                //iteratively updates the medianMZ and medianRT values as new clusters are added
                medianMZ = mzMedian.evaluate(clustersToAdd.stream().mapToDouble(x -> x.getChromatograms().get(0).getMeanMZ()).toArray());
                medianRT = RTMedian.evaluate(clustersToAdd.stream().mapToDouble(x -> x.getChromatograms().get(0).getStartingPointRT()).toArray());
                //medianMZ = clustersToAdd.stream().mapToDouble(x -> x.getChromatograms().get(0).getMeanMZ()).reduce().getAsDouble();
                //medianRT = clustersToAdd.stream().mapToDouble(x -> x.getChromatograms().get(0).getStartingPointRT()).average().getAsDouble();
            }
        }
        //sets the inAlignedCluster flag to true so that they aren't reused in any other clusters
        for(PeakCluster cluster : clustersToAdd){
            cluster.setInAlignedCluster();
        }
        System.out.println("test");
        return new AlignedPeakCluster(clustersToAdd, medianMZ, medianRT, ppm, RTWindow);
    }

    /**
     * This method checks if a given peakCluster is within the alignment tolerance of the collective m/z and RT values.
     * Note that this method does NOT adjust the values of rollingMZ and rolling RT if the peak is within tolerance. That
     * functionality will be carried out elsewhere
     * @param peakToCheck The PeakCluster object to check
     * @param rollingMZ The "population" m/z value to check against
     * @param rollingRT The "population" RT value to check against
     * @param ppm The m/z ppm tolerance to use
     * @param RTWindow The RT tolerance to use in decimal format (e.g. 30 seconds = 0.5)
     * @return a boolean indicating whether or not it is within the tolerance
     */
    private static boolean checkPeakInTolerance(PeakCluster peakToCheck, double rollingMZ, double rollingRT, double ppm, double RTWindow){
        double mzTolerance = (rollingMZ/1e6)*ppm; //the m/z tolerance to use
        //checks if peakToCheck is within the mzTolerance (from the rolling mean)
        if(Math.abs(peakToCheck.getChromatograms().get(0).getMeanMZ()-rollingMZ) < mzTolerance){
            //checks if peakToCheck is within the RTWindow (from the rolling RT)
            if(Math.abs(peakToCheck.getChromatograms().get(0).getStartingPointRT()-rollingRT) < RTWindow)
                return true; //return true if the conditions are met
        }
        return false; //return false if the conditions aren't met
    }

    /**
     * This methods finds the euclidean distance between the input datapoints. The intended use for this method is in the
     * alignPeaks method where it is used to find the closest cluster to the theoretical position.
     * @param rollingMZ The iteratively updated theoretical m/z
     * @param rollingRT The iteratively updated theoretical RT
     * @param sampleMZ The m/z value of the new cluster being considered
     * @param sampleRT The RT of the new cluster being considered
     * @return The euclidean distance between the two datapoints
     */
    private static double euclideanDistance(double rollingMZ, double rollingRT, double sampleMZ, double sampleRT){
        double a = rollingMZ - sampleMZ;
        double b = rollingRT - sampleRT;
        return Math.sqrt((a*a)+(b*b));
    }

    /**
     * This constructor instantiats an object with the information it receives from the alignPeaks method. It is private
     * because in the intended usage, it is encapsulated by the alignPeaks method which deals with find the correct inputClusters,
     * m/z, and RT values.
     * @param inputClusters The PeakClusters which "form" this AlignedPeakCluster
     * @param mz the mean m/z value as found in alignPeaks
     * @param RT the mean RT as found in alignPeaks
     * @param mzPpm the m/z tolerance used to find the candidate clusters
     * @param RTTolerance the RT tolerance used to find the candidate clusters
     */
    private AlignedPeakCluster(ArrayList<PeakCluster> inputClusters, double mz, double RT, double mzPpm, double RTTolerance) {
        possibleClusters = inputClusters;
        medianMZ = mz;
        medianRT = RT;
        ppm = mzPpm;
        RTWindow = RTTolerance;
        checkPossibleCharges();
    }

    /**
     * This method finds the probable charge of an AlignedPeakCluster based on the list of Clusters. It does this by
     * find the mode charge.
     * <br/>
     * If more than one mode is found, an ArrayIndexOutOfBoundsException is thrown. This behaviour was chosen because the
     * aligned clusters should be the same charge if they really represent the same theoretical cluster.
     */
    private void checkPossibleCharges(){
        //creates an array containing all the charge values of the candidate clusters
        //and finds the statistical mode (which is stored in double[] charges)
        double[] charges = StatUtils.mode(possibleClusters.stream().mapToDouble(PeakCluster::getCharge).toArray());
        //converts the charges to integers
        //note that the rounding is only a formality, but the charges should be integers to start off with
        int[] chargeModes = Arrays.stream(charges).mapToInt(x -> (int)Math.round(x)).toArray();
        //validates that only 1 mode was found
        //if more than 1 was found, an error is thrown --> maybe need to change this behaviour in the future
        if(chargeModes.length > 1){
            throw new ArrayIndexOutOfBoundsException("Too many charges (mode values)");
        }
        //return the mode
        charge = chargeModes[0];
    }

    /**
     * Finds all of the possible adducts for this possible PeakCluster using the list generated by AdductDatabase
     * @param adducts The list of Adducts of the same charge
     */
    void findAdducts(List adducts) {
        double targetMZAbove = medianMZ + (medianMZ /1e6)*ppm;
        double targetMZBelow = medianMZ - (medianMZ /1e6)*ppm;
        ArrayList<Adduct> temp = new ArrayList<>();
        for (Adduct a : (Iterable<Adduct>) adducts) {
            if (a.getResultMZ() < targetMZAbove && a.getResultMZ() > targetMZBelow) {
                temp.add(a);
            }
            if (a.getResultMZ() > targetMZAbove) break;
        }
        this.adducts = temp;
    }

    public int getCharge() {
        return charge;
    }

    public void setPossibleClusters(ArrayList<PeakCluster> possibleClusters) {
        this.possibleClusters = possibleClusters;
    }
}
