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

package net.czlee.debatekeeper.debatemanager

import net.czlee.debatekeeper.AlertManager
import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender

/**
 * DebateElementManager is an abstract base class for classes that manage elements that
 * are part of a debate.
 *
 * Current known subclasses: [DebatePhaseManager], [PoiManager].
 *
 * @author Chuan-Zheng Lee
 * @since 2012-09-09
 */
abstract class DebateElementManager(protected val mAlertManager: AlertManager) {

    // Abstract methods
    abstract val isRunning: Boolean
    abstract fun start()
    abstract fun stop()

    private var mBroadcastSender: GuiUpdateBroadcastSender? = null

    /**
     * Sets a broadcast sender for this speech manager.
     * `DebatePhaseManager` will call `sendBroadcast()` on the broadcast sender
     * when the timer counts up/down.
     * @param sender the [GuiUpdateBroadcastSender]
     */
    fun setBroadcastSender(sender: GuiUpdateBroadcastSender?) {
        mBroadcastSender = sender
    }

    protected fun sendBroadcast() {
        mBroadcastSender?.sendBroadcast()
    }

    companion object {
        protected const val TIMER_DELAY: Long = 1000
        protected const val TIMER_PERIOD: Long = 1000
    }
}
