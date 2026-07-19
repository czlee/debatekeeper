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

import android.content.res.Resources
import net.czlee.debatekeeper.R
import org.w3c.dom.Element

/**
 * Provides convenience functions for dealing with XML files.
 *
 * If constructed, it takes a [Resources] which it uses to retrieve resources.  Methods that
 * require these resources must be called from an instance of this class.  Other methods are on
 * the companion object (and obviously don't require an instance).
 *
 * @author Chuan-Zheng Lee
 * @since 2013-06-04
 */
class XmlUtilities internal constructor(private val mResources: Resources) {

    private val mLangChooser = LanguageChooser()
    private var mDeclaredLanguages: MutableList<String>? = null

    class XmlInvalidValueException internal constructor(val value: String) : Exception()

    class IllegalSchemaVersionException internal constructor(detailMessage: String) :
            Exception(detailMessage)

    /**
     * Convenience function.  Finds the [Element] of the name given by a resource ID.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return the [Element] if found, or `null` if no such element is found
     */
    internal fun findElement(element: Element, tagNameResId: Int): Element? {
        val elemName = getString(tagNameResId)
        return getChildElementsByTagName(element, elemName).firstOrNull()
    }

    /**
     * Convenience function.  Finds the text of the element of the name given by a resource ID.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or `null` if no such element is found (if multiple
     * elements are found it just returns the text of the first)
     */
    internal fun findElementText(element: Element, tagNameResId: Int): String? {
        return findElement(element, tagNameResId)?.textContent
    }

    /**
     * Convenience function.  Finds the [Element] of the name given by a source ID, whose
     * `xml:lang` attribute is the language closest to the user.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return the [Element] if found, or `null` if no such element is found
     */
    internal fun findLocalElement(element: Element, tagNameResId: Int): Element? {
        val elemName = getString(tagNameResId)
        val candidates = getChildElementsByTagName(element, elemName)
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]

        val langAttrName = getString(R.string.xml2attrName_language)
        val langToCandidate = HashMap<String, Element>()
        val languagesSupported = ArrayList<String>() // retain order information
        for (candidate in candidates) {
            val langAttr = candidate.getAttribute(langAttrName) // 'xml:lang' attribute
            langToCandidate[langAttr] = candidate
            languagesSupported.add(langAttr)
        }

