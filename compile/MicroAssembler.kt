package com.grokthis.ucisc.compile

class MicroAssembler {

    fun compile(assembly: String): CompiledCode {
        val lines = assembly.split("\n").map { it.trim() }.filter {
            (!it.startsWith('#')).and(it.trim().isNotEmpty())
        }
        val instructions = mutableListOf<Int>()
        val variables = mutableMapOf<String, Int>()
        val tests = mutableMapOf<Int, Int>()
        lines.forEach { str ->
            val line = str.trim()
            if (line.startsWith(":")) {
                val args = line.split(" ")
                variables[args.first().substring(1)] = args.last().toInt()
            } else if (line.startsWith("TEST")) {
                val test = line.substring(4).trim().split(" ")
                tests[test.first().toInt()] = test.last().toInt()
            } else if (line.trim().isNotEmpty() && !line.startsWith("#")) {
                var args: List<String> = line.split(" ").filter { it != "" }
                args = args.map {
                    if (it == "X" || it == "-") {
                        // X is a temporary placeholder used for debugging, - is zero used for irrelevant args
                        "0"
                    } else {
                        it
                    }
                }
                var encoded = args[1].toInt().and(0xF).shl(28)
                encoded = encoded.or(args[3].toInt().and(0xF).shl(24))
                encoded = encoded.or(args[5].toInt().and(0x1).shl(23))
                encoded = encoded.or(args[6].toInt().and(0x7).shl(20))
                encoded = encoded.or(args[0].toInt().shl(16))
                val imm = variables[args[2]] ?: args[2].toInt()
                val offset = if (args[4] == "-") 0 else variables[args[4]] ?: args[4].toInt()
                if (args[3].toInt() in 1..3 || args[3].toInt() in 9..11) {
                    encoded = encoded.or(offset.and(0xF).shl(12))
                    encoded = encoded.or(imm.and(0x0FFF))
                } else {
                    encoded = encoded.or(imm.and(0xFFFF))
                }

                instructions.add(encoded.shr(16).and(0xFFFF))
                instructions.add(encoded.and(0xFFFF))
            }
        }
        return CompiledCode(instructions, tests)
    }
}