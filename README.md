# mzXML-project-java
Java project for untargeted mass spec raw peak analysis. In particular, this project aims to design and implement 
algorithms for:
- Peak Picking
- Peak Identification
- Sample Alignment
- Peak Integration

from raw mzXML files.

##Peak Picking
So far, a recursive peak picking algorithm has been designed and implemented. This algorithm recursively creates 
chromatograms and then collates them into peak clusters. Valid peak clusters are then determined by checking the validity
 of the chromatogram which was used to form it (the starting point). Invalid chromatograms are discarded. 

The software also has functionality to identify isobars within chromatograms. It does this by using a Savitzky-Golay 
filter to smooth the data before finding the minima. NOTE: This functionality is still experimental and it's reliability
is NOT guaranteed.

##Peak Identification
The aim for this software is to be able to identify which compounds could correspond to which peaks. The first step in 
achieving this is to identify possible MS1 peaks from a pre-existing list of compounds and ions. This basic functionality
has been implemented.

Note that the software has been tested against multiple lipidomics mzXML files and appears to work as intended, HOWEVER,
at this early stage is is difficult to validate it's accuracy (whilst changes are still being made regularly).

##Sample Alignment
So far, a basic Sample Alignment algorithm which uses the euclidean distance to estimate the true M/Z and RT values has
been implemented. This algorithm uses the median position to estimate the true values.