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

import android.os.Bundle
import android.util.Log
import net.czlee.debatekeeper.AlertManager
import net.czlee.debatekeeper.debateformat.BellInfo
import net.czlee.debatekeeper.debateformat.BellSoundInfo
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat
import net.czlee.debatekeeper.debateformat.PeriodInfo
import java.util.Timer
import java.util.TimerTask

/**
 * DebatePhaseManager manages the mechanics of a single phase of a debate.  A "phase" of a debate
 * is a generic term to refer to a part governed by a single timer, e.g. a speech, or a period of
 * preparation time.
 *
 * Exactly one instance of DebatePhaseManager should exist during a debate.  DebatePhaseManager
 * can switch between various phases dynamically—there is no need to destroy and/or
 * re-create this instance in order to load or start a new phase (speech or prep time).  However,
 * DebatePhaseManager doesn't remember anything about phases that are no longer loaded.  Therefore,
 * users of DebatePhaseManager will need to take note of the current time if it would like to
 * save and restore the state of a phase.
 *
 * DebatePhaseManager is responsible for:
 *
 *  - Keeping time
 *  - Keeping track of, and setting off, bells.
 *  - Keeping track of period information and providing it when asked.
 *
 * @param am the AlertManager associated with this instance
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 */
class DebatePhaseManager(am: AlertManager) : DebateElementManager(am) {

    /** The current [DebatePhaseFormat]. */
    var format: DebatePhaseFormat? = null
        private set

    private var mPhaseName: String? = null

    /** The [PeriodInfo] object currently appropriate to be displayed to the user. */
    var currentPeriodInfo: PeriodInfo = PeriodInfo()
        private set

    private var mTimer: Timer? = null

    /** The current state of the timer. */
    var status = DebateTimerState.NOT_STARTED
        private set

    private var mFirstOvertimeBellTime: Long = 30
    private var mOvertimeBellPeriod: Long = 20

