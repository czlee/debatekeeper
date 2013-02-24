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
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * PeriodInfoManager retrieves {@link PeriodInfo} objects from appropriate locations.
 * This class is only relevant to debate formats using the 2.0 schema and above.
 * It is <b>not</b> relevant to debate formats using the 1.0/1.1 schemas.
 *
 * @author Chuan-Zheng Lee
 * @since 2013-02-24
 */
public class PeriodInfoManager {

    private final Context mContext;
    private final AssetManager mAssets;
    private final FormatXmlFilesManager mFormatXmlFilesManager;
    private final HashMap<String, PeriodInfo> mGlobalPeriodInfos;

    private static final String GLOBAL_PERIODS_FILE = "periods.xml";

    public PeriodInfoManager(Context context) {
        mContext = context;
        mAssets = context.getAssets();
        mFormatXmlFilesManager = new FormatXmlFilesManager(context);
        mGlobalPeriodInfos = parseGlobalPeriodInfos();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public PeriodInfo getPeriodInfo(String periodInfoRef) {
        // TODO write this function
        return null;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************
    private HashMap<String, PeriodInfo> parseGlobalPeriodInfos() {

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

        NodeList periodTypeNodes = doc.getElementsByTagName(getString(R.string.xml2elemName_periodType));


        return null;
    }

    private String getString(int resId) {
        return mContext.getString(resId);
    }

}
