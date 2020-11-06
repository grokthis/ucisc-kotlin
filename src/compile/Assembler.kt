package com.grokthis.ucisc.compile

/**
 * Parse the most basic level of uCISC assembly language. Higher level elements
 * will translate, perhaps through multiple levels into an assembly file. The
 * assembler will thin convert this file into machine code.
 *
 * The only thing that is allowed to remain in the file at this point are labels
 * and assembly statements of the form:
 *     A.op S.reg/mem I.imm D.reg/mem O.imm M.inc E.eff
 * and hex data of the form:
 *     % XXXX XXXX XXXX XXXX ...
 * nothing is optional at this point. Everything must fit on a 16-bit boundary.
 *
 * The instructions will be translated into a set of 32-bit values of the form
 *     SSSSDDDD MEEEAAAA OOOOIIII IIIIIIII
 * where:
 *   A = ALU Op Code
 *   S = Source
 *   D = Destination
 *   M = Increment
 *   E = Effect
 *   O = Offset
 *   I = Immediate
 *
 * For more details, you can see the uCISC spec or read the Assembler code.
 *
 * TODO: Replace this with uCISC code once compile is bootstrapped
 */
class Assembler {

    fun compile(assembly: String): CompiledCode {
        val lines = assembly.split("\n").map { it.trim() }
        var parsers: List<Parser> = listOf()
        // Parse each line for instructions, labels, data, etc
        val parser = AggregateParser.get()
        val parsed = lines.mapIndexed { index: Int, line: String ->
            if (line.startsWith("#") || line.isBlank()) {
                null
            } else {
                val parsed: ParsedLine? = parser.parse(line, parser)
                if (parsed == null) {
                    val lineNumber = index + 1
                    println("Error: Unable to parse line $lineNumber:\n  $line")
                }
                parsed
            }
        }.filterNotNull()

        var loopAddresses = mutableListOf<Int>()
        var breakInstructions = mutableListOf<MutableList<Instruction>>()
        var address = 0
        var labelAddresses = mutableMapOf<String, Int>()
        val defs = mutableMapOf<String, Int>()
        // Set the address for each instruction
        parsed.forEach { parsedLine ->
            parsedLine.labels.forEach { label ->
                if (label == "loop") {
                    loopAddresses.add(address)
                } else if (label == "break") {
                    if (breakInstructions.size > 0) {
                        breakInstructions[0].forEach { instruction ->
                            instruction.setImmediate(address - instruction.address)
                        }
                        breakInstructions.removeAt(0)
                    }
                } else {
                    labelAddresses[label] = address
                }
            }
            defs.putAll(parsedLine.defs)

            parsedLine.instructions.forEach { instruction ->
                instruction.address = address
                address += 2

                if (instruction.immediateLabel.startsWith("loop")) {
                    val count = instruction.immediateLabel.split(".").last().toIntOrNull()
                    if (count == null) {
                        instruction.setImmediate(loopAddresses.last() - instruction.address)
                    } else {
                        instruction.setImmediate(loopAddresses[loopAddresses.size - count] - instruction.address)
                    }
                }

                if (instruction.immediateLabel.startsWith("break")) {
                    val count = instruction.immediateLabel.split(".").last().toIntOrNull()
                    if (count == null) {
                        if (breakInstructions.size == 0) {
                            breakInstructions.add(mutableListOf(instruction))
                        } else {
                            breakInstructions[0].add(instruction)
                        }
                    } else {
                        while (breakInstructions.size < count) {
                            breakInstructions.add(mutableListOf())
                        }
                        breakInstructions[count - 1].add(instruction)
                    }
                }
            }
            address += parsedLine.data.size
        }

        // Replace all label addresses
        parsed.forEach { parsedLine ->
            parsedLine.instructions.forEach { instruction ->
                val labelAddress = labelAddresses[instruction.immediateLabel]
                val defValue = defs[instruction.immediateLabel]
                if (defValue != null) {
                    instruction.setImmediate(defValue)
                } else if (labelAddress != null) {
                    instruction.setImmediate(labelAddress - instruction.address)
                }
            }
        }


        val instructions = parsed
            .flatMap { parsedLine -> parsedLine.instructions }
            .flatMap { instruction -> instruction.words() }

        val tests = mutableMapOf<Int, Int>()
        parsed.forEach { parsedLine ->
            parsedLine.tests.forEach { label, returnValue ->
                val address = labelAddresses[label] ?: label.toIntOrNull()
                if (address != null) {
                    tests[address] = returnValue
                }
            }
        }

        return CompiledCode(instructions, tests)
    }

    private fun toWords(int32: Int): List<Int> {
        return listOf(int32.shr(16).and(0xFFFF), int32.and(0xFFFF))
    }

    private fun isValidOpCode(opCode: Int): Boolean {
        return opCode in 0..15
    }

    private fun isValidArgument(registerCode: Int): Boolean {
        return registerCode in 0..15
    }

    private fun isValidImm(immValue: Int, src: Int, dest: Int): Boolean {
        return when {
            (dest in 1..3).or(dest in 9..11) -> when (src) {
                in 1..3 -> (immValue >= 0).and(immValue < 4096)
                in 9..11 -> (immValue >= 0).and(immValue < 4096)
                else -> (immValue >= -2048).and(immValue < 2048)
            }
            else -> when (src) {
                in 1..3 -> (immValue >= 0).and(immValue < 65535)
                in 9..11 -> (immValue >= 0).and(immValue < 65535)
                else -> (immValue >= -32768).and(immValue < 32768)
            }
        }
    }

    private fun isValidOffset(immValue: Int, dest: Int): Boolean {
        return when (dest) {
            in 1..3 -> (immValue >= 0).and(immValue < 16)
            in 9..11 -> (immValue >= 0).and(immValue < 16)
            else -> immValue == 0
        }
    }

    private fun isValidIncrement(effectCode: Int): Boolean {
        return effectCode in 0..1
    }

    private fun isValidEffect(effectCode: Int): Boolean {
        return effectCode in 0..7
    }
}