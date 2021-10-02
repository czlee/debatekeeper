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

import android.content.Context;
import android.util.Xml;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Extracts and holds very basic information about schema 1.0 files. Retained with minimal
 * functionality as a transition from supporting schema 1.0 to not knowing that schema 1.0 exists.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-20
 */
public class DebateFormatInfoForSchema1 implements DebateFormatInfo {

    private String mName;
    private String mSchemaVersion = null;

    private static final String MINIMUM_SCHEMA_VERSION = "1.0";
    private static final String MAXIMUM_SCHEMA_VERSION = "1.1";

    private final Context mContext;
    private final String  DEBATING_TIMER_URI;

    public DebateFormatInfoForSchema1(Context context, InputStream is) throws IOException, SAXException {
        mContext           = context;
        DEBATING_TIMER_URI = context.getString(R.string.xml_uri);

        Xml.parse(is, Xml.Encoding.UTF_8, new DebateFormatInfoContentHandler());
    }

    private class DebateFormatInfoContentHandler extends DefaultHandler {

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            if (localName.equals(mContext.getString(R.string.xml1elemName_root))) {
                String name = atts.getValue(DEBATING_TIMER_URI, mContext.getString(R.string.xml1attrName_root_name));
                if (name != null) mName = name;

                String schemaVersion = atts.getValue(DEBATING_TIMER_URI, mContext.getString(R.string.xml1attrName_root_schemaVersion));
                if (schemaVersion != null) mSchemaVersion = schemaVersion;
            }

        }
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getDescription()
     */
    @Override
    public String getDescription() {
        return "";
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getLevels()
     */
    @Override
    public ArrayList<String> getLevels() {
        return new ArrayList<>();
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getName()
     */
    @Override
    public String getName() {
        return mName;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getPrepTimeDescription()
     */
    @Override
    public String getPrepTimeDescription() {
        return null;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getRegions()
     */
    @Override
    public ArrayList<String> getRegions() {
        return new ArrayList<>();
    }

    /**
     * @return the schemaVersion
     */
    @Override
    public String getSchemaVersion() {
        return mSchemaVersion;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getSpeeches()
     */
    @Override
    public ArrayList<String[]> getSpeeches(ArrayList<String[]> descriptions) {
        return new ArrayList<>();
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getSpeechFormatDescriptions()
     */
    @Override
    public ArrayList<String[]> getSpeechFormatDescriptions() { return new ArrayList<>(); }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getUsedAts()
     */
    @Override
    public ArrayList<String> getUsedAts() {
        return new ArrayList<>();
    }

    @Override
    public boolean isSchemaSupported() {
        if (mSchemaVersion == null)
            return false; // either not built, or if it was built then probably the wrong schema
        try {
            return (XmlUtilities.compareSchemaVersions(mSchemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0)
                    && (XmlUtilities.compareSchemaVersions(mSchemaVersion, MINIMUM_SCHEMA_VERSION) >= 0);
        } catch (IllegalSchemaVersionException e) {
            return false;
        }
    }

}
