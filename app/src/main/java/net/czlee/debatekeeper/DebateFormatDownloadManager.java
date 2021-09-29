package net.czlee.debatekeeper;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.Log;

import androidx.core.os.HandlerCompat;

import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.Normalizer;
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
    private final String mListUrl;
    private final ArrayList<DownloadableFormatEntry> mEntries;
    private final DownloadFormatsFragment.DownloadBinder mBinder;
    private ExecutorService mExecutorService;
    private Handler mMainThreadHandler;
    private final FormatXmlFilesManager mFilesManager;

    //******************************************************************************************
    // Public class
    //******************************************************************************************

    public static class DownloadableFormatEntry {

        public enum DownloadState {
            NOT_DOWNLOADED,
            UPDATE_AVAILABLE,
            DOWNLOAD_IN_PROGRESS,
            DOWNLOADED
        }

        private static final String TAG = "DownloadFormatEntry";

        public int version;
        public String filename;
        public String url;
        public String styleName;
        public String[] regions;
        public String[] usedAts;
        public String[] levels;
        public String description;
        public DownloadState state = DownloadState.NOT_DOWNLOADED;
        public boolean expanded = true;

        /**
         * Constructs a new <code>DownloadableFormatEntry</code> from a {@link JsonReader}.
         */
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

            if (filesManager.getLocation(this.filename) == FormatXmlFilesManager.LOCATION_NOT_FOUND) {
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
    }

    //******************************************************************************************
    // Public constructor
    //******************************************************************************************

    DebateFormatDownloadManager(Context context, DownloadFormatsFragment.DownloadBinder binder) {
        mContext = context;
        mListUrl = context.getString(R.string.formatDownloader_list_url);
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
            } catch (IOException e) {
                e.printStackTrace();
                String message = (e instanceof FileNotFoundException)
                        ? mContext.getString(R.string.formatDownloader_notFoundError, e.getMessage())
                        : e.getMessage();
                mMainThreadHandler.post(() -> mBinder.notifyListDownloadError(message));
            } catch (IllegalStateException | NumberFormatException e) {
                e.printStackTrace();
                mMainThreadHandler.post(() -> mBinder.notifyJsonParseError(e.getMessage()));
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
                        ? mContext.getString(R.string.formatDownloader_notFoundError, e.getMessage())
                        : e.getMessage();
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
     * Downlaods the file represented by a {@link DownloadableFormatEntry}. This accesses the network,
     * so it must be run on a background thread. It also checks that the URL host matches that of
     * where the format came from.
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

        ArrayList<DownloadableFormatEntry> entries = new ArrayList<>();
        DebateFormatFieldExtractor versionExtractor = new DebateFormatFieldExtractor(mContext, R.string.xml2elemName_version);

        URL url = new URL(mListUrl);
        Log.d(TAG, "url: " + url.toString());
        URLConnection connection = url.openConnection();
        InputStream in = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(in);

        try (JsonReader reader = new JsonReader(isr)) {
            reader.beginArray();
            while (reader.hasNext()) {
                DownloadableFormatEntry entry = DownloadableFormatEntry.fromJsonReader(reader);
                Log.d(TAG, "added: " + entry.styleName + " (" + entry.filename + ")");
                entry.checkForExistingFile(mFilesManager, versionExtractor);
                entries.add(entry);
            }
            reader.endArray();
        }

        return Collections.unmodifiableList(entries);
    }

    private boolean verifyHostMatch(URL url) throws MalformedURLException {
        URL listUrl = new URL(mListUrl);
        return url.getHost().equals(listUrl.getHost());
    }

}
