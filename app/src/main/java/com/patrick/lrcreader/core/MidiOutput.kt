package com.patrick.lrcreader.core

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.util.Log

/**
 * Sortie MIDI réelle via Android MIDI API (Bluetooth MIDI inclus).
 * Architecture :
 * - init(context) : à appeler UNE fois (ex: au démarrage de l’app)
 * - sendProgramChange(...) : utilisé par MidiCueDispatcher
 */
object MidiOutput {

    private const val TAG = "MidiOutput"

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null

    /**
     * À appeler une seule fois (ex: dans MainActivity ou PlayerScreen root).
     */
    fun init(context: Context) {
        if (midiManager != null) return

        midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager

        val devices = midiManager?.devices ?: emptyArray()
        if (devices.isEmpty()) {
            Log.w(TAG, "Aucun périphérique MIDI détecté")
            return
        }

        // On prend le PREMIER périphérique MIDI disponible (WIDI Master en général)
        val deviceInfo = devices.first()
        Log.d(TAG, "Ouverture périphérique MIDI : ${deviceInfo.properties}")

        midiManager?.openDevice(deviceInfo, { device ->
            if (device == null) {
                Log.e(TAG, "Impossible d’ouvrir le périphérique MIDI")
                return@openDevice
            }

            midiDevice = device

            // On ouvre le premier port d’entrée (OUT côté synthé)
            inputPort = device.openInputPort(0)

            if (inputPort != null) {
                Log.d(TAG, "Port MIDI ouvert avec succès")
            } else {
                Log.e(TAG, "Échec ouverture port MIDI")
            }
        }, null)
    }

    /**
     * Envoi réel d’un Program Change MIDI.
     *
     * @param channel 1–16
     * @param program 1–128
     */
    fun sendProgramChange(channel: Int, program: Int) {
        val port = inputPort
        if (port == null) {
            Log.w(TAG, "Port MIDI non prêt (Program Change ignoré)")
            return
        }

        val safeChannel = channel.coerceIn(1, 16) - 1
        val safeProgram = program.coerceIn(1, 128) - 1

        val status = 0xC0 or safeChannel
        val msg = byteArrayOf(
            status.toByte(),
            safeProgram.toByte()
        )

        try {
            port.send(msg, 0, msg.size)
            Log.d(
                TAG,
                "MIDI SENT → PC ch=${safeChannel + 1} prog=${safeProgram + 1}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoi MIDI", e)
        }
    }

    fun release() {
        try {
            inputPort?.close()
            midiDevice?.close()
        } catch (_: Exception) {
        }
        inputPort = null
        midiDevice = null
        midiManager = null
    }
}