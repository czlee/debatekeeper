/*
 * Copyright (C) 2026 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 */

package net.czlee.debatekeeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;

import net.czlee.debatekeeper.debateformat.BellInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Characterization tests for {@link PrepTimeBellsManager}: bell specification handling,
 * resolution against a prep length, and persistence round-trips.
 */
@RunWith(RobolectricTestRunner.class)
public class PrepTimeBellsManagerTest {

    private Context mContext;
    private PrepTimeBellsManager mManager;

    private static Bundle startBell(int seconds) {
        Bundle bundle = new Bundle();
        bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_START);
        bundle.putLong(PrepTimeBellsManager.KEY_TIME, seconds);
        return bundle;
    }

    private static Bundle finishBell(int secondsBeforeFinish) {
        Bundle bundle = new Bundle();
        bundle.putString(PrepTimeBellsManager.KEY_TYPE, PrepTimeBellsManager.VALUE_TYPE_FINISH);
        bundle.putLong(PrepTimeBellsManager.KEY_TIME, secondsBeforeFinish);
        return bundle;
    }

    private static Bundle proportionalBell(double proportion) {
        Bundle bundle = new Bundle();
        bundle.putString(PrepTimeBellsManager.KEY_TYPE,
                PrepTimeBellsManager.VALUE_TYPE_PROPORTIONAL);
        bundle.putDouble(PrepTimeBellsManager.KEY_PROPORTION, proportion);
        return bundle;
    }

    private static List<Long> bellTimes(PrepTimeBellsManager manager, long length) {
        ArrayList<Long> times = new ArrayList<>();
        for (BellInfo bell : manager.getBellsList(length))
            times.add(bell.getBellTime());
        return times;
    }

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mManager = new PrepTimeBellsManager(mContext);
    }

    @Test
    public void emptyPreferencesLoadsDefaultFinishBell() {
        SharedPreferences prefs = mContext.getSharedPreferences("test_empty", Context.MODE_PRIVATE);
        mManager.loadFromPreferences(prefs);
        assertTrue(mManager.hasBells());
        assertTrue(mManager.hasFinishBell());
        assertFalse(mManager.hasBellsOtherThanFinish());
        assertEquals(List.of(600L), bellTimes(mManager, 600));
    }

    @Test
    public void bellsResolveAgainstPrepLength() {
        mManager.addFromBundle(startBell(120));
        mManager.addFromBundle(finishBell(0));
        mManager.addFromBundle(proportionalBell(0.5));

        // getBellsList prioritises by descending bell count, so the finish bell (which is
        // made a double bell) comes first; the others follow in specification order.
        assertEquals(List.of(600L, 120L, 300L), bellTimes(mManager, 600));
        // Same specs against a different length: start stays, finish and proportional move.
        assertEquals(List.of(1800L, 120L, 900L), bellTimes(mManager, 1800));
    }

    @Test
    public void finishBellOffset() {
        mManager.addFromBundle(finishBell(60)); // one minute before finish
        assertEquals(List.of(540L), bellTimes(mManager, 600));
    }

    @Test
    public void preferencesRoundTripPreservesBells() {
        mManager.addFromBundle(startBell(120));
        mManager.addFromBundle(finishBell(0));
        mManager.addFromBundle(proportionalBell(0.25));

        SharedPreferences prefs =
                mContext.getSharedPreferences("test_roundtrip", Context.MODE_PRIVATE);
        mManager.saveToPreferences(prefs);

        PrepTimeBellsManager reloaded = new PrepTimeBellsManager(mContext);
        reloaded.loadFromPreferences(prefs);

        assertEquals(mManager.getBellDescriptions(), reloaded.getBellDescriptions());
        assertEquals(bellTimes(mManager, 1200), bellTimes(reloaded, 1200));
    }

    @Test
    public void deleteAllBellsCanSpareFinishBell() {
        mManager.addFromBundle(startBell(120));
        mManager.addFromBundle(finishBell(0));
        mManager.addFromBundle(proportionalBell(0.5));

        mManager.deleteAllBells(true);
        assertTrue(mManager.hasFinishBell());
        assertFalse(mManager.hasBellsOtherThanFinish());

        mManager.deleteAllBells(false);
        assertFalse(mManager.hasBells());
    }
}
