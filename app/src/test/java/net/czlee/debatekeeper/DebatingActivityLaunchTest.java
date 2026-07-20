/*
 * Copyright (C) 2026 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 */

package net.czlee.debatekeeper;

import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Smoke test: the main activity — and with it the fragment/navigation/service startup path and
 * the debate timer screen — must launch without crashing.
 *
 * The test seeds a chosen debate format first, because the no-format-chosen path redirects to
 * FormatChooserFragment, whose file-name extraction uses android.util.Xml (Expat), which is
 * not available in Robolectric. The format-chooser layout itself is covered by
 * {@link ViewBindingRootTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class DebatingActivityLaunchTest {

    private Context context;

    @Before
    public void seedChosenFormat() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        // Copy a bundled format into the user files directory
        File formatsDir = new File(context.getExternalFilesDir(null), "formats");
        //noinspection ResultOfMethodCallIgnored
        formatsDir.mkdirs();
        try (InputStream in = context.getAssets().open("formats/bp.xml");
             OutputStream out = new FileOutputStream(new File(formatsDir, "bp.xml"))) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }

        // Mark it as the chosen format (same preferences file as Activity#getPreferences)
        context.getSharedPreferences("DebatingActivity", Context.MODE_PRIVATE)
                .edit().putString("xmlfn", "bp.xml").commit();
    }

    @Test
    public void activityLaunchesWithoutCrashing() {
        // Robolectric doesn't run bound services itself, so create the service up front and
        // register its binder, so that bindService() connects with it; this triggers
        // initialiseDebate() and brings up the real timer display.
        DebatingTimerService service =
                Robolectric.buildService(DebatingTimerService.class).create().get();
        IBinder binder = service.onBind(new Intent(context, DebatingTimerService.class));
        ComponentName name = new ComponentName(context, DebatingTimerService.class);
        shadowOf((Application) context).setComponentNameAndServiceForBindService(name, binder);

        ActivityController<DebatingActivity> controller =
                Robolectric.buildActivity(DebatingActivity.class);
        controller.setup(); // create -> start -> resume -> visible
        ShadowLooper.idleMainLooper();

        assertNotNull(controller.get());
    }
}
