package lsi.sling;

import com.opencsv.CSVWriter;
import lsi.sling.FragmentHandling.AlignedFragmentCluster;
import lsi.sling.FragmentHandling.MS2Fragment;
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
import java.util.stream.Collectors;

//"S:\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML"
//"C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML"

public class Main {

//    public static ArrayList<IScan> ms1scanArrayList;
//    public static ArrayList<LocalPeak> ms1PeakList;
//    public static ArrayList<Chromatogram> chromatograms;
//    public static ArrayList<PeakCluster> peakClusters;
//    //static String location = "S:\\mzXML Sample Data\\7264381_RP_pos.mzXML";
      //static String location = "C:\\Users\\lsiv67\\Documents\\mzXML Sample Data\\7264381_RP_pos.mzXML";
//    static String location = "C:/Users/lsiv67/Documents/DDApos/CS52684_pos_IDA.mzXML";
//    //static String location = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/7264381_RP_pos.mzXML";
//    //static String location = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/PH697085_pos_IDA.mzXML";
//    //static String location = "C:\\Users\\adith\\Desktop\\mzxml sample data\\7264381_RP_pos.mzXML";
    //static String databaseDir = "C:/Users/lsiv67/Documents/mzXML Sample Data/databaseFiles";
    static String databaseDir = "D:/lsiv67/mzXML Sample Data/databaseFiles";
    //static String mzXMLFileDir = "C:/Users/lsiv67/Documents/mzXML Sample Data/DDApos/";
    static String mzXMLFileDir = "D:/lsiv67/mzXML Sample Data/DDApos";
    //static String mzXMLFileDir = "D:/lsiv67/mzXML Sample Data/temp";
    //static String mzXMLFileDir = "C:/Users/lsiv67/Documents/mzXML Sample Data";
//    //static String databaseDir = "C:/Users/Adithya Diddapur/Documents/mzXML sample files/adductDatabase/database";
//
    //static String adductFile = "C:/Users/lsiv67/Documents/mzXML Sample Data/Adducts.csv";
    static String adductFile = "D:/lsiv67/mzXML Sample Data/Adducts.csv";
    //static String compoundFile = "C:/Users/lsiv67/Documents/mzXML Sample Data/Database.csv";
    static String compoundFile = "D:/lsiv67 mzXML Sample Data/Database.csv";
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
                    files.add(new MzXMLFile(file.getAbsolutePath()));
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
            allPeakClusters.addAll(file.getPeakClusters());
        }

        //Finds the max and min RT and m/z values to use when rescaling the locations of the clusters (to use the euclidean distance)
        //If there is an error in the stream (the min or max can't be found), return -1
        double mzMin = allPeakClusters.stream().mapToDouble(PeakCluster::getMainMZ).min().orElse(-1);
        double mzMax = allPeakClusters.stream().mapToDouble(PeakCluster::getMainMZ).max().orElse(-1);
        double rtMin = allPeakClusters.stream().mapToDouble(PeakCluster::getMainRT).min().orElse(-1);
        double rtMax = allPeakClusters.stream().mapToDouble(PeakCluster::getMainRT).max().orElse(-1);

        assert mzMax != -1: "No mzMax";
        assert mzMin != -1: "No mzMin";
        assert rtMin != -1: "No rtMin";
        assert rtMax != -1: "No rtMax";

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

        //Cluster the fragments in each individual peakCluster
        for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
            for(PeakCluster cluster : alignedPeakCluster.getClusters()){
                cluster.clusterFragments();
            }
            //TODO: cluster the fragments within each peakCluster
            //for now just collates them
            alignedPeakCluster.clusterFragments();
            
        }

        //ArrayList<LocalPeak> ms2LocalPeaks = new ArrayList<>();
        //for(MzXMLFile file : files){
        //    ms2LocalPeaks.addAll(file.getMs2PeakList());
        //}
        //writeLocalPeakListToCSV(ms2LocalPeaks);

        //ArrayList<AlignedPeakCluster> withFragments = numberPeakClustersWithFragments(alignedPeakClusters);

        //writeAlignedPeakClusterFragmentsToCSV(alignedPeakClusters.get(1), "D:/lsiv67/mzXML Sample Data/alignedTestData/");
        for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
            //writeAlignedPeakClusterFragmentsToCSV(alignedPeakCluster, "D:/lsiv67/mzXML Sample Data/alignedTestData/");
            writeAlignedPeakClusterFragmentsToCSV(alignedPeakCluster, "D:/lsiv67/mzXML Sample Data/AlignedFragments/");
        }
        //writeMS2PeakClustersToCSV(allPeakClusters, "D:/lsiv67/mzXML Sample Data/testdata/");
        ArrayList<PeakCluster> clustersWithFragments = (ArrayList<PeakCluster>) allPeakClusters.stream().filter(p -> p.getFragmentClusters().size()>0).collect(Collectors.toList()); //for debugging
        System.out.println(System.currentTimeMillis()-time);
        time = System.currentTimeMillis()-time;
        System.out.println("test");
    }

    static ArrayList<AlignedPeakCluster> numberPeakClustersWithFragments(ArrayList<AlignedPeakCluster> peakClusters){
        ArrayList<AlignedPeakCluster> toReturn = new ArrayList<>();
        for(AlignedPeakCluster alignedPeakCluster : peakClusters){
            if(alignedPeakCluster.getClusters().stream().filter(p -> p.getFragmentClusters().size()>0).count()>0){
                toReturn.add(alignedPeakCluster);
            }
        }
        return toReturn;
    }

    static void writeLocalPeakListToCSV(ArrayList<LocalPeak> peakList) throws IOException{
        CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File("S:/mzXML Sample Data/localpeak.csv"))));
        for(LocalPeak localPeak : peakList){
            csvWriter.writeNext(new String[]{String.valueOf(localPeak.getMZ()), String.valueOf(localPeak.getRT()), String.valueOf(localPeak.getIntensity())});
        }
        csvWriter.close();
    }

    static void writeAlignedPeakClusterFragmentsToCSV(AlignedPeakCluster alignedPeakCluster, String folder) throws IOException{
        if(alignedPeakCluster.getAlignedFragmentClusters().size()>0) {
            int i = 0;
            CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File(folder + alignedPeakCluster.getMeanMZ() + ".csv"))));
            csvWriter.writeNext(new String[]{String.valueOf(alignedPeakCluster.getMeanMZ()), String.valueOf(alignedPeakCluster.getMeanRT()), "1", String.valueOf(i)});
            for (AlignedFragmentCluster fragmentCluster : alignedPeakCluster.getAlignedFragmentClusters()) {
                csvWriter.writeNext(new String[]{String.valueOf(fragmentCluster.getAlignedMZ()), String.valueOf(fragmentCluster.getAlignedRT()), "2", String.valueOf(i)});
            }
            i++;
        /*for(PeakCluster cluster : alignedPeakCluster.getClusters()){
            csvWriter.writeNext(new String[]{String.valueOf(cluster.getMainMZ()), String.valueOf(cluster.getMainRT()), "1", String.valueOf(i)});
            for(MS2Cluster fragmentCluster : cluster.getFragmentClusters()){
                csvWriter.writeNext(new String[]{String.valueOf(fragmentCluster.getMeanMZ()), String.valueOf(fragmentCluster.getMeanRT()), "2", String.valueOf(i)});
            }
            i++;
        }*/
            csvWriter.close();
        }
    }

    static void writeMS2PeakClustersToCSV(ArrayList<PeakCluster> peakClusters, String folder) throws IOException{
        for(PeakCluster peakCluster : peakClusters) {
            if(peakCluster.getMainChromatogramFragments().size()>0) {
                CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File(folder + peakCluster.getMainMZ() + ".csv"))));
                csvWriter.writeNext(new String[]{String.valueOf(peakCluster.getMainMZ()), String.valueOf(peakCluster.getMainRT()), String.valueOf(peakCluster.getMainIntensity()),"1"});
                ArrayList<MS2Fragment> fragmentsToWrite = peakCluster.getMainChromatogramFragments();
                for (MS2Fragment fragment : fragmentsToWrite) {
                    csvWriter.writeNext(new String[]{String.valueOf(fragment.getMZ()), String.valueOf(fragment.getRT()), String .valueOf(fragment.getIntensity()), "2"});
                }
                csvWriter.close();
            }
        }
    }

    /**
     * Writes the results from the clustering algorithm for testing
     * @param list The returned list from the clustering algorithm
     * @throws IOException If there is an error with the file handling
     */
    static void writeToCSV(List<Cluster<PeakCluster>> list) throws IOException{
        CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File("S:/mzXML Sample Data/list.csv"))));

        for(int i=0; i<list.size(); i++){
            Cluster<PeakCluster> cluster = list.get(i);
            for(PeakCluster peakCluster : cluster.getPoints()){
                csvWriter.writeNext(new String[]{String.valueOf(peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).getMeanMZ()), String.valueOf(peakCluster.getChromatograms().get(peakCluster.getStartingPointIndex()).getStartingPointRT()), String.valueOf(i)});
            }
        }
        csvWriter.close();
    }

    /**
     * For testing
     * @param clusterArrayList The clusters to write
     * @throws IOException If there is an error with the file handling
     */
    static void writeToCSV(ArrayList<PeakCluster> clusterArrayList) throws IOException {
        CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(new File("S:/mzXML Sample Data/test1.csv"))));

        for(PeakCluster cluster : clusterArrayList){
            csvWriter.writeNext(new String[]{String.valueOf(cluster.getMainMZ()), String.valueOf(cluster.getMainRT())});
        }
        csvWriter.close();
    }

}