    /** The current time in seconds, starting from zero and counting up (always). */
    var currentTime: Long = 0
        private set

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Enumerated constant for the current state of the debate timer.
     */
    enum class DebateTimerState {
        NOT_STARTED,
        RUNNING,
        STOPPED_BY_USER,
        STOPPED_BY_BELL,
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private inner class IncrementTimeTask : TimerTask() {

        override fun run() {
            // Increment the counter
            currentTime++

            // Send an update GUI broadcast, if applicable
            sendBroadcast()

            // If this is a bell time, raise the bell
            val thisBell = format!!.getBellAtTime(currentTime)
            if (thisBell != null)
                handleBell(thisBell)

            // If this is an overtime bell time, raise a bell
            if (isOvertimeBellTime(currentTime))
                doOvertimeBell()
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Loads a speech with a given time (or zero if not specified).
     * @param sf The speech format to load
     * @param seconds The time in seconds to load
     * @throws IllegalStateException if the timer is currently running
     */
    @JvmOverloads
    fun loadSpeech(sf: DebatePhaseFormat, name: String, seconds: Long = 0) {
        check(status != DebateTimerState.RUNNING) { "Can't load speech while timer running" }

        format = sf
        mPhaseName = name
        currentTime = seconds

        if (seconds == 0L) {
            currentPeriodInfo = sf.firstPeriodInfo
            status = DebateTimerState.NOT_STARTED
        } else {
            currentPeriodInfo = sf.getPeriodInfoForTime(seconds)
            status = DebateTimerState.STOPPED_BY_USER
        }
    }

    /**
     * Starts the timer.
     * Calling this while the timer is running has no effect.
     * Calling this before a speech format has been set has no effect.
     */
    override fun start() {
        if (format == null)
            return
        if (status == DebateTimerState.RUNNING)
            return
        val timer = Timer()
        mTimer = timer
        timer.scheduleAtFixedRate(IncrementTimeTask(), TIMER_DELAY, TIMER_PERIOD)
        status = DebateTimerState.RUNNING
        mAlertManager.makeActive(mPhaseName)
    }

    /**
     * Stops the timer.
     */
    override fun stop() {
        mTimer?.cancel()
        mTimer = null
        status = DebateTimerState.STOPPED_BY_USER
        mAlertManager.makeInactive()
    }

    /**
     * Resets the timer, stopping it if necessary.
     */
    fun reset() {
        stop()
        currentTime = 0
        currentPeriodInfo = format!!.firstPeriodInfo
        status = DebateTimerState.NOT_STARTED
    }

    /**
     * Returns the next overtime bell time after the time given, assuming the length given.
     * @return the next overtime bell time in seconds, or `null` if there are no more bells.
     * If it is not yet overtime, it still returns the time of the first overtime bell.
     * Note that this can be `null`.
     */
    fun getNextOvertimeBellTimeAfter(time: Long, length: Long): Long? {

        // TODO is this the best place for this code?

        if (mFirstOvertimeBellTime == 0L)
            return null

        val overtimeAmount = time - length

        if (overtimeAmount < mFirstOvertimeBellTime)
            return length + mFirstOvertimeBellTime

        // If past the first overtime bell, keep adding periods until we find one we haven't hit yet
        if (mOvertimeBellPeriod == 0L)
            return null

        var overtimeBellTime = mFirstOvertimeBellTime + mOvertimeBellPeriod

        // TODO make this more efficient
        while (overtimeAmount > overtimeBellTime)
            overtimeBellTime += mOvertimeBellPeriod
        return length + overtimeBellTime
    }

    /**
     * Returns the next overtime bell time in seconds as a Long object.
     * @return the next overtime bell time in seconds, or `null` if there are no more bells.
     * If it is not yet overtime, it still returns the time of the first overtime bell.
     * Note that this can be `null`.
     */
    val nextOvertimeBellTime: Long?
        get() = getNextOvertimeBellTimeAfter(currentTime, format!!.length)

    /**
     * `true` if the timer is running, `false` otherwise.
     */
    override val isRunning: Boolean
        get() = status == DebateTimerState.RUNNING

    /**
     * Sets the current time.
     * This method will set the time even if the timer is currently running.
     * @param seconds the new time in seconds
     */
    fun setCurrentTime(seconds: Long) {
        currentTime = seconds

        // If the timer is currently stopped, then change the state to the appropriate stopped state.
        // If the timer is running, then it will still be running after this.  (This class will
        // allow this, but the UI probably shouldn't allow editing during running.)  Note that
        // if the timer is paused by a bell (STOPPED_BY_BELL) then its state will change to either
        // NOT_STARTED or STOPPED_BY_USER, since the user has now intervened so it's not really a
        // pause-by-bell anymore.
        if (status != DebateTimerState.RUNNING)
            status = if (currentTime == 0L) DebateTimerState.NOT_STARTED
                     else DebateTimerState.STOPPED_BY_USER

        // restore the appropriate period info
        currentPeriodInfo = format!!.getPeriodInfoForTime(seconds)
    }

    /**
     * Sets the overtime bell specifications
     * @param firstBell The number of seconds after the finish time to ring the first overtime bell
     * @param period The time in between subsequence overtime bells
     */
    fun setOvertimeBells(firstBell: Long, period: Long) {
        mFirstOvertimeBellTime = firstBell
        mOvertimeBellPeriod = period
    }

    /**
     * Saves the state of this `DebatePhaseManager` to a [Bundle].
     * @param key A String to uniquely distinguish this `DebatePhaseManager` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    fun saveState(key: String, bundle: Bundle) {
        bundle.putLong(key + BUNDLE_SUFFIX_TIME, currentTime)
        bundle.putString(key + BUNDLE_SUFFIX_STATE, status.name)
        currentPeriodInfo.saveState(key + BUNDLE_SUFFIX_PERIOD_INFO, bundle)
    }

    /**
     * Restores the state of this `DebatePhaseManager` from a [Bundle].
     * [loadSpeech] should be called **before** this is called.
     * @param key A String to uniquely distinguish this `DebatePhaseManager` from any other
     *   objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    fun restoreState(key: String, bundle: Bundle) {
        currentTime = bundle.getLong(key + BUNDLE_SUFFIX_TIME, 0)

        val stateString = bundle.getString(key + BUNDLE_SUFFIX_STATE)
        status = if (stateString == null) {
            if (currentTime == 0L) DebateTimerState.NOT_STARTED else DebateTimerState.STOPPED_BY_USER
        } else try {
            DebateTimerState.valueOf(stateString)
        } catch (e: IllegalArgumentException) {
            if (currentTime == 0L) DebateTimerState.NOT_STARTED else DebateTimerState.STOPPED_BY_USER
        }

        currentPeriodInfo.restoreState(key + BUNDLE_SUFFIX_PERIOD_INFO, bundle)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Stops the timer and puts it into the "stopped by bell" state.
     * Also wakes up the screen so that the user knows.
     */
    private fun pause() {
        mTimer?.cancel()
        mTimer = null
        status = DebateTimerState.STOPPED_BY_BELL
        mAlertManager.wakeUpScreenForPause()
    }

    /**
     * Triggers appropriate user interface elements arising from a bell.
     * @param bi the [BellInfo] to be handled
     */
    private fun handleBell(bi: BellInfo) {
        Log.v(TAG, "bell at $currentTime")
        if (bi.isPauseOnBell)
            pause()

        // It's important that the PeriodInfo update comes before triggering the alert in
        // AlertManager, to avoid a race condition between updating the PeriodInfo (which
        // affects the background colour) and reading the PeriodInfo for the screen flash.
        // (The screen flash will be on a different thread to this function, which is on a
        // TimerTask.)
        currentPeriodInfo.update(bi.nextPeriodInfo)
        mAlertManager.triggerAlert(bi.bellSoundInfo)
    }

    /**
     * @return true if this time is an overtime bell
     */
    private fun isOvertimeBellTime(time: Long): Boolean {
        val overtimeAmount = time - format!!.length

        // Don't bother checking if we haven't hit the first overtime bell
        if (overtimeAmount < mFirstOvertimeBellTime)
            return false

        // There is no concept of overtime if the first overtime bell is zero
        if (mFirstOvertimeBellTime <= 0)
            return false

        // First check the first bell
        // Specifications are only valid if greater than zero
        if (mFirstOvertimeBellTime == overtimeAmount)
            return true

        // Then, check for subsequent bell matches
        val timeSinceFirstOvertimeBell = overtimeAmount - mFirstOvertimeBellTime

        if (mOvertimeBellPeriod > 0)
            return timeSinceFirstOvertimeBell % mOvertimeBellPeriod == 0L

        return false
    }

    /**
     * Does an overtime bell.
     */
    private fun doOvertimeBell() {
        Log.v(TAG, "overtime bell at $currentTime")
        mAlertManager.playBell(BellSoundInfo(3))
    }

    companion object {
        private const val TAG = "DebatePhaseManager"

        private const val BUNDLE_SUFFIX_TIME = ".t"
        private const val BUNDLE_SUFFIX_STATE = ".s"
        private const val BUNDLE_SUFFIX_PERIOD_INFO = ".cpi"
    }
}
