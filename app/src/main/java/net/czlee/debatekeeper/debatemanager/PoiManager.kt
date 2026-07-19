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

import net.czlee.debatekeeper.AlertManager
import java.util.Timer
import java.util.TimerTask

/**
 * PoiManager governs the timer for points of information.
 * It does **not** check that POIs are currently permissible or anything like that,
 * it just times POIs.  The GUI should control whether POIs can actually be started.
 * @param poiLength the length of points of information timed by this PoiManager.
 * @author Chuan-Zheng Lee
 * @since  2012-09-01
 */
class PoiManager(am: AlertManager, private var mPoiLength: Int) : DebateElementManager(am) {

    private var mTimer: Timer? = null
    private var mState = PoiTimerState.NOT_RUNNING

    /** The current time in seconds, as it would be displayed on the countdown. */
    var currentTime: Long = 0
        private set

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private enum class PoiTimerState {
        NOT_RUNNING,
        RUNNING,
    }

    private inner class DecrementTimeTask : TimerTask() {

        override fun run() {
            if (currentTime == 0L) {
                // If time expired a second ago (i.e. before the decrement), stop the timer
                stop()
            } else {
                // Decrement the counter
                currentTime--

                // If time has just expired, do the alert
                if (currentTime == 0L)
                    doTimeExpiredAlert()

                // Send an update GUI broadcast, if applicable
                sendBroadcast()
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Starts a new POI timer.  If a POI timer is currently running, that is discarded.
     */
    override fun start() {
        mTimer?.cancel()
        currentTime = mPoiLength.toLong()
        val timer = Timer()
        mTimer = timer
        timer.scheduleAtFixedRate(DecrementTimeTask(), TIMER_DELAY, TIMER_PERIOD)
        mState = PoiTimerState.RUNNING
        sendBroadcast()
    }

    /**
     * Stops the current POI timer.
     */
    override fun stop() {
        mTimer?.cancel()
        mTimer = null
        mState = PoiTimerState.NOT_RUNNING
        currentTime = 0
        sendBroadcast()
    }

    /**
     * `true` if the POI timer is running, `false` otherwise.
     */
    override val isRunning: Boolean
        get() = mState == PoiTimerState.RUNNING

    /**
     * @param poiLength the new length of points of information timed by this PoiManager.
     */
    fun setPoiLength(poiLength: Int) {
        mPoiLength = poiLength
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private fun doTimeExpiredAlert() {
        mAlertManager.triggerPoiAlert()
    }
}
