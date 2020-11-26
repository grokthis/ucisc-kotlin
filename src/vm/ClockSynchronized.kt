package com.grokthis.ucisc.vm

/**
 * Interface for parts of the CPU that are synchronized to the clock
 * to implement. Because code execution is inherently serialized, it
 * is difficult to capture the fact that hardware operations happen
 * simultaneously. To do this, the clock is broken into 3 steps:
 *
 * 1. Capture inputs - capture anything that would be captured on
 *    a positive clock edge. Do not process any state yet or you
 *    may change the results that another component is about to
 *    capture.
 *
 * 2. Execute the stage - compute whatever the hardware would do
 *
 * 3. Capture outputs - set any output state that would be true at
 *    the positive clock edge going out of the component. Hold these
 *    values through at least the next capture inputs stage.
 */
interface ClockSynchronized {

    /**
     * Capture anything that would be captured on a positive clock edge.
     */
    fun captureStageInputs()

    /**
     * Execute the stage internals
     */
    fun execStage()

    /**
     * Capture the outputs for the next clock edge
     */
    fun captureStageOutputs()

    /**
     * Sets the current processor execution step
     */
    var step: Int
}