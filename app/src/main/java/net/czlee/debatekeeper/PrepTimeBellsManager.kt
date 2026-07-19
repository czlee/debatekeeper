/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

package net.czlee.debatekeeper

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import net.czlee.debatekeeper.debateformat.BellInfo
import net.czlee.debatekeeper.debateformat.PrepTimeSimpleFormat
import java.util.Locale

/**
 * PrepTimeBellsManager manages the user-defined bell times for preparation time.
 * It is used by [PrepTimeSimpleFormat].
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
 *   1. Time from the start
 *   2. Time from the end
 *   3. Time as a proportion of total time (e.g. halfway)
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-27
 */
class PrepTimeBellsManager(private val mContext: Context) {

    private val mBellSpecs = ArrayList<PrepTimeBellSpec>()

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private interface PrepTimeBellSpec {

        /**
         * @param length the length of the prep time
         * @return A [BellInfo] object, or `null` if this bell is incompatible with the length
         * given (e.g. it would occur after the end of the prep time given).
         */
        fun getBell(length: Long): BellInfo?

        /**
         * Saves this bell specification to a bundle.
         * @param bundle the Bundle to save to
         */
        fun saveToBundle(bundle: Bundle)

        /**
         * Saves this bell specification to a shared preferences file.
         * @param index the index of this PrepTimeBellSpec in the list
         */
        fun saveToPreferences(editor: SharedPreferences.Editor, index: Int)

        /**
         * `true` if the bell is *always* at the finish; `false` otherwise. Note that a bell
         * that *can* be, but is not *always* at the finish (*e.g.* a start bell whose time
         * coincides with the prep time length) returns `false`.
         */
        val isAtFinish: Boolean
    }

    /**
     * Thrown from [PrepTimeBellSpec] constructors if there is a problem with the
     * contents of the [SharedPreferences] file
     */
    private open class PrepTimeBellConstructorException : Exception {
        constructor(index: Int, detailMessage: String) : super("$index: $detailMessage")
        constructor(detailMessage: String) : super(detailMessage)
    }

    /**
     * Thrown from [PrepTimeBellSpec] constructors if the type stored in the
     * [SharedPreferences] file does not match the class of the constructor.
     */
    private class PrepTimeBellWrongTypeException : PrepTimeBellConstructorException {
        constructor(index: Int, expected: String, actual: String) :
                super(index, "Expected $expected, found $actual")
        constructor(expected: String, actual: String) :
                super("Expected $expected, found $actual")
    }

    private abstract inner class PrepTimeBellByTime : PrepTimeBellSpec {

        protected val time: Long

        /**
         * Constructor from a [Bundle].
         * @param bundle the Bundle.
         * @throws PrepTimeBellWrongTypeException if the type in the given [Bundle] does not
         * match the expected type.
         * @throws PrepTimeBellConstructorException if there is some other problem with the bundle
         */
        @Throws(PrepTimeBellConstructorException::class)
        constructor(bundle: Bundle) {
            val type = bundle.getString(KEY_TYPE)
                    ?: throw PrepTimeBellConstructorException("No type found")
            if (type != valueType)
                throw PrepTimeBellWrongTypeException(valueType, type)
            if (!bundle.containsKey(KEY_TIME))
                throw PrepTimeBellConstructorException("No time found")
            this.time = bundle.getLong(KEY_TIME, 0)
        }

        constructor(time: Long) {
            this.time = time
        }

        /**
         * Constructor from a [SharedPreferences] file.
         * @param prefs the [SharedPreferences] object
         * @param index the index of the desired bell specification in `prefs`.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given [SharedPreferences] file does not match the expected type.
         * @throws PrepTimeBellConstructorException if there is some other problem with the
         * preferences
         */
        @Throws(PrepTimeBellConstructorException::class)
        constructor(prefs: SharedPreferences, index: Int) {
            val type = prefs.getString(index.toString() + KEY_TYPE, "")
            if (type != valueType)
                throw PrepTimeBellWrongTypeException(index, valueType, type!!)
            if (!prefs.contains(index.toString() + KEY_TIME))
                throw PrepTimeBellConstructorException(index, "No time found")
            this.time = prefs.getLong(index.toString() + KEY_TIME, 0)
        }

        override fun saveToBundle(bundle: Bundle) {
            bundle.putString(KEY_TYPE, valueType)
            bundle.putLong(KEY_TIME, time)
        }

        override fun saveToPreferences(editor: SharedPreferences.Editor, index: Int) {
            editor.putString(index.toString() + KEY_TYPE, valueType)
            editor.putLong(index.toString() + KEY_TIME, time)
        }

        protected abstract val valueType: String
    }

