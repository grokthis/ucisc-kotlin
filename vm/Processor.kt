package com.grokthis.ucisc.vm

import kotlin.system.measureTimeMillis

class Processor(id: Int, addressWidth: Int) : Device(id, DeviceType.PROCESSOR, addressWidth) {
    var pc: Int = 0
        set(value) {
            field = value
            next = value + 2
        }
    var next: Int = 2
    var overflow: Int = 0
    private val registers = Array(8) { 0 }
    var debug = false

    fun setRegister(number: Int, value: Int) {
        when (number) {
            0 -> next = value
            in 1..3 -> registers[number] = value.and(0xFFFF)
            4 -> flags = value
            in 5..7 -> registers[number] = value.and(0xFFFF)
            8 -> flags = value
            12 -> interruptHandler = value
        }
    }

    fun getRegister(number: Int): Int {
        return when (number) {
            0 -> pc
            in 1..3 -> registers[number]
            4 -> flags
            in 5..7 -> registers[number]
            8 -> flags
            12 -> interruptHandler
            else -> 0
        }
    }

    fun run(): Int {
        var instructionCount = 0
        var exitCode = 0 // continue
        val time = measureTimeMillis {
            while (exitCode == 0 && instructionCount < 100000000) {
                val msWord = readMem(id, pc)
                val lsWord = readMem(id, pc + 1)

                val instruction = Instruction(msWord.shl(16).or(lsWord), this)
                if (debug) printInstruction(instruction)
                exitCode = instruction.execute()
                if (exitCode != 0 && debug) {
                    exitCode = 0
                    pc += 2
                    next = pc + 2
                }
                instructionCount += 1
            }
        }
        val returnVal = readMem(id, registers[1])
        println("Process terminated after $instructionCount instructions in $time ms with return code ".plus(returnVal))
        return returnVal
    }

    private fun stackString(): String {
        var address = registers[1].and(0xFFFF)
        val endAddress = if (address > 0) (address + 10) else address
        var result = String.format("%04X:", address)
        while (address > 0 && address < endAddress && address <= 0xFFFF) {
            result += String.format(" %04X", readMem(id, address))
            address += 1
        }
        return result
    }

    private fun printInstruction(instruction: Instruction) {
        val srcVal = instruction.sourceValue(true)
        val op = when (instruction.aluCode) {
            0 -> "copy"
            1 -> "and "
            2 -> " or "
            3 -> "xor "
            4 -> "inv "
            5 -> "shl "
            6 -> "shr "
            7 -> "swap"
            8 -> "msb "
            9 -> "lsb "
            10 -> "add "
            11 -> "sub "
            12 -> "mult"
            13 -> "div "
            14 -> "rflw"
            15 -> "wflw"
            else -> "????"
        }
        val dstVal = instruction.destinationValue(true)
        val store = instruction.shouldStore()
        val result = if (store) instruction.computeResult(true) else dstVal

        println(String.format("Stack: %s", stackString()))
        println(String.format("%04X: %s - %05d %s %05d = %05d", pc, instruction.toString(), srcVal, op, dstVal, result))
    }

    override fun getControl(sourceDevice: Int, address: Int, debug: Boolean): Int {
        if (isControllingDevice(sourceDevice)) {
            return when (address) {
                6 -> (1.shl(addressWidth) - 1).and(0xFF00)
                7 -> 0 // interrupts not yet implemented
                8 -> pc
                9 -> getRegister(1)
                10 -> getRegister(2)
                11 -> getRegister(3)
                12 -> flags
                13 -> getRegister(4)
                14 -> getRegister(5)
                15 -> getRegister(6)
                else -> super.getControl(sourceDevice, address, debug)
            }
        } else {
            return super.getControl(sourceDevice, address, debug)
        }
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
    override fun setControl(sourceDevice: Int, address: Int, value: Int) {
        when (address) {
            8 -> pc = value
            9 -> setRegister(1, value)
            10 -> setRegister(2, value)
            11 -> setRegister(3, value)
            12 -> flags = value
            13 -> setRegister(4, value)
            14 -> setRegister(5, value)
            15 -> setRegister(6, value)
            else -> super.setControl(sourceDevice, address, value)
        }
    }
}