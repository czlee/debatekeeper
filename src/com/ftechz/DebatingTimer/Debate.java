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
    private AlertManager mNotificationControl;
    //
    // Methods
    public Debate(AlertManager notificationControl)
    {
        mNotificationControl = notificationControl;
        debateStatus = DebateStatus.setup;
        mStages = new LinkedList<AlarmChain>();
        mStageIterator = mStages.iterator();
        tickTimer = new Timer();
    }

    public void addPrep(final AlarmChain.AlarmChainAlert[] alerts)
    {
        mStages.add(new PrepTimer(alerts));
        mStageIterator = mStages.iterator();
    }

    public void addStage(Speaker speaker, final AlarmChain.AlarmChainAlert[] alerts)
    {
        mStages.add(new SpeakerTimer(speaker, alerts));
        mStageIterator = mStages.iterator();
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
            mNotificationControl.showNotification(mCurrentStage);
        }
    }

    public void stop()
    {
        if(debateStatus == DebateStatus.speaking)
        {
            if (mCurrentStage != null)
            {
                mNotificationControl.hideNotification();
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
