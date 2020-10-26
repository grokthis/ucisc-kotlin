package com.grokthis.ucisc.vm

interface DeviceListener {
    fun deviceChanged(device: Device)
}