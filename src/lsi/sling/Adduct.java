package lsi.sling;

/**
 * This class represents an adduct which could be a peak.
 * @author Adithya Diddapur
 */
public class Adduct implements Comparable<Adduct>{

    private String ionName;
    private String ionMassFunction;
    private double ionMass;
    private int ionCharge;
    private double compoundExactMass;
    private double resultMZ;
    private String compoundFormula;
    private String compoundCommonName;
    private String compoundSystemicName;

    /**
     * This constructor creates an object given all of the relevant information. The idea is that all computation is
     * performed elsewhere and then this object is used to store the calculated data.
     * @param iName The name of the ion
     * @param iMassFunction The formula to calculate the mass of the entire adduct for this specific ion
     * @param iMass The mass of the ion
     * @param iCharge The charge of the ion
     * @param cExactmass The exact mass of the compound (without the ion component)
     * @param rMZ The combined mass of the compound and ion (the entire adduct)
     * @param cFormula The formula of the compound (without the ion component)
     * @param cCommonName The common name of the compound (without the ion component)
     * @param cSystemicName The systemic name of the compound (without the ion component)
     */
    public Adduct(String iName, String iMassFunction, double iMass, int iCharge, double cExactmass, double rMZ, String cFormula, String cCommonName, String cSystemicName){
        ionName = iName;
        ionMassFunction = iMassFunction;
        ionMass = iMass;
        ionCharge = iCharge;
        compoundExactMass = cExactmass;
        resultMZ = rMZ;
        compoundFormula = cFormula;
        compoundCommonName = cCommonName;
        compoundSystemicName = cSystemicName;
    }

    public String getIonName() {
        return ionName;
    }

    public String getIonMassFunction() {
        return ionMassFunction;
    }

    public double getIonMass() {
        return ionMass;
    }

    public int getIonCharge() {
        return ionCharge;
    }

    public double getCompoundExactMass() {
        return compoundExactMass;
    }

    public double getResultMZ() {
        return resultMZ;
    }

    public String getCompoundFormula() {
        return compoundFormula;
    }

    public String getCompoundCommonName() {
        return compoundCommonName;
    }

    public String getCompoundSystemicName() {
        return compoundSystemicName;
    }

    @Override
    public int compareTo(Adduct o) {
        int val = 0;
        if(this.getResultMZ()>o.getResultMZ()){
            val = -1;
        } else if (this.getResultMZ()<o.getResultMZ()){
            val = 1;
        }
        return val;
    }
}
