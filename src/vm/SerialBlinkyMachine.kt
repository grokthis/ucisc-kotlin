package com.grokthis.ucisc.vm

import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants.LEADING
import javax.swing.SwingUtilities

class SerialBlinkyMachine(val code: List<Int>): JFrame(), DeviceListener {

    val processor: Processor = Processor(1, 13)
    val serial: SerialConnection = SerialConnection(100)
    val leds: LedIOBus = LedIOBus(200)
    private val ledLabels: List<JLabel>

    init {
        leds.addListener(this)
        processor.connected[2] = serial
        processor.connected[3] = leds
        processor.load(code)

        title = "uCISC VM"
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(960, 540)
        setLocationRelativeTo(null)
        layout = FlowLayout()

        val list = mutableListOf<JLabel>()
        for (i in 0..31) {
            val x = 8 + 8 * i
            val label = JLabel("   ", null, LEADING).apply {
                minimumSize = Dimension(16, 16)
                background = if (leds.ledLit(i)) Color.red else Color.black
                isOpaque = true
            }
            list.add(label)
            add(label)
        }
        ledLabels = list
        isVisible = true
    }

    fun run() {
        processor.run()
    }

    override fun deviceChanged(device: Device) {
        SwingUtilities.invokeLater(Runnable {
            ledLabels.forEachIndexed { i, label ->
                label.background = if (leds.ledLit(31 - i)) Color.red else Color.black
            }
        })
    }
}