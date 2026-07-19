/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

package net.czlee.debatekeeper.debatemanager

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.viewpager.widget.PagerAdapter
import net.czlee.debatekeeper.AlertManager
import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender
import net.czlee.debatekeeper.PrepTimeBellsManager
import net.czlee.debatekeeper.R
import net.czlee.debatekeeper.debateformat.DebateFormat
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat
import net.czlee.debatekeeper.debateformat.PeriodInfo
import net.czlee.debatekeeper.debateformat.PrepTimeSimpleFormat

/**
 * DebateManager manages a debate by keeping track of speeches and running the speech timers.
 *
 * It is given a [DebateFormat], which cannot then be changed.  If it must be changed, this
 * `DebateManager` must be destroyed and another one created with the new `DebateFormat`.
 *
 * DebateManager is also capable of:
 *
 *  - navigating forwards and backwards between phases
 *  - storing times for phases
 *
 * DebateManager is **not** capable of handling the GUI, but it sends a message to the
 * DebatingTimerFragment to update the GUI.
 *
 * The internal mechanics of a single speech are handled by [DebatePhaseManager].
 *
 * @param mContext a [Context] relating to this DebateManager, probably a Service.
 * @param mDebateFormat the [DebateFormat] used by this DebateManager.
 * @param am the [AlertManager] used by this DebateManager.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 */
