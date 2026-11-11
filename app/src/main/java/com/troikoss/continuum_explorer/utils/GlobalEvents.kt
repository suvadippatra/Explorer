package com.troikoss.continuum_explorer.utils

import com.troikoss.continuum_explorer.model.LibraryItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A global event bus to communicate between different windows/instances of the app.
 */
object GlobalEvents {
    private val _refreshEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()

    private val _configChangeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val configChangeEvent = _configChangeEvent.asSharedFlow()

    private val _openLibraryItemEvent = MutableSharedFlow<LibraryItem>(extraBufferCapacity = 1)
    val openLibraryItemEvent = _openLibraryItemEvent.asSharedFlow()

    /**
     * Triggers a refresh across all active file explorer windows for file contents.
     */
    fun triggerRefresh() {
        _refreshEvent.tryEmit(Unit)
    }

    /**
     * Triggers a configuration sync across all active file explorer windows (e.g. favorites changes).
     */
    fun triggerConfigUpdate() {
        _configChangeEvent.tryEmit(Unit)
    }

    /**
     * Navigates the active FileExplorer window to a specific LibraryItem (such as Server Hub).
     */
    fun requestOpenLibraryItem(item: LibraryItem) {
        _openLibraryItemEvent.tryEmit(item)
    }
}
