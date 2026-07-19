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

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import net.czlee.debatekeeper.databinding.FragmentFormatChooserBinding
import net.czlee.debatekeeper.databinding.ViewFormatFullBinding
import net.czlee.debatekeeper.databinding.ViewFormatShortBinding
import net.czlee.debatekeeper.debateformat.DebateFormatFieldExtractor
import net.czlee.debatekeeper.debateformat.DebateFormatInfo
import net.czlee.debatekeeper.debateformat.DebateFormatInfoForSchema2
import net.czlee.debatekeeper.debateformat.XmlUtilities
import net.czlee.debatekeeper.debateformat.XmlUtilities.IllegalSchemaVersionException
import org.xml.sax.SAXException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * This fragment displays a list of formats for the user to choose from. It
 * returns a file name to the calling fragment.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-17
 */
class FormatChooserFragment : Fragment() {

    private lateinit var mViewBinding: FragmentFormatChooserBinding
    private lateinit var mFilesManager: FormatXmlFilesManager
    private lateinit var mStylesListView: ListView
    private lateinit var mImportFileLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var mStylesArrayAdapter: DebateFormatEntryArrayAdapter
    private val mStylesList = ArrayList<DebateFormatListEntry>()

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    /**
     * Passive data class storing a filename and a style name.
     */
    class DebateFormatListEntry internal constructor(val filename: String, val styleName: String) {

        override fun toString(): String {
            return styleName
        }
    }

    /**
     * Interface to [DebateFormatEntryArrayAdapter]. Provides a method for
     * the `DebateFormatEntryArrayAdapter` to request the selected
     * position and whether "more details" mode is on.
     *
     * @author Chuan-Zheng Lee
     */
    inner class FormatChooserFragmentBinder {
        internal fun getDetailsButtonOnClickListener(filename: String): View.OnClickListener {
            return View.OnClickListener {
                val fragment = MoreDetailsDialogFragment.newInstance(filename)
                fragment.show(childFragmentManager, DIALOG_TAG_MORE_DETAILS + filename)
            }
        }

        /**
         * The position of the currently checked item.
         */
        internal val selectedPosition: Int
            get() = mStylesListView.checkedItemPosition

        @Throws(IOException::class, SAXException::class)
        internal fun populateBasicInfo(vb: ViewFormatShortBinding, filename: String) {
            val dfi = getDebateFormatInfo(filename)
            vb.viewFormatRegionValue.text = concatenate(dfi.regions)
            vb.viewFormatLevelValue.text = concatenate(dfi.levels)
            vb.viewFormatUsedAtValue.text = concatenate(dfi.usedAts)
            vb.viewFormatDescValue.text = dfi.description
        }
    }

    //******************************************************************************************
    // Dialog fragments
    //******************************************************************************************

    class ConfirmDeleteDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val filename = requireArguments().getString(DIALOG_ARGUMENT_FILE_NAME)

            val activity = requireActivity()
            val parent = parentFragment as FormatChooserFragment
            val builder = AlertDialog.Builder(activity)

            builder.setMessage(
                    getString(R.string.formatChooser_dialog_confirmDelete_message, filename))
                    .setPositiveButton(R.string.formatChooser_dialog_confirmDelete_yes) { _, _ ->
                        parent.deleteFile(filename!!)
                    }
                    .setNegativeButton(R.string.formatChooser_dialog_confirmDelete_no, null)

