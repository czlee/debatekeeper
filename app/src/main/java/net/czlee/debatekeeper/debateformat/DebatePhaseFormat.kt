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

package net.czlee.debatekeeper.debateformat

/**
 * DebatePhaseFormat is an interface for DebatePhaseManager.
 *
 * Known direct implementations: [GenericDebatePhaseFormat]
 *
 * Known sub-interfaces: [PrepTimeFormat]
 *
 * @author Chuan-Zheng Lee
 */
interface DebatePhaseFormat {

    /** The length of the speech or prep format in seconds. */
    val length: Long

    /** The first [PeriodInfo] of the speech or prep format. */
    val firstPeriodInfo: PeriodInfo

    /** `true` if this is a prep time, `false` otherwise. */
    val isPrep: Boolean

    /** A list of [BellInfo] objects, sorted by time. */
    val bellsSorted: List<BellInfo>

    /**
     * Returns the bell for the specified time, or `null` if there is no such bell.
     * @param seconds the time in seconds
     */
    fun getBellAtTime(seconds: Long): BellInfo?

    /**
     * Returns the [PeriodInfo] appropriate for the given time.
     * @param seconds the time in seconds
     */
    fun getPeriodInfoForTime(seconds: Long): PeriodInfo
}
