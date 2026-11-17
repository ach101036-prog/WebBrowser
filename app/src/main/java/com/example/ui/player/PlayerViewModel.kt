package com.example.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadRepository
import com.example.data.local.DownloadedVideoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(val video: DownloadedVideoEntity) : PlayerUiState
    data object Missing : PlayerUiState
}

class PlayerViewModel(
    videoId: Long,
    downloadRepository: DownloadRepository
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = downloadRepository.observeDownload(videoId)
        .map { entity ->
            if (entity != null) PlayerUiState.Ready(entity) else PlayerUiState.Missing
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PlayerUiState.Loading
        )

    class Factory(
        private val videoId: Long,
        private val downloadRepository: DownloadRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(videoId, downloadRepository) as T
        }
    }
}
