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

package net.czlee.debatekeeper.debateformat;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ibm.icu.util.ULocale;

import net.czlee.debatekeeper.DebatingTimerFragment;
import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException;
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    @NonNull private final Context mContext;
    @NonNull private final XmlUtilities xu;
    @Nullable private Element mRootElement = null;
    @Nullable private Element mInfoElement = null; // keep <info> readily accessible for performance
    @Nullable private ArrayList<String> mDeclaredLanguages = null;

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

        if (doc != null) mRootElement = doc.getDocumentElement();

        if (mRootElement != null) {
            Element languagesRoot = xu.findElement(mRootElement, R.string.xml2elemName_languages);
            if (languagesRoot != null) {
                mDeclaredLanguages = xu.findAllElementTexts(languagesRoot, R.string.xml2elemName_languages_language);
                xu.setDeclaredLanguages(mDeclaredLanguages);
            }

            mInfoElement = xu.findLocalElement(mRootElement, R.string.xml2elemName_info);
        }

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public String getName() {
        if (mRootElement == null) return "";
        String result = xu.findLocalElementText(mRootElement, R.string.xml2elemName_name);
        if (result == null) return "";
        else return result;
    }

    @Override
    public String getDescription() {
        if (mInfoElement == null) return "-";
        String result = xu.findLocalElementText(mInfoElement, R.string.xml2elemName_info_desc);
        if (result == null) return "-";
        else return result;
    }

    @Override
    public List<String> getRegions() {
        return xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_region);
    }

    @Override
    public List<String> getLevels() {
        return xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_level);
    }

    @Override
    public List<String> getUsedAts() {
        return xu.findAllElementTexts(mInfoElement, R.string.xml2elemName_info_usedAt);
    }

    @Override
    public List<String> getDisplayLanguages() {
        if (mDeclaredLanguages == null) return new ArrayList<>();
        ArrayList<String> languages = new ArrayList<>();
        for (String code : mDeclaredLanguages) {
            ULocale locale = new ULocale(code);
            String language = locale.getDisplayLanguage();
            if (language != null) languages.add(language);
        }
        return languages;
    }

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

            List<Element> bells = xu.findAllElements(prepTimeControlled, R.string.xml2elemName_bell);
            description += "\n" + buildBellsString(bells, length);

            return description;

        }
        // If they both exist, or if neither exist, return null
        return null;

    }

    @Override
    public String getSchemaVersion() {
        if (mRootElement == null) return null;
        return xu.findAttributeText(mRootElement, R.string.xml2attrName_root_schemaVersion);
    }

    @Override
    public List<String[]> getSpeechFormatDescriptions() {
        ArrayList<String[]> result = new ArrayList<>();

        if (mRootElement == null) return result;
        Element speechFormatsElement = xu.findElement(mRootElement, R.string.xml2elemName_speechFormats);
        if (speechFormatsElement == null) return result;
        List<Element> speechFormats = xu.findAllElements(speechFormatsElement, R.string.xml2elemName_speechFormat);

        for (Element element : speechFormats) {
            String reference, description;

            reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref);
            if (reference == null) continue;

            String speechName = xu.findLocalElementText(element, R.string.xml2elemName_name);
            if (speechName == null) speechName = reference;

            Long length;
            try {
                length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
            } catch (XmlInvalidValueException e) {
                continue;
            }
            if (length == null) continue;
            else description = buildLengthString(length);

            List<Element> bells = xu.findAllElements(element, R.string.xml2elemName_bell);
            description += "\n" + buildBellsString(bells, length);

            String [] pair = {speechName, description, reference};
            result.add(pair);

        }

        return result;
    }

    @Override
    public List<String[]> getSpeeches(List<String[]> descriptions) {
        ArrayList<String[]> result = new ArrayList<>();

        if (mRootElement == null) return result;
        Element speechesElement = xu.findElement(mRootElement, R.string.xml2elemName_speechesList);
        if (speechesElement == null) return result;
        List<Element> speechFormats = xu.findAllElements(speechesElement, R.string.xml2elemName_speech);

        // Build a map from format string to description
        HashMap<String, String> refToDescr = new HashMap<>();
        for (int i = 0; i < descriptions.size(); i++) {
            String descr = descriptions.get(i)[0];
            String ref = descriptions.get(i)[2];
            refToDescr.put(ref, descr);
        }

        for (Element element : speechFormats) {
            String name, format;

            name = xu.findLocalElementText(element, R.string.xml2elemName_speech_name);
            if (name == null) continue;

            format = xu.findAttributeText(element, R.string.xml2attrName_speech_format);
            if (format == null) continue;

            String descr = refToDescr.get(format);
            if (descr == null) descr = format;

            String [] pair = {name, descr};
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
     * @return the completed string e.g. "bells at 1:00, 6:00, 7:00"
     */
    private String buildBellsString(List<Element> list, long finishTime) {
        StringBuilder bellsList = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            Element element = list.get(i);
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
            bellsList.append(DebatingTimerFragment.secsToTextSigned(time));
            boolean pauseOnBell;
            try {
                pauseOnBell = xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell);
            } catch (XmlInvalidValueException e) {
                pauseOnBell = false;
            }
            if (pauseOnBell)
                bellsList.append(mContext.getString(R.string.timer_pauseOnBellIndicator));

            // If there's one after this, add a comma
            if (i < list.size() - 1) bellsList.append(", ");
        }

        return mContext.getResources().getQuantityString(
                R.plurals.viewFormat_timeDescription_bellsList, list.size(), bellsList);
    }

    private String buildLengthString(long length) {
        if (length % 60 == 0) {
            long minutes = length / 60;
            return mContext.getResources().getQuantityString(R.plurals.viewFormat_timeDescription_lengthInMinutesOnly, (int) minutes, minutes);
        } else
            return mContext.getString(R.string.viewFormat_timeDescription_lengthInMinutesSeconds, DebatingTimerFragment.secsToTextSigned(length));
    }

}

