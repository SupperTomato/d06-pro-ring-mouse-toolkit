package com.d06.sdk.sample

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDecoder

class MainActivity : Activity() {
    private val decoder = D06InputDecoder(D06InputConfig(detectMousepadTap = true))
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply {
            textSize = 14f
            text = "D06 SDK Sample\nPair the D06 and interact with this screen.\n\n"
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(logView) })
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val events = decoder.onMotionEvent(ev)
        if (events.isNotEmpty()) {
            append("motion ${deviceLabel(ev)} ${events.joinToString()}")
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val events = decoder.onKeyEvent(event)
        if (events.isNotEmpty()) {
            append("key ${deviceLabel(event)} ${events.joinToString()}")
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun append(line: String) {
        logView.append("$line\n")
    }

    private fun deviceLabel(event: MotionEvent): String {
        val device = event.device ?: return "[unknown-device]"
        val match = if (decoder.isD06Device(device)) "D06" else "other"
        return "[$match ${device.name} vid=${device.vendorId} pid=${device.productId}]"
    }

    private fun deviceLabel(event: KeyEvent): String {
        val device = event.device ?: return "[unknown-device]"
        val match = if (decoder.isD06Device(device)) "D06" else "other"
        return "[$match ${device.name} vid=${device.vendorId} pid=${device.productId}]"
    }
}
