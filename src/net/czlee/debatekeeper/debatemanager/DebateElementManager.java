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

package net.czlee.debatekeeper.debatemanager;

import net.czlee.debatekeeper.AlertManager;
import net.czlee.debatekeeper.DebatingTimerService.GuiUpdateBroadcastSender;

/**
 * DebateElementManager is an abstract base class for classes that manage elements that
 * are part of a debate.
 *
 * <p>Current known subclasses: {@link DebatePhaseManager}, {@link PoiManager}.</p>
 *
 * @author Chuan-Zheng Lee
 * @since 2012-09-09
 */
public abstract class DebateElementManager {

    protected final AlertManager mAlertManager;

    // Abstract methods
    public abstract boolean isRunning();
    public abstract void start();
    public abstract void stop();

    private GuiUpdateBroadcastSender mBroadcastSender;

    protected static final long TIMER_DELAY = 1000;
    protected static final long TIMER_PERIOD = 1000;

    public DebateElementManager(AlertManager am) {
        super();
        this.mAlertManager = am;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Sets a broadcast sender for this speech manager.
     * <code>DebatePhaseManager</code> will call <code>sendBroadcast()</code> on the broadcast sender
     * when the timer counts up/down.
     * @param sender the {@link GuiUpdateBroadcastSender}
     */
    public void setBroadcastSender(GuiUpdateBroadcastSender sender) {
        this.mBroadcastSender = sender;
    }

    //******************************************************************************************
    // Protected methods
    //******************************************************************************************

    protected void sendBroadcast() {
        if (mBroadcastSender != null)
            mBroadcastSender.sendBroadcast();
    }

}