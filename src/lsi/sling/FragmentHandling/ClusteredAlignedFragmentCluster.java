package lsi.sling.FragmentHandling;

import org.apache.commons.math3.ml.clustering.Cluster;

import java.util.ArrayList;

/**
 * This class represents the clustered AlignedClusters, which are basically the final step in the cluster processing
 * sequence. Essentially, this class represents the actual characteristic fragment for each AlignedPeakCluster.
 */
public class ClusteredAlignedFragmentCluster {

    private ArrayList<AlignedFragmentCluster> alignedFragmentClusters;
    private double meanMZ;
    private double meanRT;

    /**
     * Creates a ClusteredAlignedFragmentCluster given an input Cluster (which is the output from the DBSCAN clustering
     * algorithm)
     * @param cluster the input cluster of AlignedFragmentClusters from the DBSCAN clustering algorithm.
     */
    public ClusteredAlignedFragmentCluster(Cluster<AlignedFragmentCluster> cluster){
        alignedFragmentClusters = new ArrayList<>(cluster.getPoints().size());
        alignedFragmentClusters.addAll(cluster.getPoints());
        meanMZ = alignedFragmentClusters.stream().mapToDouble(AlignedFragmentCluster::getAlignedMZ).average().getAsDouble();
        meanRT = alignedFragmentClusters.stream().mapToDouble(AlignedFragmentCluster::getAlignedRT).average().getAsDouble();
    }

    public ArrayList<AlignedFragmentCluster> getAlignedFragmentClusters() {
        return alignedFragmentClusters;
    }

    public double getMeanMZ() {
        return meanMZ;
    }

    public double getMeanRT() {
        return meanRT;
    }
}
