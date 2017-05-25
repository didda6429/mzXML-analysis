package lsi.sling;

import lsi.sling.FragmentHandling.MS2Fragment;

import java.util.ArrayList;

/**
 * Represents a single peak from the MZXML as a single data structure containing the relevant information.
 * This class is intended to be called from other classes as a wrapper, and not directly by
 * the User.
 *
 * @author Adithya Diddapur
 *
 */
public class LocalPeak implements Comparable<LocalPeak> {

    private int scanNumber;
    private double intensity;
    private double MZ;
    private double RT;
    private boolean isUsed;
    private ArrayList<MS2Fragment> fragments;

    /**
     * Constructor which doesn't take the mslevel as one of its inputs. In this case, the msLevel is defaulted to 1.
     *
     * @param scan the scan number
     * @param inten the intensity
     * @param massCharge the m/z ratio value
     * @param retentionTime the retention time of the scan
     */
    public LocalPeak(int scan, double inten, double massCharge, double retentionTime){
        scanNumber = scan;
        intensity = inten;
        MZ = massCharge;
        RT = retentionTime;
        isUsed = false;
        fragments = new ArrayList<>();
    }

    /**
     * Adds the input fragment to the existing list. This logic prevents people from removing fragments accidentaly.
     * @param fragments The fragment to append
     */
    public void addFragment(MS2Fragment fragments) {
        //this.fragments = fragments;
        this.fragments.add(fragments);
    }

    public ArrayList<MS2Fragment> getFragments() {
        return fragments;
    }

    /**
     * Returns the scan number
     * @return the scan number as an integer
     */
    public int getScanNumber(){
        return scanNumber;
    }

    /**
     * Returns the intensity
     * @return the intensity as a double
     */
    public double getIntensity(){
        return intensity;
    }

    /**
     * Returns the m/z value
     * @return the m/z value as a double
     */
    public double getMZ(){
        return MZ;
    }

    /**
     * Returns the retention time
     * @return the RT value as a double
     */
    public double getRT(){
        return RT;
    }

    /**
     * Returns the value of the isUsed flag
     * @return the value of the flag as a boolean
     */
    public boolean getIsUsed(){
        return isUsed;
    }

    /**
     * Sets the value of the isUsed flag to true. In normal use, this should only need to be called once in
     * the life-cycle of the object
     */
    public void setIsUsed(){
        isUsed = true;
    }

    /**
     * Implementation of the required method from the Comparable interface. In this case, the value returned is
     * dependent on the intensities of the 2 LocalPeak objects.
     * @param o a LocalPeak object to compare with
     * @return 1 if this object has higher intensity, -1 if this object has lower intensity, 0 if the intensities are equal
     */
    @Override
    public int compareTo(LocalPeak o) {
        int val = 0;
        if(this.getIntensity()>o.getIntensity()){
            val = -1;
        } else if (this.getIntensity()<o.getIntensity()){
            val = 1;
        }
        return val;
    }

    /**
     * Implementation of the required method from the Comparable interface. Compares two LocalPeak objects for equality
     * (two LocalPeaks are equal if all of their fields contain equal values)
     * @param obj A LocalPeak object to compare with
     * @return true if the two objects are 'equal', otherwise false
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (obj instanceof LocalPeak)
        {
            LocalPeak other = (LocalPeak) obj;
            return other.getIntensity()==(getIntensity()) &&
                    other.getMZ()==(getMZ()) &&
                    other.getIsUsed()==(getIsUsed()) &&
                    other.getRT()==(getRT()) &&
                    other.getScanNumber()==(getScanNumber());

        }
        else
        {
            return false;
        }
    }

}
