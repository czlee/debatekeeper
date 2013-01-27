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

    public enum UserDefinedBellType {
        FROM_START, FROM_END, PROPORTION_OF_TOTAL
    }

    public ArrayList<BellInfo> getBellsList() {
        // TODO write this method
        return null;

    }

}
