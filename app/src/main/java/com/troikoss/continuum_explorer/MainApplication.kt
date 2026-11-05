package com.troikoss.continuum_explorer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.providers.StorageProviders
import com.troikoss.continuum_explorer.providers.UniversalFileFetcher

class MainApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        StorageProviders.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(UniversalFileFetcher.Factory()) }
        .respectCacheHeaders(false)
        .build()
}
