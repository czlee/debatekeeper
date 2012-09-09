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

package net.czlee.debatekeeper;

import java.util.Timer;
import java.util.TimerTask;


/**
 * PoiManager governs the timer for points of information.
 * It does <b>not</b> check that POIs are currently permissible or anything like that,
 * it just times POIs.  The GUI should control whether POIs can actually be started.
 * @author Chuan-Zheng Lee
 * @since  2012-09-01
 */
public class PoiManager extends DebateElementManager {

    private Timer mTimer;
    private PoiTimerState mState;
    private int mPoiLength;
    protected long mCurrentTime;
    /**
     * @param poiLength the length of points of information timed by this PoiManager.
     */
    public PoiManager(AlertManager am, int poiLength) {
        super(am);
        this.mPoiLength = poiLength;
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    private enum PoiTimerState {
        NOT_RUNNING,
        RUNNING,
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private class DecrementTimeTask extends TimerTask {

        @Override
        public void run() {
            if (mCurrentTime == 0) {
                // If time expired a second ago (i.e. before the decrement), stop the timer
                PoiManager.this.stop();

            } else {
                // Decrement the counter
                mCurrentTime--;

                // If time has just expired, do the alert
                if (mCurrentTime == 0)
                    PoiManager.this.doTimeExpiredAlert();

                // Send an update GUI broadcast, if applicable
                sendBroadcast();
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Starts a new POI timer.  If a POI timer is currently running, that is discarded.
     */
    @Override
    public void start() {
        if (mTimer != null)
            mTimer.cancel();
        mCurrentTime = mPoiLength;
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new DecrementTimeTask(), TIMER_DELAY, TIMER_PERIOD);
        mState = PoiTimerState.RUNNING;
        sendBroadcast();
    }

    /**
     * Stops the current POI timer.
     */
    @Override
    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mState = PoiTimerState.NOT_RUNNING;
        mCurrentTime = 0;
        sendBroadcast();
    }

    /**
     * @return true if the POI timer is running, false otherwise.
     */
    @Override
    public boolean isRunning() {
        return mState == PoiTimerState.RUNNING;
    }

    /**
     * @return the current time in seconds, as it would be displayed on the countdown.
     */
    public long getCurrentTime() {
        return mCurrentTime;
    }

    /**
     * @param poiLength the new length of points of information timed by this PoiManager.
     */
    public void setPoiLength(int poiLength) {
        this.mPoiLength = poiLength;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private void doTimeExpiredAlert() {
        mAlertManager.triggerPoiAlert();
    }

}
