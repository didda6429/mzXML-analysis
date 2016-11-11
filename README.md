# mzXML-project-java
Java project for untargeted mass spec raw peak analysis. In particular, this project aims to design and implement 
algorithms for:
- Peak Picking
- Peak integration

from raw mzXML files.

##Peak Picking
So far, only a protoype peak picking algorithm has been implemented. It works recursively to extract an entire ion chromatograms from all significant starting points (based on fixed threshold). To avoid excessive data, a flag is used to ensure each chromatogram is only selected once (and not multiple times). It also finds the turning points in the chromatograms to help seperate isobars by using a Savitzky-Golay filter to remove some of the noise.
