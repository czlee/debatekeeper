/*
 * Copyright (C) 2016 Chuan-Zheng Lee
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

package net.czlee.debatekeeper.debateformat

import android.content.Context
import android.util.Xml
import net.czlee.debatekeeper.R
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream

/**
 * DebateFormatFieldExtractor provides a method to extract a single field (and only one field) from
 * a given input stream. It must be tied to a context, so that it can access its resources. It
 * should be used when **only** one field is desired. Callers that wish to read other
 * things in the file should use another class designed to do more with the debate format file.
 *
 * @author Chuan-Zheng Lee
 * @since 2016-09-25
 */
class DebateFormatFieldExtractor(context: Context, fieldNameResId: Int) {

    private val mResources = context.resources
    private val DEBATING_TIMER_URI: String = mResources.getString(R.string.xml_uri)
    private val mFieldName: String = mResources.getString(fieldNameResId)
    private var mFieldValue: String? = null
    private var mLanguages = ArrayList<String>()
    private var mCandidates = HashMap<String, String>()

    /**
     * Parses the XML file to retrieve the value held in the requested field for the given input
     * stream.
     *
     * @param inputStream an [InputStream]
     * @return the name of the style, e.g. "British Parliamentary", or null if the file is not a
     * valid debate format XML file.
     * @throws IOException if thrown by [Xml.parse]
     * @throws SAXException if thrown by [Xml.parse]
     */
    @Throws(IOException::class, SAXException::class)
    fun getFieldValue(inputStream: InputStream): String? {
        mFieldValue = null

        try {
            Xml.parse(inputStream, Xml.Encoding.UTF_8, GetDebateFormatNameXmlContentHandler())
        } catch (e: AllInformationFoundException) {
            return mFieldValue
        } catch (e: AllLanguagesFoundException) {
            val languageChooser = LanguageChooser()
            // Choose appropriate name language
            val bestLang = languageChooser.choose(mLanguages)
            // Map back to the matching name
            mFieldValue = mCandidates[bestLang]
            return mFieldValue
        }

        return null
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private class AllInformationFoundException : SAXException()

    private class AllLanguagesFoundException : SAXException()

    /**
     * This class just looks for first `<name>` element and saves its contents to
     * `mFieldValue`.
     */
    private inner class GetDebateFormatNameXmlContentHandler : DefaultHandler() {

        private var mFieldValueBuffer: StringBuilder? = null
        private var mCurrentLang: String? = null

        override fun characters(ch: CharArray, start: Int, length: Int) {
            val buffer = mFieldValueBuffer ?: return
            mFieldValueBuffer = buffer.append(String(ch, start, length))
        }

        @Throws(SAXException::class)
        override fun endDocument() {
            throw AllLanguagesFoundException()
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val buffer = mFieldValueBuffer
            if (buffer != null) {
                if (!mCandidates.containsKey(mCurrentLang)) {
                    mLanguages.add(mCurrentLang!!)
                    mCandidates[mCurrentLang!!] = buffer.toString()
                }
                mFieldValueBuffer = null
            }
        }

        override fun startDocument() {
            // initialise
            mFieldValue = null
            mCandidates = HashMap()
            mLanguages = ArrayList()
        }

        @Throws(SAXException::class)
        override fun startElement(uri: String, localName: String, qName: String,
                                  atts: Attributes) {
            if (uri != DEBATING_TIMER_URI)
                return

            // Track all elements of the root, stored by language
            if (localName == mFieldName) {
                mFieldValueBuffer = StringBuilder()
                mCurrentLang = atts.getValue(mResources.getString(R.string.xml2attrName_language))
                        ?: ""
            } else if (mCandidates.isNotEmpty()) {
                // We expect all relevant fields to be next to each other, so once we hit a
                // different element, we're done.
                throw AllLanguagesFoundException()
            }
        }
    }
}
