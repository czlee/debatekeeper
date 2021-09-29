package net.czlee.debatekeeper;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.czlee.debatekeeper.databinding.FragmentDownloadFormatsListBinding;

/**
 * Fragment that downloads the online debate formats list and allows the user to download formats.
 */
public class DownloadFormatsFragment extends Fragment {

    DebateFormatDownloadManager mDownloadManager;
    DownloadableFormatEntryRecyclerAdapter mRecyclerAdapter;

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
        }

        public void notifyListDownloadError() {

        }

        public void notifyJsonParseError() {

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

        FragmentDownloadFormatsListBinding binding = FragmentDownloadFormatsListBinding.inflate(inflater, container, false);

        // Configure up button
        binding.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        binding.toolbar.setNavigationOnClickListener(
                (v) -> NavHostFragment.findNavController(this).navigateUp());

        // Configure recycler view
        RecyclerView recyclerView = binding.list;
        Context context = requireContext();
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecyclerAdapter = new DownloadableFormatEntryRecyclerAdapter(mDownloadManager.getEntries());
        recyclerView.setAdapter(mRecyclerAdapter);
        DividerItemDecoration decoration = new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(decoration);

        // Download the list
        mDownloadManager.startDownloadList();

        return binding.getRoot();
    }

}