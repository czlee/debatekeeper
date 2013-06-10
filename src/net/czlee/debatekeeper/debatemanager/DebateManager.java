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

import java.util.ArrayList;

import net.czlee.debatekeeper.AlertManager;
import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender;
import net.czlee.debatekeeper.PrepTimeBellsManager;
import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.DebateFormat;
import net.czlee.debatekeeper.debateformat.PeriodInfo;
import net.czlee.debatekeeper.debateformat.PrepTimeSimpleFormat;
import net.czlee.debatekeeper.debateformat.SpeechFormat;
import net.czlee.debatekeeper.debateformat.SpeechOrPrepFormat;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;


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
 * The internal mechanics of a single speech are handled by {@link MainTimerManager}.
 *
 * It does not handle the GUI.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 */
public class DebateManager {

    private final DebateFormat        mDebateFormat;
    private final MainTimerManager    mSpeechManager;
    private final PoiManager          mPoiManager;
    private final Context             mContext;

    private final ArrayList<Long> mSpeechTimes;
    private long                  mPrepTime;

    private boolean           mPrepTimeEnabledByUser = true;
    private int               mCurrentSpeechIndex;
    private DebateManagerItem mCurrentItemType;

    private static final String BUNDLE_SUFFIX_ITEM_TYPE    = ".cit";
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
        this.mSpeechManager = new MainTimerManager(am);
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
            this.mSpeechManager.loadSpeech(mDebateFormat.getPrepFormat(), getCurrentSpeechName());
        } else {
            this.mCurrentItemType = DebateManagerItem.SPEECH;
            this.mCurrentSpeechIndex = 0;
            this.mSpeechManager.loadSpeech(mDebateFormat.getSpeechFormat(mCurrentSpeechIndex), getCurrentSpeechName());
        }


    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    public enum DebateManagerItem {

        // Strings are used in the bundle in saveState() and restoreState().
        PREP_TIME ("prepTime"),
        SPEECH ("speech");

        private final String key;

        private DebateManagerItem(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }

        public static DebateManagerItem toEnum(String key) {
            DebateManagerItem[] values = DebateManagerItem.values();
            for (int i = 0; i < values.length; i++)
                if (key.equals(values[i].key))
                    return values[i];
            throw new IllegalArgumentException(String.format("There is no enumerated constant '%s'", key));
        }
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
     * Sets the {@link PrepTimeBellsManager} for the prep time format.
     * If the prep time is controlled for the current format, or if there is no prep time, does nothing.
     * @param ptbm
     */
    public void setPrepTimeBellsManager(PrepTimeBellsManager ptbm) {
        if (mDebateFormat.getPrepFormat() != null)
            try {
                ((PrepTimeSimpleFormat) mDebateFormat.getPrepFormat()).setBellsManager(ptbm);
            } catch (ClassCastException e) {
                // Do nothing - this just means the bells manager isn't applicable to this
                // case.
            }
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
     * If already on the last speaker, does nothing.
     */
    public void goToNextItem() {
        if (isLastItem()) return;
        setCurrentPosition(getCurrentPosition() + 1);
    }

    /**
     * Moves to the previous item (speech or prep time).
     * If already on the first item, does nothing.
     */
    public void goToPreviousItem() {
        if (isFirstItem()) return;
        setCurrentPosition(getCurrentPosition() - 1);
    }

    /**
     * Sets the current position (index of item that is current).  Does nothing if the new
     * position is the same as the old current position.
     * @param position the new position
     */
    public void setCurrentPosition(int position) {
        if (position == getCurrentPosition()) return; // do nothing if no change

        validatePosition(position);
        saveSpeech();
        mSpeechManager.stop();

        if (hasPrepTime()) {
            if (position == 0) {
                mCurrentItemType = DebateManagerItem.PREP_TIME;
                mCurrentSpeechIndex = 0;
            } else {
                mCurrentItemType = DebateManagerItem.SPEECH;
                mCurrentSpeechIndex = position - 1;
            }
        } else {
            mCurrentItemType = DebateManagerItem.SPEECH;
            mCurrentSpeechIndex = position;
        }

        loadSpeech();

    }

    /**
     * @return the current state
     */
    public MainTimerManager.DebatingTimerState getStatus() {
        return mSpeechManager.getStatus();
    }

    /**
     * @return the current position out of all the timers in the debate, including prep timers,
     * note that this can change when prep time is enabled or disabled
     */
    public int getCurrentPosition() {
        if (mCurrentItemType == DebateManagerItem.PREP_TIME)
            return 0;
        else if (hasPrepTime())
            return mCurrentSpeechIndex + 1;
        else
            return mCurrentSpeechIndex;
    }

    /**
     * @return the total number of timers
     */
    public int getCount() {
        int count = mDebateFormat.numberOfSpeeches();
        if (hasPrepTime()) count += 1;
        return count;
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
        return getCurrentPosition() == 0;
    }

    /**
     * @return <code>true</code> if the current speech is the last speech, <code>false</code>
     * otherwise
     */
    public boolean isLastItem() {
        return getCurrentPosition() == getCount() - 1;
    }

    /**
     * @return <code>true</code> if the current item is prep time, <code>false</code> otherwise
     */
    public boolean isInPrepTime() {
        return mCurrentItemType == DebateManagerItem.PREP_TIME;
    }

    /**
     * @return <code>true</code> if the prep time is controlled (this does not depend on whether
     * the current item is prep time).  If there is no prep time, returns <code>false</code>.
     */
    public boolean isPrepTimeControlled() {
        if (!hasPrepTime()) return false;
        return mDebateFormat.getPrepFormat().isControlled();
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
     * @return the current period info to be displayed
     */
    public PeriodInfo getCurrentPeriodInfo() {
        return mSpeechManager.getCurrentPeriodInfo();
    }

    /**
     * @param position the position of the timer in the {@link DebateManager}
     * @return the current time for that position
     */
    public long getSpeechTime(int position) {
        validatePosition(position);
        if (position == getCurrentPosition())
            return getCurrentSpeechTime();
        else if (position == 0 && hasPrepTime())
            return mPrepTime;
        else if (hasPrepTime())
            return mSpeechTimes.get(position - 1);
        else
            return mSpeechTimes.get(position);
    }

    /**
     * @return the debate format name
     */
    public String getDebateFormatName() {
        return mDebateFormat.getName();
    }

    /**
     * @param position the position of the timer in the {@link DebateManager}
     * @return the name of the timer (speech or prep time) at that position
     */
    public String getSpeechName(int position) {
        validatePosition(position);
        if (position == 0 && hasPrepTime())
            return mContext.getString(R.string.prepTime_title);
        else if (hasPrepTime())
            return mDebateFormat.getSpeechName(position - 1);
        else
            return mDebateFormat.getSpeechName(position);
    }

    /**
     * @return the current speech name
     */
    public String getCurrentSpeechName() {
        if (mCurrentItemType == DebateManagerItem.PREP_TIME)
            return mContext.getString(R.string.prepTime_title);
        else
            return mDebateFormat.getSpeechName(mCurrentSpeechIndex);
    }

    /**
     * @param position the position of the timer in the {@link DebateManager}
     * @return the {@link SpeechOrPrepFormat} for that position
     */
    public SpeechOrPrepFormat getFormat(int position) {
        validatePosition(position);
        if (position == 0 && hasPrepTime())
            return mDebateFormat.getPrepFormat();
        else if (hasPrepTime())
            return mDebateFormat.getSpeechFormat(position - 1);
        else
            return mDebateFormat.getSpeechFormat(position);
    }

    /**
     * @return the current {@link SpeechOrPrepFormat}
     */
    public SpeechOrPrepFormat getCurrentSpeechFormat() {
        return mSpeechManager.getFormat();
    }

    /**
     * @return the next overtime bell after the time given, or <code>null</code> if there are no more overtime bells
     */
    public Long getNextOvertimeBellTimeAfter(long time, int position) {
        long length = getFormat(position).getLength();
        return mSpeechManager.getNextOvertimeBellTimeAfter(time, length);
    }

    /**
     * @return the next overtime bell, or <code>null</code> if there are no more overtime bells
     */
    public Long getNextOvertimeBellTime() {
        return mSpeechManager.getNextOvertimeBellTime();
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

        // Take note of which item type we're in
        bundle.putString(key + BUNDLE_SUFFIX_ITEM_TYPE, mCurrentItemType.toString());

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

        // Restore the current item type
        String itemTypeValue = bundle.getString(key + BUNDLE_SUFFIX_ITEM_TYPE);
        if (itemTypeValue == null)
            Log.e(this.getClass().getSimpleName(), "No item type found");
        else try {
            mCurrentItemType = DebateManagerItem.toEnum(itemTypeValue);
        } catch (IllegalArgumentException e) {
            Log.e(this.getClass().getSimpleName(), "Invalid item type: " + itemTypeValue);
        }

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
            mSpeechManager.loadSpeech(mDebateFormat.getPrepFormat(), getCurrentSpeechName(),
                    mPrepTime);
            break;
        case SPEECH:
            mSpeechManager.loadSpeech(mDebateFormat.getSpeechFormat(mCurrentSpeechIndex),
                    getCurrentSpeechName(), mSpeechTimes.get(mCurrentSpeechIndex));
        }
    }

    private void validatePosition(int position) {
        if (position >= mDebateFormat.numberOfSpeeches() + 1)
            throw new IndexOutOfBoundsException(String.format("Position %d out of bounds, with prep time", position));
    }

}
