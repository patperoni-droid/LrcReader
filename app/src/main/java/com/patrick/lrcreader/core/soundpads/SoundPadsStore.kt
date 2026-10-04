package com.patrick.lrcreader.core.soundpads

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One durable bank. Disk transactions are serialized even across overlay/store instances. */
class SoundPadsStore(context: Context, private val root: File = File(context.filesDir, "soundpads")) {
    private val app = context.applicationContext
    private val audioDir = File(root, "audio")
    private val bank = AtomicFile(File(root, "bank.json"))

    companion object {
        private val lock = Mutex()
        private val audioName = Regex("[0-9a-fA-F-]{36}\\.audio")
    }

    private suspend fun <T> transaction(action: () -> T): T =
        withContext(Dispatchers.IO + NonCancellable) {
            lock.withLock {
                check(audioDir.isDirectory || audioDir.mkdirs())
                action()
            }
        }

    suspend fun load(): List<SoundPad> = transaction {
        read().also { cleanup(it) }
    }

    suspend fun add(name: String): List<SoundPad> = transaction {
        val pads = read() + SoundPad(UUID.randomUUID().toString(), name.trim())
        write(pads)
        pads
    }

    suspend fun update(id: String, name: String, inMs: Long, outMs: Long?, volume: Float): List<SoundPad> = transaction {
        val pads = read()
        val previous = pads.single { it.padId == id }
        val next = previous.copy(name = name.trim(), inMs = inMs, outMs = outMs, volume = volume)
        if (next.audioPath.isNotEmpty()) {
            val duration = SoundPadsPrototypeFiles.audioDurationMs(File(next.audioPath))
            require(inMs < duration && (outMs == null || outMs <= duration))
        }
        val updated = pads.map { if (it.padId == id) next else it }
        write(updated)
        updated
    }

    suspend fun delete(id: String): List<SoundPad> = transaction {
        val pads = read()
        require(pads.any { it.padId == id })
        val updated = pads.filterNot { it.padId == id }
        write(updated) // Publish references before deleting any now-unused audio.
        cleanup(updated)
        updated
    }

    suspend fun import(id: String, uri: Uri): List<SoundPad> = transaction {
        val pads = read()
        val previous = pads.single { it.padId == id }
        val file = File(audioDir, "${UUID.randomUUID()}.audio")
        val tmp = File(audioDir, "${file.name}.tmp")
        var published = false
        try {
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            val duration = SoundPadsPrototypeFiles.audioDurationMs(tmp)
            check(tmp.renameTo(file))
            // New audio starts with the full file. Keep identity, name, volume and appearance.
            val next = previous.copy(audioPath = file.absolutePath, inMs = 0L, outMs = duration)
            val updated = pads.map { if (it.padId == id) next else it }
            write(updated)
            published = true
            cleanup(updated)
            updated
        } finally {
            tmp.delete()
            if (!published) file.delete()
        }
    }

    private fun read(): List<SoundPad> {
        if (!File(root, "bank.json").exists() && !File(root, "bank.json.bak").exists()) return emptyList()
        val json = JSONObject(bank.readFully().toString(Charsets.UTF_8))
        require(json.getInt("schemaVersion") == 1) { "Unsupported Sound Pads bank version" }
        val array = json.getJSONArray("pads")
        val pads = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val filename = item.getString("audioFile")
            require(filename.isEmpty() || audioName.matches(filename))
            SoundPad(
                padId = item.getString("id"), name = item.getString("name"),
                audioPath = if (filename.isEmpty()) "" else File(audioDir, filename).absolutePath,
                volume = item.getDouble("volume").toFloat(), inMs = item.getLong("inMs"),
                outMs = if (item.isNull("outMs")) null else item.getLong("outMs"),
                pitchSemitones = item.getInt("pitchSemitones"),
                colorArgb = if (item.isNull("colorArgb")) null else item.getLong("colorArgb")
            )
        }
        require(pads.map { it.padId }.distinct().size == pads.size)
        return pads
    }

    private fun write(pads: List<SoundPad>) {
        val array = JSONArray()
        pads.forEach { pad ->
            val filename = if (pad.audioPath.isEmpty()) "" else {
                val file = File(pad.audioPath)
                require(file.parentFile?.canonicalFile == audioDir.canonicalFile && audioName.matches(file.name))
                file.name
            }
            array.put(JSONObject().put("id", pad.padId).put("name", pad.name)
                .put("audioFile", filename).put("volume", pad.volume.toDouble())
                .put("inMs", pad.inMs).put("outMs", pad.outMs ?: JSONObject.NULL)
                .put("pitchSemitones", pad.pitchSemitones).put("colorArgb", pad.colorArgb ?: JSONObject.NULL))
        }
        val bytes = JSONObject().put("schemaVersion", 1).put("pads", array).toString().toByteArray(Charsets.UTF_8)
        val output = bank.startWrite()
        try {
            output.write(bytes)
            bank.finishWrite(output)
        } catch (error: Throwable) {
            bank.failWrite(output)
            throw error
        }
    }

    private fun cleanup(pads: List<SoundPad>) {
        // Cleanup failure must never turn a committed mutation into an apparent failed import.
        runCatching {
            val used = pads.map { File(it.audioPath).name }.toSet()
            audioDir.listFiles()?.forEach { file ->
                val unusedAudio = audioName.matches(file.name) && file.name !in used
                val unfinishedCopy = file.name.endsWith(".audio.tmp") &&
                    audioName.matches(file.name.removeSuffix(".tmp"))
                if (file.isFile && (unusedAudio || unfinishedCopy)) {
                    file.delete() // Best effort; retry on the next successful load.
                }
            }
        }
    }
}
