package lsi.sling;

import flanagan.analysis.CurveSmooth;
import umich.ms.datatypes.scan.IScan;
import umich.ms.datatypes.spectrum.ISpectrum;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;


/**
 * Represents a group of LocalPeaks which together form a peak which is significant across the entire dataset.
 * (This is the peak which will later be integrated and analysed)
 * <p>
 * All of the class member fields are private to help ensure reproducibility (They can't be changed which ensures
 * that you can read them later to find the same values which were used to create the object)
 *
 * @author Adithya Diddapur
 */
public class Chromatogram {

    private ArrayList<LocalPeak> intensityScanPairs;
    private ArrayList<LocalPeak> intensityScanPairsBelow;
    private ArrayList<Integer> pointsOfInflection;
    private ArrayList<Isobar> isobars;
    private double meanMZ;
    private double tolerance;
    private double threshold; //used to define noise to signal ratio
    private int startingPointIndex; //index of the max peak within the ArrayList (max intensity)
    private double startingPointRT;
    private double startingPointIntensity;
    private double[] smoothData;
    private boolean inCluster;

    /**
     * Constructor which creates a new Chromatogram. This class is designed so that in normal use, a user only every needs
     * to call the constructor which acts as a wrapper for everything.
     * i.e. Once called, the constructor initialises all the relevant variables and runs the recursive algorithm to
     * find the edges of the peak (where it stops being significant) Note: at the moment significance is determined by
     * a constant (thresh).
     *
     * @param scanList      An ArrayList of all the scans (as IScan objects)
     * @param startingPoint The LocalPeak to use as the starting point for the larger peak
     * @param tol           The tolerance (in ppm) to account for the jitter
     * @param thresh        The threshold to determine the end points of the peak. This value is used to determine the
     *                      validity of a chromatogram (within the scope of a peak cluster)
     * @throws FileParsingException Thrown when the recursive loops try to access the scan data
     */
    public Chromatogram(ArrayList<IScan> scanList, LocalPeak startingPoint, double tol, double thresh) throws FileParsingException {
        startingPointRT = startingPoint.getRT();
        startingPointIntensity = startingPoint.getIntensity();
        intensityScanPairs = new ArrayList<>();
        intensityScanPairsBelow = new ArrayList<>();
        pointsOfInflection = new ArrayList<>();
        tolerance = tol;
        threshold = thresh;
        inCluster = false;
        meanMZ = startingPoint.getMZ();
        if (startingPoint.getScanNumber() > 0) { //checks if the startingpoint is at the bottom of the file
            createPeakBelow(scanList, meanMZ, tol, startingPoint.getScanNumber() - 1);
        }
        for (int i = intensityScanPairsBelow.size(); i > 0; i--) {
            intensityScanPairs.add(intensityScanPairsBelow.get(i - 1));
        }
        intensityScanPairs.add(startingPoint);
        if (startingPoint.getScanNumber() + 1 < scanList.size()) {
            createPeakAbove(scanList, averageMZ(), tol, startingPoint.getScanNumber() + 1);
        }
        startingPointIndex = intensityScanPairsBelow.size();
        if (intensityScanPairs.size() > 4) {
            smoothToFindMinima();
        } else {
            findLocalMinima();
        }
        isobars = new ArrayList<>();
        pointsOfInflection.add(0, 0);
        pointsOfInflection.add(pointsOfInflection.size(), intensityScanPairs.size());
        if (intensityScanPairs.size() > 4 && pointsOfInflection.size() > 2) { //only performs the following code if the data has been smoothed
            for (int i = 0; i < pointsOfInflection.size() - 1; i++) {
                ArrayList<LocalPeak> pairs = new ArrayList<>();
                double[] smooth = null;
                try {
                    smooth = new double[pointsOfInflection.get(i + 1) - pointsOfInflection.get(i)];
                } catch (NegativeArraySizeException e) {
                    e.printStackTrace();
                    System.out.println("test");
                }
                int x = 0;
                for (int j = pointsOfInflection.get(i); j < pointsOfInflection.get(i + 1); j++) {
                    pairs.add(intensityScanPairs.get(j));
                    smooth[x] = smoothData[j];
                    x++;
                }
                isobars.add(new Isobar(pairs, meanMZ, tolerance, threshold, smooth, inCluster));
            }
        } else {
            isobars.add(new Isobar(intensityScanPairs, meanMZ, tolerance, threshold, smoothData, inCluster));
        }
    }

