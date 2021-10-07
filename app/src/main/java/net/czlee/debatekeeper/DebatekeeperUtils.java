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

package net.czlee.debatekeeper;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;

/**
 * Utility functions that are used by more than one other class.
 *
 * @author Chuan-Zheng Lee
 * @since 2021-10-07
 */
public class DebatekeeperUtils {

    private final static String TAG = "DebatekeeperUtils";

    /**
     * Converts a number of seconds to a String in the format 0:00, or +0:00 if the time
     * given is negative.  (Note: A <i>plus</i> sign is used for <i>negative</i> numbers; this
     * indicates overtime.)  If {@code seconds} is at least 3600, the format also includes hours,
     * e.g. 1:00:00.
     * @param seconds a time in seconds
     * @return the String
     */
    public static String secsToTextSigned(long seconds) {
        StringBuilder builder = new StringBuilder();
        if (seconds < 0) {
            builder.append("+");
            seconds = -seconds;
        }
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes %= 60;
        seconds %= 60;
        if (hours > 0) {
            builder.append(hours).append(":");
            if (minutes < 10) builder.append("0");
        }
        builder.append(minutes).append(":");
        if (seconds < 10) builder.append("0");
        builder.append(seconds);
        return builder.toString();
    }

    /**
     * Figures out what the file name should be, from the given URI. This is mostly in this class
     * for want of a better place to put it. Its main job is to parse a URI.
     *
     * @param uri a {@link Uri} to decipher
     * @return a file name, or null if it failed to discern the file name.
     */
    @Nullable
    public static String getFilenameFromUri(ContentResolver resolver, Uri uri) {
        String filename = null;
        String scheme = uri.getScheme();

        switch (scheme) {
            case "file":
                // Just retrieve the file name
                File file = new File(uri.getPath());
                String name = file.getName();
                if (name.length() > 0)
                    filename = name;
                break;

            case "content":
                // Try to find a name for the file
                Cursor cursor = resolver.query(uri,
                        new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA}, null, null, null);
                if (cursor == null) {
                    Log.e(TAG, "getFilenameFromUri: cursor was null");
                    return null;
                }
                if (!cursor.moveToFirst()) {
                    Log.e(TAG, "getFilenameFromUri: failed moving cursor to first row");
                    cursor.close();
                    return null;
                }
                int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                Log.i(TAG, "getFilenameFromUri: data at column " + dataIndex + ", name at column " + nameIndex);
                if (dataIndex >= 0) {
                    String path = cursor.getString(dataIndex);
                    if (path == null)
                        Log.w(TAG, "getFilenameFromUri: data column failed, path was null");
                    else
                        filename = (new File(path)).getName();
                    Log.i(TAG, "getFilenameFromUri: got from data column, path: " + path + ", name: " + filename);
                }
                if (filename == null && nameIndex >= 0) {
                    filename = cursor.getString(nameIndex);
                    Log.i(TAG, "getFilenameFromUri: got from name column: " + filename);
                }
                if (filename == null)
                    Log.e(TAG, "getFilenameFromUri: file name is still null after trying both columns");
                cursor.close();
                break;

            default:
                return null;
        }

        // If it doesn't end in the .xml extension, make it end in one
        if (filename != null && !filename.endsWith(".xml")) {

            // Do this by stripping the current extension if there is one...
            int lastIndex = filename.lastIndexOf(".");
            if (lastIndex > 0) filename = filename.substring(0, lastIndex);

            // ...and then adding .xml.
            filename = filename + ".xml";

        }

        return filename;
    }
}
