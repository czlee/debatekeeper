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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.Log;
import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import androidx.preference.PreferenceManager;

import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor;
import net.czlee.debatekeeper.debateformat.LanguageChooser;

import org.xml.sax.SAXException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manager for downloading information and format XML files from the Debatekeeper formats server.
 * This class:
 * <ul>
 *     <li>contacts the server to download a list of all format XML files available</li>
 *     <li>provides a class representing a downloadable format for {@link DownloadableFormatRecyclerAdapter}</li>
 *     <li>downloads requested format XML files and saves them to the user's device</li>
 * </ul>
 *
 * @author Chuan-Zheng Lee
 * @since 2021-09-28
 */
public class DebateFormatDownloadManager {

    private final String TAG = "DebateFormatDownload";

    private final Context mContext;
    private final ArrayList<DownloadableFormatEntry> mEntries;
    private final DownloadFormatsFragment.DownloadBinder mBinder;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    private final FormatXmlFilesManager mFilesManager;

    //******************************************************************************************
    // Public class
    //******************************************************************************************

    public static class DownloadableFormatEntry implements Comparable<DownloadableFormatEntry> {

        public enum DownloadState {
            NOT_DOWNLOADED,
            UPDATE_AVAILABLE,
            DOWNLOAD_IN_PROGRESS,
            DOWNLOADED
        }

        private static final String TAG = "DownloadFormatEntry";

        public int version = 0;
        @NonNull
        public String filename = "";
        @NonNull
        public String url = "";
        @NonNull
        public String name = "";
        @NonNull
        public String[] regions = new String[0];
        @NonNull
        public String[] usedAts = new String[0];
        @NonNull
        public String[] levels = new String[0];
        @NonNull
        public String description = "";
        @NonNull
        public DownloadState state = DownloadState.NOT_DOWNLOADED;
        public boolean expanded = true;

        /**
         * Given a format XML files manager and version extractor, checks the version number in the
         * existing file in the files manager (if any) and updates the <code>state</code> field
         * accordingly.
         *
         * @param filesManager     a {@link FormatXmlFilesManager}
         * @param versionExtractor a {@link DebateFormatFieldExtractor} that should be initialised
         *                         with <code>fieldName</code> as the version field.
         */
        void checkForExistingFile(FormatXmlFilesManager filesManager, DebateFormatFieldExtractor versionExtractor) {
            InputStream in;
            String versionStr;

            if (!filesManager.exists(this.filename)) {
                this.state = DownloadState.NOT_DOWNLOADED;
                return;
            }

            try {
                in = filesManager.open(this.filename);
                versionStr = versionExtractor.getFieldValue(in);
            } catch (IOException | SAXException e) {
                Log.e(TAG, "Couldn't get version from " + this.filename);
                this.state = DownloadState.NOT_DOWNLOADED;
                return;
            }

            int existingVersion;
            try {
                existingVersion = Integer.parseInt(versionStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid version in " + this.filename + ": " + versionStr);
                this.state = DownloadState.UPDATE_AVAILABLE;
                return;
            }

            if (this.version > existingVersion)
                this.state = DownloadState.UPDATE_AVAILABLE;
            else
                this.state = DownloadState.DOWNLOADED;
        }

        @Override
        public int compareTo(DownloadableFormatEntry o) {
            // when sorting, sort by name
            return this.name.compareTo(o.name);
        }

    }

    //******************************************************************************************
    // Private class
    //******************************************************************************************

    /**
     * Helper class that builds a {@link DownloadableFormatEntry} using a {@link JsonReader}. This
     * class mostly just helps to group these functions together, but it does maintain state in a
     * {@link LanguageChooser}.
     */
    private static class DownloadableFormatListBuilder {

        private static class FormatInfo {
            @NonNull
            public String name = "";
            @NonNull
            public String[] regions = new String[0];
            @NonNull
            public String[] usedAts = new String[0];
            @NonNull
            public String[] levels = new String[0];
            @NonNull
            public String description = "";
        }

        private final LanguageChooser mLangChooser;

        DownloadableFormatListBuilder() {
            mLangChooser = new LanguageChooser();
        }

        /**
         * Entry function. Builds a list of {@link DownloadableFormatEntry} objects from the given
         * {@link JsonReader}.
         *
         * See <a href="https://github.com/czlee/debatekeeper-formats/blob/main/v1/formats.json">
         * formats.json in the official formats repository</a> for an example of a
         * JSON file this expects to parse.
         *
         * @param reader a {@link JsonReader} pointing to the (start of a) JSON file
         * @return list of {@link DownloadableFormatEntry} objects
         * @throws IOException if there is an issue parsing the JSON
         */
        List<DownloadableFormatEntry> buildListFromJson(JsonReader reader) throws IOException {
            ArrayList<DownloadableFormatEntry> entries = new ArrayList<>();

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("formats"))
                    addFormatsFromJson(reader, entries);
                else
                    reader.skipValue();
            }
            reader.endObject();

            return entries;
        }

