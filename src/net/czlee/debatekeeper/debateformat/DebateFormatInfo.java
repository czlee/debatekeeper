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

package net.czlee.debatekeeper.debateformat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import net.czlee.debatekeeper.R;
import android.content.Context;

/**
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
    private       PrepTimeInfo                      prepFormat    = null;
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

        protected ArrayList<MiniBellInfo> getBells() {
            return bells;
        }
        public void addBell(long time, boolean pause) {
            this.bells.add(new MiniBellInfo(time, pause));
        }
    }

    private class SpeechFormatOrPrepInfo extends Resource {
        protected final long length;

        public SpeechFormatOrPrepInfo(long length) {
            super();
            this.length = length;
        }

        public long getLength() {
            return length;
        }

        public String getDescription() {
            // Length line
            String description;
            if (length % 60 == 0) {
                long minutes = length / 60;
                description = mContext.getResources().getQuantityString(R.plurals.viewFormat_timeDescription_lengthInMinutesOnly, (int) minutes, minutes);
            } else
                description = mContext.getString(R.string.viewFormat_timeDescription_lengthInMinutesSeconds, secsToText(length));

            if (getBells().size() > 0) {
                String bellsDesc = getBellsString();
                description += "\n" + bellsDesc;
            }

            return description;
        }

        protected String getBellsString() {
            String bellsList = concatenateBellTimes(getBells());
            String bellsDesc = mContext.getResources().getQuantityString(R.plurals.viewFormat_timeDescription_bellsList, getBells().size(), bellsList);
            return bellsDesc;
        }

        /**
         * Concatenates all the bell times in a list into a single user-readable string
         * @param list a list of <code>MiniBellInfo</code>s
         * @return the single string listing all the bell times
         */
        private String concatenateBellTimes(ArrayList<MiniBellInfo> list) {
            StringBuilder str = new StringBuilder();
            Iterator<MiniBellInfo> iterator = list.iterator();

            while (iterator.hasNext()) {
                MiniBellInfo bi = iterator.next();
                str.append(secsToText(bi.getTime()));
                if (bi.isPause())
                    str.append(mContext.getString(R.string.pauseOnBellIndicator));

                // If there's one after this, add a comma
                if (iterator.hasNext()) str.append(", ");
            }

            return str.toString();
        }
    }

    private class SpeechFormatInfo extends SpeechFormatOrPrepInfo {
        public SpeechFormatInfo(long length) {
            super(length);
        }

        /**
         * Adds all the bells in a {@link Resource} to this SpeechFormatOrPrepInfo.
         * @param res the <code>Resource</code> to add
         */
        public void addResource(Resource res) {
            Iterator<MiniBellInfo> biIterator = res.getBells().iterator();
            MiniBellInfo bi;
            while (biIterator.hasNext()) {
                bi = biIterator.next();
                this.addBell(bi.getTime(), bi.isPause());
            }
        }

    }

    private class PrepTimeInfo extends SpeechFormatOrPrepInfo {

        private final boolean controlled;

        public PrepTimeInfo(long length, boolean controlled) {
            super(length);
            this.controlled = controlled;
        }

        @Override
        public String getDescription() {
            // Length line
            String description;
            if (length % 60 == 0){
                long minutes = length / 60;
                description = mContext.getResources().getQuantityString(R.plurals.viewFormat_timeDescription_lengthInMinutesOnly, (int) minutes, minutes);
            } else
                description = mContext.getString(R.string.viewFormat_timeDescription_lengthInMinutesSeconds, secsToText(length));

            if (controlled)
                description += mContext.getString(R.string.viewFormat_timeDescription_controlledPrepSuffix);

            if (getBells().size() > 0) {
                String bellsDesc = getBellsString();
                description += "\n" + bellsDesc;
            }

            return description;
        }
    }

    private class SpeechInfo {
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

    /**
     * Adds a prep time format to this debate format.
     * Does nothing if a prep time format has already been added.
     * @param length the length in seconds of the prep time
     */
    public void addPrepTime(long length, boolean controlled) {
        if (prepFormat != null) return;
        prepFormat = new PrepTimeInfo(length, controlled);
    }

    /**
     * Adds a speech format to this debate format.
     * @param ref a short reference for this speech format
     * @param length the length in seconds of this speech
     */
    public void addSpeechFormat(String ref, long length) {
        speechFormats.put(ref, new SpeechFormatInfo(length));
    }

    /**
     * Adds a bell to the prep time in this debate format.
     * Does nothing if there is no prep time in this debate format.
     * @param time the bell time within the speech
     * @param pause <b>true</b> if this bell pauses the timer, <b>false</b> if not
     */
    public void addBellToPrepTime(long time, boolean pause) {
        if (prepFormat == null) return;
        prepFormat.addBell(time, pause);
    }

    /**
     * Adds a finish bell to the prep time in this debate format.
     * @param pause <b>true</b> if this bell pauses the timer, <b>false</b> if not
     */
    public void addFinishBellToPrepTime(boolean pause) {
        if (prepFormat == null) return;
        long finishTime = prepFormat.getLength();
        addBellToPrepTime(finishTime, pause);
    }

    /**
     * Adds a bell to a resource in this debate format.
     * @param time the bell time within the speech
     * @param pause <b>true</b> if this bell pauses the timer, <b>false</b> if not
     * @param resourceRef the short reference for the resource to which this bell should be added
     */
    public void addBellToResource(long time, boolean pause, String resourceRef) {
        resources.get(resourceRef).addBell(time, pause);
    }

    /**
     * Adds a bell to a speech format in this debate format.
     * @param time the bell time within the speech
     * @param pause <b>true</b> if this bell pauses the timer, <b>false</b> if not
     * @param speechRef the short reference for the speech format to which this bell should be added
     */
    public void addBellToSpeechFormat(long time, boolean pause, String speechRef) {
        speechFormats.get(speechRef).addBell(time, pause);
    }

    /**
     * Adds a finish bell to a speech format in this debate format.
     * @param pause <b>true</b> if this bell pauses the timer, <b>false</b> if not
     * @param speechRef the short reference for the speech format to which this bell should be added
     */
    public void addFinishBellToSpeechFormat(boolean pause, String speechRef) {
        SpeechFormatOrPrepInfo sfi = speechFormats.get(speechRef);
        long finishTime = sfi.getLength();
        addBellToSpeechFormat(finishTime, pause, speechRef);
    }

    public void addSpeech(String name, String formatRef) {
        speeches.add(new SpeechInfo(name, formatRef));
    }

    /**
     * Instructs that all the bells in a specified resource should be included in a specified
     * speech format
     * @param speechRef the short reference for the speech format
     * @param resourceRef the short reference for the resource
     */
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

    public String getPrepTimeDescription() {
        if (prepFormat == null) return null;
        else return prepFormat.getDescription();
    }

    /**
     * Returns a list of all the speech formats in this debate format, with descriptions.
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
                SpeechFormatOrPrepInfo sti = speechFormats.get(formatRef);
                String typeDesc = sti.getDescription();
                String[] pair = {formatRef, typeDesc};
                result.add(pair);
            }
        }

        return result;
    }

    /**
     * Returns a list of speeches in this debate format.
     * @return An <code>ArrayList</code> of <code>String</code> arrays. Each
     *         <code>String</code> array has two elements.  The first element
     *         is the name of the speech, the second element is the reference
     *         for the format that speech uses.
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

    // ******************************************************************************************
    // Private methods
    // ******************************************************************************************

    private static String secsToText(long time) {
        return String.format("%02d:%02d", time / 60, time % 60);
    }
}
