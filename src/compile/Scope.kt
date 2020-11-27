package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class Scope(val parent: Scope? = null): Words() {
    private val parsers: List<Parser> = listOf(
        DefParser(),
        VarParser(),
        LabelParser(),
        StatementParser(),
        DataParser(),
        FunctionParser()
    )

    private val words: MutableList<Words> = mutableListOf()
    private val defines: MutableMap<String, Register> = mutableMapOf()
    private val variables: MutableMap<Register, MutableMap<String, Int>> = mutableMapOf()
    private val deltas: MutableMap<Register, MutableMap<String, Int>> = mutableMapOf()

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

    fun defineRegister(name: String, register: Register) {
        defines[name] = register
        variables.getOrPut(register) { mutableMapOf() }
        deltas.getOrPut(register) { mutableMapOf() }
    }

    fun findVariable(register: Register, variableName: String): Int {
        return when {
            variables[register] != null -> {
                val offset = variables[register]!![variableName]
                    ?: parent?.findVariable(register, variableName)
                    ?: throw IllegalArgumentException(
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

    fun defineVariable(register: Register, name: String, offset: Int) {
        val vars = variables.getOrPut(register) { mutableMapOf() }
        vars[name] = offset
        val offsets = deltas.getOrPut(register) { mutableMapOf() }
        offsets[name] = 0
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

    fun lastWords(): Words {
        return if (words.isEmpty()) {
            this
        } else {
            words.last()
        }
    }

    fun addWords(words: Words) {
        this.words.add(words)
    }

    fun updateDelta(register: Register, change: Int) {
        if (deltas[register] != null) {
            val vars = deltas[register]!!
            vars.forEach { (name, value) ->
                vars[name] = value + change
            }
        }
        parent?.updateDelta(register, change)
    }

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
            else -> {
                val parser = parsers.find { it.matches(line) }
                    ?: throw IllegalArgumentException("Expecting valid statement")
                return parser.parse(line, this)
            }
        }
    }

    override fun resolveLabels(pc: Int, labels: MutableMap<String, Int>): Int {
        this.labels.forEach { (label, _) ->
            labels[label] = pc
        }
        var currentPC = pc
        words.forEach { statement ->
            currentPC = statement.resolveLabels(currentPC, labels)
        }
        return currentPC
    }

    override fun words(pc: Int, labels: Map<String, Int>): List<Int> {
        val words = mutableListOf<Int>()
        val implicitLabels = labels.toMutableMap()
        implicitLabels["loop"] = pc
        implicitLabels["break"] = pc + wordCount()
        this.words.forEach { statement ->
            words.addAll(statement.words(pc + words.size, implicitLabels))
        }
        return words
    }

    override fun wordCount(): Int {
        return words.map { it.wordCount() }.sum()
    }
}