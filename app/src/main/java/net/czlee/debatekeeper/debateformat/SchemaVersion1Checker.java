/*
 * Copyright (C) 2016 Chuan-Zheng Lee
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
import android.content.res.Resources;
import android.util.Xml;

import net.czlee.debatekeeper.R;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple class to check if a file looks like a schema version 1.0 file, i.e., if its root element
 * is <code>debateformat</code> and its version starts with "1." (1.0 or 1.1).
 */

public class SchemaVersion1Checker {

    private final String DEBATING_TIMER_URI;
    private final Resources mResources;
    private boolean mIsVersion1;

    public SchemaVersion1Checker(Context context) {
        mResources = context.getResources();
        DEBATING_TIMER_URI = mResources.getString(R.string.xml_uri);
    }

    /**
     * Convenience function, instantiates a SchemaVersion1Checker and uses it to check if the given
     * input stream looks like a schema 1.0 file.
     * @param context a context
     * @param is the input stream
     * @return <code>true</code> if it looks like a schema 1.0 file, <code>false</code> otherwise
     */
    public static boolean checkIfVersion1(Context context, InputStream is) throws IOException, SAXException {
        SchemaVersion1Checker sv1c = new SchemaVersion1Checker(context);
        return sv1c.checkIfVersion1(is);
    }

    /**
     * Parses the XML file to retrieve the name of the style for the given input stream.
     * @param is an {@link InputStream}
     * @return the name of the style, e.g. "British Parliamentary", or null if the file is not a
     * valid debate format XML file.
     *
     */
    public boolean checkIfVersion1(InputStream is) throws IOException, SAXException {
        mIsVersion1 = false;

        try {
            Xml.parse(is, Xml.Encoding.UTF_8, new CheckForOutdatedSchemaXMLContentHandler());
        } catch (CheckForOutdatedSchemaDoneException e) {
            return mIsVersion1;
        }

        return false;
    }
    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private static class CheckForOutdatedSchemaDoneException extends SAXException {
    }

    /**
     * XML handler that checks if the first element is "debateformat" and its version starts with
     * "1." (i.e. is 1.0 or 1.1).
     */
    private class CheckForOutdatedSchemaXMLContentHandler extends DefaultHandler {

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) throws SAXException {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            if (localName.equals(mResources.getString(R.string.xml1elemName_root))) {
                String version = atts.getValue(DEBATING_TIMER_URI,
                        mResources.getString(R.string.xml1attrName_root_schemaVersion));
                mIsVersion1 = version.startsWith("1.");
                throw new CheckForOutdatedSchemaDoneException();
            }
        }
    }
}
