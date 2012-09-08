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

import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender;

/**
 * PoiManager governs the timer for points of information.
 * It does <b>not</b> check that POIs are currently permissible or anything like that,
 * it just times POIs.  The GUI should control whether POIs can actually be started.
 * @author Chuan-Zheng Lee
 * @since  2012-09-01
 */
public class PoiManager {

    private final AlertManager mAlertManager;
    private GuiUpdateBroadcastSender mBroadcastSender;
    private Timer mTimer;
    private PoiTimerState mState;
    private int mPoiLength;
    private int mCurrentTime = 0;

    private static final long TIMER_DELAY  = 1000;
    private static final long TIMER_PERIOD = 1000;

    /**
     * @param poiLength the length of points of information timed by this PoiManager.
     */
    public PoiManager(AlertManager am, int poiLength) {
        super();
        this.mAlertManager = am;
        this.mPoiLength = poiLength;
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    public enum PoiTimerState {
        NOT_RUNNING,
        RUNNING,
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private class DecrementTimeTask extends TimerTask {

        @Override
        public void run() {
            // Decrement the counter
            mCurrentTime--;

            // Send an update GUI broadcast, if applicable
            if (mBroadcastSender != null)
                mBroadcastSender.sendBroadcast();

            // If time has expired, stop the timer
            if (mCurrentTime == 0) {
                PoiManager.this.stop();
                PoiManager.this.doTimeExpiredAlert();
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Starts a new POI timer.  If a POI timer is currently running, that is discarded.
     */
    public void start() {
        if (mTimer != null)
            mTimer.cancel();
        mCurrentTime = mPoiLength;
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new DecrementTimeTask(), TIMER_DELAY, TIMER_PERIOD);
        mState = PoiTimerState.RUNNING;
        mBroadcastSender.sendBroadcast();
    }

    /**
     * Stops the current POI timer.
     */
    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mState = PoiTimerState.NOT_RUNNING;
        mCurrentTime = 0;
        mBroadcastSender.sendBroadcast();
    }

    /**
     * @return true if the POI timer is running, false otherwise.
     */
    public boolean isRunning() {
        return mState == PoiTimerState.RUNNING;
    }

    public int getCurrentTime() {
        return mCurrentTime;
    }

    /**
     * Sets a broadcast sender for this speech manager.
     * <code>PoiManager</code> will call <code>sendBroadcast()</code> on the broadcast sender
     * when the timer counts up/down.
     * @param sender the {@link GuiUpdateBroadcastSender}
     */
    public void setBroadcastSender(GuiUpdateBroadcastSender sender) {
        this.mBroadcastSender = sender;
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
