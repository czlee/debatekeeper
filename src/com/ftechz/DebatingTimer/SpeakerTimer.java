package com.ftechz.DebatingTimer;

/**
 * SpeakerTimer class
 * Exist as a stage in a debate, keeping the timer of the stage
 * and its own internal state according on the AlarmChainAlerts provided
 */
public class SpeakerTimer extends AlarmChain
{
    enum SpeakerState {
        setup,
        normal,
        warning,
        overtime
    }

    private TeamsManager mTeamsManager;
    private TeamsManager.SpeakerSide mSpeakerSide;
    private int mSpeakerNumber;

    private SpeakerState mSpeakerState = SpeakerState.setup;

    public SpeakerTimer(TeamsManager.SpeakerSide speakerSide,
                        int speakerNumber)
    {
        super();
        mSpeakerSide = speakerSide;
        mSpeakerNumber = speakerNumber;
    }

    SpeakerTimer(TeamsManager teamsManager,
                    TeamsManager.SpeakerSide speakerSide,
                    int speakerNumber, AlarmChainAlert[] alarms)
    {
        super();
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
    protected void handleAlert(AlarmChainAlert alert) {
        Class alertClass = alert.getClass();

        if(alertClass == IntermediateAlert.class)
        {
            // Do nothing
        }
        else if(alertClass == WarningAlert.class)
        {
            mSpeakerState = SpeakerState.warning;
        }
        else if(alertClass == FinishAlert.class)
        {
            mSpeakerState = SpeakerState.overtime;
        }
        else if(alertClass == OvertimeAlert.class)
        {
            // Do nothing
        }

        alert.alert();
    }

    @Override
    public String getNotificationText() {
        return String.format("%s: %s", getSpeakerName(), getStateText());
    }

    @Override
    public String getNotificationTickerText() {
        return "Speaker started";
    }

    public String getStateText()
    {
        String text = "";
        switch(mSpeakerState)
        {
            case setup:
                text = "Setup";
                break;
            case normal:
                text = "Normal";
                break;
            case warning:
                text = "Warning";
                break;
            case overtime:
                text = "Overtime";
                break;
            default:
                break;
        }
        return text;
    }

    @Override
    protected void onStart() {
        mSpeakerState = SpeakerState.normal;
    }

    @Override
    public SpeakerTimer newCopy()
    {
        return new SpeakerTimer(mTeamsManager, mSpeakerSide,
                mSpeakerNumber, mAlerts.toArray(new AlarmChainAlert[mAlerts.size()]));
    }

    @Override
    public String getTitleText() {
        return getSpeakerName();
    }
}
