package com.ftechz.DebatingTimer;

import java.util.ArrayList;

/**
 * SpeakerTimer class
 * Exist as a stage in a debate, keeping the timer of the stage
 * and its own internal state according on the AlarmChainAlerts provided
 */
public class SpeakerTimer extends AlarmChain
{
    enum SpeakerState {
        normal,
        warning,
        overtime
    }
    
    private Speaker mSpeaker;
    private SpeakerState mSpeakerState = SpeakerState.normal;

    public SpeakerTimer(Speaker speaker)
    {
        super();
        mSpeaker = speaker;
    }

    public SpeakerTimer(AlarmChainAlert alarmChainAlert[])
    {
        super(alarmChainAlert);
    }

    public SpeakerTimer(Speaker speaker, AlarmChainAlert alarmChainAlert[])
    {
        super(alarmChainAlert);
        mSpeaker = speaker;
    }

    public void setSpeaker(Speaker speaker)
    {
        mSpeaker = speaker;
    }

    public String getSpeakerName()
    {
        return mSpeaker.getName();
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
        return String.format("Speaker: %s", getSpeakerName());
    }

    public String getStateText()
    {
        String text = "";
        switch(mSpeakerState)
        {
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
    public SpeakerTimer newCopy()
    {
        return new SpeakerTimer(mSpeaker, mAlerts.toArray(new AlarmChainAlert[mAlerts.size()]));
    }
}
