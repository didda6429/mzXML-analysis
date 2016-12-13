package lsi.sling;

import umich.ms.datatypes.LCMSDataSubset;
import umich.ms.datatypes.scan.IScan;
import umich.ms.datatypes.scan.StorageStrategy;
import umich.ms.datatypes.scancollection.impl.ScanCollectionDefault;
import umich.ms.datatypes.spectrum.ISpectrum;
import umich.ms.fileio.exceptions.FileParsingException;
import umich.ms.fileio.filetypes.mzxml.MZXMLFile;

import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Collections;

//"S:\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML"

public class Main {

    public static ArrayList<IScan> scanArrayList;
    public static ArrayList<LocalPeak> peakList;
    public static ArrayList<Chromatogram> chromatograms;
    public static ArrayList<PeakCluster> peakClusters;
    //static String location = "S:\\mzXML Sample Data\\7264381_RP_pos.mzXML";
    static String location = "C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML";
    //static String location = "C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML";

    public static void main(String[] args) throws FileParsingException, IOException, ScriptException {

        // Creating data source
        Path path = Paths.get(location);
        MZXMLFile source = new MZXMLFile(path.toString());
        long time = System.currentTimeMillis();

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
            if (spectrum != null) {
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
        peakList = localPeakList(scanArrayList,spectrumArrayList,500);

        chromatograms = new ArrayList<>();
        for(LocalPeak localPeak : peakList){
            if(!localPeak.getIsUsed()){
                chromatograms.add(new Chromatogram(scanArrayList,localPeak,40,1000,10)); //requires 10 points to form a chromatogram
            }
        }

        //ArrayList test = new ArrayList();

        /*for(Chromatogram chromatogram : chromatograms){
            if(Math.abs(chromatogram.getStartingPointRT()-14.9)<0.5 && Math.abs(chromatogram.getMeanMZ()-521)<5)
                test.add(chromatogram);
        }*/

        //removes invalid chromatograms based on the method in the Chromatogram class
        /*for(int i=0; i<chromatograms.size(); i++){
            if(!chromatograms.get(i).isValidStartingPoint()){
                chromatograms.remove(i);
            }
        }*


        System.out.println(chromatograms.get(0).getIntensityScanPairs().size());

        /*for(int i=0; i<chromatograms.size(); i++) {
            if (chromatograms.get(i).getPointsOfInflection().size() >= 1) {
                try {
                    chromatograms.get(i).smoothToFindMinima();
                    chromatograms.get(i).writeToCSV();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }*/

        peakClusters = new ArrayList<>();
        ArrayList doubles = new ArrayList(); //only used for testing
//        ArrayList test = new ArrayList();

        for (Chromatogram chromatogram : chromatograms){
            if(!chromatogram.getInCluster()){
                chromatogram.setInCluster();
                peakClusters.add(new PeakCluster(chromatogram,20));
            }
        }

        //removes clusters (as invalid) if the starting chromatogram is an invalid chromatogram. The validity of the
        //chromatogram is determined by the isValidStartingPoint() method in the Chromatogram class
        for(int i=0; i<peakClusters.size(); i++){
            if(!peakClusters.get(i).getChromatograms().get(peakClusters.get(i).getStartingPointIndex()).isValidStartingPoint()){
                peakClusters.remove(i);
                i--;
            }
        }

        for(int i=0; i<peakClusters.size(); i++){
            if(peakClusters.get(i).getChromatograms().size()==1){
                System.out.println(peakClusters.get(i).getCharge() + "       " + i);
                doubles.add(i);
            }
        }

//        for(Chromatogram chromatogram : chromatograms){
//            if(Math.abs(chromatogram.getStartingPointRT()-14.9)<0.5 && Math.abs(chromatogram.getMeanMZ()-521)<5)
//                test.add(chromatogram);
//        }
        //doubles.clear();
        /*for(PeakCluster peakCluster : peakClusters){
            if(peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).getMeanMZ()<814.8&&peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).getMeanMZ()>814.5){
                doubles.add(peakCluster);
            }
        }*/

        /*for(Chromatogram chromatogram : chromatograms){
            if(chromatogram.getMeanMZ()<521.5&&chromatogram.getMeanMZ()>521){
                chromatogram.writeToCSV();
            }
        }*/

        /*for(int i=0; i<10; i++){
            chromatograms.get(i).plotSmoothToFindMinima();
        }*/
        double time1 = System.currentTimeMillis()-time;
        List temp = adductDatabase.createDatabase();

        time = System.currentTimeMillis()-time;
        System.out.println(time);
        System.out.println("test");
    }

    /**
     * Takes all of the spectrum data from across the entire dataset and combines it into a single ArrayList. That
     * ArrayList is then sorted into descending order of intensity to help streamline downstream use. Also, only significant
     * values (intensity>threshold) are added to the ArrayList, everything else is discarded as noise
     * @param scanArrayList An ArrayList containing the data for all scans
     * @param spectra An ArrayList containing the data for all spectra
     * @param threshold The threshold used to determine whether or not a peak is noise or signal
     * @return An ArrayList of LocalPeak objects containing all the significant chromatograms
     */
    static ArrayList<LocalPeak> localPeakList(ArrayList<IScan> scanArrayList, ArrayList<ISpectrum> spectra, double threshold){
        ArrayList<LocalPeak> peakList = new ArrayList<>();
        for(int j=0; j<spectra.size(); j++){
            ISpectrum spectrum = spectra.get(j);
            double[] spec = spectrum.getIntensities();
            double[] mzVal = spectrum.getMZs();
            for(int i = 0; i<spectrum.getIntensities().length; i++){
                if(spec[i]>threshold){
                    peakList.add(new LocalPeak(j,spec[i],mzVal[i],scanArrayList.get(j).getRt()));
                }
            }
        }
        Collections.sort(peakList);
        return peakList;
    }
}