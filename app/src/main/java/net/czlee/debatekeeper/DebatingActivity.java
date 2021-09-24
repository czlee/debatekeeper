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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import net.czlee.debatekeeper.debatemanager.DebateManager;

/**
 * This is the main activity for the Debatekeeper application. It hosts all of the fragments.
 *
 * @author Chuan-Zheng Lee
 * @since  2021-09-22
 */
public class DebatingActivity extends AppCompatActivity {

    private static final String TAG = "DebatingActivity";

    private DebatingTimerService.DebatingTimerServiceBinder mServiceBinder;
    private final DebatekeeperServiceConnection mServiceConnection = new DebatekeeperServiceConnection();

    private class DebatekeeperServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service connected");
            mServiceBinder = (DebatingTimerService.DebatingTimerServiceBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service disconnected");
            mServiceBinder = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start the timer service in the background
        // (DebateManager will push it to the foreground when the timer is started.)
        // TODO push to foreground when timer is started in DebateManager
        Intent serviceIntent = new Intent(this, DebatingTimerService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);

        DebateManager debateManager = mServiceBinder.getDebateManager();
        if (debateManager == null || !debateManager.isRunning()) {
            Intent intent = new Intent(this, DebatingTimerService.class);
            stopService(intent);
            Log.i(TAG, "Stopped service because timer is stopped");
        } else {
            Log.i(TAG, "Keeping service alive because timer is running");
        }
    }
}