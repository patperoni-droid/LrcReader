package com.patrick.lrcreader.core

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

object MidiOutput {

    private const val TAG = "MidiOutput"

    private var midiManager: MidiManager? = null
    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null

    // Pour éviter d’ouvrir 15 fois en même temps
    @Volatile private var opening = false

    fun init(context: Context) {
        Log.d(TAG, "INIT CALLED ✅")

        if (inputPort != null || opening) {
            Log.d(TAG, "Déjà prêt (ou ouverture en cours)")
            return
        }

        if (midiManager == null) {
            midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
        }

        val devices = midiManager?.devices ?: emptyArray()
        Log.d(TAG, "Devices MIDI détectés = ${devices.size}")

        val widi = devices.firstOrNull { info ->
            val name = (info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "").lowercase()
            val manu = (info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "").lowercase()
            val prod = (info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "").lowercase()

            name.contains("widi") || manu.contains("widi") || prod.contains("widi") ||
                    name.contains("cme")  || manu.contains("cme")  || prod.contains("cme")
        }

        if (widi == null) {
            Log.w(TAG, "❌ WIDI/CME non trouvé. Assure-toi qu’il est connecté en Bluetooth MIDI dans Android.")
            return
        }

        val name = widi.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
        val manu = widi.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
        Log.d(TAG, "✅ WIDI choisi: name=$name manufacturer=$manu")

        opening = true
        midiManager?.openDevice(widi, { device ->
            opening = false

            if (device == null) {
                Log.e(TAG, "❌ Impossible d’ouvrir le WIDI")
                return@openDevice
            }

            midiDevice = device
            inputPort = device.openInputPort(0)

            if (inputPort != null) {
                Log.d(TAG, "✅ Port MIDI WIDI ouvert (inputPort=0)")
            } else {
                Log.e(TAG, "❌ Échec ouverture port WIDI (inputPort=0)")
            }
        }, Handler(Looper.getMainLooper()))

    }
    fun sendBleBlinkTest(channel: Int = 1) {
        val port = inputPort
        if (port == null) {
            Log.w(TAG, "TEST BLE : inputPort = null")
            return
        }

        val ch0 = channel - 1

        val noteOn = byteArrayOf(
            (0x90 or ch0).toByte(),
            60.toByte(),
            100.toByte()
        )

        try {
            port.send(noteOn, 0, noteOn.size)
            Log.d(TAG, "TEST BLE : NOTE ON envoyée")
        } catch (e: Exception) {
            Log.e(TAG, "TEST BLE : erreur", e)
        }
    }
    fun sendProgramChange(channel: Int, program: Int) {
        val port = inputPort
        if (port == null) {
            Log.w(TAG, "⚠️ sendProgramChange ignoré : port MIDI non prêt")
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
            Log.d(TAG, "🎹 MIDI SENT → PC ch=${safeChannel + 1} prog=${safeProgram + 1}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur envoi MIDI Program Change", e)
        }
    }

    fun release() {
        try { inputPort?.close() } catch (_: Exception) {}
        try { midiDevice?.close() } catch (_: Exception) {}
        inputPort = null
        midiDevice = null
        opening = false
        Log.d(TAG, "Release OK")
    }
}