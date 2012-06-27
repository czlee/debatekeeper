/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import android.content.Context;

/**
 * TODO Comment this class, before it's too late!
 * Passive data class for holding quick information about a debate format. This
 * is NOT the same as the {@link DebateFormat} class itself.
 * <code>DebateFormatInfo</code> holds only information that is human-readable
 * on an "information" screen about the debate format. Specifically:
 * <ul>
 * <li><code>DebateFormatInfo</code>, unlike <code>DebateFormat</code>,
 * <b>does</b> store the information between the &lt;info&gt; tags.</li>
 * <li><code>DebateFormatInfo</code> does <b>not</b> have
 * <code>SpeechFormat</code>s, <code>BellInfo</code>s or <code>PeriodInfo</code>
 * s.</li>
 * <li>In fact, <code>DebateFormatInfo</code> does <b>not</b> store anything
 * about periods at all.</li>
 * <li><code>DebateFormatInfo</code> stores only enough information about the
 * speeches to be able to describe them quickly.</li>
 * </ul>
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-20
 */
public class DebateFormatInfo {

    private final Context mContext;

    private       String                            name          = new String();
    private final ArrayList<String>                 regions       = new ArrayList<String>();
    private final ArrayList<String>                 levels        = new ArrayList<String>();
    private final ArrayList<String>                 usedAts       = new ArrayList<String>();
    private final HashMap<String, Resource>         resources     = new HashMap<String, Resource>();
    private final HashMap<String, SpeechFormatInfo> speechFormats = new HashMap<String, SpeechFormatInfo>();
    private final ArrayList<SpeechInfo>             speeches      = new ArrayList<SpeechInfo>();
    private       String                            description   = new String("-");

    public DebateFormatInfo(Context context) {
        super();
        this.mContext = context;
    }

    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************
    private class MiniBellInfo {
        private final long time;
        private final boolean pause;
        public MiniBellInfo(long time, boolean pause) {
            super();
            this.time = time;
            this.pause = pause;
        }
        public long getTime() {
            return time;
        }
        public boolean isPause() {
            return pause;
        }
    }

    private class Resource {
        private final ArrayList<MiniBellInfo> bells = new ArrayList<MiniBellInfo>();

        public ArrayList<MiniBellInfo> getBells() {
            return bells;
        }
        public void addBell(long time, boolean pause) {
            this.bells.add(new MiniBellInfo(time, pause));
        }
    }

    private class SpeechFormatInfo extends Resource {
        private long length;

        public long getLength() {
            return length;
        }
        public void setLength(long length) {
            this.length = length;
        }

        public void addResource(Resource res) {
            Iterator<MiniBellInfo> biIterator = res.getBells().iterator();
            MiniBellInfo bi;
            while (biIterator.hasNext()) {
                bi = biIterator.next();
                this.addBell(bi.getTime(), bi.isPause());
            }
        }
    }

    public class SpeechInfo {
        private final String name;
        private final String format;
        public SpeechInfo(String name, String format) {
            super();
            this.name = name;
            this.format = format;
        }
        public String getName() {
            return name;
        }
        public String getFormat() {
            return format;
        }

    }

    // ******************************************************************************************
    // Public methods
    // ******************************************************************************************

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ArrayList<String> getRegions() {
        return regions;
    }

    public ArrayList<String> getLevels() {
        return levels;
    }

    public ArrayList<String> getUsedAts() {
        return usedAts;
    }

    public void addRegion(String region) {
        regions.add(region);
    }

    public void addLevel(String level) {
        levels.add(level);
    }

    public void addUsedAt(String usedAt) {
        usedAts.add(usedAt);
    }

    public void addResource(String ref) {
        resources.put(ref, new Resource());
    }

    public void addSpeechFormat(String ref, long length) {
        speechFormats.put(ref, new SpeechFormatInfo());
        speechFormats.get(ref).setLength(length);
    }

    public void addBellToResource(long time, boolean pause, String resourceRef) {
        resources.get(resourceRef).addBell(time, pause);
    }

    public void addBellToSpeechFormat(long time, boolean pause, String speechRef) {
        speechFormats.get(speechRef).addBell(time, pause);
    }

    public void addFinishBellToSpeechFormat(boolean pause, String speechRef) {
        SpeechFormatInfo sfi = speechFormats.get(speechRef);
        long finishTime = sfi.getLength();
        addBellToSpeechFormat(finishTime, pause, speechRef);
    }

    public void addSpeech(String name, String formatRef) {
        speeches.add(new SpeechInfo(name, formatRef));
    }

    public void includeResource(String speechRef, String resourceRef) {
        Resource res = resources.get(resourceRef);
        speechFormats.get(speechRef).addResource(res);
    }

    public boolean hasResource(String ref) {
        return resources.containsKey(ref);
    }

    public boolean hasSpeechFormat(String ref) {
        return speechFormats.containsKey(ref);
    }

    /**
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements. The first element is
     *         the speech type reference. The second element is a short
     *         description of the speech type. The <code>ArrayList</code> is
     *         sorted in the order the speech types appear in the debate. If a
     *         speech type isn't used, it isn't part of the returned
     *         <code>ArrayList</code>.
     */
    public ArrayList<String[]> getSpeechFormatDescriptions() {
        ArrayList<String[]> result = new ArrayList<String[]>();
        Iterator<SpeechInfo> iterator = speeches.iterator();
        HashSet<String> seenFormatRefs = new HashSet<String>();

        while (iterator.hasNext()) {
            String formatRef = iterator.next().getFormat();
            if (!seenFormatRefs.contains(formatRef) && speechFormats.containsKey(formatRef)) {
                seenFormatRefs.add(formatRef);
                SpeechFormatInfo sti = speechFormats.get(formatRef);
                String bellsList = concatenate(sti.getBells());
                String typeDesc = mContext.getString(R.string.SpeechTypeDescription,
                        secsToText(sti.getLength()), bellsList);
                String[] pair = {formatRef, typeDesc};
                result.add(pair);
            }
        }

        return result;
    }

    /**
     * @return
     */
    public ArrayList<String[]> getSpeeches() {
        ArrayList<String[]> result = new ArrayList<String[]>();
        Iterator<SpeechInfo> iterator = speeches.iterator();
        while (iterator.hasNext()) {
            SpeechInfo speech = iterator.next();
            String[] pair = {speech.getName(), speech.getFormat()};
            result.add(pair);
        }
        return result;
    }

    private static String secsToText(long time) {
        if (time >= 0) {
            return String.format("%02d:%02d", time / 60, time % 60);
        } else {
            return String.format("%02d:%02d over", -time / 60, -time % 60);
        }
    }

    private String concatenate(ArrayList<MiniBellInfo> list) {
        String str = new String();
        Iterator<MiniBellInfo> iterator = list.iterator();
        MiniBellInfo bi;

        // Start with the first item (if it exists)
        if (iterator.hasNext()) {
            bi = iterator.next();
            str = secsToText(bi.getTime());
            if (bi.isPause())
                str = str.concat(mContext.getString(R.string.SpeechTypePauseIndicator));
        }

        // Add the second and further items, putting a line break in between.
        while (iterator.hasNext()) {
            str = str.concat(", ");
            bi = iterator.next();
            str = str.concat(secsToText(bi.getTime()));
            if (bi.isPause())
                str = str.concat(mContext.getString(R.string.SpeechTypePauseIndicator));
        }
        return str;
    }
}
