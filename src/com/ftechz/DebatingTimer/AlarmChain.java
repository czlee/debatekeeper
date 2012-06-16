package com.ftechz.DebatingTimer;

import static java.util.Collections.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.util.Log;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 * AlarmChain class
 * This class manages a single contiguous ordered series of alerts,
 * for example, a single speaker in a debate or a single period of a match.
 * It keeps the time and calls alerts at associated alert times
 * When last alert has been reached, it continues to check the last alert for match
 */
public abstract class AlarmChain extends TimerTask {

    public class AlarmChainAlertCompare implements Comparator<BellInfo> {
        @Override
        public int compare(BellInfo alert1, BellInfo alert2) {
            return (int) (alert1.getBellTime() - alert2.getBellTime());
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
    protected ArrayList<BellInfo> mAlerts;

    protected String mName;

    private int mAlertNumber;
    private AlertManager mAlertManager = null;
    private final AlarmChainAlertCompare mAlertComparator = new AlarmChainAlertCompare();
    protected boolean mCountdown = false;
    protected long mFinishTime = 0;
    protected PeriodInfo mInitialPeriodInfo = new PeriodInfo("Initial", null);

    // mCurrentPeriodInfo is a working copy of the current period information (PeriodInfo).
    // It should NOT be initialised to have any null members, as this risks invoking a
    // NullPointerException!
    // Note: NEVER assign directly to mCurrentPeriodInfo unless you are absolutely certain
    // that none of its members are null.  Use mCurrentPeriodInfo.update(pi) instead.
    protected PeriodInfo mCurrentPeriodInfo = new PeriodInfo("Not started", 0);

    protected RunningState mRunningState = RunningState.BeforeStart;

    //
    // Methods
    public AlarmChain() {
        super();
        init();
    }

    public AlarmChain(long finishTime, BellInfo[] alerts) {
        super();
        init(alerts);
        setFinishTime(finishTime);
    }

    public AlarmChain(long finishTime, BellInfo[] alerts, boolean countdown) {
        super();
        init(alerts);
        setFinishTime(finishTime);
        mCountdown = countdown;
    }

    /**
     * @param alertManager the AlertManager to set
     */
    public void setAlertManager(AlertManager alertManager) {
        this.mAlertManager = alertManager;
    }

    public void setFinishTime(long finishTime) { mFinishTime = finishTime; }

    public void setInitialPeriodInfo(PeriodInfo periodInfo) { mInitialPeriodInfo = periodInfo; }

    public void setInitialPeriodInfo(String description, Integer backgroundColor) {
        mInitialPeriodInfo = new PeriodInfo(description, backgroundColor);
    }

    public PeriodInfo getInitialPeriodInfo() { return mInitialPeriodInfo; }

    private void init() {
        mAlerts = new ArrayList<BellInfo>();
        mAlertNumber = 0;
        mSecondCounter = 0;
    }

    private void init(BellInfo[] alerts) {
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
            if (mSecondCounter == mAlerts.get(mAlertNumber).getBellTime()) {
                do {
                    handleAlert(mAlerts.get(mAlertNumber));
                    if (mAlertNumber < mAlerts.size() - 1) {
                        mAlertNumber++;
                    } else {
                        break;
                    }
                } while (mSecondCounter == mAlerts.get(mAlertNumber).getBellTime()); // Handle multiple with the same time
            } else if (mSecondCounter > mAlerts.get(mAlertNumber).getBellTime()) {
                if (mAlertNumber < mAlerts.size() - 1) {
                    mAlertNumber++;
                }
            }
        }
    }

    public void addTime(BellInfo alert) {
        mAlerts.add(alert);
        sort(mAlerts, mAlertComparator);
    }

    public void addTimes(BellInfo[] alerts) {
        if (alerts != null) {
            for (BellInfo alert : alerts) {
                mAlerts.add(alert);
            }
        }

        sort(mAlerts, mAlertComparator);
    }

