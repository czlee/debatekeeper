/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat

import android.os.Bundle

/**
 * PeriodInfo is a passive data class that holds information about a period *between* bells.
 *
 * An example of a period is "prep time", "points of information allowed", "warning bell rung".
 * It is valid to have a period with an empty string as its description.
 *
 * @param reference reference
 * @param name human-readable name
 * @param description description to be displayed when active
 * @param backgroundColor background colour (alpha is ignored and always replaced with 0xFF)
 * @param poisAllowed whether POIs are allowed
 *
 * @author Chuan-Zheng Lee
 * @since 2012-05-12
 */
class PeriodInfo @JvmOverloads constructor(
        /**
         * A reference string, not strictly part of the period type information but users
         * may find it useful to know what the reference was that was used to create this
         * `PeriodInfo`.
         */
        val reference: String? = null,
        /** A name that would appear in a list of lots of period types. */
        val name: String? = null,
        description: String? = null,
        backgroundColor: Int? = null,
        poisAllowed: Boolean = false) {

    // The meaning of "null" in both these members is "do not change from what it is currently".
    private var mDescription: String? = description
    private var mBackgroundColor: Int? = backgroundColor

    /** Whether POIs are allowed. There is no "null" option for this, it is always updated. */
    var isPoisAllowed: Boolean = poisAllowed
        private set

    /** The description that would appear on the screen while this period is being displayed. */
    val description: String?
        get() = mDescription

    /** The background colour, with the alpha value always set to `0xff`. */
    val backgroundColor: Int?
        get() = mBackgroundColor?.or(0xff000000.toInt())

    /**
     * Updates this `PeriodInfo` using the information in another PeriodInfo.
     * It replaces members if the new information is not null, and leaves them as they are if
     * the new information is null.
     * @param pi The new PeriodInfo object from which to take the updated information.
     */
    fun update(pi: PeriodInfo) {
        if (pi.mDescription != null) mDescription = pi.mDescription
        if (pi.mBackgroundColor != null) mBackgroundColor = pi.mBackgroundColor

        // There is no "do not change" option for POIs allowed
        isPoisAllowed = pi.isPoisAllowed
    }

    /**
     * Adds information to this `PeriodInfo`, but does not replace existing information.
     * It replaces members if the existing member is null, but always leaves non-null members as
     * they are.
     * @param pi The new PeriodInfo object from which to take the information.
     */
    fun addInfo(pi: PeriodInfo) {
        if (mDescription == null) mDescription = pi.mDescription
        if (mBackgroundColor == null) mBackgroundColor = pi.mBackgroundColor

        // There is no "do not change" option for POIs allowed
        isPoisAllowed = pi.isPoisAllowed
    }

    /**
     * Saves the state of this `PeriodInfo` to a [Bundle].
     * @param key A String to uniquely distinguish this `PeriodInfo` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    fun saveState(key: String, bundle: Bundle) {
        bundle.putString(key + BUNDLE_SUFFIX_DESC, mDescription)
        mBackgroundColor?.let { bundle.putInt(key + BUNDLE_SUFFIX_BGCOLOR, it) }
        bundle.putBoolean(key + BUNDLE_SUFFIX_POIS_ALLOWED, isPoisAllowed)
    }

    /**
     * Restores the state of this `PeriodInfo` from a [Bundle].
     * @param key A String to uniquely distinguish this `PeriodInfo` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    fun restoreState(key: String, bundle: Bundle) {
        val description = bundle.getString(key + BUNDLE_SUFFIX_DESC)
        if (description != null) mDescription = description
        mBackgroundColor = if (bundle.containsKey(key + BUNDLE_SUFFIX_BGCOLOR))
            bundle.getInt(key + BUNDLE_SUFFIX_BGCOLOR)
        else
            null
        isPoisAllowed = bundle.getBoolean(key + BUNDLE_SUFFIX_POIS_ALLOWED, false)
    }

    companion object {
        // Bundle suffixes
        private const val BUNDLE_SUFFIX_DESC = ".d"
        private const val BUNDLE_SUFFIX_BGCOLOR = ".b"
        private const val BUNDLE_SUFFIX_POIS_ALLOWED = ".p"
    }
}
