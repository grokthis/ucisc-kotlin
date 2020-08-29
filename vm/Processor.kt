package com.grokthis.ucisc.vm

class Processor(id: Int, addressWidth: Int): Device(id, DeviceType.PROCESSOR, addressWidth) {
    var pc: Int = 0
        set(value) {
            field = value
            next = value + 1
        }
    var next: Int = 0
    var flags: Int = 0
    var overflow: Int = 0
    private val registers = Array(4) { 0 }

    fun setRegister(number: Int, value: Int) {
        registers[number] = value
    }

    fun getRegister(number: Int): Int {
        return registers[number]
    }

   override fun readValue(address: Int, memory: BankedMemory): Int {
        return when (address) {
            6 -> (1.shl(addressWidth) - 1).and(0xFF00)
            7 -> 0 // interrupts not yet implemented
            8 -> pc
            9 -> getRegister(1)
            10 -> getRegister(2)
            11 -> getRegister(3)
            12 -> flags
            13 -> memory.bankMask
            else -> 0
        }
    }

    override fun controlReadable(address: Int, isInitDevice: Boolean): Boolean {
        return (isInitDevice && address in 6..13) ||
                super.controlReadable(address, isInitDevice)
    }

    override fun controlUpdatable(address: Int, isInitDevice: Boolean, value: Int): Boolean {
        return (isInitDevice && address in 8..13) ||
                super.controlUpdatable(address, isInitDevice, value)
    }

    /**
     * 0x6 - Maximum local memory block (MSB)
     * 0x7 - Control block address of next non-suppressed interrupted device (0 if none)
     * 0x8 - 0.reg (PC)
     * 0x9 - 1.reg
     * 0xA - 2.reg
     * 0xB - 3.reg
     * 0xC - flags register
     * 0xD - banking control
     */
    override fun controlUpdated(address: Int, memory: BankedMemory, value: Int) {
        when (address) {
            8 -> pc = value
            9 -> setRegister(1, value)
            10 -> setRegister(2, value)
            11 -> setRegister(3, value)
            12 -> flags = value
            13 -> if (memory.bankMask != value) memory.bankMask = value
        }
    }
}