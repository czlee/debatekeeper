package com.ftechz.DebatingTimer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;

/**
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
    private Iterator<AlarmChain> mStageIterator;
    private final TeamsManager mTeamsManager;
    private Timer mTickTimer;

    private final AlertManager mAlertManager;
    private final HashMap<String, AlarmChain.Event[]> mAlertSets;
    private final HashMap<String, Long> mFinishTimes; // We can't use primitive longs in HashMap, so we use Longs instead

    //
    // Methods
    public Debate(AlertManager alertManager) {
        mAlertManager = alertManager;
        mStages = new LinkedList<AlarmChain>();
        mAlertSets = new HashMap<String, AlarmChain.Event[]>();
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
    public void addAlarmSet(String name, AlarmChain.Event[] alarmSet, long finishTime) {
        for (AlarmChain.Event alarm : alarmSet) {
            alarm.setAlertManager(mAlertManager);
        }

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

    public boolean prepareNextSpeaker() {
        if (mStageIterator.hasNext()) {
            if (mCurrentStage != null) {
                mCurrentStage.cancel();
            }
            mCurrentStage = mStageIterator.next();
            mCurrentStage.resetState();
            return true;
        } else {
            mCurrentStage = null;
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
                    mAlertManager.makeActive(mCurrentStage);
                }
            return true;
        }
        return false;
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

        mTickTimer.cancel();
        mTickTimer.purge();
        mTickTimer = null;

        mStages = null;
    }

    public void resume() {
        if (mCurrentStage != null) {
            mCurrentStage.resume();
            mAlertManager.makeInactive();   // Hide if already showing
            mAlertManager.makeActive(mCurrentStage);
       }
    }

    public void pause() {
        if (mCurrentStage != null) {
            mCurrentStage.pause();
        }
    }

}
