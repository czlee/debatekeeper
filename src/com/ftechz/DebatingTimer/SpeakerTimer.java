package com.ftechz.DebatingTimer;

import android.util.Log;

/**
 * Created by IntelliJ IDEA.
 * User: Phil
 * Date: 3/26/12
 * Time: 7:20 PM
 * To change this template use File | Settings | File Templates.
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
}
