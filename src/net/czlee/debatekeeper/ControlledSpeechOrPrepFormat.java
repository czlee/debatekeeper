/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

import java.util.ArrayList;
import java.util.Iterator;

/**
 * ControlledSpeechOrPrepFormat is an abstract class providing shared functionality for
 * speech and prep time formats that are fully controlled.  Currently, this means all
 * speeches, and prep time for formats like Easters.
 *
 * Known subclasses: {@link SpeechFormat}, {@link PrepTimeControlledFormat}
 *
 * @author Chuan-Zheng Lee
 *
 */
public abstract class ControlledSpeechOrPrepFormat implements SpeechOrPrepFormat {

    protected final long          mLength;
    protected PeriodInfo          mFirstPeriodInfo = new PeriodInfo();
    protected ArrayList<BellInfo> mBells = new ArrayList<BellInfo>();

    public ControlledSpeechOrPrepFormat(long length) {
        super();
        this.mLength = length;
    }

    @Override
    public long getLength() {
        return mLength;
    }

    @Override
    public PeriodInfo getFirstPeriodInfo() {
        PeriodInfo pi = new PeriodInfo("", 0, false);
        pi.update(mFirstPeriodInfo);
        return pi;
    }

    public void setFirstPeriodInfo(PeriodInfo firstPeriodInfo) {
        this.mFirstPeriodInfo = firstPeriodInfo;
    }

    /**
     * Adds a BellInfo to the speech.
     * This method avoids throwing exceptions.  The caller must be diligent enough not to do
     * anything weird.  If there are two bells at the same time, it replaces the existing one with
     * this one.  If this bell is after the finish time, it doesn't care, it just adds it anyway.
     * @param bi the BellInfo to add
     */
    public void addBellInfo(BellInfo bi) {

        // If there is already a bell with this time, remove it
        Iterator<BellInfo> biIterator = mBells.iterator();
        BellInfo checkBi;
        while (biIterator.hasNext()) {
            checkBi = biIterator.next();
            if (checkBi.getBellTime() == bi.getBellTime()) {
                biIterator.remove();
            }
        }

        this.mBells.add(bi);
    }

    @Override
    public abstract CountDirection getCountDirection();

    @Override
    public BellInfo getFirstBellFromTime(long seconds) {
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo workingBell = null;
        BellInfo thisBell    = null;

        // We are looking for the *earliest* bell that is *after* the given time.
        while (bellIterator.hasNext()) {
            // To replace the current working bell, two conditions have to be met:
            //  1. This bell is, in fact, after or at the given time.
            //  2. This bell is before the current working bell, if there is one.
            //     (If there isn't one, then condition 2 is deemed met.)
            // We will test the opposite condition, and assign if it survives both.
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() < seconds)
                continue;
            if (workingBell != null) {
                if (workingBell.getBellTime() < thisBell.getBellTime()) {
                    continue;
                }
            }
            // If it survived both negative conditions, assign.
            workingBell = thisBell;
        }

        return workingBell;
    }

    @Override
    public BellInfo getBellAtTime(long seconds) {
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo thisBell    = null;

        while (bellIterator.hasNext()) {
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() == seconds)
                return thisBell;
        }

        return null;
    }

    @Override
    public PeriodInfo getPeriodInfoForTime(long seconds) {
        PeriodInfo workingPi = new PeriodInfo();
        Iterator<BellInfo> bellIterator = mBells.iterator();
        BellInfo thisBell = null;
        long latestBellTimeSoFar = 0;

        workingPi.update(getFirstPeriodInfo());

        // We are looking for the *latest* bell that is *before* (or at) the given time,
        // that actually has a descriptor.
        while (bellIterator.hasNext()) {
            // To be useful, this bell must *not* be after the given time.
            // Then, if this bell is the latest bell considered so far, it should replace the
            // existing information.  If it is not the latest bell considered, it should be added
            // where the existing information is null, but existing information should not be
            // changed.
            thisBell = bellIterator.next();
            if (thisBell.getBellTime() > seconds)
                continue;
            if (thisBell.getBellTime() > latestBellTimeSoFar) {
                workingPi.update(thisBell.getNextPeriodInfo()); // update and replace info
                latestBellTimeSoFar = thisBell.getBellTime();   // take note of the new latest bell
            } else {
                workingPi.addInfo(thisBell.getNextPeriodInfo()); // add, but don't replace
            }
        }

        return workingPi;
    }

}