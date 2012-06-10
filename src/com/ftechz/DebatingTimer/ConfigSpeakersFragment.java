package com.ftechz.DebatingTimer;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 * Fragment for adding/configuring speakers
 */
public class ConfigSpeakersFragment extends Fragment {
    public EditText speaker1Field;
    public EditText speaker2Field;
    public EditText speaker3Field;
    public EditText speaker4Field;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        View view =inflater.inflate(R.layout.config_speakers_fragment, container, false);

        speaker1Field = (EditText) view.findViewById(R.id.speaker1Field);
        speaker2Field = (EditText) view.findViewById(R.id.speaker2Field);
        speaker3Field = (EditText) view.findViewById(R.id.speaker3Field);
        speaker4Field = (EditText) view.findViewById(R.id.speaker4Field);

        return view;
    }
}