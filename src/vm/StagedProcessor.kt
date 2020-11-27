package com.grokthis.ucisc.vm

import kotlin.system.exitProcess

/**
 * This processor is intended to emulate the reference processor hardware
 * defined at https://github.com/grokthis/ucisc/tree/master/hardware/reference
 *
 * It does not go so far as to emulate the precise timings, but emulates the
 * staged nature of the processor to ensure any quirks of the hardware are emulated.
 * Any functional difference between this emulation and the reference hardware
 * is a bug unless otherwise documented. Speed differences are not a bug.
 */
class StagedProcessor(
    private val memory: MemoryBlock,
    private val devices: Devices
): ClockSynchronized {
    override var step: Int = 0
    private val clockedComponents = mutableListOf<ClockSynchronized>()
    init {
        clockedComponents.add(memory)
        clockedComponents.add(this)
        clockedComponents.addAll(devices.devices)
    }
    private var pc: Int = 0xFFFE
    private var instructionWord: Int = 0
    private var immediateWord: Int = 0
    private var instruction: Instruction = Instruction(0, 0)
    private var srcAddress: Int = 0
    private var srcValue: Int = 0
    private var dstAddress: Int = 0
    private var dstValue: Int = 0
    private var result: Alu.AluResult = Alu.AluResult(0, 0)

    private var r1: Int = 0
    private var r2: Int = 0
    private var r3: Int = 0
    private var r4: Int = 0
    private var r5: Int = 0
    private var r6: Int = 0
    private var flags: Int = 0x0100
    private var interrupt: Int = 0
    private var banking: Int = 0xE0

    var debug = false

    fun synchronizeWith(cs: ClockSynchronized) {
        clockedComponents.add(cs)
    }

    fun peek(src: Int, imm: Int): Int {
        return when (src) {
            0 -> (pc + imm).and(0xFFFF)
            1 -> memory.peek((r1 + imm).and(0xFFFF))
            2 -> memory.peek((r2 + imm).and(0xFFFF))
            3 -> memory.peek((r3 + imm).and(0xFFFF))
            4 -> imm.and(0xFFFF)
            5 -> (r1 + imm).and(0xFFFF)
            6 -> (r2 + imm).and(0xFFFF)
            7 -> (r3 + imm).and(0xFFFF)
            8 -> (flags.and(imm)).and(0xFFFF)
            9 -> memory.peek((r4 + imm).and(0xFFFF))
            10 -> memory.peek((r5 + imm).and(0xFFFF))
            11 -> memory.peek((r6 + imm).and(0xFFFF))
            12 -> (interrupt + imm).and(0xFFFF)
            13 -> (r4 + imm).and(0xFFFF)
            14 -> (r5 + imm).and(0xFFFF)
            15 -> (r6 + imm).and(0xFFFF)
            else -> 0
        }
    }

    private fun srcAddress(src: Int, imm: Int): Int {
        return when (src) {
            0 -> (pc + imm).and(0xFFFF)
            1 -> (r1 + imm).and(0xFFFF)
            2 -> (r2 + imm).and(0xFFFF)
            3 -> (r3 + imm).and(0xFFFF)
            4 -> imm.and(0xFFFF)
            5 -> (r1 + imm).and(0xFFFF)
            6 -> (r2 + imm).and(0xFFFF)
            7 -> (r3 + imm).and(0xFFFF)
            8 -> (flags.and(imm)).and(0xFFFF)
            9 -> (r4 + imm).and(0xFFFF)
            10 -> (r5 + imm).and(0xFFFF)
            11 -> (r6 + imm).and(0xFFFF)
            12 -> (interrupt + imm).and(0xFFFF)
            13 -> (r4 + imm).and(0xFFFF)
            14 -> (r5 + imm).and(0xFFFF)
            15 -> (r6 + imm).and(0xFFFF)
            else -> 0
        }
    }

    private fun dstAddress(dst: Int, offset: Int): Int {
        return when (dst) {
            0 -> (pc + offset).and(0xFFFF)
            1 -> (r1 + offset).and(0xFFFF)
            2 -> (r2 + offset).and(0xFFFF)
            3 -> (r3 + offset).and(0xFFFF)
            4 -> offset.and(0xFFFF)
            5 -> (r1 + offset).and(0xFFFF)
            6 -> (r2 + offset).and(0xFFFF)
            7 -> (r3 + offset).and(0xFFFF)
            8 -> (flags + offset).and(0xFFFF)
            9 -> (r4 + offset).and(0xFFFF)
            10 -> (r5 + offset).and(0xFFFF)
            11 -> (r6 + offset).and(0xFFFF)
            12 -> (interrupt + offset).and(0xFFFF)
            13 -> (r4 + offset).and(0xFFFF)
            14 -> (r5 + offset).and(0xFFFF)
            15 -> (r6 + offset).and(0xFFFF)
            else -> 0
        }
    }

    fun isBanked(register: Int): Boolean {
        return when (register) {
            1 -> banking.and(0x02) > 0
            2 -> banking.and(0x04) > 0
            3 -> banking.and(0x08) > 0
            9 -> banking.and(0x20) > 0
            10 -> banking.and(0x40) > 0
            11 -> banking.and(0x80) > 0
            else -> false
        }
    }

    override fun captureStageInputs() {
        when (step) {
            0 -> {
                dstValue = if (instruction.dstMem) {
                    if (isBanked(instruction.destination)) {
                        devices.dataOut
                    } else {
                        memory.dataOut
                    }
                } else {
                    dstAddress
                }
            }
            1 -> {
                instructionWord = memory.dataOut
            }
            2 -> {
                immediateWord = memory.dataOut
                instruction = Instruction(instructionWord, immediateWord)
                srcAddress = srcAddress(instruction.source, instruction.immediate)
            }
            3 -> {
                srcValue = if (instruction.srcMem) {
                    if (isBanked(instruction.source)) {
                        devices.dataOut
                    } else {
                        memory.dataOut
                    }
                } else {
                    srcAddress
                }
                dstAddress = dstAddress(instruction.destination, instruction.offset)
            }
        }
    }

    override fun execStage() {
        when (step) {
            0 -> {
                result = Alu().compute(instruction.aluCode, srcValue, dstValue, flags)
                var increment = 0
                if (instruction.shouldStore(flags)) {
                    increment = when {
                        instruction.push -> -1
                        instruction.pop -> instruction.immediate + 1
                        else -> 0
                    }
                    when (if (instruction.push) instruction.destination else instruction.source) {
                        1 -> r1 = (r1 + increment).and(0xFFFF)
                        2 -> r2 = (r2 + increment).and(0xFFFF)
                        3 -> r3 = (r3 + increment).and(0xFFFF)
                        9 -> r4 = (r4 + increment).and(0xFFFF)
                        10 -> r5 = (r5 + increment).and(0xFFFF)
                        11 -> r6 = (r6 + increment).and(0xFFFF)
                    }
                }
                memory.writeData = result.value
                devices.writeData = result.value
                memory.writeAddress = dstAddress + increment
                devices.writeAddress = dstAddress + increment
                pc = (pc + 2).and(0xFFFF)
                memory.readAddress =
                    if (instruction.destination == 0 && instruction.shouldStore(flags)) {
                        result.value
                    } else {
                        pc
                    }
                val memWriteEnabled = instruction.dstMem && instruction.shouldStore(flags)
                memory.writeEnabled = memWriteEnabled && !isBanked(instruction.destination)
                devices.writeEnabled = memWriteEnabled && isBanked(instruction.destination)
            }
            1 -> {
                memory.readAddress = pc + 1
            }
            2 -> {
                memory.readAddress = srcAddress
                devices.readAddress = srcAddress
            }
            3 -> {
                memory.readAddress = dstAddress
                devices.readAddress = dstAddress
            }
        }
    }

    override fun captureStageOutputs() {
        if (step == 0) {
            if (instruction.shouldStore(flags)) {
                when (instruction.destination) {
                    0 -> pc = result.value
                    4 -> banking = result.value
                    5 -> r1 = result.value
                    6 -> r2 = result.value
                    7 -> r3 = result.value
                    8 -> flags = result.value
                    12 -> interrupt = result.value
                    13 -> r4 = result.value
                    14 -> r5 = result.value
                    15 -> r6 = result.value
                }
            }

            if (instruction.aluCode != 0) {
                flags = result.flags
            }
        }
    }

    fun run(debugEnabled: Boolean = false) {
        debug = debugEnabled
        while (true) {
            // Set the current step
            clockedComponents.forEach { it.step = step }

            // Do clock tick
            clockedComponents.forEach { it.captureStageInputs() }

            if (debug && step == 2) {
                printInstruction()
                doCommandPrompt()
            }

            clockedComponents.forEach { it.execStage() }

            clockedComponents.forEach { it.captureStageOutputs() }

            step += 1
            if (step > 3) {
                step = 0
            }
        }
    }

    private fun doCommandPrompt() {
        var done = false
        while (!done) {
            print(" > ")
            when (val input = readLine()) {
                "c", "continue" -> {
                    debug = false
                    done = true
                }
                "", "n", "next" -> {
                    debug = true
                    done = true
                }
                "p", "print" -> {
                    printInstruction()
                }
                "e", "exit" -> {
                    exitProcess(0)
                }
                else -> {
                    if (input?.startsWith("peek") == true) {
                        val peekReg = input.split(" ").last().toInt()
                        println("Peeked at $peekReg: ${peek(peekReg, 0).toString(16).toUpperCase()}")
                    }
                }
            }
        }
    }

    private fun printInstruction() {
        println("=======================================================")
        println(String.format("r3#%s", stackString(3)))
        println(String.format("r2#%s", stackString(2)))
        println(String.format("Stack#%s", stackString(1)))
        println(
            String.format("%04X: %s # %s",
                pc,
                instruction.toString(),
                if (instruction.shouldStore(flags)) "store result" else "update flags"
            )
        )
    }

    private fun stackString(reg: Int): String {
        var address = when (reg) {
            1 -> r1
            2 -> r2
            3 -> r3
            4 -> r4
            5 -> r5
            6 -> r6
            else -> 0
        }
        val endAddress = if (address > 0) (address + 10) else address
        var result = String.format("%04X:", address)
        while (address in 1 until endAddress && address <= 0xFFFF) {
            result += String.format(" %04X", memory.peek(address))
            address += 1
        }
        return result
    }
}