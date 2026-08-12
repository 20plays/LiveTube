package com.livetube.player.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.livetube.player.R
import com.livetube.player.data.CachedStreamEntity
import com.livetube.player.data.LibraryItemEntity
import com.livetube.player.databinding.ItemLibraryBinding
import com.livetube.player.databinding.ItemStreamBinding
import com.livetube.player.util.formatDuration

class LibraryAdapter(
    private val onClick: (LibraryItemEntity) -> Unit,
) : RecyclerView.Adapter<LibraryAdapter.VH>() {

    private val items = mutableListOf<LibraryItemEntity>()

    fun submitList(newItems: List<LibraryItemEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemLibraryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLibraryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.name.text = item.name
        val isChannel = item.kind == "channel"
        b.subtitle.text = if (isChannel) "Channel" else "Playlist"
        b.kindIcon.setImageResource(
            if (isChannel) R.drawable.ic_video_library else R.drawable.ic_playlist,
        )
        b.thumb.load(item.thumbnail) {
            crossfade(true)
            placeholder(R.drawable.ic_video_library)
            error(R.drawable.ic_video_library)
        }
        b.root.setOnClickListener { onClick(item) }
        b.thumb.isVisible = item.thumbnail != null
    }
}

class StreamAdapter(
    private val onClick: (CachedStreamEntity) -> Unit,
) : RecyclerView.Adapter<StreamAdapter.VH>() {

    private val items = mutableListOf<CachedStreamEntity>()

    fun submitList(newItems: List<CachedStreamEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemStreamBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStreamBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.title.text = item.title
        b.sub.text = if (item.isLive) "LIVE" else formatDuration(item.durationSec)
        b.thumb.load(item.thumbnail) {
            crossfade(true)
            error(R.drawable.ic_video_library)
        }
        b.root.setOnClickListener { onClick(item) }
    }
}