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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private final Context      mContext;
    private final AssetManager mAssets;
    private static final String XML_FILE_ROOT_DIRECTORY_NAME = "debatekeeper";
    private static final String ASSETS_PATH                  = "formats";

    public static final int LOCATION_ASSETS       = 0;
    public static final int LOCATION_USER_DEFINED = 1;
    public static final int LOCATION_NOT_FOUND    = -1;

    public FormatXmlFilesManager(Context context) {
        mContext = context;
        mAssets  = context.getAssets();
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Opens a file and returns an <code>InputStream</code> for the file.
     * @param filename the name of the file
     * @return the <code>InputStream</code> for the file
     * @throws IOException if the file can't be found or there is a problem with the file.
     */
    public InputStream open(String filename) throws IOException {
        InputStream is = openFromRoot(filename);
        if (is != null)
            return is;
        return openFromAssets(filename);
    }

    /**
     * Copies the given file to the user-defined XML files directory.
     * <p><b>Note that this overwrites the existing file if there is one.</b></p>
     * @param file the file to copy
     * @throws IOException if there was an error dealing with any of the files
     */
    public void copy(File source) throws IOException {

        // Check we can write to external storage
        if (!isExternalStorageWriteable())
            throw new IOException();

        // Figure out where to copy the file to
        String filename = source.getName();
        File userFilesDirectory = getOrCreateUserFilesDirectory();
        if (userFilesDirectory == null)
            throw new IOException();

        File destination = new File(userFilesDirectory, filename);

        // Open the files
        InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(destination);

        // Copy the file over
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0)
            out.write(buf, 0, len);
        in.close();
        out.close();

    }

    /**
     * Deletes a file, if it is a built-in asset file.
     * <p>Note that this method does <i>not</i> throw IOException on failure.
     * Callers must check the return value.  However, it does throw an
     * {@link IllegalArgumentException} if the file isn't a user-defined file, including if
     * it couldn't be found.</p>
     * @param filename the file to delete
     * @return <code>true</code> if the file was deleted, <code>false</code> otherwise
     * @throws IllegalArgumentException if the file isn't user-defined
     */
    public boolean delete(String filename) {

        if (!isExternalStorageWriteable())
            return false;

        if (getLocation(filename) != LOCATION_USER_DEFINED)
            throw new IllegalArgumentException("Tried to delete a file that isn't user-defined");

        File file = getFile(filename);

        return file.delete();

    }

    /**
     * Returns a <code>File</code> object for the given filename.
     * @param filename the name of the file to get a <code>File</code> object for
     * @return a <code>File</code> if the file exists, or <code>null</code> if it does not.
     */
    public File getFile(String filename) {

        // See if we can find the directory...
        File userFilesDirectory = getUserFilesDirectory();
        if (userFilesDirectory == null)
            return null;

        // Then see if we can find the file...
        File xmlFile = new File(userFilesDirectory, filename);
        if (!xmlFile.isFile())
            return null;

        return xmlFile;
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
        String[] assetList = mAssets.list(ASSETS_PATH);
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
            assetInputStream = openFromAssets(filename);
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

    private boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * @return the user files directory, or <code>null</code> if it does not exist and could not
     * be created or is not a directory.  If this method returns something non-null, you can assume
     * it is a directory.
     */
    private File getOrCreateUserFilesDirectory() {

        // Can't do anything if we can't read external storage.
        if (!isExternalStorageReadable())
            return null;

        File root = Environment.getExternalStorageDirectory();
        File userFilesDirectory = new File(root, XML_FILE_ROOT_DIRECTORY_NAME);

        // If it's already a directory, bingo!
        if (userFilesDirectory.isDirectory()) return userFilesDirectory;

        // If there's nothing where the directory is supposed to be, and we can write to external
        // storage, attempt to create the directory.
        if (!userFilesDirectory.exists() && isExternalStorageWriteable()) {
            boolean result = userFilesDirectory.mkdirs();
            if (result) return userFilesDirectory;
        }

        return null;
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

        File xmlFile = getFile(filename);

        if (xmlFile == null) return null;

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
        return mAssets.open(xmlFile.getPath());
    }

}
