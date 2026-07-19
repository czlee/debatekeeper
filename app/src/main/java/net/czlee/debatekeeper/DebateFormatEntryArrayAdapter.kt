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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import net.czlee.debatekeeper.FormatChooserFragment.DebateFormatListEntry
import net.czlee.debatekeeper.FormatChooserFragment.FormatChooserFragmentBinder
import net.czlee.debatekeeper.databinding.FormatItemNotSelectedBinding
import net.czlee.debatekeeper.databinding.FormatItemSelectedBinding
import org.xml.sax.SAXException
import java.io.IOException

/**
 * An ArrayAdapter for displaying a list of debate formats. This adapter changes
 * the layout depending on whether or not the item is selected. If it's
 * selected, it expands to show information about the format. If not, it just
 * returns the standard android simple_list_item_single_choice.
 *
 * It would be good to update this setup to use `RecyclerView` instead.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-20
 */
class DebateFormatEntryArrayAdapter(
        context: Context,
        objects: List<DebateFormatListEntry>,
        private val mBinder: FormatChooserFragmentBinder) :
        ArrayAdapter<DebateFormatListEntry>(context,
                R.layout.simple_list_item_single_choice, objects) {

    override fun getItemViewType(position: Int): Int {
        return if (position == mBinder.selectedPosition) 0 else 1
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val titleView: CheckedTextView
        val selected = position == mBinder.selectedPosition
        if (selected) {
            val binding = FormatItemSelectedBinding.inflate(LayoutInflater.from(context))
            view = binding.root

            val filename = getItem(position)!!.filename
            try {
                // Population information like the region, level, where used and short
                // description of the style.
                mBinder.populateBasicInfo(binding.formatItemInfo, filename)
            } catch (e: IOException) {
                // Do nothing.
                // This basically just means the view won't be populated with information,
                // i.e. the fields will just have a hyphen ("-") in them.  This is fine.  When
                // the user tries to do something else with the file, it will show the real
                // error message.
            } catch (e: SAXException) {
                // Do nothing, as above.
            }

            // Set the OnClickListener of the "More" details button
            val showDetailsButton = binding.formatItemInfo.viewFormatShowDetailsButton
            showDetailsButton.visibility = View.VISIBLE
            showDetailsButton.setOnClickListener(mBinder.getDetailsButtonOnClickListener(filename))

            // Populate the style name and whether the radio button is checked
            titleView = binding.formatItemChoice.formatItemText

        } else {

            // If not selected, this just shows the style name and a blank radio button
            val binding = FormatItemNotSelectedBinding.inflate(LayoutInflater.from(context))
            view = binding.root
            titleView = binding.formatItemChoice.formatItemText
        }

        // Regardless of whether this item is selected, populate the style name and set whether
        // the radio button is checked.
        titleView.text = getItem(position)!!.styleName
        titleView.isChecked = selected

        return view
    }
}
