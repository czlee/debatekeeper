package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Timer;

/**
 * Created by IntelliJ IDEA.
 * User: Phil
 * Date: 3/26/12
 * Time: 9:22 PM
 * To change this template use File | Settings | File Templates.
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

    //
    // Methods
    public Debate()
    {
        debateStatus = DebateStatus.setup;
        mStages = new LinkedList<AlarmChain>();
        mStageIterator = mStages.iterator();
        tickTimer = new Timer();
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
            mCurrentStage.resetState();
            tickTimer.schedule(mCurrentStage, 1000, 1000);
        }
    }

    public void stop()
    {
        if(debateStatus == DebateStatus.speaking)
        {
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

    public String getSpeakerName()
    {
        if(mCurrentStage != null)
        {
            if(mCurrentStage.getClass() == SpeakerTimer.class)
            {
                SpeakerTimer speakerTimer = (SpeakerTimer) mCurrentStage;
                return speakerTimer.getSpeakerName();
            }
        }
        return "";
    }

    public long getCurrentSpeakerCurrentTime()
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

    public long getCurrentSpeakerNextTime()
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

    public long getCurrentSpeakerFinalTime()
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
    
    public String getCurrentStateText()
    {
        if(debateStatus == DebateStatus.speaking)
        {
            return mCurrentStage.getStateText();
        }
        else
        {
            return "Setup";
        }
    }
}
