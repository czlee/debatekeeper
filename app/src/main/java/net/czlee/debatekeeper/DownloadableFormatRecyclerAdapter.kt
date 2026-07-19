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

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import net.czlee.debatekeeper.DebateFormatDownloadManager.DownloadableFormatEntry
import net.czlee.debatekeeper.databinding.ViewFormatDownloadBinding

/**
 * [RecyclerView.Adapter] that can display a [DownloadableFormatEntry].
 *
 * @author Chuan-Zheng Lee
 * @since 2021-09-29
 */
class DownloadableFormatRecyclerAdapter(private val mDownloadManager: DebateFormatDownloadManager) :
        RecyclerView.Adapter<DownloadableFormatRecyclerAdapter.ViewHolder>() {

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    inner class ViewHolder internal constructor(private val binding: ViewFormatDownloadBinding) :
            RecyclerView.ViewHolder(binding.root) {

        private lateinit var entry: DownloadableFormatEntry

        internal fun bind(entry: DownloadableFormatEntry) {
            this.entry = entry
            binding.viewFormatTitle.text = entry.name
            binding.viewFormatFileNameValue.text = entry.filename
            binding.viewFormatRegionValue.text = TextUtils.join("\n", entry.regions)
            binding.viewFormatUsedAtValue.text = TextUtils.join("\n", entry.usedAts)
            binding.viewFormatLevelValue.text = TextUtils.join("\n", entry.levels)
            binding.viewFormatDescValue.text = entry.description

            val expandListener = View.OnClickListener {
                this.entry.expanded = !this.entry.expanded
                updateExpandedState()
            }
            binding.viewFormatChevron.setOnClickListener(expandListener)
            binding.viewFormatTitle.setOnClickListener(expandListener)
            binding.viewFormatStatusIcon.setOnClickListener(expandListener)

            updateExpandedState()
        }

        fun updateDownloadProgress() {
            var buttonVisibility = View.GONE
            var progressVisibility = View.GONE
            var doneVisibility = View.GONE
            var statusIconVisibility = View.GONE
            var updateTextVisibility = View.GONE

            when (entry.state) {
                DownloadableFormatEntry.DownloadState.UPDATE_AVAILABLE -> {
                    binding.viewFormatStatusIcon.setImageResource(
                            R.drawable.ic_baseline_upgrade_24)
                    statusIconVisibility = View.VISIBLE
                    if (entry.expanded) updateTextVisibility = View.VISIBLE
                    // fall through to NOT_DOWNLOADED behaviour (as in the original Java switch)
                    binding.viewFormatDownloadButton.setOnClickListener {
                        mDownloadManager.startDownloadFile(entry, this)
                    }
                    if (entry.expanded) buttonVisibility = View.VISIBLE
                }
                DownloadableFormatEntry.DownloadState.NOT_DOWNLOADED -> {
                    binding.viewFormatDownloadButton.setOnClickListener {
                        mDownloadManager.startDownloadFile(entry, this)
                    }
                    if (entry.expanded) buttonVisibility = View.VISIBLE
                }
                DownloadableFormatEntry.DownloadState.DOWNLOAD_IN_PROGRESS -> {
                    if (entry.expanded) progressVisibility = View.VISIBLE
                }
                DownloadableFormatEntry.DownloadState.DOWNLOADED -> {
                    binding.viewFormatStatusIcon.setImageResource(
                            R.drawable.ic_outline_file_download_done_24)
                    if (entry.expanded) doneVisibility = View.VISIBLE
                    else statusIconVisibility = View.VISIBLE // show the status icon only when collapsed
                }
            }

            binding.viewFormatDownloadButton.visibility = buttonVisibility
            binding.viewFormatDownloadProgress.visibility = progressVisibility
            binding.viewFormatDownloadDone.visibility = doneVisibility
            binding.viewFormatUpdateAvailableText.visibility = updateTextVisibility
            binding.viewFormatStatusIcon.visibility = statusIconVisibility
        }

        internal fun updateExpandedState() {
            binding.viewFormatDetailsGroup.visibility =
                    if (entry.expanded) View.VISIBLE else View.GONE
            updateDownloadProgress()
            binding.viewFormatChevron.setImageResource(if (entry.expanded)
                R.drawable.ic_baseline_expand_more_24
            else
                R.drawable.ic_baseline_chevron_right_24)
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ViewFormatDownloadBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = mDownloadManager.entries[position]
        holder.bind(entry)
    }

    override fun getItemCount(): Int {
        return mDownloadManager.entries.size
    }
}
