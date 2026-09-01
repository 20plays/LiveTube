package com.livetube.player.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.livetube.player.R
import com.livetube.player.data.CachedStreamEntity
import com.livetube.player.databinding.FragmentDetailBinding
import com.livetube.player.extractor.Yt
import com.livetube.player.playback.Playback
import com.livetube.player.ui.adapters.StreamAdapter
import com.livetube.player.util.Prefs
import kotlinx.coroutines.launch

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val itemId: String by lazy { requireArguments().getString("itemId").orEmpty() }
    private val vm: DetailViewModel
            by viewModels { DetailViewModel.factory(itemId) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_refresh -> {
                    vm.refresh()
                    true
                }
                R.id.menu_load_all -> {
                    confirmLoadAll()
                    true
                }
                R.id.menu_remove -> {
                    confirmRemove()
                    true
                }
                else -> false
            }
        }

        val adapter = StreamAdapter { stream -> playStream(stream) }
        binding.rv.layoutManager = LinearLayoutManager(requireContext())
        binding.rv.adapter = adapter

        binding.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val last = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                if (total > 0 && last >= total - 3) {
                    vm.onScrollNearEnd()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.ui.collect { state ->
                        val item = state.item
                        binding.toolbar.title = item?.name ?: "Detail"
                        adapter.submitList(state.streams)

                        binding.empty.isVisible =
                            item != null && state.streams.isEmpty() && !state.loadingMore &&
                                !state.loadingAll
                        binding.empty.text =
                            if (item == null) "Item not found" else "No videos loaded yet"

                        binding.footer.isVisible =
                            state.loadingMore || state.loadingAll
                        binding.footerText.text = when {
                            state.loadingAll ->
                                if (state.allProgress > 0) "Loading all… $state.allProgress videos" else "Loading all…"
                            else -> "Loading more…"
                        }
                    }
                }
                launch {
                    vm.message.collect { text ->
                        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun confirmLoadAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.load_all_confirm_title)
            .setMessage(R.string.load_all_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ -> vm.loadAll() }
            .show()
    }

    private fun confirmRemove() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove this from your library?")
            .setMessage("The saved list and cached feed will be deleted.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                vm.delete()
                findNavController().navigateUp()
            }
            .show()
    }

    private fun playStream(stream: CachedStreamEntity) {
        val audioOnly = Prefs.audioOnly(requireContext())
        val current = vm.ui.value.streams
        val startIndex = current.indexOfFirst { it.videoUrl == stream.videoUrl }
        val queue = if (startIndex >= 0) {
            current.drop(startIndex + 1).map {
                Playback.QueueItem(it.videoUrl, it.title, it.isLive)
            }
        } else {
            emptyList()
        }
        val root = _binding?.root ?: return
        val snackbar = Snackbar.make(root, R.string.preparing, Snackbar.LENGTH_LONG)
        snackbar.show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (stream.videoUrl.contains("youtube.com") || stream.videoUrl.contains("youtu.be")) {
                runCatching { Yt.resolveStream(stream.videoUrl, audioOnly) }
            } else {
                Result.success(Yt.StreamPlay(stream.videoUrl, stream.isLive, stream.title))
            }
            snackbar.dismiss()
            result.fold(
                onSuccess = { play ->
                    Playback.play(stream.videoUrl, play.url, play.title, play.live, queue)
                },
                onFailure = { e ->
                    _binding?.root?.let { rootView ->
                        Snackbar.make(rootView, e.message ?: "Failed to play", Snackbar.LENGTH_LONG).show()
                    }
                },
            )
        }
    }
    override fun onDestroyView() {
        binding.rv.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
