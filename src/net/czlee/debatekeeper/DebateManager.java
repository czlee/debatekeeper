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

import java.util.ArrayList;

import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender;
import android.content.Context;
import android.os.Bundle;


/**
 * DebateManager manages a debate by keeping track of speeches and running the speech timers.
 *
 * It is given a {@link DebateFormat}, which cannot then be changed.  If it must be changed, this
 * <code>DebateManager</code> must be destroyed and another one created with the new
 * <code>DebateFormat</code>.
 *
 * <p>DebateManager is also capable of:</p>
 *  <ul>
 *  <li> navigating forwards and backwards between speakers
 *  <li> storing times for speeches
 *  </ul>
 *
 * DebateManager is NOT capable of:
 *  <ul>
 *  <li> handling the GUI, but it sends a message to the DebatingActivity to update the GUI
 *  </ul>
 *
 * The internal mechanics of a single speech are handled by {@link SpeechOrPrepManager}.
 *
 * It does not handle the GUI.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 */
public class DebateManager {

    private final DebateFormat        mDebateFormat;
    private final SpeechOrPrepManager mSpeechManager;
    private final PoiManager          mPoiManager;
    private final Context             mContext;

    private final ArrayList<Long> mSpeechTimes;
    private long                  mPrepTime;

    private boolean           mPrepTimeEnabledByUser = true;
    private int               mCurrentSpeechIndex;
    private DebateManagerItem mCurrentItemType;

    private static final String BUNDLE_SUFFIX_INDEX        = ".csi";
    private static final String BUNDLE_SUFFIX_SPEECH       = ".sm";
    private static final String BUNDLE_SUFFIX_SPEECH_TIMES = ".st";
    private static final String BUNDLE_SUFFIX_PREP_TIME    = ".pt";

