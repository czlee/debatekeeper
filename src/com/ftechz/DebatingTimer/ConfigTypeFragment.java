package com.ftechz.DebatingTimer;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * <b> OBSOLETE, DO NOT USE </b>
 * Fragment for selecting debate type
 */
public class ConfigTypeFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        return inflater.inflate(R.layout.config_type_fragment, container, false);
    }
}