package com.livetube.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.livetube.player.data.Repo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    val items = Repo.dao().observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    fun refreshAll() {
        viewModelScope.launch {
            _busy.value = true
            try {
                val (ok, failed) = Repo.refreshAll()
                val text = if (ok > 0) "Refreshed $ok items" else "Nothing to refresh"
                _message.tryEmit(
                    if (failed.isEmpty()) text else "$text · failed: ${failed.take(3).joinToString()}",
                )
            } finally {
                _busy.value = false
            }
        }
    }

    fun addUrl(raw: String, onFinished: (String?) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val error = runCatching {
                Repo.addByUrl(raw)
                null
            }.getOrElse { e -> e.message ?: "Couldn't add." }
            _busy.value = false
            onFinished(error)
        }
    }
}