package com.livetube.player.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.livetube.player.R
import com.livetube.player.databinding.FragmentPlayerBinding
import com.livetube.player.extractor.Yt
import com.livetube.player.playback.Playback
import com.livetube.player.util.Prefs
import com.livetube.player.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private lateinit var binding: FragmentPlayerBinding

    private val vm: PlayerViewModel by viewModels()
    private var audioOnly = true
    private var userSeeking = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentPlayerBinding.bind(view)

        binding.playerView.player = Playback.player

        audioOnly = Prefs.audioOnly(requireContext())
        binding.audioSwitch.isChecked = audioOnly

        binding.playBtn.setOnClickListener { playPastedUrl() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                playPastedUrl()
                true
            } else false
        }
        binding.audioSwitch.setOnCheckedChangeListener { _, checked ->
            onAudioModeChanged(checked)
        }
        binding.autoNextSwitch.isChecked = Prefs.autoNext(requireContext())
        binding.autoNextSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setAutoNext(requireContext(), checked)
        }
        binding.audioPlayPause.setOnClickListener {
            Playback.togglePlayPause()
            updatePlayPauseIcon()
        }
        binding.audioStop.setOnClickListener { Playback.stop() }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) =
                if (fromUser) updateTimeLabel(progress.toLong()) else Unit

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                userSeeking = false
                val position = seekBar?.progress?.toLong() ?: 0L
                Playback.player.seekTo(position)
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.nowPlaying.collect { updateUi(it) } }
                launch { ticker() }
            }
        }
    }

    override fun onDestroyView() {
        binding.playerView.player = null
        super.onDestroyView()
    }

    private fun playPastedUrl() {
        val raw = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        setStatus("Preparing…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (raw.contains("youtube.com") || raw.contains("youtu.be")) {
                runCatching { Yt.resolveStream(raw, audioOnly) }
            } else {
                // Not a YouTube link: treat it as a direct media URL (podcast streams, etc.)
                val title = raw.substringAfterLast('/').take(40).ifBlank { "Direct URL" }
                Result.success(Yt.StreamPlay(raw, false, title))
            }
            result.fold(
                onSuccess = { play -> Playback.play(raw, play.url, play.title, play.live) },
                onFailure = { e -> setStatus(e.message ?: "Failed to play") },
            )
        }
    }

    private fun onAudioModeChanged(checked: Boolean) {
        vm.setAudioOnly(checked)
        audioOnly = checked
        val np = Playback.nowPlaying.value ?: run {
            updateLayoutVisibility(false)
            return
        }
        if (np.live) {
            updateLayoutVisibility(true)
            return
        }
        val watch = Playback.watchUrl.value ?: return
        val position = Playback.player.currentPosition
        setStatus("Preparing…")
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { Yt.resolveStream(watch, checked) }
            result.fold(
                onSuccess = { play ->
                    Playback.restartWith(watch, play.url, play.title, play.live, position)
                    setStatus(play.title)
                },
                onFailure = { e -> setStatus(e.message ?: "Failed to switch") },
            )
        }
    }

    private fun updateUi(np: Playback.NowPlaying?) {
        if (np == null) {
            binding.status.text = ""
            binding.seekBar.progress = 0
            binding.seekBar.max = 0
            updateTimeLabel(0)
            updateLayoutVisibility(false)
        } else {
            binding.status.text = np.title
            updateLayoutVisibility(true)
        }
    }

    private fun updateLayoutVisibility(playing: Boolean) {
        val showVideo = playing && !audioOnly
        val showAudio = playing && audioOnly
        binding.videoCard.isVisible = showVideo
        binding.audioControls.isVisible = showAudio
        binding.timeLabel.isVisible = showAudio
    }

    private suspend fun ticker() {
        while (true) {
            val player = Playback.player
            val duration = player.duration.takeIf { it > 0 }?.toInt() ?: 0
            if (duration != binding.seekBar.max) binding.seekBar.max = duration
            if (!userSeeking) binding.seekBar.progress = player.currentPosition.toInt()
            if (!userSeeking) updateTimeLabel(player.currentPosition)
            updatePlayPauseIcon()
            delay(500)
        }
    }

    private fun updatePlayPauseIcon() {
        val playing = Playback.player.isPlaying
        binding.audioPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateTimeLabel(positionMs: Long) {
        val duration = Playback.player.duration.takeIf { it > 0 }?.toLong() ?: 0L
        val text = if (duration > 0) {
            "${formatDuration(positionMs / 1000)} / ${formatDuration(duration / 1000)}"
        } else ""
        binding.timeLabel.text = text
    }

    private fun setStatus(text: String) {
        binding.status.text = text
    }
}