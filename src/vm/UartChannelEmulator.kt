package com.grokthis.ucisc.vm

import java.io.InputStream
import java.io.OutputStream

/**
 * Implements a simple UART emulator. This class has no buffer
 * so you must wait for the output to indicate it is read before
 * writing the next byte or one of them will get lost.
 */
class UartChannelEmulator(
    private val rx: InputStream,
    private val tx: OutputStream
) {
    private var byte: Int? = null
    private var toWrite: Int? = null
    private var ticks = 0

    fun doTick(baudDivider: Int) {
        ticks += 1
        if (ticks > baudDivider * 10) {
            ticks = 0
            val doWrite = toWrite
            toWrite = null
            if (doWrite != null) {
                tx.write(doWrite)
                tx.flush()
            }
            if (rx.available() > 0) {
                byte = rx.read()
            }
        }
    }

    val readReady
        get() = byte != null

    val readValue: Int
        get() = byte ?: 134 // simulate garbage if you read when not ready

    fun clearRead() {
        byte = null
    }

    val writeReady
        get() = toWrite == null

    fun write(b: Int) {
        toWrite = b
    }
}