    /**
     * Constructor.
     * @param df The {@link DebateFormat} used by this debate manager.
     * @param am The {@link AlertManager} used by this debate manager.
     */
    public DebateManager(Context context, DebateFormat df, AlertManager am) {
        super();
        this.mContext       = context;
        this.mDebateFormat  = df;
        this.mSpeechManager = new SpeechOrPrepManager(am);
        // TODO un-hardcode this '15'
        this.mPoiManager    = new PoiManager(am, 15);
        this.mSpeechTimes   = new ArrayList<Long>();
        this.mPrepTime      = 0;

        this.mSpeechTimes.ensureCapacity(df.numberOfSpeeches());
        for (int i = 0; i < df.numberOfSpeeches(); i++)
            mSpeechTimes.add((long) 0);

        if (hasPrepTime()) {
            this.mCurrentItemType = DebateManagerItem.PREP_TIME;
            this.mCurrentSpeechIndex = 0;
            this.mSpeechManager.loadSpeech(mDebateFormat.getPrepFormat());
        } else {
            this.mCurrentItemType = DebateManagerItem.SPEECH;
            this.mCurrentSpeechIndex = 0;
            this.mSpeechManager.loadSpeech(mDebateFormat.getSpeechFormat(mCurrentSpeechIndex));
        }


    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    public enum DebateManagerItem {
        PREP_TIME, SPEECH
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Sets a broadcast sender for this speech manager.
     * <code>DebateManager</code> will call <code>sendBroadcast()</code> on the broadcast sender
     * when the timer counts up/down.
     * @param sender the {@link GuiUpdateBroadcastSender}
     */
    public void setBroadcastSender(GuiUpdateBroadcastSender sender) {
        this.mSpeechManager.setBroadcastSender(sender);
        this.mPoiManager.setBroadcastSender(sender);
    }

    /**
     * Starts the timer.
     */
    public void startTimer() {
        mSpeechManager.start();
    }

    /**
     * Stops the timer.
     * Also stops the POI timer, since POIs can't be running when the timer is stopped.
     */
    public void stopTimer() {
        mSpeechManager.stop();
        stopPoiTimer();
    }

    /**
     * Starts the POI timer.
     */
    public void startPoiTimer() {
        mPoiManager.start();
    }

    /**
     * Stops the POI timer.
     */
    public void stopPoiTimer() {
        mPoiManager.stop();
    }

    /**
     * Resets the current speaker.
     */
    public void resetSpeaker() {
        mSpeechManager.reset();
    }

    /**
     * Moves to the next item (speech or prep time).
     * If already on the last speaker, reloads the last speaker.
     */
    public void goToNextItem() {
        saveSpeech();
        mSpeechManager.stop();
        switch (mCurrentItemType) {
        case PREP_TIME:
            mCurrentSpeechIndex = 0;
            mCurrentItemType    = DebateManagerItem.SPEECH;
            break;
        case SPEECH:
            if (!isLastItem()) mCurrentSpeechIndex++;
        }
        loadSpeech();
    }

    /**
     * Moves to the previous item (speech or prep time).
     * If already on the first item, reloads the first item.
     */
    public void goToPreviousItem() {
        saveSpeech();
        mSpeechManager.stop();
        switch (mCurrentItemType) {
        case PREP_TIME:
            break;
        case SPEECH:
            if (mCurrentSpeechIndex == 0)
                mCurrentItemType = DebateManagerItem.PREP_TIME;
            else
                mCurrentSpeechIndex--;
        }
        loadSpeech();
    }

    /**
     * @return the current state
     */
    public SpeechOrPrepManager.DebatingTimerState getStatus() {
        return mSpeechManager.getStatus();
    }

    /**
     * @return <code>true</code> if the timer is running, <code>false</code> otherwise
     */
    public boolean isRunning() {
        return mSpeechManager.isRunning();
    }

    /**
     * @return <code>true</code> if the current speech is the first speech, <code>false</code>
     * otherwise
     */
    public boolean isFirstItem() {
        if (hasPrepTime())
            return mCurrentItemType == DebateManagerItem.PREP_TIME;
        else
            return mCurrentSpeechIndex == 0;
    }

    /**
     * @return <code>true</code> if the current speech is the last speech, <code>false</code>
     * otherwise
     */
    public boolean isLastItem() {
        return mCurrentItemType == DebateManagerItem.SPEECH &&
                mCurrentSpeechIndex == mDebateFormat.numberOfSpeeches() - 1;
    }

    /**
     * @return <code>true</code> if the current item is prep time, <code>false</code> otherwise
     */
    public boolean isPrepTime() {
        return mCurrentItemType == DebateManagerItem.PREP_TIME;
    }

    /**
     * @return <code>true</code> if the next bell will pause the timer, <code>false</code> otherwise.
     * Returns <code>false</code> if there are no more bells or if there are only overtime bells left.
     */
    public boolean isNextBellPause() {
        return mSpeechManager.isNextBellPause();
    }

    /**
     * Checks if the current speech is in overtime
     * @return <code>true</code> is the current speech is in overtime, <code>false</code> otherwise.
     */
    public boolean isOvertime() {
        return mSpeechManager.isOvertime();
    }

    /**
     * @return <code>true</code> if GUI elements relating to POIs should be displayed.
     * This is the case if either POIs are currently allowed, or the POI timer is currently
     * running (i.e. the POI in action started before the warning bell).
     */
    public boolean isPoisActive() {
        return mSpeechManager.getCurrentPeriodInfo().isPoisAllowed() || mPoiManager.isRunning();
    }

    /**
     * @return <code>true</code> if if POIs are allowed somewhere in the current speech,
     * <code>false</code> otherwise.
     */
    public boolean hasPoisInCurrentSpeech() {
        SpeechOrPrepFormat spf = mSpeechManager.getFormat();
        if (spf.getClass() == SpeechFormat.class)
            return ((SpeechFormat) spf).hasPoisAllowedSomewhere();
        else
            return false;
    }

    /**
     * @return <code>true</code> if the POI timer is running.
     */
    public boolean isPoiRunning() {
        return mPoiManager.isRunning();
    }

    /**
     * @return the current time for the current speaker
     */
    public long getCurrentSpeechTime() {
        return mSpeechManager.getCurrentTime();
    }

    /**
     * @return the current time for the POI timer, or if the POI timer is
     * not currently running, then null.
     */
    public Long getCurrentPoiTime() {
        if (mPoiManager.isRunning())
            return mPoiManager.getCurrentTime();
        else
            return null;
    }

    /**
     * @return the next bell time, or <code>null</code> if there are no more bells
     */
    public Long getNextBellTime() {
        return mSpeechManager.getNextBellTime();
    }

    /**
     * @return the current period info to be displayed
     */
    public PeriodInfo getCurrentPeriodInfo() {
        return mSpeechManager.getCurrentPeriodInfo();
    }

    /**
     * @return an ArrayList of speech times
     */
    public ArrayList<Long> getSpeechTimes() {
        return mSpeechTimes;
    }

    /**
     * @return the debate format name
     */
    public String getDebateFormatName() {
        return mDebateFormat.getName();
    }

    /**
     * @return the current speech name
     */
    public String getCurrentSpeechName() {
        if (mCurrentItemType == DebateManagerItem.PREP_TIME)
            return mContext.getString(R.string.PrepTimeTitle);
        else
            return mDebateFormat.getSpeechName(mCurrentSpeechIndex);
    }

    /**
     * @return the current {@link SpeechFormat}
     */
    public SpeechOrPrepFormat getCurrentSpeechFormat() {
        return mSpeechManager.getFormat();
    }

    /**
     * Sets the current speech time.
     * This method sets the current speech time even if the timer is running.
     * @param seconds the new time in seconds
     */
    public void setCurrentSpeechTime(long seconds) {
        mSpeechManager.setCurrentTime(seconds);
    }

    /**
     * Sets the overtime bell specifications
     * @param firstBell The number of seconds after the finish time to ring the first overtime bell
     * @param period The time in between subsequence overtime bells
     */
    public void setOvertimeBells(long firstBell, long period) {
        mSpeechManager.setOvertimeBells(firstBell, period);
    }

    public void setPrepTimeEnabled(boolean prepTimeEnabled) {
        mPrepTimeEnabledByUser = prepTimeEnabled;

        // Switch out of prep time if necessary, since if you disable prep time it doesn't make
        // any sense to continue to be in prep time
        if (prepTimeEnabled == false) {
            if (mCurrentItemType == DebateManagerItem.PREP_TIME) {
                saveSpeech();
                mSpeechManager.stop();
                mCurrentItemType    = DebateManagerItem.SPEECH;
                mCurrentSpeechIndex = 0;
                loadSpeech();
            }
        }
    }

    /**
     * Saves the state of this <code>DebateManager</code> to a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>DebateManager</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    public void saveState(String key, Bundle bundle) {

        // Take note of which speech we're on
        bundle.putInt(key + BUNDLE_SUFFIX_INDEX, mCurrentSpeechIndex);

        // Save the speech times
        long[] speechTimes = new long[mSpeechTimes.size()];
        for (int i = 0; i < mSpeechTimes.size(); i++)
            speechTimes[i] = mSpeechTimes.get(i);
        bundle.putLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES, speechTimes);

        // Save the prep time
        bundle.putLong(key + BUNDLE_SUFFIX_PREP_TIME, mPrepTime);

        mSpeechManager.saveState(key + BUNDLE_SUFFIX_SPEECH, bundle);
    }

    /**
     * Restores the state of this <code>DebateManager</code> from a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>DebateManager</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle from which to restore this information.
     */
    public void restoreState(String key, Bundle bundle) {

        // Restore the current speech
        mCurrentSpeechIndex = bundle.getInt(key + BUNDLE_SUFFIX_INDEX, 0);
        loadSpeech();

        // If there are saved speech times, restore them as well
        long[] speechTimes = bundle.getLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES);
        if (speechTimes != null)
            for (int i = 0; i < speechTimes.length; i++)
                mSpeechTimes.set(i, speechTimes[i]);

        // Restore the prep time
        mPrepTime = bundle.getLong(key + BUNDLE_SUFFIX_PREP_TIME, 0);

        mSpeechManager.restoreState(key + BUNDLE_SUFFIX_SPEECH, bundle);
    }

    /**
     * Cleans up, should be called before deleting.
     */
    public void release() {
        stopTimer();
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private boolean hasPrepTime() {
        return mPrepTimeEnabledByUser && mDebateFormat.hasPrepFormat();
    }

    private void saveSpeech() {
        switch (mCurrentItemType) {
        case PREP_TIME:
            mPrepTime = mSpeechManager.getCurrentTime();
            break;
        case SPEECH:
            mSpeechTimes.set(mCurrentSpeechIndex, mSpeechManager.getCurrentTime());
        }
    }

    private void loadSpeech() {
        switch (mCurrentItemType) {
        case PREP_TIME:
            mSpeechManager.loadSpeech(mDebateFormat.getPrepFormat(), mPrepTime);
            break;
        case SPEECH:
            mSpeechManager.loadSpeech(mDebateFormat.getSpeechFormat(mCurrentSpeechIndex),
                    mSpeechTimes.get(mCurrentSpeechIndex));
        }
    }

}
