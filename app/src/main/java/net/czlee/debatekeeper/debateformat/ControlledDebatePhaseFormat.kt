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
 * ControlledDebatePhaseFormat is an abstract class providing shared functionality for
 * speech and prep time formats that are fully controlled.  Currently, this means all
 * speeches, and prep time for formats like Easters.
 *
 * Known subclasses: [SpeechFormat], [PrepTimeControlledFormat]
 *
 * @author Chuan-Zheng Lee
 */
abstract class ControlledDebatePhaseFormat(final override val length: Long) :
        GenericDebatePhaseFormat() {

    private var mFirstPeriodInfo = PeriodInfo()

    override val bells = ArrayList<BellInfo>()

    override var firstPeriodInfo: PeriodInfo
        get() {
            val pi = super.firstPeriodInfo
            pi.update(mFirstPeriodInfo)
            return pi
        }
        set(value) {
            mFirstPeriodInfo = value
        }

    /**
     * Adds a [BellInfo] to the speech.
     * This method avoids throwing exceptions.  The caller must be diligent enough not to do
     * anything weird.  If there are two bells at the same time, it replaces the existing one with
     * this one.  If this bell is after the finish time, it doesn't care, it just adds it anyway.
     * @param bi the [BellInfo] to add
     */
    fun addBellInfo(bi: BellInfo) {
        // If there is already a bell with this time, remove it
        bells.removeAll { it.bellTime == bi.bellTime }
        bells.add(bi)
    }
}
