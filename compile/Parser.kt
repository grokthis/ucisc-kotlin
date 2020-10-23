package com.grokthis.ucisc.compile

abstract class Parser {
   /**
     * Parses a part of the input if it can, returns the number
     * of characters parsed. If it can't parse the input, it
     * returns 0.
     */
    abstract fun parse(line: String, rootParser: Parser?): ParsedLine?
}