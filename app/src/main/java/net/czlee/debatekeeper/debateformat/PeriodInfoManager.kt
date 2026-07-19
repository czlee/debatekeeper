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
import android.content.res.AssetManager
import android.util.Log
import net.czlee.debatekeeper.R
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.math.BigInteger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * PeriodInfoManager retrieves [PeriodInfo] objects from appropriate locations.
 *
 * This class is only relevant to debate formats using the 2.0 schema and above.
 * It is **not** relevant to debate formats using the 1.0/1.1 schemas.
 *
 * @author Chuan-Zheng Lee
 * @since 2013-02-24
 */
class PeriodInfoManager(context: Context, private val xu: XmlUtilities) {

    private val mResources = context.resources
    private val mLastElementErrors = ArrayList<String>()
    private val mBuiltInPeriodInfos = HashMap<String, PeriodInfo>()
    private val mLocalPeriodInfos = HashMap<String, PeriodInfo>()

    init {
        populateBuiltInPeriodInfos(context.assets)
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Thrown if there is a problem with the debate format (e.g. duplicates or non-existent
     * references)
     */
    inner class PeriodInfoException(resId: Int, vararg formatArgs: Any) :
            Exception(mResources.getString(resId, *formatArgs))

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a [PeriodInfo] based on an [Element] to the repository of local
     * [PeriodInfo] objects.  This will still return without error if non-fatal errors
     * are encountered while adding the period.  The caller should call [lastElementErrors]
     * to check for non-fatal errors immediately after calling this method.
     * @param element the [Element] from which to create the period info
     * @throws PeriodInfoException if a fatal error was encountered adding the period, for example
     * if the period-type would be a duplicate, or if the period-type lacked a reference or name
     */
    @Throws(PeriodInfoException::class)
    fun addPeriodInfoFromElement(element: Element) {
        val pi = createPeriodInfoFromElement(element)
        val reference = pi.reference
        if (mBuiltInPeriodInfos.containsKey(reference))
            throw PeriodInfoException(R.string.dfb2error_periodInfo_builtInDuplicate, reference!!)
        if (mLocalPeriodInfos.containsKey(reference))
            throw PeriodInfoException(R.string.dfb2error_periodInfo_duplicate, reference!!)
        mLocalPeriodInfos[reference!!] = pi
    }

    /**
     * @param ref a period type reference name
     * @return a [PeriodInfo] object being that period type, or `null` if no such period exists
     */
    fun getPeriodInfo(ref: String): PeriodInfo? {
        // First try built-in; if not found then try local. If both null we will want to return
        // null anyway.
        return mBuiltInPeriodInfos[ref] ?: mLocalPeriodInfos[ref]
    }

    /**
     * Retrieves the list of parsing errors in processing the last element with
     * [addPeriodInfoFromElement].
     * @return an [ArrayList] of strings, each one being a parsing error message encountered
     * while parsing the last [Element] passed to [addPeriodInfoFromElement]
     */
    fun lastElementErrors(): ArrayList<String> {
        return mLastElementErrors
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Opens the global period information file (assets/periods.xml) and uses it to populate
     * the global period info repository.  Prints errors if they arise.
     */
    private fun populateBuiltInPeriodInfos(assets: AssetManager) {

        // The reason these errors are all logged as wtfs is because the global periods file
        // is in assets - and so can't be touched by the user, or anyone, ever. So everything
        // in this method should always work.

        // Open the global periods file
        val inputStream = try {
            assets.open(BUILT_IN_PERIODS_FILE)
        } catch (e: IOException) {
            e.printStackTrace()
            Log.wtf(TAG, "Error opening global periods file")
            return
        }

        // Create a DocumentBuilder
        val dbf = DocumentBuilderFactory.newInstance()
        val builder = try {
            dbf.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            e.printStackTrace()
            Log.wtf(TAG, "Error creating document builder")
            return
        }

        // Parse the file
        val doc = try {
            builder.parse(inputStream)
        } catch (e: SAXException) {
            Log.wtf(TAG, "SAX error parsing global periods file")
            e.printStackTrace()
            return
        } catch (e: IOException) {
            Log.wtf(TAG, "IO error parsing global periods file")
            e.printStackTrace()
            return
        }
        val root = doc.documentElement

        // Get all <period-type> elements
        val periodTypeElements = xu.findAllElements(root, R.string.xml2elemName_periodType)

        // For each such element, add a PeriodInfo to the repository
        for (periodType in periodTypeElements) {
            val pi = try {
                createPeriodInfoFromElement(periodType)
            } catch (e: PeriodInfoException) {
                // this should never happen
                Log.e(TAG, e.localizedMessage!!)
                continue
            }

            val reference = pi.reference
            if (reference != null)
                mBuiltInPeriodInfos[reference] = pi
            else
                Log.e(TAG, "A global period didn't have a reference")
        }
    }

    /**
     * Creates a [PeriodInfo] object from an [Element], storing any non-fatal parsing
     * errors to mLastElementErrors for retrieval via [lastElementErrors].  This checks
     * for XML format errors, but does not check for duplicate period-type references; the
     * caller must check that if required.  If this method returns without throwing a
     * [PeriodInfoException], then the reference of the [PeriodInfo] is guaranteed not to be
     * `null`.
     * @param element an [Element] representing a `<period-type>` element
     * @return a fully-populated [PeriodInfo] with the information in the element
     * @throws PeriodInfoException if there was a fatal parsing error (*e.g.* the period-type
     * had no reference).
     */
    @Throws(PeriodInfoException::class)
    private fun createPeriodInfoFromElement(element: Element): PeriodInfo {

        var defaultBackgroundColor: Int? = null
        var poisAllowed = false

        // Clear the last element errors log
        mLastElementErrors.clear()

        // Extract the reference and check for validity
        // We enforce restrictions against blank references and names because these have to
        // work with the app globally, i.e. the effects aren't constrained to the file in which
        // they are found.
        val ref = xu.findAttributeText(element, R.string.xml2attrName_common_ref)
                ?: throw PeriodInfoException(R.string.xml2error_periodType_ref_null)
        if (ref.isEmpty())
            throw PeriodInfoException(R.string.xml2error_periodType_ref_blank)

        // Extract the name and check for validity
        val name = xu.findLocalElementText(element, R.string.xml2elemName_periodType_name)
                ?: throw PeriodInfoException(R.string.xml2error_periodType_name_null, ref)
        if (name.isEmpty())
            throw PeriodInfoException(R.string.xml2error_periodType_name_blank, ref)

        // Extract the description (there are no constraints on this field)
        val description = xu.findLocalElementText(element, R.string.xml2elemName_periodType_display)

        // Parse the default background colour, if there is one
        val defaultBackgroundColorStr =
                xu.findElementText(element, R.string.xml2elemName_periodType_defaultBackgroundColor)
        if (defaultBackgroundColorStr != null) {
            if (defaultBackgroundColorStr.startsWith("#")) {
                try {
                    defaultBackgroundColor =
                            BigInteger(defaultBackgroundColorStr.substring(1), 16).toInt()
                } catch (e: NumberFormatException) {
                    addError(R.string.xml2Error_periodType_defaultBgColor_invalid,
                            defaultBackgroundColorStr, ref)
                }
            } else {
                addError(R.string.xml2Error_periodType_defaultBgColor_invalid,
                        defaultBackgroundColorStr, ref)
            }
        }

        // Parse the "pois-allowed" attribute
        try {
            poisAllowed = xu.isAttributeTrue(element, R.string.xml2attrName_periodType_poisAllowed)
        } catch (e: XmlInvalidValueException) {
            addError(R.string.xml2Error_periodType_poisAllowed_invalid, e.value)
        }

        return PeriodInfo(ref, name, description, defaultBackgroundColor, poisAllowed)
    }

    private fun addError(resId: Int, vararg formatArgs: Any) {
        mLastElementErrors.add(mResources.getString(resId, *formatArgs))
    }

    companion object {
        private const val TAG = "PeriodInfoManager"
        private const val BUILT_IN_PERIODS_FILE = "periods.xml"
    }
}
