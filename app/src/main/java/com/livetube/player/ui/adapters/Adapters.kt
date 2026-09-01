package com.livetube.player.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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
) : ListAdapter<LibraryItemEntity, LibraryAdapter.VH>(LibraryDiff) {

    class VH(val binding: ItemLibraryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLibraryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
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

    private object LibraryDiff : DiffUtil.ItemCallback<LibraryItemEntity>() {
        override fun areItemsTheSame(
            oldItem: LibraryItemEntity,
            newItem: LibraryItemEntity,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: LibraryItemEntity,
            newItem: LibraryItemEntity,
        ): Boolean = oldItem == newItem
    }
}

class StreamAdapter(
    private val onClick: (CachedStreamEntity) -> Unit,
) : ListAdapter<CachedStreamEntity, StreamAdapter.VH>(StreamDiff) {

    class VH(val binding: ItemStreamBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStreamBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.title.text = item.title
        b.sub.text = if (item.isLive) "LIVE" else formatDuration(item.durationSec)
        b.thumb.load(item.thumbnail) {
            crossfade(true)
            error(R.drawable.ic_video_library)
        }
        b.root.setOnClickListener { onClick(item) }
    }

    private object StreamDiff : DiffUtil.ItemCallback<CachedStreamEntity>() {
        override fun areItemsTheSame(
            oldItem: CachedStreamEntity,
            newItem: CachedStreamEntity,
        ): Boolean =
            oldItem.itemId == newItem.itemId && oldItem.position == newItem.position

        override fun areContentsTheSame(
            oldItem: CachedStreamEntity,
            newItem: CachedStreamEntity,
        ): Boolean = oldItem == newItem
    }
}