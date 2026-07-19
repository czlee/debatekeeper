/*
 * Copyright (C) 2026 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 */

package net.czlee.debatekeeper.debateformat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Characterization tests for the bell/period lookup logic in
 * {@link GenericDebatePhaseFormat}/{@link SpeechFormat}, built programmatically the same way
 * the XML builder does.
 */
@RunWith(RobolectricTestRunner.class)
public class SpeechFormatBellsTest {

    private SpeechFormat mFormat;
    private BellInfo mBellAt60;
    private BellInfo mBellAt360;
    private BellInfo mFinishBell;

    // Note: PeriodInfo.update()/addInfo() propagate only description, background color and
    // the POI flag (not name or reference), so the descriptions below are what identifies
    // each period in the assertions — as in the app itself.
    private final PeriodInfo mNormal =
            new PeriodInfo("normal", "Normal", "Normal period", null, false);
    private final PeriodInfo mPoisAllowed =
            new PeriodInfo("pois-allowed", "POIs allowed", "POIs allowed", null, true);
    private final PeriodInfo mWarning =
            new PeriodInfo("warning", "Warning", "Warning bell rung", 0x77ff7700, false);
    private final PeriodInfo mOvertime =
            new PeriodInfo("overtime", "Overtime", "Overtime", 0x77ff0000, false);

    @Before
    public void setUp() {
        mFormat = new SpeechFormat("substantive", 420);
        mFormat.setFirstPeriodInfo(mNormal);

        // Deliberately added out of order: getBellsSorted() must sort by time.
        mFinishBell = new BellInfo(420, 2);
        mFinishBell.setNextPeriodInfo(mOvertime);
        mFormat.addBellInfo(mFinishBell);

        mBellAt60 = new BellInfo(60, 1);
        mBellAt60.setNextPeriodInfo(mPoisAllowed);
        mFormat.addBellInfo(mBellAt60);

        mBellAt360 = new BellInfo(360, 1);
        mBellAt360.setNextPeriodInfo(mWarning);
        mFormat.addBellInfo(mBellAt360);
    }

    @Test
    public void bellLookupByTime() {
        assertSame(mBellAt60, mFormat.getBellAtTime(60));
        assertSame(mBellAt360, mFormat.getBellAtTime(360));
        assertSame(mFinishBell, mFormat.getBellAtTime(420));
        assertNull(mFormat.getBellAtTime(0));
        assertNull(mFormat.getBellAtTime(61));
        assertNull(mFormat.getBellAtTime(421));
    }

    @Test
    public void bellsAreSortedByTime() {
        List<BellInfo> bells = mFormat.getBellsSorted();
        assertEquals(3, bells.size());
        assertEquals(60, bells.get(0).getBellTime());
        assertEquals(360, bells.get(1).getBellTime());
        assertEquals(420, bells.get(2).getBellTime());
    }

    @Test
    public void periodInfoAccumulatesThroughTime() {
        // Before the first bell, the first period applies.
        assertEquals("Normal period", mFormat.getPeriodInfoForTime(0).getDescription());
        assertEquals("Normal period", mFormat.getPeriodInfoForTime(59).getDescription());

        // From each bell's time onwards, its next-period applies.
        assertEquals("POIs allowed", mFormat.getPeriodInfoForTime(60).getDescription());
        assertTrue(mFormat.getPeriodInfoForTime(60).isPoisAllowed());
        assertEquals("POIs allowed", mFormat.getPeriodInfoForTime(359).getDescription());
        assertEquals("Warning bell rung", mFormat.getPeriodInfoForTime(360).getDescription());
        assertFalse(mFormat.getPeriodInfoForTime(360).isPoisAllowed());
        assertEquals("Overtime", mFormat.getPeriodInfoForTime(420).getDescription());
        assertEquals("Overtime", mFormat.getPeriodInfoForTime(1000).getDescription());
    }

    @Test
    public void poisAllowedDetection() {
        assertTrue(mFormat.hasPoisAllowedSomewhere());

        SpeechFormat noPois = new SpeechFormat("reply", 240);
        noPois.setFirstPeriodInfo(mNormal);
        BellInfo bell = new BellInfo(180, 1);
        bell.setNextPeriodInfo(mWarning);
        noPois.addBellInfo(bell);
        assertFalse(noPois.hasPoisAllowedSomewhere());
    }

    @Test
    public void speechFormatBasics() {
        assertEquals(420, mFormat.getLength());
        assertEquals("substantive", mFormat.getReference());
        assertFalse(mFormat.isPrep());
        // getFirstPeriodInfo returns a merged copy, not the object passed in.
        assertEquals("Normal period", mFormat.getFirstPeriodInfo().getDescription());
        assertFalse(mFormat.getFirstPeriodInfo().isPoisAllowed());
    }

    @Test
    public void bellSoundInfoCounts() {
        assertEquals(1, mBellAt60.getBellSoundInfo().getNumberOfBells());
        assertEquals(2, mFinishBell.getBellSoundInfo().getNumberOfBells());
        assertFalse(mFinishBell.isSilent());
        assertFalse(mFinishBell.isPauseOnBell());
    }
}
