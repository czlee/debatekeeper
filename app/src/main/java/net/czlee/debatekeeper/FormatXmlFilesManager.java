/*
 * Copyright (C) 2012-2021 Chuan-Zheng Lee
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

package net.czlee.debatekeeper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

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
    private static final String TAG = "FormatXmlFilesManager";
    private static final String LEGACY_DIRECTORY_NAME = "debatekeeper";
    private static final String XML_FORMATS_DIRECTORY_NAME = "formats";
    private static final String ASSETS_PATH = "formats";

    FormatXmlFilesManager(Context context) {
        mContext = context;
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
     * Opens the file given by 'filename' and returns an <code>InputStream</code> for the file.
     *
     * @param filename the name of the file
     * @return the <code>InputStream</code> for the file
     * @throws IOException if the file can't be found or there is a problem with the file.
     */
    @NonNull
    public InputStream open(String filename) throws IOException {
        File xmlFile = getFileFromExternalStorage(filename);
        if (xmlFile == null) throw new FileNotFoundException(filename);
        return new FileInputStream(xmlFile);
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
    public String[] list() throws IOException {
        File userFilesDirectory = getAppSpecificUserFilesDirectory();
        if (userFilesDirectory == null) return new String[0];

        String[] list = userFilesDirectory.list();
        if (list == null) return new String[0];

        return list;
    }

    public boolean exists(@NonNull String filename) {
        try {
            InputStream in = open(filename);
            in.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * @return {@code true} if the app-specific directory has only the initial files, {@code false}
     * otherwise
     * @throws IOException if thrown by the file system
     */
    public boolean hasOnlyInitialFiles() throws IOException {
        String[] assetList = mContext.getAssets().list(ASSETS_PATH);
        String[] userList = list();
        if (userList.length > assetList.length)
            return false;
        // we return true if there exists any user file name that is not an asset file name
        for (String userName : userList) {
            boolean isAsset = false;
            for (String assetName : assetList)
                if (userName.equals(assetName)) {
                    isAsset = true;
                    break;
                }
            if (!isAsset) return false;
        }
        return true;
    }

    /**
     * @return {@code true} if there are no files in the app-specific directory, {@code false}
     * otherwise
     * @throws IOException if thrown by the file system
     */
    public boolean isEmpty() throws IOException {
        return this.list().length == 0;
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
     * Returns a free file name. It will probably look like "imported-debate-format-n.xml", for some
     * value n. It's meant to be name that makes it clear it wasn't written by a user.
     *
     * @return a file name that should be safe to write to without losing data (at least at the time
     * it was returned). There's not much effort made to make this user-friendly -- it is intended
     * to be a fallback.
     * @throws FileNotFoundException if it gave up trying to find a file
     * @throws IOException           if it couldn't figure out what files exist
     */
    public String getFreeFileName() throws IOException {
        List<String> existing = Arrays.asList(list());
        for (int i = 1; i <= 1000; i++) {
            @SuppressLint("DefaultLocale")
            String newFilename = String.format("imported-debate-format-%d.xml", i);
            if (!existing.contains(newFilename))
                return newFilename;
        }
        throw new FileNotFoundException();
    }

    /**
     * Copies all of the assets to the app-specific directory.
     */
    public void copyAssets() throws IOException {
        String[] assetList = mContext.getAssets().list(ASSETS_PATH);
        for (String filename : assetList) {
            Log.i(TAG, "Copying " + filename + " from assets to file system");
            File assetsFile = new File(ASSETS_PATH, filename);
            InputStream in = mContext.getAssets().open(assetsFile.getPath());
            copy(in, filename);
        }
    }

    //******************************************************************************************
    // Static methods
    //******************************************************************************************

    //******************************************************************************************
    // Legacy support
    //******************************************************************************************

    /**
     * Copies a legacy file to the new app-specific destination. To be removed in a future version.
     *
     * @param filename the name of the file to be copied
     */
    public void copyLegacyFile(@NonNull String filename) throws IOException {
        File source = new File(getLegacyUserFilesDirectory(), filename);
        InputStream in = new FileInputStream(source);
        copy(in, filename);
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
        File userFilesDirectory = getLegacyUserFilesDirectory();
        if (userFilesDirectory == null) return new String[0];

        String[] list = userFilesDirectory.list();
        if (list == null) return new String[0];

        return list;
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
        File userFilesDirectory = new File(root, LEGACY_DIRECTORY_NAME);
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

}
