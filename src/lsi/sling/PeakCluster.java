package lsi.sling;

import com.google.common.collect.ArrayListMultimap;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This Class represents a peak cluster (i.e. a peak along with it's isotopes).
 * @author Adithya Diddapur
 */
public class PeakCluster extends MzXMLFile {

    final static double NEUTRON_MASS = 1.00866491588;

    private double neutronMassPpmAbove;
    private double neutronMassPpmBelow;
    private ArrayList<Chromatogram> chromatograms; //the individual chromatograms which make up the cluster
    private ArrayList<Chromatogram> tempChroma;
    private int charge; //in normal use, this should only ever be 1 or 2
    private int startingPointIndex;
    private List<Adduct> adductList;
    private double targetMZAbove;
    private double targetMZBelow;

    /**
     * This method acts as a wrapper to create a list of PeakClusters for a given file. In normal use, this method should
     * be the only necessary point of access into this class
     * @param file The mzXML file to loop through
     * @param adductDir The folder containing the adduct database
     * @return An ArrayList<PeakCluster>
     * @throws IOException Thrown if there is a problem reading in the database information
     * @throws ClassNotFoundException Thrown if there is a problem reading in the database information
     * @throws InterruptedException Thrown if there is a problem with the concurrency
     */
    public static ArrayList<PeakCluster> createPeakClusters(MzXMLFile file, String adductDir) throws IOException, InterruptedException, ClassNotFoundException {
        ArrayList<Chromatogram> chromatograms = file.chromatograms;
        ArrayList<PeakCluster> clusters = new ArrayList<>();
        for(Chromatogram chromatogram : chromatograms){
            if(!chromatogram.getInCluster()){
                chromatogram.setInCluster();
                clusters.add(new PeakCluster(chromatogram, 20, Main.files.indexOf(file)));
            }
        }
        //filters out the invalid peakClusters (based on starting point)
        clusters = (ArrayList<PeakCluster>) clusters.parallelStream().filter(peakCluster -> peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).isValidStartingPoint()).collect(Collectors.toList());
        //maps the clusters to their adducts
        clusters = mapClusters(clusters, adductDir);
        return clusters;
    }

    public PeakCluster(Chromatogram startingPoint, double ppm, int pos) throws IOException {
        adductList = new ArrayList<>();
        chromatograms = new ArrayList<>();
        tempChroma = new ArrayList<>();
        neutronMassPpmAbove = ppmAbove(NEUTRON_MASS, ppm);
        neutronMassPpmBelow = ppmBelow(NEUTRON_MASS, ppm);
        checkCharge(startingPoint, pos);
        checkAboveOrBelow(startingPoint,false, ppm, pos);
        for(int i = tempChroma.size(); i>0; i--){
            chromatograms.add(tempChroma.get(i-1));
        }
        startingPointIndex = chromatograms.size();
        chromatograms.add(startingPoint);
        tempChroma.clear();
        checkAboveOrBelow(startingPoint,true, ppm, pos);
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
    private int checkAboveOrBelow(Chromatogram previous, boolean above, double ppm, int pos){
        double RT = previous.getStartingPointRT();
        double inten = previous.getStartingPointIntensity();
        double mz = previous.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList<>();
        //This for loop checks for doubly charged isotopes (difference in mz = 0.5)
        for (Chromatogram chromatogram : Main.files.get(pos).chromatograms){
            if(!chromatogram.equals(previous)) {
                //if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove /charge && Math.abs(mz-chromatogram.getMeanMZ()) > neutronMassPpmBelow/charge&& recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                if (Math.abs(Math.abs(mz - chromatogram.getMeanMZ()) - neutronMassPpmAbove /charge) < 0.05 && recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                    if (correlateChromatograms(previous, chromatogram) > 0.8) { //uses the correlation function below to determine isobars. The constant still needs to be adjusted.
                        temp.add(chromatogram);
                    }
                    /*if (Math.abs(RT - chromatogram.getStartingPointRT()) < 0.03) { //check this constant
                    //if(Math.abs(RT - chromatogram.getStartingPointRT()) < RT*0.05){  //This statement still needs work
                        temp.add(chromatogram);
                    }*/
                }
            }
        }
        if(temp.size()==1){
            Main.files.get(pos).chromatograms.get(Main.files.get(pos).chromatograms.indexOf(temp.get(0))).setInCluster();
            tempChroma.add(temp.get(0));
            return checkAboveOrBelow(temp.get(0), above, ppm, pos);
        } else if(temp.size()==0) {
            return 1;
        } else if(temp.size()>1) {
            double minDistance = Math.abs(temp.get(0).getMeanMZ()-(previous.getMeanMZ()+NEUTRON_MASS));
            int index = 0;
            for(int i=1; i<temp.size(); i++){
                if(Math.abs(temp.get(0).getMeanMZ()-(previous.getMeanMZ()+NEUTRON_MASS))<minDistance){
                    index = i;
                }
            }
            Main.files.get(pos).chromatograms.get(Main.files.get(pos).chromatograms.indexOf(temp.get(index))).setInCluster();
            tempChroma.add(temp.get(index));
            return checkAboveOrBelow(temp.get(0), above, ppm, pos);
        } else {
            throw new IllegalArgumentException();
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
        if(aInten.length!=bInten.length){
            throw new DimensionMismatchException(bInten.length,aInten.length);
        }
        for(int i=0; i<aInten.length; i++){
            aInten[i] = aIntensities.get(i);
            bInten[i] = bIntensities.get(i);

        }
        double corr;
        try {
            corr = new PearsonsCorrelation().correlation(aInten, bInten);
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
     * This method checks the charge of a peak cluster by looking for peaks adjacent to the starting point at the mz+-0.5
     * locations. If it finds something, the charge is set to 2 and if it finds nothing the charge is set to 1. Note,
     * if the charge!=2, it does NOT explicitly look for another chromatogram at the mz+-1 locations. However, this
     * functionality does exist but is currently commented out of the code to improve performance and reliability.
     * @param startingPoint the starting Chromatogram
     */
    private void checkCharge(Chromatogram startingPoint, int pos){
        double RT = startingPoint.getStartingPointRT();
        double inten = startingPoint.getStartingPointIntensity();
        double mz = startingPoint.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList();
        //This for loop checks for doubly charged isotopes (difference in mz = 0.5)
        for (Chromatogram chromatogram : Main.files.get(pos).chromatograms){
            if(!chromatogram.equals(startingPoint)) {
                //if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove /2 && Math.abs(mz-chromatogram.getMeanMZ()) > neutronMassPpmBelow/2) {
                if(Math.abs(mz-chromatogram.getMeanMZ())< neutronMassPpmAbove /2){
                    if (Math.abs(RT - chromatogram.getStartingPointRT()) < 0.03) { //check this constant
                        temp.add(chromatogram);
                    }
                }
            }
        }
        if(temp.size()>0){
            charge = 2;
        } else {
            charge = 1;
            /*for (Chromatogram chromatogram : Main.chromatograms){
                if(!chromatogram.equals(startingPoint)) {
                    if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove) {
                        if (Math.abs(RT - chromatogram.getStartingPointRT()) < 0.03) { //check this constant
                            temp.add(chromatogram);
                        }
                    }
                }
            }
            if(temp.size()>0){
                charge = 1;
            }*/
        }
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
    int getCharge() {return charge;}

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
     * This method maps each PeakCluster to it's possible adducts.
     * @param list the list of PeakClusters to map
     * @return An ArrayList<PeakCluster> containing mapped PeakClusters
     * @throws InterruptedException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    static ArrayList<PeakCluster> mapClusters(ArrayList<PeakCluster> list, String dir) throws InterruptedException, IOException, ClassNotFoundException {
        ArrayListMultimap<Integer,Adduct> multimap = ArrayListMultimap.create();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(PeakCluster cluster : list){
            //List<Adduct> sameCharge = dat.stream().filter(p -> p.getIonCharge()==cluster.getCharge()).collect(Collectors.toList());
            if(!multimap.keySet().contains(new Integer(cluster.getCharge()))){
                multimap.putAll(new Integer(cluster.getCharge()),AdductDatabase.readDatabase(dir,cluster.getCharge()));
            }
            Runnable task = () -> {
                cluster.findAdducts(multimap.get(cluster.getCharge()).stream().filter(p -> p.getIonCharge()==cluster.getCharge()).collect(Collectors.toList()));
            };
            executorService.submit(task);
        }
        executorService.shutdown();
        executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        return list;
    }
}
