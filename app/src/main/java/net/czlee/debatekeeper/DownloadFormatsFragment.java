package net.czlee.debatekeeper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import net.czlee.debatekeeper.databinding.FragmentDownloadFormatsBinding;

/**
 * Fragment that downloads the online debate formats list and allows the user to download formats.
 */
public class DownloadFormatsFragment extends Fragment {

    FragmentDownloadFormatsBinding mViewBinding;
    DebateFormatDownloadManager mDownloadManager;
    DownloadableFormatRecyclerAdapter mRecyclerAdapter;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public class DownloadBinder {
        /**
         * Convenience function, runs the notifications to replace the entire dataset.
         * @param oldCount the original number of entries
         * @param newCount the new number of entries
         */
        public void notifyAdapterItemsReplaced(int oldCount, int newCount) {
            if (oldCount == newCount) {
                mRecyclerAdapter.notifyItemRangeChanged(0, newCount);
            } else if (oldCount < newCount) {
                mRecyclerAdapter.notifyItemRangeChanged(0, oldCount);
                mRecyclerAdapter.notifyItemRangeInserted(oldCount, newCount - oldCount);
            } else {
                mRecyclerAdapter.notifyItemRangeChanged(0, newCount);
                mRecyclerAdapter.notifyItemRangeRemoved(newCount, oldCount - newCount);
            }

            if (newCount > 0) setViewToList();
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        private boolean checkConnectivity(Context context) {
            ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
            if (connectivityManager == null) return false;
            Network currentNetwork = connectivityManager.getActiveNetwork();
            if (currentNetwork == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(currentNetwork);
            if (caps == null) return false;
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        public void notifyListDownloadError(String detailMessage) {

            // Check if there's no internet in general (only works at API level 23 and higher)
            Context context = getContext();
            if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!checkConnectivity(context)) {
                    mViewBinding.loadingText.setText(R.string.formatDownloader_noInternet);
                    setViewToError();
                    return;
                }
            }

            mViewBinding.loadingText.setText(R.string.formatDownloader_ioError);
            mViewBinding.errorDetailText.setText(detailMessage);
            setViewToError();
        }

        public void notifyJsonParseError(String detailMessage) {
            mViewBinding.loadingText.setText(R.string.formatDownloader_jsonError);
            mViewBinding.errorDetailText.setText(detailMessage);
            setViewToError();
        }

        public void showSnackbarError(String filename, String detailMessage) {
            String message = getString(R.string.formatDownloader_fileError, filename, detailMessage);
            Snackbar snackbar = Snackbar.make(mViewBinding.getRoot(), message, BaseTransientBottomBar.LENGTH_LONG);
            View snackbarText = snackbar.getView();
            TextView textView = snackbarText.findViewById(com.google.android.material.R.id.snackbar_text);
            if (textView != null) textView.setMaxLines(5);
            snackbar.show();
        }
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDownloadManager = new DebateFormatDownloadManager(requireContext(), new DownloadBinder());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mViewBinding = FragmentDownloadFormatsBinding.inflate(inflater, container, false);

        // Configure up button
        mViewBinding.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        mViewBinding.toolbar.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());

        // Configure retry button
        mViewBinding.retryButton.setOnClickListener((v) -> {
            setViewToLoading();
            mDownloadManager.startDownloadList();
        });

        // Configure recycler view
        RecyclerView recyclerView = mViewBinding.list;
        Context context = requireContext();
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerAdapter = new DownloadableFormatRecyclerAdapter(mDownloadManager);
        recyclerView.setAdapter(mRecyclerAdapter);
        DividerItemDecoration decoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(decoration);

        // Download the list
        setViewToLoading();
        mDownloadManager.startDownloadList();

        return mViewBinding.getRoot();
    }

    public void setViewToLoading() {
        if (mViewBinding == null) return;
        mViewBinding.list.setVisibility(View.GONE);
        mViewBinding.loadingText.setVisibility(View.VISIBLE);
        mViewBinding.loadingText.setText(R.string.formatDownloader_loadingText);
        mViewBinding.errorDetailText.setVisibility(View.GONE);
        mViewBinding.retryButton.setVisibility(View.GONE);
    }

    public void setViewToError() {
        if (mViewBinding == null) return;
        mViewBinding.list.setVisibility(View.GONE);
        mViewBinding.loadingText.setVisibility(View.VISIBLE);
        mViewBinding.errorDetailText.setVisibility(View.VISIBLE);
        mViewBinding.retryButton.setVisibility(View.VISIBLE);
    }

    public void setViewToList() {
        if (mViewBinding == null) return;
        mViewBinding.list.setVisibility(View.VISIBLE);
        mViewBinding.loadingText.setVisibility(View.GONE);
        mViewBinding.errorDetailText.setVisibility(View.GONE);
        mViewBinding.retryButton.setVisibility(View.GONE);
    }

}