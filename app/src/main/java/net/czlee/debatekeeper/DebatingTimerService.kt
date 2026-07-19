/*
 * Copyright (C) 2012 Phillip Cao, Chuan-Zheng Lee
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

package net.czlee.debatekeeper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.czlee.debatekeeper.debateformat.DebateFormat
import net.czlee.debatekeeper.debatemanager.DebateManager

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
class DebatingTimerService : Service() {

    private val mBinder: IBinder = DebatingTimerServiceBinder()
    private var mDebateManager: DebateManager? = null
    private var mAlertManager: AlertManager? = null

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * This class is the binder between this service and the DebatingActivity.
     */
    inner class DebatingTimerServiceBinder : Binder() {

        val debateManager: DebateManager?
            get() = mDebateManager

        val alertManager: AlertManager?
            get() = mAlertManager

        fun createDebateManager(df: DebateFormat): DebateManager {
            releaseDebateManager()
            val debateManager = DebateManager(this@DebatingTimerService, df, mAlertManager!!)
            debateManager.setBroadcastSender(GuiUpdateBroadcastSender())
            mDebateManager = debateManager
            return debateManager
        }

        fun releaseDebateManager() {
            mDebateManager?.release()
            mDebateManager = null
        }
    }

    /**
     * This class is passed to the `DebatePhaseManager` (indirectly) as a means to trigger a
     * GUI update in the `DebatingActivity`.
     */
    inner class GuiUpdateBroadcastSender {
        fun sendBroadcast() {
            val broadcastIntent = Intent(UPDATE_GUI_BROADCAST_ACTION)
            LocalBroadcastManager.getInstance(this@DebatingTimerService)
                    .sendBroadcast(broadcastIntent)
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreate() {
        super.onCreate()
        mAlertManager = AlertManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // We don't care if the service is started multiple times.  It only ever
        // makes sense to have one of these at a given time; all activities should bind
        // do this single instance.  In fact, there should never be more than one
        // activity.

        // We don't do anything with intent.  If we ever do, be sure to check
        // for the possibility that intent could be null!

        Log.v(TAG, "The service is starting: $startId")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        mDebateManager?.release()
        mDebateManager = null

        Log.v(TAG, "The service is shutting down now!")
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i(TAG, "hello, I am createNotificationChannel")
            val channel = NotificationChannel(CHANNEL_ID,
                    getString(R.string.notificationChannel_timer_name),
                    NotificationManager.IMPORTANCE_LOW)
            channel.description = getString(R.string.channel_description)

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "timer"
        private const val TAG = "DebatingTimerService"

        const val UPDATE_GUI_BROADCAST_ACTION = "net.czlee.debatekeeper.update"
    }
}
