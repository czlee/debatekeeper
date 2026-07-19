/*
 * Copyright (C) 2012 Chuan-Zheng Lee
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

package net.czlee.debatekeeper

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream

/**
 * FormatXmlFilesManager manages the multiple sources of debate format XML files. All debate format
 * XML files are accessed through this class.  You only need a file name to be able to retrieve a
 * file.
 *
 * FormatXmlFilesManager also manages the "look for custom formats" preference.
 *
 * @author Chuan-Zheng Lee
 * @since 2012-06-27
 */
class FormatXmlFilesManager internal constructor(private val mContext: Context) {

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Copies all data from the given stream to a file in the user-defined XML files directory.
     *
     * **Note that this overwrites the existing file if there is one.**
     *
     * @param in an [InputStream]
     * @param destinationName the name of the destination file
     * @throws IOException if there was an error dealing with any of the files
     */
    @Throws(IOException::class)
    fun copy(`in`: InputStream, destinationName: String) {
        // Figure out where to copy the file to
        val userFilesDirectory = getAppSpecificUserFilesDirectory()
        val destination = File(userFilesDirectory, destinationName)

        // Open the files
        val out: OutputStream = FileOutputStream(destination)

        // Copy the file over
        val buf = ByteArray(1024)
        var len: Int
        while (`in`.read(buf).also { len = it } > 0)
            out.write(buf, 0, len)
        out.flush()
        `in`.close()
        out.close()
    }

    /**
     * Opens the file given by 'filename' and returns an `InputStream` for the file.
     *
     * @param filename the name of the file
     * @return the `InputStream` for the file
     * @throws IOException if the file can't be found or there is a problem with the file.
     */
    @Throws(IOException::class)
    fun open(filename: String): InputStream {
        val xmlFile = getFileFromExternalStorage(filename)
                ?: throw FileNotFoundException(filename)
        return FileInputStream(xmlFile)
    }

    /**
     * Deletes the file given by 'filename'.
     *
     * @param filename the name of the file
     * @return `true` if and only if the file or directory is successfully deleted;
     * `false` otherwise.
     */
    fun delete(filename: String): Boolean {
        val file = File(getAppSpecificUserFilesDirectory(), filename)
        return file.delete()
    }

    /**
     * Returns a list of all user files in the app-specific external storage location.
     *
     * @return an array of Strings, possibly empty, each being an existent file name in external
     * storage.
     * @throws IOException if there is a problem with some file
     */
    @Throws(IOException::class)
    fun list(): Array<String> {
        val userFilesDirectory = getAppSpecificUserFilesDirectory() ?: return arrayOf()
        return userFilesDirectory.list() ?: arrayOf()
    }

    fun exists(filename: String): Boolean {
        try {
            val `in` = open(filename)
            `in`.close()
        } catch (e: IOException) {
            return false
        }
        return true
    }

    /**
     * @return `true` if the app-specific directory has only the initial files, `false` otherwise
     * @throws IOException if thrown by the file system
     */
    @Throws(IOException::class)
    fun hasOnlyInitialFiles(): Boolean {
        val assetList = mContext.assets.list(ASSETS_PATH)!!
        val userList = list()
        if (userList.size > assetList.size)
            return false
        // we return true if there exists any user file name that is not an asset file name
        for (userName in userList) {
            if (!assetList.contains(userName)) return false
        }
        return true
    }

    /**
     * @return `true` if there are no files in the app-specific directory, `false` otherwise
     * @throws IOException if thrown by the file system
     */
    @Throws(IOException::class)
    fun isEmpty(): Boolean {
        return list().isEmpty()
    }

    /**
     * Returns a [File] object representing the file.
     *
     * @param filename Name of file to find.
     * @return a [File] object, or `null` if it isn't a file.
     */
    fun getFileFromExternalStorage(filename: String): File? {
        // See if we can find the directory...
        val userFilesDirectory = getAppSpecificUserFilesDirectory()

        // Then see if we can find the file...
        val xmlFile = File(userFilesDirectory, filename)
        return if (!xmlFile.isFile) null else xmlFile
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
    @Throws(IOException::class)
    fun getFreeFileName(): String {
        val existing = list().toList()
        for (i in 1..1000) {
            @SuppressLint("DefaultLocale")
            val newFilename = String.format("imported-debate-format-%d.xml", i)
            if (!existing.contains(newFilename))
                return newFilename
        }
        throw FileNotFoundException()
    }

    /**
     * Copies all of the assets to the app-specific directory.
     */
    @Throws(IOException::class)
    fun copyAssets() {
        val assetList = mContext.assets.list(ASSETS_PATH)!!
        for (filename in assetList) {
            Log.i(TAG, "Copying $filename from assets to file system")
            val assetsFile = File(ASSETS_PATH, filename)
            val `in` = mContext.assets.open(assetsFile.path)
            copy(`in`, filename)
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * App-specific user files directory.
     *
     * @return the user files directory if it exists, or `null` if it does not exist.
     */
    private fun getAppSpecificUserFilesDirectory(): File? {
        val root = mContext.getExternalFilesDir(null)
        val directory = File(root, XML_FORMATS_DIRECTORY_NAME)

        // Create if it doesn't exist
        if (!directory.exists()) {
            val result = directory.mkdirs()
            if (!result) return null
        }

        return if (!directory.isDirectory) null else directory
    }

    companion object {
        private const val TAG = "FormatXmlFilesManager"
        private const val XML_FORMATS_DIRECTORY_NAME = "formats"
        private const val ASSETS_PATH = "formats"
    }
}
