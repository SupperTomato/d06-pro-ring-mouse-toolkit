package com.d06.sdk.remapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class D06AccessibilityRemapperService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Ordinary Android apps do not receive a global stream of all HID mouse
        // events here. Feed decoded D06Event values from an app-controlled path.
    }

    override fun onInterrupt() {
        // No persistent operation to interrupt.
    }

    fun perform(action: D06RemapAction): Boolean {
        return when (action) {
            D06RemapAction.NoOp -> false
            D06RemapAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            D06RemapAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            D06RemapAction.RecentApps -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            is D06RemapAction.ScrollBy -> dispatchScrollGesture(action.dy)
        }
    }

    private fun dispatchScrollGesture(dy: Int): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f
        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(centerX, centerY + dy.coerceIn(-600, 600))
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 180))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