    private inner class PrepTimeBellFromFinish : PrepTimeBellByTime {

        @Throws(PrepTimeBellConstructorException::class)
        constructor(bundle: Bundle) : super(bundle)

        constructor(time: Long) : super(time)

        @Throws(PrepTimeBellConstructorException::class)
        constructor(prefs: SharedPreferences, index: Int) : super(prefs, index)

        override fun getBell(length: Long): BellInfo? {
            return if (length > time)
                BellInfo(length - time, 1)
            else
                null
        }

        override fun toString(): String {
            if (time == 0L) return mContext.getString(R.string.prepTimeBellDescription_atFinish)
            return mContext.getString(R.string.prepTimeBellDescription_beforeFinish,
                    DebatekeeperUtils.secsToTextSigned(time))
        }

        override val valueType: String
            get() = VALUE_TYPE_FINISH

        override val isAtFinish: Boolean
            get() = time == 0L
    }

    private inner class PrepTimeBellFromStart : PrepTimeBellByTime {

        @Throws(PrepTimeBellConstructorException::class)
        constructor(bundle: Bundle) : super(bundle)

        @Throws(PrepTimeBellConstructorException::class)
        constructor(prefs: SharedPreferences, index: Int) : super(prefs, index)

        override fun getBell(length: Long): BellInfo? {
            // If the time is within the given length, return a bell at that time.
            // Otherwise, return null.
            return if (length > time)
                BellInfo(time, 1)
            else
                null
        }

        override fun toString(): String {
            if (time == 0L) return mContext.getString(R.string.prepTimeBellDescription_atStart)
            return mContext.getString(R.string.prepTimeBellDescription_afterStart,
                    DebatekeeperUtils.secsToTextSigned(time))
        }

        override val valueType: String
            get() = VALUE_TYPE_START

        override val isAtFinish: Boolean
            get() = false // always false
    }

