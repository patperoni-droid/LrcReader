package com.patrick.lrcreader.core.locallink

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.net.Socket
import java.nio.charset.StandardCharsets

class LocalLinkConnection internal constructor(
    private val socket: Socket,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onClosed: () -> Unit = {}
) : Closeable {
    private val writeMutex = Mutex()
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    fun startReading(
        scope: CoroutineScope,
        onMessage: (LocalLinkMessage) -> Unit
    ) {
        if (readerJob != null) return
        readerJob = scope.launch(dispatcher) {
            runCatching {
                socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    while (!socket.isClosed) {
                        val line = reader.readLine() ?: break
                        onMessage(LocalLinkJson.decode(line))
                    }
                }
            }
            close()
        }
    }

    suspend fun send(message: LocalLinkMessage): Boolean {
        val rawLine = withContext(dispatcher) {
            LocalLinkJson.encode(message)
        }
        return sendRawLine(rawLine)
    }

    suspend fun sendRawLine(rawLine: String): Boolean = withContext(dispatcher) {
        writeMutex.withLock {
            runCatching {
                val activeWriter = writer ?: socket.getOutputStream()
                    .bufferedWriter(StandardCharsets.UTF_8)
                    .also { writer = it }
                activeWriter.write(rawLine)
                activeWriter.newLine()
                activeWriter.flush()
                true
            }.getOrDefault(false)
        }
    }

    override fun close() {
        readerJob?.cancel()
        readerJob = null
        runCatching { writer?.close() }
        writer = null
        runCatching { socket.close() }
        onClosed()
    }
}
