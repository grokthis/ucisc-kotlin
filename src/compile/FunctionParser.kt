package com.grokthis.ucisc.compile

import java.lang.IllegalArgumentException

class FunctionParser: Parser {
    private val funRegex = Regex("(?<declare>fun)? *(?<reg>[a-zA-Z0-9_\\-]+)\\.(?<label>[a-zA-Z0-9_\\-]+) *\\((?<args>.*)\\)( *-> *(?<returns>[a-zA-Z0-9_\\-, ]+))(?<scope>\\{)?")
    private val labelRegex = Regex("[a-zA-Z0-9_\\-]+")

    override fun parse(line: String, scope: Scope): Scope {
        val match = funRegex.matchEntire(line)
            ?: throw IllegalArgumentException("Invalid function declaration or call")

        val isDeclare = match.groups["declare"] != null
        if (isDeclare) {
            if (match.groups["scope"] == null) {
                throw IllegalArgumentException(
                    "Function declaration must start a new block"
                )
            }
            val functionScope = Scope(scope)
            scope.addWords(functionScope)

            val registerName = match.groups["reg"]!!.value
            val register = functionScope.findRegister(registerName)

            val argsString = match.groups["args"]!!.value
            val args = argsString.split(",").map { it.trim() }
            val invalidArguments = args.filter { !it.matches(labelRegex) }
            if (invalidArguments.isNotEmpty()) {
                throw IllegalArgumentException("Invalid arguments: ${invalidArguments.joinToString { ", " }}")
            }

            val name = match.groups["label"]!!.value
            functionScope.addLabel(name, args.size)


            functionScope.defineVariable(register, "return", args.size)
            args.forEachIndexed { offset, arg ->
                functionScope.defineVariable(register, arg, offset)
            }
            val returns = match.groups["returns"]
            if (returns != null) {
                val returnArgs = returns.value.split(",").map { it.trim() }
                val invalidArguments = returnArgs.filter { !it.matches(labelRegex) }
                if (invalidArguments.isNotEmpty()) {
                    throw IllegalArgumentException("Invalid return arguments: ${invalidArguments.joinToString { ", " }}")
                }
                returnArgs.forEachIndexed() { offset, arg ->
                    functionScope.defineVariable(register, arg, args.size + 1 + offset)
                }
            }

            return functionScope
        } else { // function call
            val functionScope = if (match.groups["scope"] == null) {
                scope
            } else {
                val newScope = Scope(scope)
                scope.addWords(newScope)
                newScope
            }

            val registerName = match.groups["reg"]!!.value
            val register = functionScope.findRegister(registerName)

            val argsString = match.groups["args"]!!.value
            val args = argsString.split(",").map { it.trim() }

            val returns = match.groups["returns"]
            if (returns != null) {
                val returnArgs = returns.value.split(",").map { it.trim() }
                val invalidArguments = returnArgs.filter { !it.matches(labelRegex) }
                if (invalidArguments.isNotEmpty()) {
                    throw IllegalArgumentException("Invalid return arguments: ${invalidArguments.joinToString { ", " }}")
                }

                // Create space on the stack for the return args
                functionScope.addWords(
                    Statement(
                        Argument(register, 0, true),
                        false,
                        Source(Argument(register, -1 * returnArgs.size, true), Op.COPY, Effect.STORE, false)
                    )
                )
                functionScope.updateDelta(register, returnArgs.size)
                // Create variables for the return args
                returnArgs.forEachIndexed { offset, arg ->
                    functionScope.defineVariable(register, arg, offset)
                }
            }
            // Push the return address to the stack
            functionScope.addWords(
                Statement(
                    Argument(register, 0, false),
                    true,
                    Source(Argument(Register.PC, args.size * 2 + 4, true), Op.COPY, Effect.STORE, false)
                )
            )
            functionScope.updateDelta(register, 1)
            val parsedArgs = args.map { Argument.parse(it, functionScope) }
            functionScope.updateDelta(register, -1)
            parsedArgs.reversed().forEach {
                functionScope.addWords(
                    Statement(
                        Argument(register, 0, false),
                        true,
                        Source(it, Op.COPY, Effect.STORE, false)
                    )
                )
            }
            val name = match.groups["label"]!!.value
            functionScope.addWords(
                Statement(
                    Argument(Register.PC, 0, true),
                    false,
                    Source(Argument(Register.PC, 0, true, name), Op.COPY, Effect.STORE, false)
                )
            )
            return functionScope
        }
    }

    override fun matches(line: String): Boolean {
        return line.startsWith("fun") || line.matches(funRegex)
    }

}