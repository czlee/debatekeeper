package com.ftechz.DebatingTimer;

import static java.util.Collections.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

/**
 * AlarmChain class
 * This class manages a single contiguous ordered series of alerts,
 * for example, a single speaker in a debate or a single period of a match.
 * It keeps the time and calls alerts at associated alert times
 * When last alert has been reached, it continues to check the last alert for match
 */
// TODO: Make a new passive info class AlarmChainInfo, so that AlarmChain is instantiated
// from an AlarmChainInfo and is instantiated only when it is needed.
// REASON: TimerTask can't be scheduled more than once, which wreaks havoc if we need to
// restart a speaker or go back to a previous speaker.
// TODO: Currently, overtime alarms don't continue if the timer is resumed after a stop-by-
// user, because the TimerTask was stopped.  This needs to be fixed.
public abstract class AlarmChain extends TimerTask {
    //
    // Classes
    /**
     * PeriodInfo is a passive data class that holds information about a period *between* Events.
     * This information is drawn to the GUI straight *after* the event is triggered.
     */
    public static class PeriodInfo {

        // The meaning of "null" in both these objects is "do not change from what it is currently".
        protected String  mDescription      = null;
        protected Integer mBackgroundColor  = null; // Use Integer so that we can also use null.

        public PeriodInfo(String description, Integer backgroundColor) {
            mDescription     = description;
            mBackgroundColor = backgroundColor;
        }

        public String  getDescription()     { return mDescription;     }
        public Integer getBackgroundColor() { return mBackgroundColor; }

        /*
         * Updates the object using the information in another PeriodInfo.
         * It replaces members if they are not null, and leaves them as they are if they are null.
         */
        public void update(PeriodInfo pi) {
            if (pi.mDescription != null)     mDescription     = pi.mDescription;
            if (pi.mBackgroundColor != null) mBackgroundColor = pi.mBackgroundColor;
        }
    }

    /**
     * Event is a mostly-passive data structure that contains information about an event.
     * It has one non-data method, alert(), which triggers the alert function in its AlertManager.
     */
    public static class Event {

        /**
         * BellInfo is a passive data class containing information about a bell.
         */
        public static class BellInfo {
            protected int  mSoundResid  = R.raw.desk_bell; // default sound
            protected int  mTimesToPlay = 1;               // default times to play
            protected long mRepeatPeriod = 300;

            public BellInfo() {}

            public BellInfo(int soundResid, int timesToPlay) {
                mSoundResid  = soundResid;
                mTimesToPlay = timesToPlay;
            }

            public BellInfo(int soundResid, int timesToPlay, long repeatPeriod) {
                mSoundResid   = soundResid;
                mTimesToPlay  = timesToPlay;
                mRepeatPeriod = repeatPeriod;
            }

            public void setSoundResid(int soundResid)     { mSoundResid   = soundResid;   }
            public void setTimesToPlay(int timesToPlay)   { mTimesToPlay  = timesToPlay;  }
            public void setRepeatPeriod(int repeatPeriod) { mRepeatPeriod = repeatPeriod; }
            public int  getSoundResid()   { return mSoundResid;   }
            public int  getTimesToPlay()  { return mTimesToPlay;  }
            public long getRepeatPeriod() { return mRepeatPeriod; }

            public boolean isPlayable() {
                return mSoundResid != 0 && mTimesToPlay != 0;
            }
        }

        protected long         mAlertTime    = 0;
        protected boolean      mPauseOnEvent = false;
        protected AlertManager mAlertManager = null;
        protected PeriodInfo   mPeriodInfo   = new PeriodInfo(null, null);
        protected BellInfo     mBellInfo     = new BellInfo();

        public Event(long seconds) {
            setAlertTime(seconds);
        }

        public Event(long seconds, String periodDescription) {
            setAlertTime(seconds);
            setPeriodInfo(periodDescription, null);
        }

        public Event(long seconds, String periodDescription, Integer backgroundColor) {
            setAlertTime(seconds);
            setPeriodInfo(periodDescription, backgroundColor);
        }

        public Event(long seconds, int timesToPlay) {
            setAlertTime(seconds);
            mBellInfo.setTimesToPlay(timesToPlay);
        }

        public Event(long seconds, int timesToPlay, String periodDescription) {
            setAlertTime(seconds);
            mBellInfo.setTimesToPlay(timesToPlay);
            setPeriodInfo(periodDescription, null);
        }

        public Event(long seconds, int timesToPlay, String periodDescription, Integer backgroundColor) {
            setAlertTime(seconds);
            mBellInfo.setTimesToPlay(timesToPlay);
            setPeriodInfo(periodDescription, backgroundColor);
        }

        public Event(long seconds, AlertManager alertManager, String periodDescription) {
            setAlertTime(seconds);
            setAlertManager(alertManager);
            setPeriodInfo(periodDescription, null);
        }

        public Event(long seconds, AlertManager alertManager, String periodDescription, Integer backgroundColor) {
            setAlertTime(seconds);
            setAlertManager(alertManager);
            setPeriodInfo(periodDescription, backgroundColor);
        }

        public Event(long seconds, AlertManager alertManager, int soundResid, int timesToPlay, String periodDescription, Integer backgroundColor) {
            setAlertTime(seconds);
            setAlertManager(alertManager);
            setSound(soundResid, timesToPlay);
            setPeriodInfo(periodDescription, backgroundColor);
        }

        public void setAlertTime(long seconds)                 { mAlertTime    = seconds;      }
        public void setAlertManager(AlertManager alertManager) { mAlertManager = alertManager; }
        public void setPauseOnEvent(boolean pauseOnEvent)      { mPauseOnEvent = pauseOnEvent; }

