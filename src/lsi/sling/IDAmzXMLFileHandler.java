package lsi.sling;

import lsi.sling.databasehandling.AdductDatabase;
import lsi.sling.mzxmlfilehandling.MzXMLFile;
import lsi.sling.peakextraction.AlignedPeakCluster;
import lsi.sling.peakextraction.LCPeakCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class contains all the logic to analyse untargeted DIA files
 */
public class IDAmzXMLFileHandler {

    public int instrumentPPM;

    public String databaseDir;
    //private static String mzXMLFileDir;
    //Used to create the database
    public String adductFile;
    public String compoundFile;

    public ArrayList<MzXMLFile> files;
    public ArrayList<AlignedPeakCluster> alignedPeakClusters;

    public IDAmzXMLFileHandler(String databaseDir, String adductFile, String compoundFile, File[] mzXMLFiles, int ppm) throws IOException {
        //checks that the files are all mzXML Files
        assert Arrays.stream(mzXMLFiles).filter(p -> p.getName().endsWith(".mzXMl")).toArray().length==mzXMLFiles.length : "File's aren't all mzXML Files";
        //initialise the String variables
        this.databaseDir = databaseDir;
        this.adductFile = adductFile;
        this.compoundFile = compoundFile;
        instrumentPPM = ppm;
        //create the database if it doesn't already exist
        try {
            AdductDatabase.createDatabase(this.databaseDir, this.adductFile, this.compoundFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //read in the individual mzXMLFiles
        files = readMzXMLFiles(mzXMLFiles);
        //clusters the peaks
        alignedPeakClusters = alignPeaks();
        //map the alignedPeakClusters to their adducts
        mapAlignedPeakClusterToAdducts();
        System.out.println("test");
    }

    /**
     * Reads in the mzXMLFiles concurrently to create the list of mzXMLFile objects
     * @param mzXMLFiles an array containing the mzXMLFiles to read.
     * @return an ArrayList of MzXMLFile objects
     */
    public ArrayList<MzXMLFile> readMzXMLFiles(File[] mzXMLFiles) {
        ArrayList<MzXMLFile> files = new ArrayList<>();
        assert mzXMLFiles != null : "no mzXML Files selected";
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
        try {
            executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return files;
    }

    public ArrayList<AlignedPeakCluster> alignPeaks(){
        ArrayList<LCPeakCluster> allLCPeakClusters = new ArrayList<>();
        //Stores ALL peak clusters across all samples in a single list for downstream clustering and alignment
        for(MzXMLFile file : files){
            allLCPeakClusters.addAll(file.getLCPeakClusters());
        }

        //Finds the max and min RT and m/z values to use when rescaling the locations of the clusters (to use the euclidean distance)
        //If there is an error in the stream (the min or max can't be found), return -1
        double mzMin = allLCPeakClusters.stream().mapToDouble(LCPeakCluster::getMainMZ).min().orElse(-1);
        double mzMax = allLCPeakClusters.stream().mapToDouble(LCPeakCluster::getMainMZ).max().orElse(-1);
        double rtMin = allLCPeakClusters.stream().mapToDouble(LCPeakCluster::getMainRT).min().orElse(-1);
        double rtMax = allLCPeakClusters.stream().mapToDouble(LCPeakCluster::getMainRT).max().orElse(-1);

        assert mzMax != -1: "No mzMax";
        assert mzMin != -1: "No mzMin";
        assert rtMin != -1: "No rtMin";
        assert rtMax != -1: "No rtMax";

        //Sets the rescaled values (to use the euclidean distance when clustering)
        for(LCPeakCluster cluster : allLCPeakClusters){
            cluster.setRescaledValues(mzMax, mzMin, rtMax, rtMin);
        }

        //Peforms the clustering and stores the results in a list
        DBSCANClusterer<LCPeakCluster> clusterer = new DBSCANClusterer<>(0.005, files.size()-2); //epsilon=0.005 works quite well
        List<Cluster<LCPeakCluster>> clusterResults = clusterer.cluster(allLCPeakClusters);

        //'converts' the Cluster objects returned from the DBSCANClusterer to AlignedPeakCluster objects and stores them in alignedPeakClusters
        ArrayList<AlignedPeakCluster> alignedPeakClusters = new ArrayList<>();

        for(Cluster<LCPeakCluster> cluster : clusterResults){
            alignedPeakClusters.add(new AlignedPeakCluster(cluster.getPoints(), 20));
        }
        //Cluster the fragments in each individual peakCluster
        for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
            for(LCPeakCluster cluster : alignedPeakCluster.getClusters()){
                cluster.clusterFragments();
            }
            //TODO: cluster the fragments within each peakCluster
            //for now just collates them
            alignedPeakCluster.clusterFragments();

        }
        return alignedPeakClusters;
    }

    public void mapAlignedPeakClusterToAdducts(){
        for(AlignedPeakCluster alignedPeakCluster : alignedPeakClusters){
            try {
                AdductDatabase.mapClusters(alignedPeakCluster, databaseDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public ArrayList<AlignedPeakCluster> getAlignedPeakClusters(){
        return alignedPeakClusters;
    }

}
