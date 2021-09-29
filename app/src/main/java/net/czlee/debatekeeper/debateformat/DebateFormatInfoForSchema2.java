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
import android.text.format.DateUtils;
import android.util.Log;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


/**
 * A class that retrieves relevant information from a given debate format XML file.
 * This class does not validate the XML file against a schema, and does not raise any
 * errors if fields are invalid or anything like that - it just returns sensible
 * "empty" values.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-06-05
 */
public class DebateFormatInfoForSchema2 implements DebateFormatInfo {

    private static final String TAG = "DebateFormatInfoForSchema2";

    private final Context mContext;
    private final XmlUtilities xu;
    private final Element mRootElement;
    private final Element mInfoElement; // keep <info> readily accessible for performance

    private static final String MINIMUM_SCHEMA_VERSION = "2.0";
    private static final String MAXIMUM_SCHEMA_VERSION = "2.2";

    public DebateFormatInfoForSchema2(Context context, InputStream is) {
        mContext = context;
        xu = new XmlUtilities(context.getResources());

        Document doc;
        try {
            doc = getDocumentFromInputStream(is);
        } catch (SAXException | IOException e) {
            e.printStackTrace();
            doc = null;
        }

        if (doc != null)
            mRootElement = doc.getDocumentElement();
        else
            mRootElement = null;

        if (mRootElement != null)
            mInfoElement = xu.findElement(mRootElement, R.string.xml2elemName_info);
        else
            mInfoElement = null;

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getName()
     */
    @Override
    public String getName() {
        if (mRootElement == null) return "";
        String result = xu.findElementText(mRootElement, R.string.xml2elemName_name);
        if (result == null) return "";
        else return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getDescription()
     */
    @Override
    public String getDescription() {
        if (mInfoElement == null) return "-";
        String result = xu.findElementText(mInfoElement, R.string.xml2elemName_info_desc);
        if (result == null) return "-";
        else return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getRegions()
     */
    @Override
    public ArrayList<String> getRegions() {
        ArrayList<String> result = new ArrayList<>();
        if (mInfoElement == null) return result; // empty list
        NodeList regionsList = xu.findAllElements(mInfoElement, R.string.xml2elemName_info_region);
        for (int i = 0; i < regionsList.getLength(); i++) {
            Element element = (Element) regionsList.item(i);
            String text = element.getTextContent();
            result.add(text);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getLevels()
     */
    @Override
    public ArrayList<String> getLevels() {
        ArrayList<String> result = new ArrayList<>();
        if (mInfoElement == null) return result; // empty list
        NodeList levelsList = xu.findAllElements(mInfoElement, R.string.xml2elemName_info_level);
        for (int i = 0; i < levelsList.getLength(); i++) {
            Element element = (Element) levelsList.item(i);
            String text = element.getTextContent();
            result.add(text);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getUsedAts()
     */
    @Override
    public ArrayList<String> getUsedAts() {
        ArrayList<String> result = new ArrayList<>();
        if (mInfoElement == null) return result; // empty list
        NodeList usedAtsList = xu.findAllElements(mInfoElement, R.string.xml2elemName_info_usedAt);
        for (int i = 0; i < usedAtsList.getLength(); i++) {
            Element element = (Element) usedAtsList.item(i);
            String text = element.getTextContent();
            result.add(text);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getPrepTimeDescription()
     */
    @Override
    public String getPrepTimeDescription() {
        if (mRootElement == null) return null;

        Element prepTimeSimple     = xu.findElement(mRootElement, R.string.xml2elemName_prepTimeSimpleFormat);
        Element prepTimeControlled = xu.findElement(mRootElement, R.string.xml2elemName_prepTimeControlledFormat);

        if (prepTimeSimple != null && prepTimeControlled == null) { // simple
            Long length;
            try {
                length = xu.findAttributeAsTime(prepTimeSimple, R.string.xml2attrName_controlledTimeLength);
            } catch (XmlInvalidValueException e) {
                return null;
            }
            if (length == null) return null;
            else return buildLengthString(length);

        } else if (prepTimeControlled != null && prepTimeSimple == null) { // controlled
            String description;
            Long length;
            try {
                length = xu.findAttributeAsTime(prepTimeControlled, R.string.xml2attrName_controlledTimeLength);
            } catch (XmlInvalidValueException e) {
                return null;
            }
            if (length == null) return null;
            else description = buildLengthString(length);

            description += mContext.getString(R.string.viewFormat_timeDescription_controlledPrepSuffix);

            NodeList bells = xu.findAllElements(prepTimeControlled, R.string.xml2elemName_bell);
            description += "\n" + buildBellsString(bells, length);

            return description;

        }
        // If they both exist, or if neither exist, return null
        return null;

    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getSchemaVersion()
     */
    @Override
    public String getSchemaVersion() {
        if (mRootElement == null) return null;
        return xu.findAttributeText(mRootElement, R.string.xml2attrName_root_schemaVersion);
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getSpeechFormatDescriptions()
     */
    @Override
    public ArrayList<String[]> getSpeechFormatDescriptions() {
        ArrayList<String[]> result = new ArrayList<>();

        if (mRootElement == null) return result;
        Element speechFormatsElement = xu.findElement(mRootElement, R.string.xml2elemName_speechFormats);
        if (speechFormatsElement == null) return result;
        NodeList speechFormats = xu.findAllElements(speechFormatsElement, R.string.xml2elemName_speechFormat);

        for (int i = 0; i < speechFormats.getLength(); i++) {
            String reference, description;
            Element element = (Element) speechFormats.item(i);

            reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref);
            if (reference == null) continue;

            Long length;
            try {
                length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
            } catch (XmlInvalidValueException e) {
                continue;
            }
            if (length == null) continue;
            else description = buildLengthString(length);

            NodeList bells = xu.findAllElements(element, R.string.xml2elemName_bell);
            description += "\n" + buildBellsString(bells, length);

            String [] pair = {reference, description};
            result.add(pair);

        }

        return result;
    }

    /* (non-Javadoc)
     * @see net.czlee.debatekeeper.debateformat.DebateFormatInfo#getSpeeches()
     */
    @Override
    public ArrayList<String[]> getSpeeches() {
        ArrayList<String[]> result = new ArrayList<>();

        if (mRootElement == null) return result;
        Element speechesElement = xu.findElement(mRootElement, R.string.xml2elemName_speechesList);
        if (speechesElement == null) return result;
        NodeList speechFormats = xu.findAllElements(speechesElement, R.string.xml2elemName_speech);

        for (int i = 0; i < speechFormats.getLength(); i++) {
            String name, format;
            Element element = (Element) speechFormats.item(i);

            name = xu.findElementText(element, R.string.xml2elemName_speech_name);
            if (name == null) continue;

            format = xu.findAttributeText(element, R.string.xml2attrName_speech_format);
            if (format == null) continue;

            String [] pair = {name, format};
            result.add(pair);

        }

        return result;
    }

    @Override
    public boolean isSchemaSupported() {
        String schemaVersion = getSchemaVersion();
        if (schemaVersion == null)
            return false; // either not built, or if it was built then probably the wrong schema
        try {
            return (XmlUtilities.compareSchemaVersions(schemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0)
                    && (XmlUtilities.compareSchemaVersions(schemaVersion, MINIMUM_SCHEMA_VERSION) >= 0);
        } catch (IllegalSchemaVersionException e) {
            return false;
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private Document getDocumentFromInputStream(InputStream is) throws SAXException, IOException {

        DocumentBuilder builder;
        try {
            // There should never be a problem creating this DocumentBuilder, ever.
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            Log.wtf(TAG, "Error creating document builder");
            // After this, the app is pretty much guaranteed to crash.
            return null;
        }

        return builder.parse(is);

    }

    /**
     * Builds a string describing a list of bells
     * @param list a list of &lt;bell&gt; {@link Element}s
     * @return the completed string e.g. "bells at 01:00, 06:00, 07:00"
     */
    private String buildBellsString(NodeList list, long finishTime) {
        StringBuilder bellsList = new StringBuilder();

        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            String timeStr = xu.findAttributeText(element, R.string.xml2attrName_bell_time);
            long time;
            if (timeStr == null) continue;
            if (timeStr.equals(mContext.getString(R.string.xml2attrValue_bell_time_finish)))
                time = finishTime;
            else {
                try {
                    time = XmlUtilities.timeStr2Secs(timeStr);
                } catch (NumberFormatException e) {
                    continue; // if we couldn't interpret the time, ignore it
                }
            }
            bellsList.append(DateUtils.formatElapsedTime(time));
            boolean pauseOnBell;
            try {
                pauseOnBell = xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell);
            } catch (XmlInvalidValueException e) {
                pauseOnBell = false;
            }
            if (pauseOnBell)
                bellsList.append(mContext.getString(R.string.mainScreen_pauseOnBellIndicator));

            // If there's one after this, add a comma
            if (i < list.getLength() - 1) bellsList.append(", ");
        }

        return mContext.getResources().getQuantityString(
                R.plurals.viewFormat_timeDescription_bellsList, list.getLength(), bellsList);
    }

    private String buildLengthString(long length) {
        if (length % 60 == 0) {
            long minutes = length / 60;
            return mContext.getResources().getQuantityString(R.plurals.viewFormat_timeDescription_lengthInMinutesOnly, (int) minutes, minutes);
        } else
            return mContext.getString(R.string.viewFormat_timeDescription_lengthInMinutesSeconds, DateUtils.formatElapsedTime(length));
    }
}

