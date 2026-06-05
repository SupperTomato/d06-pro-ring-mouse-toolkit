package com.d06.sdk.sample

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ScrollView
import android.widget.TextView
import com.d06.sdk.input.D06Input
import com.d06.sdk.input.D06InputConfig

class MainActivity : Activity() {
    private val d06 = D06Input(D06InputConfig(detectMousepadTap = true)) { event ->
        append(event.toString())
    }
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
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return d06.dispatch(event) || super.dispatchKeyEvent(event)
    }

    private fun append(line: String) {
        logView.append("$line\n")
    }
}
