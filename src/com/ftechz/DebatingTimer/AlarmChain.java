package com.ftechz.DebatingTimer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

import static java.util.Collections.sort;

/**
 * AlarmChain class
 * This class manages a single contiguous ordered series of alerts,
 * for example, a single speaker in a debate or a single period of a match.
 * It keeps the time and calls alerts at associated alert times
 * When last alert has been reached, it continues to check the last alert for match
 */
// TODO: Make a new passive info class AlarmChainInfo, so that AlarmChain is instantitated
// from an AlarmChainInfo and is instantiated only when it is needed.
// REASON: TimerTask can't be scheduled more than once, which wreaks havoc if we need to
// restart a speaker or go back to a previous speaker.
public abstract class AlarmChain extends TimerTask {
    //
    // Classes
    public static abstract class AlarmChainAlert {
        public long time;
        protected AlertManager mAlertManager;

        public AlarmChainAlert(long seconds) {
            time = seconds;
        }

        public AlarmChainAlert(long seconds, AlertManager alertManager) {
            time = seconds;
            mAlertManager = alertManager;
        }

        public void setAlertManager(AlertManager alertManager) {
            mAlertManager = alertManager;
        }

        public abstract void alert();

        public void reset() {

        }
    }

    public static class IntermediateAlert extends AlarmChain.AlarmChainAlert {
        public IntermediateAlert(long seconds) {
            super(seconds);
        }

        @Override
        public void alert() {
            Log.i(this.getClass().getSimpleName(), "Intermediate Alert.");
        }
    }

    public static class WarningAlert extends AlarmChain.AlarmChainAlert {
        public WarningAlert(long seconds, AlertManager alertManager) {
            super(seconds, alertManager);
        }

        public WarningAlert(long seconds) {
            super(seconds);
        }

        @Override
        public void alert() {
            Log.i(this.getClass().getSimpleName(), "Warning Alert.");
            mAlertManager.triggerAlert(R.raw.beep1);
        }
    }

    public static class FinishAlert extends AlarmChain.AlarmChainAlert {
        public FinishAlert(long seconds, AlertManager alertManager) {
            super(seconds, alertManager);
        }

        public FinishAlert(long seconds) {
            super(seconds);
        }

        @Override
        public void alert() {
            Log.i(this.getClass().getSimpleName(), "Finish.");
            // Do an do-do alert
            mAlertManager.triggerAlert(R.raw.beep2);

        }
    }

    public static class OvertimeAlert extends AlarmChain.AlarmChainAlert {
        private long mRepeatPeriod = 0;
        private long initTime;

