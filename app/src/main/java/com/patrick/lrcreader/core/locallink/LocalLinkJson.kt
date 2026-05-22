package com.patrick.lrcreader.core.locallink

object LocalLinkJson {
    fun encode(message: LocalLinkMessage): String = message.toJsonString()

    fun decode(rawLine: String): LocalLinkMessage {
        return LocalLinkMessage.fromJsonString(rawLine.trim())
    }
}
