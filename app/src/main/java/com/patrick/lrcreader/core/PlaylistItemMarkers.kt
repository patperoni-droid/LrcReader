package com.patrick.lrcreader.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val GROUP_MARKER_PREFIX = "__SPL_GROUP__|v1|"
private const val GROUP_MARKER_TOKEN = "__SPL_GROUP__"
private const val GROUP_MARKER_VERSION = "v1"
private const val GROUP_DEFAULT_TITLE = "Group"
const val GROUP_END_PREFIX = "__SPL_GROUP_END__|v1|"
private const val GROUP_END_TOKEN = "__SPL_GROUP_END__"
private const val SMP_ITEM_PREFIX = "smp://"

private data class GroupMarkerParts(
    val uuid: String,
    val encodedTitle: String,
    val colorHex: String?
)

fun isGroupHeader(item: String): Boolean = parseGroupMarker(item) != null

fun isGroupEnd(item: String): Boolean = parseGroupEndUuid(item) != null

fun buildGroupHeader(title: String): String {
    val cleanTitle = title.trim().ifBlank { GROUP_DEFAULT_TITLE }
    val encodedTitle = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
    return "$GROUP_MARKER_PREFIX${UUID.randomUUID()}|$encodedTitle"
}

fun getGroupTitle(item: String): String {
    val parts = parseGroupMarker(item) ?: return GROUP_DEFAULT_TITLE
    return runCatching {
        URLDecoder.decode(parts.encodedTitle, StandardCharsets.UTF_8.name()).trim()
    }.getOrDefault(GROUP_DEFAULT_TITLE).ifBlank { GROUP_DEFAULT_TITLE }
}

fun renameGroupHeader(item: String, newTitle: String): String {
    val parts = parseGroupMarker(item) ?: return buildGroupHeader(newTitle)
    val cleanTitle = newTitle.trim().ifBlank { GROUP_DEFAULT_TITLE }
    val encodedTitle = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
    return buildGroupHeaderMarker(parts.uuid, encodedTitle, parts.colorHex)
}

fun getGroupColorArgb(item: String): Long? {
    val colorHex = parseGroupMarker(item)?.colorHex ?: return null
    return colorHex.toLongOrNull(16)
}

fun setGroupColorArgb(item: String, colorArgb: Long?): String {
    val parts = parseGroupMarker(item) ?: return item
    val cleanColor = colorArgb
        ?.takeIf { it in 0x00000000L..0xFFFFFFFFL }
        ?.let { "%08X".format(it) }
    return buildGroupHeaderMarker(parts.uuid, parts.encodedTitle, cleanColor)
}

fun getGroupUuid(item: String): String? {
    parseGroupMarker(item)?.let { return it.uuid }
    return parseGroupEndUuid(item)
}

fun buildGroupEnd(uuid: String): String {
    val cleanUuid = uuid.trim().ifBlank { UUID.randomUUID().toString() }
    return "$GROUP_END_PREFIX$cleanUuid"
}

fun buildSmpItem(songId: String): String {
    val cleanSongId = songId.trim().ifBlank { UUID.randomUUID().toString() }
    val encodedSongId = URLEncoder.encode(cleanSongId, StandardCharsets.UTF_8.name())
    return "$SMP_ITEM_PREFIX$encodedSongId"
}

fun getSmpSongId(item: String): String? {
    if (!item.startsWith(SMP_ITEM_PREFIX)) return null
    val encodedSongId = item.removePrefix(SMP_ITEM_PREFIX).trim()
    if (encodedSongId.isEmpty()) return null
    val decodedSongId = runCatching {
        URLDecoder.decode(encodedSongId, StandardCharsets.UTF_8.name()).trim()
    }.getOrNull()
    return decodedSongId?.takeIf { it.isNotEmpty() }
}

fun isSmpItem(item: String): Boolean = getSmpSongId(item) != null

fun isPrompterItem(item: String): Boolean = item.startsWith("prompter://")

fun isVirtualPlaylistItem(item: String): Boolean =
    isGroupHeader(item) || isGroupEnd(item) || isPrompterItem(item)

fun isPlayableAudioItem(item: String): Boolean = item.isNotBlank() && !isVirtualPlaylistItem(item)

private fun parseGroupMarker(item: String): GroupMarkerParts? {
    if (!item.startsWith(GROUP_MARKER_PREFIX)) return null
    val parts = item.split('|', limit = 5)
    if (parts.size !in 4..5) return null
    if (parts[0] != GROUP_MARKER_TOKEN || parts[1] != GROUP_MARKER_VERSION) return null
    val uuid = parts[2].trim()
    val encodedTitle = parts[3]
    if (uuid.isEmpty() || encodedTitle.isEmpty()) return null
    val colorHex = parts.getOrNull(4)
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.matches(Regex("[0-9A-F]{8}")) }
    return GroupMarkerParts(
        uuid = uuid,
        encodedTitle = encodedTitle,
        colorHex = colorHex
    )
}

private fun buildGroupHeaderMarker(uuid: String, encodedTitle: String, colorHex: String?): String {
    val base = "$GROUP_MARKER_PREFIX$uuid|$encodedTitle"
    return colorHex?.let { "$base|$it" } ?: base
}

private fun parseGroupEndUuid(item: String): String? {
    if (!item.startsWith(GROUP_END_PREFIX)) return null
    val parts = item.split('|', limit = 3)
    if (parts.size != 3) return null
    if (parts[0] != GROUP_END_TOKEN || parts[1] != GROUP_MARKER_VERSION) return null
    val uuid = parts[2].trim()
    if (uuid.isEmpty()) return null
    return uuid
}
