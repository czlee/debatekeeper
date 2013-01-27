package net.czlee.debatekeeper;

import java.util.ArrayList;

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
     * Returns a list of the bells that the current user-defined settings imply
     * for prep time of a given length
     * @param length total length of the prep time
     * @return an {@link ArrayList} of {@link BellInfo} objects sorted by time
     */
    public ArrayList<BellInfo> getBellsList(long length) {
        // TODO write this method
        return null;

    }



}
