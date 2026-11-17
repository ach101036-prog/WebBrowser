package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkRepository
import com.example.data.local.DownloadRepository
import com.example.service.DownloadCoordinator
import com.example.service.DownloadNotificationHelper

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val downloadRepository = DownloadRepository(
        context = context,
        dao = database.downloadedVideoDao()
    )
    val bookmarkRepository = BookmarkRepository(
        dao = database.bookmarkDao()
    )
    val downloadCoordinator = DownloadCoordinator()
}

class FlowBrowserApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DownloadNotificationHelper.ensureChannels(this)
    }
}