            return builder.create()
        }

        companion object {
            internal fun newInstance(filename: String): ConfirmDeleteDialogFragment {
                val fragment = ConfirmDeleteDialogFragment()
                val args = Bundle()
                args.putString(DIALOG_ARGUMENT_FILE_NAME, filename)
                fragment.arguments = args
                return fragment
            }
        }
    }

    class ConfirmOverwriteDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment as FormatChooserFragment
            val args = requireArguments()
            val activity = requireActivity()

            val uri = BundleCompat.getParcelable(args, DIALOG_ARGUMENT_IMPORT_URI, Uri::class.java)
            val filename = args.getString(DIALOG_ARGUMENT_FILE_NAME)
            val styleName = args.getString(DIALOG_ARGUMENT_STYLE_NAME)

            val builder = AlertDialog.Builder(activity)

            builder.setMessage(getString(R.string.formatChooser_dialog_confirmOverwrite_message,
                    filename, styleName))
                    .setPositiveButton(R.string.formatChooser_dialog_confirmOverwrite_yes) { _, _ ->
                        parent.importIncomingFile(uri!!, filename!!)
                    }
                    .setNegativeButton(R.string.formatChooser_dialog_confirmOverwrite_no, null)

            return builder.create()
        }

        companion object {
            internal fun newInstance(uri: Uri, filename: String,
                                     existingStyleName: String): ConfirmOverwriteDialogFragment {
                val fragment = ConfirmOverwriteDialogFragment()
                val args = Bundle()
                args.putParcelable(DIALOG_ARGUMENT_IMPORT_URI, uri)
                args.putString(DIALOG_ARGUMENT_FILE_NAME, filename)
                args.putString(DIALOG_ARGUMENT_STYLE_NAME, existingStyleName)
                fragment.arguments = args
                return fragment
            }
        }
    }

    class MoreDetailsDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val filename = requireArguments().getString(DIALOG_ARGUMENT_FILE_NAME)!!

            val activity = requireActivity()
            val parent = parentFragment as FormatChooserFragment
            val builder = AlertDialog.Builder(activity)

            val binding = ViewFormatFullBinding.inflate(layoutInflater)

            var dfi: DebateFormatInfo? = null
            try {
                dfi = parent.getDebateFormatInfo(filename)
            } catch (e: Exception) {
                when (e) {
                    is IOException, is SAXException -> {
                        val message = if (e is FileNotFoundException)
                            getString(R.string.viewFormat_error_fileNotFound, e.localizedMessage)
                        else
                            e.localizedMessage

                        populateFileInfo(binding, filename, null)
                        binding.viewFormatDetailsGroup.visibility = View.GONE
                        binding.viewFormatLanguagesGroup.visibility = View.GONE
                        binding.viewFormatErrorLabel.visibility = View.VISIBLE
                        binding.viewFormatErrorValue.visibility = View.VISIBLE
                        binding.viewFormatErrorValue.text = message
                    }
                    else -> throw e
                }
            }

            if (dfi != null) {
                val schemaVersion = dfi.schemaVersion
                populateFileInfo(binding, filename, schemaVersion)
                populateBasicInfo(binding, dfi)
                populatePrepTimeInfo(binding, dfi)
                val speechDescriptions = dfi.speechFormatDescriptions
                populateTwoColumnTable(binding.viewFormatTableSpeechTypes,
                        R.layout.speech_type_row, dfi.speechFormatDescriptions)
                populateTwoColumnTable(binding.viewFormatTableSpeeches, R.layout.speech_row,
                        dfi.getSpeeches(speechDescriptions))
                binding.viewFormatTitle.text = dfi.name
            }
            builder.setCancelable(true)

            // Configure the buttons
            binding.viewFormatShareButton.setOnClickListener {
                parent.shareDebateFormatFile(filename)
            }
            binding.viewFormatDeleteButton.setOnClickListener {
                val fragment = ConfirmDeleteDialogFragment.newInstance(filename)
                fragment.show(parentFragmentManager, DIALOG_TAG_CONFIRM_DELETION + filename)
            }

            val dialog = builder.create()
            dialog.setView(binding.root, 0, 10, 10, 15)
            return dialog
        }

        /**
         * Populates a View with information about a given file
         * @param vb the [ViewFormatFullBinding] to populate
         * @param filename the file name
         */
        private fun populateFileInfo(vb: ViewFormatFullBinding, filename: String,
                                     schemaVersion: String?) {

            // Display its schema version if it's not the current version
            if (schemaVersion != null) {
                var comparison = 0
                var schemaVersionTextValue: String? = null
                try {
                    comparison = XmlUtilities.compareSchemaVersions(schemaVersion,
                            CURRENT_SCHEMA_VERSION)
                } catch (e: IllegalSchemaVersionException) {
                    schemaVersionTextValue =
                            getString(R.string.viewFormat_invalidSchemaVersion, schemaVersion)
                }
                if (schemaVersionTextValue == null) {
                    if (comparison > 0)
                        schemaVersionTextValue =
                                getString(R.string.viewFormat_futureSchemaVersion, schemaVersion)
                    else if (comparison < 0)
                        schemaVersionTextValue =
                                getString(R.string.viewFormat_outdatedSchemaVersion, schemaVersion)
                }
                if (schemaVersionTextValue != null) {
                    val schemaVersionText = vb.viewFormatSchemaVersionValue
                    schemaVersionText.text = schemaVersionTextValue
                    schemaVersionText.visibility = View.VISIBLE
                }
            }

            vb.viewFormatFileNameValue.text = filename
        }

        private fun populateBasicInfo(vb: ViewFormatFullBinding, dfi: DebateFormatInfo) {
            vb.viewFormatRegionValue.text = concatenate(dfi.regions)
            vb.viewFormatLevelValue.text = concatenate(dfi.levels)
            vb.viewFormatUsedAtValue.text = concatenate(dfi.usedAts)
            vb.viewFormatDescValue.text = dfi.description

            val languages = dfi.displayLanguages
            if (languages.isEmpty()) {
                vb.viewFormatLanguagesGroup.visibility = View.GONE
            } else {
                vb.viewFormatLanguagesGroup.visibility = View.VISIBLE
                vb.viewFormatLanguagesValue.text = concatenate(languages)
            }
        }

        /**
         * Populates a table from a list of String arrays.
         * @param table A `TableLayout`
         * @param rowResId A resource ID pointing to a `TableRow` **layout file**.
         * (Not the `TableRow` itself.)
         * TableRow must have at least two TextView elements, which must have IDs "text1" and
         * "text2".
         * @param list the list of String arrays.  Each array must have two elements.
         */
        private fun populateTwoColumnTable(table: TableLayout, rowResId: Int,
                                           list: List<Array<String>>) {
            for (rowText in list) {
                val row = View.inflate(activity, rowResId, null) as TableRow
                row.findViewById<TextView>(R.id.text1).text = rowText[0] + " "
                row.findViewById<TextView>(R.id.text2).text = rowText[1] + " "
                table.addView(row)
            }
        }

        private fun populatePrepTimeInfo(vb: ViewFormatFullBinding, dfi: DebateFormatInfo) {
            val prepTimeDescription = dfi.prepTimeDescription

            // If there is prep time, populate the view.
            if (prepTimeDescription != null)
                vb.viewFormatPrepTimeValue.text = prepTimeDescription

            // Otherwise, hide the whole row.
            else {
                vb.viewFormatPrepTimeLabel.visibility = View.GONE
                vb.viewFormatPrepTimeValue.visibility = View.GONE
            }
        }

        companion object {
            internal fun newInstance(filename: String): MoreDetailsDialogFragment {
                val fragment = MoreDetailsDialogFragment()
                val args = Bundle()
                args.putString(DIALOG_ARGUMENT_FILE_NAME, filename)
                fragment.arguments = args
                return fragment
            }
        }
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private inner class FormatChooserMenuItemClickListener : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.formatChooser_actionBar_ok -> {
                    confirmSelectionAndReturn()
                    return true
                }
                R.id.formatChooser_actionBar_searchOnline -> {
                    val action = FormatChooserFragmentDirections.actionGoToDownloads()
                    NavHostFragment.findNavController(this@FormatChooserFragment).navigate(action)
                    return true
                }
                R.id.formatChooser_actionBar_importFile -> {
                    mImportFileLauncher.launch(arrayOf("text/xml", "application/xml"))
                    return true
                }
                R.id.formatChooser_actionBar_learnMore -> {
                    val uri = Uri.parse(getString(R.string.formats_learnMoreUrl))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    return true
                }
                else -> return false
            }
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mImportFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            importIncomingFilePrompt(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mViewBinding = FragmentFormatChooserBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mViewBinding.toolbarFormatChooser.setOnMenuItemClickListener(
                FormatChooserMenuItemClickListener())
        mViewBinding.toolbarFormatChooser.setNavigationOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }

        val context = requireContext()
        mFilesManager = FormatXmlFilesManager(context)
        mStylesArrayAdapter = DebateFormatEntryArrayAdapter(context, mStylesList,
                FormatChooserFragmentBinder())

        // Configure the ListView
        mStylesListView = mViewBinding.formatChooserStylesList
        mStylesListView.adapter = mStylesArrayAdapter
        mStylesListView.setOnItemClickListener { _, _, _, _ ->
            mStylesArrayAdapter.notifyDataSetChanged()
        }

        // Populate the styles list
        populateStylesList()

        // Show the download banner if it hasn't been dismissed
        showDownloadHelpBanner(context)

        // Select and scroll to the incoming selection (if existent)
        val incomingFilename = FormatChooserFragmentArgs.fromBundle(requireArguments()).xmlFileName
        setSelectionAndScroll(incomingFilename)
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    /**
     * Confirms and handles the selection appropriately, and returns to the previous fragment.
     */
    private fun confirmSelectionAndReturn() {
        val selectedFilename = getSelectedFilename()
        val incomingFilename = FormatChooserFragmentArgs.fromBundle(requireArguments()).xmlFileName

        val result = Bundle()

        if (selectedFilename != null && selectedFilename == incomingFilename) {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_UNCHANGED)
            result.putString(BUNDLE_KEY_XML_FILE_NAME, null)
            Log.v(TAG, "Returning no file, selection wasn't changed")

        } else if (selectedFilename == null) {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_NO_SELECTION)
            result.putString(BUNDLE_KEY_XML_FILE_NAME, null)
            Log.e(TAG, "Returning no file, no selection found")

        } else {
            result.putInt(BUNDLE_KEY_RESULT, RESULT_SUCCESS)
            result.putString(BUNDLE_KEY_XML_FILE_NAME, selectedFilename)
            Log.v(TAG, "File name is $selectedFilename")
        }

        parentFragmentManager.setFragmentResult(REQUEST_KEY_CHOOSE_FORMAT, result)
        NavHostFragment.findNavController(this).navigateUp()
    }

    /**
     * Given a filename, returns the index in the styles list where the entry is.
     * @param filename the file name of the style
     * @return integer between 0 and `mStylesList.length - 1`, or
     * [ListView.INVALID_POSITION] if the item could not be found.
     */
    private fun convertFilenameToIndex(filename: String?): Int {
        if (filename != null) {
            for ((i, entry) in mStylesList.withIndex()) {
                if (filename == entry.filename) return i
            }
        }
        return ListView.INVALID_POSITION
    }

    /**
     * Given an index in the styles list, returns the filename.
     * @param index the index in the styles list
     * @return filename, or null if the index was invalid.
     */
    private fun convertIndexToFilename(index: Int): String? {
        if (index < 0 || index >= mStylesList.size)
            return null
        return mStylesList[index].filename
    }

    /**
     * Carry out the required work to delete the given file. Called after the user has confirmed
     * the deletion in [ConfirmDeleteDialogFragment].
     * @param filename file to delete
     */
    private fun deleteFile(filename: String) {
        // Dismiss the details fragment (whether or not this works, we'll need to show the
        // Snackbar)
        val detailsFragment =
                childFragmentManager.findFragmentByTag(DIALOG_TAG_MORE_DETAILS + filename)
        if (detailsFragment is DialogFragment)
            detailsFragment.dismiss()
        else
            Log.e(TAG, "Couldn't find the details fragment")

        val success = mFilesManager.delete(filename)

        if (success) {
            // Show a confirmation message
            showSnackbar(R.string.formatChooser_dialog_deleted_success, filename)

            // Remove the entry from the styles list
            val index = convertFilenameToIndex(filename)
            if (index >= 0) {
                mStylesList.removeAt(index)
                mStylesArrayAdapter.notifyDataSetChanged()
            }
        } else showSnackbar(R.string.formatChooser_dialog_deleted_failure, filename)
    }

    /**
     * Parses an XML file to get the [DebateFormatInfo] object
     * @param filename the filename for the debate format XML file
     * @return a `DebateFormatInfo` object
     * @throws IOException if there was an IO problem with the XML file
     * @throws SAXException if thrown by the XML parser
     */
    @Throws(IOException::class, SAXException::class)
    private fun getDebateFormatInfo(filename: String): DebateFormatInfo {
        val inputStream: InputStream = mFilesManager.open(filename)
        return DebateFormatInfoForSchema2(requireContext(), inputStream)
    }

    /**
     * Returns the currently selected file name.
     *
     * @return The currently selected file name, or `null` if nothing is selected.
     */
    private fun getSelectedFilename(): String? {
        val selectedPosition = mStylesListView.checkedItemPosition
        return convertIndexToFilename(selectedPosition)
    }

    /**
     * Checks if the incoming file would overwrite an existing one. If so, prompt the user with
     * a dialog; if not, just import it.
     * @param uri a [Uri] to the incoming content
     */
    private fun importIncomingFilePrompt(uri: Uri?) {
        if (uri == null) {
            showSnackbar(R.string.formatChooser_import_error_noFileChosen)
            return
        }

        val context = requireContext()
        var filename = DebatekeeperUtils.getFilenameFromUri(context.contentResolver, uri)

        Log.i(TAG, "saving to file: $filename")
        if (filename == null) {
            try {
                filename = mFilesManager.getFreeFileName()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                showSnackbar(R.string.formatChooser_import_error_noFilename)
                return
            } catch (e: IOException) {
                e.printStackTrace()
                showSnackbar(R.string.formatChooser_ioError)
                return
            }
        }
        if (!filename.endsWith(".xml"))
            filename = "$filename.xml"

        if (mFilesManager.exists(filename)) {
            var existingStyleName: String? = null
            val nameExtractor = DebateFormatFieldExtractor(context, R.string.xml2elemName_name)
            try {
                val `in` = mFilesManager.open(filename)
                existingStyleName = nameExtractor.getFieldValue(`in`)
            } catch (e: IOException) {
                Log.e(TAG, "Couldn't open existing file, even though one exists")
                e.printStackTrace()
            } catch (e: SAXException) {
                Log.e(TAG, "Couldn't open existing file, even though one exists")
                e.printStackTrace()
            }
            if (existingStyleName == null)
                existingStyleName =
                        getString(R.string.importDebateFormat_placeholder_unknownStyleName)

            val fragment =
                    ConfirmOverwriteDialogFragment.newInstance(uri, filename, existingStyleName)
            fragment.show(childFragmentManager, DIALOG_TAG_CONFIRM_OVERWRITE + filename)

        } else {
            importIncomingFile(uri, filename)
        }
    }

    /**
     * Imports the selected file, refreshes the list and selects the new file.
     * @param uri a [Uri] to import
     */
    private fun importIncomingFile(uri: Uri, filename: String) {
        val contentResolver = requireContext().contentResolver
        val `in`: InputStream?
        try {
            `in` = contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            showSnackbar(R.string.formatChooser_import_error_opening)
            return
        }

        if (`in` == null) {
            showSnackbar(R.string.formatChooser_import_error_opening)
            return
        }

        try {
            mFilesManager.copy(`in`, filename)
        } catch (e: IOException) {
            e.printStackTrace()
            showSnackbar(R.string.formatChooser_import_error_reading)
            return
        }

        showSnackbar(R.string.formatChooser_import_success, filename)
        populateStylesList()
        setSelectionAndScroll(filename)
    }

    /**
     * Populates the master styles list, `mStylesList`.  Should be called when this
     * fragment is created, or whenever we want to refresh the styles list. If there is an error
     * so serious that it can't even get the list, we show a message to that effect, and leave
     * the list empty.
     */
    private fun populateStylesList() {
        val nameExtractor =
                DebateFormatFieldExtractor(requireContext(), R.string.xml2elemName_name)

        val fileList = try {
            mFilesManager.list()
        } catch (e: IOException) {
            Log.e(TAG, "IO error loading formats list!")
            e.printStackTrace()
            mViewBinding.formatChooserError.visibility = View.VISIBLE
            mViewBinding.formatChooserStylesList.visibility = View.GONE
            return
        }

        mStylesList.clear()

        for (filename in fileList) {
            if (!filename.endsWith(".xml")) continue

            val inputStream = try {
                mFilesManager.open(filename)
            } catch (e: IOException) {
                Log.e(TAG, "populateStylesList: Couldn't find file $filename")
                continue
            }

            var styleName: String? = null
            try {
                styleName = nameExtractor.getFieldValue(inputStream)
            } catch (e: SAXException) {
                Log.e(TAG, "populateStylesList: Couldn't get name from $filename")
            } catch (e: IOException) {
                Log.e(TAG, "populateStylesList: Couldn't get name from $filename")
            }

            try {
                inputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "populateStylesList: error closing file $filename")
            }

            if (styleName == null)
                styleName = filename
            mStylesList.add(DebateFormatListEntry(filename, styleName))
        }

        // Sort alphabetically by style name and tell observers
        mStylesArrayAdapter.sort { lhs, rhs -> lhs.styleName.compareTo(rhs.styleName, true) }
        mStylesArrayAdapter.notifyDataSetChanged()
    }

    /**
     * Sets the selection to the given file name and scrolls so that the selection is visible.
     * If the file name isn't in the list, it deselects everything.
     * @param filename name of file to select
     */
    private fun setSelectionAndScroll(filename: String?) {
        val index = convertFilenameToIndex(filename)
        mStylesListView.setItemChecked(index, true)
        if (index != ListView.INVALID_POSITION)
            mStylesListView.smoothScrollToPosition(index)
    }

    /**
     * Shares the given file. If there is a problem, it shows a [Snackbar] with an error message.
     */
    private fun shareDebateFormatFile(filename: String?) {

        // Check for error conditions
        if (filename == null) {
            showSnackbar(R.string.formatChooser_share_error_noFileSelected)
            return
        }

        if (!mFilesManager.exists(filename)) {
            Log.e(TAG, "shareSelection: file does not exist")
            showSnackbar(R.string.formatChooser_share_error_notFound, filename)
            return
        }

        val file = mFilesManager.getFileFromExternalStorage(filename)
        if (file == null) {
            Log.e(TAG, String.format(
                    "shareSelection: getFileFromExternalStorage returned null on file %s",
                    filename))
            showSnackbar(R.string.formatChooser_share_error_generic)
            return
        }

        val fileUri = try {
            FileProvider.getUriForFile(requireContext(), FILES_AUTHORITY, file)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "shareSelection: tried to get file from outside allowable paths")
            Log.e(TAG, "path was: " + file.absolutePath)
            showSnackbar(R.string.formatChooser_share_error_generic)
            return
        }

        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "text/xml"
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)

        // I'm pretty sure this part doesn't do anything useful. It's part of the spec that
        // setClipData is like putExtra with the ability to grant URI permissions using flags, but
        // apps like Gmail don't seem to honour it, and there's almost no third-party posts on the
        // topic. It also doesn't seem to be harmful, though.
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clipData = ClipData(filename, arrayOf("text/xml"), ClipData.Item(fileUri))
        shareIntent.clipData = clipData

        val chooserIntent = Intent.createChooser(shareIntent,
                getString(R.string.formatChooser_share_chooserTitle))
        startActivity(chooserIntent)
    }

    private fun showDownloadHelpBanner(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val bannerDismissed = prefs.getBoolean(PREFERENCE_DOWNLOAD_BANNER_DISMISSED, false)
        if (!bannerDismissed) {
            mViewBinding.downloadBannerGroup.visibility = View.VISIBLE
            mViewBinding.toolbarFormatChooser.elevation = 0f
            mViewBinding.downloadBannerDismiss.setOnClickListener {
                prefs.edit().putBoolean(PREFERENCE_DOWNLOAD_BANNER_DISMISSED, true).apply()
                mViewBinding.downloadBannerGroup.visibility = View.GONE
                val scale = context.resources.displayMetrics.density
                mViewBinding.toolbarFormatChooser.elevation = 4 * scale + 0.5f
            }
        }
    }

    private fun showSnackbar(stringResId: Int, vararg formatArgs: Any) {
        val string = getString(stringResId, *formatArgs)
        val coordinator = mViewBinding.formatChooserCoordinator
        val snackbar = Snackbar.make(coordinator, string, BaseTransientBottomBar.LENGTH_LONG)
        val res = resources
        @Suppress("deprecation")
        snackbar.setBackgroundTint(res.getColor(R.color.snackbar_background))
        @Suppress("deprecation")
        snackbar.setTextColor(res.getColor(R.color.snackbar_text))
        val snackbarText = snackbar.view
        val textView = snackbarText.findViewById<TextView>(
                com.google.android.material.R.id.snackbar_text)
        textView?.maxLines = 5
        snackbar.show()
    }

    companion object {
        private const val TAG = "FormatChooserActivity"
        const val FILES_AUTHORITY = "net.czlee.debatekeeper.fileprovider"

        private const val DIALOG_ARGUMENT_FILE_NAME = "filename"
        private const val DIALOG_ARGUMENT_IMPORT_URI = "import-uri"
        private const val DIALOG_ARGUMENT_STYLE_NAME = "style-name"
        private const val DIALOG_TAG_MORE_DETAILS = "details/"
        private const val DIALOG_TAG_CONFIRM_DELETION = "delete/"
        private const val DIALOG_TAG_CONFIRM_OVERWRITE = "overwrite/"

        const val BUNDLE_KEY_RESULT = "res"
        const val BUNDLE_KEY_XML_FILE_NAME = "xmlfn"
        const val REQUEST_KEY_CHOOSE_FORMAT = "choose-format"

        private const val PREFERENCE_DOWNLOAD_BANNER_DISMISSED = "dlb-dismiss"

        const val RESULT_SUCCESS = 0
        const val RESULT_UNCHANGED = 2
        const val RESULT_NO_SELECTION = 3

        const val CURRENT_SCHEMA_VERSION = "2.2"

        /**
         * Concatenates a list of `String`s with line breaks delimiting.
         *
         * @param list a list of `String`s.
         * @return the result, a single `String`
         */
        private fun concatenate(list: List<String>): String {
            return list.joinToString("\n")
        }
    }
}
