/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
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

package net.czlee.debatekeeper;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * DebatingTimerService class
 * The background service for the application
 * Keeps the debate/timers ticking in the background
 * Uses a broadcast (though not the best way IMO) to update the main UI
 *
 * NOTE NOTE NOTE NOTE NOTE NOTE NOTE
 * We are NOT using a separate thread for this class.  This means that the Service runs
 * in the same process as the Activity that calls it (DebatingActivity), because we
 * haven't specified otherwise.  This means that this service must NOT do intensive work,
 * because if it does, IT WILL BLOCK THE USER INTERFACE!
 *
 * @author Phillip Cao
 * @author Chuan-Zheng Lee
 * @since  2012-03-30
 */
public class DebatingTimerService extends Service
{
    public static final String UPDATE_GUI_BROADCAST_ACTION = "net.czlee.debatekeeper.update";
    private final IBinder mBinder = new DebatingTimerServiceBinder();
    private DebateManager mDebateManager;
    private AlertManager mAlertManager;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * This class is the binder between this service and the DebatingActivity.
     */
    public class DebatingTimerServiceBinder extends Binder {
        public DebateManager getDebateManager() {
            return mDebateManager;
        }

        public AlertManager getAlertManager() {
            return mAlertManager;
        }

        public DebateManager createDebateManager(DebateFormat df) {
            releaseDebateManager();
            mDebateManager = new DebateManager(df, mAlertManager);
            mDebateManager.setBroadcastSender(new GuiUpdateBroadcastSender());
            return mDebateManager;
        }

        public void releaseDebateManager() {
            if(mDebateManager != null)
                mDebateManager.release();
            mDebateManager = null;
        }
    }

    /**
     * This class is passed to the <code>SpeechManager</code> (indirectly) as a means to trigger a
     * GUI update in the <code>DebatingActivity</code>.
     */
    public class GuiUpdateBroadcastSender {
        public void sendBroadcast() {
            Intent broadcastIntent = new Intent(UPDATE_GUI_BROADCAST_ACTION);
            LocalBroadcastManager.getInstance(DebatingTimerService.this)
                    .sendBroadcast(broadcastIntent);
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    @Override
    public void onCreate() {
        super.onCreate();
        mAlertManager = new AlertManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We don't care if the service is started multiple times.  It only ever
        // makes sense to have one of these at a given time; all activities should bind
        // do this single instance.  In fact, there should never be more than one
        // activity.

        // We don't do anything with intent.  If we ever do, be sure to check
        // for the possibility that intent could be null!

        Log.v(this.getClass().getSimpleName(), String.format("The service is starting: %d", startId));

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mDebateManager != null) {
            mDebateManager.release();
            mDebateManager = null;
        }

        Log.v(this.getClass().getSimpleName(), "The service is shutting down now!");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}
