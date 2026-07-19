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
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import net.czlee.debatekeeper.databinding.FragmentDownloadFormatsBinding

/**
 * Fragment that downloads the online debate formats list and allows the user to download formats.
 */
class DownloadFormatsFragment : Fragment() {

    private var mViewBinding: FragmentDownloadFormatsBinding? = null
    private lateinit var mDownloadManager: DebateFormatDownloadManager
    private lateinit var mRecyclerAdapter: DownloadableFormatRecyclerAdapter

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    inner class DownloadBinder {
        /**
         * Convenience function, runs the notifications to replace the entire dataset.
         * @param oldCount the original number of entries
         * @param newCount the new number of entries
         */
        fun notifyAdapterItemsReplaced(oldCount: Int, newCount: Int) {
            val viewBinding = mViewBinding ?: return

            // If a file name was given at the input,
            val incomingFilename =
                    DownloadFormatsFragmentArgs.fromBundle(requireArguments()).xmlFileName
            var incomingIndex = -1
            if (incomingFilename != null) {
                for ((i, entry) in mDownloadManager.entries.withIndex()) {
                    val found = entry.filename == incomingFilename
                    entry.expanded = found
                    if (found) incomingIndex = i
                }
            }

            if (oldCount == newCount) {
                mRecyclerAdapter.notifyItemRangeChanged(0, newCount)
            } else if (oldCount < newCount) {
                mRecyclerAdapter.notifyItemRangeChanged(0, oldCount)
                mRecyclerAdapter.notifyItemRangeInserted(oldCount, newCount - oldCount)
            } else {
                mRecyclerAdapter.notifyItemRangeChanged(0, newCount)
                mRecyclerAdapter.notifyItemRangeRemoved(newCount, oldCount - newCount)
            }

            if (newCount > 0) {
                setViewToList()
            } else {
                viewBinding.loadingText.setText(R.string.formatDownloader_emptyList)
                setViewToError()
                return
            }

            if (incomingFilename != null) {
                if (incomingIndex >= 0) viewBinding.list.scrollToPosition(incomingIndex)
                setExpandCollapseButton(true)
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        private fun checkConnectivity(context: Context): Boolean {
            val connectivityManager =
                    context.getSystemService(ConnectivityManager::class.java) ?: return false
            val currentNetwork = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(currentNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        fun notifyListDownloadError(detailMessage: String?) {
            val viewBinding = mViewBinding ?: return

            // Check if there's no internet in general (only works at API level 23 and higher)
            val context = context
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!checkConnectivity(context)) {
                    viewBinding.loadingText.setText(R.string.formatDownloader_noInternet)
                    setViewToError()
                    return
                }
            }

            viewBinding.loadingText.setText(R.string.formatDownloader_ioError)
            viewBinding.errorDetailText.text = detailMessage
            setViewToError()
        }

        fun notifyJsonParseError(detailMessage: String?) {
            val viewBinding = mViewBinding ?: return
            viewBinding.loadingText.setText(R.string.formatDownloader_jsonError)
            viewBinding.errorDetailText.text = detailMessage
            setViewToError()
        }

        fun showSnackbarError(filename: String?, detailMessage: String?) {
            val viewBinding = mViewBinding ?: return
            val message = getString(R.string.formatDownloader_fileError, filename, detailMessage)
            val snackbar = Snackbar.make(viewBinding.root, message,
                    BaseTransientBottomBar.LENGTH_LONG)
            val res = resources
            @Suppress("deprecation")
            snackbar.setBackgroundTint(res.getColor(R.color.snackbar_background))
            @Suppress("deprecation")
            snackbar.setTextColor(res.getColor(R.color.snackbar_text))
            val snackbarText = snackbar.view
            val textView =
                    snackbarText.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView?.maxLines = 5
            snackbar.show()
        }
    }

    //******************************************************************************************
    // Private classes
    //******************************************************************************************

    private inner class DownloadFormatsMenuItemClickListener : Toolbar.OnMenuItemClickListener {

        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.formatDownloader_actionBar_expand -> {
                    for (entry in mDownloadManager.entries) {
                        entry.expanded = true
                    }
                    mRecyclerAdapter.notifyItemRangeChanged(0, mDownloadManager.entries.size)
                    setExpandCollapseButton(false)
                    return true
                }
                R.id.formatDownloader_actionBar_collapse -> {
                    for (entry in mDownloadManager.entries) {
                        entry.expanded = false
                    }
                    mRecyclerAdapter.notifyItemRangeChanged(0, mDownloadManager.entries.size)
                    setExpandCollapseButton(true)
                    return true
                }
                R.id.formatDownloader_actionBar_learnMore -> {
                    val uri = Uri.parse(getString(R.string.formats_learnMoreUrl))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    return true
                }
                R.id.formatDownloader_actionBar_config -> {
                    val action = DownloadFormatsFragmentDirections.actionConfig()
                    NavHostFragment.findNavController(this@DownloadFormatsFragment)
                            .navigate(action)
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

        mDownloadManager = DebateFormatDownloadManager(requireContext(), DownloadBinder())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {

        val viewBinding = FragmentDownloadFormatsBinding.inflate(inflater, container, false)
        mViewBinding = viewBinding

        // Configure menu
        viewBinding.toolbarDownloadFormats.setOnMenuItemClickListener(
                DownloadFormatsMenuItemClickListener())
        viewBinding.toolbarDownloadFormats.setNavigationOnClickListener {
            NavHostFragment.findNavController(this).navigateUp()
        }

        // Configure retry button
        viewBinding.retryButton.setOnClickListener {
            setViewToLoading()
            mDownloadManager.startDownloadList()
        }

        // Configure recycler view
        val recyclerView = viewBinding.list
        val context = requireContext()
        recyclerView.layoutManager = LinearLayoutManager(context)
        mRecyclerAdapter = DownloadableFormatRecyclerAdapter(mDownloadManager)
        recyclerView.adapter = mRecyclerAdapter
        val decoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(decoration)

        // Download the list
        setViewToLoading()
        mDownloadManager.startDownloadList()

        return viewBinding.root
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private fun setExpandCollapseButton(expand: Boolean) {
        val menu = mViewBinding?.toolbarDownloadFormats?.menu ?: return
        menu.findItem(R.id.formatDownloader_actionBar_expand)?.isVisible = expand
        menu.findItem(R.id.formatDownloader_actionBar_collapse)?.isVisible = !expand
    }

    private fun setViewToLoading() {
        val viewBinding = mViewBinding ?: return
        viewBinding.list.visibility = View.GONE
        viewBinding.loadingText.visibility = View.VISIBLE
        viewBinding.loadingText.setText(R.string.formatDownloader_loadingText)
        viewBinding.errorDetailText.visibility = View.GONE
        viewBinding.retryButton.visibility = View.GONE
    }

    private fun setViewToError() {
        val viewBinding = mViewBinding ?: return
        viewBinding.list.visibility = View.GONE
        viewBinding.loadingText.visibility = View.VISIBLE
        viewBinding.errorDetailText.visibility = View.VISIBLE
        viewBinding.retryButton.visibility = View.VISIBLE
    }

    private fun setViewToList() {
        val viewBinding = mViewBinding ?: return
        viewBinding.list.visibility = View.VISIBLE
        viewBinding.loadingText.visibility = View.GONE
        viewBinding.errorDetailText.visibility = View.GONE
        viewBinding.retryButton.visibility = View.GONE
    }

    companion object {
        const val PREFERENCE_DOWNLOAD_LIST_URL = "download-list-url"
    }
}