        /**
         * Adds entries to the given list from the given {@link JsonReader}. Expects to find an
         * array containing entry objects in the JSON file.
         */
        void addFormatsFromJson(JsonReader reader, List<DownloadableFormatEntry> entries) throws IOException {
            reader.beginArray();
            while (reader.hasNext()) {
                DownloadableFormatEntry entry = buildEntryFromJson(reader);
                // Log.d(TAG, "added: " + entry.styleName + " (" + entry.filename + ")");
                entries.add(entry);
            }
            reader.endArray();
        }


        /**
         * Builds a {@link DownloadableFormatEntry} from a {@link JsonReader}. Expects to find an
         * object representing an entry in the JSON file.
         */
        DownloadableFormatEntry buildEntryFromJson(JsonReader reader) throws IOException, IllegalStateException {
            DownloadableFormatEntry entry = new DownloadableFormatEntry();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "filename":
                        entry.filename = reader.nextString();
                        break;
                    case "info":
                        Map<String, FormatInfo> infoObjects = readInfoObjects(reader);
                        FormatInfo info = chooseInfo(infoObjects);
                        if (info != null) {
                            entry.name = info.name;
                            entry.regions = info.regions;
                            entry.levels = info.levels;
                            entry.usedAts = info.usedAts;
                            entry.description = info.description;
                        }
                        break;
                    case "version":
                        entry.version = reader.nextInt();
                        break;
                    case "url":
                        entry.url = reader.nextString();
                        break;
                    default:
                        reader.skipValue();
                }
            }
            reader.endObject();
            return entry;
        }

        /**
         * Reads the "info" object for all languages.
         *
         * @param reader a {@link JsonReader}
         * @return a {@link Map} mapping (possibly empty) language codes to {@link FormatInfo}
         * objects for that language.
         */
        Map<String, FormatInfo> readInfoObjects(JsonReader reader) throws IOException {
            HashMap<String, FormatInfo> infoObjects = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String lang = reader.nextName();
                FormatInfo info = new FormatInfo();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    switch (key) {
                        case "name":
                            info.name = reader.nextString();
                            break;
                        case "regions":
                            info.regions = readStringList(reader);
                            break;
                        case "levels":
                            info.levels = readStringList(reader);
                            break;
                        case "used-ats":
                            info.usedAts = readStringList(reader);
                            break;
                        case "description":
                            info.description = reader.nextString();
                            break;
                        default:
                            reader.skipValue();
                    }
                }
                reader.endObject();
                infoObjects.put(lang, info);
            }
            reader.endObject();
            return infoObjects;
        }

        FormatInfo chooseInfo(Map<String, FormatInfo> infoObjects) {
            List<String> languages = new ArrayList<>(infoObjects.keySet());
            String chosen = mLangChooser.choose(languages);
            if (chosen == null) return null;
            else return infoObjects.get(chosen);
        }

        String[] readStringList(JsonReader reader) throws IOException, IllegalStateException {
            ArrayList<String> strings = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                strings.add(reader.nextString());
            }
            reader.endArray();
            return strings.toArray(new String[0]);
        }

    }

    //******************************************************************************************
    // Public constructor
    //******************************************************************************************

    DebateFormatDownloadManager(Context context, DownloadFormatsFragment.DownloadBinder binder) {
        mContext = context;
        mBinder = binder;
        mEntries = new ArrayList<>();
        mFilesManager = new FormatXmlFilesManager(context);
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public ArrayList<DownloadableFormatEntry> getEntries() {
        return mEntries;
    }

    /**
     * Starts downloading the list of downloadable entries from the server, and notifies the binder
     * on the main thread when done. This should be safe to call from any thread.
     */
    public void startDownloadList() {
        initialiseThreads();
        mExecutorService.execute(() -> {
            try {
                List<DownloadableFormatEntry> newEntries = synchronousDownloadList();
                mMainThreadHandler.post(() -> replaceEntriesAndNotify(newEntries));
            } catch (MalformedJsonException e) {
                e.printStackTrace();
                String originalMessage = e.getMessage();
                String message = originalMessage;

                // Since we're using a strict JsonReader, the error is normally "Use JsonReader.setLenient(true)...".
                // If this is the case, try to extract the line and column number.
                if (originalMessage != null && originalMessage.startsWith("Use JsonReader.setLenient")) {
                    Matcher matcher = Pattern.compile("line \\d+ column \\d+").matcher(originalMessage);
                    if (matcher.find())
                        message = "Malformed JSON at " + matcher.group();
                }

                final String finalMessage = message;
                mMainThreadHandler.post(() -> mBinder.notifyJsonParseError(finalMessage));
            } catch (IOException e) {
                e.printStackTrace();
                String message = (e instanceof FileNotFoundException)
                        ? mContext.getString(R.string.formatDownloader_notFoundError, e.getLocalizedMessage())
                        : e.getLocalizedMessage();
                mMainThreadHandler.post(() -> mBinder.notifyListDownloadError(message));
            } catch (IllegalStateException | NumberFormatException e) {
                e.printStackTrace();
                mMainThreadHandler.post(() -> mBinder.notifyJsonParseError(e.getLocalizedMessage()));
            }
        });
    }

    /**
     * Starts downloading the format file represented by the given {@link DownloadableFormatEntry},
     * calling <code>holder.updateDownloadProgress()</code> on the main thread as it starts and
     * completes. This must be called from the main thread.
     *
     * @param entry  a {@link DownloadableFormatEntry}
     * @param holder a {@link DownloadableFormatRecyclerAdapter.ViewHolder}
     */
    public void startDownloadFile(DownloadableFormatEntry entry, DownloadableFormatRecyclerAdapter.ViewHolder holder) {
        initialiseThreads();
        final DownloadableFormatEntry.DownloadState originalState = entry.state;
        entry.state = DownloadableFormatEntry.DownloadState.DOWNLOAD_IN_PROGRESS;
        holder.updateDownloadProgress();
        mExecutorService.execute(() -> {
            try {
                synchronousDownloadFile(entry);
                mMainThreadHandler.post(() -> {
                    entry.state = DownloadableFormatEntry.DownloadState.DOWNLOADED;
                    holder.updateDownloadProgress();
                });
            } catch (IOException e) {
                e.printStackTrace();
                String message = (e instanceof FileNotFoundException)
                        ? mContext.getString(R.string.formatDownloader_notFoundError, e.getLocalizedMessage())
                        : e.getLocalizedMessage();
                mMainThreadHandler.post(() -> {
                    entry.state = originalState;
                    holder.updateDownloadProgress();
                    mBinder.showSnackbarError(entry.filename, message);
                });
            }
        });
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Initialises thread management if it hasn't already been initialised. Must be called before
     * any background thread is done. Does nothing if initialisation has already happened.
     */
    private void initialiseThreads() {
        if (mExecutorService == null)
            mExecutorService = Executors.newSingleThreadExecutor();
        if (mMainThreadHandler == null)
            mMainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    /**
     * Replaces <code>mEntries</code> with <code>newEntries</code>, and notifies the binder that the
     * data has changed. Must be run on the main thread.
     *
     * @param newEntries the new entries
     */
    private void replaceEntriesAndNotify(List<DownloadableFormatEntry> newEntries) {
        int originalSize = mEntries.size();
        mEntries.clear();
        mEntries.addAll(newEntries);
        mBinder.notifyAdapterItemsReplaced(originalSize, newEntries.size());
    }

    /**
     * Downloads the file represented by a {@link DownloadableFormatEntry}. This accesses the
     * network, so it must be run on a background thread. It also checks that the URL host matches
     * that of where the format came from.
     *
     * @param entry a {@link DownloadableFormatEntry}
     */
    private void synchronousDownloadFile(DownloadableFormatEntry entry) throws IOException {
        Log.i(TAG, "Downloading file from server: " + entry.filename);

        URL url = new URL(entry.url);
        Log.d(TAG, "url: " + url.toString());
        if (!verifyHostMatch(url)) {
            String message = mContext.getString(R.string.formatDownloader_wrongHostError, url.toString());
            throw new MalformedURLException(message);
        }

        URLConnection connection = url.openConnection();
        InputStream in = connection.getInputStream();

        mFilesManager.copy(in, entry.filename);
    }

    /**
     * Downloads the list of formats from the server. This accesses the network, so it must be run
     * on a background thread. The list it returns is unmodifiable, to try to protect against
     * accidental threading errors.
     */
    private List<DownloadableFormatEntry> synchronousDownloadList()
            throws IOException, IllegalStateException, NumberFormatException {
        Log.i(TAG, "Downloading list from server");

        URL url = getListUrl();
        URLConnection connection = url.openConnection();
        InputStream in = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(in);

        DownloadableFormatListBuilder listBuilder = new DownloadableFormatListBuilder();
        List<DownloadableFormatEntry> entries;

        try (JsonReader reader = new JsonReader(isr)) {
            entries = listBuilder.buildListFromJson(reader);
        } catch (IOException e) {
            return Collections.emptyList();
        }

        // Check for available updates
        DebateFormatFieldExtractor versionExtractor = new DebateFormatFieldExtractor(mContext, R.string.xml2elemName_version);
        for (DownloadableFormatEntry entry : entries)
            entry.checkForExistingFile(mFilesManager, versionExtractor);

        Collections.sort(entries);
        return Collections.unmodifiableList(entries);
    }

    private boolean verifyHostMatch(URL url) throws MalformedURLException {
        URL listUrl = getListUrl();
        return url.getHost().equals(listUrl.getHost());
    }

    private URL getListUrl() throws MalformedURLException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String userUrl = prefs.getString(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, null);
        if (userUrl == null) {
            String defaultUrl = mContext.getString(R.string.formatDownloader_list_defaultUrl);
            Log.i(TAG, "using default URL: " + defaultUrl);
            return new URL(defaultUrl);
        } else {
            Log.i(TAG, "using user-provided URL: " + userUrl);
            return new URL(userUrl);
        }
    }

}
