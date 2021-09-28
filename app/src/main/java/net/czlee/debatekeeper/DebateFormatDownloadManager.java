package net.czlee.debatekeeper;

import android.content.Context;
import android.util.JsonReader;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
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
    ExecutorService mExecutorService;

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
        initialise();
        mExecutorService.execute(() -> {
            try {
                synchronousDownloadList();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Downloads the list of formats from the server.
     */
    private void synchronousDownloadList() throws IOException, IllegalStateException {
        URL url = new URL(mContext.getString(R.string.formatDownloader_list_url));
        Log.d(TAG, "url: " + url.toString());
        URLConnection connection = url.openConnection();
        InputStream in = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(in);

        try (JsonReader reader = new JsonReader(isr)) {
            reader.beginArray();
            while (reader.hasNext()) {
                DownloadableFormatEntry entry = DownloadableFormatEntry.fromJsonReader(reader);
                Log.d(TAG, "added: " + entry.styleName + " (" + entry.filename + ")");
                mEntries.add(entry);
            }
            reader.endArray();
        }

        mBinder.notifyAdapterItemRangeInserted(0, mEntries.size());
    }

    private void initialise() {
        if (mExecutorService == null) {
            mExecutorService = Executors.newSingleThreadExecutor();
        }
    }


}