class DebateManager(private val mContext: Context, private val mDebateFormat: DebateFormat,
                    am: AlertManager) {

    private val mPhaseManager = DebatePhaseManager(am)
    // TODO un-hardcode this '15'
    private val mPoiManager = PoiManager(am, 15)

    private val mSpeechTimes = ArrayList<Long>()
    private var mPrepTime: Long = 0

    private var mPrepTimeEnabledByUser = true
    private var mActiveSpeechIndex = 0
    private var mActivePhaseType: DebatePhaseType

    init {
        mSpeechTimes.ensureCapacity(mDebateFormat.numberOfSpeeches())
        for (i in 0 until mDebateFormat.numberOfSpeeches())
            mSpeechTimes.add(0L)

        if (hasPrepTime()) {
            mActivePhaseType = DebatePhaseType.PREP_TIME
            mActiveSpeechIndex = 0
            mPhaseManager.loadSpeech(mDebateFormat.getPrepFormat()!!, activePhaseName)
        } else {
            mActivePhaseType = DebatePhaseType.SPEECH
            mActiveSpeechIndex = 0
            mPhaseManager.loadSpeech(mDebateFormat.getSpeechFormat(mActiveSpeechIndex)!!,
                    activePhaseName)
        }
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Used to uniquely identify speeches and prep timers in a way that is independent of phase
     * index.  The reason we do this is so that we can enable and disable prep time, but still give
     * other classes a means of identifying which speeches before correlate to which speeches after.
     * We can't just do this using the phase index, as the phase index changes when we add or
     * remove prep time.  (Phase indices must always number consecutively from zero.)
     *
     * Users of this class should treat it as a black box.  It can change in any way to reflect
     * extensions of this class to support other debate structures.  The exception to this is
     * the public member [specialTag], which is always `null` for tags returned
     * by this class, but which user classes may like to use for their own purposes.  As of
     * 11 June 2013, this field is used by `DebatingActivity.DebateTimerDisplayPagerAdapter`
     * to mark tags where no debate is loaded.
     */
    class DebatePhaseTag {
        @JvmField
        var specialTag: String? = null
        internal var format: DebateFormat? = null
        internal var type: DebatePhaseType? = null
        internal var index = 0
    }

    enum class DebatePhaseType(private val key: String) {

        // Strings are used in the bundle in saveState() and restoreState().
        PREP_TIME("prepTime"),
        SPEECH("speech");

        override fun toString(): String {
            return key
        }

        companion object {
            @JvmStatic
            fun toEnum(key: String): DebatePhaseType {
                for (value in entries)
                    if (key == value.key)
                        return value
                throw IllegalArgumentException(
                        String.format("There is no enumerated constant '%s'", key))
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /** The current period info to be displayed. */
    val activePhaseCurrentPeriodInfo: PeriodInfo
        get() = mPhaseManager.currentPeriodInfo

    /** The current time for the active phase. */
    val activePhaseCurrentTime: Long
        get() = mPhaseManager.currentTime

    /** The [DebatePhaseFormat] for the active phase. */
    val activePhaseFormat: DebatePhaseFormat
        get() = mPhaseManager.format!!

    /**
     * The phase index of the timer that is currently active.
     *
     * The phase indices number consecutively starting from 0.  Note that phase indices don't
     * necessarily correlate with speeches if, for example, prep time is enabled or disabled.
     * Setting this instructs the `DebateManager` to switch to the phase with the given phase
     * index; it does nothing if the new phase index is the same as the old phase index.
     */
    var activePhaseIndex: Int
        get() = findPhaseIndex(mActivePhaseType, mActiveSpeechIndex)
        set(phaseIndex) {
            if (phaseIndex == activePhaseIndex) return // do nothing if no change

            validatePhaseIndex(phaseIndex)
            saveSpeech()
            mPhaseManager.stop()

            if (hasPrepTime()) {
                if (phaseIndex == 0) {
                    mActivePhaseType = DebatePhaseType.PREP_TIME
                    mActiveSpeechIndex = 0
                } else {
                    mActivePhaseType = DebatePhaseType.SPEECH
                    mActiveSpeechIndex = phaseIndex - 1
                }
            } else {
                mActivePhaseType = DebatePhaseType.SPEECH
                mActiveSpeechIndex = phaseIndex
            }

            loadSpeech()
        }

    /**
     * A human-readable name for the currently active phase of the debate. For prep time
     * this is (currently) always a string "Preparation time". For speeches it is the name of the
     * speech.
     */
    val activePhaseName: String
        get() = if (mActivePhaseType == DebatePhaseType.PREP_TIME)
            mContext.getString(R.string.prepTime_title)
        else
            mDebateFormat.getSpeechName(mActiveSpeechIndex)!!

    /** The next overtime bell, or `null` if there are no more overtime bells. */
    val activePhaseNextOvertimeBellTime: Long?
        get() = mPhaseManager.nextOvertimeBellTime

    /**
     * The current time on the POI timer, or `null` if the POI timer is not currently running.
     */
    val currentPoiTime: Long?
        get() = if (mPoiManager.isRunning)
            mPoiManager.currentTime
        else
            null

    /** The debate format name. */
    val debateFormatName: String
        get() = mDebateFormat.name

    /** The debate format short name. */
    val debateFormatShortName: String?
        get() = mDebateFormat.shortName

    /**
     * The number of phases in this debate.  A "phase" is a part of a debate that is
     * governed by a single running timer, e.g. a speech or a period of preparation time.
     */
    val numberOfPhases: Int
        get() {
            var count = mDebateFormat.numberOfSpeeches()
            if (hasPrepTime()) count += 1
            return count
        }

    /**
     * Returns the current time of the phase identified by the given phase index.
     * @param phaseIndex the phase index (numbered consecutively from 0 including all speeches
     * and prep time periods)
     * @return the current time for that phase
     */
    fun getPhaseCurrentTime(phaseIndex: Int): Long {
        validatePhaseIndex(phaseIndex)
        return if (phaseIndex == activePhaseIndex)
            activePhaseCurrentTime
        else if (phaseIndex == 0 && hasPrepTime())
            mPrepTime
        else if (hasPrepTime())
            mSpeechTimes[phaseIndex - 1]
        else
            mSpeechTimes[phaseIndex]
    }

    /**
     * Returns the [DebatePhaseFormat] for the phase specified by the given phase index.
     * @param phaseIndex the position of the timer in the [DebateManager]
     * @return the [DebatePhaseFormat] for that position
     */
    fun getPhaseFormat(phaseIndex: Int): DebatePhaseFormat {
        validatePhaseIndex(phaseIndex)
        return if (phaseIndex == 0 && hasPrepTime())
            mDebateFormat.getPrepFormat()!!
        else if (hasPrepTime())
            mDebateFormat.getSpeechFormat(phaseIndex - 1)!!
        else
            mDebateFormat.getSpeechFormat(phaseIndex)!!
    }

    /**
     * Returns a human-readable name for the phase specified by the given phase index.
     * @param phaseIndex the index of the required phase in the [DebateManager].
     * @return the name of the phase (speech or prep time) at that position.
     */
    fun getPhaseName(phaseIndex: Int): String {
        validatePhaseIndex(phaseIndex)
        return if (phaseIndex == 0 && hasPrepTime())
            mContext.getString(R.string.prepTime_title)
        else if (hasPrepTime())
            mDebateFormat.getSpeechName(phaseIndex - 1)!!
        else
            mDebateFormat.getSpeechName(phaseIndex)!!
    }

    /**
     * Returns the phase index for the phase referenced by a tag.  If the tag is from a DebateFormat
     * that is different to this one, always returns [NO_SUCH_PHASE].
     *
     * Note that while [getPhaseIndexForTag] and [getPhaseTagForIndex] are inverses of each
     * other, if the result of one is passed into the other after the phases are renumbered
     * (e.g. because prep time becomes enabled or disabled) then the result may not be the
     * original value.
     *
     * @param tag the [DebatePhaseTag] for the phase
     * @return the phase index for that tag, or [NO_SUCH_PHASE] if the phase is not found
     */
    fun getPhaseIndexForTag(tag: DebatePhaseTag): Int {
        if (mDebateFormat !== tag.format) {
            Log.i(TAG, String.format(
                    "getPhaseIndexForTag - no such phase, tag.format was %s, currently on %s",
                    tag.format?.name ?: "null", mDebateFormat.name))
            return NO_SUCH_PHASE
        }
        return findPhaseIndex(tag.type!!, tag.index)
    }

    /**
     * Returns a tag that uniquely identifies a phase of the debate.  The tag will remain the
     * same even if phases are added or removed or re-ordered.  Currently, this happens when
     * prep time is enabled or disabled.
     * @param phaseIndex the phase index for the phase whose tag is to be retrieved
     * @return a [DebatePhaseTag] object being the suitable tag
     */
    fun getPhaseTagForIndex(phaseIndex: Int): DebatePhaseTag {
        val tag = DebatePhaseTag()
        if (hasPrepTime()) {
            if (getPhaseFormat(phaseIndex).isPrep) {
                tag.type = DebatePhaseType.PREP_TIME
                tag.index = 0
            } else {
                tag.type = DebatePhaseType.SPEECH
                tag.index = phaseIndex - 1
            }
        } else {
            tag.type = DebatePhaseType.SPEECH
            tag.index = phaseIndex
        }
        tag.format = mDebateFormat
        return tag
    }

    /**
     * Calculates and returns when the next overtime bell is.
     * @return the next overtime bell after the time given, or `null` if there are no more
     * overtime bells
     */
    fun getPhaseNextOvertimeBellTime(phaseIndex: Int): Long? {
        if (phaseIndex == activePhaseIndex) return activePhaseNextOvertimeBellTime
        val time = getPhaseCurrentTime(phaseIndex)
        val length = getPhaseFormat(phaseIndex).length
        return mPhaseManager.getNextOvertimeBellTimeAfter(time, length)
    }

    /** The current state of the timer. */
    val timerStatus: DebatePhaseManager.DebateTimerState
        get() = mPhaseManager.status

    /**
     * Moves to the next phase of the debate (speech or prep time).
     * If already on the last phase, does nothing.
     */
    fun goToNextPhase() {
        if (isInLastPhase) return
        activePhaseIndex += 1
    }

    /**
     * Moves to the previous phase of the debate (speech or prep time).
     * If already on the first phase, does nothing.
     */
    fun goToPreviousPhase() {
        if (isInFirstPhase) return
        activePhaseIndex -= 1
    }

    /** `true` if the active phase is the first phase, `false` otherwise. */
    val isInFirstPhase: Boolean
        get() = activePhaseIndex == 0

    /** `true` if the active phase is the last phase, `false` otherwise. */
    val isInLastPhase: Boolean
        get() = activePhaseIndex == numberOfPhases - 1

    /** `true` if the POI timer is running, `false` otherwise. */
    val isPoiRunning: Boolean
        get() = mPoiManager.isRunning

    /**
     * `true` if GUI elements relating to POIs should be displayed.
     * This is the case if either POIs are currently allowed, or the POI timer is currently
     * running (i.e. the POI in action started before the warning bell).
     */
    val isPoisActive: Boolean
        get() = mPhaseManager.currentPeriodInfo.isPoisAllowed || mPoiManager.isRunning

    /** `true` if the timer is running, `false` otherwise. */
    val isRunning: Boolean
        get() = mPhaseManager.isRunning

    /**
     * Cleans up, should be called before deleting.
     */
    fun release() {
        stopTimer()
    }

    /**
     * Resets the currently active phase.
     */
    fun resetActivePhase() {
        mPhaseManager.reset()
    }

    /**
     * Restores the state of this `DebateManager` from a [Bundle].
     * @param key A String to uniquely distinguish this `DebateManager` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    fun restoreState(key: String, bundle: Bundle) {

        // Restore the current item type
        val itemTypeValue = bundle.getString(key + BUNDLE_SUFFIX_ITEM_TYPE)
        if (itemTypeValue == null)
            Log.e(TAG, "restoreState: No item type found")
        else try {
            mActivePhaseType = DebatePhaseType.toEnum(itemTypeValue)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "restoreState: Invalid item type: $itemTypeValue")
        }

        // Restore the current speech
        mActiveSpeechIndex = bundle.getInt(key + BUNDLE_SUFFIX_INDEX, 0)
        loadSpeech()

        // If there are saved speech times, restore them as well
        val speechTimes = bundle.getLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES)
        if (speechTimes != null)
            for (i in speechTimes.indices)
                mSpeechTimes[i] = speechTimes[i]

        // Restore the prep time
        mPrepTime = bundle.getLong(key + BUNDLE_SUFFIX_PREP_TIME, 0)

        mPhaseManager.restoreState(key + BUNDLE_SUFFIX_SPEECH, bundle)
    }

    /**
     * Saves the state of this `DebateManager` to a [Bundle].
     * @param key A String to uniquely distinguish this `DebateManager` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    fun saveState(key: String, bundle: Bundle) {

        // Take note of which item type we're in
        bundle.putString(key + BUNDLE_SUFFIX_ITEM_TYPE, mActivePhaseType.toString())

        // Take note of which speech we're on
        bundle.putInt(key + BUNDLE_SUFFIX_INDEX, mActiveSpeechIndex)

        // Save the speech times
        val speechTimes = LongArray(mSpeechTimes.size)
        for (i in mSpeechTimes.indices)
            speechTimes[i] = mSpeechTimes[i]
        bundle.putLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES, speechTimes)

        // Save the prep time
        bundle.putLong(key + BUNDLE_SUFFIX_PREP_TIME, mPrepTime)

        mPhaseManager.saveState(key + BUNDLE_SUFFIX_SPEECH, bundle)
    }

    /**
     * Sets the current time of the active phase.
     * This method will set the time even if the timer is running.
     * @param seconds the new time in seconds
     */
    fun setActivePhaseCurrentTime(seconds: Long) {
        mPhaseManager.setCurrentTime(seconds)
    }

    /**
     * Sets a broadcast sender for this speech manager.
     * `DebateManager` will call `sendBroadcast()` on the broadcast sender
     * when the timer counts up/down.
     * @param sender the [GuiUpdateBroadcastSender]
     */
    fun setBroadcastSender(sender: GuiUpdateBroadcastSender?) {
        mPhaseManager.setBroadcastSender(sender)
        mPoiManager.setBroadcastSender(sender)
    }

    /**
     * Sets the overtime bell specifications.
     * @param firstBell The number of seconds after the finish time to ring the first overtime bell
     * @param period The time in between subsequence overtime bells
     */
    fun setOvertimeBells(firstBell: Long, period: Long) {
        mPhaseManager.setOvertimeBells(firstBell, period)
    }

    /**
     * Sets the [PrepTimeBellsManager] for the prep time format.
     * If the prep time is controlled for the current format, or if there is no prep time, does
     * nothing.
     * @param ptbm the [PrepTimeBellsManager] object
     */
    fun setPrepTimeBellsManager(ptbm: PrepTimeBellsManager?) {
        val prepFormat = mDebateFormat.getPrepFormat()
        // If the format isn't a PrepTimeSimpleFormat, do nothing - this just means the bells
        // manager isn't applicable to this case.
        (prepFormat as? PrepTimeSimpleFormat)?.setBellsManager(ptbm)
    }

    /**
     * Sets whether prep time is enabled.
     * @param prepTimeEnabled `true` if the user wants prep time to be enabled, `false` otherwise
     */
    fun setPrepTimeEnabled(prepTimeEnabled: Boolean) {
        mPrepTimeEnabledByUser = prepTimeEnabled

        // Switch out of prep time if necessary, since if you disable prep time it doesn't make
        // any sense to continue to be in prep time
        if (!prepTimeEnabled && mActivePhaseType == DebatePhaseType.PREP_TIME) {
            saveSpeech()
            mPhaseManager.stop()
            mActivePhaseType = DebatePhaseType.SPEECH
            mActiveSpeechIndex = 0
            loadSpeech()
        }
    }

    /**
     * Starts the POI timer.
     */
    fun startPoiTimer() {
        mPoiManager.start()
    }

    /**
     * Starts the timer.
     */
    fun startTimer() {
        mPhaseManager.start()
    }

    /**
     * Stops the POI timer.
     */
    fun stopPoiTimer() {
        mPoiManager.stop()
    }

    /**
     * Stops the timer. Also stops the POI timer, since POIs can't be running when the timer is
     * stopped.
     */
    fun stopTimer() {
        mPhaseManager.stop()
        stopPoiTimer()
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private fun hasPrepTime(): Boolean {
        return mPrepTimeEnabledByUser && mDebateFormat.hasPrepFormat()
    }

    private fun loadSpeech() {
        when (mActivePhaseType) {
            DebatePhaseType.PREP_TIME ->
                mPhaseManager.loadSpeech(mDebateFormat.getPrepFormat()!!, activePhaseName,
                        mPrepTime)
            DebatePhaseType.SPEECH ->
                mPhaseManager.loadSpeech(mDebateFormat.getSpeechFormat(mActiveSpeechIndex)!!,
                        activePhaseName, mSpeechTimes[mActiveSpeechIndex])
        }
    }

    private fun saveSpeech() {
        when (mActivePhaseType) {
            DebatePhaseType.PREP_TIME -> mPrepTime = mPhaseManager.currentTime
            DebatePhaseType.SPEECH ->
                mSpeechTimes[mActiveSpeechIndex] = mPhaseManager.currentTime
        }
    }

    private fun validatePhaseIndex(phaseIndex: Int) {
        if (phaseIndex >= mDebateFormat.numberOfSpeeches() + 1)
            throw IndexOutOfBoundsException(
                    String.format("Position %d out of bounds, with prep time", phaseIndex))
    }

    /**
     * Converts a phase type and speech index (which together uniquely identify a phase in a
     * debate) to a phase index.  Note that phase indices for a given phase can change depending
     * on whether prep time is enabled.
     * @param type the [DebatePhaseType]
     * @param speechIndex the index of the speech, if applicable (ignored if not applicable)
     * @return the appropriate phase index, or [NO_SUCH_PHASE] =
     * [PagerAdapter.POSITION_NONE] if not applicable
     */
    private fun findPhaseIndex(type: DebatePhaseType, speechIndex: Int): Int {
        return if (hasPrepTime()) {
            when (type) {
                DebatePhaseType.PREP_TIME -> 0
                DebatePhaseType.SPEECH -> speechIndex + 1
            }
        } else {
            when (type) {
                DebatePhaseType.PREP_TIME -> NO_SUCH_PHASE
                DebatePhaseType.SPEECH -> speechIndex
            }
        }
    }

    companion object {
        private const val TAG = "DebateManager"

        private const val BUNDLE_SUFFIX_ITEM_TYPE = ".cit"
        private const val BUNDLE_SUFFIX_INDEX = ".csi"
        private const val BUNDLE_SUFFIX_SPEECH = ".sm"
        private const val BUNDLE_SUFFIX_SPEECH_TIMES = ".st"
        private const val BUNDLE_SUFFIX_PREP_TIME = ".pt"

        private const val NO_SUCH_PHASE = PagerAdapter.POSITION_NONE
    }
}
