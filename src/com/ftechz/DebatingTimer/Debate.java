package com.ftechz.DebatingTimer;

import java.util.*;

/**
 * Debate class
 * This class manages a debate, i.e. a series of AlarmChains.
 */
public class Debate {
    public enum DebateStatus {
        startOfSpeaker,      // After a speaker's screen is loaded, but before the timer starts
        timerRunning,        // While the timer is running (including overtime)
        timerStoppedByAlarm, // The timer is paused by an alarm
        timerStoppedByUser,  // The timer is stopped by the user
        endOfDebate          // After the very last speaker, i.e. the debate is over
    }

    //
    // Members
    private LinkedList<AlarmChain> mStages;
    private AlarmChain mCurrentStage;
    private Iterator<AlarmChain> mStageIterator;
    private TeamsManager mTeamsManager;
    private Timer mTickTimer;

    private AlertManager mAlertManager;
    private HashMap<String, AlarmChain.AlarmChainAlert[]> mAlertSets;

    //
    // Methods
    public Debate(AlertManager alertManager) {
        mAlertManager = alertManager;
        mStages = new LinkedList<AlarmChain>();
        mAlertSets = new HashMap<String, AlarmChain.AlarmChainAlert[]>();
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
    public void addAlarmSet(String name, AlarmChain.AlarmChainAlert[] alarmSet) {
        for (AlarmChain.AlarmChainAlert alarm : alarmSet) {
            alarm.setAlertManager(mAlertManager);
        }

        mAlertSets.put(name, alarmSet);
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
        if (getDebateStatus() == DebateStatus.timerRunning) {
            if (mCurrentStage != null) {
                mAlertManager.hideNotification();
                mCurrentStage.stop();
            }
        }
    }

    public boolean start() {
        if ((getDebateStatus() == DebateStatus.startOfSpeaker) ||
                (getDebateStatus() == DebateStatus.timerStoppedByUser)) {
                if (mCurrentStage != null) {
                    mTickTimer.purge();
                    mCurrentStage.setTimer(mTickTimer);
                    mCurrentStage.start();
                    mAlertManager.hideNotification();   // Hide if already showing
                    mAlertManager.showNotification(mCurrentStage);
                }
            return true;
        }
        return false;
    }

    public DebateStatus getDebateStatus() {
        if (mCurrentStage != null) {
            switch (mCurrentStage.getRunningState()) {
                case Running:
                    return DebateStatus.timerRunning;
                case StoppedByAlarm:
                    return DebateStatus.timerStoppedByAlarm;
                case StoppedByUser:
                    return DebateStatus.timerStoppedByUser;
                case BeforeStart:
                default:
                    return DebateStatus.startOfSpeaker;
            }
        } else {
            return DebateStatus.startOfSpeaker;
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
            return mCurrentStage.getSeconds();
        } else {
            return 0;
        }
    }

    public long getStageNextTime() {
        if (mCurrentStage != null) {
            return mCurrentStage.getNextTime();
        } else {
            return 0;
        }
    }

    public long getStageFinalTime() {
        if (mCurrentStage != null) {
            return mCurrentStage.getFinalTime();
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
        
        mAlertManager.hideNotification(); // if it exists
        
    }
    
    // TODO: This method is not actually currently used, and may not even be correct
    public void resetDebate() {
        // Stop current stage timer, if on
        if (mCurrentStage != null) {
            mCurrentStage.cancel();
        }
        mCurrentStage = null;
        mTickTimer.purge();
        mTickTimer.cancel();
        mTickTimer = new Timer();

        mAlertManager.hideNotification();

        ListIterator<AlarmChain> stageIterator = mStages.listIterator();
        while (stageIterator.hasNext()) {
            AlarmChain stage = stageIterator.next();
            stage.cancel();
            stageIterator.set(stage.newCopy());
        }

        mStageIterator = mStages.iterator();
    }

    public void release() {
        if (mAlertManager != null) {
            mAlertManager.hideNotification();
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
        }
    }

    public void pause() {
        if (mCurrentStage != null) {
            mCurrentStage.pause();
        }
    }

}
