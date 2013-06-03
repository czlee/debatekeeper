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

package net.czlee.debatekeeper.debatemanager;

import java.util.Timer;
import java.util.TimerTask;

import net.czlee.debatekeeper.AlertManager;
import net.czlee.debatekeeper.debateformat.BellInfo;
import net.czlee.debatekeeper.debateformat.BellSoundInfo;
import net.czlee.debatekeeper.debateformat.PeriodInfo;
import net.czlee.debatekeeper.debateformat.SpeechFormat;
import net.czlee.debatekeeper.debateformat.SpeechOrPrepFormat;

import android.os.Bundle;
import android.util.Log;

/**
 * MainTimerManager manages the mechanics of a single speech.  Exactly one instance should exist
 * during a debate.  <code>MainTimerManager</code> can switch between speeches dynamically&mdash;there
 * is no need to destroy and/or re-create this instance in order to start a new speech.
 *
 * MainTimerManager is responsible for:<br>
 *  <ul>
 *  <li> Keeping time
 *  <li> Keeping track of, and setting off, bells.
 *  <li> Keeping track of period information and providing it when asked.
 *  </ul>
 *
 *  MainTimerManager doesn't remember anything about speeches that are no longer loaded.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 *
 */
public class MainTimerManager extends DebateElementManager {

    private SpeechOrPrepFormat       mFormat;
    private String                   mSpeechName;
    private PeriodInfo               mCurrentPeriodInfo;
    private Timer                    mTimer;
    private DebatingTimerState       mState = DebatingTimerState.NOT_STARTED;
    private long                     mFirstOvertimeBellTime = 30;
    private long                     mOvertimeBellPeriod    = 20;
    protected long mCurrentTime;

    private static final String BUNDLE_SUFFIX_TIME        = ".t";
    private static final String BUNDLE_SUFFIX_STATE       = ".s";
    private static final String BUNDLE_SUFFIX_PERIOD_INFO = ".cpi";

