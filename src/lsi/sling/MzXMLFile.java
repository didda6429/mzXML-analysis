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

    private ArrayList<IScan> ms1scanArrayList;
    private ArrayList<IScan> ms2scanArrayList;
    private ArrayList<LocalPeak> ms1PeakList;
    private ArrayList<LocalPeak> ms2PeakList;
    ArrayList<Chromatogram> chromatograms;
    private ArrayList<PeakCluster> peakClusters;
    private String fileLocation;

    double threshold = 0;

    public MzXMLFile(String location) throws FileParsingException, InterruptedException, IOException, ClassNotFoundException {
        MZXMLFile source = new MZXMLFile(location);
        long time = System.currentTimeMillis();
        fileLocation = location;
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
        ms1scanArrayList = new ArrayList<>(); //ArrayList containing only the data from the scans with spectrums
        ms2scanArrayList = new ArrayList<>();
        for (IScan scan : num2scanMap.values()) {
            ISpectrum spectrum = scan.getSpectrum();
            //if(spectrum==null){
            //    System.out.println("null");
            //}
            if (spectrum != null && scan.getMsLevel() == 1) {
                //System.out.printf("%s does NOT have a parsed spectrum\n", scan.toString());
                //System.out.printf("%s has a parsed spectrum, it contains %d data points\n",
                //        scan.toString(), spectrum.getMZs().length);
                ms1scanArrayList.add(scan);
            } if(spectrum != null && scan.getMsLevel() == 2){
                ms2scanArrayList.add(scan);
            }
        }


        //creates an ArrayList containing only the spectrum data from ms1scanArrayList
        ArrayList<ISpectrum> ms1SpectrumArrayList = new ArrayList<>();
        for(IScan scan : ms1scanArrayList){
            ms1SpectrumArrayList.add(scan.fetchSpectrum());
        }

        //creates an ArrayList containing only the spectrum data from ms2scanArrayList
        ArrayList<ISpectrum> ms2SpectrumArrayList = new ArrayList<>();
        for(IScan scan : ms2scanArrayList){
            ms2SpectrumArrayList.add(scan.fetchSpectrum());
        }

        //Compiles all of the local peaks from across the entire file into a single ArrayList for later analysis
        ms1PeakList = localPeakList(ms1scanArrayList,ms1SpectrumArrayList);
        ms2PeakList = localPeakList(ms2scanArrayList, ms2SpectrumArrayList);

        //calculates the mean intensity of the LocalPeak objects and the value of mu+2sigma
        double mean = meanIntensity();
        //sets the threshold to be mu+2sigma for future steps
        threshold = mean + 5*intensityStandardDeviation(mean);
        //filters the ms1PeakList so that only LocalPeaks with intensity>(mu+2sigma) are kept for further analysis
        //ms1PeakList = (ArrayList<LocalPeak>) ms1PeakList.stream().filter(localPeak -> localPeak.getIntensity()>fivesd).collect(Collectors.toList());

        //iterates through ms1PeakList (which contains LocalPeak objects) to form the chromatograms. Note that they are in descending order (of max intensity)
        chromatograms = new ArrayList<>();
        createChromatograms();

        peakClusters = new ArrayList<>(); //the arraylist which contains the PeakCluster objects
        peakClusters = createPeakClusters();

        peakClusters = (ArrayList<PeakCluster>) peakClusters.stream().filter(peakCluster -> peakCluster.getMainIntensity() > threshold).collect(Collectors.toList());

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

    private void createChromatograms() throws FileParsingException {
        for(LocalPeak localPeak : ms1PeakList){
            if(!localPeak.getIsUsed()){
                //iteratively creates recursive chromatograms from all localPeaks
                //intensities below mu+5sigma should have already been filtered out
                chromatograms.add(new Chromatogram(ms1scanArrayList, localPeak, 20, threshold, ms1PeakList));
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
        clusters = (ArrayList<PeakCluster>) clusters.parallelStream().filter(peakCluster -> peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).isValidStartingPoint()).collect(Collectors.toList());
        return clusters;
    }

    /**
     * Calculates the meanIntensity of ALL LocalPeaks in this file. This value is later used to calculate mu+5sigma which
     * is used as the noise/signal ration
     * @return the mean intensity
     */
    private double meanIntensity(){
        double sum = 0;
        for(LocalPeak peak : ms1PeakList){
            sum += peak.getIntensity();
        }
        return sum/ ms1PeakList.size();
    }

    /**
     * Calculates the meanIntensity of ALL LocalPeaks in this file. This value is later used to calculate mu+5sigma which
     * is used as the noise/signal ration
     * @return the mean intensity
     */
    private double intensityStandardDeviation(double mean){
        double sum = 0;
        for(LocalPeak peak : ms1PeakList){
            sum += (peak.getIntensity()-mean)*(peak.getIntensity()-mean);
        }
        sum = sum/ ms1PeakList.size();
        return Math.sqrt(sum);
    }

    public ArrayList<LocalPeak> getMs1PeakList() {
        return ms1PeakList;
    }

    public ArrayList<LocalPeak> getMs2PeakList() {
        return ms2PeakList;
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

    /**
     * Used to rescale the m/z and RT to use the euclidean distance in the clustering step
     * @return the minimum m/z value from this MzXMLfile
     */
    public double getMinMZ(){
        return ms1scanArrayList.stream().mapToDouble(IScan::getBasePeakMz).min().orElseThrow(() -> new ArithmeticException("no minimum m/z"));
    }

    /**
     * Used to rescale the m/z and RT to use the euclidean distance in the clustering step
     * @return the maximum m/z value from this MzXMLfile
     */
    public double getMaxMZ(){
        return ms1scanArrayList.stream().mapToDouble(IScan::getBasePeakMz).max().orElseThrow(() -> new ArithmeticException("no maximum m/z"));
    }

    /**
     * Used to rescale the m/z and RT to use the euclidean distance in the clustering step
     * @return the minimum RT value from this MzXMLfile
     */
    public double getMinRT(){
        return ms1scanArrayList.stream().mapToDouble(IScan::getRt).min().orElseThrow(() -> new ArithmeticException("no minimum rt"));
    }

    /**
     * Used to rescale the m/z and RT to use the euclidean distance in the clustering step
     * @return the maximum RT value from this MzXMLfile
     */
    public double getMaxRT(){
        return ms1scanArrayList.stream().mapToDouble(IScan::getRt).max().orElseThrow(() -> new ArithmeticException("no maximum rt"));
    }
}