        public OvertimeAlert(long seconds, long repeatPeriod, AlertManager alertManager) {
            super(seconds, alertManager);
            initTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        public OvertimeAlert(long seconds, long repeatPeriod) {
            super(seconds);
            initTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        @Override
        public void alert() {
            time += mRepeatPeriod;
            Log.i(this.getClass().getSimpleName(), "OVERTIME!");
            // Do an do-do-do alert
            mAlertManager.triggerAlert(R.raw.beep3);

        }

        @Override
        public void reset() {
            time = initTime;
        }
    }

    public class AlarmChainAlertCompare implements Comparator<AlarmChainAlert> {
        @Override
        public int compare(AlarmChainAlert alert1, AlarmChainAlert alert2) {
            return (int) (alert1.time - alert2.time);
        }
    }

    public enum RunningState {
        BeforeStart,
        Running,
        StoppedByUser,    // "StoppedByUser" means it was stopped by the user
        StoppedByAlarm    // "StoppedByAlarm" means it was stopped by an alarm
    }

    //
    // Members
    private boolean mIsScheduled = false;
    private long mSecondCounter;
    protected ArrayList<AlarmChainAlert> mAlerts;

    protected String mName;

    private int mAlertNumber;
    private AlarmChainAlertCompare mAlertComparator = new AlarmChainAlertCompare();
    protected boolean mCountdown = false;
    protected long mFinishTime = 0;

    protected RunningState mRunningState = RunningState.BeforeStart;

    //
    // Methods
    public AlarmChain() {
        super();
        init();
    }

    public AlarmChain(AlarmChainAlert[] alerts) {
        super();
        init(alerts);
    }

    public AlarmChain(AlarmChainAlert[] alerts, boolean countdown) {
        super();
        init(alerts);
        mCountdown = countdown;
    }

    private void init() {
        mAlerts = new ArrayList<AlarmChainAlert>();
        mAlertNumber = 0;
        mSecondCounter = 0;
    }

    private void init(AlarmChainAlert[] alerts) {
        init();
        addTimes(alerts);
    }

    // Assumed to execute every second
    // Increments counter and checks for alert times
    @Override
    public void run() {
        if (mRunningState != RunningState.Running) {
            return;
        }

        mSecondCounter++;

        if (mAlertNumber < mAlerts.size()) {
            if (mSecondCounter == mAlerts.get(mAlertNumber).time) {
                do {
                    handleAlert(mAlerts.get(mAlertNumber));
                    if (mAlertNumber < mAlerts.size() - 1) {
                        mAlertNumber++;
                    } else {
                        break;
                    }
                } while (mSecondCounter == mAlerts.get(mAlertNumber).time); // Handle multiple with the same time
            } else if (mSecondCounter > mAlerts.get(mAlertNumber).time) {
                if (mAlertNumber < mAlerts.size() - 1) {
                    mAlertNumber++;
                }
            }
        }
    }

    public void addTime(AlarmChainAlert alert) {
        mAlerts.add(alert);
        if (alert.getClass() == FinishAlert.class) {
            mFinishTime = alert.time;
        }

        sort(mAlerts, mAlertComparator);
    }

    public void addTimes(AlarmChainAlert[] alerts) {
        if (alerts != null) {
            for (AlarmChainAlert alert : alerts) {
                if (alert.getClass() == FinishAlert.class) {
                    mFinishTime = alert.time;
                }
                mAlerts.add(alert);
            }
        }

        sort(mAlerts, mAlertComparator);
    }

    public long getSeconds() {
        if (mCountdown) {
            long time = getFinishTime() - mSecondCounter;
            if (time > 0) {
                return time;
            } else {
                return 0;
            }
        } else {
            return mSecondCounter;
        }
    }

    public long getNextTime() {
        if (mAlertNumber < mAlerts.size()) {
            if (mCountdown) {
                return getFinishTime() - mAlerts.get(mAlertNumber).time;
            } else {
                return mAlerts.get(mAlertNumber).time;
            }
        } else {
            return 0;
        }
    }

    public long getFinalTime() {
        if (mAlerts.size() > 0) {
            if (mCountdown) {
                return 0;
            } else {
                return getFinishTime();
            }
        } else {
            return 0;
        }
    }

    protected abstract void handleAlert(AlarmChainAlert alert);

    public abstract String getStateText();

    // Resets the timer to zero
    public void resetState() {
        mRunningState = RunningState.BeforeStart;
        mSecondCounter = 0;
        mAlertNumber = 0;
        for (AlarmChainAlert alert : mAlerts) {
            alert.reset();
        }
    }

    public abstract String getNotificationText();

    public abstract String getNotificationTickerText();

    // Required for rescheduling...
    public abstract AlarmChain newCopy();
    
    public void start() {
        mRunningState = RunningState.Running;
    }

    public void pause() {
        mRunningState = RunningState.StoppedByAlarm;
    }

    public void stop() {
        mRunningState = RunningState.StoppedByUser;
    }
    
    public void resume() {
        mRunningState = RunningState.Running;
    }

    protected abstract void onStart();

    public abstract String getTitleText();

    private long getFinishTime() {
        if (mFinishTime > 0) {
            return mFinishTime;
        } else {
            return mAlerts.get(mAlerts.size() - 1).time;
        }
    }

    @Override
    public boolean cancel() {
        mRunningState = RunningState.StoppedByUser;
        return super.cancel();
    }

    public RunningState getRunningState() {
        return mRunningState;
    }

    public String getName() {
        return mName;
    }
    
    // TODO: This runs all the time, even when the timer is not actually incrementing.
    // Should find a neater way to implement this timer so it doesn't run when it doesn't need to.
    // The best way is probably to make AlarmChain exist only when needed, see the to-do at the top
    // of this file.
    public void setTimer(Timer timer) {
        if (mIsScheduled == false) {
            onStart();
            // TODO change this to scheduleAtFixedRate
            String hashCodesString;
            hashCodesString = String.format("Hash codes: timer is %x, task is %x", timer.hashCode(), this.hashCode());
            Log.i(this.getClass().getSimpleName(), hashCodesString);
            timer.schedule(this, 1000 , 1000);
            mIsScheduled = true;
        }
    }
}