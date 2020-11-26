package com.grokthis.ucisc.vm

import java.lang.IllegalArgumentException

class MemoryBlock(
    addressWidth: Int
): ClockSynchronized {
    override var step: Int = 0
    var writeAddress: Int = 0
    var writeData: Int = 0
    var writeEnabled: Boolean = false
    var readAddress: Int = 0
    val dataOut: Int
       get() = _dataOut

    private var _dataOut: Int = 0

    val data: IntArray
    init {
        if (addressWidth < 8 || addressWidth > 16) {
            throw IllegalArgumentException(
                "Address width must be between 8 and 16"
            )
        }
        data = IntArray(1.shl(addressWidth))
    }

    fun setData(address: Int, word: Int) {
        data[address] = word
    }

    fun peek(address: Int): Int {
        return data[address % data.size]
    }

    override fun captureStageInputs() {
    }

    override fun execStage() {
    }

    override fun captureStageOutputs() {
        // Read must happen before write to match the
        // hardware in read before write mode.
        _dataOut = data[readAddress % data.size]
        if (writeEnabled && step == 0) {
            data[writeAddress] = writeData
        }
    }
}