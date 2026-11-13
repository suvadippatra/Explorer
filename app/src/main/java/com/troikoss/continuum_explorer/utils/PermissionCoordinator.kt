package com.troikoss.continuum_explorer.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-safe, serialized runtime permission coordinator.
 *
 * Root Cause Addressed:
 * Calling multiple `ActivityResultLauncher.launch(...)` instances back-to-back during `onCreate`
 * (e.g. Storage permissions followed immediately by `POST_NOTIFICATIONS`) triggers Android's
 * `W/Activity: Can request only one set of permissions at a time` and drops the second request.
 *
 * This coordinator batches all required runtime permissions into a single `RequestMultiplePermissions`
 * transaction and queues any subsequent permission requests until the active dialog completes.
 */
class PermissionCoordinator(
    private val activity: ComponentActivity,
    private val onBatchResult: (Map<String, Boolean>) -> Unit = {}
) {
    private val isRequestInFlight = AtomicBoolean(false)
    private val pendingQueue = ConcurrentLinkedQueue<Array<String>>()

    private val multiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            isRequestInFlight.set(false)
            onBatchResult(results)
            drainNextIfPending()
        }

    /**
     * Collects all missing startup runtime permissions (Media Storage + Notifications) and launches
     * at most ONE combined system permission dialog.
     */
    fun requestStartupPermissionsIfNeeded() {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isGranted(activity, Manifest.permission.READ_MEDIA_IMAGES)) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (!isGranted(activity, Manifest.permission.READ_MEDIA_VIDEO)) {
                missing.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (!isGranted(activity, Manifest.permission.READ_MEDIA_AUDIO)) {
                missing.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (!isGranted(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (!isGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                missing.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (!isGranted(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (missing.isNotEmpty()) {
            enqueuePermissions(missing.distinct().toTypedArray())
        }
    }

    fun enqueuePermissions(permissions: Array<String>) {
        val ungranted = permissions.filter { !isGranted(activity, it) }.distinct().toTypedArray()
        if (ungranted.isEmpty()) return
        pendingQueue.offer(ungranted)
        drainNextIfPending()
    }

    private fun drainNextIfPending() {
        if (!isRequestInFlight.compareAndSet(false, true)) return
        val nextBatch = pendingQueue.poll()
        if (nextBatch == null) {
            isRequestInFlight.set(false)
            return
        }
        try {
            multiplePermissionsLauncher.launch(nextBatch)
        } catch (_: Exception) {
            isRequestInFlight.set(false)
        }
    }

    companion object {
        fun isGranted(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