        val languagesOrdered = getLanguageOrder(languagesSupported)
        val bestLang = mLangChooser.choose(languagesOrdered)
        return langToCandidate[bestLang]
    }

    /**
     * Convenience function.  Finds the text of the element of the name given by a resource ID.
     * Supports multiple elements with the same name but different "lang" attributes and selects the
     * most appropriate variant.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or `null` if no such element is found.
     */
    internal fun findLocalElementText(element: Element, tagNameResId: Int): String? {
        return findLocalElement(element, tagNameResId)?.textContent
    }

    /**
     * Convenience function.  Finds all [Element]s of the name given by a resource ID.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return a [List] of [Element]s
     */
    internal fun findAllElements(element: Element?, tagNameResId: Int): List<Element> {
        val elemName = getString(tagNameResId)
        return getChildElementsByTagName(element, elemName)
    }

    /**
     * Convenience function.  Finds all [Element]s of the name given by a resource ID, and
     * returns a list of the text content of each of those elements.
     *
     * @param element      an [Element]
     * @param tagNameResId a resource ID referring to a string
     * @return an [ArrayList] containing the text in each element found
     */
    internal fun findAllElementTexts(element: Element?, tagNameResId: Int): ArrayList<String> {
        val result = ArrayList<String>()
        if (element == null) return result // empty list
        for (child in findAllElements(element, tagNameResId))
            result.add(child.textContent)
        return result
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID.
     *
     * @param element       an [Element]
     * @param attrNameResId a resource ID referring to a string
     * @return a string, or `null` if the attribute does not have a specified value
     */
    internal fun findAttributeText(element: Element, attrNameResId: Int): String? {
        val attrName = getString(attrNameResId)
        return if (!element.hasAttribute(attrName)) null else element.getAttribute(attrName)
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID and
     * converts the time string to a number of seconds.
     *
     * @param element       an [Element]
     * @param attrNameResId a resource ID referring to a string
     * @return a Long, or `null` if the attribute does not have a specified value
     * @throws XmlInvalidValueException if the attribute text cannot be interpreted as a time
     */
    @Throws(XmlInvalidValueException::class)
    internal fun findAttributeAsTime(element: Element, attrNameResId: Int): Long? {
        val text = findAttributeText(element, attrNameResId) ?: return null
        try {
            return timeStr2Secs(text)
        } catch (e: NumberFormatException) {
            throw XmlInvalidValueException(text)
        }
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID and
     * converts the string to an integer.
     *
     * @param element       an [Element]
     * @param attrNameResId a resource ID referring to a string
     * @return an Integer, or `null` if the attribute does not have a specified value
     * @throws XmlInvalidValueException if the attribute text cannot be interpreted as an integer
     */
    @Throws(XmlInvalidValueException::class)
    internal fun findAttributeAsInteger(element: Element, attrNameResId: Int): Int? {
        val text = findAttributeText(element, attrNameResId) ?: return null
        try {
            return text.toInt()
        } catch (e: NumberFormatException) {
            throw XmlInvalidValueException(text)
        }
    }

    /**
     * Convenience function.  Examines the attribute of the name given by a resource ID and
     * determines if it is "true" or "false".  Values are case-sensitive.
     *
     * @param element       an [Element]
     * @param attrNameResId a resource ID referring to a string
     * @return `true` if the attribute's value is "true", `false` if it is "false" or isn't
     * specified
     */
    @Throws(XmlInvalidValueException::class)
    internal fun isAttributeTrue(element: Element, attrNameResId: Int): Boolean {
        val text = findAttributeText(element, attrNameResId) ?: return false
        if (text == getString(R.string.xml2attrValue_common_true)) return true
        if (text == getString(R.string.xml2attrValue_common_false)) return false
        throw XmlInvalidValueException(text)
    }

    /**
     * Set the declared languages referenced by these utilities. The "declared languages" are the
     * languages declared generally to be supported by the XML file that this object is parsing. The
     * languages should be in order of preference (of the XML file). If this is set, then methods of
     * this class that return localised elements will elevate these languages to the top of the
     * preference order. If this is set to `null`, it is ignored.
     *
     * @param declaredLanguages an array of BCP 47 language codes
     */
    fun setDeclaredLanguages(declaredLanguages: List<String>?) {
        mDeclaredLanguages = declaredLanguages?.toMutableList()
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return mResources.getString(resId, *formatArgs)
    }

    /**
     * Sadly, [Element.getElementsByTagName] returns all descendants with the matching tag name,
     * not just the immediate children. This function instead returns only the immediate children
     * with the matching tag name, and is intended to be used instead of
     * [Element.getElementsByTagName].
     *
     * @param element an [Element] to search
     * @param name    the child element name to look for
     */
    private fun getChildElementsByTagName(element: Element?, name: String): List<Element> {
        val result = ArrayList<Element>()
        if (element == null) return result // empty list
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue
            if (child.nodeName == name)
                result.add(child)
        }
        return result
    }

    /**
     * Reconciles the languages available for some resource with the declared languages of the XML
     * file, if present. If no declared languages are set, this just returns the input.
     *
     * The current implementation lists the languages in the declared languages first (in the same
     * order, if in the input), and then lists any other languages in the input.
     *
     * @param languages a list of BCP 47 language codes supported by some given resource
     * @return a compiled list of languages that can be passed to [LanguageChooser.choose],
     * that has the same elements as the input list (but possibly in a different order).
     */
    private fun getLanguageOrder(languages: MutableList<String>): List<String> {
        val declaredLanguages = mDeclaredLanguages ?: return languages
        val result = ArrayList<String>()
        for (lang in declaredLanguages) {
            if (languages.contains(lang)) {
                result.add(lang)
                languages.remove(lang)
            }
        }
        result.addAll(languages)
        return result
    }

    companion object {

        /**
         * Converts a String in the format 00:00 to a long, being the number of seconds
         *
         * @param s the String
         * @return the total number of seconds (minutes + seconds * 60)
         * @throws NumberFormatException if the given value cannot be interpreted as a time
         */
        @JvmStatic
        @Throws(NumberFormatException::class)
        fun timeStr2Secs(s: String): Long {
            var seconds: Long = 0
            val parts = s.split(":".toRegex(), limit = 2).toTypedArray()
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLong()
                    seconds += minutes * 60
                    seconds += parts[1].toLong()
                }
                1 -> seconds = parts[0].toLong()
                else -> throw NumberFormatException()
            }
            return seconds
        }

        /**
         * @param a a schema version string
         * @param b a schema version string
         * @return 1 if a > b, 0 if a == b, -1 if a < b
         * @throws IllegalSchemaVersionException if the version could not be interpreted
         */
        @JvmStatic
        @Throws(IllegalSchemaVersionException::class)
        fun compareSchemaVersions(a: String, b: String): Int {
            val aInt = versionToIntArray(a)
            val bInt = versionToIntArray(b)
            val minLength = minOf(aInt.size, bInt.size)
            for (i in 0 until minLength) {
                if (aInt[i] > bInt[i]) return 1
                if (aInt[i] < bInt[i]) return -1
            }
            return 0
        }

        /**
         * @param version a schema version string
         * @return `true` if the string is a valid version, `false` otherwise
         */
        @JvmStatic
        fun isValidSchemaVersion(version: String): Boolean {
            return try {
                versionToIntArray(version)
                true
            } catch (e: IllegalSchemaVersionException) {
                false
            }
        }

        /**
         * @param version a schema version string
         * @return an integer array
         * @throws IllegalSchemaVersionException if the version could not be interpreted
         */
        @Throws(IllegalSchemaVersionException::class)
        private fun versionToIntArray(version: String): IntArray {
            val result = IntArray(2)
            val parts = version.split("\\.".toRegex(), limit = 2).toTypedArray()
            if (parts.size != 2)
                throw IllegalSchemaVersionException(
                        "version must be in the form 'a.b' where a and b are numbers")
            for (i in 0 until 2) {
                try {
                    result[i] = parts[i].toInt()
                } catch (e: NumberFormatException) {
                    throw IllegalSchemaVersionException(
                            "version must be in the form 'a.b' where a and b are numbers")
                }
            }
            return result
        }
    }
}
