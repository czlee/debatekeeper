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
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Characterization tests for {@link DebateFormatBuilderFromXmlForSchema2}: parses the four
 * debate formats bundled in assets and compares a complete summary of the result against
 * golden files in src/test/resources/golden/.  These pin the parser's behavior across
 * refactors (in particular the Java-to-Kotlin conversion); they are not a specification of
 * intended behavior beyond "whatever the app did before".
 *
 * If a golden file is missing or a summary changes, the actual summary is written to
 * build/golden-actual/ for inspection.
 */
@RunWith(RobolectricTestRunner.class)
public class DebateFormatCharacterizationTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private void checkAgainstGolden(String assetName, String goldenName) throws IOException, org.xml.sax.SAXException {
        DebateFormatBuilderFromXmlForSchema2 builder =
                new DebateFormatBuilderFromXmlForSchema2(context);
        DebateFormat format;
        try (InputStream is = context.getAssets().open("formats/" + assetName)) {
            format = builder.buildDebateFromXml(is);
        }
        assertFalse("parse errors: " + builder.getErrorLog(), builder.hasErrors());
        assertEquals("2.2", builder.getSchemaVersion());

        String actual = FormatSummariser.summarise(format);

        File actualDir = new File("build/golden-actual");
        //noinspection ResultOfMethodCallIgnored
        actualDir.mkdirs();
        Files.write(Paths.get(actualDir.getPath(), goldenName),
                actual.getBytes(StandardCharsets.UTF_8));

        InputStream goldenStream = getClass().getResourceAsStream("/golden/" + goldenName);
        if (goldenStream == null) {
            fail("Golden file missing: src/test/resources/golden/" + goldenName
                    + " — actual summary written to app/build/golden-actual/" + goldenName);
            return;
        }
        String golden = new String(goldenStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("Summary of " + assetName + " differs from golden file "
                + goldenName + " (actual written to app/build/golden-actual/)", golden, actual);
    }

    @Test
    public void britishParliamentary() throws Exception {
        checkAgainstGolden("bp.xml", "bp.txt");
    }

    @Test
    public void australs() throws Exception {
        checkAgainstGolden("australs.xml", "australs.txt");
    }

    @Test
    public void asian() throws Exception {
        checkAgainstGolden("asian.xml", "asian.txt");
    }

    @Test
    public void worldSchools() throws Exception {
        checkAgainstGolden("worldschools.xml", "worldschools.txt");
    }
}
