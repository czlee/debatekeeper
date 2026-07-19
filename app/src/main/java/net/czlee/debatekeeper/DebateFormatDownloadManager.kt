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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.Log
import android.util.MalformedJsonException
import androidx.core.os.HandlerCompat
import androidx.preference.PreferenceManager
import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor
import net.czlee.debatekeeper.debateformat.LanguageChooser
import org.xml.sax.SAXException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Manager for downloading information and format XML files from the Debatekeeper formats server.
 * This class:
 *
 *  - contacts the server to download a list of all format XML files available
 *  - provides a class representing a downloadable format for [DownloadableFormatRecyclerAdapter]
 *  - downloads requested format XML files and saves them to the user's device
 *
 * @author Chuan-Zheng Lee
 * @since 2021-09-28
 */
class DebateFormatDownloadManager internal constructor(
        private val mContext: Context,
        private val mBinder: DownloadFormatsFragment.DownloadBinder) {

    private val TAG = "DebateFormatDownload"

    val entries = ArrayList<DownloadableFormatEntry>()
    private var mExecutorService: ExecutorService? = null
    private var mMainThreadHandler: Handler? = null
    private val mFilesManager = FormatXmlFilesManager(mContext)

    //******************************************************************************************
    // Public class
    //******************************************************************************************

    class DownloadableFormatEntry : Comparable<DownloadableFormatEntry> {

        enum class DownloadState {
            NOT_DOWNLOADED,
            UPDATE_AVAILABLE,
            DOWNLOAD_IN_PROGRESS,
            DOWNLOADED
        }

        @JvmField
        var version = 0
        @JvmField
        var filename = ""
        @JvmField
        var url = ""
        @JvmField
        var name = ""
        @JvmField
        var regions = arrayOf<String>()
        @JvmField
        var usedAts = arrayOf<String>()
        @JvmField
        var levels = arrayOf<String>()
        @JvmField
        var description = ""
        @JvmField
        var state = DownloadState.NOT_DOWNLOADED
        @JvmField
        var expanded = false

        /**
         * Given a format XML files manager and version extractor, checks the version number in the
         * existing file in the files manager (if any) and updates the [state] field
         * accordingly.
         *
         * @param filesManager     a [FormatXmlFilesManager]
         * @param versionExtractor a [DebateFormatFieldExtractor] that should be initialised
         *                         with `fieldName` as the version field.
         */
        internal fun checkForExistingFile(filesManager: FormatXmlFilesManager,
                                          versionExtractor: DebateFormatFieldExtractor) {
            if (!filesManager.exists(filename)) {
                state = DownloadState.NOT_DOWNLOADED
                return
            }

            val versionStr: String?
            try {
                val `in` = filesManager.open(filename)
                versionStr = versionExtractor.getFieldValue(`in`)
            } catch (e: IOException) {
                Log.e(TAG, "Couldn't get version from $filename")
                state = DownloadState.NOT_DOWNLOADED
                return
            } catch (e: SAXException) {
                Log.e(TAG, "Couldn't get version from $filename")
                state = DownloadState.NOT_DOWNLOADED
                return
            }

            if (versionStr == null) {
                Log.e(TAG, "No version found in $filename")
                state = DownloadState.UPDATE_AVAILABLE
                return
            }

            val existingVersion: Int
            try {
                existingVersion = versionStr.toInt()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Invalid version in $filename: $versionStr")
                state = DownloadState.UPDATE_AVAILABLE
                return
            }

            state = if (version > existingVersion)
                DownloadState.UPDATE_AVAILABLE
            else
                DownloadState.DOWNLOADED
        }

        override fun compareTo(other: DownloadableFormatEntry): Int {
            // when sorting, sort by name
            return name.compareTo(other.name)
        }

        companion object {
            private const val TAG = "DownloadFormatEntry"
        }
    }

    //******************************************************************************************
    // Private class
    //******************************************************************************************

    /**
     * Helper class that builds a [DownloadableFormatEntry] using a [JsonReader]. This
     * class mostly just helps to group these functions together, but it does maintain state in a
     * [LanguageChooser].
     */
    private class DownloadableFormatListBuilder {

        private class FormatInfo {
            var name = ""
            var regions = arrayOf<String>()
            var usedAts = arrayOf<String>()
            var levels = arrayOf<String>()
            var description = ""
        }

        private val mLangChooser = LanguageChooser()

        /**
         * Entry function. Builds a list of [DownloadableFormatEntry] objects from the given
         * [JsonReader].
         *
         * See [formats.json in the official formats repository](https://github.com/czlee/debatekeeper-formats/blob/main/v1/formats.json)
         * for an example of a JSON file this expects to parse.
         *
         * @param reader a [JsonReader] pointing to the (start of a) JSON file
         * @return list of [DownloadableFormatEntry] objects
         * @throws IOException if there is an issue parsing the JSON
         */
        @Throws(IOException::class)
        fun buildListFromJson(reader: JsonReader): MutableList<DownloadableFormatEntry> {
            val entries = ArrayList<DownloadableFormatEntry>()

            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "formats")
                    addFormatsFromJson(reader, entries)
                else
                    reader.skipValue()
            }
            reader.endObject()

            return entries
        }

        /**
         * Adds entries to the given list from the given [JsonReader]. Expects to find an
         * array containing entry objects in the JSON file.
         */
        @Throws(IOException::class)
        fun addFormatsFromJson(reader: JsonReader, entries: MutableList<DownloadableFormatEntry>) {
            reader.beginArray()
            while (reader.hasNext()) {
                val entry = buildEntryFromJson(reader)
                // Log.d(TAG, "added: " + entry.styleName + " (" + entry.filename + ")");
                entries.add(entry)
            }
            reader.endArray()
        }

        /**
         * Builds a [DownloadableFormatEntry] from a [JsonReader]. Expects to find an
         * object representing an entry in the JSON file.
         */
        @Throws(IOException::class, IllegalStateException::class)
        fun buildEntryFromJson(reader: JsonReader): DownloadableFormatEntry {
            val entry = DownloadableFormatEntry()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "filename" -> entry.filename = reader.nextString()
                    "info" -> {
                        val infoObjects = readInfoObjects(reader)
                        val info = chooseInfo(infoObjects)
                        if (info != null) {
                            entry.name = info.name
                            entry.regions = info.regions
                            entry.levels = info.levels
                            entry.usedAts = info.usedAts
                            entry.description = info.description
                        }
                    }
                    "version" -> entry.version = reader.nextInt()
                    "url" -> entry.url = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return entry
        }

        /**
         * Reads the "info" object for all languages.
         *
         * @param reader a [JsonReader]
         * @return a [Map] mapping (possibly empty) language codes to [FormatInfo]
         * objects for that language.
         */
        @Throws(IOException::class)
        fun readInfoObjects(reader: JsonReader): Map<String, FormatInfo> {
            val infoObjects = HashMap<String, FormatInfo>()
            reader.beginObject()
            while (reader.hasNext()) {
                val lang = reader.nextName()
                val info = FormatInfo()
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> info.name = reader.nextString()
                        "regions" -> info.regions = readStringList(reader)
                        "levels" -> info.levels = readStringList(reader)
                        "used-ats" -> info.usedAts = readStringList(reader)
                        "description" -> info.description = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                infoObjects[lang] = info
            }
            reader.endObject()
            return infoObjects
        }

        fun chooseInfo(infoObjects: Map<String, FormatInfo>): FormatInfo? {
            val languages = ArrayList(infoObjects.keys)
            val chosen = mLangChooser.choose(languages) ?: return null
            return infoObjects[chosen]
        }

        @Throws(IOException::class, IllegalStateException::class)
        fun readStringList(reader: JsonReader): Array<String> {
            val strings = ArrayList<String>()
            reader.beginArray()
            while (reader.hasNext()) {
                strings.add(reader.nextString())
            }
            reader.endArray()
            return strings.toTypedArray()
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    /**
     * Starts downloading the list of downloadable entries from the server, and notifies the binder
     * on the main thread when done. This should be safe to call from any thread.
     */
    fun startDownloadList() {
        initialiseThreads()
        mExecutorService!!.execute {
            try {
                val newEntries = synchronousDownloadList()
                mMainThreadHandler!!.post { replaceEntriesAndNotify(newEntries) }
            } catch (e: MalformedJsonException) {
                e.printStackTrace()
                val originalMessage = e.message
                var message = originalMessage

                // Since we're using a strict JsonReader, the error is normally "Use
                // JsonReader.setLenient(true)...". If this is the case, try to extract the line
                // and column number.
                if (originalMessage != null && originalMessage.startsWith("Use JsonReader.setLenient")) {
                    val matcher = Pattern.compile("line \\d+ column \\d+").matcher(originalMessage)
                    if (matcher.find())
                        message = "Malformed JSON at " + matcher.group()
                }

                val finalMessage = message
                mMainThreadHandler!!.post { mBinder.notifyJsonParseError(finalMessage) }
            } catch (e: IOException) {
                e.printStackTrace()
                val message = if (e is FileNotFoundException)
                    mContext.getString(R.string.formatDownloader_notFoundError, e.localizedMessage)
                else
                    e.localizedMessage
                mMainThreadHandler!!.post { mBinder.notifyListDownloadError(message) }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                mMainThreadHandler!!.post { mBinder.notifyJsonParseError(e.localizedMessage) }
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                mMainThreadHandler!!.post { mBinder.notifyJsonParseError(e.localizedMessage) }
            }
        }
    }

    /**
     * Starts downloading the format file represented by the given [DownloadableFormatEntry],
     * calling `holder.updateDownloadProgress()` on the main thread as it starts and
     * completes. This must be called from the main thread.
     *
     * @param entry  a [DownloadableFormatEntry]
     * @param holder a [DownloadableFormatRecyclerAdapter.ViewHolder]
     */
    fun startDownloadFile(entry: DownloadableFormatEntry,
                          holder: DownloadableFormatRecyclerAdapter.ViewHolder) {
        initialiseThreads()
        val originalState = entry.state
        entry.state = DownloadableFormatEntry.DownloadState.DOWNLOAD_IN_PROGRESS
        holder.updateDownloadProgress()
        mExecutorService!!.execute {
            try {
                synchronousDownloadFile(entry)
                mMainThreadHandler!!.post {
                    entry.state = DownloadableFormatEntry.DownloadState.DOWNLOADED
                    holder.updateDownloadProgress()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                val message = if (e is FileNotFoundException)
                    mContext.getString(R.string.formatDownloader_notFoundError, e.localizedMessage)
                else
                    e.localizedMessage
                mMainThreadHandler!!.post {
                    entry.state = originalState
                    holder.updateDownloadProgress()
                    mBinder.showSnackbarError(entry.filename, message)
                }
            }
        }
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Initialises thread management if it hasn't already been initialised. Must be called before
     * any background thread is done. Does nothing if initialisation has already happened.
     */
    private fun initialiseThreads() {
        if (mExecutorService == null)
            mExecutorService = Executors.newSingleThreadExecutor()
        if (mMainThreadHandler == null)
            mMainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    }

    /**
     * Replaces [entries] with `newEntries`, and notifies the binder that the
     * data has changed. Must be run on the main thread.
     *
     * @param newEntries the new entries
     */
    private fun replaceEntriesAndNotify(newEntries: List<DownloadableFormatEntry>) {
        val originalSize = entries.size
        entries.clear()
        entries.addAll(newEntries)
        mBinder.notifyAdapterItemsReplaced(originalSize, newEntries.size)
    }

    /**
     * Downloads the file represented by a [DownloadableFormatEntry]. This accesses the
     * network, so it must be run on a background thread. It also checks that the URL host matches
     * that of where the format came from.
     *
     * @param entry a [DownloadableFormatEntry]
     */
    @Throws(IOException::class)
    private fun synchronousDownloadFile(entry: DownloadableFormatEntry) {
        Log.i(TAG, "Downloading file from server: " + entry.filename)

        val url = URL(entry.url)
        Log.d(TAG, "url: $url")
        if (!verifyHostMatch(url)) {
            val message = mContext.getString(R.string.formatDownloader_wrongHostError,
                    url.toString())
            throw MalformedURLException(message)
        }

        val connection = url.openConnection()
        val `in` = connection.getInputStream()

        mFilesManager.copy(`in`, entry.filename)
    }

    /**
     * Downloads the list of formats from the server. This accesses the network, so it must be run
     * on a background thread. The list it returns is unmodifiable, to try to protect against
     * accidental threading errors.
     */
    @Throws(IOException::class, IllegalStateException::class, NumberFormatException::class)
    private fun synchronousDownloadList(): List<DownloadableFormatEntry> {
        Log.i(TAG, "Downloading list from server")

        val url = getListUrl()
        val connection = url.openConnection()
        val `in`: InputStream = connection.getInputStream()
        val isr = InputStreamReader(`in`)

        val listBuilder = DownloadableFormatListBuilder()
        val entries: MutableList<DownloadableFormatEntry>

        JsonReader(isr).use { reader ->
            entries = listBuilder.buildListFromJson(reader)
        }

        // Check for available updates
        val versionExtractor = DebateFormatFieldExtractor(mContext, R.string.xml2elemName_version)
        for (entry in entries)
            entry.checkForExistingFile(mFilesManager, versionExtractor)

        Collections.sort(entries)
        return Collections.unmodifiableList(entries)
    }

    @Throws(MalformedURLException::class)
    private fun verifyHostMatch(url: URL): Boolean {
        val listUrl = getListUrl()
        return url.host == listUrl.host
    }

    @Throws(MalformedURLException::class)
    private fun getListUrl(): URL {
        val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
        val userUrl =
                prefs.getString(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, null)
        return if (userUrl == null) {
            val defaultUrl = mContext.getString(R.string.formatDownloader_list_defaultUrl)
            Log.i(TAG, "using default URL: $defaultUrl")
            URL(defaultUrl)
        } else {
            Log.i(TAG, "using user-provided URL: $userUrl")
            URL(userUrl)
        }
    }
}
