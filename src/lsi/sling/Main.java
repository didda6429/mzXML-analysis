package lsi.sling;

import umich.ms.fileio.exceptions.FileParsingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    static String databaseDir = "C:/Users/lsiv67/Documents/mzXML Sample Data/databaseFiles";
    static String mzXMLFileDir = "C:/Users/lsiv67/Documents/mzXML Sample Data/DDApos/";
    //static String mzXMLFileDir = "C:/Users/lsiv67/Documents/mzXML Sample Data";
//    //static String databaseDir = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/adductDatabase/database";
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
        File[] mzXMLFiles = new File(mzXMLFileDir).listFiles(f -> f.getName().endsWith(".mzXML"));
        //used for debugging
        //files.add(new MzXMLFile("C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML", databaseDir, adductFile, compoundFile));
        //files.add(new MzXMLFile("C:/Users/lsiv67/Documents/mzXML Sample Data/DDApos/CS52684_pos_IDA.mzXML", databaseDir, adductFile, compoundFile));
        assert mzXMLFiles != null : "Main line 48";
        AdductDatabase.createDatabase(databaseDir,adductFile,compoundFile);
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        //ExecutorService executorService = Executors.newWorkStealingPool();
        for(File file : mzXMLFiles){
            Runnable task = () -> {
                try {
                    files.add(new MzXMLFile(file.getAbsolutePath(), databaseDir, adductFile, compoundFile));
                } catch (FileParsingException | InterruptedException | IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            };
            executorService.submit(task);
        }
        executorService.shutdown();
        executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);

        System.out.println("test");

        //maps each peak cluster in each file to it's adducts
        //this task is inherrently parallel, so each file is called sequentially and then the function acts concurrently
        for(MzXMLFile file : files) {
            file.setPeakClusters(AdductDatabase.mapClusters(file.getPeakClusters(), databaseDir));
            //file.getPeakClusters() = AdductDatabase.mapClusters(file.peakClusters, databaseDir);
        }

        //ExecutorService service = Executors.newFixedThreadPool(8);
        //for (MzXMLFile file : files) {
            //file.chromatograms = Chromatogram.createChromatograms(file);
            //file.createChromatograms();
        //    Runnable task = () -> {
        //        try {
        //            file.peakClusters = AdductDatabase.mapClusters(file.peakClusters, databaseDir);
        //        } catch (InterruptedException | IOException | ClassNotFoundException e){
        //            e.printStackTrace();
        //        }
                /*try {
                    //file.createChromatograms();
                    //file.createPeakClusters(databaseDir);
                } catch (InterruptedException | ClassNotFoundException | IOException e) {
                   e.printStackTrace();
                }*/
        //    };
         //   service.submit(task);
        //}
        //service.shutdown();
        //service.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);

        //AdductDatabase.createDatabase(databaseDir,adductFile,compoundFile);
        //for (MzXMLFile file : files) {
            //file.peakClusters = PeakCluster.createPeakClusters(file, databaseDir);
            //file.createPeakClusters(databaseDir);
        //}

        //THE FOLLOWING CODE CHUNK DEALS WITH THE SAMPLE ALIGNMENT PROCESS

        ArrayList<AlignedPeakCluster> alignedPeakClusters = new ArrayList<>();
        for(PeakCluster cluster : files.get(0).getPeakClusters()){
            alignedPeakClusters.add(AlignedPeakCluster.alignPeaks(new ArrayList<>(files.subList(1, files.size())), cluster, 100, 0.5));
        }
        //for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
        //    alignedPeakCluster.setPossibleClusters(AdductDatabase.mapAlignedClusters());
        //}
        alignedPeakClusters = AdductDatabase.mapAlignedClusters(alignedPeakClusters, databaseDir);
        PeakCluster start = files.get(0).getPeakClusters().get(0);
        AlignedPeakCluster.alignPeaks(new ArrayList<>(files.subList(1,files.size())), start, 100, 0.5);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println("test");
    }
}