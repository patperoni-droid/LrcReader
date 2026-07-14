package com.patrick.lrcreader.core.locallink

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.ServerSocket

private const val LOCAL_LINK_DIAG_TAG = "LOCAL_LINK_DIAG"

private fun logLocalLinkDiag(message: String) {
    runCatching { Log.d(LOCAL_LINK_DIAG_TAG, message) }
}

private fun logLocalLinkDiagWarning(message: String, throwable: Throwable) {
    runCatching { Log.w(LOCAL_LINK_DIAG_TAG, message, throwable) }
}

class LocalLinkServer(
    private val sessionId: String,
    private val token: String,
    private val deviceName: String,
    private val deviceId: String? = null,
    private val deviceRole: String? = null,
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
        if (serverSocket != null) {
            logLocalLinkDiag(
                "server_start_reused startedPort=$boundPort socketLocalPort=${serverSocket?.localPort} isClosed=${serverSocket?.isClosed}"
            )
            return@withContext boundPort
        }

        logLocalLinkDiag("server_start_requested requestedPort=$port")
        val socket = ServerSocket(port)
        serverSocket = socket
        boundPort = socket.localPort
        logLocalLinkDiag(
            "server_socket_bound startedPort=$boundPort socketLocalPort=${socket.localPort} isClosed=${socket.isClosed}"
        )
        acceptJob = scope.launch(dispatcher) {
            logLocalLinkDiag(
                "server_accept_loop_listening startedPort=$boundPort socketLocalPort=${socket.localPort} isClosed=${socket.isClosed}"
            )
            while (!socket.isClosed) {
                runCatching {
                    val clientSocket = socket.accept()
                    logLocalLinkDiag(
                        "server_client_accepted startedPort=$boundPort localPort=${clientSocket.localPort} remote=${clientSocket.inetAddress?.hostAddress}:${clientSocket.port}"
                    )
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
                    if (!socket.isClosed) {
                        logLocalLinkDiagWarning("server_accept_failed startedPort=$boundPort", it)
                        markDisconnected()
                    }
                }
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
                    remoteDeviceName = message.deviceName,
                    remoteDeviceId = message.deviceId,
                    remoteDeviceRole = message.deviceRole
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
            deviceName = deviceName,
            deviceId = deviceId,
            deviceRole = deviceRole
        )
    }

    private fun markDisconnected() {
        session = session.copy(connected = false)
    }

    override fun close() {
        logLocalLinkDiag(
            "server_close startedPort=$boundPort socketLocalPort=${serverSocket?.localPort} isClosed=${serverSocket?.isClosed}"
        )
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
