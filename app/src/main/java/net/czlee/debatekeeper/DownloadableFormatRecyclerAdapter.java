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
            binding.viewFormatTitle.setText(entry.name);
            binding.viewFormatFileNameValue.setText(entry.filename);
            binding.viewFormatRegionValue.setText(TextUtils.join("\n", entry.regions));
            binding.viewFormatUsedAtValue.setText(TextUtils.join("\n", entry.usedAts));
            binding.viewFormatLevelValue.setText(TextUtils.join("\n", entry.levels));
            binding.viewFormatDescValue.setText(entry.description);

            View.OnClickListener expandListener = (v) -> {
                this.entry.expanded = !this.entry.expanded;
                updateExpandedState();
            };
            binding.viewFormatChevron.setOnClickListener(expandListener);
            binding.viewFormatTitle.setOnClickListener(expandListener);
            binding.viewFormatStatusIcon.setOnClickListener(expandListener);

            updateExpandedState();
        }

        void updateDownloadProgress() {
            int buttonVisibility = View.GONE;
            int progressVisibility = View.GONE;
            int doneVisibility = View.GONE;
            int statusIconVisibility = View.GONE;
            int updateTextVisibility = View.GONE;

            switch (entry.state) {
                case UPDATE_AVAILABLE:
                    binding.viewFormatStatusIcon.setImageResource(R.drawable.ic_baseline_upgrade_24);
                    statusIconVisibility = View.VISIBLE;
                    if (entry.expanded) updateTextVisibility = View.VISIBLE;
                case NOT_DOWNLOADED:
                    binding.viewFormatDownloadButton.setOnClickListener(
                            (v) -> mDownloadManager.startDownloadFile(entry, this));
                    if (entry.expanded) buttonVisibility = View.VISIBLE;
                    break;
                case DOWNLOAD_IN_PROGRESS:
                    if (entry.expanded) progressVisibility = View.VISIBLE;
                    break;
                case DOWNLOADED:
                    binding.viewFormatStatusIcon.setImageResource(R.drawable.ic_outline_file_download_done_24);
                    if (entry.expanded) {
                        doneVisibility = View.VISIBLE;
                        statusIconVisibility = View.GONE;
                    }
                    else statusIconVisibility = View.VISIBLE;  // show the status icon only when collapsed
                    break;
            }

            binding.viewFormatDownloadButton.setVisibility(buttonVisibility);
            binding.viewFormatDownloadProgress.setVisibility(progressVisibility);
            binding.viewFormatDownloadDone.setVisibility(doneVisibility);
            binding.viewFormatUpdateAvailableText.setVisibility(updateTextVisibility);
            binding.viewFormatStatusIcon.setVisibility(statusIconVisibility);
        }

        void updateExpandedState() {
            binding.viewFormatDetailsGroup.setVisibility((entry.expanded) ? View.VISIBLE : View.GONE);
            updateDownloadProgress();
            binding.viewFormatChevron.setImageResource((entry.expanded)
                    ? R.drawable.ic_baseline_expand_more_24
                    : R.drawable.ic_baseline_chevron_right_24);
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