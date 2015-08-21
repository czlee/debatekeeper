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


/**
 * <p>DebatePhaseFormat is an interface for DebatePhaseManager.</p>
 *
 * <p>Known direct implementations: {@link GenericSpeechorPrepFormat}</p>
 *
 * <p>Known direct sub-interfaces: {@link PrepTimeFormat}</p>
 *
 * <p>Known indirect implementation: {@link ControlledDebatePhaseFormat}, {@link PrepTimeSimpleFormat},
 * {@link PrepTimeControlledFormat}, {@link SpeechFormat}</p>
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-21
 */

public interface DebatePhaseFormat {

    /**
     * Returns the length of the speech or prep format in seconds.
     * @return the length in seconds
     */
    public abstract long getLength();

    /**
     * Returns the first {@link PeriodInfo} of the speech or prep format.
     * @return the PeriodInfo object
     */
    public abstract PeriodInfo getFirstPeriodInfo();

    /**
     * Returns the bell for the specified time, or <code>null</code> if there is no such bell.
     * @param seconds the time in seconds
     * @return the {@link BellInfo} object representing that bell
     */
    public abstract BellInfo getBellAtTime(long seconds);

    /**
     * Returns the {@link PeriodInfo} appropriate for the given time.
     * @param seconds the time in seconds
     * @return the PeriodInfo object
     */
    public abstract PeriodInfo getPeriodInfoForTime(long seconds);

    /**
     * @return an {@link ArrayList} of {@link BellInfo} objects that are sorted by time.
     */
    public abstract ArrayList<BellInfo> getBellsSorted();

    /**
     * @return <code>true</code> if it is a prep time, <code>false</code> otherwise
     */
    public abstract boolean isPrep();

}