package lsi.sling;

import umich.ms.datatypes.LCMSDataSubset;
import umich.ms.datatypes.scan.IScan;
import umich.ms.datatypes.scan.StorageStrategy;
import umich.ms.datatypes.scancollection.impl.ScanCollectionDefault;
import umich.ms.datatypes.spectrum.ISpectrum;
import umich.ms.fileio.exceptions.FileParsingException;
import umich.ms.fileio.filetypes.mzxml.MZXMLFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * This class represents an mzXMLFile and the data within it. The intended use for this class is to store the data from multiple
 * files for the sample alignment algorithm to use
 * @author Adithya Diddapur
 */
public class MzXMLFile {

    private ArrayList<LocalPeak> localPeakList;
    ArrayList<Chromatogram> chromatograms;
    private ArrayList<PeakCluster> peakClusters;
    private String fileLocation;
    private ArrayList<ScanCombination> scanCombinations;

    double threshold = 0;

    public MzXMLFile(String location) throws FileParsingException, InterruptedException, IOException, ClassNotFoundException {
        MZXMLFile source = new MZXMLFile(location);
        long time = System.currentTimeMillis();
        fileLocation = location;
        scanCombinations = new ArrayList<>();
        // This is a data structure used to store scans and to navigate around the run
        ScanCollectionDefault scans = new ScanCollectionDefault();
        // Softly reference spectral data, make it reclaimable by GC
        scans.setDefaultStorageStrategy(StorageStrategy.SOFT);
        // Set it to automatically re-parse spectra from the file if spectra were not
        // yet parsed or were reclaimed to make auto-loading work you'll need to use
        // IScan#fetchSpectrum() method instead of IScan#getSpectrum()
        scans.isAutoloadSpectra(true);

        // Set our mzXML file as the data source for this scan collection
        scans.setDataSource(source);
        // Set number of threads for multi-threaded parsing.
        // null means use as many cores as reported by Runtime.getRuntime().availableProcessors()
        source.setNumThreadsForParsing(null);
        // load the meta-data about the whole run, with forced parsing of MS1 spectra
        // as we have enabled auto-loading, then if we ever invoke IScan#fetchSpectrum()
        // on an MS2 spectrum, for which the spectrum has not been parsed, it will be
        // obtained from disk automatically. And because of Soft referencing, the GC
        // will be able to reclaim it.
        try {
            scans.loadData(LCMSDataSubset.WHOLE_RUN);
        } catch (FileParsingException e){
            System.out.println("FileParsingException line 51");
        }
        // let's traverse the data-structure
        TreeMap<Integer, IScan> num2scanMap = scans.getMapNum2scan();
        int i = 0; //to iterate through the arraylist of ScanCombinations
        for (IScan scan : num2scanMap.values()) {
            ISpectrum spectrum = scan.getSpectrum();
            //if(spectrum==null){
            //    System.out.println("null");
            //}
            if (spectrum != null && scan.getMsLevel() == 1) {
                scanCombinations.add(new ScanCombination(scan, 20, i));
                i++;
                //ms1scanArrayList.add(scan);
            } if(spectrum != null && scan.getMsLevel() == 2){
                //sanity check to help prevent runtime bugs
                if(scanCombinations.get(i-1).getMs1ScanNumber()==scan.getPrecursor().getParentScanNum()){
                    scanCombinations.get(i-1).addMs2Scan(scan);
                }
                //ms2scanArrayList.add(scan);
            }
        }


        //creates an ArrayList containing only the spectrum data from ms1scanArrayList
        //ArrayList<ISpectrum> ms1SpectrumArrayList = new ArrayList<>();

        localPeakList = new ArrayList<>();
        for(ScanCombination combination : scanCombinations){
            localPeakList.addAll(combination.createLocalPeaks());
        }
        Collections.sort(localPeakList);

        //for(IScan scan : ms1scanArrayList){
        //    ms1SpectrumArrayList.add(scan.fetchSpectrum());
        //}

        //creates an ArrayList containing only the spectrum data from ms2scanArrayList
        /*ArrayList<ISpectrum> ms2SpectrumArrayList = new ArrayList<>();
        for(IScan scan : ms2scanArrayList){
            ms2SpectrumArrayList.add(scan.fetchSpectrum());
        }*/

        //calculates the mean intensity of the LocalPeak objects and the value of mu+2sigma
        double mean = meanIntensity();
        //sets the threshold to be mu+2sigma for future steps
        threshold = mean + 3*intensityStandardDeviation(mean);
        //filters the localPeakList so that only LocalPeaks with intensity>(mu+3sigma) are kept for further analysis.
        //Filtering the LocalPeaks here significantly improves downstream performance (when extracting the EICs)
        localPeakList = (ArrayList<LocalPeak>) localPeakList.stream().filter(localPeak -> localPeak.getIntensity()>threshold).collect(Collectors.toList());

        /*for(IScan ms1Scan : ms1scanArrayList){
            ArrayList<IScan> relevantMS2Scans = (ArrayList<IScan>) ms2scanArrayList.stream().filter(ms2Scan -> ms2Scan.getPrecursor().getParentScanNum()==ms1Scan.getNum()).collect(Collectors.toList());
            System.out.println("test");
        }*/

        /*System.out.println("time test " + System.currentTimeMillis());
        long timetest = System.currentTimeMillis();
        for(IScan ms2Scan : ms2scanArrayList){
            for(LocalPeak ms1LocalPeak : localPeakList.stream().filter(ms1peak -> ms1peak.getScanNumber()==ms2Scan.getPrecursor().getParentScanNum()).collect(Collectors.toList())){
                if(ms2Scan.getPrecursor().getMzTarget()==ms1LocalPeak.getMZ()){
                    ms1LocalPeak.setFragments(generateMS2Peaks(ms2Scan, ms1LocalPeak));
                }
            }
        }
        System.out.println(timetest);*/

        //iterates through localPeakList (which contains LocalPeak objects) to form the chromatograms. Note that they are in descending order (of max intensity)
        chromatograms = new ArrayList<>();
        createChromatograms();

        peakClusters = new ArrayList<>(); //the arraylist which contains the PeakCluster objects
        peakClusters = createPeakClusters();

        //peakClusters = (ArrayList<PeakCluster>) peakClusters.stream().filter(peakCluster -> peakCluster.getMainIntensity() > threshold).collect(Collectors.toList());

        //Clears up some memory after it's done using the scanCombinations objects
        scanCombinations.clear();
        System.gc();


        time = System.currentTimeMillis()-time;
        System.out.println(time);
        System.out.println("test");
    }

