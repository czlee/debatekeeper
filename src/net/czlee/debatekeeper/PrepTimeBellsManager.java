package net.czlee.debatekeeper;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * PrepTimeBellsManager manages the user-defined bell times for preparation time.
 * It is used by {@link PrepTimeSimpleFormat}.
 *
 * It is responsible for storing information about the user-defined settings for
 * preparation bell times, and for returning a list of bell times based on those
 * settings given a prep time length.  For example, if there is a single bell
 * always five minutes from the end (and two bells at the end), then this would
 * return:
 *   - If prep time is 5 minutes,  bells at: finish (only)
 *   - If prep time is 10 minutes, bells at: 5, finish
 *   - If prep time is 20 minutes, bells at: 15, finish
 *
 * There are three types of user-defined bells:
 *   (1) Time from the start
 *   (2) Time from the end
 *   (3) Time as a proportion of total time (e.g. halfway)
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-27
 */
public class PrepTimeBellsManager {

    private final ArrayList<PrepTimeBellSpec> mBellSpecs = new ArrayList<PrepTimeBellSpec>();

    private interface PrepTimeBellSpec {
        /**
         * @param length
         * @return A {@link BellInfo} object, or <code>null</code> if this bell is
         * incompatible with the length given (e.g. it would occur after the end
         * of the prep time given).
         */
        public BellInfo getBell(long length);
    }

    private class PrepTimeBellFromStart implements PrepTimeBellSpec {

        private final long time;

        public PrepTimeBellFromStart(long time) {
            this.time = time;
        }

        @Override
        public BellInfo getBell(long length) {
            // If the time is within the given length, return a bell at that time.
            // Otherwise, return null.
            if (length > time)
                return new BellInfo(time, 1);
            else
                return null;
        }

    }

    private class PrepTimeBellFromFinish implements PrepTimeBellSpec {

        private final long time;

        public PrepTimeBellFromFinish(long time) {
            this.time = time;
        }

        @Override
        public BellInfo getBell(long length) {
            if (length > time)
                return new BellInfo(length - time, 1);
            else
                return null;
        }

    }

    private class PrepTimeBellProportional implements PrepTimeBellSpec {

        private final float proportion;

        public PrepTimeBellProportional (float proportion) {
            this.proportion = proportion;
        }

        @Override
        public BellInfo getBell(long length) {

            // Calculate when the bell should ring
            long time = (long) (length * proportion);
            return new BellInfo(time, 1);
        }

    }

    /**
     * Adds a bell specification, specified relative to the start of prep time.
     * @param time the time from the start of prep time
     */
    public void addBellFromStart(long time) {
        PrepTimeBellSpec bell = new PrepTimeBellFromStart(time);
        mBellSpecs.add(bell);
    }

    public void addBellFromFinish(long time) {
        PrepTimeBellSpec bell = new PrepTimeBellFromFinish(time);
        mBellSpecs.add(bell);
    }

    public void addBellProportional(float proportion) {
        PrepTimeBellSpec bell = new PrepTimeBellProportional(proportion);
        mBellSpecs.add(bell);
    }

    /**
     * Returns a list of the bells that the current user-defined settings imply
     * for prep time of a given length
     * @param length total length of the prep time
     * @return an {@link ArrayList} of {@link BellInfo} objects sorted by time
     */
    public ArrayList<BellInfo> getBellsList(long length) {

        Iterator<PrepTimeBellSpec> specIterator = mBellSpecs.iterator();
        ArrayList<BellInfo> bells = new ArrayList<BellInfo>();

        // First, generate all the bells.
        // But don't add bells that are at the same time as some other bell in the list.
        while (specIterator.hasNext()) {
            PrepTimeBellSpec spec = specIterator.next();
            BellInfo bell = spec.getBell(length);
            if (bell != null) {

                boolean duplicate = false;

                // Treat as a "duplicate" if the bell is within fifteen seconds of another bell.
                // (The bell added first always gets priority.)
                Iterator<BellInfo> bellIterator = bells.iterator();
                while (bellIterator.hasNext()) {
                    BellInfo checkBell = bellIterator.next();
                    if (Math.abs(checkBell.getBellTime() - bell.getBellTime()) < 15) {
                        duplicate = true;
                        break;
                    }
                }

                // If duplicate, skip the rest
                if (duplicate) continue;

                // Otherwise, add the bell.
                bells.add(bell);
            }
        }

        // TODO write this method
        return bells;

    }



}
