package lsi.sling;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.lang.Math;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.opencsv.CSVReader;
import expr.Expr;
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

/**
 * This Class represents a peak cluster (i.e. a peak along with it's isotopes).
 * @author Adithya Diddapur
 */
public class PeakCluster {

    final static double NEUTRON_MASS = 1.00866491588;

    private double neutronMassPpmAbove;
    private double neutronMassPpmBelow;
    private ArrayList<Chromatogram> chromatograms; //the individual chromatograms which make up the cluster
    private ArrayList<Chromatogram> tempChroma;
    private int charge; //in normal use, this should only ever be 1 or 2
    private int startingPointIndex;
    List<Adduct> adductList;
    private double targetMZAbove;
    private double targetMZBelow;

    public PeakCluster(Chromatogram startingPoint, double ppm){
        //Main.chromatograms.get(Main.chromatograms.indexOf(startingPoint)).setInCluster();
        adductList = new ArrayList<>();
        chromatograms = new ArrayList<>();
        tempChroma = new ArrayList<>();
        setNeutronMassPpmAbove(ppm);
        setNeutronMassPpmBelow(ppm);
        checkCharge(startingPoint);
        checkAboveOrBelow(startingPoint,false);
        for(int i = tempChroma.size(); i>0; i--){
            chromatograms.add(tempChroma.get(i-1));
        }
        startingPointIndex = chromatograms.size();
        chromatograms.add(startingPoint);
        tempChroma.clear();
        checkAboveOrBelow(startingPoint,true);
        for(Chromatogram chromatogram : tempChroma){
            chromatograms.add(chromatogram);
        }
        targetMZAbove = chromatograms.get(startingPointIndex).getMeanMZ() + (chromatograms.get(startingPointIndex).getMeanMZ()/1e6)*ppm;
        targetMZBelow = chromatograms.get(startingPointIndex).getMeanMZ() - (chromatograms.get(startingPointIndex).getMeanMZ()/1e6)*ppm;
    }

