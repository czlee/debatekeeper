/*
 * Copyright (C) 2021 Chuan-Zheng Lee
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.czlee.debatekeeper.DebatingTimerService.DebatingTimerServiceBinder

/**
 * This is the main activity for the Debatekeeper application. It hosts all of the fragments.
 *
 * @author Chuan-Zheng Lee
 * @since  2021-09-22
 */
class DebatingActivity : AppCompatActivity() {

    private var mServiceBinder: DebatingTimerServiceBinder? = null
    private val mServiceConnection = DebatekeeperServiceConnection()

    private inner class DebatekeeperServiceConnection : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "service connected")
            mServiceBinder = service as DebatingTimerServiceBinder
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "service disconnected")
            mServiceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Under edge-to-edge (enforced from Android 15 when targeting SDK 35+), inset the whole
        // container so that no screen draws under the system bars; the container's black
        // background shows behind the bars instead. On older versions the insets are zero and
        // this is a no-op, preserving the pre-edge-to-edge appearance everywhere.
        val topLayout = findViewById<android.view.View>(R.id.top_layout)
        ViewCompat.setOnApplyWindowInsetsListener(topLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Start the timer service in the background
        // (DebateManager will push it to the foreground when the timer is started.)
        val serviceIntent = Intent(this, DebatingTimerService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(mServiceConnection)

        val binder = mServiceBinder
        if (binder != null) {
            val debateManager = binder.debateManager
            if (debateManager == null || !debateManager.isRunning) {
                val intent = Intent(this, DebatingTimerService::class.java)
                stopService(intent)
                Log.i(TAG, "Stopped service because timer is stopped")
            } else {
                Log.i(TAG, "Keeping service alive because timer is running")
            }
        } else {
            Log.e(TAG, "Tried to stop service, but the service binder was null!")
        }
    }

    companion object {
        private const val TAG = "DebatingActivity"
    }
}
