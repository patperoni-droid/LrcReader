package com.patrick.lrcreader.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DjScanState {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun start() { _isScanning.value = true }
    fun stop() { _isScanning.value = false }
}