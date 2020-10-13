package com.grokthis.ucisc.vm

class SerialConnection(id: Int): Device(id, DeviceType.SERIAL, 0) {
    private var rxOffset = 0
    var rxData = ""
        set(value) {
            synchronized(this) {
                field = field.substring(rxOffset)
                field += value
                rxOffset = 0
            }
        }
    private var rxAvailable = 0
    var txData = mutableListOf<Byte>()
        get() {
            synchronized(this) {
                val ret = field
                field = mutableListOf()
                return ret
            }
        }
    private var txAvailable = 2
    var baud = 115200
    init {
        flags = 0x2000
    }

    private val runner = Thread(Runnable {
        while (enabled) {
            synchronized(this) {
                if (rxAvailable < 2 && (rxOffset + rxAvailable) < rxData.length) {
                    rxAvailable += 1
                }
                if (txAvailable < 2) {
                    txAvailable += 1
                }
            }
            // Simulate baud connection
            val byteRate = baud / 10 // 1 start, 8 bits, 1 stop
            val nanosPerByte = 1000000000 / byteRate
            Thread.sleep(0, nanosPerByte)
        }
    })

    init {
        runner.start()
    }

    fun setData(data: String) {

    }

    override fun getControl(sourceDevice: Int, address: Int, debug: Boolean): Int {
        return when (isControllingDevice(sourceDevice)) {
            true -> {
                when (address) {
                    // The buffer start/stop are
                    6 -> 0
                    7 -> 2.shl(8).or(txAvailable)
                    8 -> 0
                    9 -> 2.shl(8).or(rxAvailable)
                    10 -> {
                        if (debug && rxAvailable > 0) {
                            rxData[rxOffset].toInt().and(0xFF)
                        } else if (rxAvailable > 0) {
                            rxAvailable -= 1
                            rxOffset += 1
                            rxData[rxOffset - 1].toInt().and(0xFF)
                        } else {
                            (Math.random() * 0xFFFF).toInt().and(0xFF)
                        }
                    }
                    else -> super.getControl(sourceDevice, address, debug)
                }
            }
            false -> super.getControl(sourceDevice, address, debug)
        }
    }

    override fun setControl(sourceDevice: Int, address: Int, value: Int) {
         when (isControllingDevice(sourceDevice)) {
            true -> {
                if (address == 8) {
                    if (txAvailable == 0) {
                        // Simulate that the write happens before the transmission
                        txData[txData.size - 1] = value.toByte()
                    } else
                        txData.add(value.toByte())
                } else {
                    super.setControl(sourceDevice, address, value)
                }
            }
            false -> super.setControl(sourceDevice, address, value)
        }
    }
}