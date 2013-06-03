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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.czlee.debatekeeper.R;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

/**
 * PeriodInfoManager retrieves {@link PeriodInfo} objects from appropriate locations.
 *
 * <p>This class is only relevant to debate formats using the 2.0 schema and above.
 * It is <b>not</b> relevant to debate formats using the 1.0/1.1 schemas.</p>
 *
 * @author Chuan-Zheng Lee
 * @since 2013-02-24
 */
public class PeriodInfoManager {

    private final Resources mResources;
    private final HashMap<String, PeriodInfo> mBuiltInPeriodInfos = new HashMap<String, PeriodInfo>();
    private final HashMap<String, PeriodInfo> mLocalPeriodInfos = new HashMap<String, PeriodInfo>();
    private final XmlUtilities xu;

    private static final String GLOBAL_PERIODS_FILE = "periods.xml";

    public PeriodInfoManager(Context context) {
        mResources = context.getResources();
        xu = new XmlUtilities(mResources);
        populateBuiltInPeriodInfos(context.getAssets());
    }

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Thrown if there is a problem with the debate format (e.g. duplicates or non-existent
     * references)
     */
    public class PeriodInfoException extends Exception {

        public PeriodInfoException(int resId) {
            super(mResources.getString(resId));
        }

        public PeriodInfoException(int resId, Object... formatArgs) {
            super(mResources.getString(resId, formatArgs));
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Adds a {@link PeriodInfo} based on an {@link Element} to the repository of local
     * period infos.  If the period info doesn't have a reference, it does not add the
     * {@link PeriodInfo}.
     * @param element the {@link Element} from which to create the period info
     * @throws PeriodInfoException if the period info would be a duplicate
     */
    public void addPeriodInfoFromElement(Element element) throws PeriodInfoException {
        PeriodInfo pi = createPeriodInfoFromElement(element);
        String reference = pi.getReference();
        if (reference == null) return;
        if (mBuiltInPeriodInfos.containsKey(reference))
            throw new PeriodInfoException(R.string.dfb2error_periodInfoBuiltInDuplicate, reference);
        if (mLocalPeriodInfos.containsKey(reference))
            throw new PeriodInfoException(R.string.dfb2error_periodInfoDuplicate, reference);
        mLocalPeriodInfos.put(reference, pi);
    }

    /**
     * @param ref a period type reference name
     * @return a {@link PeriodInfo} object being that period type, or <code>null</code> if no
     * such period exists
     */
    public PeriodInfo getPeriodInfo(String ref) {
        PeriodInfo result;
        // First try built-in
        result = mBuiltInPeriodInfos.get(ref);
        if (result != null) return result;
        // If not found then try local
        result = mLocalPeriodInfos.get(ref);
        return result; // just return, as if this is also null we will want to return null anyway
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    /**
     * Opens the global period information file (assets/periods.xml) and uses it to populate
     * the global period info repository.  Prints errors if they arise.
     */
    private void populateBuiltInPeriodInfos(AssetManager assets) {

        // The reason these errors are all logged as wtfs is because the global periods file
        // is in assets - and so can't be touched by the user, or anyone, ever. So everything
        // in this method should always work.

        // Open the global periods file
        InputStream is;
        try {
            is = assets.open(GLOBAL_PERIODS_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            Log.wtf(this.getClass().getSimpleName(), "Error opening global periods file");
            return;
        }

        // Create a DocumentBuilder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            Log.wtf(this.getClass().getSimpleName(), "Error creating document builder");
            return;
        }

        // Parse the file
        Document doc;
        try {
            doc = builder.parse(is);
        } catch (SAXException e) {
            Log.wtf(this.getClass().getSimpleName(), "SAX error parsing global periods file");
            e.printStackTrace();
            return;
        } catch (IOException e) {
            Log.wtf(this.getClass().getSimpleName(), "IO error parsing global periods file");
            e.printStackTrace();
            return;
        }
        Element root = doc.getDocumentElement();

        // Get all <period-type> elements
        NodeList periodTypeElements = xu.findAllElements(root, R.string.xml2elemName_periodType);

        // For each such element, add a PeriodInfo to the repository
        for (int i = 0; i < periodTypeElements.getLength(); i++) {
            Element periodType = (Element) periodTypeElements.item(i);
            PeriodInfo pi = createPeriodInfoFromElement(periodType);
            String reference = pi.getReference();
            if (reference != null)
                mBuiltInPeriodInfos.put(reference, pi);
            else
                Log.e(this.getClass().getSimpleName(), "A global period didn't have a reference");
        }

    }

    /**
     * @param node an {@link Element} representing a <period-type> element (for convenience
     * it can take a {@link Node} so long as it is an {@link Element}, it will do the class cast)
     * @return a fully-period {@link PeriodInfo} with the information in the element
     */
    private PeriodInfo createPeriodInfoFromElement(Element element) {

        String ref, name, description, defaultBackgroundColorStr, poisAllowedStr;
        Integer defaultBackgroundColor = null;
        boolean poisAllowed;

        // Extract the relevant information
        ref         = xu.findAttributeText(element, R.string.xml2attrName_common_ref);
        name        = xu.findElementText(element, R.string.xml2elemName_periodType_name);
        description = xu.findElementText(element, R.string.xml2elemName_periodType_display);

        // Parse the default background colour, if there is one
        defaultBackgroundColorStr = xu.findElementText(element, R.string.xml2elemName_periodType_defaultBackgroundColor);
        if (defaultBackgroundColorStr != null) {
            if (defaultBackgroundColorStr.startsWith("#")) {
                try {
                    defaultBackgroundColor = new BigInteger(defaultBackgroundColorStr.substring(1), 16).intValue();
                } catch (NumberFormatException e) {
                    Log.w(this.getClass().getSimpleName(), "Invalid colour: " + defaultBackgroundColorStr);
                }
            } else {
                Log.w(this.getClass().getSimpleName(), "Invalid colour: " + defaultBackgroundColorStr);
            }
        }

        // Parse the "pois-allowed" attribute
        poisAllowed = xu.isAttributeTrue(element, R.string.xml2attrName_periodType_poisAllowed);

        PeriodInfo pi = new PeriodInfo(ref, name, description, defaultBackgroundColor, poisAllowed);

        return pi;

    }

}
