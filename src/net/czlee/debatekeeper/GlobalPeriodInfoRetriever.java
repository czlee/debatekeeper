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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
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
    private final HashMap<String, PeriodInfo> mGlobalPeriodInfos;
    private final XPath mXPath;

    private static final String GLOBAL_PERIODS_FILE = "periods.xml";

    public GlobalPeriodInfoRetriever(Context context) {
        mContext = context;
        mAssets = context.getAssets();
        mGlobalPeriodInfos = parseGlobalPeriodInfos();
        mXPath = XPathFactory.newInstance().newXPath();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public boolean isGlobalPeriod(String ref) {
        return mGlobalPeriodInfos.containsKey(ref);
    }

    public PeriodInfo getGlobalPeriodInfo(String ref) {
        return mGlobalPeriodInfos.get(ref);
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private HashMap<String, PeriodInfo> parseGlobalPeriodInfos() {

        // The reason these errors are all logged as wtfs is because the global periods file
        // is in assets - and so can't be touched by the user, or anyone, ever. So everything
        // in this method should always work.

        // Open the global periods file
        InputStream is;
        try {
            is = mAssets.open(GLOBAL_PERIODS_FILE);
        } catch (IOException e) {
            // return empty map
            Log.wtf(this.getClass().getSimpleName(), "Could not open global periods files");
            return new HashMap<String, PeriodInfo>();
        }

        // Create a DocumentBuilder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // return empty map
            Log.wtf(this.getClass().getSimpleName(), "Could not create document builder");
            return new HashMap<String, PeriodInfo>();
        }

        // Parse the file
        Document doc;
        try {
            doc = builder.parse(is);
        } catch (SAXException e) {
            // return empty map
            Log.wtf(this.getClass().getSimpleName(), "Error parsing global periods XML file");
            return new HashMap<String, PeriodInfo>();
        } catch (IOException e) {
            // return empty map
            Log.wtf(this.getClass().getSimpleName(), "Error opening global periods XML file");
            return new HashMap<String, PeriodInfo>();
        }

        String expression = "/" + getString(R.string.xml2elemName_periodTypes) + "/" + getString(R.string.xml2elemName_periodType);
        NodeList periodNodes;
        try {
            periodNodes = (NodeList) mXPath.evaluate(expression, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            // return empty map
            Log.wtf(this.getClass().getSimpleName(), "Problem with xpath: " + expression);
            return new HashMap<String, PeriodInfo>();
        }

        for (int i = 0; i < periodNodes.getLength(); i++) {
            Node node = periodNodes.item(i);
            addPeriodInfoFromNode(node);
        }

        return null;
    }

    private void addPeriodInfoFromNode(Node node) {
        String ref, name, description, defaultBackgroundColorStr;
        Integer defaultBackgroundColor = null;
        boolean poisAllowed;

        name = findElementText(node, R.string.xml2elemName_periodType_name);
        description = findElementText(node, R.string.xml2elemName_periodType_display);
        defaultBackgroundColorStr = findElementText(node, R.string.xml2elemName_periodType_defaultBackgroundColor);

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

        String expression = "/" + getString(R.string.xml2elemName_periodType_poisAllowed);
        try {
            NodeList poisAllowedNodes = (NodeList) mXPath.evaluate(expression, node, XPathConstants.NODESET);
            poisAllowed = poisAllowedNodes.getLength() > 0;
        } catch (XPathExpressionException e) {
            Log.wtf(this.getClass().getSimpleName(), "Problem with xpath: " + expression);
            poisAllowed = false;
        }

        PeriodInfo pi = new PeriodInfo(description, defaultBackgroundColor, poisAllowed);
        pi.setName(name);


    }

    /**
     * Finds the text of the element referred to in the string in the resources specified by
     * a given resource ID.
     * @param node a {@link Node}
     * @param tagnameResId a resId referring to a string
     * @return the String if found, or <code>null</code> if no such element is found
     */
    private String findElementText(Node node, int tagnameResId) {
        String expression = "/" + getString(tagnameResId) + "/text()";
        try {
            return mXPath.evaluate(expression, node);
        } catch (XPathExpressionException e) {
            return null;
        }
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }

}
