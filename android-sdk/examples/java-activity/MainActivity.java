package com.example.d06;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;
import com.d06.sdk.core.D06EventTransformConfig;
import com.d06.sdk.input.D06Input;
import com.d06.sdk.input.D06InputConfig;
import com.d06.sdk.input.D06InputDiagnostics;

public class MainActivity extends Activity {
    private final D06InputDiagnostics diagnostics = new D06InputDiagnostics();
    private final D06Input d06 = D06Input.create(
            new D06InputConfig(
                    true,
                    250L,
                    new D06EventTransformConfig(false, false, 1.25f, 1.0f, 2)
            ),
            diagnostics,
            event -> log(event.toString())
    );
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logView = new TextView(this);
        setContentView(logView);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        return d06.dispatch(ev) || super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return d06.dispatch(event) || super.dispatchKeyEvent(event);
    }

    private void log(String line) {
        if (logView != null) {
            logView.append(line + "\n");
        }
    }
}
