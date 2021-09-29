/*
 * Copyright (C) 2012 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageButton;

import net.czlee.debatekeeper.FormatChooserFragment.DebateFormatListEntry;
import net.czlee.debatekeeper.FormatChooserFragment.FormatChooserFragmentBinder;
import net.czlee.debatekeeper.databinding.FormatItemNotSelectedBinding;
import net.czlee.debatekeeper.databinding.FormatItemSelectedBinding;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

/**
 * An ArrayAdapter for displaying a list of debate formats. This adapter changes
 * the layout depending on whether or not the item is selected. If it's
 * selected, it expands to show information about the format. If not, it just
 * returns the standard android simple_list_item_single_choice.
 *
 * It would be good to update this setup to use <code>RecyclerView</code> instead.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-20
 */
public class DebateFormatEntryArrayAdapter extends
        ArrayAdapter<DebateFormatListEntry> {

    private final FormatChooserFragmentBinder mBinder;

    public DebateFormatEntryArrayAdapter(Context context,
            List<DebateFormatListEntry> objects, FormatChooserFragmentBinder binder) {
        super(context, R.layout.simple_list_item_single_choice, objects);
        mBinder = binder;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mBinder.getSelectedPosition())
            return 0;
        else
            return 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        CheckedTextView titleView;
        boolean selected = (position == mBinder.getSelectedPosition());
        if (selected) {
            FormatItemSelectedBinding binding = FormatItemSelectedBinding.inflate(LayoutInflater.from(getContext()));
            view = binding.getRoot();

            String filename = this.getItem(position).getFilename();
            try {
                // Population information like the region, level, where used and short
                // description of the style.
                mBinder.populateBasicInfo(binding.formatItemInfo, filename);

            } catch (IOException | SAXException e) {
                // Do nothing.
                // This basically just means the view won't be populated with information,
                // i.e. the fields will just have a hyphen ("-") in them.  This is fine.  When
                // the user tries to do something else with the file, it will show the real
                // error message.
            }

            // Set the OnClickListener of the "More" details button
            ImageButton showDetailsButton = binding.formatItemInfo.viewFormatShowDetailsButton;
            showDetailsButton.setVisibility(View.VISIBLE);
            showDetailsButton.setOnClickListener(mBinder.getDetailsButtonOnClickListener(filename));

            // Populate the style name and whether the radio button is checked
            titleView = binding.formatItemChoice.formatItemText;

        } else {

            // If not selected, this just shows the style name and a blank radio button
            FormatItemNotSelectedBinding binding = FormatItemNotSelectedBinding.inflate(LayoutInflater.from(getContext()));
            view = binding.getRoot();
            titleView = binding.formatItemChoice.formatItemText;
        }

        // Regardless of whether this item is selected, populate the style name and set whether the radio button is checked.
        titleView.setText(this.getItem(position).getStyleName());
        titleView.setChecked(selected);

        return view;
    }

}
