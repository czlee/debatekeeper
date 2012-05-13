package com.ftechz.DebatingTimer;

/**
 * SpeakerTimer class
 * Exist as a stage in a debate, keeping the timer of the stage
 * and its own internal state according on the AlarmChainAlerts provided
 */
// TODO: This class should probably be eliminated, in favour of a parameterised AlarmChain.
public class PrepTimer extends AlarmChain
{

    public PrepTimer(String name)
    {
        super();
        mName = name;
    }

    public PrepTimer(String name, long finishTime, Event alarmChainAlert[])
    {
        super(finishTime, alarmChainAlert, true);
        mName = name;
    }

    @Override
    protected void handleAlert(Event alert) {
        if (alert.getAlertTime() == this.mFinishTime){
            this.cancel();
        }
        super.handleAlert(alert);
    }

    @Override
    public String getNotificationTickerText() {
        return "Preparation started";
    }

    @Override
    public PrepTimer newCopy()
    {
        return new PrepTimer(mName, mFinishTime, mAlerts.toArray(new Event[mAlerts.size()]));
    }
}
