package com.ftechz.DebatingTimer;

import android.os.Bundle;

/**
 * PeriodInfo is a passive data class that holds information about a period *between* bells.
 *
 * An example of a period is "Points of information allowed", or "Warning bell rung". It is
 * valid to have a period with an empty string as its description.
 *
 * This information is intended to be drawn to the GUI immediately after a bell.
 *
 * This class intentionally does not have setters.  Members should never be changed individually.
 * In most cases, its members should be set and final upon construction.  "Working copies" should
 * use the update() and restoreState() methods.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-05-12
 */
public class PeriodInfo {

    // The meaning of "null" in both these objects is "do not change from what it is currently".
    protected String  mDescription      = null;
    protected Integer mBackgroundColor  = null; // Use Integer so that we can also use null.

    // Bundle suffixes
    private final String BUNDLE_SUFFIX_DESC = ".d";
    private final String BUNDLE_SUFFIX_BGCOLOR = ".b";

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public PeriodInfo() {
        super();
    }

    public PeriodInfo(String description, Integer backgroundColor) {
        super();
        mDescription     = description;
        mBackgroundColor = backgroundColor;
    }

    public String  getDescription() {
        return mDescription;
    }

    public Integer getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * Updates this <code>PeriodInfo</code> using the information in another PeriodInfo.
     * It replaces members if they are not null, and leaves them as they are if they are null.
     * @param pi The new PeriodInfo object from which to take the updated information.
     */
    public void update(PeriodInfo pi) {
        if (pi.mDescription != null)     mDescription     = pi.mDescription;
        if (pi.mBackgroundColor != null) mBackgroundColor = pi.mBackgroundColor;
    }

    /**
     * Adds information to this <code>PeriodInfo</code>, but does not replace existing information.
     * It replaces members if the existing member is null, but always leaves non-null members as
     * they are.
     * @param pi The new PeriodInfo object from which to take the information.
     */
    public void addInfo(PeriodInfo pi) {
        if (this.mDescription != null)     mDescription     = pi.mDescription;
        if (this.mBackgroundColor != null) mBackgroundColor = pi.mBackgroundColor;
    }

    /**
     * Saves the state of this <code>PeriodInfo</code> to a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>PeriodInfo</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    public void saveState(String key, Bundle bundle) {
        bundle.putString(key + BUNDLE_SUFFIX_DESC, mDescription);
        bundle.putInt(key + BUNDLE_SUFFIX_BGCOLOR, mBackgroundColor);
    }

    /**
     * Restores the state of this <code>PeriodInfo</code> from a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>PeriodInfo</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    public void restoreState(String key, Bundle bundle) {
        String description = bundle.getString(key + BUNDLE_SUFFIX_DESC);
        if (description != null) mDescription = description;
        mBackgroundColor = bundle.getInt(key + BUNDLE_SUFFIX_BGCOLOR);
    }
}