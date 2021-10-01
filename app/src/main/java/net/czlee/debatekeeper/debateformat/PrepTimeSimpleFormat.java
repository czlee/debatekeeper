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
package net.czlee.debatekeeper.debateformat;

import java.util.ArrayList;

import net.czlee.debatekeeper.PrepTimeBellsManager;


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
public class PrepTimeSimpleFormat extends GenericDebatePhaseFormat implements PrepTimeFormat {

    protected final long mPrepLength;
    protected PrepTimeBellsManager mBellsManager;

    public PrepTimeSimpleFormat(long prepLength) {
        super();
        this.mPrepLength = prepLength;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public void setBellsManager(PrepTimeBellsManager manager) {
        this.mBellsManager = manager;
    }

    @Override
    public long getLength() {
        return mPrepLength;
    }

    @Override
    public boolean isControlled() {
        return false;
    }

    @Override
    public boolean isPrep() {
        return true;
    }

    //******************************************************************************************
    // Protected and private methods
    //******************************************************************************************

    @Override
    protected ArrayList<BellInfo> getBells() {

        if (mBellsManager == null) {
            ArrayList<BellInfo> bells = new ArrayList<>(1);
            bells.add(getFinishBell());
            return bells;

        } else {
            return mBellsManager.getBellsList(getLength());

        }
    }

    private BellInfo getFinishBell() {
        return new BellInfo(getLength(), 2);
    }

}
