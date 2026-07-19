/*
 * Copyright (C) 2026 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 */

package net.czlee.debatekeeper.debateformat;

import java.util.List;
import java.util.Locale;

/**
 * Produces a deterministic plain-text summary of a parsed {@link DebateFormat}, for use in
 * characterization (golden-file) tests.  The summary intentionally covers everything a
 * {@link DebateFormatBuilderFromXmlForSchema2} parse produces that affects timing behavior:
 * speeches, lengths, periods (with colors and POI flags) and bells (with times, counts,
 * silence/pause flags and next periods).
 */
class FormatSummariser {

    static String summarise(DebateFormat format) {
        StringBuilder s = new StringBuilder();
        s.append("name: ").append(format.getName()).append('\n');
        s.append("short name: ").append(format.getShortName()).append('\n');

        s.append("prep: ");
        if (format.hasPrepFormat()) {
            PrepTimeFormat prep = format.getPrepFormat();
            s.append(prep.isControlled() ? "controlled" : "simple")
                    .append(", length ").append(prep.getLength()).append('\n');
            if (prep.isControlled())
                appendPhaseDetails(s, prep);
        } else {
            s.append("none\n");
        }

        int count = format.numberOfSpeeches();
        s.append("speeches: ").append(count).append('\n');
        for (int i = 0; i < count; i++) {
            DebatePhaseFormat speech = format.getSpeechFormat(i);
            s.append("speech ").append(i).append(": ").append(format.getSpeechName(i))
                    .append(", length ").append(speech.getLength()).append('\n');
            appendPhaseDetails(s, speech);
        }
        return s.toString();
    }

    private static void appendPhaseDetails(StringBuilder s, DebatePhaseFormat phase) {
        s.append("  first period: ");
        appendPeriod(s, phase.getFirstPeriodInfo());
        s.append('\n');
        List<BellInfo> bells = phase.getBellsSorted();
        for (BellInfo bell : bells) {
            s.append("  bell at ").append(bell.getBellTime())
                    .append(": count ").append(bell.getBellSoundInfo().getNumberOfBells())
                    .append(", silent ").append(bell.isSilent())
                    .append(", pause ").append(bell.isPauseOnBell())
                    .append(", next period ");
            appendPeriod(s, bell.getNextPeriodInfo());
            s.append('\n');
        }
    }

    private static void appendPeriod(StringBuilder s, PeriodInfo pi) {
        if (pi == null) {
            s.append("null");
            return;
        }
        Integer color = pi.getBackgroundColor();
        s.append('[').append(pi.getReference())
                .append(", name=").append(pi.getName())
                .append(", desc=").append(pi.getDescription())
                .append(", color=")
                .append(color == null ? "null" : String.format(Locale.ROOT, "#%08x", color))
                .append(", pois=").append(pi.isPoisAllowed())
                .append(']');
    }
}
