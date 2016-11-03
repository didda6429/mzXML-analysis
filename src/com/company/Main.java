package com.company;
import umich.ms.datatypes.LCMSDataSubset;
import umich.ms.datatypes.scan.IScan;
import umich.ms.datatypes.scan.StorageStrategy;
import umich.ms.datatypes.scancollection.impl.ScanCollectionDefault;
import umich.ms.datatypes.spectrum.ISpectrum;
import umich.ms.fileio.exceptions.FileParsingException;
import umich.ms.fileio.filetypes.mzxml.MZXMLFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.TreeMap;

//"S:\\mzXML Sample Data\\7264381_RP_pos.mzXML"

public class Main {

    public static ArrayList<IScan> scanArrayList;
    static String location = "S:\\mzXML Sample Data\\7264381_RP_pos.mzXML";

    public static void main(String[] args) throws FileParsingException {

        // Creating data source
        Path path = Paths.get(location);
        MZXMLFile source = new MZXMLFile(path.toString());

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
        int i=0;
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
                i++;
            }
        }


        //creates an ArrayList containing only the spectrum data from scanArrayList
        ArrayList<ISpectrum> spectrumArrayList = new ArrayList<>();
        for(IScan scan : scanArrayList){
            spectrumArrayList.add(scan.fetchSpectrum());
        }

        //finds the maximum intensity peak across the entire database
        // NOTE --> this is only being used for testing at the moment
        LocalPeak maxPeak = maxIntensityPeak(spectrumArrayList,400,50);


        //LocalPeak maxPeak = Peak.maxIntWithinTol(spectrumArrayList.get(540),616.2,400,540,scanArrayList.get(540).getRt());
        //LocalPeak maxPeak = Peak.maxIntWithinTol(spectrumArrayList.get(spec1),location,400,spec1,time); //finds the globally maximum peak
        Peak peak1 = new Peak(scanArrayList, maxPeak, 400, 500);
        System.out.println(peak1.getIntensityScanPairs().size());
        System.out.println("test");
    }

    /**
     * Function which scans the dataset to find the maximum intensity datapoints. The number of peaks found
     * is (approximately?) equal to the value of numOfPeaks and then only the single highest value is returned.
     * @param spectra an ArrayList containing all the spectrum data from all the scans
     * @param tol The tolerance to jitter (passed to a call to Peak.maxIntWithinTol)
     * @param numOfPeaks The number of peaks to look for
     * @return The highest intensity peak across the entire dataset
     */
    static LocalPeak maxIntensityPeak(ArrayList<ISpectrum> spectra, double tol, int numOfPeaks){
        //LinkedList which stores the maximum peaks
        //In this usage, the LinkedList is treated as a FILO stack
        LinkedList<LocalPeak> tempList = new LinkedList<>();
        for(int i=0; i<numOfPeaks; i++){
            tempList.push(Peak.maxIntWithinTol(spectra.get(1),spectra.get(1).getMZs()[0],tol,1,scanArrayList.get(1).getRt()));
        }
        //loops which find the maximum intensities and add them to the stack (LinkedList)
        for(int i = 0; i<spectra.size(); i++){
            double maxV = spectra.get(i).getMaxInt();
            double loc = spectra.get(i).getMaxIntMz();
            for(int j=0; j<numOfPeaks-1; j++) {
                if (maxV > tempList.get(j).getIntensity()) {
                    tempList.add(j,Peak.maxIntWithinTol(spectra.get(i), loc, tol, i, scanArrayList.get(i).getRt()));
                    j=11;
                }
            }
        }
        //return Peak.maxIntWithinTol(spectra.get(spec1),location,tol,spec1,time);
        //for(int i=tempList.size(); i>tempList.size()-10; i--){
        //    tempList.remove(i-1);
        //}
        return tempList.pop();
    }
}