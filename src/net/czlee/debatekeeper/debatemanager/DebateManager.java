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
import net.czlee.debatekeeper.debateformat.DebatePhaseFormat;
import net.czlee.debatekeeper.debateformat.PeriodInfo;
import net.czlee.debatekeeper.debateformat.PrepTimeSimpleFormat;
import net.czlee.debatekeeper.debateformat.SpeechFormat;
import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;


/**
 * <p>DebateManager manages a debate by keeping track of speeches and running the speech timers.</p>
 *
 * <p>It is given a {@link DebateFormat}, which cannot then be changed.  If it must be changed, this
 * <code>DebateManager</code> must be destroyed and another one created with the new
 * <code>DebateFormat</code>.</p>
 *
 * <p>DebateManager is also capable of:</p>
 *  <ul>
 *  <li> navigating forwards and backwards between phases
 *  <li> storing times for phases
 *  </ul>
 *
 * <p>DebateManager is <b>not</b> capable of handling the GUI, but it sends a message to the
 * DebatingActivity to update the GUI.</p>
 *
 * <p>The internal mechanics of a single speech are handled by {@link DebatePhaseManager}.</p>
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-09
 */
public class DebateManager {

    private final DebateFormat        mDebateFormat;
    private final DebatePhaseManager  mPhaseManager;
    private final PoiManager          mPoiManager;
    private final Context             mContext;

    private final ArrayList<Long>     mSpeechTimes;
    private long                      mPrepTime;

    private boolean                   mPrepTimeEnabledByUser = true;
    private int                       mActiveSpeechIndex;
    private DebatePhaseType           mActivePhaseType;

    private static final String BUNDLE_SUFFIX_ITEM_TYPE    = ".cit";
    private static final String BUNDLE_SUFFIX_INDEX        = ".csi";
    private static final String BUNDLE_SUFFIX_SPEECH       = ".sm";
    private static final String BUNDLE_SUFFIX_SPEECH_TIMES = ".st";
    private static final String BUNDLE_SUFFIX_PREP_TIME    = ".pt";

