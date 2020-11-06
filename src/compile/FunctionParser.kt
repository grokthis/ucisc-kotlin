package com.grokthis.ucisc.compile

class FunctionParser: Parser() {
    override fun parse(line: String, rootParser: Parser?): ParsedLine? {
        val commentFree = line.replace(Regex("#.*"), "").trim()
        val regex = Regex("^(?<name>[a-zA-Z0-9.?/@$]+)\\((?<argList>[^\\)]*)\\)")
        val match = regex.matchEntire(commentFree) ?: return null
        val name = match.groups["name"]?.value ?: return null
        val argsList = match.groups["argList"]?.value ?: return null
        val args = argsList.split(",").map { it.trim() }.filter { it.isNotBlank() }

        // 1. Push return address pc + argsList.size + 4
        val parsed = ParsedLine()
        val pushReturnAddress = Instruction()
        val retOffset = args.size * 2 + 4
        pushReturnAddress.setInstruction(
            "copy",
            "pc",
            retOffset.toString(),
            "stack",
            "0",
            "push",
            "store"
        )
        parsed.instructions.add(pushReturnAddress)
        var stackOffset = 1
        // 2. Push args
        args.forEach { arg ->
            val components = arg.split(Regex("\\s")).filter { it.isNotBlank() }.toMutableList()
            val pushArg = Instruction()
            if (components[0] == "stack" || components[0] == "r1") {
                components[1] = (components[1].toInt() + stackOffset).toString()
            }
            pushArg.setInstruction(
                "copy",
                components[0],
                if (components.size < 2) "0" else components[1],
                "stack",
                "0",
                "push",
                "store"
            )
            parsed.instructions.add(pushArg)
            stackOffset += 1
        }
        // 3. Jump to label
        val jump = Instruction()
        jump.setInstruction(
            "copy",
            "pc",
            name,
            "pc",
            "NA",
            "NA",
            "store"
        )
        parsed.instructions.add(jump)

        return parsed
    }
}