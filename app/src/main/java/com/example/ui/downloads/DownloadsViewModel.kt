package com.example.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadRepository
import com.example.data.local.DownloadedVideoEntity
import com.example.service.ActiveDownloadJob
import com.example.service.DownloadCoordinator
import com.example.service.DownloadService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val searchQuery: String = "",
    val activeJobs: List<ActiveDownloadJob> = emptyList(),
    val savedVideos: List<DownloadedVideoEntity> = emptyList()
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val savedVideosFlow = _searchQuery.flatMapLatest { query ->
        downloadRepository.searchDownloads(query)
    }

    val uiState: StateFlow<DownloadsUiState> = combine(
        _searchQuery,
        downloadCoordinator.jobs,
        savedVideosFlow
    ) { query, jobs, saved ->
        DownloadsUiState(
            searchQuery = query,
            activeJobs = jobs,
            savedVideos = saved
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DownloadsUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun cancelJob(context: Context, jobId: String) {
        DownloadService.cancelJob(context, jobId)
    }

    fun dismissInactiveJob(jobId: String) {
        downloadCoordinator.dismissInactiveJob(jobId)
    }

    fun deleteVideo(entity: DownloadedVideoEntity) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(entity)
        }
    }

    class Factory(
        private val downloadRepository: DownloadRepository,
        private val downloadCoordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadsViewModel(downloadRepository, downloadCoordinator) as T
        }
    }
}
