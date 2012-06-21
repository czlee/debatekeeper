package com.ftechz.DebatingTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

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
 *
 */
public class DebateFormatInfo {

    private final Context mContext;

    private final ArrayList<String>               regions     = new ArrayList<String>();
    private final ArrayList<String>               levels      = new ArrayList<String>();
    private final ArrayList<String>               usedAts     = new ArrayList<String>();
    private final HashMap<String, Resource>       resources   = new HashMap<String, Resource>();
    private final HashMap<String, SpeechTypeInfo> speechTypes = new HashMap<String, SpeechTypeInfo>();
    private final HashMap<String, String>         speeches    = new HashMap<String, String>();
    private       String                          description = new String("-");

    public DebateFormatInfo(Context context) {
        super();
        this.mContext = context;
    }

    // ******************************************************************************************
    // Private classes
    // ******************************************************************************************
    private class BellInfo {
        private final long time;
        private final boolean pause;
        public BellInfo(long time, boolean pause) {
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
        private ArrayList<BellInfo> bells;

        public ArrayList<BellInfo> getBells() {
            return bells;
        }
        public void addBell(long time, boolean pause) {
            this.bells.add(new BellInfo(time, pause));
        }
    }

    private class SpeechTypeInfo extends Resource {
        private long length;

        public long getLength() {
            return length;
        }
        public void setLength(long length) {
            this.length = length;
        }
    }

    // ******************************************************************************************
    // Public methods
    // ******************************************************************************************

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

    public void addSpeechType(String ref, long length) {
        speechTypes.put(ref, new SpeechTypeInfo());
        speechTypes.get(ref).setLength(length);
    }

    public void addBellToResource(long time, boolean pause, String resourceRef) {
        speechTypes.get(resourceRef).addBell(time, pause);
    }

    public void addBellToSpeechType(long time, boolean pause, String speechTypeRef) {
        speechTypes.get(speechTypeRef).addBell(time, pause);
    }

    public HashMap<String, String> getSpeechTypeDescriptions() {
        HashMap<String, String> descs = new HashMap<String, String>();
        Iterator<Entry<String, SpeechTypeInfo>> iterator = speechTypes.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, SpeechTypeInfo> entry = iterator.next();
            SpeechTypeInfo sti = entry.getValue();
            String bellsList = concatenate(sti.getBells());
            String typeDesc = mContext.getString(R.string.SpeechTypeDescription,
                    secsToText(sti.getLength()), bellsList);
            descs.put(entry.getKey(), typeDesc);
        }
        return descs;
    }

    public HashMap<String, String> getSpeeches() {
        return speeches;
    }

    private static String secsToText(long time) {
        if (time >= 0) {
            return String.format("%02d:%02d", time / 60, time % 60);
        } else {
            return String.format("%02d:%02d over", -time / 60, -time % 60);
        }
    }

    private String concatenate(ArrayList<BellInfo> list) {
        String str = new String();
        Iterator<BellInfo> iterator = list.iterator();
        BellInfo bi;

        // Start with the first item (if it exists)
        if (iterator.hasNext()) {
            bi = iterator.next();
            str = secsToText(bi.getTime());
            if (bi.isPause())
                str = str.concat(mContext.getString(R.string.SpeechTypePauseIndicator));
        }

        // Add the second and further items, putting a line break in between.
        while (iterator.hasNext()) {
            str = str.concat(",");
            bi = iterator.next();
            str = secsToText(bi.getTime());
            if (bi.isPause())
                str = str.concat(mContext.getString(R.string.SpeechTypePauseIndicator));
        }
        return str;
    }
}
