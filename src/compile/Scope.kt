package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class Scope(val parent: Scope? = null): Words() {
    private val words: MutableList<Words> = mutableListOf()
    private val defines: MutableMap<String, Register> = mutableMapOf()
    private val variables: MutableMap<Register, MutableMap<String, Int>> = mutableMapOf()
    private val deltas: MutableMap<Register, MutableMap<String, Int>> = mutableMapOf()
    private val labels: MutableList<String> = mutableListOf()

    fun findRegister(registerName: String): Register {
        return when {
            defines[registerName] != null -> {
                defines[registerName]!!
            }
            parent != null -> {
                parent.findRegister(registerName)
            }
            else -> {
                try {
                    Register.valueOf(registerName.toUpperCase())
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Invalid register name: $registerName, expecting valid variable or register."
                    )
                }
            }
        }
    }

    fun findVariable(register: Register, variableName: String): Int {
        return when {
            variables[register] != null -> {
                val offset = variables[register]!![variableName]
                    ?: parent?.findVariable(register, variableName)
                    ?: throw IllegalStateException(
                        "Unexpected missing variable: $variableName"
                    )
                offset
            }
            parent != null -> {
                parent.findVariable(register, variableName)
            }
            else -> {
                throw IllegalArgumentException(
                    "Missing variable declaration: $register.$variableName."
                )
            }
        }
    }

    fun findDelta(register: Register, variableName: String): Int {
        return when {
            deltas[register] != null -> {
                val offset = deltas[register]!![variableName]
                    ?: parent?.findDelta(register, variableName)
                    ?: throw IllegalStateException(
                        "Unexpected missing delta: $variableName"
                    )
                offset
            }
            parent != null -> {
                parent.findDelta(register, variableName)
            }
            else -> {
                throw IllegalArgumentException(
                    "Missing variable declaration: $register.$variableName."
                )
            }
        }
    }

    private fun updateDelta(register: Register, change: Int) {
        if (deltas[register] != null) {
            val vars = deltas[register]!!
            vars.forEach { (name, value) ->
                vars[name] = vars[name]!! + change
            }
        }
        parent?.updateDelta(register, change)
    }


    private val defRegex =
        Regex("def +(?<name>[a-zA-Z0-9_\\-]+)/(?<reg>[a-zA-Z0-9]+) *(?<src><.+)?")
    private val varRegex =
        Regex("var +(?<reg>[a-zA-Z0-9_\\-]+)\\.(?<name>[a-zA-Z0-9_\\-]+)/(?<offset>[0-9]+) *(?<push>push)? *(?<src><.+)?")
    private val dstRegex =
        Regex("(?<arg>&?[a-zA-Z0-9\\-_/.]+) *(?<inc>push)? *(?<src><.+)")
    private val srcRegex =
        Regex("<(?<eff>[\\-~0!noei])\\?? (?<op>[a-z]+) (?<arg>&?[a-zA-Z0-9\\-_/.]+) *(?<inc>pop)?")
    private val labelRegex = Regex("(?<label>[a-zA-Z0-9_\\-]+):")

    /**
     * Parses a line of uCISC code.
     *
     * Takes a trimmed line with no comments in it and the
     * line number, for good error messages.
     */
    fun parseLine(line: String): Scope {
        when {
            line.startsWith("{") -> {
                val subScope = Scope(this)
                words.add(subScope)
                return subScope
            }
            line.startsWith("}") -> {
                if (parent == null) {
                    throw IllegalArgumentException("Unmatched close scope")
                }
                return parent
            }
            line.startsWith("def") -> {
                parseDef(line)
            }
            line.startsWith("var") -> {
                parseVar(line)
            }
            line.matches(labelRegex) -> {
                parseLabel(line)
            }
            else -> {
                parseDst(line)
            }
        }
        return this
    }

    fun parseLabel(line: String) {
        val match = labelRegex.matchEntire(line)
            ?: throw IllegalArgumentException("Expected valid label")

        val label = match.groups["label"]!!.value
        if (words.isEmpty()) {
            labels.add(label)
        } else {
            words.last().addLabel(label)
        }
    }

    fun parseSource(line: String): Source {
        val match = srcRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expected valid source: <effect> [&]<register>[.<variable>][/<offset>] [pop]"
            )

        val effStr = match.groups["eff"]!!.value
        val effect: Effect = when(effStr) {
            "0" -> Effect.ZERO
            "!" -> Effect.NOTZERO
            "n" -> Effect.NEGATIVE
            "p" -> Effect.POSITIVE
            "~" -> Effect.FLAGS
            "-" -> Effect.STORE
            "o" -> Effect.OVERFLOW
            "i" -> Effect.INTERRUPTED
            else -> {
                throw IllegalArgumentException(
                    "Invalid effect: <$effStr"
                )
            }
        }

        val opStr = match.groups["op"]!!.value
        val op: Op =
            try {
                Op.valueOf(opStr.toUpperCase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid op code: $opStr")
            }

        val isInc = match.groups["inc"] != null
        val argString = match.groups["arg"]!!.value
        val argument = Argument.parse(argString, this)
        return Source(argument, op, effect, isInc)
    }

    fun parseDst(line: String) {
        val match = dstRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid destination: [&]<register>[.<variable>][/<offset>] [push] [source]"
            )

        val argString = match.groups["arg"]!!.value
        val argument = Argument.parse(argString, this)

        if (argument.addr && argument.offset != 0) {
            throw IllegalArgumentException(
                "Offset must be zero for address destinations"
            )
        } else if (!argument.addr && argument.offset !in 0..15) {
            throw IllegalArgumentException(
                "Offset out of bounds: ${argument.offset} - it must be in 0..15"
            )
        }

        val isInc = match.groups["inc"] != null
        val source: Source = parseSource(match.groups["src"]!!.value)
        words.add(Statement(argument, isInc, source))

        if (isInc) {
            updateDelta(argument.register, 1)
        }
    }

    fun parseDef(line: String) {
        val match = defRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid def: def <name>/<register> [source]"
            )

        val name = match.groups["name"]!!.value
        val registerName = match.groups["reg"]!!.value.toUpperCase()
        try {
            val register = Register.valueOf(registerName)
            defines[name] = register
            variables.getOrPut(register) { mutableMapOf<String, Int>() }
            deltas.getOrPut(register) { mutableMapOf<String, Int>() }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid register name: $registerName, expecting r[1-6]."
            )
        }
        val source = match.groups["src"]?.value
        if (source != null) {
            val source: Source = parseSource(source)
            val argument = Argument(defines[name]!!, 0, true)
            words.add(Statement(argument, false, source))
        }
    }

    fun parseVar(line: String) {
        val match = varRegex.matchEntire(line)
            ?: throw IllegalArgumentException(
                "Expecting valid var: var <register>.<name>/<offset> [source]"
            )

        val registerName = match.groups["reg"]!!.value
        val name = match.groups["name"]!!.value
        val offset = match.groups["offset"]!!.value
        val push = match.groups["push"]?.value
        val source = match.groups["src"]?.value

        val register = findRegister(registerName)
        val offsetValue = offset.toInt()
        val vars = variables.getOrPut(register) { mutableMapOf() }
        vars[name] = offsetValue
        val offsets = deltas.getOrPut(register) { mutableMapOf() }
        offsets[name] = 0

        if (source != null) {
            val source: Source = parseSource(source)
            val argument = Argument(register, offsetValue, false)
            words.add(Statement(argument, push != null, source))
        }
    }

    override fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int {
        this.labels.forEach { label ->
            labels[label] = pc
        }
        var currentPC = pc
        words.forEach() { statement ->
            currentPC = statement.resolveLabels(currentPC, labels)
        }
        return currentPC
    }

    override fun addLabel(name: String) {
        labels.add(name)
    }

    override fun words(pc: Int, labels: Map<String, Int>): List<Int> {
        var currentPC = pc
        val words = mutableListOf<Int>()
        val implicitLabels = labels.toMutableMap()
        implicitLabels["loop"] = pc
        implicitLabels["break"] = pc + wordCount()
        this.words.forEach() { statement ->
            words.addAll(statement.words(pc + words.size, implicitLabels))
        }
        return words
    }

    override fun wordCount(): Int {
        return words.map { it.wordCount() }.sum()
    }
}