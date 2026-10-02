package com.jituileet.tvmouse;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Simple help page containing only the core TV Mouse functions. */
public class AboutActivity extends Activity {
    private String appliedLanguageMode;

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        appliedLanguageMode = LanguageManager.getMode(this);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        String mode = LanguageManager.getMode(this);
        if (!mode.equals(appliedLanguageMode)) {
            appliedLanguageMode = mode;
            recreate();
        }
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(48), dp(28), dp(48), dp(32));
        root.setBackgroundColor(Color.rgb(16,17,20));

        TextView title = text(getString(R.string.about_title), 30, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout languages = new LinearLayout(this);
        languages.setOrientation(LinearLayout.HORIZONTAL);
        languages.setGravity(Gravity.CENTER);
        Button zh = languageButton(getString(R.string.language_chinese));
        Button en = languageButton(getString(R.string.language_english));
        zh.setOnClickListener(v -> { LanguageManager.setMode(this, LanguageManager.ZH); recreate(); });
        en.setOnClickListener(v -> { LanguageManager.setMode(this, LanguageManager.EN); recreate(); });
        languages.addView(zh, new LinearLayout.LayoutParams(dp(220), dp(60)));
        SpaceHolder.add(languages, dp(12));
        languages.addView(en, new LinearLayout.LayoutParams(dp(220), dp(60)));
        root.addView(languages, new LinearLayout.LayoutParams(-1, dp(76)));

        TextView content = text(getString(R.string.about_message), 17, false);
        content.setGravity(Gravity.LEFT | Gravity.TOP);
        content.setLineSpacing(0f, 1.18f);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.addView(content, new LinearLayout.LayoutParams(dp(650), -2));

        Button back = languageButton(getString(R.string.back));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(220), dp(60)));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        zh.requestFocus();
    }

    private Button languageButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setMinHeight(dp(60));
        return b;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private static final class SpaceHolder {
        static void add(LinearLayout parent, int width) {
            android.view.View space = new android.view.View(parent.getContext());
            parent.addView(space, new LinearLayout.LayoutParams(width, 1));
        }
    }
}
