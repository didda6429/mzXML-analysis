package lsi.sling;

import umich.ms.datatypes.scan.IScan;
import umich.ms.datatypes.spectrum.ISpectrum;
import umich.ms.fileio.exceptions.FileParsingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * This class represents an MS1 scan with it's corresponding MS2 scans. This class is only used in the MzXML File class
 * whilst parsing the mzXML files to improve the memory and runtime performance.
 *
 * @author Adithya Diddapur
 */
public class ScanCombination {

    private IScan MS1SCAN;
    private ArrayList<IScan> ms2Scans;
    private int ppm;
    private int orderedNumber; //used to create the localPeaks

    /**
     * Initialises the object with the ms1Scan and the ppm to use when mapping the ms2 peaks. The intention is for the
     * ms1Scans to be added later on.
     * @param ms1scan The initial ms1 scna which all ms2Scans 'belong' to
     * @param ppm The ppm to use when mapping the ms2Peaks
     * @param ms1ScanNum The scan number of the ms1 scan (ignoring the ms2 scan numbers). This is used when creating the chromatograms.
     */
    public ScanCombination(IScan ms1scan, int ppm, int ms1ScanNum){
        assert ms1scan.getNum() == 1; //checks that the scan really is a ms1 scan
        MS1SCAN = ms1scan;
        ms2Scans = new ArrayList<>();
        this.ppm = ppm;
        orderedNumber = ms1ScanNum;
    }

    /**
     * Add an ms2 Scan to the object. This method checks that it is actually an ms2Scan before adding it.
     * @param ms2scan the ms2Scan to add
     */
    public void addMs2Scan(IScan ms2scan){
        assert ms2scan.getMsLevel()==2; //checks that the scan really is a ms2 scan
        ms2Scans.add(ms2scan);
    }

    public int getMs1ScanNumber(){
        return MS1SCAN.getNum();
    }

    public IScan getMS1SCAN(){
        return MS1SCAN;
    }

    /**
     * Creates a list of the LocalPeak objects stored in the scans in this object. The LocalPeak objects in the returned
     * ArrayList contain both the ms1 and ms2 peaks as described in the LocalPeak class
     * @return An ArrayList containing LocalPeaks with both ms1 and ms2 data
     * @throws FileParsingException if there is a problem fetching the spectrum
     */
    public ArrayList<LocalPeak> createLocalPeaks() throws FileParsingException {
        ArrayList<LocalPeak> peakList = new ArrayList<>();
        ISpectrum spectrum = MS1SCAN.fetchSpectrum();
        //for loop to create all the MS1 Peaks
        for(int i = 0; i < spectrum.getIntensities().length; i++){
            //peakList.add(new LocalPeak(MS1SCAN.getNum(), spectrum.getIntensities()[i], spectrum.getMZs()[i], MS1SCAN.getRt()));
            peakList.add(new LocalPeak(orderedNumber, spectrum.getIntensities()[i], spectrum.getMZs()[i], MS1SCAN.getRt()));
        }
        //for loop to create and assign the MS2 Peaks
        for(IScan scan : ms2Scans){
            ISpectrum ms2Spectrum = scan.fetchSpectrum();
            double[] ms2MZs = ms2Spectrum.getMZs();
            double ms2PrecursorMZ = scan.getPrecursor().getMzTarget();
            double[] ms2Intensities = ms2Spectrum.getIntensities();
            int closestMS1Peak = findClosestMS1Peak(ms2PrecursorMZ, peakList);
            if(closestMS1Peak != -1) {
                for (int i = 0; i < ms2MZs.length; i++) {
                    //Should it use the ms1 or ms2 RT?
                    //peakList.get(closestMS1Peak).addFragment(new MS2Fragment(ms2Intensities[i], ms2MZs[i], scan.getRt()));
                    peakList.get(closestMS1Peak).addFragment(new MS2Fragment(ms2Intensities[i], ms2MZs[i], scan.getRt()));
                }
            }
        }
        return peakList;
    }

    /**
     * Finds the closest MS1 Peak to the given ms2 m/z so that the ms2 peak can be assigned to the corresponding LocalPeak object.
     * @param ms2MZ The ms2 m/z value to compare against
     * @param peakList The list of ms1 LocalPeak objects to search in
     * @return -1 if nothing is found, otherwise the index of the corresponding ms1 LocalPeak in peakList
     */
    private int findClosestMS1Peak(double ms2MZ, ArrayList<LocalPeak> peakList){
        //uses a stream to narrow down the list of LocalPeaks which need to be checked
        ArrayList<Double> distances = (ArrayList<Double>) peakList.stream().mapToDouble(p -> Math.abs(p.getMZ()-ms2MZ)).boxed().collect(Collectors.toList());
        double ppmTolerance = (ms2MZ/1e6)*ppm;
        //finds the minimum distance and compares it to the ppmtolerance to return the appropriate value
        int minIndex = distances.indexOf(Collections.min(distances));
        if(distances.get(minIndex)<ppmTolerance){
            return minIndex;
        } else {
            return -1;
        }
    }
}
