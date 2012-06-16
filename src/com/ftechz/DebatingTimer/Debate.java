package com.ftechz.DebatingTimer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;

import android.os.Bundle;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 * Debate class
 * This class manages a debate, i.e. a series of AlarmChains.
 */
public class Debate {
    public enum DebateStatus {
        StartOfSpeaker,      // After a speaker's screen is loaded, but before the timer starts
        TimerRunning,        // While the timer is running (including overtime)
        TimerStoppedByAlarm, // The timer is paused by an alarm
        TimerStoppedByUser,  // The timer is stopped by the user
        EndOfDebate          // After the very last speaker, i.e. the debate is over
    }

    //
    // Members
    private LinkedList<AlarmChain> mStages;
    private AlarmChain mCurrentStage;
    private int mCurrentStageInt;  // Used for saving the instance state
    private Iterator<AlarmChain> mStageIterator;
    private final TeamsManager mTeamsManager;
    private Timer mTickTimer;

    private final AlertManager mAlertManager;
    private final HashMap<String, BellInfo[]> mAlertSets;
    private final HashMap<String, Long> mFinishTimes; // We can't use primitive longs in HashMap, so we use Longs instead

    //
    // Methods
    public Debate(AlertManager alertManager) {
        mAlertManager = alertManager;
        mStages = new LinkedList<AlarmChain>();
        mAlertSets = new HashMap<String, BellInfo[]>();
        mFinishTimes = new HashMap<String, Long>();
        mStageIterator = mStages.iterator();
        mTeamsManager = new TeamsManager();
        mTickTimer = new Timer();
    }

    /**
     * Add a stage to the debate
     * Has to be added in the order of the debate
     *
     * @param alarmChain
     * @param alarmSetName The name of the alarmSet specified when added to the debate
     * @return
     */
    public boolean addStage(AlarmChain alarmChain, String alarmSetName) {
        if (mAlertSets.containsKey(alarmSetName)) {
            alarmChain.addTimes(mAlertSets.get(alarmSetName));

            if (mFinishTimes.containsKey(alarmSetName)) {
                alarmChain.setFinishTime(mFinishTimes.get(alarmSetName).longValue());
            }

            if (alarmChain.getClass() == SpeakerTimer.class) {
                SpeakerTimer speakerTimer = (SpeakerTimer) alarmChain;
                speakerTimer.setSpeakersManager(mTeamsManager);
            }

            alarmChain.setAlertManager(mAlertManager);

            mStages.add(alarmChain);

            mStageIterator = mStages.iterator();
            return true;
        }
        return false;
    }

    /**
     * Add an alarm set that will be used in this debate
     *
     * @param name
     * @param alarmSet
     */
    public void addAlarmSet(String name, BellInfo[] alarmSet, long finishTime) {
        mAlertSets.put(name, alarmSet);
        mFinishTimes.put(name, new Long(finishTime));
    }

    /**
     *
     * @param team
     */
    public int addTeam(Team team) {
        return mTeamsManager.addTeam(team);
    }

    public void setSide(int team, TeamsManager.SpeakerSide side) {
        mTeamsManager.setSide(team, side);
    }

    public boolean isLastSpeaker() {
        return !mStageIterator.hasNext();
    }

    public boolean prepareNextSpeaker() {
        if (mStageIterator.hasNext()) {
            if (mCurrentStage != null) {
                mCurrentStage.cancel();
            }
            mCurrentStage = mStageIterator.next();
            mCurrentStage.resetState();
            mCurrentStageInt += 1;
            return true;
        } else {
            mCurrentStage = null;
            mCurrentStageInt = 0;
        }

        return false;
    }

    public void stop() {
        if (getDebateStatus() == DebateStatus.TimerRunning) {
            if (mCurrentStage != null) {
                mAlertManager.makeInactive();
                mCurrentStage.stop();
            }
        }
    }

    public boolean start() {
        if ((getDebateStatus() == DebateStatus.StartOfSpeaker) ||
                (getDebateStatus() == DebateStatus.TimerStoppedByUser)) {
                if (mCurrentStage != null) {
                    mTickTimer.purge();
                    mCurrentStage.setTimer(mTickTimer);
                    mCurrentStage.start();
                    mAlertManager.makeInactive();   // Hide if already showing
                    mAlertManager.makeActive(mCurrentStage.mCurrentPeriodInfo);
                }
            return true;
        }
        return false;
    }

    public boolean isRunning() {
        return (getDebateStatus() == DebateStatus.TimerRunning);
    }

    public boolean isSilentMode() {
        return mAlertManager.isSilentMode();
    }

    public void setSilentMode(boolean silentMode) {
        mAlertManager.setSilentMode(silentMode);
    }

    public boolean isVibrateMode() {
        return mAlertManager.isVibrateMode();
    }

