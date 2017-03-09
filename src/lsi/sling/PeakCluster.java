package lsi.sling;

import com.google.common.collect.ArrayListMultimap;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This Class represents a peak cluster (i.e. a peak and it's isotopes).
 * @author Adithya Diddapur
 */
public class PeakCluster implements Clusterable{

    private final static double NEUTRON_MASS = 1.00866491588;

    private double neutronMassPpmAbove;
    private double neutronMassPpmBelow;
    private ArrayList<Chromatogram> chromatograms; //the individual chromatograms which make up the cluster
    private ArrayList<Chromatogram> tempChroma;
    private int charge; //in normal use, this should only ever be 1 or 2
    private int startingPointIndex;
    private List<Adduct> adductList;
    //determines the m/z window in which to find adducts
    private double targetMZAbove;
    private double targetMZBelow;
    private boolean inAlignedCluster;
    //Used for the calculating the distance during the DBSCAN clustering
    private double normalisedMZ;
    private double normalisedRT;

    /**
     * Creates a new peakcluster from a given starting point. This includes estimating the charge and isotopes. Also, after
     * a chromatogram has been used, it is marked as inPeakCluster in the MzXMLFile
     * @param startingPoint The Chromatogram to use as a starting point
     * @param ppm The precision to use
     * @param mzXMLFile The MzXMLFile to look through for isotopes
     */
    public PeakCluster(Chromatogram startingPoint, double ppm, MzXMLFile mzXMLFile) {
        inAlignedCluster = false;
        adductList = new ArrayList<>();
        chromatograms = new ArrayList<>();
        tempChroma = new ArrayList<>();
        neutronMassPpmAbove = ppmAbove(NEUTRON_MASS, ppm);
        neutronMassPpmBelow = ppmBelow(NEUTRON_MASS, ppm);
        checkCharge(startingPoint, mzXMLFile, 3, ppm); //estimates the charge, considering 3 as the maximum (inclusive)
        checkAboveOrBelow(startingPoint,false, ppm, mzXMLFile); //looks for isotopes below the starting m/z
        for(int i = tempChroma.size(); i>0; i--){
            chromatograms.add(tempChroma.get(i-1));
        }
        startingPointIndex = chromatograms.size();
        chromatograms.add(startingPoint);
        tempChroma.clear();
        checkAboveOrBelow(startingPoint,true, ppm, mzXMLFile); //looks for isotopes above the starting m/z
        for(Chromatogram chromatogram : tempChroma){
            chromatograms.add(chromatogram);
        }
        targetMZAbove = ppmAbove(chromatograms.get(startingPointIndex).getMeanMZ(), ppm);
        targetMZBelow = ppmBelow(chromatograms.get(startingPointIndex).getMeanMZ(), ppm);
    }

    public List<Adduct> getAdductList() {
        return adductList;
    }

    /**
     * This method recursively looks for the chromatograms adjacent to the starting point which can then be arranged
     * into the Peak Cluster. Currently, it looks for chromatograms within 0.05 Da. If multiple chromatograms are found,
     * the one closest to the expected theoretical value is used.
     * @param previous The starting point used in the previous iteration of the recursive loop
     * @param above Whether to look above (high mz) or below (lower mz) the starting point. A value of true is interpreted
     *              as look above whilst a value of false is interpreted as look below
     * @return If the operation completed succesfully it returns 1, otherwise (if more than 1 usable isotope is found in
     * the same place --> should not be possible which means it's a bug) it returns 2
     * @throws IllegalArgumentException if the number of valid peaks it finds is nonsensical (negative or not a number or something silly)
     */
    private int checkAboveOrBelow(Chromatogram previous, boolean above, double ppm, MzXMLFile mzXMLFile){
        double RT = previous.getStartingPointRT();
        double inten = previous.getStartingPointIntensity();
        double mz = previous.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList<>();
        //This loop looks for Chromatograms within the m/z value which correlate to the recursive starting point
        for (Chromatogram chromatogram : mzXMLFile.chromatograms){
            if(!chromatogram.equals(previous)) {
                if (Math.abs(Math.abs(mz - chromatogram.getMeanMZ()) - neutronMassPpmAbove /charge) < 0.05 && recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                    if (correlateChromatograms(previous, chromatogram) > 0.8) { //uses the correlation function below to determine isobars. The constant still needs to be adjusted.
                        temp.add(chromatogram);
                    }
                }
            }
        }
        if(temp.size()==1){ //if only one possibility is found add it to tempChroma and repeat the recursive loop with it as a starting point
            mzXMLFile.chromatograms.get(mzXMLFile.chromatograms.indexOf(temp.get(0))).setInCluster();
            tempChroma.add(temp.get(0));
            return checkAboveOrBelow(temp.get(0), above, ppm, mzXMLFile);
        } else if(temp.size()==0) { //if no possibilities are found, return 1
            return 1;
        } else if(temp.size()>1) { //if more than one possibility is found, use the one closest to the exact theoretical value
            double minDistance = Math.abs(temp.get(0).getMeanMZ()-(previous.getMeanMZ()+NEUTRON_MASS));
            int index = 0;
            for(int i=1; i<temp.size(); i++){
                if(Math.abs(temp.get(0).getMeanMZ()-(previous.getMeanMZ()+NEUTRON_MASS))<minDistance){
                    index = i;
                }
            }
            mzXMLFile.chromatograms.get(mzXMLFile.chromatograms.indexOf(temp.get(index))).setInCluster();
            tempChroma.add(temp.get(index));
            return checkAboveOrBelow(temp.get(0), above, ppm, mzXMLFile);
        } else {
            throw new IllegalArgumentException(); //thrown if temp.size()<1. If this is thrown there is a big problem
        }
    }

    /**
     * Calculates the correlation between two chromatograms. If the ranges of the retention times aren't equal (and they
     * usually aren't), only the overlapping datapoints are used to calculate the correlation. This method calculates the
     * pearson correlation coefficient through the apache commons math library.
     * @param a The first chromatogram
     * @param b The second chromatogram
     * @return The correlation coefficient between the two chromatograms
     */
    private static double correlateChromatograms(Chromatogram a, Chromatogram b){
        //finds the overlapping datapoints to correlate
        double minPoint = Math.max(a.getIntensityScanPairs().get(0).getRT(),b.getIntensityScanPairs().get(0).getRT());
        double maxPoint = Math.min(a.getIntensityScanPairs().get(a.getIntensityScanPairs().size()-1).getRT(),b.getIntensityScanPairs().get(b.getIntensityScanPairs().size()-1).getRT());
        ArrayList<Double> aIntensities = new ArrayList();
        for(int i=0; i<a.getIntensityScanPairs().size(); i++){
            if(a.getIntensityScanPairs().get(i).getRT()>=minPoint && a.getIntensityScanPairs().get(i).getRT()<=maxPoint){
                aIntensities.add(a.getIntensityScanPairs().get(i).getIntensity());
            }
        }
        ArrayList<Double> bIntensities = new ArrayList();
        for(int i=0; i<b.getIntensityScanPairs().size(); i++){
            if(b.getIntensityScanPairs().get(i).getRT()>=minPoint && b.getIntensityScanPairs().get(i).getRT()<=maxPoint){
                bIntensities.add(b.getIntensityScanPairs().get(i).getIntensity());
            }
        }
        double[] aInten = new double[aIntensities.size()];
        double[] bInten = new double[bIntensities.size()];
        ///validates that both arrays have the same size
        if(aInten.length!=bInten.length){
            throw new DimensionMismatchException(bInten.length,aInten.length);
        }
        for(int i=0; i<aInten.length; i++){
            aInten[i] = aIntensities.get(i);
            bInten[i] = bIntensities.get(i);

        }
        double corr;
        try {
            corr = new PearsonsCorrelation().correlation(aInten, bInten); //calculates the pearsons correlation coefficient
        } catch (MathIllegalArgumentException e){
            //e.printStackTrace();
            return 0; //returns 0 if the overlap is too small to calculate a correlation(less than 2 data points)
        }
        return corr;
    }

    /**
     * This method is used as part of the recursive algorithm. Specifically, the logical difference between looking
     * above and below the starting point (mz>starting point or mz<starting point) is defined and calculated in this
     * method
     * @param above Whether to look above or below. A value of true is interpreted as above and a value of false is
     *              interpreted as below
     * @param test The test chromatogram
     * @param prev The previous chromatogram
     * @return A comparison of the two values. The direction of the comparison is determined by the value of <code>above</code>
     */
    private boolean recursiveCondition(boolean above, double test, double prev){
        if(!above){
            return test<prev;
        } else {
            return test>prev;
        }
    }

    /**
     * This method checks the charge of a PeakCluster by looking in a nearby m/z and RT window for chromatograms which could be
     * the isotopes of the starting point. It can check for arbitrarily large charge values, and iterates down to 1 until
     * it finds something. If it doesn't find anything, the charge is assumed to be 1.
     * @param startingPoint The starting Chromatogram
     * @param mzXMLFile The MzXMLFile in which to look for nearby chromatograms
     * @param maxCharge The maximum charge value to consider
     * @param ppm The ppm tolerance to use when looking for candidate chromatograms
     */
    private void checkCharge(Chromatogram startingPoint, MzXMLFile mzXMLFile, int maxCharge, double ppm){
        boolean chargeFound = false;
        double RT = startingPoint.getStartingPointRT();
        double inten = startingPoint.getStartingPointIntensity();
        double mz = startingPoint.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList();
        //Updated to check to arbitrarily many charges
        for(int i = maxCharge; i > 0; i--) {
            for (Chromatogram chromatogram : mzXMLFile.chromatograms) {
                if (!chromatogram.equals(startingPoint)) {
                    //if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove /2 && Math.abs(mz-chromatogram.getMeanMZ()) > neutronMassPpmBelow/2) {
                    if (Math.abs(mz - chromatogram.getMeanMZ()) < (neutronMassPpmAbove / i)+((neutronMassPpmAbove/i)/1e6)*ppm) {
                        if (Math.abs(RT - chromatogram.getStartingPointRT()) < 0.03) { //check this constant
                            temp.add(chromatogram);
                        }
                    }
                }
            }
            if (temp.size() > 0 && !chargeFound) {
                charge = i;
                chargeFound = true;
            }
        }
        //If no relevant chromatograms found, assume charge = 1;
        if(!chargeFound)
            charge = 1;
    }

    /**
     * Calculates the upper bound of a given value for a specific ppm window
     * @param val the value to calculate around
     * @param ppm the tolerance to use
     * @return the upper bound of the ppm windows
     */
    private double ppmAbove(double val,double ppm){
        return val + (val /1e6)*ppm;
    }

    /**
     * Calculates the lower bound of a given value for a specific ppm window
     * @param val the value to calculate around
     * @param ppm the tolerance to use
     * @return the lower bound of the ppm windows
     */
    private double ppmBelow(double val, double ppm) { return val - (val /1e6)*ppm; }

    /**
     * Returns the chromatograms which make up the peak cluster
     * @return the chromatograms in a Arraylist
     */
    ArrayList<Chromatogram> getChromatograms() {return chromatograms;}

    /**
     * Returns the calculated charge of the cluster
     * @return the charge of the cluster
     */
    public int getCharge() {return charge;}

    int getStartingPointIndex() { return startingPointIndex;}

    /**
     * Finds all of the possible adducts for this possible PeakCluster using the list generated by AdductDatabase
     * @param adducts The list of Adducts of the same charge
     */
    void findAdducts(List adducts) {
        ArrayList<Adduct> temp = new ArrayList<>();
        for (Adduct a : (Iterable<Adduct>) adducts) {
            if (a.getResultMZ() < targetMZAbove && a.getResultMZ() > targetMZBelow) {
                temp.add(a);
            }
            if (a.getResultMZ() > targetMZAbove) break;
        }
        this.adductList = temp;
    }

    /**
     * Sets the value of the flag inAlignedCluster to true. Once it has been set as true, there is no way to turn it
     * back to false. This is to ensure the integrity of the data.
     */
    void setInAlignedCluster(){
        inAlignedCluster = true;
    }

    boolean getInAlignedCluster(){
        return inAlignedCluster;
    }

    /**
     * This method acts as a wrapper to create a list of PeakClusters for a given file. In normal use, this method should
     * be the only necessary point of access into this class. Note that this method has been replaced by the createPeakClusters
     * method in the MzXMLFile class.
     * @param file The mzXML file to loop through
     * @param adductDir The folder containing the adduct database
     * @return An ArrayList<PeakCluster>
     * @throws IOException Thrown if there is a problem reading in the database information
     * @throws ClassNotFoundException Thrown if there is a problem reading in the database information
     * @throws InterruptedException Thrown if there is a problem with the concurrency
     */
    @Deprecated
    public static ArrayList<PeakCluster> createPeakClusters(MzXMLFile file, String adductDir) throws IOException, InterruptedException, ClassNotFoundException {
        ArrayList<Chromatogram> chromatograms = file.chromatograms;
        ArrayList<PeakCluster> clusters = new ArrayList<>();
        for(Chromatogram chromatogram : chromatograms){
            if(!chromatogram.getInCluster()){
                chromatogram.setInCluster();
                clusters.add(new PeakCluster(chromatogram, 20, file));
            }
        }
        //filters out the invalid peakClusters (based on starting point)
        clusters = (ArrayList<PeakCluster>) clusters.stream().filter(peakCluster -> peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).isValidStartingPoint()).collect(Collectors.toList());
        //maps the clusters to their adducts
        clusters = mapClusters(clusters, adductDir);
        return clusters;
    }

    /**
     * This method maps each PeakCluster to it's possible adducts. This method has been replaced by the mapClusters method
     * in the AdductDatabase class.
     * @param list the list of PeakClusters to map
     * @return An ArrayList<PeakCluster> containing mapped PeakClusters
     * @throws InterruptedException If there is an error with the concurrency
     * @throws IOException If there is an error reading the database
     */
    @Deprecated
    static ArrayList<PeakCluster> mapClusters(ArrayList<PeakCluster> list, String dir) throws InterruptedException, IOException {
        ArrayListMultimap<Integer,Adduct> multimap = ArrayListMultimap.create();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(PeakCluster cluster : list){
            //List<Adduct> sameCharge = dat.stream().filter(p -> p.getIonCharge()==cluster.getCharge()).collect(Collectors.toList());
            if(!multimap.keySet().contains(cluster.getCharge())){
                multimap.putAll(cluster.getCharge(),AdductDatabase.readDatabase(dir,cluster.getCharge()));
            }
            Runnable task = () -> cluster.findAdducts(multimap.get(cluster.getCharge()).stream().filter(p -> p.getIonCharge()==cluster.getCharge()).collect(Collectors.toList()));
            executorService.submit(task);
        }
        executorService.shutdown();
        executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        return list;
    }

    /**
     * Given the input parameters, rescales the m/z and RT values for this peak cluster to enable the use of the euclidean distance
     * in the clustering algorithm.
     * @param MZMax The maximum m/z value across the MzXMLFiles
     * @param MZMin The minimum m/z value across the MzXMLFiles
     * @param RTMax The maximum RT value across the MzXMLFiles
     * @param RTMin The minimum RT value across the MzXMLFiles
     */
    public void setRescaledValues(double MZMax, double MZMin, double RTMax, double RTMin){
        double mz = chromatograms.get(startingPointIndex).getMeanMZ();
        normalisedMZ = (mz-MZMin)/(MZMax-MZMin);
        double rt = chromatograms.get(startingPointIndex).getStartingPointRT();
        normalisedRT = (rt-RTMin)/(RTMax-RTMin);
    }

    /**
     * Required by the Clusterable interface. Used downstream to perform the DBSCAN clustering during the sample alignment step
     * @return An array containing the m/z and RT values for this peakCluster
     */
    @Override
    public double[] getPoint() {
        return new double[] {normalisedMZ, normalisedRT};
    }

    /**
     * Returns the m/z value for the starting chromatogram (the m+0 isotope)
     * @return the m/z value for the m+0 isotope
     */
    public double getMainMZ(){
        return chromatograms.get(startingPointIndex).getMeanMZ();
    }

    /**
     * Returns the RT value for the starting chromatogram (the m+0 isotope)
     * @return the RT value for the m+0 isotope
     */
    public double getMainRT(){
        return chromatograms.get(startingPointIndex).getStartingPointRT();
    }
}