    /**
     * Recursive loop which 'looks' at the scans above the starting points (higher RT) to find where the peak ends.
     *
     * @param scanList  An ArrayList of all the scans (as IScan objects)
     * @param average   The average m/z value of the peak. This is used to account for the jitter and is re-calculated
     *                  once for each recursive iteration.
     * @param toler     The tolerance (in ppm) to account for the jitter
     * @param increment Used to iterate through one of the loops is a semi-recursive manner
     * @return the integer 1 if the operation was carried out successfully. The integer 2 is returned if the scans
     * reach the end of the file
     * @throws FileParsingException Thrown when the recursive loops try to access the scan data
     */
    private int createPeakAbove(ArrayList<IScan> scanList, double average, double toler, int increment) throws FileParsingException {
        ISpectrum temp = scanList.get(increment).fetchSpectrum();
        if (temp.findMzIdxsWithinPpm(average, toler) != null) {
            LocalPeak tempPeak = maxIntWithinTol(temp, average, toler, increment, scanList.get(increment).getRt());
            if (tempPeak.getIntensity() > threshold) {
                int tempInt = Main.peakList.indexOf(tempPeak);
                if (tempInt == -1) {
                    tempPeak.setIsUsed();
                    tempInt = Main.peakList.indexOf(tempPeak);
                }
                tempPeak.setIsUsed();
                intensityScanPairs.add(tempPeak);
                Main.peakList.get(tempInt).setIsUsed();
                if (increment < scanList.size() - 2) {
                    return createPeakAbove(scanList, averageMZ(), toler, increment + 1);
                } else {
                    return 2;
                }
            }
        }
        return 1;
    }

    /**
     * Recursive loop which 'looks' at the scans below the starting points (lower RT) to find where the peak ends.
     *
     * @param scanList  An ArrayList of all the scans (as IScan objects)
     * @param average   The average m/z value of the peak. This is used to account for the jitter and is re-calculated
     *                  once for each recursive iteration.
     * @param toler     The tolerance (in ppm) to account for the jitter
     * @param increment Used to iterate through one of the loops is a semi-recursive manner
     * @return the integer 1 if the operation was carried out successfully, 2 if the scans reached the end of the file
     * @throws FileParsingException Thrown when the recursive loops try to access the scan data
     */
    private int createPeakBelow(ArrayList<IScan> scanList, double average, double toler, int increment) throws FileParsingException {
        ISpectrum temp = scanList.get(increment).fetchSpectrum();
        if (temp.findMzIdxsWithinPpm(average, toler) != null) {
            LocalPeak tempPeak = maxIntWithinTol(temp, average, toler, increment, scanList.get(increment).getRt());
            if (tempPeak.getIntensity() > this.threshold) {
                int tempInt = Main.peakList.indexOf(tempPeak);
                if (tempInt == -1) {
                    tempPeak.setIsUsed();
                    tempInt = Main.peakList.indexOf(tempPeak);
                }
                tempPeak.setIsUsed();
                intensityScanPairsBelow.add(tempPeak);
                Main.peakList.get(tempInt).setIsUsed();
                if (increment > 1) {
                    return createPeakBelow(scanList, averageMZBelow(), toler, increment - 1);
                } else {
                    return 2;
                }
            }
        }
        return 1;
    }


    /**
     * Finds the highest single peak within a given tolerance in a individual spectrum(to account for jitter).
     * <p>
     * NOTE: This function is crucial to the operation of the Chromatogram data structure and is
     * called by several other functions at higher levels so be careful when modifying it
     *
     * @param spec      The single spectrum from which to extract a single Chromatogram
     * @param mean      The value around which the tolerance is centered (this partly defines where the single peak will
     *                  be extracted from
     * @param tol       The tolerance to jitter
     * @param increment The scan number (from the parent IScan object) which is then used to create a LocalPeak object
     *                  containing the relevant information
     * @param RT        The RT of the scan which is used to create a LocalPeak object containing the relevant information
     * @return A LocalPeak object containing all of the relvant information
     */
    private static LocalPeak maxIntWithinTol(ISpectrum spec, double mean, double tol, int increment, double RT) {
        int[] temp = spec.findMzIdxsWithinPpm(mean, tol);   //according to source code tolerance is calculated as (mean/1e6)*tol
        double[] inten = spec.getIntensities();
        int maxIndex = 0;
        double maxIntensity = 0;
        for (int i = temp[0]; i <= temp[1]; i++) {
            if (inten[i] > maxIntensity) {
                maxIntensity = inten[i];
                maxIndex = i;
            }
        }
        return new LocalPeak(increment, maxIntensity, spec.getMZs()[maxIndex], RT);
    }

