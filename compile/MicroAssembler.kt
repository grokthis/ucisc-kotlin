package com.grokthis.ucisc.compile

class MicroAssembler {

    fun compile(assembly: String): CompiledCode {
        val lines = assembly.split("\n").map { it.trim() }.filter {
            (!it.startsWith('#')).and(it.trim().isNotEmpty())
        }
        val instructions = mutableListOf<Int>()
        val immediateLabels = mutableMapOf<Int, String>()
        val offsetLabels = mutableMapOf<Int, String>()
        val tests = mutableMapOf<Int, Int>()
        val labels = mutableMapOf<String, Int>()

        lines.forEach { str ->
            val line = str.trim()
            if (line.startsWith(":")) {
                var args = line.split(" ")
                labels[args.first().substring(1)] = instructions.size
            } else if (line.startsWith("TEST")) {
                val test = line.substring(4).trim().split(" ")
                tests[test.first().toInt()] = test.last().toInt()
            } else if (line.trim().isNotEmpty() && !line.startsWith("#")) {
                var args: List<String> = line.split(" ").filter { it != "" }
                var encoded = 0
                var imm = 0
                var dest = 0
                args.forEachIndexed { index, arg ->
                    val value = when {
                        arg == "-" -> 0 // - is zero used for irrelevant args
                        arg.toIntOrNull() == null -> {
                            when (index) {
                                2 -> immediateLabels[instructions.size] = arg
                                4 -> offsetLabels[instructions.size] = arg
                            }
                            0
                        }
                        else -> arg.toInt()
                    }
                    when (index) {
                        0 -> encoded = encoded.or(value.shl(16))
                        1 -> encoded = encoded.or(value.and(0xF).shl(28))
                        2 -> imm = value
                        3 -> {
                            dest = value
                            encoded = encoded.or(value.and(0xF).shl(24))
                        }
                        4 -> {
                            if (dest in 1..3 || dest in 9..11) {
                                encoded = encoded.or(value.and(0xF).shl(12))
                                imm = imm.and(0x0FFF)
                                encoded = encoded.or(imm)
                            } else {
                                imm = imm.and(0xFFFF)
                                encoded = encoded.or(imm)
                            }
                        }
                        5 -> encoded = encoded.or(value.and(0x1).shl(23))
                        6 -> encoded = encoded.or(value.and(0x7).shl(20))
                    }
                }
                instructions.add(encoded.shr(16).and(0xFFFF))
                instructions.add(encoded.and(0xFFFF))
            }
        }

        offsetLabels.forEach {
            val ins = it.key
            val label = it.value
            val offset = labels[label] ?: 0
            instructions[ins] = instructions[ins + 1].or(offset.shl(12))
        }
        immediateLabels.forEach {
            val ins = it.key
            val label = it.value
            val imm = labels[label] ?: 0
            instructions[ins] = instructions[ins + 1].or(imm)
        }
        return CompiledCode(instructions, tests)
    }
}