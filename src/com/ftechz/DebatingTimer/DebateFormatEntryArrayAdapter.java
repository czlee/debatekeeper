package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.util.List;

import org.xml.sax.SAXException;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;

import com.ftechz.DebatingTimer.FormatChooserActivity.DebateFormatListEntry;
import com.ftechz.DebatingTimer.FormatChooserActivity.FormatChooserActivityBinder;

/**
 * TODO Comment this class, before it's too late!
 * An ArrayAdapter for displaying a list of debate formats. This adapter changes
 * the layout depending on whether or not the item is selected. If it's
 * selected, it expands to show information about the format. If not, it just
 * returns the standard android simple_list_item_single_choice.
 * <p>
 * Please note that the current implementation of this class specifically
 * hard-codes a number of things that arguably don't need to be hard-coded. If
 * this class needs to be generalised, the following could likely be generalised
 * without adverse effect:
 * <ul>
 * <li>{@link FormatChooserActivityBinder} can be generalised to an interface
 * with a single abstract method <code>getSelectedPosition()</code>.</li>
 * <li>The layout resources are hard-coded. This isn't strictly necessary; the
 * constructor could take in resource IDs as arguments. But the layout resources
 * provided do need to be able to support all the calls in
 * <code>getView()</code>, so if one does take this route, checking for class
 * cast exceptions might be a good idea.</li>
 * </p>
 *
 * @author Chuan-Zheng Lee
 * @since 2012-06-20
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

            Button showDetailsButton = (Button) view.findViewById(R.id.ViewFormatShowDetailsButton);
            showDetailsButton.setVisibility(View.VISIBLE);
            showDetailsButton.setOnClickListener(mBinder.getDetailsButtonOnClickListener(filename));

        } else {
            view = View.inflate(getContext(),
                    R.layout.format_item_not_selected, null);
        }

        CheckedTextView titleView = (CheckedTextView) view
                .findViewById(android.R.id.text1);
        titleView.setText(this.getItem(position).getStyleName());
        titleView.setChecked(selected);

        return view;
    }

}
