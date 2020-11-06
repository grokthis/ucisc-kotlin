package com.grokthis.ucisc.vm

class LedIOBus(id: Int): Device(id, DeviceType.PINIO, 0) {
    private var ledState: Int = 0

    init {
        flags = 0x2000
    }

    fun ledLit(index: Int): Boolean {
        return 1.shl(index).and(ledState) > 0
    }

    override fun getControl(sourceDevice: Int, address: Int, debug: Boolean): Int {
        return when (isControllingDevice(sourceDevice)) {
            true -> {
                when (address) {
                    // The buffer start/stop are
                    6 -> ledState.and(0xFFFF)
                    7 -> ledState.and(0xFFFF0000.toInt()).shr(16)
                    else -> super.getControl(sourceDevice, address, debug)
                }
            }
            false -> super.getControl(sourceDevice, address, debug)
        }
    }

    override fun setControl(sourceDevice: Int, address: Int, value: Int) {
         when (isControllingDevice(sourceDevice)) {
            true -> {
                when (address) {
                    6 -> {
                        ledState = ledState.and(0xFFFF0000.toInt()).or(value.and(0xFFFF))
                        notifyListeners()
                    }
                    7 -> {
                        ledState = value.and(0xFFFF).shl(16).or(ledState.and(0xFFFF))
                        notifyListeners()
                    }
                    else -> super.setControl(sourceDevice, address, value)
                }
            }
            false -> super.setControl(sourceDevice, address, value)
        }
    }
}