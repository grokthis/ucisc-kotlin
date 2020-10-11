package com.grokthis.ucisc.vm

class TerminalWriter(id: Int): Device(id, DeviceType.TERMINAL, 8) {

    var bufferStart = 0
    var bufferEnd = 0

    val runner = Thread(Runnable {
        while (enabled) {
            while (bufferEnd != bufferStart) {
                val word = readMem(id, bufferStart)
                val bytes = listOf(word.and(0xFF00).shr(8).toByte(), word.and(0xFF).toByte())
                print(bytes.toByteArray().decodeToString())

                bufferStart = (bufferStart + 1).and(0xFF)
            }
            Thread.sleep(0, 1)
        }
    })

    override fun getControl(sourceDevice: Int, address: Int): Int {
        return when (isControllingDevice(sourceDevice)) {
            true -> {
                when (address) {
                    // The buffer start/stop are
                    6 -> bufferStart
                    7 -> bufferEnd
                    else -> super.getControl(sourceDevice, address)
                }
            }
            false -> super.getControl(sourceDevice, address)
        }
    }

    override fun setControl(sourceDevice: Int, address: Int, value: Int) {
         when (isControllingDevice(sourceDevice)) {
            true -> {
                when (address) {
                    6 -> bufferStart = value.and(0xFF)
                    7 -> bufferEnd = value.and(0xFF)
                    else -> super.setControl(sourceDevice, address, value)
                }
            }
            false -> super.setControl(sourceDevice, address, value)
        }
    }
}