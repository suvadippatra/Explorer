package com.troikoss.continuum_explorer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.troikoss.continuum_explorer.managers.SettingsManager
import com.troikoss.continuum_explorer.providers.StorageProviders
import com.troikoss.continuum_explorer.providers.UniversalFileFetcher
import com.troikoss.continuum_explorer.server.ServerManager
import com.troikoss.continuum_explorer.server.SyncPlayManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSAdminConfigStore
import com.troikoss.continuum_explorer.server.omnios.OmniOSAudioRoutingManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSDisplayProjectionManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSHostServer
import com.troikoss.continuum_explorer.server.omnios.OmniOSPairingManager
import com.troikoss.continuum_explorer.server.omnios.OmniOSStateEngine
import com.troikoss.continuum_explorer.ui.theme.preloadAppFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application(), ImageLoaderFactory {

    private val appWarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        OmniOSAdminConfigStore.init(this)
        OmniOSPairingManager.init()
        OmniOSDisplayProjectionManager.init(this)
        OmniOSAudioRoutingManager.init(this)
        ServerManager.registerCastingBridge(OmniOSHostServer)
        SyncPlayManager.onRoomStateChangedListener = { roomState ->
            OmniOSStateEngine.syncWithSyncPlayState(roomState)
        }
        appWarmupScope.launch {
            preloadAppFonts(this@MainApplication)
            StorageProviders.init(this@MainApplication)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(UniversalFileFetcher.Factory())
            add(VideoFrameDecoder.Factory())
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