    /**
     * Takes all of the spectrum data from across the entire dataset and combines it into a single ArrayList. That
     * ArrayList is then sorted into descending order of intensity to help streamline downstream use. Note that all data
     * from the data set is included in this list, but it is expected that this list will be filtered further downstream
     * to remove the insignificant points
     * @param scanArrayList An ArrayList containing the data for all scans
     * @param spectra An ArrayList containing the data for all spectra
     * @return An ArrayList of LocalPeak objects containing all the significant chromatograms
     */
    private static ArrayList<LocalPeak> localPeakList(ArrayList<IScan> scanArrayList, ArrayList<ISpectrum> spectra){
        ArrayList<LocalPeak> peakList = new ArrayList<>();
        for(int j=0; j<spectra.size(); j++){
            ISpectrum spectrum = spectra.get(j);
            double[] spec = spectrum.getIntensities();
            double[] mzVal = spectrum.getMZs();
            for(int i = 0; i<spectrum.getIntensities().length; i++){
                peakList.add(new LocalPeak(j,spec[i],mzVal[i],scanArrayList.get(j).getRt()));
            }
        }
        Collections.sort(peakList); //sorts it in descending order of intensity
        return peakList;
    }

    /**
     * Given an ms2Scan and it's corresponding ms1Scan, this method converts the scan data into an arraylist of MS2Fragment
     * items which can be added to the relevant LocalPeak object.
     * @param ms2Scan The ms2Scan to "convert"
     * @param ms1Peak The corresponding ms1LocalPeak
     * @return and ArrayList containing the generated MS2Fragment objects
     * @throws FileParsingException if there is an error fetching the spectra
     */
    private static ArrayList<MS2Fragment> generateMS2Peaks(IScan ms2Scan, LocalPeak ms1Peak) throws FileParsingException {
        ArrayList<MS2Fragment> ms2LocalPeaks = new ArrayList<>();
        ISpectrum spectrum = ms2Scan.fetchSpectrum();
        for(int i=0; i<spectrum.getIntensities().length; i++){
            ms2LocalPeaks.add(new MS2Fragment(spectrum.getIntensities()[i], spectrum.getMZs()[i],ms1Peak.getRT()));
        }
        return ms2LocalPeaks;
    }

