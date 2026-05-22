package com.patrick.lrcreader.core.locallink

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.ServerSocket

class LocalLinkServer(
    private val sessionId: String,
    private val token: String,
    private val deviceName: String,
    private val port: Int = 0,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Closeable {
    @Volatile
    var session: LocalLinkSession = LocalLinkSession(sessionId = sessionId)
        private set

    @Volatile
    var boundPort: Int = -1
        private set

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var connection: LocalLinkConnection? = null

    suspend fun start(
        scope: CoroutineScope,
        onMessage: (LocalLinkMessage) -> Unit = {}
    ): Int = withContext(dispatcher) {
        if (serverSocket != null) return@withContext boundPort

        val socket = ServerSocket(port)
        serverSocket = socket
        boundPort = socket.localPort
        acceptJob = scope.launch(dispatcher) {
            runCatching {
                val clientSocket = socket.accept()
                val nextConnection = LocalLinkConnection(
                    socket = clientSocket,
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
            }.onFailure {
                markDisconnected()
            }
        }
        boundPort
    }

    suspend fun send(message: LocalLinkMessage): Boolean {
        return connection?.send(message) ?: false
    }

    suspend fun sendRawLine(rawLine: String): Boolean {
        return connection?.sendRawLine(rawLine) ?: false
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
        acceptJob?.cancel()
        acceptJob = null
        connection?.close()
        connection = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = -1
        markDisconnected()
    }
}
