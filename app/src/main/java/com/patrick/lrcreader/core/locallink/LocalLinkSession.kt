package com.patrick.lrcreader.core.locallink

data class LocalLinkSession(
    val sessionId: String,
    val connected: Boolean = false,
    val lastPing: Long? = null,
    val remoteDeviceName: String? = null
)
