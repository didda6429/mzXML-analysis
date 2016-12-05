# mzXML-project-java
Java project for untargeted mass spec raw peak analysis. In particular, this project aims to design and implement 
algorithms for:
- Peak Picking
- Peak integration

from raw mzXML files.

##Peak Picking
So far, a recursive peak picking algorithm has been designed and implemented. This algorithm recursively creates chromatograms and then collates them into peak clusters. Valid peak clusters are then determined by checking the validity of the chromatogram which was used to form it (the starting point). Invalid chromatograms are discarded. 

The software also has functionality to identify isobars within chromatograms. It does this by using a Savitzky-Golay filter to smooth the data before finding the minima. NOTE: This functionality is still experimental and it's reliability is NOT guaranteed.
