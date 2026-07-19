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

import net.czlee.debatekeeper.PrepTimeBellsManager

/**
 * PrepTimeSimpleFormat is a passive data class that holds information about a prep format.
 *
 * A prep format normally only defines the length of the prep time.  The rest is configured
 * by the user, and this class figures out the user configurations accordingly.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-01-21
 */
class PrepTimeSimpleFormat(private val prepLength: Long) :
        GenericDebatePhaseFormat(), PrepTimeFormat {

    private var mBellsManager: PrepTimeBellsManager? = null

    fun setBellsManager(manager: PrepTimeBellsManager?) {
        mBellsManager = manager
    }

    override val length: Long
        get() = prepLength

    override val isControlled: Boolean
        get() = false

    override val isPrep: Boolean
        get() = true

    override val bells: ArrayList<BellInfo>
        get() {
            val manager = mBellsManager
                    ?: return arrayListOf(finishBell)
            return manager.getBellsList(length)
        }

    private val finishBell: BellInfo
        get() = BellInfo(length, 2)
}
