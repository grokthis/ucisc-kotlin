package com.grokthis.ucisc.vm

interface MemoryMapped : ClockSynchronized {
    override var step: Int
    var writeAddress: Int
    var writeData: Int
    var writeEnabled: Boolean
    var readAddress: Int
    val dataOut: Int

    override fun captureStageInputs()

    override fun execStage()

    override fun captureStageOutputs()
}