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

package net.czlee.debatekeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;

/**
 * FormatXmlFilesManager manages the multiple sources of debate format XML files. All debate format
 * XML files are accessed through this class.  You only need a file name to be able to retrieve a
 * file.
 * <p>
 * FormatXmlFilesManager also manages the "look for custom formats" preference.
 * <p>
 * MIGRATION TO APP-SPECIFIC STORAGE Debatekeeper version 1.3 will target Android 10 (API level 29).
 * It needs to migrate its file location to the "scoped storage" location before Android 11, so the
 * current code does this: - The method migrateToScopedStorage() copies all files from the legacy
 * location to the new scoped app-specific location. -
 *
 * @author Chuan-Zheng Lee
 * @since 2012-06-27
 */
class FormatXmlFilesManager {

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private static final String TAG = "FormatXmlFilesManager";
    private static final String XML_FILE_ROOT_DIRECTORY_NAME = "debatekeeper";
    private static final String XML_FORMATS_DIRECTORY_NAME = "formats";
    private static final String ASSETS_PATH = "formats";
    private static final String PREFERENCE_LOOK_FOR_CUSTOM_FORMATS = "lookForCustom";

    static final int LOCATION_ASSETS = 0;
    static final int LOCATION_EXTERNAL_STORAGE = 1;
    static final int LOCATION_NOT_FOUND = -1;

    private boolean mLookForUserFiles;

