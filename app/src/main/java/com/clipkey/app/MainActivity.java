package com.clipkey.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);

        TextView info = new TextView(this);
        info.setTextSize(16);
        info.setText("ClipKey: keyboard with a numbered clipboard history.\n\n"
                + "1. Tap \"Enable keyboard\" and switch ClipKey on.\n"
                + "2. Tap \"Choose keyboard\" and select ClipKey.\n"
                + "3. Copy some text anywhere, then open the Clipboard tab.\n");
        box.addView(info);

        Button enable = new Button(this);
        enable.setText("1. Enable keyboard");
        enable.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(enable);

        Button choose = new Button(this);
        choose.setText("2. Choose keyboard");
        choose.setOnClickListener(v -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        box.addView(choose);

        EditText test = new EditText(this);
        test.setHint("Test field: type here");
        box.addView(test);

        setContentView(box);
    }
}
