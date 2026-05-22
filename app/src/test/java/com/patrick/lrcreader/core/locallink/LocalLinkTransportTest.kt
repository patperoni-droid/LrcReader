package com.patrick.lrcreader.core.locallink

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class LocalLinkTransportTest {

    @Test
    fun clientServer_connectAndExchangeHello() = runBlocking {
        val serverMessages = synchronizedMessages()
        val clientMessages = synchronizedMessages()
        val server = testServer()
        val port = server.start(this) { message -> serverMessages += message }
        val client = testClient(port)

        try {
            assertTrue(client.connect(this) { message -> clientMessages += message })

            assertTrue(waitUntil { server.session.connected && client.session.connected })
            assertTrue(waitUntil { serverMessages.any { it is HelloMessage } })
            assertTrue(waitUntil { clientMessages.any { it is HelloMessage } })
            assertEquals("client-device", server.session.remoteDeviceName)
            assertEquals("server-device", client.session.remoteDeviceName)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun clientServer_exchangePingMessages() = runBlocking {
        val serverMessages = synchronizedMessages()
        val clientMessages = synchronizedMessages()
        val server = testServer()
        val port = server.start(this) { message -> serverMessages += message }
        val client = testClient(port)

        try {
            assertTrue(client.connect(this) { message -> clientMessages += message })
            assertTrue(waitUntil { server.session.connected && client.session.connected })

            assertTrue(client.send(PingMessage(seq = 7L)))
            assertTrue(waitUntil { server.session.lastPing == 7L })
            assertTrue(waitUntil { serverMessages.any { it == PingMessage(seq = 7L) } })

            assertTrue(server.send(PingMessage(seq = 8L)))
            assertTrue(waitUntil { client.session.lastPing == 8L })
            assertTrue(waitUntil { clientMessages.any { it == PingMessage(seq = 8L) } })
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun invalidJsonLine_isDeliveredAsUnknownMessage() = runBlocking {
        val serverMessages = synchronizedMessages()
        val server = testServer()
        val port = server.start(this) { message -> serverMessages += message }
        val client = testClient(port)

        try {
            assertTrue(client.connect(this))
            assertTrue(waitUntil { server.session.connected })

            assertTrue(client.sendRawLine("{not-json"))

            assertTrue(waitUntil {
                serverMessages.any { message ->
                    message is UnknownMessage && message.reason.startsWith("invalid_json")
                }
            })
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun clientClose_marksServerDisconnectedWithoutThrowing() = runBlocking {
        val server = testServer()
        val port = server.start(this)
        val client = testClient(port)

        try {
            assertTrue(client.connect(this))
            assertTrue(waitUntil { server.session.connected && client.session.connected })

            client.close()

            assertTrue(waitUntil { !server.session.connected })
            assertFalse(client.session.connected)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun serverAcceptsNewClientAfterDisconnect() = runBlocking {
        val server = testServer()
        val port = server.start(this)
        val firstClient = testClient(port)
        val secondClient = testClient(port)

        try {
            assertTrue(firstClient.connect(this))
            assertTrue(waitUntil { server.session.connected && firstClient.session.connected })

            firstClient.close()
            assertTrue(waitUntil { !server.session.connected })

            assertTrue(secondClient.connect(this))
            assertTrue(waitUntil { server.session.connected && secondClient.session.connected })
            assertEquals("client-device", server.session.remoteDeviceName)
        } finally {
            firstClient.close()
            secondClient.close()
            server.close()
        }
    }

    @Test
    fun clientConnect_returnsFalseWhenServerUnavailable() = runBlocking {
        val client = LocalLinkClient(
            host = "127.0.0.1",
            port = 9,
            sessionId = "session-1",
            token = "token",
            deviceName = "client-device",
            reconnectAttempts = 1,
            reconnectDelayMs = 10L,
            connectTimeoutMs = 100
        )

        try {
            assertFalse(client.connect(this))
            assertFalse(client.session.connected)
        } finally {
            client.close()
        }
    }

    private fun testServer(): LocalLinkServer {
        return LocalLinkServer(
            sessionId = "session-1",
            token = "token",
            deviceName = "server-device"
        )
    }

    private fun testClient(port: Int): LocalLinkClient {
        return LocalLinkClient(
            host = "127.0.0.1",
            port = port,
            sessionId = "session-1",
            token = "token",
            deviceName = "client-device",
            reconnectAttempts = 0,
            reconnectDelayMs = 10L,
            connectTimeoutMs = 500
        )
    }

    private fun synchronizedMessages(): MutableList<LocalLinkMessage> {
        return Collections.synchronizedList(mutableListOf())
    }

    private suspend fun waitUntil(
        timeoutMs: Long = 1_500L,
        condition: () -> Boolean
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (condition()) return true
            delay(20L)
        }
        return condition()
    }
}