    FormatXmlFilesManager(Context context) {
        mContext = context;

        // initialise with the user files preference
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mLookForUserFiles = mPrefs.getBoolean(PREFERENCE_LOOK_FOR_CUSTOM_FORMATS, false);
    }


    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Copies all data from the given stream to a file in the user-defined XML files directory.
     * <p><b>Note that this overwrites the existing file if there is one.</b></p>
     *
     * @param in              an {@link InputStream}
     * @param destinationName the name of the destination file
     * @throws IOException if there was an error dealing with any of the files
     */
    public void copy(InputStream in, @NonNull String destinationName) throws IOException {
        // Figure out where to copy the file to
        File userFilesDirectory = getAppSpecificUserFilesDirectory();
        File destination = new File(userFilesDirectory, destinationName);

        // Open the files
        OutputStream out = new FileOutputStream(destination);

        // Copy the file over
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0)
            out.write(buf, 0, len);
        out.flush();
        in.close();
        out.close();
    }

    /**
     * Copies a legacy file to the new app-specific destination. To be removed in a future version.
     *
     * @param filename the name of the file to be copied
     */
    public void copyLegacyFile(@NonNull String filename) throws IOException {
        File source = new File(getLegacyUserFilesDirectory(), filename);
        File destination = new File(getAppSpecificUserFilesDirectory(), filename);
        InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(destination);

        // Copy the file over
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0)
            out.write(buf, 0, len);
        out.flush();
        in.close();
        out.close();
    }

    /**
     * Opens the file given by 'filename' and returns an <code>InputStream</code> for the file.
     *
     * @param filename the name of the file
     * @return the <code>InputStream</code> for the file
     * @throws IOException if the file can't be found or there is a problem with the file.
     */
    public InputStream open(String filename) throws IOException {
        if (mLookForUserFiles) {
            InputStream is = openFromExternalStorage(filename);
            if (is != null)
                return is;
        }
        return openFromAssets(filename);
    }

    /**
     * Deletes the file given by 'filename'.
     *
     * @param filename the name of the file
     * @return <code>true</code> if and only if the file or directory is successfully deleted;
     * <code>false</code> otherwise.
     */
    public boolean delete(String filename) {
        File file = new File(getAppSpecificUserFilesDirectory(), filename);
        return file.delete();
    }

    /**
     * Returns a list of all user files in the app-specific external storage location.
     *
     * @return an array of Strings, possibly empty, each being an existent file name in external
     * storage.
     * @throws IOException if there is a problem with some file
     */
    @NonNull
    public String[] legacyUserFileList() throws IOException {
        if (!mLookForUserFiles) return new String[0];

        File userFilesDirectory = getLegacyUserFilesDirectory();
        if (userFilesDirectory == null) return new String[0];

        String[] list = userFilesDirectory.list();
        if (list == null) return new String[0];

        return list;
    }

    /**
     * Returns a list of all user files in the app-specific external storage location.
     *
     * @return an array of Strings, possibly empty, each being an existent file name in external
     * storage.
     * @throws IOException if there is a problem with some file
     */
    @NonNull
    public String[] userFileList() throws IOException {
        if (!mLookForUserFiles) return new String[0];

        File userFilesDirectory = getAppSpecificUserFilesDirectory();
        if (userFilesDirectory == null) return new String[0];

        String[] list = userFilesDirectory.list();
        if (list == null) return new String[0];

        return list;
    }

    /**
     * Returns a list of all files available in the relevant locations.
     *
     * @return an array of Strings, each being an existent file name (but not necessarily a valid
     * XML file)
     * @throws IOException if there is a problem with some file
     */
    public String[] list() throws IOException {
        HashSet<String> compiledSet = new HashSet<>();

        // First add files in the user files directory...
        Collections.addAll(compiledSet, userFileList());

        // Then add files in the assets...
        String[] assetList = mContext.getAssets().list(ASSETS_PATH);
        if (assetList != null)
            Collections.addAll(compiledSet, assetList);

        // Finally, return the combined list.
        return compiledSet.toArray(new String[0]);
    }

    /**
     * Returns a {@link File} object representing the file.
     *
     * @param filename Name of file to find.
     * @return a {@link File} object, or <code>null</code> if it isn't a file.
     */
    @Nullable
    public File getFileFromExternalStorage(String filename) {
        // See if we can find the directory...
        File userFilesDirectory = getAppSpecificUserFilesDirectory();

        // Then see if we can find the file...
        File xmlFile = new File(userFilesDirectory, filename);
        if (!xmlFile.isFile())
            return null;

        return xmlFile;
    }

    /**
     * Finds out in which location this file is.
     *
     * @param filename the name of the file
     * @return a LOCATION_* integer representing the location of the file
     */
    public int getLocation(@NonNull String filename) {
        if (mLookForUserFiles) {
            InputStream userFileInputStream = openFromExternalStorage(filename);
            if (userFileInputStream != null)
                return LOCATION_EXTERNAL_STORAGE;
        }

        InputStream assetInputStream;
        try {
            assetInputStream = openFromAssets(filename);
        } catch (IOException e) {
            return LOCATION_NOT_FOUND;
        }

        if (assetInputStream != null)
            return LOCATION_ASSETS;

        return LOCATION_NOT_FOUND;
    }

    /**
     * @return whether this manager is set to look for user files.
     */
    public boolean isLookingForUserFiles() {
        return mLookForUserFiles;
    }

    /**
     * Sets whether the files manager will look for user files. This method also saves the setting
     * to preferences.
     *
     * @param lookForUserFiles whether to look for user files
     */
    public void setLookForUserFiles(boolean lookForUserFiles) {
        this.mLookForUserFiles = lookForUserFiles;
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(PREFERENCE_LOOK_FOR_CUSTOM_FORMATS, lookForUserFiles);
        editor.apply();
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    /**
     * Legacy user files directory. Deprecated from Android 10 (API level 29).
     *
     * @return the user files directory if it exists, or <code>null</code> if it does not exist or
     * is not a directory.  If this method returns something non-null, you can assume it is a
     * directory.
     */
    private File getLegacyUserFilesDirectory() {
        if (!isExternalStorageReadable())
            return null;

        File root = Environment.getExternalStorageDirectory();
        File userFilesDirectory = new File(root, XML_FILE_ROOT_DIRECTORY_NAME);
        if (!userFilesDirectory.isDirectory())
            return null;

        return userFilesDirectory;
    }

    /**
     * App-specific user files directory.
     *
     * @return the user files directory if it exists, or <code>null</code> if it does not exist.
     */
    private File getAppSpecificUserFilesDirectory() {
        File root = mContext.getExternalFilesDir(null);
        File directory = new File(root, XML_FORMATS_DIRECTORY_NAME);

        // Create if it doesn't exist
        if (!directory.exists()) {
            boolean result = directory.mkdirs();
            if (!result) return null;
        }

        if (!directory.isDirectory()) return null;

        return directory;
    }

    /**
     * @param filename the name of the file to open
     * @return an InputStream if the file exists, or <code>null</code> if it does not.
     */
    @Nullable
    private InputStream openFromExternalStorage(String filename) {

        File xmlFile = getFileFromExternalStorage(filename);
        if (xmlFile == null)
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

    private InputStream openFromAssets(String filename) throws IOException {
        File xmlFile = new File(ASSETS_PATH, filename);
        return mContext.getAssets().open(xmlFile.getPath());
    }

}
