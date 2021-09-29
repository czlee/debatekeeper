package net.czlee.debatekeeper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.Log;

import androidx.core.os.HandlerCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager for downloading information and format XML files from the Debatekeeper formats server.
 * This class:
 * <ul>
 *     <li>contacts the server to download a list of all format XML files available</li>
 *     <li>provides a class representing a downloadable format for {@link DownloadableFormatEntryRecyclerAdapter}</li>
 *     <li>downloads requested format XML files and saves them to the user's device</li>
 * </ul>
 *
 * @author Chuan-Zheng Lee
 * @since  2021-09-28
 */
public class DebateFormatDownloadManager {

    private final String TAG = "DebateFormatDownload";

    private final Context mContext;
    private final ArrayList<DownloadableFormatEntry> mEntries;
    private final DownloadFormatsFragment.DownloadBinder mBinder;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;

    //******************************************************************************************
    // Public class
    //******************************************************************************************

    public static class DownloadableFormatEntry {

        private static String TAG = "DownloadFormatEntry";
        public int version;
        public String filename;
        public String url;
        public String styleName;
        public String[] regions;
        public String[] usedAts;
        public String[] levels;
        public String description;

        static DownloadableFormatEntry fromJsonReader(JsonReader reader) throws IOException, IllegalStateException {
            DownloadableFormatEntry entry = new DownloadableFormatEntry();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "filename":
                        entry.filename = reader.nextString();
                        break;
                    case "name":
                        entry.styleName = reader.nextString();
                        break;
                    case "regions":
                        entry.regions = readStringList(reader);
                        break;
                    case "levels":
                        entry.levels = readStringList(reader);
                        break;
                    case "used-ats":
                        entry.usedAts = readStringList(reader);
                        break;
                    case "description":
                        entry.description = reader.nextString();
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

        static String[] readStringList(JsonReader reader) throws IOException, IllegalStateException {
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
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    public ArrayList<DownloadableFormatEntry> getEntries() {
        return mEntries;
    }

    public void startDownloadList() {
        initialiseThreads();
        String url = mContext.getString(R.string.formatDownloader_list_url);
        mExecutorService.execute(() -> {
            try {
                List<DownloadableFormatEntry> newEntries = synchronousDownloadList(url);
                mMainThreadHandler.post(() -> replaceEntriesAndNotify(newEntries));
            } catch (FileNotFoundException e) {
                // prepend "Not found:" to be a little clearer
                e.printStackTrace();
                String message = mContext.getString(R.string.formatDownloader_notFoundError, e.getMessage());
                mMainThreadHandler.post(() -> mBinder.notifyListDownloadError(message));
            } catch (IOException e) {
                e.printStackTrace();
                mMainThreadHandler.post(() -> mBinder.notifyListDownloadError(e.getMessage()));
            } catch (IllegalStateException|NumberFormatException e) {
                e.printStackTrace();
                mMainThreadHandler.post(() -> mBinder.notifyJsonParseError(e.getMessage()));
            }
        });
    }

    /**
     * Replaces <code>mEntries</code> with <code>newEntries</code>, and notifies the binder that
     * the data has changed. Must be run on the main thread.
     * @param newEntries the new entries
     */
    private void replaceEntriesAndNotify(List<DownloadableFormatEntry> newEntries) {
        int originalSize = mEntries.size();
        mEntries.clear();
        mEntries.addAll(newEntries);
        mBinder.notifyAdapterItemsReplaced(originalSize, newEntries.size());
    }

    /**
     * Downloads the list of formats from the server. This accesses the network, so it must be run
     * on a background thread. The list it returns is unmodifiable, to try to protect against
     * accidental threading errors.
     */
    private List<DownloadableFormatEntry> synchronousDownloadList(String repositoryUrl) throws IOException, IllegalStateException {
        Log.i(TAG, "Downloading list from server");

        ArrayList<DownloadableFormatEntry> entries = new ArrayList<>();

        URL url = new URL(repositoryUrl);
        Log.d(TAG, "url: " + url.toString());
        URLConnection connection = url.openConnection();
        InputStream in = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(in);

        try (JsonReader reader = new JsonReader(isr)) {
            reader.beginArray();
            while (reader.hasNext()) {
                DownloadableFormatEntry entry = DownloadableFormatEntry.fromJsonReader(reader);
                Log.d(TAG, "added: " + entry.styleName + " (" + entry.filename + ")");
                entries.add(entry);
            }
            reader.endArray();
        }

        return Collections.unmodifiableList(entries);
    }

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

}
