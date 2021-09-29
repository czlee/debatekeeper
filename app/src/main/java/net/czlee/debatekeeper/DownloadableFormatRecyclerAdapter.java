package net.czlee.debatekeeper;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.czlee.debatekeeper.DebateFormatDownloadManager.DownloadableFormatEntry;
import net.czlee.debatekeeper.databinding.ViewFormatDownloadBinding;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DownloadableFormatEntry}.
 */
public class DownloadableFormatRecyclerAdapter extends RecyclerView.Adapter<DownloadableFormatRecyclerAdapter.ViewHolder> {

    private final DebateFormatDownloadManager mDownloadManager;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewFormatDownloadBinding binding;
        private DownloadableFormatEntry entry;

        ViewHolder(ViewFormatDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final DownloadableFormatEntry entry) {
            this.entry = entry;
            binding.viewFormatTitle.setText(entry.styleName);
            binding.viewFormatFileNameValue.setText(entry.filename);
            binding.viewFormatRegionValue.setText(TextUtils.join("\n", entry.regions));
            binding.viewFormatUsedAtValue.setText(TextUtils.join("\n", entry.usedAts));
            binding.viewFormatLevelValue.setText(TextUtils.join("\n", entry.levels));
            binding.viewFormatDescValue.setText(entry.description);
            updateDownloadProgress();
        }

        void updateDownloadProgress() {
            switch (entry.state) {
                case NOT_DOWNLOADED:
                case UPDATE_AVAILABLE:
                    binding.viewFormatDownloadButton.setOnClickListener(
                            (v) -> mDownloadManager.startDownloadFile(entry, this));
                    binding.viewFormatDownloadButton.setVisibility(View.VISIBLE);
                    binding.viewFormatDownloadProgress.setVisibility(View.GONE);
                    binding.viewFormatDownloadDone.setVisibility(View.GONE);
                    break;
                case DOWNLOAD_IN_PROGRESS:
                    binding.viewFormatDownloadButton.setVisibility(View.GONE);
                    binding.viewFormatDownloadProgress.setVisibility(View.VISIBLE);
                    binding.viewFormatDownloadDone.setVisibility(View.GONE);
                    break;
                case DOWNLOADED:
                    binding.viewFormatDownloadButton.setVisibility(View.GONE);
                    binding.viewFormatDownloadProgress.setVisibility(View.GONE);
                    binding.viewFormatDownloadDone.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    //******************************************************************************************
    // Public constructor
    //******************************************************************************************

    public DownloadableFormatRecyclerAdapter(DebateFormatDownloadManager downloadManager) {
        mDownloadManager = downloadManager;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewFormatDownloadBinding binding = ViewFormatDownloadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        DownloadableFormatEntry entry = mDownloadManager.getEntries().get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return mDownloadManager.getEntries().size();
    }

}