    /**
     * This method applies a set of rules to the chromatogram object to determine if it is valid.
     * NOTE - The list of rules still needs development. At the moment, it only checks :
     * - more than 5 data points
     * - maxIntensity/minIntensity > 5
     * - maxIntensity > 5* threshold
     * - peak width is less than 30 seconds (0.5 units)
     *
     * @return true if it is valid, otherwise false
     */
    boolean isValidStartingPoint() {
        if (intensityScanPairs.size() > 5) {
            ArrayList<LocalPeak> tempList = intensityScanPairs;
            Collections.sort(tempList);
            double maxIntensity = tempList.get(0).getIntensity();
            double minIntensity = tempList.get(tempList.size() - 1).getIntensity();
            if (maxIntensity / minIntensity > 5) {
                if (maxIntensity > 5 * threshold) {
                    if(intensityScanPairs.get(intensityScanPairs.size()-1).getRT()-intensityScanPairs.get(0).getRT()<0.5){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Finds the local minima within the peak (based on intensities). This information is directly related to separating
     * isobars. Note: This method finds the minimas by comparing adjacent intensities and therefore picks up a lot of
     * noise. It has been replaced by the smoothToFindMinima method but is retained for potential use in cases where
     * the smoothing doesnt work properly.
     */
    @Deprecated
    private void findLocalMinima() {
        pointsOfInflection.clear();
        double[] intensityArray = new double[intensityScanPairs.size()];
        for (int i = 0; i < intensityArray.length; i++)
            intensityArray[i] = intensityScanPairs.get(i).getIntensity();
        for (int i = 1; i < intensityArray.length - 1; i++) {
            if (intensityArray[i - 1] > intensityArray[i] && intensityArray[i] < intensityArray[i + 1])
                pointsOfInflection.add(i);
        }
    }

    /**
     * Calculates the average m/z value of all the LocalPeak objects in the intensityScanPairs ArrayList. This method
     * returns the value as a double for use in the recursive loops and saves the value to the class field meanMZ
     *
     * @return The average m/z value as a double
     */
    private double averageMZ() {
        int i = 0;
        double total = 0;
        for (LocalPeak intensityScanPair : intensityScanPairs) {
            total = total + intensityScanPair.getMZ();
            i++;
        }
        double average = total / i;
        meanMZ = average;
        return average;
    }

    /**
     * The average m/z value of all the LocalPeak objects in the intensityScanPairsBelow ArrayList. Note: This method
     * is very similar to the averageMZ() method except that it iis specifically intended for use in the
     * createPeakBelow() method. The calculated value also gets saved to the class field meanMZ
     *
     * @return the average m/z value from intensityScanPairsBelow
     */
    private double averageMZBelow() {
        int i = 0;
        double total = 0;
        for (LocalPeak anIntensityScanPairsBelow : intensityScanPairsBelow) {
            total = total + anIntensityScanPairsBelow.getMZ();
            i++;
        }
        double average = total / i;
        meanMZ = average;
        return average;
    }

    /**
     * Returns the intensityScanPairs ArrayList
     *
     * @return An ArrayList containing the LocalPeak objects from intensityScanPairs
     */
    ArrayList<LocalPeak> getIntensityScanPairs() {
        return intensityScanPairs;
    }

    /**
     * Returns the points of inflection (turning points) for the peak. Specifically, this method returns the indices of
     * the turning points in the ArrayList
     *
     * @return the locations of the turning points
     */
    ArrayList<Integer> getPointsOfInflection() {
        return pointsOfInflection;
    }

    /**
     * Returns the mean m/z for the Chromatogram
     *
     * @return the mean m/z value as a double
     */
    double getMeanMZ() {
        return meanMZ;
    }

    /**
     * Returns the tolerance used to create the peak
     *
     * @return the tolerance as a double
     */
    double getTolerance() {
        return tolerance;
    }

    /**
     * Returns the threshold used to generate the peak
     *
     * @return The threshold as a double
     */
    double getThreshold() {
        return threshold;
    }

    /**
     * Returns the index of the LocalPeak which was used as a starting point
     *
     * @return the index of the starting point as a integer
     */
    int getStartingPointIndex() {
        return startingPointIndex;
    }

    /**
     * Returns the RT of the LocalPeak which was used as a starting point
     *
     * @return the RT of the starting point as a double
     */
    double getStartingPointRT() {
        return startingPointRT;
    }

    /**
     * Returns the intensity of the LocalPeak which was used as a starting point
     *
     * @return the intensity of the starting point as a double
     */
    double getStartingPointIntensity() {
        return startingPointIntensity;
    }

    /**
     * Returns only the intensities in an array
     *
     * @return only the intensities from the ion chromatogram
     */
    double[] getIntensities() {
        double[] val = new double[intensityScanPairs.size()];
        for (int i = 0; i < intensityScanPairs.size(); i++) {
            val[i] = intensityScanPairs.get(i).getIntensity();
        }
        return val;
    }

    /**
     * Returns only the retention times in an array
     *
     * @return only the retention times from the ion chromatogram
     */
    double[] getRT() {
        double[] val = new double[intensityScanPairs.size()];
        for (int i = 0; i < intensityScanPairs.size(); i++) {
            val[i] = intensityScanPairs.get(i).getRT();
        }
        return val;
    }

    /**
     * Writes the smoothData information to a file. NOTE, this method is only intended for debugging at the moment
     * (I am using it to transfer the data to R more easily where it's easier to plot and validate the data)
     *
     * @throws IOException IOException from the file handling stuff
     */
    void writeToCSV() throws IOException {
        FileWriter writer = new FileWriter("C://Users//lsiv67//Documents//peaks//smoothpeak" + this.getMeanMZ() + "intenis" + this.getStartingPointIntensity() + "rt" + this.getStartingPointRT() + ".csv");
        StringBuilder sb = new StringBuilder();
        double[] inten = this.getIntensities();
        double[] rt = this.getRT();
        for (int i = 0; i < inten.length; i++) {
            sb.append(inten[i]).append(",").append(rt[i]).append("\n");
        }
        writer.append(sb);
        writer.close();
    }

    /**
     * Smooths out the curve using a savitzky-Golay Plot (from the Michael Thomas Flanagan's java scientific library).
     * The the number of points to use is calculated as 6*log(ArrayList.size). The smoothed data is then stored into
     * the class variable double[] smoothData. The indices of the smoothed-minima are also stored into pointsOfInflection,
     * replacing whatever values were there.
     */
    void smoothToFindMinima() {
        CurveSmooth curveSmooth = new CurveSmooth(this.getRT(), this.getIntensities());
        //At the moment the flanagan plotting program is also called to help evaluate the performance of the filter
        smoothData = curveSmooth.savitzkyGolay((int) (10 * Math.log(this.getRT().length)));
        //smoothData = curveSmooth.savitzkyGolayPlot(15);
        //smoothData = curveSmooth.savitzkyGolayPlot((int) (Math.ceil(getRT()[getRT().length-1]-getRT()[0])*8));
        double[][] minima = curveSmooth.getMinimaSavitzkyGolay();
        //pointsOfInflection.clear();
        ArrayList<Double> temp = new ArrayList();
        for (int i = 0; i < getRT().length; i++) {
            temp.add(getRT()[i]);
        }
        for (int i = 0; i < minima[0].length; i++) {
            pointsOfInflection.add(temp.indexOf(search(minima[0][i], this.getRT())));
        }
    }

    /**
     * finds the value of an array which is closest to the given value. This method is used in the smoothToFindMinima()
     * method when the position of the minima isn't exactly the same as the RT of one of the scans
     *
     * @param myNumber The number to compare against
     * @param numbers  The array of numbers to search in
     * @return The closest number in the array
     */
    private static double search(double myNumber, double[] numbers) {
        int idx = 0;
        double distance = Math.abs(myNumber - numbers[0]);
        for (int c = 1; c < numbers.length; c++) {
            double cdistance = Math.abs(myNumber - numbers[c]);
            if (cdistance < distance) {
                idx = c;
                distance = cdistance;
            }
        }
        double theNumber = numbers[idx];
        return theNumber;
    }

    //This method is for testing only
    void plotSmoothToFindMinima() {
        CurveSmooth curveSmooth = new CurveSmooth(this.getRT(), this.getIntensities());
        //At the moment the flanagan plotting program is also called to help evaluate the performance of the filter
        double[] temp = curveSmooth.savitzkyGolayPlot((int) (6 * Math.log(this.getRT().length)));
    }

    /**
     * Returns the smoothed dataset as calculated in smoothToFindMinima() (using a Savitzky-Golay filter)
     *
     * @return the smoothed dataset as doubles
     */
    double[] getSmoothData() {
        return smoothData;
    }

    /**
     * This method sets the value of the inCluster flag to true. In normal use, this method should only ever need
     * to be called once in the lifecycle of the Chromatogram object
     */
    void setInCluster() {
        inCluster = true;
    }

    /**
     * Returns the value of the inCluster flag indicating whether or not the Chromatogram is already part of a PeakCluster
     *
     * @return the value of the inCluster flag
     */
    boolean getInCluster() {
        return inCluster;
    }

    /**
     * Returns the isobars found in this chromatogram.
     *
     * @return an ArrayList<Isobar> containing the isobars
     */
    public ArrayList<Isobar> getIsobars() {
        return isobars;
    }

    /**
     * Returns whether or not this chromatogram is part of a PeakCluster
     *
     * @return a boolean indication whether or not it is part of a cluster
     */
    public boolean isInCluster() {
        return inCluster;
    }

}