    /**
     * This method recursively looks for the chromatograms adjacent to the starting point which can then be arranged
     * into the Peak Cluster. If multiple chromatograms are found, the one closest to the expected theoretical value is used
     * @param previous The starting point used in the previous iteration of the recursive loop
     * @param above Whether to look above (high mz) or below (lower mz) the starting point. A value of true is interpreted
     *              as look above whilst a value of false is interpreted as look below
     * @return If the operation completed succesfully it returns 1, otherwise (if more than 1 usable isotope is found in
     * the same place --> should not be possible which means it's a bug) it returns 2
     * @throws IllegalArgumentException if the number of valid peaks it finds is nonsensical (negative or not a number or something silly)
     */
    private int checkAboveOrBelow(Chromatogram previous, boolean above){
        double RT = previous.getStartingPointRT();
        double inten = previous.getStartingPointIntensity();
        double mz = previous.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList<>();
        //This for loop checks for doubly charged isotopes (difference in mz = 0.5)
        for (Chromatogram chromatogram : Main.chromatograms){
            if(!chromatogram.equals(previous)) {
                //if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove /charge && Math.abs(mz-chromatogram.getMeanMZ()) > neutronMassPpmBelow/charge&& recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                if (Math.abs(mz - chromatogram.getMeanMZ()) < neutronMassPpmAbove /charge && recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
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
            Main.chromatograms.get(Main.chromatograms.indexOf(temp.get(0))).setInCluster();
            tempChroma.add(temp.get(0));
            return checkAboveOrBelow(temp.get(0), above);
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
            Main.chromatograms.get(Main.chromatograms.indexOf(temp.get(index))).setInCluster();
            tempChroma.add(temp.get(index));
            return checkAboveOrBelow(temp.get(0), above);
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
        ArrayList aIntensities = new ArrayList();
        for(int i=0; i<a.getIntensityScanPairs().size(); i++){
            if(a.getIntensityScanPairs().get(i).getRT()>=minPoint && a.getIntensityScanPairs().get(i).getRT()<=maxPoint){
                aIntensities.add(a.getIntensityScanPairs().get(i).getIntensity());
            }
        }
        ArrayList bIntensities = new ArrayList();
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
            aInten[i] = (double) aIntensities.get(i);
            bInten[i] = (double) bIntensities.get(i);

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
    private void checkCharge(Chromatogram startingPoint){
        double RT = startingPoint.getStartingPointRT();
        double inten = startingPoint.getStartingPointIntensity();
        double mz = startingPoint.getMeanMZ();
        ArrayList temp = new ArrayList();
        //This for loop checks for doubly charged isotopes (difference in mz = 0.5)
        for (Chromatogram chromatogram : Main.chromatograms){
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
        System.out.println("test");
    }

    /**
     * Calculates and sets the value of the proton mass within a given tolerance (ppm)
     * @param ppm the tolerance to use
     */
    private void setNeutronMassPpmAbove(double ppm){
        neutronMassPpmAbove = NEUTRON_MASS + (NEUTRON_MASS /1e6)*ppm;
    }

    private void setNeutronMassPpmBelow(double ppm) { neutronMassPpmBelow = NEUTRON_MASS - (NEUTRON_MASS /1e6)*ppm; }

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

    List<Adduct> findAdducts() throws IOException {
        List<Adduct> temp = Collections.synchronizedList(new ArrayList());
        //ArrayList temp = new ArrayList();
        File adductFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Adducts.csv");
        File compoundFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Database.csv");
        CSVReader adductReader = new CSVReader(new FileReader(adductFile));
        //CSVReader compoundReader = new CSVReader(new FileReader(compoundFile));
        String[] nextLineCompound;

        //ExecutorService executor = Executors.newCachedThreadPool();
        ExecutorService executor = Executors.newWorkStealingPool();

        Iterator<String[]> adductIterator = adductReader.iterator();
        //ArrayList<Double> expressions = new ArrayList();
        while(adductIterator.hasNext()){
            String[] adductInfo = adductIterator.next();
            String expression = adductInfo[2];
            String ionName = adductInfo[1]; //this line works
            if(!expression.equals("Ion mass")) {
                double ionMass = Double.parseDouble(adductInfo[5]); //this line works
                String icharge = adductInfo[3]; //this line works
                icharge = (icharge.charAt(icharge.length()-1) + icharge); //this line works
                icharge = icharge.substring(0,icharge.length()-1); //this line works
                int ionCharge = Integer.parseInt(icharge); //this line works
                if(ionCharge==charge) {
                    CSVReader compoundReader = null;
                    compoundReader = new CSVReader(new FileReader(compoundFile));
                    while ((nextLineCompound = compoundReader.readNext()) != null) {
                        if (!nextLineCompound[0].equals("")) {
                            String massString = nextLineCompound[1];
                            String compoundFormula = nextLineCompound[0]; //this line works
                            String compoundCommonName = nextLineCompound[2]; //this line works
                            String compoundSystemicName = nextLineCompound[3]; //this line works
                            if (!massString.equals("exactMass")) {
                                Runnable task = () -> {
                                    //String icharge = adductInfo[3];
                                    //icharge = (icharge.charAt(icharge.length()-1) + icharge);
                                    //icharge = icharge.substring(0,icharge.length()-1);
                                    Expr expr = null;
                                    try {
                                        expr = Parser.parse(expression);
                                    } catch (SyntaxException e) {
                                        e.printStackTrace();
                                    }
                                    Variable M = Variable.make("M");
                                    M.setValue(Double.parseDouble(massString));
                                    //temp.add(new Adduct(adductInfo[1],expression,Double.parseDouble(adductInfo[5]),Integer.parseInt(adductInfo[3]),Double.parseDouble(massString),expr.value(),compoundInfo[0],compoundInfo[2],compoundInfo[3]));
                                    //temp.add(expr.value());
                                    temp.add(new Adduct(ionName, expression, ionMass, ionCharge, Double.parseDouble(massString), expr.value(), compoundFormula, compoundCommonName, compoundSystemicName));
                                    //System.out.println(expr.value()); //this line does NOT work
                                };

                                executor.submit(task);
                            }
                        }
                    }
                }
            }
        }
        executor.shutdown();
//        for(int i=0; i<temp.size(); i++){
//            if(temp.get(i).getResultMZ()>targetMZAbove||temp.get(i).getResultMZ()<targetMZBelow){
//                temp.remove(i);
//                i--;
//            }
//        }
        for(Iterator<Adduct> i = temp.iterator(); i.hasNext();){
            Adduct a = i.next();
            if(a.getResultMZ()>targetMZAbove||a.getResultMZ()<targetMZBelow){
                i.remove();
            }
        }
        return temp;
    }
}
