package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.service.OverlayStateManager

/**
 * Tracks application lifecycle state to prevent unnecessary background CPU and battery drain.
 * When the app is minimized/in background and no floating overlay is active, all background
 * polling loops are suspended.
 */
object AppLifecycleTracker {

    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private var activeActivitiesCount = 0

    fun onActivityStarted() {
        activeActivitiesCount++
        _isForeground.value = true
    }

    fun onActivityStopped() {
        activeActivitiesCount = (activeActivitiesCount - 1).coerceAtLeast(0)
        if (activeActivitiesCount == 0) {
            _isForeground.value = false
        }
    }

    /**
     * All-Time Active: Background workers and polling loops are always allowed to run,
     * ensuring APIs remain active whether user is foreground, background, or overlay.
     */
    fun canRunBackgroundWork(): Boolean {
        return true
    }
}
