package com.ftechz.DebatingTimer;

import java.util.*;

/**
 * Debate class
 * In charge of the flow of stages (speakers) in the debate
 */
public class Debate
{
    public enum DebateStatus
    {
        setup,
        speaking,
        transitioning
    }

    //
    // Members
    private LinkedList<AlarmChain> mStages;
    private AlarmChain mCurrentStage;
    private Iterator<AlarmChain> mStageIterator;
    private Timer tickTimer;

    private DebateStatus debateStatus;

    private ArrayList<Speaker> mSpeakers;
    private AlertManager mAlertManager;
    private HashMap<String, AlarmChain.AlarmChainAlert[]> mAlertSets;

    //
    // Methods
    public Debate(AlertManager alertManager)
    {
        mAlertManager = alertManager;
        debateStatus = DebateStatus.setup;
        mStages = new LinkedList<AlarmChain>();
        mSpeakers = new ArrayList<Speaker>();
        mAlertSets = new HashMap<String, AlarmChain.AlarmChainAlert[]>();
        mStageIterator = mStages.iterator();
        tickTimer = new Timer();
    }

    public boolean addPrep(String alarmSetName)
    {
        if(mAlertSets.containsKey(alarmSetName))
        {
            mStages.add(new PrepTimer(mAlertSets.get(alarmSetName)));
            mStageIterator = mStages.iterator();
            return true;
        }
        return false;
    }

    public boolean addStage(Speaker speaker, String alarmSetName)
    {
        if(mAlertSets.containsKey(alarmSetName))
        {
            mStages.add(new SpeakerTimer(speaker, mAlertSets.get(alarmSetName)));
            mStageIterator = mStages.iterator();
            return true;
        }
        return false;
    }

    public void addAlarmSet(String name, AlarmChain.AlarmChainAlert[] alarmSet)
    {
        for(AlarmChain.AlarmChainAlert alarm : alarmSet)
        {
            alarm.setAlertManager(mAlertManager);
        }

        mAlertSets.put(name, alarmSet);
    }

    public void addSpeaker(Speaker speaker)
    {
        mSpeakers.add(speaker);
    }

    public boolean prepareNextSpeaker()
    {
        if(mStageIterator.hasNext())
        {
            if(mCurrentStage != null)
            {
                mCurrentStage.cancel();
            }
            mCurrentStage = mStageIterator.next();
            return true;
        }
        else
        {
            mCurrentStage = null;
        }

        return false;
    }

    public void startNextSpeaker()
    {
        if(mCurrentStage != null)
        {
            tickTimer.purge();
            mCurrentStage.start(tickTimer);
            mAlertManager.showNotification(mCurrentStage);
        }
    }

    public void stop()
    {
        if(debateStatus == DebateStatus.speaking)
        {
            if (mCurrentStage != null)
            {
                mAlertManager.hideNotification();
                mCurrentStage.cancel();
                if(mStageIterator.hasNext())
                {
                    debateStatus = DebateStatus.transitioning;
                }
                else
                {
                    debateStatus = DebateStatus.setup;
                }
            }
            else
            {
                debateStatus = DebateStatus.setup;
            }
        }
    }

    public boolean start()
    {
        if((debateStatus == DebateStatus.setup) ||
                (debateStatus == DebateStatus.transitioning))
        {
            if(prepareNextSpeaker())
            {
                startNextSpeaker();
                debateStatus = DebateStatus.speaking;
                return true;
            }
        }
        return false;
    }

    public DebateStatus getDebateStatus() {
        return debateStatus;
    }

    public String getTitleText()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getTitleText();
        }
        return "";
    }

    public long getStageCurrentTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getSeconds();
        }
        else
        {
            return 0;
        }
    }

    public long getStageNextTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getNextTime();
        }
        else
        {
            return 0;
        }
    }

    public long getStageFinalTime()
    {
        if(mCurrentStage != null)
        {
            return mCurrentStage.getFinalTime();
        }
        else
        {
            return 0;
        }
    }
    
    public String getStageStateText()
    {
        if(mCurrentStage != null) {
            return mCurrentStage.getStateText();
        } else {
            return "";
        }
    }

    public void reset()
    {
        // Stop current stage timer, if on
        if(mCurrentStage != null)
        {
            mCurrentStage.cancel();
        }
        mCurrentStage = null;
        tickTimer.purge();
        tickTimer.cancel();
        tickTimer = new Timer();

        ListIterator<AlarmChain> stageIterator = mStages.listIterator();
        while(stageIterator.hasNext())
        {
            AlarmChain stage = stageIterator.next();
            stage.cancel();
            stageIterator.set(stage.newCopy());
        }

        mStageIterator = mStages.iterator();
        debateStatus = DebateStatus.setup;
    }

    public void release()
    {
        mCurrentStage = null;

        tickTimer.cancel();
        tickTimer.purge();
        tickTimer = null;

        mStages = null;
    }
}