    public void setVibrateMode(boolean vibrateMode) {
        mAlertManager.setVibrateMode(vibrateMode);
    }

    public void playBell() {
        mAlertManager.playBell();
    }

    public DebateStatus getDebateStatus() {
        if (mCurrentStage != null) {
            switch (mCurrentStage.getRunningState()) {
                case Running:
                    return DebateStatus.TimerRunning;
                case StoppedByAlarm:
                    return DebateStatus.TimerStoppedByAlarm;
                case StoppedByUser:
                    return DebateStatus.TimerStoppedByUser;
                case BeforeStart:
                default:
                    return DebateStatus.StartOfSpeaker;
            }
        } else {
            return DebateStatus.StartOfSpeaker;
        }
    }

    public String getStageName() {
        if (mCurrentStage != null) {
            return mCurrentStage.getName();
        }
        return "";
    }

    public long getStageCurrentTime() {
        if (mCurrentStage != null) {
            return mCurrentStage.getSecondsForDisplay();
        } else {
            return 0;
        }
    }

    public long getStageNextTime() {
        if (mCurrentStage != null) {
            return mCurrentStage.getNextTimeForDisplay();
        } else {
            return 0;
        }
    }

    public long getStageFinalTime() {
        if (mCurrentStage != null) {
            return mCurrentStage.getFinishTimeForDisplay();
        } else {
            return 0;
        }
    }

    public String getStageStateText() {
        if (mCurrentStage != null) {
            return mCurrentStage.getStateText();
        } else {
            return "";
        }
    }

    public int getStageBackgroundColor() {
        if (mCurrentStage != null) {
            return mCurrentStage.getCurrentBackgroundColor();
        } else {
            return 0;
        }
    }

    public void resetSpeaker() {
        // If there is a current timer, stop and reset it
        if (mCurrentStage != null) {
            mCurrentStage.resetState();
        }

        // If there is no current timer, retrieve the first
        else {
            mStageIterator = mStages.iterator();
            prepareNextSpeaker(); // This sets mCurrentStage
        }

        mAlertManager.makeInactive(); // if it exists

    }

    public void resetDebate() {
        // Stop current stage timer, if on
        if (mCurrentStage != null) {
            mCurrentStage.cancel();
        }

        mCurrentStage = null;
        mCurrentStageInt = 0;
        mTickTimer.purge();
        mTickTimer.cancel();
        mTickTimer = new Timer();

        mAlertManager.makeInactive();

        ListIterator<AlarmChain> stageIterator = mStages.listIterator();
        while (stageIterator.hasNext()) {
            AlarmChain stage = stageIterator.next();
            stage.cancel();
            stageIterator.set(stage.newCopy());
        }

        mStageIterator = mStages.iterator();
        resetSpeaker();
    }

    public void release() {
        if (mAlertManager != null) {
            mAlertManager.makeInactive();
        }

        mCurrentStage = null;

        if (mTickTimer != null) {
            mTickTimer.cancel();
            mTickTimer.purge();
            mTickTimer = null;
        }

        mStages = null;
    }

    public void resume() {
        if (mCurrentStage != null) {
            mCurrentStage.setTimer(mTickTimer); // if not already set (no effect if it is)
            mCurrentStage.resume();
            mAlertManager.makeInactive();   // Hide if already showing
            mAlertManager.makeActive(mCurrentStage.mCurrentPeriodInfo);
       }
    }

    public void pause() {
        if (mCurrentStage != null) {
            mCurrentStage.pause();
        }
    }

    /**
     * Saves the state of the debate.  To be called by DebatingActivity.onSaveInstanceState().
     */
    public void saveState(Bundle bundle) {
        // Things to save:
        //   1. The current speaker.
        //   2. The current time.
        //   3. The current state.

        bundle.putInt("currentStageNumber", mCurrentStageInt);
        if (mCurrentStage != null) {
            mCurrentStage.saveState("currentStage", bundle);
        }
    }

    public void restoreState(Bundle bundle) {
        // Loop through to put the iterator in the right state
        // Default to first speaker
        mCurrentStageInt = bundle.getInt("currentStageNumber", 1);

        mStageIterator = mStages.iterator();

        if (mCurrentStageInt <= 0) mCurrentStageInt = 1; // Must be on at least the first speaker (no zeroth speaker)
        for (int i = 0; i < mCurrentStageInt; i++) {
            if (mStageIterator.hasNext()) {
                mCurrentStage = mStageIterator.next();
            } else {
                // If we hit the end, start from the beginning and break
                mStageIterator = mStages.iterator();
                if (mStageIterator.hasNext()) {
                    mCurrentStage = mStageIterator.next();
                } else {
                    mCurrentStage = null;
                }
                break;
            }
        }
        if (mCurrentStage != null) {
            mCurrentStage.restoreState("currentStage", bundle);
        }
    }

}
