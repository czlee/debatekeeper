package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * SpeechFormat is a passive data class that holds information about a speech format.
 *
 * A speech format defines things like the speech length and bell times.
 * A speech format is generally specific to a debate format (though, programming-wise, need
 * not be).  For example, the Australs debate format has two speech formats: substantive and
 * reply.  The British Parliamentary format has only one speech format.  Most American formats
 * have almost one format for every speech!
 *
 * This class doesn't have much brains, but it does have the ability to intelligently pick a
 * useful bell, i.e. the first bell after a time that is given to it.
 *
 * The SpeechFormat class is processed by BellChain.
 *
 * You can't change the speech length after you've instantiated this object.
 *
 *  @author Chuan-Zheng Lee
 */
public class SpeechFormat {

    public enum CountDirection {
        COUNT_USER,
        COUNT_UP,
        COUNT_DOWN
    }

    protected final long          mSpeechLength;
    protected CountDirection      mCountDirection  = CountDirection.COUNT_USER;
    protected PeriodInfo          mFirstPeriodInfo = new PeriodInfo(null, null);

    // Note: There is no guarantee that this list is sorted.
    protected ArrayList<BellInfo> mBells           = new ArrayList<BellInfo>();

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public SpeechFormat(long speechLength) {
        super();
        this.mSpeechLength = speechLength;
    }

    public long getSpeechLength() {
        return mSpeechLength;
    }

    public CountDirection getCountDirection() {
        return mCountDirection;
    }

    public void setCountDirection(CountDirection countDirection) {
        this.mCountDirection = countDirection;
    }

    public PeriodInfo getFirstPeriodInfo() {
        PeriodInfo pi = new PeriodInfo("Initial", 0);
        pi.update(mFirstPeriodInfo);
        return pi;
    }

    public void setFirstPeriodInfo(PeriodInfo firstPeriodInfo) {
        this.mFirstPeriodInfo = firstPeriodInfo;
    }

    /**
     * Adds a BellInfo to the speech.
     * This method avoids throwing exceptions.  The caller must be diligent enough not to do
     * anything weird.  If there are two bells at the same time, it replaces the existing one with
     * this one.  If this bell is after the finish time, it doesn't care, it just adds it anyway.
     * @param bi the BellInfo to add
     */
    public void addBellInfo(BellInfo bi) {

        // If there is already a bell with this time, remove it
        Iterator<BellInfo> biIterator = mBells.iterator();
        BellInfo checkBi;
        while (biIterator.hasNext()) {
            checkBi = biIterator.next();
            if (checkBi.getBellTime() == bi.getBellTime()) {
                biIterator.remove();
            }
        }

        this.mBells.add(bi);
    }

    /**
     * Returns the first bell after a given time
     * @param seconds the time in seconds
     * @return the {@link BellInfo} object representing that bell
     */
    public BellInfo getFirstBellFromTime(long seconds) {
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo workingBell = null;
        BellInfo thisBell    = null;

        // We are looking for the *earliest* bell that is *after* the given time.
        while (bellIterator.hasNext()) {
            // To replace the current working bell, two conditions have to be met:
            //  1. This bell is, in fact, after or at the given time.
            //  2. This bell is before the current working bell, if there is one.
            //     (If there isn't one, then condition 2 is deemed met.)
            // We will test the opposite condition, and assign if it survives both.
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() < seconds)
                continue;
            if (workingBell != null) {
                if (workingBell.getBellTime() < thisBell.getBellTime()) {
                    continue;
                }
            }
            // If it survived both negative conditions, assign.
            workingBell = thisBell;
        }

        return workingBell;
    }

    /**
     * Returns the bell for the specified time, or null if there is no such bell.
     * @param seconds the time in seconds
     * @return the {@link BellInfo} object representing that bell
     */
    public BellInfo getBellAtTime(long seconds) {
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo thisBell    = null;

        while (bellIterator.hasNext()) {
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() == seconds)
                return thisBell;
        }

        return null;
    }

    /**
     * Returns the {@link PeriodInfo} appropriate for the given time
     * @param seconds the time in seconds
     * @return the PeriodInfo object
     */
    public PeriodInfo getPeriodInfoForTime(long seconds) {
        PeriodInfo workingPi = new PeriodInfo();
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo thisBell = null;
        long latestBellTimeSoFar = 0;

        workingPi.update(getFirstPeriodInfo());

        // We are looking for the *latest* bell that is *before* (or at) the given time,
        // that actually has a descriptor.
        while (bellIterator.hasNext()) {
            // To be useful, this bell must *not* be after the given time.
            // Then, if this bell is the latest bell considered so far, it should replace the
            // existing information.  If it is not the latest bell considered, it should be added
            // where the existing information is null, but existing information should not be
            // changed.
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() > seconds)
                continue;
            if (thisBell.getBellTime() > latestBellTimeSoFar) {
                workingPi.update(thisBell.getNextPeriodInfo()); // update and replace info
                latestBellTimeSoFar = thisBell.getBellTime();   // take note of the new latest bell
            } else {
                workingPi.addInfo(thisBell.getNextPeriodInfo()); // add, but don't replace
            }
        }

        return workingPi;
    }

}
