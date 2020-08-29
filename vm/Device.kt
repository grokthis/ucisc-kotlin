package com.grokthis.ucisc.vm


abstract class Device(val id: Int, val type: DeviceType, val addressWidth: Int) {
  open fun controlReadable(address: Int, isInitDevice: Boolean): Boolean {
    return when {
      address in 0..2 -> true
      address in 3..5 && isInitDevice -> true
      else -> false
    }
  }

  open fun controlUpdatable(address: Int, isInitDevice: Boolean, value: Int): Boolean {
    return when {
      address in 1..3 && isInitDevice -> true
      address == 4 && !isInitDevice -> true
      address == 5 && isInitDevice -> true
      else -> false
    }
  }

  open fun controlUpdated(address: Int, memory: BankedMemory, value: Int) { }
  open fun readValue(address: Int, memory: BankedMemory): Int { return 0 }
}
