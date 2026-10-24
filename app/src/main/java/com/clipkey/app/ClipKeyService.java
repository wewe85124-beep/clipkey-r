package com.clipkey.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class ClipKeyService extends InputMethodService {

    // Change this number to keep more or fewer copied texts
    private static final int MAX_ITEMS = 100;

    private static final int BG = 0xFF202124;
    private static final int KEY = 0xFF3C4043;
    private static final int KEY_USED = 0xFF55606E;
    private static final int SPECIAL = 0xFF5F6368;
    private static final int ACCENT = 0xFF8AB4F8;

    // items.get(0) = first copied (number 1), last item = newest
    private final ArrayList<String> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClipboardManager cm;
    private ClipboardManager.OnPrimaryClipChangedListener listener;
    private SharedPreferences prefs;
    private String lastClip = "";

    private LinearLayout root;
    private FrameLayout body;
    private boolean shift = false;
    private boolean symbols = false;
    private boolean showClips = false;

    // remembered place in the clipboard list
    private int savedScroll = -1;   // -1 = not set yet, go to the bottom
    private int lastUsed = -1;      // index of the last item you tapped

    private final Runnable repeatDelete = new Runnable() {
        @Override
        public void run() {
            deleteOne();
            handler.postDelayed(this, 60);
        }
    };

    // ---------- lifecycle ----------

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("clips2", MODE_PRIVATE);
        load();
        cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        listener = () -> {
            String t = readClip();
            if (t != null) {
                lastClip = t;
                addClip(t);
            }
        };
        cm.addPrimaryClipChangedListener(listener);
    }

    @Override
    public void onDestroy() {
        if (cm != null && listener != null) {
            cm.removePrimaryClipChangedListener(listener);
        }
        handler.removeCallbacks(repeatDelete);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        shift = false;
        if (!isPasswordField(info)) {
            String t = readClip();
            if (t != null && !t.equals(lastClip)) {
                lastClip = t;
                addClip(t);
            }
        }
        if (root != null) render();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        handler.removeCallbacks(repeatDelete);
        super.onFinishInputView(finishingInput);
    }

    // ---------- clipboard storage ----------

    private String readClip() {
        try {
            ClipData d = cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) {
                CharSequence t = d.getItemAt(0).coerceToText(this);
                if (t != null) return t.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isPasswordField(EditorInfo ei) {
        if (ei == null) return false;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            return var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        if (cls == InputType.TYPE_CLASS_NUMBER) {
            return var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        return false;
    }

    private void addClip(String t) {
        if (t.trim().isEmpty()) return;
        // ignore the same text arriving twice in a row (double signal)
        if (!items.isEmpty() && items.get(items.size() - 1).equals(t)) return;
        items.add(t);
        while (items.size() > MAX_ITEMS) {
            items.remove(0);
            if (lastUsed >= 0) lastUsed--;
        }
        save();
        if (root != null && showClips) render();
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (String s : items) a.put(s);
        prefs.edit()
                .putString("items", a.toString())
                .putString("last", lastClip)
                .apply();
    }

    private void load() {
        try {
            JSONArray a = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        lastClip = prefs.getString("last", "");
    }

    // ---------- typing helpers ----------

    private void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (!TextUtils.isEmpty(sel)) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void pressEnter() {
        if (!sendDefaultEditorAction(true)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            }
        }
    }

    // ---------- UI building ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void render() {
        root.removeAllViews();
        root.addView(topBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        if (showClips) showClipList();
        else showKeys();
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(2), dp(4), 0);

        bar.addView(tab("Keys", !showClips, v -> {
            showClips = false;
            render();
        }));
        bar.addView(tab("Clipboard (" + items.size() + ")", showClips, v -> {
            showClips = true;
            render();
        }));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(spacer);

        if (showClips && !items.isEmpty()) {
            bar.addView(tab("Clear all", false, v -> {
                items.clear();
                savedScroll = -1;
                lastUsed = -1;
                save();
                render();
            }));
        }
        return bar;
    }

    private TextView tab(String label, boolean active, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setTextColor(active ? ACCENT : Color.WHITE);
        t.setTypeface(null, active ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setOnClickListener(click);
        return t;
    }

    private Button key(String label, float weight, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), weight);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        b.setLayoutParams(lp);
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.setAlpha(0.6f);
            else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) v.setAlpha(1f);
            return false;
        });
        return b;
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), weight));
        return v;
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private LinearLayout charRow(String chars, boolean pad) {
        LinearLayout r = newRow();
        if (pad) r.addView(spacer(0.5f));
        for (char c : chars.toCharArray()) {
            final String label = (!symbols && shift)
                    ? String.valueOf(c).toUpperCase() : String.valueOf(c);
            Button b = key(label, 1f, KEY);
            b.setOnClickListener(v -> {
                type(label);
                if (shift && !symbols) {
                    shift = false;
                    showKeys();
                }
            });
            r.addView(b);
        }
        if (pad) r.addView(spacer(0.5f));
        return r;
    }

    private void showKeys() {
        body.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        col.addView(charRow("1234567890", false));
        if (symbols) {
            col.addView(charRow("@#$%&-+()/", false));
        } else {
            col.addView(charRow("qwertyuiop", false));
        }
        if (symbols) {
            col.addView(charRow("*\"':;!?_=~", false));
        } else {
            col.addView(charRow("asdfghjkl", true));
        }

        LinearLayout r4 = newRow();
        if (symbols) {
            r4.addView(spacer(1.5f));
            for (char c : "<>[]{}|^".toCharArray()) {
                final String s = String.valueOf(c);
                Button b = key(s, 1f, KEY);
                b.setOnClickListener(v -> type(s));
                r4.addView(b);
            }
        } else {
            Button sh = key("Aa", 1.5f, shift ? ACCENT : SPECIAL);
            sh.setOnClickListener(v -> {
                shift = !shift;
                showKeys();
            });
            r4.addView(sh);
            for (char c : "zxcvbnm".toCharArray()) {
                final String label = shift
                        ? String.valueOf(c).toUpperCase() : String.valueOf(c);
                Button b = key(label, 1f, KEY);
                b.setOnClickListener(v -> {
                    type(label);
                    if (shift) {
                        shift = false;
                        showKeys();
                    }
                });
                r4.addView(b);
            }
        }
        Button del = key("DEL", 1.5f, SPECIAL);
        del.setOnTouchListener((v, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                v.setAlpha(0.6f);
                deleteOne();
                handler.postDelayed(repeatDelete, 400);
                return true;
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                handler.removeCallbacks(repeatDelete);
                return true;
            }
            return false;
        });
        r4.addView(del);
        col.addView(r4);

        LinearLayout r5 = newRow();
        Button mode = key(symbols ? "ABC" : "?123", 1.5f, SPECIAL);
        mode.setOnClickListener(v -> {
            symbols = !symbols;
            showKeys();
        });
        r5.addView(mode);

        Button comma = key(",", 1f, KEY);
        comma.setOnClickListener(v -> type(","));
        r5.addView(comma);

        Button space = key("space", 4f, KEY);
        space.setTextSize(14);
        space.setOnClickListener(v -> type(" "));
        r5.addView(space);

        Button dot = key(".", 1f, KEY);
        dot.setOnClickListener(v -> type("."));
        r5.addView(dot);

        Button enter = key("Enter", 1.5f, ACCENT);
        enter.setTextSize(14);
        enter.setTextColor(Color.BLACK);
        enter.setOnClickListener(v -> pressEnter());
        r5.addView(enter);
        col.addView(r5);

        body.addView(col);
    }

    private GradientDrawable rowBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        return g;
    }

    private void showClipList() {
        body.removeAllViews();

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing copied yet.\nCopy some text and it will appear here.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            body.addView(empty, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        final ScrollView sv = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4), 0, dp(4), dp(4));

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final String text = items.get(i);

            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setMinimumHeight(dp(46));
            row.setBackground(rowBg(i == lastUsed ? KEY_USED : KEY));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(rlp);

            // number in the left corner: 1 = first copied
            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextColor(ACCENT);
            num.setTextSize(15);
            num.setTypeface(null, android.graphics.Typeface.BOLD);
            num.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            num.setPadding(0, dp(8), 0, 0);
            num.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(num);

            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(15);
            tv.setMaxLines(2);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(0, dp(8), dp(4), dp(8));
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tv);

            TextView del = new TextView(this);
            del.setText("X");
            del.setTextColor(Color.LTGRAY);
            del.setTextSize(16);
            del.setGravity(Gravity.CENTER);
            del.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
            del.setOnClickListener(v -> {
                if (index < items.size()) items.remove(index);
                if (lastUsed == index) lastUsed = -1;
                else if (lastUsed > index) lastUsed--;
                save();
                render();
            });
            row.addView(del);

            row.setOnClickListener(v -> {
                type(text);
                // remember this place and mark it, without redrawing the list
                if (lastUsed >= 0 && lastUsed < list.getChildCount()) {
                    list.getChildAt(lastUsed).setBackground(rowBg(KEY));
                }
                lastUsed = index;
                row.setBackground(rowBg(KEY_USED));
                savedScroll = sv.getScrollY();
            });
            list.addView(row);
        }

        sv.addView(list);
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // go back to the remembered place (or the bottom the very first time)
        sv.post(() -> {
            if (savedScroll >= 0) sv.scrollTo(0, savedScroll);
            else sv.fullScroll(View.FOCUS_DOWN);
            // keep the position up to date while you scroll by hand
            sv.setOnScrollChangeListener((v, x, y, ox, oy) -> savedScroll = y);
        });
    }
}