        public void setSound(int soundResid, int timesToPlay) {
            mBellInfo.setSoundResid(soundResid);
            mBellInfo.setTimesToPlay(timesToPlay);
        }

        public void setPeriodInfo(String description, Integer backgroundColor) {
            mPeriodInfo = new PeriodInfo(description, backgroundColor);
        }

        public long       getAlertTime()   { return mAlertTime;    }
        public PeriodInfo getPeriodInfo()  { return mPeriodInfo;   }
        public BellInfo   getBellInfo()    { return mBellInfo;     }
        public boolean    isPauseOnEvent() { return mPauseOnEvent; }

        public void alert() {
            Log.i(this.getClass().getSimpleName(), String.format("Alert at %d seconds", mAlertTime));
            if (mBellInfo.isPlayable()) {
                mAlertManager.triggerAlert(this);
            }
        }

        public void reset() {}
    }

    // This class extends AlarmChainAlert to trigger again every x seconds after the first alert time
    public static class RepeatedEvent extends AlarmChain.Event {
        private long mRepeatPeriod = 0;
        private final long mInitTime;

        public RepeatedEvent(long seconds, long repeatPeriod, AlertManager alertManager, String periodDescription) {
            super(seconds, alertManager, periodDescription);
            mInitTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        public RepeatedEvent(long seconds, long repeatPeriod, AlertManager alertManager, String periodDescription, Integer backgroundColor) {
            super(seconds, alertManager, periodDescription, backgroundColor);
            mInitTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        public RepeatedEvent(long seconds, long repeatPeriod, int timesToPlay) {
            super(seconds, timesToPlay);
            mInitTime = seconds;
            mRepeatPeriod = repeatPeriod;
        }

        @Override
        public void alert() {
            super.alert();
            mAlertTime += mRepeatPeriod;
        }

        @Override
        public void reset() {
            mAlertTime = mInitTime;
        }
    }

    public class AlarmChainAlertCompare implements Comparator<Event> {
        @Override
        public int compare(Event alert1, Event alert2) {
            return (int) (alert1.getAlertTime() - alert2.getAlertTime());
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
    protected ArrayList<Event> mAlerts;

    protected String mName;

    private int mAlertNumber;
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

    public AlarmChain(long finishTime, Event[] alerts) {
        super();
        init(alerts);
        setFinishTime(finishTime);
    }

    public AlarmChain(long finishTime, Event[] alerts, boolean countdown) {
        super();
        init(alerts);
        setFinishTime(finishTime);
        mCountdown = countdown;
    }

    public void setFinishTime(long finishTime) { mFinishTime = finishTime; }

    public void setInitialPeriodInfo(PeriodInfo periodInfo) { mInitialPeriodInfo = periodInfo; }

    public void setInitialPeriodInfo(String description, Integer backgroundColor) {
        mInitialPeriodInfo = new PeriodInfo(description, backgroundColor);
    }

    public PeriodInfo getInitialPeriodInfo() { return mInitialPeriodInfo; }

    private void init() {
        mAlerts = new ArrayList<Event>();
        mAlertNumber = 0;
        mSecondCounter = 0;
    }

    private void init(Event[] alerts) {
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
            if (mSecondCounter == mAlerts.get(mAlertNumber).getAlertTime()) {
                do {
                    handleAlert(mAlerts.get(mAlertNumber));
                    if (mAlertNumber < mAlerts.size() - 1) {
                        mAlertNumber++;
                    } else {
                        break;
                    }
                } while (mSecondCounter == mAlerts.get(mAlertNumber).getAlertTime()); // Handle multiple with the same time
            } else if (mSecondCounter > mAlerts.get(mAlertNumber).getAlertTime()) {
                if (mAlertNumber < mAlerts.size() - 1) {
                    mAlertNumber++;
                }
            }
        }
    }

    public void addTime(Event alert) {
        mAlerts.add(alert);
        sort(mAlerts, mAlertComparator);
    }

    public void addTimes(Event[] alerts) {
        if (alerts != null) {
            for (Event alert : alerts) {
                mAlerts.add(alert);
            }
        }

        sort(mAlerts, mAlertComparator);
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
                return getFinishTime() - mAlerts.get(mAlertNumber).getAlertTime();
            } else {
                return mAlerts.get(mAlertNumber).getAlertTime();
            }
        } else {
            return 0;
        }
    }

    public long getFinalTimeForDisplay() {
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

    protected void handleAlert(Event alert){
        mCurrentPeriodInfo.update(alert.getPeriodInfo());
        if (alert.isPauseOnEvent()) {
            this.pause();
        }
        alert.alert();
    }

    public String getStateText(){
        return mCurrentPeriodInfo.getDescription();
    }

    // Resets the timer to zero
    public void resetState() {
        mRunningState = RunningState.BeforeStart;
        mSecondCounter = 0;
        mAlertNumber = 0;
        for (Event alert : mAlerts) {
            alert.reset();
        }
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
        mCurrentPeriodInfo.update(mInitialPeriodInfo);
    }

    private long getFinishTime() {
        if (mFinishTime > 0) {
            return mFinishTime;
        } else {
            return mAlerts.get(mAlerts.size() - 1).getAlertTime();
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
            String hashCodesString;
            hashCodesString = String.format("Hash codes: timer is %x, task is %x", timer.hashCode(), this.hashCode());
            Log.i(this.getClass().getSimpleName(), hashCodesString);
            timer.scheduleAtFixedRate(this, 1000, 1000);
            mIsScheduled = true;
        }
    }
}