package com.jituileet.tvmouse;

import android.content.Context;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Persistent shortcut definitions shared by settings UI and accessibility service. */
public final class ShortcutConfig {
    public static final String OPEN_SETTINGS = "open_settings";
    public static final String ENABLE_SCROLL = "enable_scroll";
    public static final String HIDE_POINTER = "hide_pointer";
    public static final String CLOSE_SCROLL = "close_scroll";
    private static final String PREFS = "shortcut_settings";
    private ShortcutConfig() {}

    public static final class Config {
        public String mode; // combo or hold
        public int[] keys;
        public long duration;
        Config(String m, int[] k, long d) { mode=m; keys=k; duration=d; }
    }

    public static Config get(Context c, String action) {
        String fallback;
        if (OPEN_SETTINGS.equals(action)) fallback="combo:24,82"; // Volume Up + Menu
        else if (ENABLE_SCROLL.equals(action)) fallback="combo:25,82"; // Volume Down + Menu
        else if (HIDE_POINTER.equals(action)) fallback="combo:0"; // unassigned
        else if (CLOSE_SCROLL.equals(action)) fallback="hold:4:3000"; // Back 3 seconds
        else fallback="combo:0";
        String raw=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(action,fallback);
        try {
            String[] p=raw.split(":");
            if ("combo".equals(p[0])) {
                String[] ks=p[1].split(","); int[] keys=new int[ks.length];
                for(int i=0;i<ks.length;i++)keys[i]=Integer.parseInt(ks[i]);
                return new Config("combo",keys,0);
            }
            if("hold".equals(p[0]))return new Config("hold",new int[]{Integer.parseInt(p[1])},Math.max(1000,Long.parseLong(p[2])));
        } catch(Exception ignored) {}
        return new Config("combo",new int[]{0},0);
    }
    /** Restore all user-configurable shortcuts to their built-in defaults. */
    public static void resetDefaults(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(OPEN_SETTINGS)
            .remove(ENABLE_SCROLL)
            .remove(HIDE_POINTER)
            .remove(CLOSE_SCROLL)
            .apply();
    }

    public static void saveCombo(Context c,String action,int[] keys) {
        StringBuilder b=new StringBuilder("combo:");
        for(int i=0;i<keys.length;i++){if(i>0)b.append(',');b.append(keys[i]);}
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(action,b.toString()).apply();
    }
    public static void saveHold(Context c,String action,int key,long ms) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(action,"hold:"+key+":"+Math.max(1000,ms)).apply();
    }
    public static String keyName(Context context, int key) {
        if (key == 0) return context.getString(R.string.shortcut_unassigned);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
            case KeyEvent.KEYCODE_DPAD_CENTER: return context.getString(R.string.shortcut_key_select);
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_BACK: return context.getString(R.string.shortcut_key_back);
            case KeyEvent.KEYCODE_MENU: return context.getString(R.string.shortcut_key_menu);
            case KeyEvent.KEYCODE_VOLUME_UP: return context.getString(R.string.shortcut_key_volume_up);
            case KeyEvent.KEYCODE_VOLUME_DOWN: return context.getString(R.string.shortcut_key_volume_down);
            case KeyEvent.KEYCODE_VOLUME_MUTE: return context.getString(R.string.shortcut_key_mute);
            case KeyEvent.KEYCODE_HOME: return context.getString(R.string.shortcut_key_home);
            case KeyEvent.KEYCODE_INFO: return context.getString(R.string.shortcut_key_info);
            case KeyEvent.KEYCODE_CHANNEL_UP: return context.getString(R.string.shortcut_key_channel_up);
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return context.getString(R.string.shortcut_key_channel_down);
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return context.getString(R.string.shortcut_key_play_pause);
            default:
                try { return KeyEvent.keyCodeToString(key).replace("KEYCODE_", ""); }
                catch (Exception e) { return context.getString(R.string.shortcut_key_number, key); }
        }
    }

    public static String summary(Context c, String action) {
        Config cfg = get(c, action);
        if ("hold".equals(cfg.mode)) return keyName(c, cfg.keys[0]) + " · " + cfg.duration + " ms";
        if (cfg.keys.length == 1 && cfg.keys[0] == 0) return c.getString(R.string.shortcut_unassigned);
        List<String> names = new ArrayList<>();
        for (int k : cfg.keys) if (k != 0) names.add(keyName(c, k));
        return names.isEmpty() ? c.getString(R.string.shortcut_unassigned) : join(names, " + ");
    }
    private static String join(List<String> a,String sep){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(sep);b.append(s);}return b.toString();}
}
