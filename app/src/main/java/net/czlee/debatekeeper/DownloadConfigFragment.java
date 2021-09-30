package net.czlee.debatekeeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import net.czlee.debatekeeper.databinding.FragmentDownloadConfigBinding;

/**
 * Configures how debate format downloads work.
 *
 * Because it's just one field on a screen, I figure it's easier just to implement the whole thing
 * in a blank Fragment, rather than load the infrastructure of
 * {@link androidx.preference.PreferenceFragmentCompat}. If the configuration here ever becomes more
 * complicated, it might be worth switching to {@code PreferenceFragmentCompat}.
 *
 * @author Chuan-Zheng Lee
 * @since 2021-09-30
 */
public class DownloadConfigFragment extends Fragment {

    private static final String TAG = "DownloadConfigFragment";
    private FragmentDownloadConfigBinding mViewBinding;

    // we have fields for convenience, but these are set in onCreateView()
    private int mErrorTextColor = 0xffff0000;
    private int mOkayTextColor = 0xffffffff;

    private class DownloadUrlTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            int color = (isValidUrl(s.toString())) ? mOkayTextColor : mErrorTextColor;
            mViewBinding.downloadUrl.setTextColor(color);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mViewBinding = FragmentDownloadConfigBinding.inflate(inflater, container, false);

        // Configure menu
        mViewBinding.toolbarDownloadConfig.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());

        // Populate the field
        Context context = requireContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String downloadUrl = prefs.getString(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, "");
        mViewBinding.downloadUrl.setText(downloadUrl);
        mViewBinding.downloadUrl.addTextChangedListener(new DownloadUrlTextWatcher());

        // Grab the text colours (so we don't have to keep looking them up in DownloadUrlTextWatcher)
        Resources res = context.getResources();
        mErrorTextColor = res.getColor(android.R.color.holo_red_light);
        mOkayTextColor = res.getColor(android.R.color.primary_text_dark);

        return mViewBinding.getRoot();
    }

    @Override
    public void onPause() {
        saveDownloadUrl();
        super.onPause();
    }

    /**
     * Checks both that the protocol is valid and that the URL is well-formed.
     * @param s URL to check
     * @return {@code true} if it's a valid URL, {@code false} otherwise
     */
    private boolean isValidUrl(String s) {
        if (!URLUtil.isValidUrl(s)) return false;
        return Patterns.WEB_URL.matcher(s).matches();
    }

    /**
     * Saves the download URL input by the user, if it is a valid URL. If it's blank, it removes
     * the preference. If it's not blank and not a valid URL, it doesn't save.
     */
    private void saveDownloadUrl() {
        String newUrl = mViewBinding.downloadUrl.getText().toString();

        // Verify that the field has a valid URL, and if it does, save it
        if (isValidUrl(newUrl)) {
            Log.i(TAG, "New URL: " + newUrl);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL, newUrl);
            editor.apply();

        } else if (newUrl.length() == 0) {
            Log.i(TAG, "Removing download URL preference");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(DownloadFormatsFragment.PREFERENCE_DOWNLOAD_LIST_URL);
            editor.apply();

        } else {
            Log.w(TAG, "Rejecting URL: " + newUrl);
        }
    }
}