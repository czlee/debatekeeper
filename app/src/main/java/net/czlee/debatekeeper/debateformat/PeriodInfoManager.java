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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

    private static final String TAG = "PeriodInfoManager";

    private final Resources mResources;
    private final ArrayList<String> mLastElementErrors = new ArrayList<>();
    private final HashMap<String, PeriodInfo> mBuiltInPeriodInfos = new HashMap<>();
    private final HashMap<String, PeriodInfo> mLocalPeriodInfos = new HashMap<>();
    private final XmlUtilities xu;

    private static final String BUILT_IN_PERIODS_FILE = "periods.xml";

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

        private static final long serialVersionUID = -6921860197911310673L;

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
     * {@link PeriodInfo} objects.  This will still return without error if non-fatal errors
     * are encountered while adding the period.  The caller should call <code>lastElementErrors()</code>
     * to check for non-fatal errors immediately after calling this method.
     * @param element the {@link Element} from which to create the period info
     * @throws PeriodInfoException if a fatal error was encountered adding the period, for example
     * if the period-type would be a duplicate, or if the period-type lacked a reference or name
     *
     */
    public void addPeriodInfoFromElement(Element element) throws PeriodInfoException {
        PeriodInfo pi = createPeriodInfoFromElement(element);
        String reference = pi.getReference();
        if (mBuiltInPeriodInfos.containsKey(reference))
            throw new PeriodInfoException(R.string.dfb2error_periodInfo_builtInDuplicate, reference);
        if (mLocalPeriodInfos.containsKey(reference))
            throw new PeriodInfoException(R.string.dfb2error_periodInfo_duplicate, reference);
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

    /**
     * Retrieves the list of parsing errors in processing the last element with <code>addPeriodInfoFromElement()</code>
     * @return an {@link ArrayList} of strings, each one being a parsing error message encountered
     * while parsing the last {@link Element} passed to <code>addPeriodInfoFromElement()</code>
     */
    public ArrayList<String> lastElementErrors() {
        return mLastElementErrors;
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
            is = assets.open(BUILT_IN_PERIODS_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            Log.wtf(TAG, "Error opening global periods file");
            return;
        }

        // Create a DocumentBuilder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            Log.wtf(TAG, "Error creating document builder");
            return;
        }

        // Parse the file
        Document doc;
        try {
            doc = builder.parse(is);
        } catch (SAXException e) {
            Log.wtf(TAG, "SAX error parsing global periods file");
            e.printStackTrace();
            return;
        } catch (IOException e) {
            Log.wtf(TAG, "IO error parsing global periods file");
            e.printStackTrace();
            return;
        }
        Element root = doc.getDocumentElement();

        // Get all <period-type> elements
        List<Element> periodTypeElements = xu.findAllElements(root, R.string.xml2elemName_periodType);

        // For each such element, add a PeriodInfo to the repository
        for (Element periodType : periodTypeElements) {
            PeriodInfo pi;

            try {
                pi = createPeriodInfoFromElement(periodType);
            } catch (PeriodInfoException e) {
                // this should never happen
                Log.e(TAG, e.getLocalizedMessage());
                continue;
            }

            String reference = pi.getReference();
            if (reference != null)
                mBuiltInPeriodInfos.put(reference, pi);
            else
                Log.e(TAG, "A global period didn't have a reference");
        }

    }

    /**
     * Creates a {@link PeriodInfo} object from an {@link Element}, storing any non-fatal parsing
     * errors to mLastElementErrors for retrieval via <code>lastElementErrors()</code>.  This checks
     * for XML format errors, but does not check for duplicate period-type references; the
     * caller must check that if required.  If this method returns without throwing a {@link PeriodInfoException},
     * then the reference of the {@link PeriodInfo} is guaranteed not to be <code>null</code>.
     * @param element an {@link Element} representing a &lt;period-type&gt; element
     * @return a fully-period {@link PeriodInfo} with the information in the element
     * @throws PeriodInfoException if there was a fatal parsing error (<i>e.g.</i> the period-type
     * had no reference).
     */
    private PeriodInfo createPeriodInfoFromElement(Element element) throws PeriodInfoException {

        String ref, name, description, defaultBackgroundColorStr;
        Integer defaultBackgroundColor = null;
        boolean poisAllowed = false;

        // Clear the last element errors log
        mLastElementErrors.clear();

        // Extract the reference and check for validity
        // We enforce restrictions against blank references and names because these have to
        // work with the app globally, i.e. the effects aren't constrained to the file in which
        // they are found.
        ref = xu.findAttributeText(element, R.string.xml2attrName_common_ref);
        if (ref == null)
            throw new PeriodInfoException(R.string.xml2error_periodType_ref_null);
        if (ref.length() == 0)
            throw new PeriodInfoException(R.string.xml2error_periodType_ref_blank);

        // Extract the name and check for validity
        name = xu.findLocalElementText(element, R.string.xml2elemName_periodType_name);
        if (name == null)
            throw new PeriodInfoException(R.string.xml2error_periodType_name_null, ref);
        if (name.length() == 0)
            throw new PeriodInfoException(R.string.xml2error_periodType_name_blank, ref);

        // Extract the description (there are no constraints on this field)
        description = xu.findLocalElementText(element, R.string.xml2elemName_periodType_display);

        // Parse the default background colour, if there is one
        defaultBackgroundColorStr = xu.findElementText(element, R.string.xml2elemName_periodType_defaultBackgroundColor);
        if (defaultBackgroundColorStr != null) {
            if (defaultBackgroundColorStr.startsWith("#")) {
                try {
                    defaultBackgroundColor = new BigInteger(defaultBackgroundColorStr.substring(1), 16).intValue();
                } catch (NumberFormatException e) {
                    addError(R.string.xml2Error_periodType_defaultBgColor_invalid, defaultBackgroundColorStr, ref);
                }
            } else {
                addError(R.string.xml2Error_periodType_defaultBgColor_invalid, defaultBackgroundColorStr, ref);
            }
        }

        // Parse the "pois-allowed" attribute
        try {
            poisAllowed = xu.isAttributeTrue(element, R.string.xml2attrName_periodType_poisAllowed);
        } catch (XmlInvalidValueException e) {
            addError(R.string.xml2Error_periodType_poisAllowed_invalid, e.getValue());
        }

        return new PeriodInfo(ref, name, description, defaultBackgroundColor, poisAllowed);
    }

    private void addError(int resId, Object... formatArgs) {
        mLastElementErrors.add(mResources.getString(resId, formatArgs));
    }

}
