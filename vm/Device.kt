package com.grokthis.ucisc.vm

import java.io.File

/**
 * This is a generic device base class providing memory and control access
 *
 * From the docs, the control word layout is as follows:
 * - 0x0 - Device ID - read only. Unique system wide.
 * - 0x1 - Local bank block (MSB) | Device type (LSB) - read only
 * - 0x2 - Init device ID - read only if set, writable if 0 or by init device
 * - 0x3 - Accessed device block address (LSB) - writable by init device
 * - 0x4 - <Reserved> (MSB) | Device status (LSB)
 * - 0x5 - Local register with interrupt handler address (read/write)
 * - 0x6 to 0xF - Device type specific (see below)
 */
open class Device(
    val id: Int,
    val type: DeviceType,
    val addressWidth: Int,
    val connected: MutableMap<Int, Device> = mutableMapOf(),
    private var initDevice: Int = 0,
    private var deviceStatus: Int = 0,
    private var bankIndex: Int = 0
) {
    private val dataMask: Int = 0xFFFF

    // Block address currently selected for init device access
    var mappedBlock: Int = 0
        set(newBlock) {
            field = newBlock.and(0xFF)
        }
    var interruptHandler: Int = 0
    private val data: IntArray
    var romEnd = 0
    var flags = 0x0100
    var enabled = true

    init {
        data = if (addressWidth == 0) {
            IntArray(0)
        } else if (addressWidth < 8 || addressWidth > 16) {
            throw IllegalArgumentException("Address width must be 0 or between 8 and 16")
        } else {
            IntArray(1.shl(addressWidth)) { 0 }
        }
    }

    fun loadFromFile(fileName: String) {
        val file = File(fileName)
        val inputStream = file.inputStream()
        val bytes = ByteArray(65536L.coerceAtMost(file.length()).toInt())
        inputStream.read(bytes)
        inputStream.close()

        for (i in 0 until (bytes.size).div(2)) {
            val index = i * 2
            data[i] = bytes[index].toInt().shl(8)
                .or(bytes[index + 1].toInt().and(0xFF)).and(0xFFFF)
        }
    }

    fun load(instructions: List<Int>) {
        instructions.toIntArray().copyInto(data)
    }

    fun isHalted(): Boolean = flags.and(0xA000) > 0

    fun writeMem(sourceDevice: Int, address: Int, value: Int): Boolean {
        if (sourceDevice != id && (sourceDevice != initDevice || !isHalted())) {
            // Only the current processor can write to the device unless it is halted
            // and the init device is set to the source device. If the write fails it
            // is non-blocking.
            return false
        }
        when (sourceDevice) {
            id -> {
                // Local device writing to local memory
                val writeAddress = address.rem(data.size)
                if (writeAddress >= romEnd) {
                    data[writeAddress] = value.and(dataMask)
                }
            }
            else -> {
                // Init device writing to local memory
                val writeAddress = mappedBlock.shl(8).or(address.and(0xFF)).rem(data.size)
                if (writeAddress >= romEnd) {
                    data[writeAddress] = value.and(dataMask)
                }
            }
        }
        return true
    }

    fun readMem(sourceDevice: Int, address: Int): Int {
        if (sourceDevice != id && (sourceDevice != initDevice || !isHalted())) {
            // Only the current processor can write to the device unless it is halted
            // and the init device is set to the source device. If the write fails it
            // is non-blocking.
            return 0
        }
        return when (sourceDevice) {
            id -> {
                // Local device reading from local memory
                val readAddress = address.rem(data.size)
                data[readAddress]
            }
            else -> {
                // Init device writing to local memory
                val readAddress = mappedBlock.shl(8).or(address.and(0xFF)).rem(data.size)
                data[readAddress]
            }
        }
    }

    fun writeBanked(address: Int, value: Int) {
        if (address < 4096) {
            // writing control words
            val index = address.shr(4)
            if (connected[index] != null) {
                connected[index]?.setControl(id, address.and(0xF), value)
            }
        } else {
            // writing banked blocks
            val index = address.shr(8)
            connected[index]?.setControl(id, address.and(0xF), value)
        }
    }

    fun readBanked(address: Int, debug: Boolean): Int {
        // If less than 4096, writing control words, otherwise banked block
        return when (val index = address.shr(if (address < 4096) 4 else 8)) {
            0 -> {
                getControl(id, address.and(0xF), debug)
            }
            else -> connected[index]?.getControl(id, address.and(0xF), debug) ?: 0
        }
    }

    protected fun isControllingDevice(sourceDevice: Int): Boolean {
        return sourceDevice == id || (sourceDevice == initDevice && isHalted())
    }

    protected open fun setControl(sourceDevice: Int, address: Int, value: Int) {
        if ((address == 2 && initDevice == 0) || isControllingDevice(sourceDevice)) {
            when (address) {
                1 -> bankIndex = value.and(0xFF00).shr(8)
                2 -> initDevice = value.and(0xFFFF)
                3 -> mappedBlock = value.and(0xFFFF)
                4 -> deviceStatus = value.and(0xFF)
                5 -> interruptHandler = value.and(0xFFFF)
            }
        } else if (isHalted() && initDevice == 0 && address == 2) {
            initDevice = value.and(0xFFFF)
        }
    }

    protected open fun getControl(sourceDevice: Int, controlAddress: Int, debug: Boolean): Int {
        return if (controlAddress in 0..2 || isControllingDevice(sourceDevice)) {
            when (controlAddress) {
                0 -> id
                1 -> bankIndex.shl(8).or(type.code)
                2 -> initDevice
                3 -> mappedBlock
                4 -> deviceStatus
                5 -> interruptHandler
                else -> 0
            }
        } else {
            0
        }
    }
}