package com.grokthis.ucisc.vm

class Devices: MemoryMapped {
    private val deviceMap: MutableMap<Int, MemoryMapped> = mutableMapOf()
    val devices get() = deviceMap.values

    fun addDevice(index: Int, device: MemoryMapped) {
        deviceMap[index] = device
    }

    override fun captureStageInputs() {
        deviceMap.values.forEach { it.captureStageInputs() }
    }

    override fun execStage() {
        deviceMap.values.forEach { it.execStage() }
    }

    override fun captureStageOutputs() {
        deviceMap.values.forEach { it.captureStageOutputs() }
    }

    override var step: Int = 0
        set(value) {
            field = value
            deviceMap.values.forEach { it.step = value }
        }

    override var writeAddress: Int = 0
        set(value) {
            field = value
            deviceMap.values.forEach { it.writeAddress = value }
            writeEnabled = writeEnabled // update the write enable flag for the selected device
        }

    override var writeData: Int = 0
        set(value) {
            field = value
            deviceMap.values.forEach { it.writeData = value }
        }

    override var readAddress: Int = 0
        set(value) {
            field = value
            deviceMap.values.forEach { it.readAddress = value }
        }

    override var writeEnabled: Boolean = false
        set(value) {
            field = value
            deviceMap.values.forEach { it.writeEnabled = false }
            if (writeEnabled) {
                val deviceIndex = if (writeAddress < 0x1000) {
                    writeAddress.and(0xFF0).shr(4)
                } else {
                    writeAddress.and(0xFF00).shr(8)
                }
                deviceMap[deviceIndex]?.writeEnabled = true
            }
        }

    override val dataOut: Int
        get() {
            val deviceIndex = if (readAddress < 0x1000) {
                readAddress.and(0xFF0).shr(4)
            } else {
                readAddress.and(0xFF00).shr(8)
            }
            return deviceMap[deviceIndex]?.dataOut ?: 0
        }
}