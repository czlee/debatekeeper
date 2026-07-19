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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Characterization tests for the static helpers in {@link XmlUtilities}: time-string parsing
 * and schema version comparison.
 */
public class XmlUtilitiesTest {

    @Test
    public void timeStringsInMinutesAndSeconds() {
        assertEquals(420, XmlUtilities.timeStr2Secs("7:00"));
        assertEquals(65, XmlUtilities.timeStr2Secs("1:05"));
        assertEquals(0, XmlUtilities.timeStr2Secs("0:00"));
        assertEquals(3900, XmlUtilities.timeStr2Secs("65:00"));
    }

    @Test
    public void timeStringsInPlainSeconds() {
        assertEquals(120, XmlUtilities.timeStr2Secs("120"));
        assertEquals(5, XmlUtilities.timeStr2Secs("5"));
    }

    @Test
    public void invalidTimeStringsThrow() {
        assertThrows(NumberFormatException.class, () -> XmlUtilities.timeStr2Secs("finsh"));
        assertThrows(NumberFormatException.class, () -> XmlUtilities.timeStr2Secs("4.03"));
        assertThrows(NumberFormatException.class, () -> XmlUtilities.timeStr2Secs(""));
    }

    @Test
    public void schemaVersionComparison() throws Exception {
        assertTrue(XmlUtilities.compareSchemaVersions("2.0", "2.2") < 0);
        assertTrue(XmlUtilities.compareSchemaVersions("2.2", "2.0") > 0);
        assertEquals(0, XmlUtilities.compareSchemaVersions("2.2", "2.2"));
        assertTrue(XmlUtilities.compareSchemaVersions("2.10", "2.2") > 0); // numeric, not lexicographic
        assertTrue(XmlUtilities.compareSchemaVersions("1.9", "2.0") < 0);
    }

    @Test
    public void invalidSchemaVersionsThrow() {
        assertThrows(XmlUtilities.IllegalSchemaVersionException.class,
                () -> XmlUtilities.compareSchemaVersions("abc", "2.0"));
        assertThrows(XmlUtilities.IllegalSchemaVersionException.class,
                () -> XmlUtilities.compareSchemaVersions("2.0", "2"));
    }

    @Test
    public void schemaVersionValidity() {
        assertTrue(XmlUtilities.isValidSchemaVersion("2.2"));
        assertTrue(XmlUtilities.isValidSchemaVersion("10.0"));
        assertFalse(XmlUtilities.isValidSchemaVersion("2"));
        assertFalse(XmlUtilities.isValidSchemaVersion("2.a"));
        // Current behavior: null is not handled gracefully.
        assertThrows(NullPointerException.class, () -> XmlUtilities.isValidSchemaVersion(null));
    }
}
