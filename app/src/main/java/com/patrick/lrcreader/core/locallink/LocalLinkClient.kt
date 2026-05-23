package com.patrick.lrcreader.core.locallink

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

class LocalLinkClient(
    private val host: String,
    private val port: Int,
    private val sessionId: String,
    private val token: String,
    private val deviceName: String,
    private val reconnectAttempts: Int = 0,
    private val reconnectDelayMs: Long = 500L,
    private val connectTimeoutMs: Int = 1_500,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Closeable {
    @Volatile
    var session: LocalLinkSession = LocalLinkSession(sessionId = sessionId)
        private set

    @Volatile
    var lastFailureReason: String? = null
        private set

    private var connection: LocalLinkConnection? = null

    suspend fun connect(
        scope: CoroutineScope,
        onMessage: (LocalLinkMessage) -> Unit = {}
    ): Boolean {
        val attempts = reconnectAttempts.coerceAtLeast(0) + 1
        repeat(attempts) { attempt ->
            if (connectOnce(scope, onMessage)) {
                return true
            }
            if (attempt < attempts - 1) {
                delay(reconnectDelayMs.coerceAtLeast(0L))
            }
        }
        return false
    }

    suspend fun send(message: LocalLinkMessage): Boolean {
        return connection?.send(message) ?: false
    }

    suspend fun sendRawLine(rawLine: String): Boolean {
        return connection?.sendRawLine(rawLine) ?: false
    }

    private suspend fun connectOnce(
        scope: CoroutineScope,
        onMessage: (LocalLinkMessage) -> Unit
    ): Boolean = withContext(dispatcher) {
        runCatching {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            val nextConnection = LocalLinkConnection(
                socket = socket,
                dispatcher = dispatcher,
                onClosed = { markDisconnected() }
            )
            connection?.close()
            connection = nextConnection
            session = session.copy(connected = true)
            nextConnection.startReading(scope) { message ->
                handleIncoming(message)
                onMessage(message)
            }
            nextConnection.send(localHello())
        }.onFailure { error ->
            lastFailureReason = error.message ?: error::class.java.simpleName
        }.getOrDefault(false)
    }

    private fun handleIncoming(message: LocalLinkMessage) {
        when (message) {
            is HelloMessage -> {
                session = session.copy(
                    connected = true,
                    remoteDeviceName = message.deviceName
                )
            }
            is PingMessage -> {
                session = session.copy(lastPing = message.seq)
            }
            else -> Unit
        }
    }

    private fun localHello(): HelloMessage {
        return HelloMessage(
            sessionId = sessionId,
            token = token,
            deviceName = deviceName
        )
    }

    private fun markDisconnected() {
        session = session.copy(connected = false)
    }

    override fun close() {
        connection?.close()
        connection = null
        markDisconnected()
    }
}
