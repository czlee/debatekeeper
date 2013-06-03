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

package net.czlee.debatekeeper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.czlee.debatekeeper.debateformat.PeriodInfo;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * GlobalPeriodInfoRetriever retrieves {@link PeriodInfo} objects from appropriate locations.
 * This class is only relevant to debate formats using the 2.0 schema and above.
 * It is <b>not</b> relevant to debate formats using the 1.0/1.1 schemas.
 *
 * @author Chuan-Zheng Lee
 * @since 2013-02-24
 */
public class GlobalPeriodInfoRetriever {

    private final Context mContext;
    private final AssetManager mAssets;
    /**
     * The repository of global PeriodInfo objects
     */
    private final HashMap<String, PeriodInfo> mGlobalPeriodInfos = new HashMap<String, PeriodInfo>();

    private static final String GLOBAL_PERIODS_FILE = "periods.xml";

    public GlobalPeriodInfoRetriever(Context context) {
        mContext = context;
        mAssets = context.getAssets();
        parseGlobalPeriodInfos();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * @param ref a period type reference name
     * @return <code>true</code> if it is a global period, <code>false</code> otherwise
     */
    public boolean isGlobalPeriod(String ref) {
        return mGlobalPeriodInfos.containsKey(ref);
    }

    /**
     * @param ref a period type reference name
     * @return a {@link PeriodInfo} object being that period type
     */
    public PeriodInfo getGlobalPeriodInfo(String ref) {
        return mGlobalPeriodInfos.get(ref);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    /**
     * Opens the global period information file (assets/periods.xml) and uses it to populate
     * the repository.  Prints errors if they arise.
     */
    private void parseGlobalPeriodInfos() {

        // The reason these errors are all logged as wtfs is because the global periods file
        // is in assets - and so can't be touched by the user, or anyone, ever. So everything
        // in this method should always work.

        // Open the global periods file
        InputStream is;
        try {
            is = mAssets.open(GLOBAL_PERIODS_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Create a DocumentBuilder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return;
        }

        // Parse the file
        Document doc;
        try {
            doc = builder.parse(is);
        } catch (SAXException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Element root = doc.getDocumentElement();

        // Get all <period-type> elements
        NodeList periodNodes = root.getElementsByTagName(mContext.getString(R.string.xml2elemName_periodType));

        // For each such element, add a PeriodInfo to the repository
        for (int i = 0; i < periodNodes.getLength(); i++) {
            Node node = periodNodes.item(i);
            PeriodInfo pi = createPeriodInfoFromElement(node);
            String reference = pi.getReference();
            if (reference != null)
                mGlobalPeriodInfos.put(reference, pi);
            else
                Log.e(this.getClass().getSimpleName(), "A period didn't have a reference");
        }

    }

    // TODO move this to a location where it can be accessed as a utility by any class.
    // Suggested approach is to create a class of static methods for parsing schema-2.0 elements,
    // called DebateElementFactoryForSchema2 or something.
    /**
     * @param node an {@link Element} representing a <period-type> element (for convenience
     * it can take a {@link Node} so long as it is an {@link Element}, it will do the class cast)
     * @return a fully-period {@link PeriodInfo} with the information in the element
     * @throws IllegalArgumentException if <code>node</code> is not an {@link Element} object
     */
    private PeriodInfo createPeriodInfoFromElement(Node node) {
        Element element = (Element) node;

        String ref, name, description, defaultBackgroundColorStr, poisAllowedStr;
        Integer defaultBackgroundColor = null;
        boolean poisAllowed;

        // Extract the relevant information
        ref         = findAttributeText(element, R.string.xml2attrName_common_ref);
        name        = findElementText(element, R.string.xml2elemName_periodType_name);
        description = findElementText(element, R.string.xml2elemName_periodType_display);

        // Parse the default background colour, if there is one
        defaultBackgroundColorStr = findElementText(element, R.string.xml2elemName_periodType_defaultBackgroundColor);
        if (defaultBackgroundColorStr != null) {
            if (defaultBackgroundColorStr.startsWith("#")) {
                try {
                    defaultBackgroundColor = new BigInteger(defaultBackgroundColorStr.substring(1), 16).intValue();
                } catch (NumberFormatException e) {
                    Log.e(this.getClass().getSimpleName(), "Invalid colour: " + defaultBackgroundColorStr);
                }
            } else {
                Log.e(this.getClass().getSimpleName(), "Invalid colour: " + defaultBackgroundColorStr);
            }
        }

        // Parse the "pois-allowed" attribute
        poisAllowedStr = findAttributeText(element, R.string.xml2attrName_periodType_poisAllowed);
        poisAllowed = poisAllowedStr.equalsIgnoreCase(mContext.getString(R.string.xml2attrValue_common_true));

        PeriodInfo pi = new PeriodInfo(ref, name, description, defaultBackgroundColor, poisAllowed);

        return pi;

    }

    /**
     * Convenience function.  Finds the text of the element referred to in the string in the
     * resources specified by a given resource ID.
     * @param element an {@link Element}
     * @param tagNameResId a resource ID referring to a string
     * @return the String if found, or <code>null</code> if no such element is found or if multiple
     * such elements are found
     */
    private String findElementText(Element element, int tagNameResId) {
        String elemName = mContext.getString(tagNameResId);
        NodeList candidates = element.getElementsByTagName(elemName);
        if (candidates.getLength() == 1)
            return candidates.item(0).getTextContent();
        else {
            Log.wtf(this.getClass().getSimpleName(), String.format("No or multiple elements of name %s found", elemName));
            return null;
        }
    }

    /**
     * Convenience function.  Finds the text of the attribute referred to in the string in the
     * resources specified by a given resource ID.
     * @param element an {@link Element}
     * @param attrNameResId a resource ID
     * @return a string, or the empty string if the attribute does not have a specified value
     */
    private String findAttributeText(Element element, int attrNameResId) {
        String attrName = mContext.getString(attrNameResId);
        return element.getAttribute(attrName);
    }

}
