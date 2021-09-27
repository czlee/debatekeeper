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

package net.czlee.debatekeeper.debateformat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;


/**
 * <p>GenericDebatePhaseFormat is a generic abstract implementation of
 * {@link DebatePhaseFormat}.</p>
 *
 * <p>Subclasses must provide <code>getLength()</code>, <code>getBells()</code> and
 * <code>getFirstPeriodInfo()</code>.  GenericDebatePhaseFormat will provide other methods using
 * these three methods.</p>
 *
 * <p>Known direct subclasses: {@link ControlledDebatePhaseFormat}, {@link PrepTimeSimpleFormat}</p>
 *
 * <p>Known indirect subclasses: {@link SpeechFormat}, {@link PrepTimeControlledFormat}</p>
 *
 * @author Chuan-Zheng Lee
 *
 */
abstract class GenericDebatePhaseFormat implements DebatePhaseFormat {

    @Override
    public abstract long getLength();

    /**
     * @return an {@link ArrayList} of {@link BellInfo} objects for this format
     */
    protected abstract ArrayList<BellInfo> getBells();

    @Override
    public PeriodInfo getFirstPeriodInfo() {
        // The description is blank, not null - it needs to remove any previous description
        // that may have been there.
        return new PeriodInfo(null, null, "", null, false);
    }

    @Override
    public BellInfo getBellAtTime(long seconds) {
        Iterator<BellInfo> bellIterator = getBells().iterator();
        BellInfo thisBell;

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
        Iterator<BellInfo> bellIterator = getBells().iterator();
        BellInfo thisBell;
        long latestBellTimeSoFar = 0;

        workingPi.update(getFirstPeriodInfo());

        // We are looking for the *latest* bell that is *before* (or at) the given time,
        // that actually has a descriptor.
        while (bellIterator.hasNext()) {
            // To be useful, this bell must *not* be after the given time. Then, if this bell is
            // the latest bell considered so far, it should replace the existing information. If it
            // is not the latest bell considered, it should be added where the existing information
            // is null, but existing information should not be changed.
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
    @Override
    public ArrayList<BellInfo> getBellsSorted() {

        // A shallow copy is fine, we just want to sort the bells, not edit them.
        ArrayList<BellInfo> bells = new ArrayList<>(getBells());

        Collections.sort(bells, (lhs, rhs) -> {
            long diff = lhs.getBellTime() - rhs.getBellTime();
            return (int) diff;
        });

        return bells;
    }

}
