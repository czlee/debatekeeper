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

import java.io.IOException;
import java.util.List;

import net.czlee.debatekeeper.FormatChooserActivity.DebateFormatListEntry;
import net.czlee.debatekeeper.FormatChooserActivity.FormatChooserActivityBinder;

import org.xml.sax.SAXException;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;

/**
 * An ArrayAdapter for displaying a list of debate formats. This adapter changes
 * the layout depending on whether or not the item is selected. If it's
 * selected, it expands to show information about the format. If not, it just
 * returns the standard android simple_list_item_single_choice.
 *
 * @author Chuan-Zheng Lee
 * @since  2012-06-20
 */
public class DebateFormatEntryArrayAdapter extends
        ArrayAdapter<FormatChooserActivity.DebateFormatListEntry> {

    private final FormatChooserActivityBinder mBinder;

    public DebateFormatEntryArrayAdapter(Context context,
            List<DebateFormatListEntry> objects, FormatChooserActivityBinder binder) {
        super(context, android.R.layout.simple_list_item_single_choice, objects);
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
        boolean selected = (position == mBinder.getSelectedPosition());
        if (selected) {
            view = View.inflate(getContext(), R.layout.format_item_selected, null);

            String filename = this.getItem(position).getFilename();
            try {
                // Population information like the region, level, where used and short
                // description of the style.
                mBinder.populateBasicInfo(view, filename);

            } catch (IOException e) {
                // Do nothing.
                // This basically just means the view won't be populated with information,
                // i.e. the fields will just have a hyphen ("-") in them.  This is fine.  When
                // the user tries to do something else with the file, it will show the real
                // error message.
            } catch (SAXException e) {
                // Do nothing.
                // This basically just means the view won't be populated with information,
                // i.e. the fields will just have a hyphen ("-") in them.  This is fine.  When
                // the user tries to do something else with the file, it will show the real
                // error message.
            }

            // Set the OnClickListener of the "More" details button
            Button showDetailsButton = (Button) view.findViewById(R.id.viewFormat_showDetailsButton);
            showDetailsButton.setVisibility(View.VISIBLE);
            showDetailsButton.setOnClickListener(mBinder.getDetailsButtonOnClickListener(filename));

        } else {

            // If not selected, this just shows the style name and a blank radio button
            view = View.inflate(getContext(),
                    R.layout.format_item_not_selected, null);
        }

        // Regardless of whether this item is selected, we need to populate the style name
        // and set whether the radio button is checked.
        CheckedTextView titleView = (CheckedTextView) view
                .findViewById(android.R.id.text1);
        titleView.setText(this.getItem(position).getStyleName());
        titleView.setChecked(selected);

        return view;
    }

}
