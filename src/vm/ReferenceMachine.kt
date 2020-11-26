package com.grokthis.ucisc.vm

import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants.LEADING
import javax.swing.SwingUtilities

class ReferenceMachine(val code: List<Int>): JFrame(), ClockSynchronized, KeyListener {

    private val processor: StagedProcessor
    private val ledLabels: List<JLabel>
    private var ledValue = 0

    init {
        val memoryBlock = MemoryBlock(13)
        code.forEachIndexed { address, word ->
            memoryBlock.setData(address, word)
        }
        processor = StagedProcessor(memoryBlock)
        processor.synchronizeWith(this)
        title = "uCISC Emulator"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(960, 540)
        setLocationRelativeTo(null)
        layout = FlowLayout()

        val list = mutableListOf<JLabel>()
        for (i in 0..15) {
            val label = JLabel("   ", null, LEADING).apply {
                minimumSize = Dimension(16, 16)
                background = Color.black
                isOpaque = true
            }
            list.add(0, label)
            add(label)
        }
        ledLabels = list
        addKeyListener(this)
        isVisible = true
    }

    fun run() {
        processor.run()
    }

    override fun captureStageInputs() {
    }

    override fun execStage() {
    }

    override fun captureStageOutputs() {
        val r1 = processor.peek(5, 0)
        if (r1 != ledValue) {
            ledValue = r1
            val leds = BooleanArray(16)
            for (i in 0..15) {
                leds[i] = r1.shr(i).and(0x1) > 0
            }
            SwingUtilities.invokeLater {
                for (i in 0..15) {
                    ledLabels[i].background = if (leds[i]) Color.red else Color.black
                }
                Toolkit.getDefaultToolkit().sync();
            }
        }
    }

    override var step: Int = 0
    override fun keyTyped(e: KeyEvent?) {
    }

    override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode == KeyEvent.VK_ESCAPE) {
            processor.debug = true
        }
    }

    override fun keyReleased(e: KeyEvent?) {
    }
}