package net.czlee.debatekeeper.debateformat;

import java.util.ArrayList;

/**
 * Interface for passive data classes holding information about a debate format that would
 * be human-readable on a quick "information" screen about the debate format.
 * @author Chuan-Zheng Lee
 * @since  2013-06-04
 */
public interface DebateFormatInfo {

    public abstract String getName();

    public abstract String getDescription();

    public abstract ArrayList<String> getRegions();

    public abstract ArrayList<String> getLevels();

    public abstract ArrayList<String> getUsedAts();

    public abstract String getPrepTimeDescription();

    /**
     * Returns a list of all the speech formats in this debate format, with descriptions.
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements. The first element is
     *         the speech type reference. The second element is a short
     *         description of the speech type. The <code>ArrayList</code> is
     *         sorted in the order the speech types appear in the debate. If a
     *         speech type isn't used, it isn't part of the returned
     *         <code>ArrayList</code>.
     */
    public abstract ArrayList<String[]> getSpeechFormatDescriptions();

    /**
     * Returns a list of speeches in this debate format.
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements.  The first element
     *         is the name of the speech, the second element is the reference
     *         for the format that speech uses.
     */
    public abstract ArrayList<String[]> getSpeeches();

}