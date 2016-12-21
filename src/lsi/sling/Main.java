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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//"S:\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML"

public class Main {

//    public static ArrayList<IScan> scanArrayList;
//    public static ArrayList<LocalPeak> peakList;
//    public static ArrayList<Chromatogram> chromatograms;
//    public static ArrayList<PeakCluster> peakClusters;
//    //static String location = "S:\\mzXML Sample Data\\7264381_RP_pos.mzXML";
      //static String location = "C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML";
//    static String location = "C:/Users/lsiv67/Documents/DDApos/CS52684_pos_IDA.mzXML";
//    //static String location = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/7264381_RP_pos.mzXML";
//    //static String location = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/PH697085_pos_IDA.mzXML";
//    //static String location = "C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML";
    static String dir = "C:/Users/lsiv67/Documents/mzXML Sample Data/databaseFiles";
//    //static String dir = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/adductDatabase/database";
//
    static String adductFile = "C:/Users/lsiv67/Documents/mzXML Sample Data/Adducts.csv";
    static String compoundFile = "C:/Users/lsiv67/Documents/mzXML Sample Data/Database.csv";
//    //static String adductFile = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/adductDatabase/Adducts.csv";
//    //static String compoundFile = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/adductDatabase/Database.csv";
//
//    static double threshold = 0;
    public static ArrayList<MzXMLFile> files;

    public static void main(String[] args) throws FileParsingException, IOException, ClassNotFoundException, InterruptedException {
        double time = System.currentTimeMillis();
        files = new ArrayList<>();
        files.add(new MzXMLFile("C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML", dir, adductFile, compoundFile));
        files.add(new MzXMLFile("C:/Users/lsiv67/Documents/DDApos/CS52684_pos_IDA.mzXML", dir, adductFile, compoundFile));
        System.out.println("test");
        for (MzXMLFile file : files) {
            file.chromatograms = Chromatogram.createChromatograms(file);
        }
        for (MzXMLFile file : files) {
            file.peakClusters = PeakCluster.createPeakClusters(file, dir);
        }
        System.out.println(System.currentTimeMillis()-time);
        System.out.println("test");
    }
}