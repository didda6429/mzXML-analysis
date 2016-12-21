package lsi.sling;

import com.google.common.collect.ArrayListMultimap;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class represents an mzXMLFile and the data within it. The intended use for this class is to store the data from multiple
 * files for the sample alignment algorithm to use
 * @author Adithya Diddapur
 */
public class MzXMLFile {

    public ArrayList<IScan> scanArrayList;
    ArrayList<LocalPeak> peakList;
    ArrayList<Chromatogram> chromatograms;
    public ArrayList<PeakCluster> peakClusters;
    String fileLocation;

    double threshold = 0;

    public MzXMLFile(String location, String dir, String adductFile, String compoundFile) throws FileParsingException, InterruptedException, IOException, ClassNotFoundException {
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
            scans.loadData(LCMSDataSubset.MS1_WITH_SPECTRA);
        } catch (FileParsingException e){
            System.out.println("FileParsingException line 51");
        }
        // let's traverse the data-structure
        TreeMap<Integer, IScan> num2scanMap = scans.getMapNum2scan();
        scanArrayList = new ArrayList<>(); //ArrayList containing only the data from the scans with spectrums
        for (IScan scan : num2scanMap.values()) {
            ISpectrum spectrum = scan.getSpectrum();
            if (spectrum != null && scan.getMsLevel().intValue()==1) {
                //System.out.printf("%s does NOT have a parsed spectrum\n", scan.toString());
                //System.out.printf("%s has a parsed spectrum, it contains %d data points\n",
                //        scan.toString(), spectrum.getMZs().length);
                scanArrayList.add(scan);
            }
        }


        //creates an ArrayList containing only the spectrum data from scanArrayList
        ArrayList<ISpectrum> spectrumArrayList = new ArrayList<>();
        for(IScan scan : scanArrayList){
            spectrumArrayList.add(scan.fetchSpectrum());
        }

        //Compiles all of the significant chromatograms (intensity>threshold) accross the entire dataset into a single ArrayList for later analysis
        peakList = localPeakList(scanArrayList,spectrumArrayList);

        //calculates the mean intensity of the LocalPeak objects and the value of mu+2sigma
        double mean = meanIntensity(peakList);
        double fivesd = mean + 5*intensityStandardDeviation(peakList,mean);
        //sets the threshold to be mu+2sigma for future steps
        threshold = fivesd;
        //filters the peakList so that only LocalPeaks with intensity>(mu+2sigma) are kept for further analysis
        peakList = (ArrayList<LocalPeak>) peakList.stream().filter(localPeak -> localPeak.getIntensity()>fivesd).collect(Collectors.toList());

        //iterates through peakList (which contains LocalPeak objects) to form the chromatograms. Note that they are in descending order (of max intensity)
        chromatograms = new ArrayList<>();
//        for(LocalPeak localPeak : peakList){
//            if(!localPeak.getIsUsed()){
//                chromatograms.add(new Chromatogram(scanArrayList,localPeak,20, threshold, Main.files.indexOf(this)));
//                peakList = chromatograms.get(chromatograms.size()-1).getPeakList();
//            }
//        }

        //chromatograms = Chromatogram.createChromatograms(this);


        peakClusters = new ArrayList<>(); //the arraylist which contains the PeakCluster objects
//
//        //loops through the chromatograms and forms the PeakCluster objects. Note that they are in descending order (of max intensity)
//        for (Chromatogram chromatogram : chromatograms){
//            if(!chromatogram.getInCluster()){
//                chromatogram.setInCluster();
//                peakClusters.add(new PeakCluster(chromatogram,20));
//            }
//        }
//
//        peakClusters = (ArrayList<PeakCluster>) peakClusters.parallelStream().filter(peakCluster -> peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).isValidStartingPoint()).collect(Collectors.toList());
//
//        double time1 = System.currentTimeMillis()-time;
//        System.out.println(time1);
//        AdductDatabase.createDatabase(databaseDir, adductFile, compoundFile);
//
//        peakClusters = mapClusters(peakClusters, databaseDir);

        time = System.currentTimeMillis()-time;
        System.out.println(time);
        System.out.println("test");
    }

    public MzXMLFile() {}

    /**
     * Takes all of the spectrum data from across the entire dataset and combines it into a single ArrayList. That
     * ArrayList is then sorted into descending order of intensity to help streamline downstream use. Note that all data
     * from the data set is included in this list, but it is expected that this list will be filtered further downstream
     * to remove the insignificant points
     * @param scanArrayList An ArrayList containing the data for all scans
     * @param spectra An ArrayList containing the data for all spectra
     * @return An ArrayList of LocalPeak objects containing all the significant chromatograms
     */
    static ArrayList<LocalPeak> localPeakList(ArrayList<IScan> scanArrayList, ArrayList<ISpectrum> spectra){
        ArrayList<LocalPeak> peakList = new ArrayList<>();
        for(int j=0; j<spectra.size(); j++){
            ISpectrum spectrum = spectra.get(j);
            double[] spec = spectrum.getIntensities();
            double[] mzVal = spectrum.getMZs();
            for(int i = 0; i<spectrum.getIntensities().length; i++){
                peakList.add(new LocalPeak(j,spec[i],mzVal[i],scanArrayList.get(j).getRt()));
            }
        }
        Collections.sort(peakList);
        return peakList;
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

    static double meanIntensity(ArrayList<LocalPeak> list){
        double sum = 0;
        for(LocalPeak peak : list){
            sum += peak.getIntensity();
        }
        return sum/list.size();
    }

    static double intensityStandardDeviation(ArrayList<LocalPeak> list, double mean){
        double sum = 0;
        for(LocalPeak peak : list){
            sum += (peak.getIntensity()-mean)*(peak.getIntensity()-mean);
        }
        sum = sum/list.size();
        return Math.sqrt(sum);
    }

    public ArrayList<LocalPeak> getPeakList() {
        return this.peakList;
    }
}