    /**
     * Constructor.
     * @param context a {@link Context} relating to this DebateManager, probably a {@link Service}.
     * @param df The {@link DebateFormat} used by this DebateManager.
     * @param am The {@link AlertManager} used by this DebateManager.
     */
    public DebateManager(Context context, DebateFormat df, AlertManager am) {
        super();
        this.mContext       = context;
        this.mDebateFormat  = df;
        this.mPhaseManager = new DebatePhaseManager(am);
        // TODO un-hardcode this '15'
        this.mPoiManager    = new PoiManager(am, 15);
        this.mSpeechTimes   = new ArrayList<Long>();
        this.mPrepTime      = 0;

        this.mSpeechTimes.ensureCapacity(df.numberOfSpeeches());
        for (int i = 0; i < df.numberOfSpeeches(); i++)
            mSpeechTimes.add((long) 0);

        if (hasPrepTime()) {
            this.mActivePhaseType = DebatePhaseType.PREP_TIME;
            this.mActiveSpeechIndex = 0;
            this.mPhaseManager.loadSpeech(mDebateFormat.getPrepFormat(), getActivePhaseName());
        } else {
            this.mActivePhaseType = DebatePhaseType.SPEECH;
            this.mActiveSpeechIndex = 0;
            this.mPhaseManager.loadSpeech(mDebateFormat.getSpeechFormat(mActiveSpeechIndex), getActivePhaseName());
        }


    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Used to uniquely identify speeches and prep timers in a way that is independent of timer
     * index.  The reason we do this is so that we can enable and disable prep time, but still give
     * other classes a means of identifying which speeches before correlate to which speeches after.
     * We can't just do this using the timer index, as the timer index changes when we add or
     * remove prep time.  (Timer indices must always number consecutively from zero.)
     *
     * Users of this class should treat it as a black box.  It can change in any way to reflect
     * extensions of this class to support other debate structures.
     */
    public class DebatePhaseTag {
        // TODO
    }

    public enum DebatePhaseType {

        // Strings are used in the bundle in saveState() and restoreState().
        PREP_TIME ("prepTime"),
        SPEECH ("speech");

        private final String key;

        private DebatePhaseType(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return key;
        }

        public static DebatePhaseType toEnum(String key) {
            DebatePhaseType[] values = DebatePhaseType.values();
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
     * @return the current period info to be displayed
     */
    public PeriodInfo getActivePhaseCurrentPeriodInfo() {
        return mPhaseManager.getCurrentPeriodInfo();
    }

    /**
     * Returns the current time of the active phase.
     * @return the current time for the active phase
     */
    public long getActivePhaseCurrentTime() {
        return mPhaseManager.getCurrentTime();
    }

    /**
     * @return the {@link DebatePhaseFormat} for the active phase
     */
    public DebatePhaseFormat getActivePhaseFormat() {
        return mPhaseManager.getFormat();
    }

    /**
     * <p>Returns the phase index of the timer that is currently active.</p>
     * <p>The phase indices number consecutively starting from 0.  Note that phase indices don't
     * necessarily correlate with speeches if, for example, prep time is enabled or disabled.</p>
     * @param timerIndex the new timer index
     */
    public int getActivePhaseIndex() {
        if (hasPrepTime()) {
            if (mActivePhaseType == DebatePhaseType.PREP_TIME)
                return 0;
            else
                return mActiveSpeechIndex + 1;
        }
        else return mActiveSpeechIndex;
    }

    /**
     * Returns a human-readable name for the currently active phase of the debate. For prep time
     * this is (currently) always a string "Preparation time". For speeches it is the name of the
     * speech.
     * @return the name of the active phase of the debate
     */
    public String getActivePhaseName() {
        if (mActivePhaseType == DebatePhaseType.PREP_TIME)
            return mContext.getString(R.string.prepTime_title);
        else
            return mDebateFormat.getSpeechName(mActiveSpeechIndex);
    }

    /**
     * @return the next overtime bell, or <code>null</code> if there are no more overtime bells
     */
    public Long getActivePhaseNextOvertimeBellTime() {
        return mPhaseManager.getNextOvertimeBellTime();
    }

    /**
     * @return the current time on the POI timer, or <code>null</code> if the POI timer is not currently running.
     */
    public Long getCurrentPoiTime() {
        if (mPoiManager.isRunning())
            return mPoiManager.getCurrentTime();
        else
            return null;
    }

    /**
     * @return the debate format name
     */
    public String getDebateFormatName() {
        return mDebateFormat.getName();
    }

    /**
     * Returns the number of phases in this debate.  A "phase" is a part of a debate that is
     * governed by a single running timer, e.g. a speech or a period of preparation time.
     * @return the number of phases
     */
    public int getNumberOfPhases() {
        int count = mDebateFormat.numberOfSpeeches();
        if (hasPrepTime()) count += 1;
        return count;
    }

    /**
     * Returns the current time of the phase identified by the given phase index.
     * @param phaseIndex the phase index (numbered consecutively from 0 including all speeches
     * and prep time periods)
     * @return the current time for that phase
     */
    public long getPhaseCurrentTime(int phaseIndex) {
        validatePhaseIndex(phaseIndex);
        if (phaseIndex == getActivePhaseIndex())
            return getActivePhaseCurrentTime();
        else if (phaseIndex == 0 && hasPrepTime())
            return mPrepTime;
        else if (hasPrepTime())
            return mSpeechTimes.get(phaseIndex - 1);
        else
            return mSpeechTimes.get(phaseIndex);
    }

    /**
     * Returns the {@link DebatePhaseFormat} for the phase specified by the given phase index.
     * @param phaseIndex the position of the timer in the {@link DebateManager}
     * @return the {@link DebatePhaseFormat} for that position
     */
    public DebatePhaseFormat getPhaseFormat(int phaseIndex) {
        validatePhaseIndex(phaseIndex);
        if (phaseIndex == 0 && hasPrepTime())
            return mDebateFormat.getPrepFormat();
        else if (hasPrepTime())
            return mDebateFormat.getSpeechFormat(phaseIndex - 1);
        else
            return mDebateFormat.getSpeechFormat(phaseIndex);
    }

    /**
     * Returns a human-readable name for the phase specified by the given phase index.
     * @param phaseIndex the index of the required phase in the {@link DebateManager}.
     * @return the name of the phase (speech or prep time) at that position.
     */
    public String getPhaseName(int phaseIndex) {
        validatePhaseIndex(phaseIndex);
        if (phaseIndex == 0 && hasPrepTime())
            return mContext.getString(R.string.prepTime_title);
        else if (hasPrepTime())
            return mDebateFormat.getSpeechName(phaseIndex - 1);
        else
            return mDebateFormat.getSpeechName(phaseIndex);
    }

    /**
     * Calculates and returns when the next overtime bell
     * @return the next overtime bell after the time given, or <code>null</code> if there are no more overtime bells
     */
    public Long getPhaseNextOvertimeBellTime(int phaseIndex) {
        if (phaseIndex == getActivePhaseIndex()) return getActivePhaseNextOvertimeBellTime();
        long time   = getPhaseCurrentTime(phaseIndex);
        long length = getPhaseFormat(phaseIndex).getLength();
        return mPhaseManager.getNextOvertimeBellTimeAfter(time, length);
    }

    /**
     * @return the current state
     */
    public DebatePhaseManager.DebateTimerState getTimerStatus() {
        return mPhaseManager.getStatus();
    }

    /**
     * Moves to the next phase of the debate (speech or prep time).
     * If already on the last phase, does nothing.
     */
    public void goToNextPhase() {
        if (isInLastPhase()) return;
        setCurrentPhaseIndex(getActivePhaseIndex() + 1);
    }

    /**
     * Moves to the previous phase of the debate (speech or prep time).
     * If already on the first phase, does nothing.
     */
    public void goToPreviousPhase() {
        if (isInFirstPhase()) return;
        setCurrentPhaseIndex(getActivePhaseIndex() - 1);
    }

    /**
     * @return <code>true</code> if if POIs are allowed somewhere in the currently active phase,
     * <code>false</code> otherwise.
     */
    public boolean hasPoisInActivePhase() {
        DebatePhaseFormat spf = mPhaseManager.getFormat();
        if (spf.getClass() == SpeechFormat.class)
            return ((SpeechFormat) spf).hasPoisAllowedSomewhere();
        else
            return false;
    }

    /**
     * @return <code>true</code> if the active phase is the first phase, <code>false</code>
     * otherwise
     */
    public boolean isInFirstPhase() {
        return getActivePhaseIndex() == 0;
    }

    /**
     * @return <code>true</code> if the active phase is the last phase, <code>false</code>
     * otherwise
     */
    public boolean isInLastPhase() {
        return getActivePhaseIndex() == getNumberOfPhases() - 1;
    }

    /**
     * @return <code>true</code> if the active phase is prep time, <code>false</code> otherwise
     */
    public boolean isInPrepTime() {
        return mActivePhaseType == DebatePhaseType.PREP_TIME;
    }

    /**
     * Checks if the active phase is in overtime.
     * @return <code>true</code> is the active phase is in overtime, <code>false</code> otherwise.
     */
    public boolean isOvertime() {
        return mPhaseManager.isOvertime();
    }

    /**
     * @return <code>true</code> if the POI timer is running, <code>false</code> otherwise.
     */
    public boolean isPoiRunning() {
        return mPoiManager.isRunning();
    }

    /**
     * @return <code>true</code> if GUI elements relating to POIs should be displayed.
     * This is the case if either POIs are currently allowed, or the POI timer is currently
     * running (i.e. the POI in action started before the warning bell).
     */
    public boolean isPoisActive() {
        return mPhaseManager.getCurrentPeriodInfo().isPoisAllowed() || mPoiManager.isRunning();
    }

    /**
     * @return <code>true</code> if the prep time is controlled (this does not depend on whether
     * the active phase is prep time).  If there is no prep time, returns <code>false</code>.
     */
    public boolean isPrepTimeControlled() {
        if (!hasPrepTime()) return false;
        return mDebateFormat.getPrepFormat().isControlled();
    }

    /**
     * @return <code>true</code> if the timer is running, <code>false</code> otherwise
     */
    public boolean isRunning() {
        return mPhaseManager.isRunning();
    }

    /**
     * Cleans up, should be called before deleting.
     */
    public void release() {
        stopTimer();
    }

    /**
     * Resets the currently active phase.
     */
    public void resetActivePhase() {
        mPhaseManager.reset();
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
            mActivePhaseType = DebatePhaseType.toEnum(itemTypeValue);
        } catch (IllegalArgumentException e) {
            Log.e(this.getClass().getSimpleName(), "Invalid item type: " + itemTypeValue);
        }

        // Restore the current speech
        mActiveSpeechIndex = bundle.getInt(key + BUNDLE_SUFFIX_INDEX, 0);
        loadSpeech();

        // If there are saved speech times, restore them as well
        long[] speechTimes = bundle.getLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES);
        if (speechTimes != null)
            for (int i = 0; i < speechTimes.length; i++)
                mSpeechTimes.set(i, speechTimes[i]);

        // Restore the prep time
        mPrepTime = bundle.getLong(key + BUNDLE_SUFFIX_PREP_TIME, 0);

        mPhaseManager.restoreState(key + BUNDLE_SUFFIX_SPEECH, bundle);
    }

    /**
     * Saves the state of this <code>DebateManager</code> to a {@link Bundle}.
     * @param key A String to uniquely distinguish this <code>DebateManager</code> from any other
     *        objects that might be stored in the same Bundle.
     * @param bundle The Bundle to which to save this information.
     */
    public void saveState(String key, Bundle bundle) {

        // Take note of which item type we're in
        bundle.putString(key + BUNDLE_SUFFIX_ITEM_TYPE, mActivePhaseType.toString());

        // Take note of which speech we're on
        bundle.putInt(key + BUNDLE_SUFFIX_INDEX, mActiveSpeechIndex);

        // Save the speech times
        long[] speechTimes = new long[mSpeechTimes.size()];
        for (int i = 0; i < mSpeechTimes.size(); i++)
            speechTimes[i] = mSpeechTimes.get(i);
        bundle.putLongArray(key + BUNDLE_SUFFIX_SPEECH_TIMES, speechTimes);

        // Save the prep time
        bundle.putLong(key + BUNDLE_SUFFIX_PREP_TIME, mPrepTime);

        mPhaseManager.saveState(key + BUNDLE_SUFFIX_SPEECH, bundle);
    }

    /**
     * Sets the current time of the active phase.
     * This method will set the time even if the timer is running.
     * @param seconds the new time in seconds
     */
    public void setActivePhaseCurrentTime(long seconds) {
        mPhaseManager.setCurrentTime(seconds);
    }

    /**
     * Sets a broadcast sender for this speech manager.
     * <code>DebateManager</code> will call <code>sendBroadcast()</code> on the broadcast sender
     * when the timer counts up/down.
     * @param sender the {@link GuiUpdateBroadcastSender}
     */
    public void setBroadcastSender(GuiUpdateBroadcastSender sender) {
        this.mPhaseManager.setBroadcastSender(sender);
        this.mPoiManager.setBroadcastSender(sender);
    }

    /**
     * <p>Instructs the {@link DebateManager} to switch to the phase with the given phase index.
     * Does nothing if the new phase index is the same as the old phase index.</p>
     * <p>The phase indices number consecutively starting from 0.  Note that phase indices don't
     * necessarily correlate with speeches if, for example, whether prep time is enabled changes.</p>
     * @param phaseIndex the new timer index
     */
    public void setCurrentPhaseIndex(int phaseIndex) {
        if (phaseIndex == getActivePhaseIndex()) return; // do nothing if no change

        validatePhaseIndex(phaseIndex);
        saveSpeech();
        mPhaseManager.stop();

        if (hasPrepTime()) {
            if (phaseIndex == 0) {
                mActivePhaseType = DebatePhaseType.PREP_TIME;
                mActiveSpeechIndex = 0;
            } else {
                mActivePhaseType = DebatePhaseType.SPEECH;
                mActiveSpeechIndex = phaseIndex - 1;
            }
        } else {
            mActivePhaseType = DebatePhaseType.SPEECH;
            mActiveSpeechIndex = phaseIndex;
        }

        loadSpeech();

    }

    /**
     * Sets the overtime bell specifications.
     * @param firstBell The number of seconds after the finish time to ring the first overtime bell
     * @param period The time in between subsequence overtime bells
     */
    public void setOvertimeBells(long firstBell, long period) {
        mPhaseManager.setOvertimeBells(firstBell, period);
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
     * Sets whether prep time is enabled.
     * @param prepTimeEnabled <code>true</code> if the user wants prep time to be enabled, <code>false</code>
     * otherwise
     */
    public void setPrepTimeEnabled(boolean prepTimeEnabled) {
        mPrepTimeEnabledByUser = prepTimeEnabled;

        // Switch out of prep time if necessary, since if you disable prep time it doesn't make
        // any sense to continue to be in prep time
        if (prepTimeEnabled == false) {
            if (mActivePhaseType == DebatePhaseType.PREP_TIME) {
                saveSpeech();
                mPhaseManager.stop();
                mActivePhaseType    = DebatePhaseType.SPEECH;
                mActiveSpeechIndex = 0;
                loadSpeech();
            }
        }
    }

    /**
     * Starts the POI timer.
     */
    public void startPoiTimer() {
        mPoiManager.start();
    }

    /**
     * Starts the timer.
     */
    public void startTimer() {
        mPhaseManager.start();
    }

    /**
     * Stops the POI timer.
     */
    public void stopPoiTimer() {
        mPoiManager.stop();
    }

    /**
     * Stops the timer. Also stops the POI timer, since POIs can't be running when the timer is stopped.
     */
    public void stopTimer() {
        mPhaseManager.stop();
        stopPoiTimer();
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private boolean hasPrepTime() {
        return mPrepTimeEnabledByUser && mDebateFormat.hasPrepFormat();
    }

    private void loadSpeech() {
        switch (mActivePhaseType) {
        case PREP_TIME:
            mPhaseManager.loadSpeech(mDebateFormat.getPrepFormat(), getActivePhaseName(),
                    mPrepTime);
            break;
        case SPEECH:
            mPhaseManager.loadSpeech(mDebateFormat.getSpeechFormat(mActiveSpeechIndex),
                    getActivePhaseName(), mSpeechTimes.get(mActiveSpeechIndex));
        }
    }

    private void saveSpeech() {
        switch (mActivePhaseType) {
        case PREP_TIME:
            mPrepTime = mPhaseManager.getCurrentTime();
            break;
        case SPEECH:
            mSpeechTimes.set(mActiveSpeechIndex, mPhaseManager.getCurrentTime());
        }
    }

    private void validatePhaseIndex(int phaseIndex) {
        if (phaseIndex >= mDebateFormat.numberOfSpeeches() + 1)
            throw new IndexOutOfBoundsException(String.format("Position %d out of bounds, with prep time", phaseIndex));
    }

}
