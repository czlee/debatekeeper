package net.czlee.debatekeeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * GenericSpeechOrPrepFormat is a generic abstract implementation of
 * {@link SpeechOrPrepFormat}.
 *
 * Subclasses must provide <code>getLength()</code>, <code>getBells()</code> and
 * <code>getFirstPeriodInfo()</code>.
 *
 * GenericSpeechOrPrepFormat will provide other methods using these three
 * methods.
 *
 * @author Chuan-Zheng Lee
 *
 */
public abstract class GenericSpeechOrPrepFormat implements SpeechOrPrepFormat {

    /*
     * (non-Javadoc)
     *
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getLength()
     */
    @Override
    public abstract long getLength();

    /**
     * @return an {@link ArrayList} of {@link BellInfo} objects for this format
     */
    protected abstract ArrayList<BellInfo> getBells();

    /*
     * (non-Javadoc)
     *
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getFirstPeriodInfo()
     */
    @Override
    public abstract PeriodInfo getFirstPeriodInfo();

    /*
     * (non-Javadoc)
     *
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getBellAtTime(long)
     */
    @Override
    public BellInfo getBellAtTime(long seconds) {
        Iterator<BellInfo> bellIterator = getBells().iterator();
        BellInfo thisBell = null;

        while (bellIterator.hasNext()) {
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() == seconds)
                return thisBell;
        }

        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getPeriodInfoForTime(long)
     */
    @Override
    public PeriodInfo getPeriodInfoForTime(long seconds) {
        PeriodInfo workingPi = new PeriodInfo();
        Iterator<BellInfo> bellIterator = getBells().iterator();
        BellInfo thisBell = null;
        long latestBellTimeSoFar = 0;

        workingPi.update(getFirstPeriodInfo());

        // We are looking for the *latest* bell that is *before* (or at) the
        // given time,
        // that actually has a descriptor.
        while (bellIterator.hasNext()) {
            // To be useful, this bell must *not* be after the given time.
            // Then, if this bell is the latest bell considered so far, it
            // should replace the
            // existing information. If it is not the latest bell considered, it
            // should be added
            // where the existing information is null, but existing information
            // should not be
            // changed.
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() > seconds)
                continue;
            if (thisBell.getBellTime() > latestBellTimeSoFar) {
                workingPi.update(thisBell.getNextPeriodInfo()); // update and
                                                                // replace info
                latestBellTimeSoFar = thisBell.getBellTime(); // take note of
                                                              // the new latest
                                                              // bell
            } else {
                workingPi.addInfo(thisBell.getNextPeriodInfo()); // add, but
                                                                 // don't
                                                                 // replace
            }
        }

        return workingPi;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.czlee.debatekeeper.SpeechOrPrepFormat#getBellsIter()
     */
    @Override
    public ArrayList<BellInfo> getBellsSorted() {

        // A shallow copy is fine, we just want to sort the bells, not edit
        // them.
        ArrayList<BellInfo> bells = new ArrayList<BellInfo>(getBells());

        Collections.sort(bells, new Comparator<BellInfo>() {
            @Override
            public int compare(BellInfo lhs, BellInfo rhs) {
                Long diff = lhs.getBellTime() - rhs.getBellTime();
                return diff.intValue();
            }
        });

        return bells;
    }

}
