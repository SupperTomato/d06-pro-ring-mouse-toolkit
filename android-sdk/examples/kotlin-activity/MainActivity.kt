package com.example.d06

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import com.d06.sdk.core.D06EventTransformConfig
import com.d06.sdk.remapper.D06RemapAction
import com.d06.sdk.remapper.D06RemapPreset
import com.d06.sdk.remapper.D06Remapper
import com.d06.sdk.input.D06Input
import com.d06.sdk.input.D06InputConfig
import com.d06.sdk.input.D06InputDiagnostics

class MainActivity : Activity() {
    private val diagnostics = D06InputDiagnostics()
    private val remapper = D06Remapper(
        D06RemapPreset("my-profile") {
            on(com.d06.sdk.core.D06Event.MousepadTap, D06RemapAction.Home)
            on(com.d06.sdk.core.D06Event.MiddleUp, D06RemapAction.Back)
        }
    )
    private val d06 = D06Input(
        D06InputConfig(
            detectMousepadTap = true,
            eventTransform = D06EventTransformConfig(
                movementSensitivity = 1.25f,
                movementDeadzone = 2
            )
        ),
        diagnostics
    ) { event ->
        val action = remapper.actionFor(event)
        log("$event -> $action")
    }
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this)
        setContentView(logView)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return d06.dispatch(event) || super.dispatchKeyEvent(event)
    }

    private fun log(line: String) {
        logView.append("$line\n")
    }
}
