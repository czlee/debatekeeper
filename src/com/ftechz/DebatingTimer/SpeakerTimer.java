package com.ftechz.DebatingTimer;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 * SpeakerTimer class
 * Exist as a stage in a debate, keeping the timer of the stage
 * and its own internal state according on the AlarmChainAlerts provided
 */
//TODO: This class should probably be eliminated, in favour of a parameterised AlarmChain.
public class SpeakerTimer extends AlarmChain
{

    private TeamsManager mTeamsManager;
    private final TeamsManager.SpeakerSide mSpeakerSide;
    private final int mSpeakerNumber;

    public SpeakerTimer(String name, TeamsManager.SpeakerSide speakerSide,
                        int speakerNumber)
    {
        super();
        mName = name;
        mSpeakerSide = speakerSide;
        mSpeakerNumber = speakerNumber;
    }

    SpeakerTimer(String name, TeamsManager teamsManager,
                    TeamsManager.SpeakerSide speakerSide,
                    int speakerNumber, long finishTime, BellInfo[] alarms)
    {
        super(finishTime, alarms);
        mName = name;
        mTeamsManager = teamsManager;
        mSpeakerSide = speakerSide;
        mSpeakerNumber = speakerNumber;
    }

    void setSpeakersManager(TeamsManager teamsManager)
    {
        mTeamsManager = teamsManager;
    }

    public String getSpeakerName()
    {
        Speaker speaker = mTeamsManager.getSpeaker(mSpeakerSide, mSpeakerNumber);
        if(speaker != null) {
            return speaker.getName();
        }
        else
        {
            return "";
        }
    }

    @Override
    public SpeakerTimer newCopy()
    {
        return new SpeakerTimer(mName, mTeamsManager, mSpeakerSide,
                mSpeakerNumber, mFinishTime, mAlerts.toArray(new BellInfo[mAlerts.size()]));
    }

}