    /**
     * Constructor.
     * @param am the AlertManager associated with this instance
     */
    public MainTimerManager(AlertManager am) {
        super(am);
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    public enum DebatingTimerState {
        NOT_STARTED,
        RUNNING,
        STOPPED_BY_USER,
        STOPPED_BY_BELL,
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************
    private class IncrementTimeTask extends TimerTask {

        @Override
        public void run() {
            // Increment the counter
            mCurrentTime++;

            // Send an update GUI broadcast, if applicable
            sendBroadcast();

            // If this is a bell time, raise the bell
            BellInfo thisBell = mFormat.getBellAtTime(mCurrentTime);
            if (thisBell != null)
                handleBell(thisBell);

            // If this is an overtime bell time, raise a bell
            if (isOvertimeBellTime(mCurrentTime))
                doOvertimeBell();
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Loads a speech with time zero seconds.
     * This does the same thing as <code>loadSpeech(sf, 0)</code>.
     * @param sf The speech format to load
     * @throws IllegalStateException if the timer is currently running
     */
    public void loadSpeech(SpeechOrPrepFormat sf, String name) {
        loadSpeech(sf, name, 0);
    }

    /**
     * Loads a speech with a given time
     * @param sf The speech format to load
     * @param seconds The time in seconds to load
     * @throws IllegalStateException if the timer is currently running
     */
    public void loadSpeech(SpeechOrPrepFormat sf, String name, long seconds) {
        if (mState == DebatingTimerState.RUNNING)
            throw new IllegalStateException("Can't load speech while timer running");

        mFormat = sf;
        mSpeechName = name;
        mCurrentTime = seconds;

        if (seconds == 0) {
            mCurrentPeriodInfo = sf.getFirstPeriodInfo();
            mState = DebatingTimerState.NOT_STARTED;
        } else {
            mCurrentPeriodInfo = sf.getPeriodInfoForTime(seconds);
            mState = DebatingTimerState.STOPPED_BY_USER;
        }
    }

    /**
     * Starts the timer.
     * Calling this while the timer is running has no effect.
     * Calling this before a speech format has been set has no effect.
     */
    @Override
    public void start() {
        if (mFormat == null)
            return;
        if (mState == DebatingTimerState.RUNNING)
            return;
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new IncrementTimeTask(), TIMER_DELAY, TIMER_PERIOD);
        mState = DebatingTimerState.RUNNING;
        mAlertManager.makeActive(mSpeechName);
    }

    /**
     * Stops the timer.
     */
    @Override
    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mState = DebatingTimerState.STOPPED_BY_USER;
        mAlertManager.makeInactive();
    }

    /**
     * Resets the timer, stopping it if necessary.
     */
    public void reset() {
        stop();
        mCurrentTime = 0;
        mCurrentPeriodInfo = mFormat.getFirstPeriodInfo();
        mState = DebatingTimerState.NOT_STARTED;
    }

    /**
     * @return the current state of the timer
     */
    public DebatingTimerState getStatus() {
        return this.mState;
    }

    /**
     * @return the current time in seconds, starting from zero and counting up (always)
     */
    public long getCurrentTime() {
        return mCurrentTime;
    }

    /**
     * @return the {@link PeriodInfo} object currently appropriate to be displayed to the user
     */
    public PeriodInfo getCurrentPeriodInfo() {
        return mCurrentPeriodInfo;
    }

    /**
     * Returns the next overtime bell time in seconds as a Long object.
     * @return the next overtime bell time in seconds, or <code>null</code> if there are no more bells.
     * If it is not yet overtime, it still returns the time of the first overtime bell.
     * Note that this can be <code>null</code>.
     */
    public Long getNextOvertimeBellTime() {

        if (mFirstOvertimeBellTime == 0)
            return null;

        long speechLength   = mFormat.getLength();
        long overtimeAmount = mCurrentTime - speechLength;

        if (overtimeAmount < mFirstOvertimeBellTime)
            return speechLength + mFirstOvertimeBellTime;

        // If past the first overtime bell, keep adding periods until we find one we haven't hit yet
        if (mOvertimeBellPeriod == 0)
            return null;

        long overtimeBellTime = mFirstOvertimeBellTime + mOvertimeBellPeriod;

        // TODO make this more efficient
        while (overtimeAmount > overtimeBellTime)
            overtimeBellTime += mOvertimeBellPeriod;
        return speechLength + overtimeBellTime;
    }

    /**
     * @return the current {@link SpeechFormat}
     */
    public SpeechOrPrepFormat getFormat() {
        return mFormat;
    }

    /**
     * @return <code>true</code> if the timer is running, <code>false</code> otherwise
     */
    @Override
    public boolean isRunning() {
        return getStatus() == DebatingTimerState.RUNNING;
    }

    /**
     * Checks whether the speech is in overtime.
     * @return <code>true</code> if the current time exceeds the speech length,
     * <code>false</code> otherwise.
     * <code>false</code> if the current time and speech length are equal
     */
    public boolean isOvertime() {
        return mCurrentTime > mFormat.getLength();
    }

    /**
     * Sets the current time.
     * This method will set the time even if the timer is currently running.
     * @param seconds the new time in seconds
     */
    public void setCurrentTime(long seconds){
        mCurrentTime = seconds;

        // If the timer is currently stopped, then change the state to the appropriate stopped state.
        // If the timer is running, then it will still be running after this.  (This class will
        // allow this, but the UI probably shouldn't allow editing during running.)  Note that
        // if the timer is paused by a bell (STOPPED_BY_BELL) then its state will change to either
        // NOT_STARTED or STOPPED_BY_USER, since the user has now intervened so it's not really a
        // pause-by-bell anymore.
        if (mState != DebatingTimerState.RUNNING)
            mState = (mCurrentTime == 0) ? DebatingTimerState.NOT_STARTED : DebatingTimerState.STOPPED_BY_USER;

        // restore the appropriate period info
        mCurrentPeriodInfo = mFormat.getPeriodInfoForTime(seconds);
    }

    /**
     * Sets the overtime bell specifications
     * @param firstBell The number of seconds after the finish time to ring the first overtime bell
     * @param period The time in between subsequence overtime bells
     */
    public void setOvertimeBells(long firstBell, long period) {
        mFirstOvertimeBellTime = firstBell;
        mOvertimeBellPeriod    = period;
    }

    /**
     * Saves the state of this <code>MainTimerManager</code> to a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>MainTimerManager</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    public void saveState(String key, Bundle bundle) {
        bundle.putLong(key + BUNDLE_SUFFIX_TIME, mCurrentTime);
        bundle.putString(key + BUNDLE_SUFFIX_STATE, mState.name());
        mCurrentPeriodInfo.saveState(key + BUNDLE_SUFFIX_PERIOD_INFO, bundle);
    }

    /**
     * Restores the state of this <code>MainTimerManager</code> from a {@link Bundle}.
     * <code>loadSpeech()</code> should be called <b>before</b> this is called.
     * @param key A String to uniquely distinguish this <code>MainTimerManager</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    public void restoreState(String key, Bundle bundle) {
        mCurrentTime = bundle.getLong(key + BUNDLE_SUFFIX_TIME, 0);

        String stateString = bundle.getString(key + BUNDLE_SUFFIX_STATE);
        if (stateString == null)
            mState = (mCurrentTime == 0) ? DebatingTimerState.NOT_STARTED : DebatingTimerState.STOPPED_BY_USER;
        else try {
            mState = DebatingTimerState.valueOf(stateString);
        } catch (IllegalArgumentException e) {
            mState = (mCurrentTime == 0) ? DebatingTimerState.NOT_STARTED : DebatingTimerState.STOPPED_BY_USER;
        }

        mCurrentPeriodInfo.restoreState(key + BUNDLE_SUFFIX_PERIOD_INFO, bundle);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Stops the timer and puts it into the "stopped by bell" state.
     * Also wakes up the screen so that the user knows.
     */
    private void pause() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mState = DebatingTimerState.STOPPED_BY_BELL;
        mAlertManager.wakeUpScreenForPause();
    }

    /**
     * Triggers appropriate user interface elements arising from a bell.
     * @param bi the {@link BellInfo} to be handled
     */
    private void handleBell(BellInfo bi) {
        Log.v(this.getClass().getSimpleName(), String.format("bell at %s", mCurrentTime));
        if (bi.isPauseOnBell())
            pause();
        mCurrentPeriodInfo.update(bi.getNextPeriodInfo());
        mAlertManager.triggerAlert(bi.getBellSoundInfo());
    }

    /**
     * @return true if this time is an overtime bell
     */
    private boolean isOvertimeBellTime(long time) {
        long overtimeAmount = time - mFormat.getLength();

        // Don't bother checking if we haven't hit the first overtime bell
        if (overtimeAmount < mFirstOvertimeBellTime)
            return false;

        // There is no concept of overtime if the first overtime bell is zero
        if (mFirstOvertimeBellTime <= 0)
            return false;

        // First check the first bell
        // Specifications are only valid if greater than zero
        if (mFirstOvertimeBellTime == overtimeAmount)
            return true;

        // Then, check for subsequent bell matches
        long timeSinceFirstOvertimeBell = overtimeAmount - mFirstOvertimeBellTime;

        if (mOvertimeBellPeriod > 0)
            if (timeSinceFirstOvertimeBell % mOvertimeBellPeriod == 0)
                return true;

        return false;
    }

    /**
     * Does an overtime bell.
     */
    private void doOvertimeBell() {
        Log.v(this.getClass().getSimpleName(), String.format("overtime bell at %s", mCurrentTime));
        mAlertManager.playBell(new BellSoundInfo(3));
    }

}
