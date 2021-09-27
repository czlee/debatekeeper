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
import java.util.Iterator;


/**
 * <p>ControlledDebatePhaseFormat is an abstract class providing shared functionality for
 * speech and prep time formats that are fully controlled.  Currently, this means all
 * speeches, and prep time for formats like Easters.</p>
 *
 * <p>Known subclasses: {@link SpeechFormat}, {@link PrepTimeControlledFormat}</p>
 *
 * @author Chuan-Zheng Lee
 *
 */
public abstract class ControlledDebatePhaseFormat extends GenericDebatePhaseFormat {

    protected final long          mLength;
    protected PeriodInfo          mFirstPeriodInfo = new PeriodInfo();
    protected ArrayList<BellInfo> mBells = new ArrayList<>();

    public ControlledDebatePhaseFormat(long length) {
        super();
        this.mLength = length;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public long getLength() {
        return mLength;
    }

    @Override
    public PeriodInfo getFirstPeriodInfo() {
        PeriodInfo pi = super.getFirstPeriodInfo();
        pi.update(mFirstPeriodInfo);
        return pi;
    }

    public void setFirstPeriodInfo(PeriodInfo firstPeriodInfo) {
        this.mFirstPeriodInfo = firstPeriodInfo;
    }

    /**
     * Adds a {@link BellInfo} to the speech.
     * This method avoids throwing exceptions.  The caller must be diligent enough not to do
     * anything weird.  If there are two bells at the same time, it replaces the existing one with
     * this one.  If this bell is after the finish time, it doesn't care, it just adds it anyway.
     * @param bi the {@link BellInfo} to add
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

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    @Override
    protected ArrayList<BellInfo> getBells() {
        return mBells;
    }

}