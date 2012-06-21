package com.ftechz.DebatingTimer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ftechz.DebatingTimer.FormatChooserActivity.DebateFormatListEntry;
import com.ftechz.DebatingTimer.FormatChooserActivity.SelectedPositionInformer;

/**
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
 * <li>{@link SelectedPositionInformer} can be generalised to an interface
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

    private final SelectedPositionInformer mSelectedPositionInformer;

    public DebateFormatEntryArrayAdapter(Context context,
            List<DebateFormatListEntry> objects, SelectedPositionInformer spi) {
        super(context, android.R.layout.simple_list_item_single_choice, objects);
        mSelectedPositionInformer = spi;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mSelectedPositionInformer.getSelectedPosition())
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
        Log.i(this.getClass().getSimpleName(),
                String.format("in getView() for position %d", position));
        View view;
        boolean selected = (position == mSelectedPositionInformer
                .getSelectedPosition());
        if (selected) {
            view = View.inflate(getContext(), R.layout.format_item_selected, null);

            // TODO add a loading splash screen
            InputStream is;

            try {
                is = getContext().getAssets().open(this.getItem(position).getFilename());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return view;
            }

            DebateFormatInfoExtractor dfie = new DebateFormatInfoExtractor(getContext());
            DebateFormatInfo dfi = dfie.getDebateFormatInfo(is);

            ((TextView) view.findViewById(R.id.ViewFormatTableCellRegionValue)).setText(
                    concatenate(dfi.getRegions()));
            ((TextView) view.findViewById(R.id.ViewFormatTableCellLevelValue)).setText(
                    concatenate(dfi.getLevels()));
            ((TextView) view.findViewById(R.id.ViewFormatTableCellUsedAtValue)).setText(
                    concatenate(dfi.getUsedAts()));
            ((TextView) view.findViewById(R.id.ViewFormatTableCellDescValue)).setText(
                    dfi.getDescription());

            populateTwoColumnTable(view, R.id.ViewFormatTableSpeechTypes, R.layout.speech_type_row,
                    dfi.getSpeechFormatDescriptions());
            populateTwoColumnTable(view, R.id.ViewFormatTableSpeeches, R.layout.speech_row,
                    dfi.getSpeeches());

        } else {
            view = View.inflate(getContext(),
                    android.R.layout.simple_list_item_single_choice, null);
        }

        CheckedTextView titleView = (CheckedTextView) view
                .findViewById(android.R.id.text1);
        titleView.setText(this.getItem(position).getStyleName());
        titleView.setChecked(selected);

        return view;
    }


    /**
     * Concatenates a list of <code>String</code>s with line breaks delimiting.
     * @param list An <code>ArrayList</code> of <code>String</code>s.
     * @return the result, a single <code>String</code>
     */
    private static String concatenate(ArrayList<String> list) {
        String str = new String();
        Iterator<String> iterator = list.iterator();

        // Start with the first item (if it exists)
        if (iterator.hasNext()) str = iterator.next();

        // Add the second and further items, putting a line break in between.
        while (iterator.hasNext()) {
            str = str.concat("\n");
            str = str.concat(iterator.next());
        }
        return str;
    }

    /**
     * Populates a table from an ArrayList of String arrays.
     * @param view
     * @param tableResid A resource ID pointing to a <code>TableLayout</code>
     * @param rowResid A resource ID pointing to a <code>TableRow</code> <b>layout file</b>.
     * (Not the <code>TableRow</code> itself.)
     * TableRow must have at least two TextView elements, which must have IDs "text1" and "text2".
     * @param list the list of String arrays.  Each array must have two elements.
     */
    private void populateTwoColumnTable(View view, int tableResid, int rowResid, ArrayList<String[]> list) {
        TableLayout table = (TableLayout) view.findViewById(tableResid);

        Iterator<String[]> iterator = list.iterator();

        while (iterator.hasNext()) {
            String[] rowText = iterator.next();
            TableRow row = (TableRow) View.inflate(getContext(), rowResid, null);
            ((TextView) row.findViewById(R.id.text1)).setText(rowText[0].concat(" "));
            ((TextView) row.findViewById(R.id.text2)).setText(rowText[1].concat(" "));
            table.addView(row);
        }

    }


}
