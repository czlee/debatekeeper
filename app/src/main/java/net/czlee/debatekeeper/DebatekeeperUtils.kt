/*
 * Copyright (C) 2021 Chuan-Zheng Lee
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

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Utility functions that are used by more than one other class.
 *
 * @author Chuan-Zheng Lee
 * @since 2021-10-07
 */
object DebatekeeperUtils {

    private const val TAG = "DebatekeeperUtils"

    /**
     * Converts a number of seconds to a String in the format 0:00, or +0:00 if the time
     * given is negative.  (Note: A *plus* sign is used for *negative* numbers; this
     * indicates overtime.)  If `seconds` is at least 3600, the format also includes hours,
     * e.g. 1:00:00.
     * @param seconds a time in seconds
     * @return the String
     */
    @JvmStatic
    fun secsToTextSigned(seconds: Long): String {
        var secs = seconds
        val builder = StringBuilder()
        if (secs < 0) {
            builder.append("+")
            secs = -secs
        }
        var minutes = secs / 60
        val hours = minutes / 60
        minutes %= 60
        secs %= 60
        if (hours > 0) {
            builder.append(hours).append(":")
            if (minutes < 10) builder.append("0")
        }
        builder.append(minutes).append(":")
        if (secs < 10) builder.append("0")
        builder.append(secs)
        return builder.toString()
    }

    /**
     * Figures out what the file name should be, from the given URI. This is mostly in this class
     * for want of a better place to put it. Its main job is to parse a URI.
     *
     * @param uri a [Uri] to decipher
     * @return a file name, or null if it failed to discern the file name.
     */
    @JvmStatic
    fun getFilenameFromUri(resolver: ContentResolver, uri: Uri): String? {
        var filename: String? = null

        when (uri.scheme) {
            "file" -> {
                // Just retrieve the file name
                val file = File(uri.path!!)
                val name = file.name
                if (name.isNotEmpty())
                    filename = name
            }

            "content" -> {
                // Try to find a name for the file. (The DATA column, once used as a fallback
                // here, is deprecated and unreliable from API 29, so only DISPLAY_NAME is used.)
                val cursor = resolver.query(uri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                if (cursor == null) {
                    Log.e(TAG, "getFilenameFromUri: cursor was null")
                    return null
                }
                if (!cursor.moveToFirst()) {
                    Log.e(TAG, "getFilenameFromUri: failed moving cursor to first row")
                    cursor.close()
                    return null
                }
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex)
                    Log.i(TAG, "getFilenameFromUri: got from name column: $filename")
                }
                if (filename == null)
                    Log.e(TAG, "getFilenameFromUri: file name is null after trying the name column")
                cursor.close()
            }

            else -> return null
        }

        // If it doesn't end in the .xml extension, make it end in one
        if (filename != null && !filename.endsWith(".xml")) {

            // Do this by stripping the current extension if there is one...
            val lastIndex = filename.lastIndexOf(".")
            if (lastIndex > 0) filename = filename.substring(0, lastIndex)

            // ...and then adding .xml.
            filename += ".xml"
        }

        return filename
    }
}
