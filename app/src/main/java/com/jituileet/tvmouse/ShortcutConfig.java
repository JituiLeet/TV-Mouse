package com.jituileet.tvmouse;

import android.content.Context;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Persistent, user-configurable key triggers for opening settings and enabling fixed scroll. */
public final class ShortcutConfig {
    private ShortcutConfig() {}
    private static final String PREF="shortcut_settings";
    public static final int COMBINATION=0, LONG_PRESS=1;
    public static int mode(Context c,String action){return c.getSharedPreferences(PREF,0).getInt(action+"_mode",COMBINATION);}
    public static int holdKey(Context c,String action){return c.getSharedPreferences(PREF,0).getInt(action+"_hold_key",KeyEvent.KEYCODE_MENU);}
    public static int holdMs(Context c,String action){return c.getSharedPreferences(PREF,0).getInt(action+"_hold_ms",1000);}
    public static List<Integer> keys(Context c,String action){
        String def="settings".equals(action)?"24,82":"25,82";
        String raw=c.getSharedPreferences(PREF,0).getString(action+"_keys",def);
        List<Integer> out=new ArrayList<>();
        for(String part:raw.split(","))try{out.add(Integer.parseInt(part));}catch(Exception ignored){}
        return out;
    }
    public static void save(Context c,String action,int mode,List<Integer> keys,int holdKey,int holdMs){
        StringBuilder b=new StringBuilder();for(Integer key:keys){if(b.length()>0)b.append(',');b.append(key);}
        c.getSharedPreferences(PREF,0).edit().putInt(action+"_mode",mode).putString(action+"_keys",b.toString())
            .putInt(action+"_hold_key",holdKey).putInt(action+"_hold_ms",Math.max(300,Math.min(5000,holdMs))).apply();
    }
    public static String keyName(int k){
        switch(k){case KeyEvent.KEYCODE_VOLUME_UP:return "音量加 / Volume Up";case KeyEvent.KEYCODE_VOLUME_DOWN:return "音量减 / Volume Down";case KeyEvent.KEYCODE_MENU:return "菜单 / Menu";case KeyEvent.KEYCODE_BACK:return "返回 / Back";case KeyEvent.KEYCODE_DPAD_CENTER:return "确定 / OK";case KeyEvent.KEYCODE_ENTER:return "Enter";case KeyEvent.KEYCODE_DPAD_UP:return "方向上 / Up";case KeyEvent.KEYCODE_DPAD_DOWN:return "方向下 / Down";case KeyEvent.KEYCODE_DPAD_LEFT:return "方向左 / Left";case KeyEvent.KEYCODE_DPAD_RIGHT:return "方向右 / Right";case KeyEvent.KEYCODE_HOME:return "主页 / Home";case KeyEvent.KEYCODE_INFO:return "信息 / Info";default:return "KeyCode "+k;}
    }
}
