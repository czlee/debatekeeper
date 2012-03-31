package com.ftechz.DebatingTimer;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TimerTask;

import static java.util.Collections.sort;

/**
 * AlarmChain class
 * Keeps the time and calls alerts at associated alert times
 * When last alert has been reached, it continues to check the last alert for match
 */
public abstract class AlarmChain extends TimerTask
{
    //
    // Classes
    public static abstract class AlarmChainAlert
    {
        public long time;
        public AlarmChainAlert(long seconds)
        {
            time = seconds;
        }
        public abstract void alert();

        public void reset()
        {

        }
    }

    public static class IntermediateAlert extends AlarmChain.AlarmChainAlert {
        public IntermediateAlert(long seconds)
        {
            super(seconds);
        }

        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Intermediate Alert.");
        }
    }

    public static class WarningAlert extends AlarmChain.AlarmChainAlert {
        private AlertManager mAudioManager;

        public WarningAlert(long seconds, AlertManager audioManager)
        {
            super(seconds);
            mAudioManager = audioManager;
        }

        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Warning Alert.");
            mAudioManager.playAlert(R.raw.beep1);
        }
    }

    public static class FinishAlert extends AlarmChain.AlarmChainAlert {
        private AlertManager mAudioManager;

        public FinishAlert(long seconds, AlertManager audioManager)
        {
            super(seconds);
            mAudioManager = audioManager;
        }

        @Override
        public void alert()
        {
            Log.i(this.getClass().getSimpleName(), "Finish.");
            // Do an do-do alert
            mAudioManager.playAlert(R.raw.beep2);

        }
    }

    public static class OvertimeAlert extends AlarmChain.AlarmChainAlert {
        private AlertManager mAudioManager;

        private long mRepeatPeriod = 0;
        private long initTime;

        public OvertimeAlert(long seconds, long repeatPeriod, AlertManager audioManager)
        {
            super(seconds);
            initTime = seconds;
            mRepeatPeriod = repeatPeriod;
            mAudioManager = audioManager;
        }

        @Override
        public void alert()
        {
            time += mRepeatPeriod;
            Log.i(this.getClass().getSimpleName(), "OVERTIME!");
            // Do an do-do-do alert
            mAudioManager.playAlert(R.raw.beep3);

        }

        @Override
        public void reset() {
            time = initTime;
        }
    }

    public class AlarmChainAlertCompare implements Comparator<AlarmChainAlert> {
        @Override
        public int compare(AlarmChainAlert alert1, AlarmChainAlert alert2) {
            return (int)(alert1.time - alert2.time);
        }
    }

    //
    // Members
    private long mSecondCounter;
    private ArrayList<AlarmChainAlert> mAlerts;

    private int state;
    private AlarmChainAlertCompare alertComparator = new AlarmChainAlertCompare();

    //
    // Methods
    private void init() {
        mAlerts = new ArrayList<AlarmChainAlert>();
        state = 0;
        mSecondCounter = 0;
    }

    public AlarmChain() {
        super();
        init();
    }

    public AlarmChain(AlarmChainAlert[] alerts)
    {
        super();
        init();
        for(AlarmChainAlert alert : alerts){
            mAlerts.add(alert);
        }
    }

    // Assumed to execute every second
    // Increments counter and checks for alert times
    @Override
    public void run() {
        mSecondCounter++;
        if(state < mAlerts.size())
        {
            if(mSecondCounter == mAlerts.get(state).time)
            {
                do {
                    handleAlert(mAlerts.get(state));
                    if(state < mAlerts.size() - 1)
                    {
                        state++;
                    }
                    else
                    {
                        break;
                    }
                } while(mSecondCounter == mAlerts.get(state).time); // Handle multiple with the same time
            }
            else if(mSecondCounter > mAlerts.get(state).time)
            {
                if(state < mAlerts.size() - 1)
                {
                    state++;
                }
            }
        }
    }

    public void addTime(AlarmChainAlert alert) {
        mAlerts.add(alert);
        sort(mAlerts, alertComparator);
    }

    public long getSeconds() {
        return mSecondCounter;
    }

    public long getNextTime()
    {
        if(state < mAlerts.size()) {
            return mAlerts.get(state).time;
        } else {
            return 0;
        }
    }

    public long getFinalTime() {
        if(mAlerts.size() > 0) {
            return mAlerts.get(mAlerts.size()-1).time;
        } else {
            return 0;
        }
    }

    protected abstract void handleAlert(AlarmChainAlert alert);

    public abstract String getStateText();
    
    public void resetState()
    {
        state = 0;
        for(AlarmChainAlert alert : mAlerts){
            alert.reset();
        }
    }
}