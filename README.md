# mzXML-project-java
Java project for untargeted mass spec raw peak analysis. In particular, this project aims to design and implement 
algorithms for:
- Peak Picking
- Peak integration

from raw mzXML files.

##Peak Picking
So far, only a protoype peak picking algorithm has been implemented. It works recursively to extract an entire ion chromatogram given a starting point.<br/>
TODO:
- implement rule to seperate isobars
- implement algorithm to select unique starting points
