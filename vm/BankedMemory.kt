package com.grokthis.ucisc.vm

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
class BankedMemory(
        val device: Device,
        var connected: List<BankedMemory> = listOf(),
        private var initDevice: Int = 0,
        var deviceStatus: Int = 0,
        private var bankIndex: Int = 0
) {
    private val dataMask: Int = 0xFFFF
    // Mask to determine which memory blocks are currently banked
    var bankMask: Int = 0
        set(newMask) {
            field = newMask.and(0xFFFF)
        }
    // Block address currently selected for init device access
    var mappedBlock: Int = 0
        set(newBlock) {
            field = newBlock.and(0xFF)
        }
    var interruptHandler: Int = 0
    private val data: Array<Int>
    private var romEnd = 0
    init {
        if (device.addressWidth < 8 || device.addressWidth > 16) {
            throw IllegalArgumentException("Address width must be between 8 and 16")
        }
        data = Array(1.shl(device.addressWidth)) { 0 }
    }

    fun setRom(romData: Array<Int>, endAddress: Int) {
        romData.copyInto(destination = data)
        romEnd = endAddress.and(0xFFFF)
    }

    private fun isBanked(address: Int): Boolean {
        val block = blockFor(address)
        return if (block == 0) {
            bankMask.and(1) != 0
        } else {
            1.shl(block).and(bankMask) != 0
        }
    }

    fun writeMem(sourceDevice: Int, address: Int, value: Int) {
        val banked = isBanked(address) && sourceDevice == device.id
        var deviceIndex = address.shr(4)
        when {
            banked && deviceIndex < (connected.size + 1) -> {
                // Writing banked control word
                val otherDevice = if (deviceIndex == 0) this else connected[deviceIndex - 1]
                val isInitDevice = sourceDevice == device.id || sourceDevice == initDevice
                val controlAddress = address.and(0xF)
                if (otherDevice.controlUpdatable(controlAddress, isInitDevice, value)) {
                    otherDevice.setControl(controlAddress, value)
                }
            }
            banked && deviceIndex >= 256 -> {
                // Writing banked memory word
                deviceIndex = address.shr(8)
                if (deviceIndex < connected.size) {
                    val otherDevice = if (deviceIndex == 0) this else connected[deviceIndex - 1]
                    otherDevice.writeMem(sourceDevice, address.and(0xFF), value)
                }
            }
            !banked && sourceDevice == device.id -> {
                // Local device writing to local memory
                val writeAddress = address.rem(data.size)
                if (writeAddress >= romEnd) {
                    data[writeAddress] = value.and(dataMask)
                }
            }
            !banked && sourceDevice == prepInitDevice(sourceDevice) -> {
                // Init device writing to local memory
                val writeAddress = mappedBlock.shl(8).or(address.and(0xFF)).rem(data.size)
                if (writeAddress >= romEnd) {
                    data[writeAddress] = value.and(dataMask)
                }
            }
        }
    }

    fun readMem(sourceDevice: Int, address: Int, forceLocal: Boolean = false): Int {
        val banked = isBanked(address) && !forceLocal && sourceDevice == device.id
        var deviceIndex = address.shr(4)
        return when {
            banked && deviceIndex < (connected.size + 1) -> {
                // Writing banked control word
                val otherDevice = if (deviceIndex == 0) this else connected[deviceIndex - 1]
                val isInitDevice = sourceDevice == device.id || sourceDevice == initDevice
                val controlAddress = address.and(0xF)
                if (otherDevice.controlReadable(controlAddress, isInitDevice)) {
                    otherDevice.getControl(controlAddress)
                } else {
                    0
                }
            }
            banked && deviceIndex >= 256 -> {
                // Writing banked memory word
                deviceIndex = address.shr(8)
                if (deviceIndex < connected.size) {
                    val otherDevice = if (deviceIndex == 0) this else connected[deviceIndex - 1]
                    otherDevice.readMem(sourceDevice, address.and(0xFF))
                } else {
                    0
                }
            }
            !banked && sourceDevice == device.id -> {
                // Local device reading from local memory
                val readAddress = address.rem(data.size)
                data[readAddress]
            }
            !banked && sourceDevice == prepInitDevice(sourceDevice) -> {
                // Init device writing to local memory
                val readAddress = mappedBlock.shl(8).or(address.and(0xFF)).rem(data.size)
                data[address]
            }
            else -> 0
        }
    }

    private fun prepInitDevice(source: Int = 0): Int {
        // A device will automatically become the init device on first write
        if (initDevice == 0) initDevice = source
        return initDevice
    }

    private fun setControl(address: Int, value: Int) {
        when (address) {
            1 -> bankIndex = value.and(0xFF00).shr(8)
            2 -> initDevice = value.and(0xFFFF)
            3 -> mappedBlock = value.and(0xFFFF)
            4 -> deviceStatus = value.and(0xFF)
            5 -> interruptHandler = value.and(0xFFFF)
        }
        if (address in 1..15) device.controlUpdated(address, this, value.and(0xFFFF))
    }

    private fun getControl(controlAddress: Int): Int {
        return when (controlAddress) {
            0 -> device.id
            1 -> bankIndex.shl(8).or(device.type.code)
            2 -> initDevice
            3 -> mappedBlock
            4 -> deviceStatus
            5 -> interruptHandler
            in 6..15 -> device.readValue(controlAddress, this)
            else -> 0
        }
    }

    private fun blockFor(address: Int): Int {
        return address.and(0xF000).shr(12)
    }

    private fun controlReadable(address: Int, isInitDevice: Boolean): Boolean =
            device.controlReadable(address, isInitDevice)
    private fun controlUpdatable(address: Int, isInitDevice: Boolean, value: Int): Boolean =
            device.controlUpdatable(address, isInitDevice, value)
}