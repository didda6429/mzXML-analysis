package lsi.sling;

import com.opencsv.CSVWriter;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
            file.setPeakClusters(AdductDatabase.mapClusters(file, databaseDir));
            //file.getPeakClusters() = AdductDatabase.mapClusters(file.peakClusters, databaseDir);
        }

        //THE FOLLOWING CODE CHUNK DEALS WITH THE SAMPLE ALIGNMENT PROCESS
        ArrayList<PeakCluster> allPeakClusters = new ArrayList<>();
        //Stores ALL peak clusters across all samples in a single list for downstream clustering and alignment
        for(MzXMLFile file : files){
            for(PeakCluster cluster : file.getPeakClusters()) {
                allPeakClusters.add(cluster);
            }
        }

        //Finds the max and min RT and m/z values to use when rescaling the locations of the clusters (to use the euclidean distance)
        double mzMin = allPeakClusters.stream().mapToDouble(PeakCluster::getMainMZ).min().getAsDouble();
        double mzMax = allPeakClusters.stream().mapToDouble(PeakCluster::getMainMZ).max().getAsDouble();
        double rtMin = allPeakClusters.stream().mapToDouble(PeakCluster::getMainRT).min().getAsDouble();
        double rtMax = allPeakClusters.stream().mapToDouble(PeakCluster::getMainRT).max().getAsDouble();

        //Sets the rescaled values (to use the euclidean distance when clustering)
        for(PeakCluster cluster : allPeakClusters){
            cluster.setRescaledValues(mzMax, mzMin, rtMax, rtMin);
        }

        //Peforms the clustering and stores the results in a list
        DBSCANClusterer<PeakCluster> clusterer = new DBSCANClusterer<>(0.005, files.size()-2); //epsilon=0.005 works quite well
        List<Cluster<PeakCluster>> clusterResults = clusterer.cluster(allPeakClusters);

        //'converts' the Cluster objects returned from the DBSCANClusterer to AlignedPeakCluster objects and stores them in alignedPeakClusters
        ArrayList<AlignedPeakCluster> alignedPeakClusters = new ArrayList<>();

        for(Cluster<PeakCluster> cluster : clusterResults){
            alignedPeakClusters.add(new AlignedPeakCluster(cluster.getPoints(), 20));
        }
        for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
            AdductDatabase.mapClusters(alignedPeakCluster, databaseDir);
        }

        PeakCluster start = files.get(0).getPeakClusters().get(0);
        System.out.println(System.currentTimeMillis()-time);
        System.out.println("test");
    }

    /**
     * For testing
     * @param clusterArrayList The clusters to write
     * @throws IOException If there is an error with the file handling
     */
    static void writeToCSV(ArrayList<PeakCluster> clusterArrayList) throws IOException {
        CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File("test.csv"))));

        for(PeakCluster cluster : clusterArrayList){
            csvWriter.writeNext(new String[]{String.valueOf(cluster.getChromatograms().get(cluster.getStartingPointIndex()).getMeanMZ()), String.valueOf(cluster.getChromatograms().get(cluster.getStartingPointIndex()).getStartingPointRT())});
        }
        csvWriter.close();
    }
}