    private void createChromatograms() throws FileParsingException {
        for(LocalPeak localPeak : localPeakList){
            if(!localPeak.getIsUsed()){
                //iteratively creates recursive chromatograms from all localPeaks
                //intensities below mu+5sigma should have already been filtered out
                ArrayList<IScan> scanList = (ArrayList<IScan>) scanCombinations.stream().map(ScanCombination::getMS1SCAN).collect(Collectors.toList());
                chromatograms.add(new Chromatogram(scanList, localPeak, 20, threshold, localPeakList));
                //chromatograms.add(new Chromatogram(ms1scanArrayList, localPeak, 20, threshold, localPeakList));
            }
        }
    }

    /**
     * This method acts as a wrapper to create a list of PeakClusters for a given file. In normal use, this method should
     * be the only necessary point of access into this class. Note: This method does NOT map the clusters to their adducts,
     * that functionality is handled by the mapCluster method in the AdductDatabase class
     */
    private ArrayList<PeakCluster> createPeakClusters() {
        ArrayList<Chromatogram> chromatograms = this.chromatograms;
        ArrayList<PeakCluster> clusters = new ArrayList<>();
        for(Chromatogram chromatogram : chromatograms){
            //iteratively loops through each unused chromatogram so that eventually every chromatogram is used
            if(!chromatogram.getInCluster()){
                chromatogram.setInCluster();
                clusters.add(new PeakCluster(chromatogram, 20, this));
            }
        }
        //filters out the invalid peakClusters (based on starting point)
        //this filtering happens at the end to ensure low-abundance isotopes aren't missed
        clusters = (ArrayList<PeakCluster>) clusters.stream().filter(peakCluster -> peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).isValidStartingPoint()).collect(Collectors.toList());
        return clusters;
    }

    /**
     * Calculates the meanIntensity of ALL LocalPeaks in this file. This value is later used to calculate mu+5sigma which
     * is used as the noise/signal ration
     * @return the mean intensity
     */
    private double meanIntensity(){
        double sum = 0;
        for(LocalPeak peak : localPeakList){
            sum += peak.getIntensity();
        }
        return sum/ localPeakList.size();
    }

    /**
     * Calculates the meanIntensity of ALL LocalPeaks in this file. This value is later used to calculate mu+5sigma which
     * is used as the noise/signal ration
     * @return the mean intensity
     */
    private double intensityStandardDeviation(double mean){
        double sum = 0;
        for(LocalPeak peak : localPeakList){
            sum += (peak.getIntensity()-mean)*(peak.getIntensity()-mean);
        }
        sum = sum/ localPeakList.size();
        return Math.sqrt(sum);
    }

    public ArrayList<LocalPeak> getLocalPeakList() {
        return localPeakList;
    }

    /**
     * This method is used in the main method to map the adducts
     * @param peakClusters The modified list of PeakClusters to save
     */
    void setPeakClusters(ArrayList<PeakCluster> peakClusters) {
        this.peakClusters = peakClusters;
    }

    ArrayList<PeakCluster> getPeakClusters() {
        return peakClusters;
    }

}
