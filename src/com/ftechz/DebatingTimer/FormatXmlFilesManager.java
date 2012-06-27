/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

package com.ftechz.DebatingTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;

/**
 * FormatXmlFilesManager manages the multiple sources of debate format XML files.
 * All debate format XML files are accessed through this class.  You only need a file name to be
 * able to retrieve a file.
 *
 * NOTE: Please DO NOT WRITE to the directory /debatekeeper/.  That is in the user's root space,
 * which we should try to avoid polluting.  If the user puts files there, we will read them.
 * But we will not put files there ourselves.
 *
 * If we do want to write files there in future, we should either:
 *  (a) ask the user, "Do you want to create the directory /debatekeeper?", or
 *  (b) write files into /Android/data/<package_name>/files/ (preferred),
 * in accordance with Android conventions.
 *
 * We should keep the /debatekeeper functionality present in all future versions, both (a) for
 * backwards compatibility and (b) more importantly, so that uninstalling the app doesn't delete
 * those user files that the user himself put there!
 *
 * @author Chuan-Zheng Lee
 *
 */
/**
 * @author Chuan-Zheng Lee
 * @since  2012-06-27
 */
public class FormatXmlFilesManager {

    private final AssetManager mAssets;
    private static final String XML_FILE_ROOT_DIRECTORY_NAME = "debatekeeper";

    public static final int LOCATION_ASSETS       = 0;
    public static final int LOCATION_USER_DEFINED = 1;
    public static final int LOCATION_NOT_FOUND    = -1;

    public FormatXmlFilesManager(Context context) {
        mAssets = context.getAssets();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Opens the file given by 'filename' and returns an <code>InputStream</code> for the file.
     * @param filename the name of the file
     * @return the <code>InputStream</code> for the file
     * @throws IOException if the file can't be found or there is a problem with the file.
     */
    public InputStream open(String filename) throws IOException {
        InputStream is = openFromRoot(filename);
        if (is != null)
            return is;
        return mAssets.open(filename);
    }

    /**
     * Returns a list of all files available in the relevant locations.
     * @return an array of Strings, each being an existent file name (but not necessarily a valid
     * XML file)
     * @throws IOException
     */
    public String[] list() throws IOException {
        HashSet<String> compiledSet = new HashSet<String>();

        // First add files in the user files directory...
        File userFilesDirectory = getUserFilesDirectory();
        if (userFilesDirectory != null) {
            String[] userFilesList = userFilesDirectory.list();
            if (userFilesList != null) {
                for (int i = 0; i < userFilesList.length; i++) {
                    compiledSet.add(userFilesList[i]);
                }
            }
        }

        // Then add files in the assets...
        String[] assetList = mAssets.list("");
        if (assetList != null) {
            for (int i = 0; i < assetList.length; i++) {
                compiledSet.add(assetList[i]);
            }
        }

        // Finally, return the combined list.
        String[] compiledList = compiledSet.toArray(new String[0]);
        return compiledList;
    }

    /**
     * Finds out in which location this file is.
     * @param filename the name of the file
     * @return a LOCATION_* integer representing the location of the file
     */
    public int getLocation(String filename) {
        InputStream userFileInputStream = openFromRoot(filename);
        if (userFileInputStream != null)
            return LOCATION_USER_DEFINED;

        InputStream assetInputStream;
        try {
            assetInputStream = mAssets.open(filename);
        } catch (IOException e) {
            return LOCATION_NOT_FOUND;
        }

        if (assetInputStream != null)
            return LOCATION_ASSETS;

        return LOCATION_NOT_FOUND;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state))
            return true;
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
            return true;
        return false;
    }

    /**
     * @return the user files directory if it exists, or <code>null</code> if it does not exist
     * or is not a directory.  If this method returns something non-null, you can assume it is
     * a directory.
     */
    private File getUserFilesDirectory() {
        if (!isExternalStorageReadable())
            return null;

        File root = Environment.getExternalStorageDirectory();
        File userFilesDirectory = new File(root, XML_FILE_ROOT_DIRECTORY_NAME);
        if (!userFilesDirectory.isDirectory())
            return null;

        return userFilesDirectory;
    }

    /**
     * @param filename the name of the file to open
     * @return an InputStream if the file exists, or <code>null</code> if it does not.
     */
    private InputStream openFromRoot(String filename) {

        // See if we can find the directory...
        File userFilesDirectory = getUserFilesDirectory();
        if (userFilesDirectory == null)
            return null;

        // Then see if we can find the file...
        File xmlFile = new File(userFilesDirectory, filename);
        if (!xmlFile.isFile())
            return null;

        // Then see if we can open it...
        FileInputStream fis;
        try {
            fis = new FileInputStream(xmlFile);
        } catch (FileNotFoundException e) {
            return null;
        }

        // And if we can, return the resulting input stream.
        return fis;
    }

}
