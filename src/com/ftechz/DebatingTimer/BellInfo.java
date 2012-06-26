package com.ftechz.DebatingTimer;



/**
 * BellInfo is a passive data class that holds information about a single bell.
 *
 * This includes (but is not limited to) when the bell is to be played, what it sounds like and
 * what the name of the following period is.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-05-12
 *
 */
public class BellInfo {

    private long          mBellTime       = 0;
    private boolean       mPauseOnBell    = false;
    private PeriodInfo    mNextPeriodInfo = new PeriodInfo(null, null);
    private final BellSoundInfo mSoundInfo      = new BellSoundInfo();

    public BellInfo(long seconds, int timesToPlay) {
        super();
        mBellTime = seconds;
        mSoundInfo.setTimesToPlay(timesToPlay);
    }

    public void setPauseOnBell(boolean pauseOnBell) {
        mPauseOnBell = pauseOnBell;
    }

    public void setSound(int soundResid) {
        mSoundInfo.setSoundResid(soundResid);
    }

    public void setSound(int soundResid, int timesToPlay) {
        mSoundInfo.setSoundResid(soundResid);
        mSoundInfo.setTimesToPlay(timesToPlay);
    }

    public void setNextPeriodInfo(PeriodInfo pi) {
        mNextPeriodInfo = pi;
    }

    public long getBellTime() {
        return mBellTime;
    }

    public void setBellTime(long seconds) {
        mBellTime = seconds;
    }

    public PeriodInfo getNextPeriodInfo() {
        return mNextPeriodInfo;
    }

    public BellSoundInfo getBellSoundInfo() {
        return mSoundInfo;
    }

    public boolean isPauseOnBell() {
        return mPauseOnBell;
    }

}