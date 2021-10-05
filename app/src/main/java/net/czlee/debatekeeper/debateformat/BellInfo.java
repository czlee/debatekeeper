/*
 * Copyright (C) 2012 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;




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

    private final long mBellTime;
    private boolean mPauseOnBell = false;
    private PeriodInfo mNextPeriodInfo  = new PeriodInfo();
    private final BellSoundInfo mSoundInfo = new BellSoundInfo();

    public BellInfo(long seconds, int numberOfBells) {
        super();
        mBellTime = seconds;
        mSoundInfo.setNumberOfBells(numberOfBells);
    }

    public void setPauseOnBell(boolean pauseOnBell) {
        mPauseOnBell = pauseOnBell;
    }

    public void setNextPeriodInfo(PeriodInfo pi) {
        mNextPeriodInfo = pi;
    }

    public long getBellTime() {
        return mBellTime;
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

    public boolean isSilent() {
        return mSoundInfo.getNumberOfBells() == 0;
    }

}