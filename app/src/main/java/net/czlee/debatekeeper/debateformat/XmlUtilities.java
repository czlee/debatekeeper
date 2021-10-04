/*
 * Copyright (C) 2013 Chuan-Zheng Lee
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

import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.czlee.debatekeeper.R;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Provides convenience functions for dealing with XML files.
 * <p>
 * If constructed, it takes a {@link Resources} which it uses to retrieve resources.  Methods that
 * require these resources must be called from an instance of this class.  Other methods are static
 * (and obviously don't require an instance).
 *
 * @author Chuan-Zheng Lee
 * @since 2013-06-04
 */
public class XmlUtilities {

    @NonNull
    private final Resources mResources;
    @NonNull
    private final LanguageChooser mLangChooser = new LanguageChooser();
    @Nullable
    private List<String> mDeclaredLanguages = null;

    XmlUtilities(@NonNull Resources resources) {
        mResources = resources;
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************
    static class XmlInvalidValueException extends Exception {

        private static final long serialVersionUID = 6918559345445076788L;
        private final String value;

        XmlInvalidValueException(String value) {
            super();
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

    public static class IllegalSchemaVersionException extends Exception {

        private static final long serialVersionUID = -5240666878173998127L;

        IllegalSchemaVersionException(String detailMessage) {
            super(detailMessage);
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Convenience function.  Finds the {@link Element} of the name given by a resource ID.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the {@link Element} if found, or <code>null</code> if no such element is found
     */
    Element findElement(Element element, int tagNameResId) {
        String elemName = getString(tagNameResId);
        List<Element> candidates = getChildElementsByTagName(element, elemName);
        if (candidates.isEmpty()) return null;
        return candidates.get(0);
    }

    /**
     * Convenience function.  Finds the text of the element of the name given by a resource ID.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or <code>null</code> if no such element is found (if multiple
     * elements are found it just returns the text of the first)
     */
    String findElementText(Element element, int tagNameResId) {
        element = findElement(element, tagNameResId);
        if (element == null) return null;
        else return element.getTextContent();
    }


    /**
     * Convenience function.  Finds the {@link Element} of the name given by a source ID, whose
     * {@code xml:lang} attribute is the language closest to the user.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the {@link Element} if found, or <code>null</code> if no such element is found
     */
    Element findLocalElement(Element element, int tagNameResId) {
        String elemName = getString(tagNameResId);
        List<Element> candidates = getChildElementsByTagName(element, elemName);
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        String langAttrName = getString(R.string.xml2attrName_language);
        HashMap<String, Element> langToCandidate = new HashMap<>();
        ArrayList<String> languagesSupported = new ArrayList<>();  // retain order information
        for (Element candidate : candidates) {
            String langAttr = candidate.getAttribute(langAttrName);  // 'xml:lang' attribute
            langToCandidate.put(langAttr, candidate);
            languagesSupported.add(langAttr);
        }

        List<String> languagesOrdered = getLanguageOrder(languagesSupported);
        String bestLang = mLangChooser.choose(languagesOrdered);
        return langToCandidate.get(bestLang);
    }

    /**
     * Convenience function.  Finds the text of the element of the name given by a resource ID.
     * Supports multiple elements with the same name but different "lang" attributes and selects the
     * most appropriate variant.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or <code>null</code> if no such element is found.
     */
    String findLocalElementText(Element element, int tagNameResId) {
        Element foundElement = findLocalElement(element, tagNameResId);
        if (foundElement == null) return null;
        else return foundElement.getTextContent();
    }

    /**
     * Convenience function.  Finds all {@link Element}s of the name given by a resource ID.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return a {@link List<Element>}
     */
    List<Element> findAllElements(@Nullable Element element, int tagNameResId) {
        String elemName = getString(tagNameResId);
        return getChildElementsByTagName(element, elemName);
    }

    /**
     * Convenience function.  Finds all {@link Element}s of the name given by a resource ID, and
     * returns a list of the text content of each of those elements.
     *
     * @param element      an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return a {@link List<String>} containing the text in each element found
     */
    ArrayList<String> findAllElementTexts(@Nullable Element element, int tagNameResId) {
        ArrayList<String> result = new ArrayList<>();
        if (element == null) return result; // empty list
        List<Element> children = findAllElements(element, tagNameResId);
        for (Element child : children)
            result.add(child.getTextContent());
        return result;
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID.
     *
     * @param element       an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a string, or <code>null</code> if the attribute does not have a specified value
     */
    String findAttributeText(Element element, int attrNameResId) {
        String attrName = getString(attrNameResId);
        if (!element.hasAttribute(attrName)) return null;
        else return element.getAttribute(attrName);
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID and
     * converts the time string to a number of seconds.
     *
     * @param element       an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a Long, or the <code>null</code> if the attribute does not have a specified value
     * @throws XmlInvalidValueException if the attribute text cannot be interpreted as a time
     */
    Long findAttributeAsTime(Element element, int attrNameResId) throws XmlInvalidValueException {
        String text = findAttributeText(element, attrNameResId);
        if (text == null) return null;
        long seconds;
        try {
            seconds = timeStr2Secs(text);
        } catch (NumberFormatException e) {
            throw new XmlInvalidValueException(text);
        }
        return seconds;
    }

    /**
     * Convenience function.  Finds the text of the attribute of the name given by a resource ID and
     * converts the time string to a number of seconds.
     *
     * @param element       an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return a Long, or the <code>null</code> if the attribute does not have a specified value
     * @throws XmlInvalidValueException if the attribute text cannot be interpreted as an integer
     */
    Integer findAttributeAsInteger(Element element, int attrNameResId) throws XmlInvalidValueException {
        String text = findAttributeText(element, attrNameResId);
        if (text == null) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new XmlInvalidValueException(text);
        }
    }

    /**
     * Convenience function.  Examines the attribute of the name given by a resource ID and
     * determines if it is "true" or "false".  Values are case-sensitive.
     *
     * @param element       an {@link Element}
     * @param attrNameResId a resource ID referring to a string
     * @return <code>true</code> if the attribute's value is "true", <code>false</code> if it is
     * "false" or isn't specified
     */
    boolean isAttributeTrue(Element element, int attrNameResId) throws XmlInvalidValueException {
        String text = findAttributeText(element, attrNameResId);
        if (text == null) return false;
        if (text.equals(getString(R.string.xml2attrValue_common_true))) return true;
        if (text.equals(getString(R.string.xml2attrValue_common_false))) return false;
        throw new XmlInvalidValueException(text);
    }

    /**
     * Converts a String in the format 00:00 to a long, being the number of seconds
     *
     * @param s the String
     * @return the total number of seconds (minutes + seconds * 60)
     * @throws NumberFormatException if the given value cannot be interpreted as a time
     */
    static long timeStr2Secs(String s) throws NumberFormatException {
        long seconds = 0;
        String[] parts = s.split(":", 2);
        switch (parts.length) {
            case 2:
                long minutes = Long.parseLong(parts[0]);
                seconds += minutes * 60;
                seconds += Long.parseLong(parts[1]);
                break;
            case 1:
                seconds = Long.parseLong(parts[0]);
                break;
            default:
                throw new NumberFormatException();
        }
        return seconds;
    }

    /**
     * @param a a schema version string
     * @param b a schema version string
     * @return 1 if a > b, 0 if a == b, 1 if a < b
     * @throws IllegalSchemaVersionException if the version could not be interpreted
     */
    public static int compareSchemaVersions(String a, String b) throws IllegalSchemaVersionException {
        int[] a_int = versionToIntArray(a);
        int[] b_int = versionToIntArray(b);
        int min_length = Math.min(a_int.length, b_int.length);
        for (int i = 0; i < min_length; i++) {
            if (a_int[i] > b_int[i]) return 1;
            if (a_int[i] < b_int[i]) return -1;
        }
        return 0;
    }

    /**
     * @param version a schema version string
     * @return <code>true</code> if the string is a valid version, <code>false</code> otherwise
     */
    static boolean isValidSchemaVersion(String version) {
        try {
            versionToIntArray(version);
            return true;
        } catch (IllegalSchemaVersionException e) {
            return false;
        }
    }

    /**
     * Set the declared languages referenced by these utilities. The "declared languages" are the
     * languages declared generally to be supported by the XML file that this object is parsing. The
     * languages should be in order of preference (of the XML file). If this is set, then methods of
     * this class that return localised elements will elevate these languages to the top of the
     * preference order. If this is set to <code>null</code>, it is ignored.
     *
     * @param declaredLanguages an array of BCP 47 language codes
     */
    public void setDeclaredLanguages(@Nullable List<String> declaredLanguages) {
        mDeclaredLanguages = declaredLanguages;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private String getString(int resId, Object... formatArgs) {
        return mResources.getString(resId, formatArgs);
    }

    /**
     * Sadly, {@link org.w3c.dom.Element#getElementsByTagName(String)} returns all descendants with
     * the matching tag name, not just the immediate children. This function instead returns only
     * the immediate children with the matching tag name, and is intended to be used instead of
     * {@link org.w3c.dom.Element#getElementsByTagName(String)}.
     *
     * @param element an {@link org.w3c.dom.Element} to search
     * @param name    the child element name to look for
     */
    private List<Element> getChildElementsByTagName(@Nullable Element element, String name) {
        ArrayList<Element> result = new ArrayList<>();
        if (element == null) return result;  // empty list
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            if (child.getNodeName().equals(name))
                result.add((Element) child);
        }
        return result;
    }

    /**
     * Reconciles the languages available for some resource with the declared languages of the XML
     * file, if present. If no declared languages are set, this just returns the input.
     *
     * The current implementation lists the languages in the declared languages first (in the same
     * order, if in the input), and then lists any other languages in the input.
     *
     * @param languages a list of BCP 47 language codes supported by some given resource
     * @return a compiled list of languages that can be passed to {@link LanguageChooser#choose},
     * that has the same elements as the input list (but possibly in a different order).
     */
    private List<String> getLanguageOrder(List<String> languages) {
        if (mDeclaredLanguages == null) return languages;
        ArrayList<String> result = new ArrayList<>();
        for (String lang : mDeclaredLanguages) {
            if (languages.contains(lang)) {
                result.add(lang);
                languages.remove(lang);
            }
        }
        result.addAll(languages);
        return result;
    }

    /**
     * @param version a schema version string
     * @return an integer array
     * @throws IllegalSchemaVersionException if the version could not be interpreted
     */
    private static int[] versionToIntArray(String version) throws IllegalSchemaVersionException {
        int[] result = new int[2];
        String[] parts = version.split("\\.", 2);
        if (parts.length != 2)
            throw new IllegalSchemaVersionException("version must be in the form 'a.b' where a and b are numbers");
        for (int i = 0; i < 2; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalSchemaVersionException("version must be in the form 'a.b' where a and b are numbers");
            }
        }
        return result;
    }

}
