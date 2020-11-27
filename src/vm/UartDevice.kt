package com.grokthis.ucisc.vm

class UartDevice(
    private val id: Int,
    private val channel: UartChannelEmulator
): MemoryMapped {
    override var step: Int = 0
    override var writeAddress: Int = 0
    override var writeData: Int = 0
    override var writeEnabled: Boolean = false
    override var readAddress: Int = 0
    private var _dataOut = 0
    override val dataOut: Int get() = _dataOut
    private var baudDivider = 139

    override fun captureStageInputs() {
        channel.doTick(baudDivider)
    }

    override fun execStage() {
    }

    override fun captureStageOutputs() {
        _dataOut = if (readAddress < 0x1000) {
            when (readAddress.and(0xF)) {
                0 -> id
                1 -> flags().shl(8).or(4).and(0xFFFF)
                2 -> baudDivider
                4 -> channel.readValue
                else -> 0
            }
        } else {
            0
        }
        if (writeEnabled && step == 0 && writeAddress < 0x1000) {
            when (writeAddress.and(0xF)) {
                2 -> baudDivider = writeData
                3 -> channel.write(writeData)
                4 -> channel.clearRead()
            }
        }
    }

    private fun flags(): Int {
        var flags = 0x10
        if (channel.writeReady) {
            flags = flags.or(0x1)
        }
        if (channel.readReady) {
            flags = flags.or(0x2)
        }
        if (!channel.writeReady || channel.readReady) {
            flags = flags.or(0x4)
        }
        return flags
    }
}