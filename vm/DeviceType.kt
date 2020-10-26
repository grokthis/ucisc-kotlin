package com.grokthis.ucisc.vm

enum class DeviceType(val code: Int) {
  INVALID(0),
  PROCESSOR(1),
  BLOCK_MEMORY(2),
  BLOCK_IO(3),
  SERIAL(4),
  HID(5),
  TERMINAL(6),
  PINIO(7)
}