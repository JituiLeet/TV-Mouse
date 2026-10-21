package com.jituileet.tvmouse;

import android.content.Context;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Persistent shortcut definitions shared by SettingsActivity and the accessibility service. */
public final class ShortcutConfig {
    public static final String OPEN_SETTINGS = "open_settings";
    public static final String ENABLE_SCROLL = "enable_scroll";
    public static final String TOGGLE_VISIBILITY = "toggle_visibility";
    public static final String COMBO = "combo";
    public static final String HOLD = "hold";
    private static final String PREF = "shortcut_settings";

    public static final class Config {
        public String mode;
        public int[] keys;
        public int holdMs;
        Config(String m, int[] k, int h) { mode=m; keys=k; holdMs=h; }
    }

    private ShortcutConfig() {}
    public static Config get(Context c, String action) {
        String defaultKeys = OPEN_SETTINGS.equals(action)
                ? KeyEvent.KEYCODE_VOLUME_UP + "," + KeyEvent.KEYCODE_MENU
                : ENABLE_SCROLL.equals(action)
                ? KeyEvent.KEYCODE_VOLUME_DOWN + "," + KeyEvent.KEYCODE_MENU
                : KeyEvent.KEYCODE_VOLUME_UP + "," + KeyEvent.KEYCODE_VOLUME_DOWN;
        String keysText = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(action+"_keys", defaultKeys);
        String mode = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(action+"_mode", COMBO);
        int hold = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(action+"_hold", 1200);
        String[] parts=keysText.split(",");
        List<Integer> list=new ArrayList<>();
        for(String part:parts) try { int k=Integer.parseInt(part.trim()); if(!list.contains(k))list.add(k); } catch(Exception ignored) {}
        int[] keys=new int[list.size()];
        for(int i=0;i<list.size();i++)keys[i]=list.get(i);
        if(keys.length==0) keys=new int[]{KeyEvent.KEYCODE_MENU};
        return new Config(mode,keys,Math.max(500,Math.min(10000,hold)));
    }
    public static void save(Context c,String action,String mode,int[] keys,int holdMs) {
        if(keys==null||keys.length==0)return;
        StringBuilder b=new StringBuilder();
        for(int k:keys){if(b.length()>0)b.append(',');b.append(k);}
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(action+"_mode",mode).putString(action+"_keys",b.toString())
                .putInt(action+"_hold",Math.max(500,Math.min(10000,holdMs))).apply();
    }
    public static String keyName(int key) {
        String s=KeyEvent.keyCodeToString(key);
        if(s.startsWith("KEYCODE_"))s=s.substring(8);
        return s.replace('_',' ');
    }
    public static String describe(Context context, Config c) {
        StringBuilder b=new StringBuilder();
        if(HOLD.equals(c.mode)) return context.getString(R.string.shortcut_hold_description,
                keyName(c.keys[0]), Math.max(1, Math.round(c.holdMs/1000f)));
        for(int k:c.keys){if(b.length()>0)b.append(" + ");b.append(keyName(k));}
        return b.toString();
    }
}
