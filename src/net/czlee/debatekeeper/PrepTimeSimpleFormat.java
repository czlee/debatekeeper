/*
 * Copyright (C) 2012 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.czlee.debatekeeper;


/**
 * PrepTimeSimpleFormat is a passive data class that holds information about a prep format.
 *
 * A prep format normally only defines the length of the prep time.  The rest is configured
 * by the user, and this class figures out the user configurations accordingly.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-21
 *
 */
public class PrepTimeSimpleFormat implements PrepTimeFormat {

    protected final long mPrepLength;

    public PrepTimeSimpleFormat(long prepLength) {
        super();
        this.mPrepLength = prepLength;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getLength()
     */
    @Override
    public long getLength() {
        return mPrepLength;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getCountDirection()
     */
    @Override
    public CountDirection getCountDirection() {
        return CountDirection.COUNT_USER;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getFirstPeriodInfo()
     */
    @Override
    public PeriodInfo getFirstPeriodInfo() {
        return new PeriodInfo("", 0, false);
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getFirstBellFromTime(long)
     */
    @Override
    public BellInfo getFirstBellFromTime(long seconds) {
        // TODO Add other bells from user preferences
        if (seconds < getLength())
            return new BellInfo(getLength(), 2);
        return null;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getBellAtTime(long)
     */
    @Override
    public BellInfo getBellAtTime(long seconds) {
        // TODO Add other bells from user preferences
        if (seconds == getLength())
            return new BellInfo(getLength(), 2);
        return null;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#getPeriodInfoForTime(long)
     */
    @Override
    public PeriodInfo getPeriodInfoForTime(long seconds) {
        if (seconds > getLength())
            return new PeriodInfo("", 0x77ff0000, false);
        return this.getFirstPeriodInfo();
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.PrepTimeFormat#isControlled()
     */
    @Override
    public boolean isControlled() {
        return false;
    }

}