    private inner class PrepTimeBellProportional : PrepTimeBellSpec {

        private val proportion: Double

        /**
         * Constructor from a [Bundle].
         * @param bundle the Bundle.
         * @throws PrepTimeBellWrongTypeException if the type in the given [Bundle] does not
         * match "proportional".
         * @throws PrepTimeBellConstructorException if there is some other problem with the bundle
         */
        @Throws(PrepTimeBellConstructorException::class)
        constructor(bundle: Bundle) {
            val type = bundle.getString(KEY_TYPE)
                    ?: throw PrepTimeBellConstructorException("No type found")
            if (type != valueType)
                throw PrepTimeBellWrongTypeException(valueType, type)
            if (!bundle.containsKey(KEY_PROPORTION))
                throw PrepTimeBellConstructorException("No proportion found")
            this.proportion = bundle.getDouble(KEY_PROPORTION, 0.0)
        }

        /**
         * Constructor from a [SharedPreferences] file.
         * @param prefs the [SharedPreferences] object
         * @param index the index of the desired bell specification in `prefs`.
         * @throws PrepTimeBellWrongTypeException if the type specified for the given index in
         * the given [SharedPreferences] file does not match "proportional".
         * @throws PrepTimeBellConstructorException if there is some other problem with the
         * preferences
         */
        @Throws(PrepTimeBellConstructorException::class)
        constructor(prefs: SharedPreferences, index: Int) {
            val type = prefs.getString(index.toString() + KEY_TYPE, "")
            if (type != valueType)
                throw PrepTimeBellWrongTypeException(index, valueType, type!!)
            if (!prefs.contains(index.toString() + KEY_PROPORTION))
                throw PrepTimeBellConstructorException(index, "No proportion found")
            this.proportion = prefs.getString(index.toString() + KEY_PROPORTION, "0")!!.toDouble()
        }

        override fun getBell(length: Long): BellInfo {
            // Calculate when the bell should ring
            val time = Math.round(length * proportion)
            return BellInfo(time, 1)
        }

        override fun saveToBundle(bundle: Bundle) {
            bundle.putString(KEY_TYPE, valueType)
            bundle.putDouble(KEY_PROPORTION, proportion)
        }

        override fun saveToPreferences(editor: SharedPreferences.Editor, index: Int) {
            editor.putString(index.toString() + KEY_TYPE, valueType)
            editor.putString(index.toString() + KEY_PROPORTION, proportion.toString())
        }

        override fun toString(): String {
            if (proportion == 0.0) return mContext.getString(R.string.prepTimeBellDescription_atStart)
            if (proportion == 1.0) return mContext.getString(R.string.prepTimeBellDescription_atFinish)
            val percentage = proportion * 100
            val percentageStr: String =
                    if (percentage == Math.round(percentage).toDouble())
                        String.format(Locale.getDefault(), "%d", Math.round(percentage))
                    else
                        String.format(Locale.getDefault(), "%.1f", percentage)
            return mContext.getString(R.string.prepTimeBellDescription_proportional, percentageStr)
        }

        private val valueType: String
            get() = VALUE_TYPE_PROPORTIONAL

        override val isAtFinish: Boolean
            get() = proportion == 1.0
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a bell based on the information in a given [Bundle].
     * The `Bundle` must have the following entries:
     *
     *  - A "type", a String that is either "start", "finish" or "proportional"
     *  - If "type" is "start" or "finish", then a "time", a long in seconds
     *  - If "type" is "proportional", then a "proportion", a double between 0 and 1
     *
     * @param bundle the [Bundle]
     */
    fun addFromBundle(bundle: Bundle) {
        val bell = createFromBundle(bundle) ?: return
        mBellSpecs.add(bell)
    }

    /**
     * Deletes the bell at the given index.
     * @param index the index to delete
     */
    fun deleteBell(index: Int) {
        mBellSpecs.removeAt(index)
    }

    /**
     * Deletes all bells, but if there is an existing bell that is at the finish, it leaves the
     * first such bell there.
     */
    fun deleteAllBells(spareFinish: Boolean) {
        val iterator = mBellSpecs.listIterator()
        var finishBellFound = false
        while (iterator.hasNext()) {
            val spec = iterator.next()

            // If it's the first finish bell and we're sparing finish bells, don't delete it
            if (!finishBellFound && spareFinish && spec.isAtFinish) {
                finishBellFound = true
                continue
            }

            iterator.remove()
        }
    }

    /**
     * @return `true` if there is at least one bell that is always at the finish,
     * `false` otherwise
     */
    fun hasFinishBell(): Boolean {
        return mBellSpecs.any { it.isAtFinish }
    }

    /**
     * @return `true` if there is at least one bell spec.
     */
    fun hasBells(): Boolean {
        return mBellSpecs.size > 0
    }

    /**
     * @return `true` if there is at least one bell that is not always at the finish,
     * or if there is more than one finish bell, `false` otherwise.  The effect of this
     * is that it will return `false` only if there is either a single finish bell only,
     * or no bells at all.
     */
    fun hasBellsOtherThanFinish(): Boolean {
        return when (mBellSpecs.size) {
            0 -> false
            1 -> !mBellSpecs[0].isAtFinish
            else -> true
        }
    }

    /**
     * @param index the index to look for
     * @return a [Bundle] representing the bell at `index`
     */
    fun getBellBundle(index: Int): Bundle {
        val bundle = Bundle()
        mBellSpecs[index].saveToBundle(bundle)
        return bundle
    }

    /**
     * @return An [ArrayList] of Strings being descriptions of the bell specs
     */
    fun getBellDescriptions(): ArrayList<String> {
        val descriptions = ArrayList<String>(mBellSpecs.size)
        for (spec in mBellSpecs)
            descriptions.add(spec.toString())
        return descriptions
    }

    /**
     * @param index the index of the bell spec whose description to retrieve
     * @return a String being the description of the bell spec
     */
    fun getBellDescription(index: Int): String {
        return mBellSpecs[index].toString()
    }

    /**
     * Returns a list of the bells that the current user-defined settings imply
     * for prep time of a given length
     * @param length total length of the prep time
     * @return an [ArrayList] of [BellInfo] objects sorted by time
     */
    fun getBellsList(length: Long): ArrayList<BellInfo> {

        // First, generate all the bells and put them in a list.
        // Don't bother adding null bells.
        val allBells = ArrayList<BellInfo>()
        for (spec in mBellSpecs) {
            val bell = spec.getBell(length) ?: continue

            // If it's a finish bell, make it a double bell
            if (spec.isAtFinish) bell.bellSoundInfo.numberOfBells = 2

            allBells.add(bell)
        }

        // Then, sort the bells in order of priority.
        // Currently, this sorts them in descending order of number of bells to play.
        allBells.sortWith { lhs, rhs ->
            rhs.bellSoundInfo.numberOfBells - lhs.bellSoundInfo.numberOfBells
        }

        // Then, run through the bells, adding only non-duplicates.
        val bells = ArrayList<BellInfo>()
        for (bell in allBells) {
            // Treat as a "duplicate" if the bell is within fifteen seconds of another bell.
            // (The bells are already in prioritised order.)
            val duplicate = bells.any { Math.abs(it.bellTime - bell.bellTime) < 15 }

            // If duplicate, skip the rest
            if (duplicate) continue

            // Otherwise, add the bell.
            bells.add(bell)
        }

        return bells
    }

    /**
     * Loads bell specifications from a [SharedPreferences] file
     * @param prefs a [SharedPreferences] instance
     */
    fun loadFromPreferences(prefs: SharedPreferences) {
        // Clear the bells list.
        mBellSpecs.clear()

        // Check if there's anything in the file.  We just do this by checking the "total
        // number of bells" field, and assume that if we can't find it, the file is empty
        // (since the file is useless to us without this field anyway).
        if (!prefs.contains(KEY_TOTAL_NUMBER_OF_BELLS)) {
            // If the file was empty, then load the default: a single bell at the end.
            val bell = PrepTimeBellFromFinish(0)
            mBellSpecs.add(bell)
            Log.i(TAG, "No file found, loaded default")
            return
        }

        // If we get to this point, that means there's something in the preferences file.
        // Proceed to load it.

        // Get the total number of bells
        val numberOfBells = prefs.getInt(KEY_TOTAL_NUMBER_OF_BELLS, 0)

        // For each bell, pass preferences to the appropriate constructor.
        for (index in 0 until numberOfBells) {

            val indexStr = index.toString()

            val type = prefs.getString(indexStr + KEY_TYPE, "")!!

            // If no type found, that's an error.  Skip.
            if (type == "") {
                Log.e(TAG, "$indexStr: No type found")
                continue
            }

            val bell: PrepTimeBellSpec
            try {
                bell = when (type) {
                    VALUE_TYPE_START -> PrepTimeBellFromStart(prefs, index)
                    VALUE_TYPE_FINISH -> PrepTimeBellFromFinish(prefs, index)
                    VALUE_TYPE_PROPORTIONAL -> PrepTimeBellProportional(prefs, index)
                    else -> {
                        Log.e(TAG, "$indexStr: Unrecognised type: $type")
                        continue
                    }
                }
                // Log.v(TAG, indexStr + ": Found a " + type);
            } catch (e: PrepTimeBellConstructorException) {
                Log.e(TAG, e.localizedMessage!!)
                continue
            }

            mBellSpecs.add(bell)
        }
    }

    fun replaceFromBundle(index: Int, bundle: Bundle) {
        val bell = createFromBundle(bundle) ?: return
        mBellSpecs[index] = bell
    }

    /**
     * Saves bell specifications to a [SharedPreferences] file
     * @param prefs a [SharedPreferences] instance
     */
    fun saveToPreferences(prefs: SharedPreferences) {
        // Convention:
        //  The keys are always numbers, followed by a hyphen, followed by a parameter.
        //  For example: "0-type", "0-time", "1-type", "1-time", etc.
        //  The numbers in the keys denote the order in which the bells are listed in the
        //  edit screen.  The saving and loading is handled by individual PrepTimeBellSpec
        //  subclasses.
        // Also, a separate field denotes the total number of bell specifications.

        val editor = prefs.edit()
        var index = 0

        // Save all the bell specifications
        for (spec in mBellSpecs) {
            spec.saveToPreferences(editor, index)
            index++
        }

        // Save the total number of bells
        editor.putInt(KEY_TOTAL_NUMBER_OF_BELLS, index)

        // Commit the changes
        editor.apply()
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Creates a [PrepTimeBellSpec] from a [Bundle].
     * @param bundle the [Bundle] containing the information from which the [PrepTimeBellSpec]
     * is to be created
     * @return the assembled [PrepTimeBellSpec], or `null` if there was an error.
     */
    private fun createFromBundle(bundle: Bundle): PrepTimeBellSpec? {
        val type = bundle.getString(KEY_TYPE)

        // If no type found, that's an error.  Skip.
        if (type == null) {
            Log.e(TAG, "createFromBundle: No type found")
            return null
        }

        val bell: PrepTimeBellSpec

        try {
            bell = when (type) {
                VALUE_TYPE_START -> PrepTimeBellFromStart(bundle)
                VALUE_TYPE_FINISH -> PrepTimeBellFromFinish(bundle)
                VALUE_TYPE_PROPORTIONAL -> PrepTimeBellProportional(bundle)
                else -> {
                    Log.e(TAG, "createFromBundle: Unrecognised type: $type")
                    return null
                }
            }
            Log.v(TAG, "createFromBundle: Found a $type")
        } catch (e: PrepTimeBellConstructorException) {
            Log.e(TAG, e.localizedMessage!!)
            return null
        }

        return bell
    }

    companion object {
        private const val TAG = "PrepTimeBellsManager"

        const val KEY_TYPE = "type"
        const val KEY_TIME = "time"
        const val KEY_PROPORTION = "proportion"
        const val VALUE_TYPE_START = "start"
        const val VALUE_TYPE_FINISH = "finish"
        const val VALUE_TYPE_PROPORTIONAL = "proportional"
        const val PREP_TIME_BELLS_PREFERENCES_NAME = "prep_time_bells"
        private const val KEY_TOTAL_NUMBER_OF_BELLS = "totalBells"
    }
}
