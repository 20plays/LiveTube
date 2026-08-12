package com.livetube.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.livetube.player.data.CachedStreamEntity
import com.livetube.player.data.LibraryItemEntity
import com.livetube.player.data.Repo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(application: Application, private val itemId: String) :
    AndroidViewModel(application) {

    data class Ui(
        val item: LibraryItemEntity?,
        val streams: List<CachedStreamEntity>,
        val loadingMore: Boolean,
        val loadingAll: Boolean,
        val allProgress: Int,
    )

    private val _loadingMore = MutableStateFlow(false)
    private val _loadingAll = MutableStateFlow(false)
    private val _allProgress = MutableStateFlow(0)

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message = _message.asSharedFlow()

    val ui = combine(
        Repo.dao().observeItem(itemId),
        Repo.dao().observeStreams(itemId),
        _loadingMore,
        _loadingAll,
        _allProgress,
    ) { item, streams, loadingMore, loadingAll, progress ->
        Ui(item, streams, loadingMore, loadingAll, progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Ui(null, emptyList(), false, false, 0))

    fun onScrollNearEnd() {
        if (!_loadingMore.value && !_loadingAll.value) {
            loadMore()
        }
    }

    fun loadMore() {
        if (_loadingMore.value || _loadingAll.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                Repo.loadMore(itemId)
            } catch (e: Exception) {
                _message.tryEmit(e.message ?: "Couldn't load more.")
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                Repo.refresh(itemId)
                _message.tryEmit("Refreshed")
            } catch (e: Exception) {
                _message.tryEmit(e.message ?: "Couldn't refresh.")
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun loadAll() {
        if (_loadingAll.value) return
        viewModelScope.launch {
            _loadingAll.value = true
            _allProgress.value = 0
            try {
                Repo.loadAll(itemId) { added -> _allProgress.value += added }
            } catch (e: Exception) {
                _message.tryEmit("Stopped: ${e.message ?: "error"}")
            } finally {
                _loadingAll.value = false
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            Repo.delete(itemId)
        }
    }

    companion object {
        fun factory(itemId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DetailViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, itemId) }
            }
    }
}