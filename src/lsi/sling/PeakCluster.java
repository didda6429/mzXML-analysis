package lsi.sling;

import java.util.ArrayList;

import java.lang.Math;

/**
 * This Class represents a peak cluster (i.e. a peak along with it's isotopes).
 * @author Adithya Diddapur
 */
public class PeakCluster {

    final static double NEUTRON_MASS = 1.00866491588;

    private double protonMassPpmAbove;
    private double protonMassPpmBelow;
    private ArrayList<Chromatogram> chromatograms; //the individual chromatograms which make up the cluster
    private ArrayList<Chromatogram> tempChroma;
    private int charge; //in normal use, this should only ever be 1 or 2
    private int startingPointIndex;

    public PeakCluster(Chromatogram startingPoint, double ppm){
        chromatograms = new ArrayList<>();
        tempChroma = new ArrayList<>();
        setProtonMassPpmAbove(ppm);
        setProtonMassPpmBelow(ppm);
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
        System.out.println("test");
    }

    /**
     * This method recursively looks for the chromatograms adjacent to the starting point which can then be arranged
     * into the Peak Cluster
     * @param previous The starting point used in the previous iteration of the recursive loop
     * @param above Whether to look above (high mz) or below (lower mz) the starting point. A value of true is interpreted
     *              as look above whilst a value of false is interpreted as look below
     * @return If the operation completed succesfully it returns 1, otherwise (if more than 1 usable isotope is found in
     * the same place --> should not be possible which means it's a bug) it returns 2
     */
    private int checkAboveOrBelow(Chromatogram previous, boolean above){
        double RT = previous.getStartingPointRT();
        double inten = previous.getStartingPointIntensity();
        double mz = previous.getMeanMZ();
        ArrayList<Chromatogram> temp = new ArrayList();
        //This for loop checks for doubly charged isotopes (difference in mz = 0.5)
        for (Chromatogram chromatogram : Main.chromatograms){
            if(!chromatogram.equals(previous)) {
                //if (Math.abs(mz - chromatogram.getMeanMZ()) < protonMassPpmAbove /charge && Math.abs(mz-chromatogram.getMeanMZ()) > protonMassPpmBelow/charge&& recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                if (Math.abs(mz - chromatogram.getMeanMZ()) < protonMassPpmAbove /charge && recursiveCondition(above,chromatogram.getMeanMZ(),mz)) {
                    if (Math.abs(RT - chromatogram.getStartingPointRT()) < 0.03) { //check this constant
                    //if(Math.abs(RT - chromatogram.getStartingPointRT()) < RT*0.05){  //This statement still needs work
                        temp.add(chromatogram);
                    }
                }
            }
        }
        if(temp.size()==1){
            Main.chromatograms.get(Main.chromatograms.indexOf(temp.get(0))).setInCluster();
            tempChroma.add(temp.get(0));
            return checkAboveOrBelow(temp.get(0), above);
        } else if(temp.size()==0) {
            return 1;
        } else {
            throw new IllegalArgumentException();
        }
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
     * functionality does exist but is currently commented out of the code to improve performance.
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
                //if (Math.abs(mz - chromatogram.getMeanMZ()) < protonMassPpmAbove /2 && Math.abs(mz-chromatogram.getMeanMZ()) > protonMassPpmBelow/2) {
                if(Math.abs(mz-chromatogram.getMeanMZ())<protonMassPpmAbove/2){
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
                    if (Math.abs(mz - chromatogram.getMeanMZ()) < protonMassPpmAbove) {
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
    private void setProtonMassPpmAbove(double ppm){
        protonMassPpmAbove = NEUTRON_MASS + (NEUTRON_MASS /1e6)*ppm;
    }

    private void setProtonMassPpmBelow(double ppm) { protonMassPpmBelow = NEUTRON_MASS - (NEUTRON_MASS /1e6)*ppm; }

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
}
