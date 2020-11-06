package com.grokthis.ucisc.compile

class InstructionParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val commentFree = line.replace(Regex("#.*"), "")
        val args = commentFree.split(Regex("\\s")).filter { it.isNotBlank() }
        if (args.size >= 4) {
            val parsed = ParsedLine()
            val instruction = Instruction()
            instruction.setOpCode(args[0])
            instruction.setSource(args[1])
            instruction.setImmediate(args[2])
            instruction.setDestination(args[3])
            instruction.setEffect(4) // Default to store

            // Handle various cases where offset, increment and effect
            // may be missing from the instruction declaration
            if (args.size >= 5 && instruction.hasOffset()) {
                instruction.setOffset(args[4])
                if (args.size >= 6) {
                    if (instruction.parseEffect(args[5]) < 0) {
                        instruction.setIncrement(args[5])
                        if (args.size >= 7) {
                            instruction.setEffect(args[6])
                        }
                    } else {
                        instruction.setEffect(args[5])
                    }
                }
            } else if (args.size >= 5) {
                if (instruction.parseEffect(args[4]) < 0) {
                    instruction.setIncrement(args[4])
                    if (args.size >= 6) {
                        instruction.setEffect(args[5])
                    }
                } else {
                    instruction.setEffect(args[4])
                }
            }
            parsed.instructions.add(instruction)
            return parsed
        }
        return null
    }
}