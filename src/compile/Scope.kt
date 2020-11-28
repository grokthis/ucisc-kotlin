package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class Scope(val parent: Scope? = null): Words() {
    private val parsers: List<Parser> = listOf(
        DefParser(),
        VarParser(),
        LabelParser(),
        DataParser(),
        FunctionParser(),
        StatementParser()
    )

    private val words: MutableList<Words> = mutableListOf()
    init {
        // Empty words is needed so that labels can be attached to the beginning
        // of the scope, but within the scope itself, even if no statements have
        // been made yet.
        words.add(EmptyWords())
    }
    private val defines: MutableMap<String, Register> = mutableMapOf()
    private val variables: MutableMap<Register, MutableMap<String, Variable>> = mutableMapOf()
    private val resolvedLabels: MutableMap<String, Int> = mutableMapOf()

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
    }

    fun findVariable(register: Register, variableName: String): Variable {
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
        vars[name] = Variable(name, offset)
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
        if (variables[register] != null) {
            val vars = variables[register]!!
            vars.forEach { (_, value) ->
                value.delta += change
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
        var currentPC = pc
        words.forEach { statement ->
            currentPC = statement.resolveLabels(currentPC, resolvedLabels)
        }
        // Labels are technically outside the current scope at the end
        this.labels.forEach { (label, _) ->
            labels[label] = pc
        }
        return currentPC
    }

    private fun fillResolvedLabelScope(labels: MutableMap<String, Int>) {
        parent?.fillResolvedLabelScope(labels)
        labels.putAll(resolvedLabels)
    }

    override fun words(pc: Int, labels: Map<String, Int>): List<Int> {
        val words = mutableListOf<Int>()
        val inScopeLabels = mutableMapOf<String, Int>()
        fillResolvedLabelScope(inScopeLabels)
        inScopeLabels.putAll(resolvedLabels)
        inScopeLabels["loop"] = pc
        inScopeLabels["break"] = pc + wordCount()
        this.words.forEach { statement ->
            words.addAll(statement.words(pc + words.size, inScopeLabels))
        }
        return words
    }

    override fun wordCount(): Int {
        return words.map { it.wordCount() }.sum()
    }
}