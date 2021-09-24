package net.czlee.debatekeeper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import net.czlee.debatekeeper.databinding.ActivitySettingsBinding;

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

    ActivitySettingsBinding mViewBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mViewBinding = ActivitySettingsBinding.inflate(inflater, container, false);
        return mViewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        mViewBinding.globalSettingsToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        mViewBinding.globalSettingsToolbar.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());
    }
}
