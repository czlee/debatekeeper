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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;
import java.util.List;

/**
 * Characterization tests for the parser's schema-version and error handling, using the
 * test files historically kept in misc/xml-test-files (copied to test resources).
 */
@RunWith(RobolectricTestRunner.class)
public class DebateFormatErrorHandlingTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private DebateFormatBuilderFromXmlForSchema2 parse(String resourceName) throws Exception {
        DebateFormatBuilderFromXmlForSchema2 builder =
                new DebateFormatBuilderFromXmlForSchema2(context);
        try (InputStream is = getClass().getResourceAsStream("/" + resourceName)) {
            assertNotNull("test resource missing: " + resourceName, is);
            builder.buildDebateFromXml(is);
        }
        return builder;
    }

    @Test
    public void tooNewSchemaIsFlagged() throws Exception {
        DebateFormatBuilderFromXmlForSchema2 builder = parse("errors.xml");
        assertEquals("3.0", builder.getSchemaVersion());
        assertTrue(builder.isSchemaTooNew());
        assertFalse(builder.isSchemaOutdated());
        // The file also deliberately contains malformed times and a bell past the end of
        // its speech; the parser must log errors rather than throw.
        assertTrue(builder.hasErrors());
        List<String> log = builder.getErrorLog();
        assertFalse(log.isEmpty());
    }

    @Test
    public void multiLanguageFormatParsesCleanly() throws Exception {
        DebateFormatBuilderFromXmlForSchema2 builder = parse("languages.xml");
        assertEquals("2.2", builder.getSchemaVersion());
        assertFalse(builder.isSchemaTooNew());
        assertFalse("parse errors: " + builder.getErrorLog(), builder.hasErrors());
    }

    @Test
    public void versionUpdateTestFileParsesCleanly() throws Exception {
        DebateFormatBuilderFromXmlForSchema2 builder = parse("test-version-update.xml");
        assertEquals("2.2", builder.getSchemaVersion());
        assertFalse("parse errors: " + builder.getErrorLog(), builder.hasErrors());
    }
}
