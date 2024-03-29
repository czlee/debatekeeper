/*
 * Copyright (C) 2016-2021 Chuan-Zheng Lee, Frank Richter
 *
 * This file is part of the Debatekeeper app, which is licensed under the GNU
 * General Public Licence version 3 (GPLv3).  You can redistribute and/or modify
 * it under the terms of the GPLv3, and you must not use this file except in
 * compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;

import android.content.Context;
import android.content.res.Resources;
import android.util.Xml;

import androidx.annotation.Nullable;

import net.czlee.debatekeeper.R;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * DebateFormatFieldExtractor provides a method to extract a single field (and only one field) from
 * a given input stream. It must be tied to a context, so that it can access its resources. It
 * should be used when <strong>only</strong> one field is desired. Callers that wish to read other
 * things in the file should use another class designed to do more with the debate format file.
 *
 * @author Chuan-Zheng Lee
 * @since 2016-09-25
 */

public class DebateFormatFieldExtractor {

    private final String DEBATING_TIMER_URI;
    private final Resources mResources;
    private final String mFieldName;
    private String mFieldValue;
    private ArrayList<String> mLanguages;
    private HashMap<String, String> mCandidates;

    public DebateFormatFieldExtractor(Context context, int fieldNameResId) {
        mResources = context.getResources();
        DEBATING_TIMER_URI = mResources.getString(R.string.xml_uri);
        mFieldName = mResources.getString(fieldNameResId);
    }

    /**
     * Parses the XML file to retrieve the value held in the requested field for the given input
     * stream.
     *
     * @param is an {@link InputStream}
     * @return the name of the style, e.g. "British Parliamentary", or null if the file is not a
     * valid debate format XML file.
     * @throws IOException if thrown by {@link Xml#parse(InputStream, Xml.Encoding, ContentHandler)}
     * @throws SAXException if thrown by {@link Xml#parse(InputStream, Xml.Encoding, ContentHandler)}
     */
    @Nullable
    public String getFieldValue(InputStream is) throws IOException, SAXException {
        mFieldValue = null;

        try {
            Xml.parse(is, Xml.Encoding.UTF_8, new GetDebateFormatNameXmlContentHandler());
        } catch (AllInformationFoundException e) {
            return mFieldValue;
        } catch (AllLanguagesFoundException e) {
            LanguageChooser languageChooser = new LanguageChooser();
            // Choose appropriate name language
            String bestLang = languageChooser.choose(mLanguages);
            // Map back to the matching name
            mFieldValue = mCandidates.get(bestLang);
            return mFieldValue;
        }

        return null;
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private static class AllInformationFoundException extends SAXException {
        private static final long serialVersionUID = 3195935815375118010L;
    }

    private static class AllLanguagesFoundException extends SAXException {}

    /**
     * This class just looks for first &lt;name&gt; element and saves its contents to
     * <code>mFieldValue</code>.
     */
    private class GetDebateFormatNameXmlContentHandler extends DefaultHandler {

        private StringBuilder mFieldValueBuffer = null;
        private String mCurrentLang = null;

        @Override
        public void characters(char[] ch, int start, int length) {
            if (mFieldValueBuffer == null) return;
            String str = new String(ch, start, length);
            mFieldValueBuffer = mFieldValueBuffer.append(str);
        }

        @Override
        public void endDocument() throws SAXException {
            throw new AllLanguagesFoundException();
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (mFieldValueBuffer != null) {
                if(!mCandidates.containsKey(mCurrentLang)) {
                    mLanguages.add(mCurrentLang);
                    mCandidates.put(mCurrentLang, mFieldValueBuffer.toString());
                }
                mFieldValueBuffer = null;
            }
        }

        @Override
        public void startDocument() {
            // initialise
            mFieldValue = null;
            mCandidates = new HashMap<>();
            mLanguages = new ArrayList<>();
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) throws SAXException {

            if (!uri.equals(DEBATING_TIMER_URI))
                return;

            // Track all elements of the root, stored by language
            if (localName.equals(mFieldName)) {
                mFieldValueBuffer = new StringBuilder();
                mCurrentLang = atts.getValue(mResources.getString(R.string.xml2attrName_language));
                if (mCurrentLang == null) mCurrentLang = "";
            } else if (!mCandidates.isEmpty())
                // We expect all relevant fields to be next to each other, so once we hit a
                // different element, we're done.
                throw new AllLanguagesFoundException();
        }
    }

}
