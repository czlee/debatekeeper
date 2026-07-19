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
import net.czlee.debatekeeper.R
import net.czlee.debatekeeper.debateformat.DebateFormat.NoSuchFormatException
import net.czlee.debatekeeper.debateformat.PeriodInfoManager.PeriodInfoException
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
 * DebateFormatBuilderForSchema2 provides mechanisms for building DebateFormats.
 *
 * While "schema 2" refers to the XML schema, this class does not actually handle the XML.
 * It merely provides building methods for other classes to use to construct a [DebateFormat].
 * The salient features of schema 2, that are not true for schema 1, are:
 *
 *  - "Resources" do not exist in schema 2 (they did in schema 1).
 *  - Most formats in schema 2 will just use the global period types; for schema 1 all
 *    period types had to be defined locally.
 *
 * It is expected that other classes will be used to interface between a means of storing
 * information about debate formats, and this class.
 *
 * Because there is no longer any concept of "resources", there is nothing that a builder needs
 * to implement that isn't already taken care of by [DebateFormat].  Therefore, unlike for
 * schema 1, this builder creates formats directly from XML files, without an intermediate class
 * to provide mechanisms for handling resources.  It does, however, have to handle global
 * period types, which it delegates to [PeriodInfoManager].
 *
 * @author Chuan-Zheng Lee
 */
