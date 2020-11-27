package com.grokthis.ucisc.compile

import java.lang.IllegalStateException

class Statement(
    val argument: Argument,
    val push: Boolean,
    val source: Source
): Words() {
    override fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int {
        val nextPC = pc + 2
        this.labels.forEach { (label, _) ->
            labels[label] = nextPC
        }
        return nextPC
    }

    override fun wordCount(): Int {
        return 2
    }

    override fun words(pc: Int, labels: Map<String, Int>): List<Int> {
        argument.resolveLabel(pc, labels)
        source.argument.resolveLabel(pc, labels)

        val instruction = Instruction()

        if (source.argument.addr) {
            instruction.setSource(source.argument.register.value)
        } else {
            if (!source.argument.register.hasMemRef) {
                instruction.setSource(source.argument.register.value)
            } else {
                instruction.setSource(source.argument.register.memRef)
            }
        }
        instruction.setImmediate(source.argument.offset)

        if (argument.addr) {
            instruction.setDestination(argument.register.value)
            if (argument.offset != 0) {
                throw IllegalStateException("Invalid destination offset for address value")
            }
        } else {
            if (!argument.register.hasMemRef) {
                instruction.setDestination(argument.register.value)
            } else {
                instruction.setDestination(argument.register.memRef)
            }
            instruction.setOffset(argument.offset)
        }

        instruction.setEffect(source.effect.value)
        if (source.argument.addr && source.pop) {
            throw IllegalStateException(
                "Source pop is not allowed for address argument"
            )
        }
        if (argument.addr && push) {
            throw IllegalStateException(
                "Destination push is not allowed for address argument"
            )
        }
        val inc = if (source.pop || push) {
            1
        } else {
            0
        }
        instruction.setIncrement(inc)
        instruction.setOpCode(source.opcode.value)

        return instruction.words()
    }
}