    public int getCurrentBackgroundColor() {
        return mCurrentPeriodInfo.getBackgroundColor();
    }

    public long getSecondsForDisplay() {
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

    public long getNextTimeForDisplay() {
        if (mAlertNumber < mAlerts.size()) {
            if (mCountdown) {
                return getFinishTime() - mAlerts.get(mAlertNumber).getBellTime();
            } else {
                return mAlerts.get(mAlertNumber).getBellTime();
            }
        } else {
            return 0;
        }
    }

    public long getFinishTimeForDisplay() {
        return getFinishTime();
    }

    protected void handleAlert(BellInfo alert){
        mCurrentPeriodInfo.update(alert.getNextPeriodInfo());
        if (alert.isPauseOnBell()) {
            this.pause();
        }
        mAlertManager.triggerAlert(alert, mCurrentPeriodInfo);
    }

    public String getStateText(){
        return mCurrentPeriodInfo.getDescription();
    }

    // Resets the timer to zero
    public void resetState() {
        mRunningState = RunningState.BeforeStart;
        mSecondCounter = 0;
        mAlertNumber = 0;
        mCurrentPeriodInfo = new PeriodInfo("Not started", 0);
        mCurrentPeriodInfo.update(mInitialPeriodInfo);
    }

    public String getNotificationText(){
        return String.format("%s: %s", getName(), getStateText());
    }

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

    protected void onStart(){
        if (mSecondCounter == 0)
            mCurrentPeriodInfo.update(mInitialPeriodInfo);
    }

    protected long getFinishTime() {
        if (mFinishTime > 0) {
            return mFinishTime;
        } else if (mAlerts.size() > 0) {
            return mAlerts.get(mAlerts.size() - 1).getBellTime();
        } else {
            return 0;
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

    private int getRunningStateAsInt() {
        switch (mRunningState) {
        case BeforeStart:
            return 0;
        case Running:
            return 1;
        case StoppedByAlarm:
            return 2;
        case StoppedByUser:
            return 3;
        default:
            return 0;
        }
    }

    // Must be kept consistent with getRunningStateAsInt().
    private void restoreRunningState(int state) {
        switch (state) {
        case 2:
            mRunningState = RunningState.StoppedByAlarm;
            break;
        case 1: // Don't restore as "running"; restore as "stopped by user".
        case 3:
            mRunningState = RunningState.StoppedByUser;
            break;
        default:
        case 0:
            mRunningState = RunningState.BeforeStart;
            break;
        }
    }

    public void saveState(String key, Bundle bundle) {
        bundle.putLong(key + ".currentTime", mSecondCounter);
        bundle.putInt(key + ".timerState", getRunningStateAsInt());
        bundle.putInt(key + ".alertNumber", mAlertNumber);
        mCurrentPeriodInfo.saveState(key + ".currentPeriodInfo", bundle);
    }

    public void restoreState(String key, Bundle bundle) {
        mSecondCounter = bundle.getLong(key + ".currentTime", 0);
        mAlertNumber = bundle.getInt(key + ".alertNumber", 0);
        restoreRunningState(bundle.getInt(key + ".timerState", 0));
        mCurrentPeriodInfo.restoreState(key + ".currentPeriodInfo", bundle);
    }

    public String getName() {
        return mName;
    }

    // This runs all the time, even when the timer is not actually incrementing.
    // Should find a neater way to implement this timer so it doesn't run when it doesn't need to.
    // The best way is probably to make AlarmChain exist only when needed, see the to-do at the top
    // of this file.
    public void setTimer(Timer timer) {
        if (mIsScheduled == false) {
            onStart();
            String hashCodesString;
            hashCodesString = String.format("Hash codes: timer is %x, task is %x", timer.hashCode(), this.hashCode());
            Log.i(this.getClass().getSimpleName(), hashCodesString);
            timer.scheduleAtFixedRate(this, 1000, 1000);
            mIsScheduled = true;
        }
    }
}