class DebateFormatBuilderFromXmlForSchema2(private val mContext: Context) :
        DebateFormatBuilderFromXml {

    private val mDocumentBuilderFactory: DocumentBuilderFactory =
            DocumentBuilderFactory.newInstance()
    private val xu = XmlUtilities(mContext.resources)
    private val mPeriodInfoManager = PeriodInfoManager(mContext, xu)
    private val mErrorLog = ArrayList<String>()

    override var schemaVersion: String? = null
        private set

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Throws(SAXException::class, IOException::class)
    override fun buildDebateFromXml(inputStream: InputStream): DebateFormat {
        val df = DebateFormat()
        val doc = getDocumentFromInputStream(inputStream)!!
        val root = doc.documentElement

        // 0. Schema version
        val schemaVersion = xu.findAttributeText(root, R.string.xml2attrName_root_schemaVersion)
        this.schemaVersion = schemaVersion

        if (schemaVersion == null)
            logXmlError(R.string.xmlError_rootNoSchemaVersion)
        else if (!XmlUtilities.isValidSchemaVersion(schemaVersion))
            logXmlError(R.string.xmlError_rootInvalidSchemaVersion, schemaVersion)
        // If the schema is too new, just keep going, the file might still work

        // 0.1. Set up declared languages from <languages>
        val languages = xu.findElement(root, R.string.xml2elemName_languages)
        if (languages != null) {
            val declaredLanguages =
                    xu.findAllElementTexts(languages, R.string.xml2elemName_languages_language)
            xu.setDeclaredLanguages(declaredLanguages)
        }

        // 1. <name> - mandatory, <short-name> - optional
        var name = xu.findLocalElementText(root, R.string.xml2elemName_name)

        if (name == null) {
            logXmlError(R.string.xml2error_root_noName)
            name = "<not named>"
        }

        df.name = name // do this even if there was an error with the name

        val shortName = xu.findLocalElementText(root, R.string.xml2elemName_shortName)
        if (shortName != null) df.shortName = shortName

        // 2. If there are <period-type>s in this format, deal with them first.  We'll need to
        // store them somewhere useful in the meantime.
        val periodTypes = xu.findElement(root, R.string.xml2elemName_periodTypes)
        if (periodTypes != null) {
            val periodTypeElements =
                    xu.findAllElements(periodTypes, R.string.xml2elemName_periodType)
            for (periodType in periodTypeElements) {
                try {
                    mPeriodInfoManager.addPeriodInfoFromElement(periodType)
                } catch (e: PeriodInfoException) {
                    logXmlError(e)
                }
                // Check for and log non-fatal errors, if any
                for (s in mPeriodInfoManager.lastElementErrors()) logXmlError(s)
            }
        }

        // 3. <prep-time> or <prep-time-controlled> - optional, only one allowed
        val prepTimeSimple = xu.findElement(root, R.string.xml2elemName_prepTimeSimpleFormat)
        val prepTimeControlled =
                xu.findElement(root, R.string.xml2elemName_prepTimeControlledFormat)

        if (prepTimeSimple != null && prepTimeControlled != null) {
            logXmlError(R.string.xml2error_prepTime_multiple)
        } else if (prepTimeSimple != null) {
            val ptsf = createPrepTimeSimpleFormatFromElement(prepTimeSimple)
            if (ptsf != null) df.setPrepFormat(ptsf)
        } else if (prepTimeControlled != null) {
            val ptcf = createPrepTimeControlledFormatFromElement(prepTimeControlled)
            if (ptcf != null) df.setPrepFormat(ptcf)
        }

        // 4. <speech-types>/<speech-type> (speech formats)
        // we can't do anything if there aren't any speech formats, so just return
        val speechFormats = xu.findElement(root, R.string.xml2elemName_speechFormats)
                ?: return df

        val speechFormatElements =
                xu.findAllElements(speechFormats, R.string.xml2elemName_speechFormat)
        for (speechFormatElement in speechFormatElements) {
            val sf = createSpeechFormatFromElement(speechFormatElement) ?: continue
            val reference = sf.reference!!

            if (df.hasSpeechFormat(reference)) {
                logXmlError(R.string.dfb2error_speechFormat_duplicate, reference)
                continue
            }

            df.addSpeechFormat(reference, sf)
        }

        // 5. <speeches>/<speech>
        // we can't do anything if there aren't any speeches, so just return
        val speechesList = xu.findElement(root, R.string.xml2elemName_speechesList)
                ?: return df

        val speechElements = xu.findAllElements(speechesList, R.string.xml2elemName_speech)
        for (speechElement in speechElements) {
            val speechName =
                    xu.findLocalElementText(speechElement, R.string.xml2elemName_speech_name)
            val formatRef =
                    xu.findAttributeText(speechElement, R.string.xml2attrName_speech_format)

            if (speechName == null) {
                logXmlError(R.string.xml2error_speech_name_null)
                continue
            }
            if (formatRef == null) {
                logXmlError(R.string.xml2error_speech_format_null, speechName)
                continue
            }

            try {
                df.addSpeech(speechName, formatRef)
            } catch (e: NoSuchFormatException) {
                logXmlError(R.string.dfb2error_addSpeech_speechFormatNotFound, formatRef, name)
            }
        }

        return df
    }

    override fun hasErrors(): Boolean {
        return mErrorLog.size > 0
    }

    override fun isSchemaOutdated(): Boolean {
        // either not built, or if it was built then probably the wrong schema
        val schemaVersion = this.schemaVersion ?: return false
        return try {
            XmlUtilities.compareSchemaVersions(schemaVersion, MINIMUM_SCHEMA_VERSION) < 0
        } catch (e: IllegalSchemaVersionException) {
            false
        }
    }

    override fun isSchemaTooNew(): Boolean {
        // either not built, or if it was built then probably the wrong schema
        val schemaVersion = this.schemaVersion ?: return false
        return try {
            XmlUtilities.compareSchemaVersions(schemaVersion, MAXIMUM_SCHEMA_VERSION) > 0
        } catch (e: IllegalSchemaVersionException) {
            false
        }
    }

    override val errorLog: List<String>
        get() = mErrorLog

    override val supportedSchemaVersion: String
        get() = MAXIMUM_SCHEMA_VERSION

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    @Throws(SAXException::class, IOException::class)
    private fun getDocumentFromInputStream(inputStream: InputStream): Document? {
        val builder = try {
            // There should never be a problem creating this DocumentBuilder, ever.
            mDocumentBuilderFactory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            e.printStackTrace()
            Log.wtf(TAG, "Error creating document builder")
            // After this, the app is pretty much guaranteed to crash.
            return null
        }

        // Parse the file
        return builder.parse(inputStream)
    }

    /**
     * Creates a [SpeechFormat] derived from an [Element]
     * @param element an [Element] object
     * @return a [SpeechFormat], may return `null` if there was an error preventing
     * the object from being created
     */
    private fun createSpeechFormatFromElement(element: Element): SpeechFormat? {

        // attribute 'ref' - mandatory
        val reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref)

        if (reference == null) {
            logXmlError(R.string.xml2error_speechFormat_ref_null)
            return null
        }
        if (reference.isEmpty()) {
            logXmlError(R.string.xml2error_speechFormat_ref_blank)
            return null
        }

        // attribute 'length' - mandatory
        val length = try {
            xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength)
        } catch (e: XmlInvalidValueException) {
            logXmlError(R.string.xml2error_speechFormat_length_invalid, e.value, reference)
            return null
        }
        if (length == null) {
            logXmlError(R.string.xml2error_speechFormat_length_null, reference)
            return null
        }

        val sf = SpeechFormat(reference, length)

        // for error messages
        val location = getString(R.string.xml2elemName_speechFormat) + " '" + reference + "'"

        populateControlledTimeFormat(sf, element, location)

        return sf
    }

    /**
     * Creates a [BellInfo] derived from an [Element]
     * @param element an [Element] object
     * @param finishTime the length of the speech, used if the bell is to be at the finish
     * time of the speech
     * @param location String describing where the bell was, used in error messages
     * @return a [BellInfo], may return `null` if there was an error preventing
     * the object from being created
     */
    private fun createBellInfoFromElement(element: Element, finishTime: Long, location: String): BellInfo? {

        // attribute 'time' - mandatory
        val timeStr = xu.findAttributeText(element, R.string.xml2attrName_bell_time)

        if (timeStr == null) {
            logXmlError(R.string.xml2error_bell_time_null)
            return null
        }

        val time: Long
        if (timeStr == getString(R.string.xml2attrValue_bell_time_finish))
            time = finishTime
        else {
            try {
                time = XmlUtilities.timeStr2Secs(timeStr)
            } catch (e: NumberFormatException) {
                logXmlError(R.string.xml2error_bell_time_invalid, timeStr, location)
                return null
            }
        }

        if (time > finishTime) {
            // not checked by schema
            logXmlError(R.string.dfb2error_bell_afterFinishTime, timeStr, location)
            return null
        }

        // attribute 'number' - optional, defaults to 1
        var numberOfBells: Int? = try {
            xu.findAttributeAsInteger(element, R.string.xml2attrName_bell_number)
        } catch (e: XmlInvalidValueException) {
            logXmlError(R.string.xml2error_bell_number_invalid, e.value, location, timeStr)
            1
        }
        if (numberOfBells == null) numberOfBells = 1

        val bi = BellInfo(time, numberOfBells)

        // attribute 'next-period' - optional
        // If there is a next period specified, and it is not "#stay", set it accordingly
        val nextPeriod = xu.findAttributeText(element, R.string.xml2attrName_bell_nextPeriod)
        if (nextPeriod != null) {
            if (nextPeriod != getString(R.string.xml2attrValue_common_stay)) {
                val npi = mPeriodInfoManager.getPeriodInfo(nextPeriod)
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfo_notFound, nextPeriod)
                else
                    bi.nextPeriodInfo = npi
            }
        }

        // attribute 'pause-on-bell' - optional
        val pauseOnBell = try {
            xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell)
        } catch (e: XmlInvalidValueException) {
            logXmlError(R.string.xml2error_bell_pauseOnBell_invalid, e.value, location, timeStr)
            false
        }
        bi.isPauseOnBell = pauseOnBell

        return bi
    }

    /**
     * Creates a [PrepTimeSimpleFormat] derived from an [Element]
     * @param element an [Element] object
     * @return a [PrepTimeSimpleFormat], may return `null` if there was an error preventing
     * the object from being created
     */
    private fun createPrepTimeSimpleFormatFromElement(element: Element): PrepTimeSimpleFormat? {
        val length = try {
            xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength)
        } catch (e: XmlInvalidValueException) {
            logXmlError(R.string.xml2error_prepTimeSimple_length_invalid, e.value)
            return null
        }
        if (length == null) {
            logXmlError(R.string.xml2error_prepTimeSimple_length_null)
            return null
        }

        return PrepTimeSimpleFormat(length)
    }

    /**
     * Creates a [PrepTimeControlledFormat] derived from an [Element]
     * @param element an [Element] object
     * @return a [PrepTimeControlledFormat], may return `null` if there was an error
     * preventing the object from being created
     */
    private fun createPrepTimeControlledFormatFromElement(element: Element): PrepTimeControlledFormat? {
        val length = try {
            xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength)
        } catch (e: XmlInvalidValueException) {
            logXmlError(R.string.xml2error_prepTimeControlled_length_invalid, e.value)
            return null
        }
        if (length == null) {
            logXmlError(R.string.xml2error_prepTimeControlled_length_null)
            return null
        }

        val ptcf = PrepTimeControlledFormat(length)

        val location = getString(R.string.xml2elemName_prepTimeControlledFormat) // for error messages
        populateControlledTimeFormat(ptcf, element, location)

        return ptcf
    }

    /**
     * Populates a [ControlledDebatePhaseFormat] with the first-period and the bells in
     * the [Element].  By the time this method is called, the [ControlledDebatePhaseFormat]
     * (more likely one of its subclasses) must already exist and have a length associated with it.
     * This method exists mainly to avoid duplicate code to handle the two subclasses of
     * [ControlledDebatePhaseFormat] ([SpeechFormat] and [PrepTimeControlledFormat]).
     * @param cdpf a [ControlledDebatePhaseFormat]
     * @param element the [Element]
     * @param location String describing the type of element this is, used in error messages
     */
    private fun populateControlledTimeFormat(cdpf: ControlledDebatePhaseFormat, element: Element, location: String) {

        // If there is a first period specified, and it is not "#stay", set it accordingly
        val firstPeriod =
                xu.findAttributeText(element, R.string.xml2attrName_controlledTimeFirstPeriod)
        if (firstPeriod != null) {
            if (firstPeriod != getString(R.string.xml2attrValue_common_stay)) {
                val npi = mPeriodInfoManager.getPeriodInfo(firstPeriod)
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfo_notFound, firstPeriod) // not checked by schema
                else
                    cdpf.firstPeriodInfo = npi
            }
        }

        val length = cdpf.length

        // Add all the bells
        val bellElements = xu.findAllElements(element, R.string.xml2elemName_bell)
        for (bellElement in bellElements) {
            val bi = createBellInfoFromElement(bellElement, length, location) ?: continue
            cdpf.addBellInfo(bi)
        }
    }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return mContext.getString(resId, *formatArgs)
    }

    // Error log methods

    /**
     * Logs an XML-related error from a string.
     * @param message the string
     */
    private fun logXmlError(message: String) {
        mErrorLog.add(message)
        Log.e("logXmlError(2)", message)
    }

    /**
     * Logs an XML-related error from an exception.
     * @param e the Exception
     */
    private fun logXmlError(e: Exception) {
        logXmlError(e.localizedMessage!!)
    }

    /**
     * Logs an XML-related error from a string resource.
     * @param resId the resource ID of the string resource
     */
    private fun logXmlError(resId: Int) {
        logXmlError(mContext.getString(resId))
    }

    /**
     * Logs an XML-related error from a string resource and formats according to
     * `String.format`
     * @param resId the resource ID of the string resource
     * @param formatArgs arguments to pass to `String.format`
     */
    private fun logXmlError(resId: Int, vararg formatArgs: Any) {
        logXmlError(mContext.getString(resId, *formatArgs))
    }

    companion object {
        private const val TAG = "DebateFormatBuilderFromXmlForSchema2"
        private const val MINIMUM_SCHEMA_VERSION = "2.0"
        private const val MAXIMUM_SCHEMA_VERSION = "2.2"
    }
}
