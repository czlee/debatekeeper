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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import net.czlee.debatekeeper.databinding.FragmentDownloadConfigBinding

/**
 * Configures how debate format downloads work.
 *
 * Because it's just one field on a screen, I figure it's easier just to implement the whole thing
 * in a blank Fragment, rather than load the infrastructure of
 * [androidx.preference.PreferenceFragmentCompat]. If the configuration here ever becomes more
 * complicated, it might be worth switching to `PreferenceFragmentCompat`.
 *
 * @author Chuan-Zheng Lee
 * @since 2021-09-30
 */
class DownloadConfigFragment : Fragment() {

    private lateinit var mViewBinding: FragmentDownloadConfigBinding

    // we have fields for convenience, but these are set in onCreateView()
    private var mErrorTextColor = -0x10000
    private var mOkayTextColor = -0x1

    private inner class DownloadUrlTextWatcher : TextWatcher {

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable) {
            val color = if (isValidUrl(s.toString())) mOkayTextColor else mErrorTextColor
            mViewBinding.downloadUrl.setTextColor(color)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mViewBinding = FragmentDownloadConfigBinding.inflate(inflater, container, false)

        // Configure menu
        mViewBinding.toolbarDownloadConfig.setNavigationOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }

        // Populate the field
        val context = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val downloadUrl =
                prefs.getString(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, "")
        mViewBinding.downloadUrl.setText(downloadUrl)
        mViewBinding.downloadUrl.addTextChangedListener(DownloadUrlTextWatcher())

        // Grab the text colours (so we don't have to keep looking them up in
        // DownloadUrlTextWatcher)
        @Suppress("deprecation")
        val res = context.resources
        @Suppress("deprecation")
        mErrorTextColor = res.getColor(android.R.color.holo_red_light)
        @Suppress("deprecation")
        mOkayTextColor = res.getColor(android.R.color.primary_text_dark)

        return mViewBinding.root
    }

    override fun onPause() {
        saveDownloadUrl()
        super.onPause()
    }

    /**
     * Checks both that the protocol is valid and that the URL is well-formed.
     * @param s URL to check
     * @return `true` if it's a valid URL, `false` otherwise
     */
    private fun isValidUrl(s: String): Boolean {
        if (!URLUtil.isValidUrl(s)) return false
        return Patterns.WEB_URL.matcher(s).matches()
    }

    /**
     * Saves the download URL input by the user, if it is a valid URL. If it's blank, it removes
     * the preference. If it's not blank and not a valid URL, it doesn't save.
     */
    private fun saveDownloadUrl() {
        val newUrl = mViewBinding.downloadUrl.text.toString()

        // Verify that the field has a valid URL, and if it does, save it
        if (isValidUrl(newUrl)) {
            Log.i(TAG, "New URL: $newUrl")
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString(
                    DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, newUrl).apply()

        } else if (newUrl.isEmpty()) {
            Log.i(TAG, "Removing download URL preference")
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().remove(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL).apply()

        } else {
            Log.w(TAG, "Rejecting URL: $newUrl")
        }
    }

    companion object {
        private const val TAG = "DownloadConfigFragment"
    }
}
