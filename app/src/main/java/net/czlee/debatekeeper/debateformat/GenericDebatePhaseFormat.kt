/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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
 * GenericDebatePhaseFormat is a generic abstract implementation of [DebatePhaseFormat].
 *
 * Subclasses must provide [length], [bells] and (optionally overriding) [firstPeriodInfo].
 * GenericDebatePhaseFormat will provide other methods using these three.
 *
 * Known direct subclasses: [ControlledDebatePhaseFormat], [PrepTimeSimpleFormat]
 *
 * Known indirect subclasses: [SpeechFormat], [PrepTimeControlledFormat]
 *
 * @author Chuan-Zheng Lee
 */
abstract class GenericDebatePhaseFormat : DebatePhaseFormat {

    /** An [ArrayList] of [BellInfo] objects for this format. */
    protected abstract val bells: ArrayList<BellInfo>

    // The description is blank, not null - it needs to remove any previous description
    // that may have been there.
    override val firstPeriodInfo: PeriodInfo
        get() = PeriodInfo(null, null, "", null, false)

    override val bellsSorted: List<BellInfo>
        get() {
            // A shallow copy is fine, we just want to sort the bells, not edit them.
            val sorted = ArrayList(bells)
            sorted.sortWith { lhs, rhs -> (lhs.bellTime - rhs.bellTime).toInt() }
            return sorted
        }

    override fun getBellAtTime(seconds: Long): BellInfo? {
        return bells.firstOrNull { it.bellTime == seconds }
    }

    override fun getPeriodInfoForTime(seconds: Long): PeriodInfo {
        val workingPi = PeriodInfo()
        var latestBellTimeSoFar: Long = 0

        workingPi.update(firstPeriodInfo)

        // We are looking for the *latest* bell that is *before* (or at) the given time,
        // that actually has a descriptor.
        for (thisBell in bells) {
            // To be useful, this bell must *not* be after the given time. Then, if this bell is
            // the latest bell considered so far, it should replace the existing information. If it
            // is not the latest bell considered, it should be added where the existing information
            // is null, but existing information should not be changed.
            if (thisBell.bellTime > seconds)
                continue
            if (thisBell.bellTime > latestBellTimeSoFar) {
                workingPi.update(thisBell.nextPeriodInfo) // update and replace info
                latestBellTimeSoFar = thisBell.bellTime   // take note of the new latest bell
            } else {
                workingPi.addInfo(thisBell.nextPeriodInfo) // add, but don't replace
            }
        }

        return workingPi
    }
}
