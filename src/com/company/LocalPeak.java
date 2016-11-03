package com.company;

import java.util.Objects;

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
    private double Intensity;
    private double MZ;
    private double RT;
    private boolean isUsed;

    /**
     * The only constructor for the class, requiring all the data needed. I won't be making another constructor
     * because this data structure becomes meaningless without all of the data.
     *
     * @param scan the scan number
     * @param inten the intensity
     * @param massCharge the m/z ratio value
     * @param retentionTime the retention time of the scan
     */
    public LocalPeak(int scan, double inten, double massCharge, double retentionTime){
        scanNumber = scan;
        Intensity = inten;
        MZ = massCharge;
        RT = retentionTime;
        isUsed = false;
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
        return Intensity;
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

    @Override
    public boolean equals(Object obj)
    {
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
