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

package net.czlee.debatekeeper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import net.czlee.debatekeeper.databinding.FragmentSettingsBinding;

/**
 * This fragment just hosts {@link SettingsSubFragment} by wrapping it with a layout that
 * includes a toolbar.
 *
 * An alternative method would've been:
 * https://stackoverflow.com/questions/35966844/preferencefragmentcompat-custom-layout/36286426
 *
 * @author Chuan-Zheng Lee
 * @since  2021-09-24
 */
public class SettingsFragment extends Fragment {

    FragmentSettingsBinding mViewBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewBinding = FragmentSettingsBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        mViewBinding.globalSettingsToolbar.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());
    }
}
