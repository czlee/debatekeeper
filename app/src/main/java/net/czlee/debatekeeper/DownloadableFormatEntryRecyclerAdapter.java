package net.czlee.debatekeeper;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.czlee.debatekeeper.DebateFormatDownloadManager.DownloadableFormatEntry;
import net.czlee.debatekeeper.databinding.ViewFormatDownloadBinding;

import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DownloadableFormatEntry}.
 */
public class DownloadableFormatEntryRecyclerAdapter extends RecyclerView.Adapter<DownloadableFormatEntryRecyclerAdapter.ViewHolder> {

    private final List<DownloadableFormatEntry> mEntries;

    //******************************************************************************************
    // Public classes
    //******************************************************************************************

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ViewFormatDownloadBinding binding;
        public DownloadableFormatEntry item;

        public ViewHolder(ViewFormatDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    //******************************************************************************************
    // Public constructor
    //******************************************************************************************

    public DownloadableFormatEntryRecyclerAdapter(List<DownloadableFormatEntry> entries) {
        mEntries = entries;
    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        return new ViewHolder(ViewFormatDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        DownloadableFormatEntry entry = mEntries.get(position);
        holder.item = entry;
        holder.binding.viewFormatTitle.setText(entry.styleName);
        holder.binding.viewFormatFileNameValue.setText(entry.filename);
        holder.binding.viewFormatRegionValue.setText(TextUtils.join("\n", entry.regions));
        holder.binding.viewFormatUsedAtValue.setText(TextUtils.join("\n", entry.usedAts));
        holder.binding.viewFormatLevelValue.setText(TextUtils.join("\n", entry.levels));
        holder.binding.viewFormatDescValue.setText(entry.description);
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

}