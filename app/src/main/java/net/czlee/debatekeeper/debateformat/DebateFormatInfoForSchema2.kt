/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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
import android.util.Log
import com.ibm.icu.util.ULocale
import net.czlee.debatekeeper.DebatekeeperUtils
import net.czlee.debatekeeper.R
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * A class that retrieves relevant information from a given debate format XML file.
 * This class does not validate the XML file against a schema, and does not raise any
 * errors if fields are invalid or anything like that - it just returns sensible
 * "empty" values.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-06-05
 */
class DebateFormatInfoForSchema2 @Throws(IOException::class, SAXException::class) constructor(
        private val mContext: Context, inputStream: InputStream) : DebateFormatInfo {

    private val xu = XmlUtilities(mContext.resources)
    private var mRootElement: Element? = null
    private var mInfoElement: Element? = null // keep <info> readily accessible for performance
    private var mDeclaredLanguages: ArrayList<String>? = null

    init {
        val doc = getDocumentFromInputStream(inputStream)

        val rootElement = doc?.documentElement
        mRootElement = rootElement

        if (rootElement != null) {
            val languagesRoot = xu.findElement(rootElement, R.string.xml2elemName_languages)
            if (languagesRoot != null) {
                val declaredLanguages = xu.findAllElementTexts(languagesRoot,
                        R.string.xml2elemName_languages_language)
                mDeclaredLanguages = declaredLanguages
                xu.setDeclaredLanguages(declaredLanguages)
            }

            mInfoElement = xu.findLocalElement(rootElement, R.string.xml2elemName_info)
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override val name: String
        get() {
            val rootElement = mRootElement ?: return ""
            return xu.findLocalElementText(rootElement, R.string.xml2elemName_name) ?: ""
        }

    override val description: String
        get() {
            val infoElement = mInfoElement ?: return "-"
            return xu.findLocalElementText(infoElement, R.string.xml2elemName_info_desc) ?: "-"
        }

    override val regions: List<String>
        get() = xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_region)

    override val levels: List<String>
        get() = xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_level)

    override val usedAts: List<String>
        get() = xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_usedAt)

    override val displayLanguages: List<String>
        get() {
            val declaredLanguages = mDeclaredLanguages ?: return ArrayList()
            val languages = ArrayList<String>()
            for (code in declaredLanguages) {
                val locale = ULocale(code)
                val language: String? = locale.displayLanguage
                if (language != null) languages.add(language)
            }
            return languages
        }

    override val prepTimeDescription: String?
        get() {
            val rootElement = mRootElement ?: return null

            val prepTimeSimple =
                    xu.findElement(rootElement, R.string.xml2elemName_prepTimeSimpleFormat)
            val prepTimeControlled =
                    xu.findElement(rootElement, R.string.xml2elemName_prepTimeControlledFormat)

            if (prepTimeSimple != null && prepTimeControlled == null) { // simple
                val length = try {
                    xu.findAttributeAsTime(prepTimeSimple,
                            R.string.xml2attrName_controlledTimeLength)
                } catch (e: XmlInvalidValueException) {
                    return null
                } ?: return null
                return buildLengthString(length)

            } else if (prepTimeControlled != null && prepTimeSimple == null) { // controlled
                val length = try {
                    xu.findAttributeAsTime(prepTimeControlled,
                            R.string.xml2attrName_controlledTimeLength)
                } catch (e: XmlInvalidValueException) {
                    return null
                } ?: return null
                var description = buildLengthString(length)

                description += mContext.getString(
                        R.string.viewFormat_timeDescription_controlledPrepSuffix)

                val bells = xu.findAllElements(prepTimeControlled, R.string.xml2elemName_bell)
                description += "\n" + buildBellsString(bells, length)

                return description
            }
            // If they both exist, or if neither exist, return null
            return null
        }

    override val schemaVersion: String?
        get() {
            val rootElement = mRootElement ?: return null
            return xu.findAttributeText(rootElement, R.string.xml2attrName_root_schemaVersion)
        }

    override val speechFormatDescriptions: List<Array<String>>
        get() {
            val result = ArrayList<Array<String>>()

            val rootElement = mRootElement ?: return result
            val speechFormatsElement =
                    xu.findElement(rootElement, R.string.xml2elemName_speechFormats)
                            ?: return result
            val speechFormats = xu.findAllElements(speechFormatsElement,
                    R.string.xml2elemName_speechFormat)

            for (element in speechFormats) {
                val reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref)
                        ?: continue

                val speechName =
                        xu.findLocalElementText(element, R.string.xml2elemName_name) ?: reference

                val length = try {
                    xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength)
                } catch (e: XmlInvalidValueException) {
                    continue
                } ?: continue
                var description = buildLengthString(length)

                val bells = xu.findAllElements(element, R.string.xml2elemName_bell)
                description += "\n" + buildBellsString(bells, length)

                result.add(arrayOf(speechName, description, reference))
            }

            return result
        }

    override fun getSpeeches(descriptions: List<Array<String>>): List<Array<String>> {
        val result = ArrayList<Array<String>>()

        val rootElement = mRootElement ?: return result
        val speechesElement = xu.findElement(rootElement, R.string.xml2elemName_speechesList)
                ?: return result
        val speechFormats = xu.findAllElements(speechesElement, R.string.xml2elemName_speech)

        // Build a map from format string to description
        val refToDescr = HashMap<String, String>()
        for (description in descriptions) {
            val descr = description[0]
            val ref = description[2]
            refToDescr[ref] = descr
        }

        for (element in speechFormats) {
            val name = xu.findLocalElementText(element, R.string.xml2elemName_speech_name)
                    ?: continue

            val format = xu.findAttributeText(element, R.string.xml2attrName_speech_format)
                    ?: continue

            val descr = refToDescr[format] ?: format

            result.add(arrayOf(name, descr))
        }

        return result
    }

    override val isSchemaSupported: Boolean
        get() {
            val schemaVersion = this.schemaVersion
                    ?: return false // either not built, or if it was built then probably the wrong schema
            return try {
                XmlUtilities.compareSchemaVersions(schemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0
                        && XmlUtilities.compareSchemaVersions(schemaVersion, MINIMUM_SCHEMA_VERSION) >= 0
            } catch (e: IllegalSchemaVersionException) {
                false
            }
        }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    @Throws(SAXException::class, IOException::class)
    private fun getDocumentFromInputStream(inputStream: InputStream): Document? {
        val builder = try {
            // There should never be a problem creating this DocumentBuilder, ever.
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            e.printStackTrace()
            Log.wtf(TAG, "Error creating document builder")
            // After this, the app is pretty much guaranteed to crash.
            return null
        }

        return builder.parse(inputStream)
    }

    /**
     * Builds a string describing a list of bells
     * @param list a list of `<bell>` [Element]s
     * @return the completed string e.g. "bells at 1:00, 6:00, 7:00"
     */
    private fun buildBellsString(list: List<Element>, finishTime: Long): String {
        val bellsList = StringBuilder()

        for (i in list.indices) {
            val element = list[i]
            val timeStr = xu.findAttributeText(element, R.string.xml2attrName_bell_time)
                    ?: continue
            val time: Long
            if (timeStr == mContext.getString(R.string.xml2attrValue_bell_time_finish))
                time = finishTime
            else {
                try {
                    time = XmlUtilities.timeStr2Secs(timeStr)
                } catch (e: NumberFormatException) {
                    continue // if we couldn't interpret the time, ignore it
                }
            }
            bellsList.append(DebatekeeperUtils.secsToTextSigned(time))
            val pauseOnBell = try {
                xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell)
            } catch (e: XmlInvalidValueException) {
                false
            }
            if (pauseOnBell)
                bellsList.append(mContext.getString(R.string.timer_pauseOnBellIndicator))

            // If there's one after this, add a comma
            if (i < list.size - 1) bellsList.append(", ")
        }

        return mContext.resources.getQuantityString(
                R.plurals.viewFormat_timeDescription_bellsList, list.size, bellsList)
    }

    private fun buildLengthString(length: Long): String {
        return if (length % 60 == 0L) {
            val minutes = length / 60
            mContext.resources.getQuantityString(
                    R.plurals.viewFormat_timeDescription_lengthInMinutesOnly, minutes.toInt(), minutes)
        } else
            mContext.getString(R.string.viewFormat_timeDescription_lengthInMinutesSeconds,
                    DebatekeeperUtils.secsToTextSigned(length))
    }

    companion object {
        private const val TAG = "DebateFormatInfoForSchema2"
        private const val MINIMUM_SCHEMA_VERSION = "2.0"
        private const val MAXIMUM_SCHEMA_VERSION = "2.2"
    }
}
