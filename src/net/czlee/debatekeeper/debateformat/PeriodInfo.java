/*
 * Copyright (C) 2012 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;

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

    private String mReference         = null;
    private String mName              = null;

    // The meaning of "null" in both these objects is "do not change from what it is currently".
    private String  mDescription      = null;
    private Integer mBackgroundColor  = null; // Use Integer so that we can also use null.
    private boolean mPoisAllowed      = false; // There is no "null" option for this, it is always updated.

    // Bundle suffixes
    private final String BUNDLE_SUFFIX_DESC = ".d";
    private final String BUNDLE_SUFFIX_BGCOLOR = ".b";
    private final String BUNDLE_SUFFIX_POIS_ALLOWED = ".p";

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public PeriodInfo() {
        super();
    }

    public PeriodInfo(String reference, String name, String description, Integer backgroundColor, boolean poisAllowed) {
        super();
        mReference       = reference;
        mName            = name;
        mDescription     = description;
        mBackgroundColor = backgroundColor;
        mPoisAllowed     = poisAllowed;
    }

    public PeriodInfo(String description, Integer backgroundColor, boolean poisAllowed) {
        super();
        mDescription     = description;
        mBackgroundColor = backgroundColor;
        mPoisAllowed     = poisAllowed;
    }

    /**
     * @return a reference string, not strictly part of the period type information but users
     * may find it useful to know what the reference was that was used to create this
     * <code>PeriodInfo</code>.  This may return <code>null</code> if the <code>PeriodInfo</code> was
     * created from a version 1 schema.
     */
    public String getReference() {
        return mReference;
    }

    /**
     * @return a name that would appear in a list of lots of period types.  This may return
     * <code>null</code> if the {@link PeriodInfo} was created from a version 1 schema.
     */
    public String getName() {
        return mName;
    }

    /**
     * @return the description that would appear on the screen while this period is being displayed.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * @return the background colour, with the alpha value always set to <code>0xff</code>.
     */
    public Integer getBackgroundColor() {
        // Always set the alpha value to 0xFF.
        if (mBackgroundColor != null)
            return mBackgroundColor | 0xff000000;
        else
            return null;
    }

    public boolean isPoisAllowed() {
        return mPoisAllowed;
    }

    /**
     * Updates this <code>PeriodInfo</code> using the information in another PeriodInfo.
     * It replaces members if the new information is not null, and leaves them as they are if
     * the new information is null.
     * @param pi The new PeriodInfo object from which to take the updated information.
     */
    public void update(PeriodInfo pi) {
        if (pi.mDescription != null)     mDescription     = pi.mDescription;
        if (pi.mBackgroundColor != null) mBackgroundColor = pi.mBackgroundColor;

        // There is no "do not change" option for POIs allowed
        mPoisAllowed = pi.mPoisAllowed;
    }

    /**
     * Adds information to this <code>PeriodInfo</code>, but does not replace existing information.
     * It replaces members if the existing member is null, but always leaves non-null members as
     * they are.
     * @param pi The new PeriodInfo object from which to take the information.
     */
    public void addInfo(PeriodInfo pi) {
        if (this.mDescription == null)     mDescription     = pi.mDescription;
        if (this.mBackgroundColor == null) mBackgroundColor = pi.mBackgroundColor;

        // There is no "do not change" option for POIs allowed
        mPoisAllowed = pi.mPoisAllowed;
    }

    /**
     * Saves the state of this <code>PeriodInfo</code> to a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>PeriodInfo</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    public void saveState(String key, Bundle bundle) {
        bundle.putString(key + BUNDLE_SUFFIX_DESC, mDescription);
        if (mBackgroundColor != null)
            bundle.putInt(key + BUNDLE_SUFFIX_BGCOLOR, mBackgroundColor);
        bundle.putBoolean(key + BUNDLE_SUFFIX_POIS_ALLOWED, mPoisAllowed);
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
        if (bundle.containsKey(key + BUNDLE_SUFFIX_BGCOLOR))
            mBackgroundColor = bundle.getInt(key + BUNDLE_SUFFIX_BGCOLOR);
        else
            mBackgroundColor = null;
        mPoisAllowed = bundle.getBoolean(key + BUNDLE_SUFFIX_POIS_ALLOWED, false);
    }

}