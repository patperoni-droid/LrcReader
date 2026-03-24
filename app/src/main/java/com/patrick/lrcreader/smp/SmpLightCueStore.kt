package com.patrick.lrcreader.smp

import android.util.Log
import com.patrick.lrcreader.core.light.LightCue
import java.io.File

object SmpLightCueStore {

    const val LIGHT_CUES_FILE_NAME = "dmx_cues.json"
    const val LEGACY_LIGHT_CUES_FILE_NAME = "dmx.json"
    private const val TAG = "SmpLightCueStore"
    private const val TRACE_TAG = "LIGHT_CUE_TRACE"

    fun read(songDir: File): List<LightCue> {
        val cueFile = resolveReadFile(songDir)
        if (cueFile == null || !cueFile.isFile) {
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songDir.absolutePath} path=null exists=false count=0 source=MISSING"
            )
            return emptyList()
        }

        return runCatching {
            val rawJson = cueFile.readText(Charsets.UTF_8)
            val cues = LightCue.listFromJsonOrEmpty(rawJson)
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songDir.absolutePath} path=${cueFile.absolutePath} exists=true count=${cues.size} source=${if (cueFile.name == LEGACY_LIGHT_CUES_FILE_NAME) "LEGACY" else "CANONICAL"}"
            )
            cues
        }.getOrElse { error ->
            Log.e(TAG, "Lecture light cues impossible: ${cueFile.absolutePath}", error)
            emptyList()
        }
    }

    fun write(songDir: File, cues: List<LightCue>): Boolean {
        val cueFile = File(songDir, LIGHT_CUES_FILE_NAME)
        val tmpFile = File(songDir, "$LIGHT_CUES_FILE_NAME.tmp")
        val normalized = cues
            .map { cue ->
                cue.copy(
                    timeMs = cue.timeMs.coerceAtLeast(0L),
                    intensity = cue.intensity.coerceIn(0f, 1f),
                    fadeMs = cue.fadeMs.coerceAtLeast(0L)
                )
            }
            .sortedBy { cue -> cue.timeMs }
        val rawJson = LightCue.toJsonString(normalized)

        return runCatching {
            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (cueFile.exists() && !cueFile.delete()) {
                Log.w(TAG, "Suppression ${cueFile.name} impossible: ${cueFile.absolutePath}")
            }
            if (!tmpFile.renameTo(cueFile)) {
                cueFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            Log.d(
                TRACE_TAG,
                "SAVE songDir=${songDir.absolutePath} path=${cueFile.absolutePath} count=${normalized.size} source=CANONICAL"
            )
            SmpLightCueBridge.invalidateSongDir(songDir)
            true
        }.getOrElse { error ->
            Log.e(TAG, "Ecriture light cues impossible: ${cueFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    fun write(songUnit: SongUnit, cues: List<LightCue>): Boolean {
        val songDir = songUnit.storageFolder?.takeIf { it.isNotBlank() }?.let(::File)
            ?: songUnit.dmxPath?.takeIf { it.isNotBlank() }?.let(::File)?.parentFile
            ?: return false
        val saved = write(songDir, cues)
        if (!saved) {
            return false
        }

        return SmpMetaStore.write(
            songUnit.copy(dmxPath = File(songDir, LIGHT_CUES_FILE_NAME).absolutePath)
        )
    }

    private fun resolveReadFile(songDir: File): File? {
        val canonical = File(songDir, LIGHT_CUES_FILE_NAME)
        if (canonical.isFile) {
            return canonical
        }

        val legacy = File(songDir, LEGACY_LIGHT_CUES_FILE_NAME)
        return legacy.takeIf { it.isFile }
    }
}
