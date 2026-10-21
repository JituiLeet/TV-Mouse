package com.jituileet.tvmouse;

import android.accessibilityservice.*;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import java.io.File;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;

public class MouseAccessibilityService extends AccessibilityService {
    private static MouseAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int heldKey = 0;
    private long centerDownAt;
    private boolean longPressSent;
    private Runnable repeat;
    private Runnable longPress;
    private Runnable backLongPress;
    private boolean backKeyTracking;
    private boolean backLongPressTriggered;
    private int volumeKeyHeld = 0;
    private boolean menuHeld, comboTriggered;
    private WindowManager pointerWm;
    private ImageView pointerView;
    private WindowManager.LayoutParams pointerParams;
    private MouseService pointerOwner;
    private final Set<Integer> pressedShortcutKeys = new HashSet<>();
    private final Map<String, Boolean> comboFired = new HashMap<>();
    private final Map<String, Runnable> shortcutHolds = new HashMap<>();

    @Override protected void onServiceConnected() {
        instance = this;
        MouseService ms = ServiceRegistry.get();
        if (ms != null) {
            try {
                ms.refreshPointerWindowType();
            } catch (RuntimeException ignored) {
                // Never let pointer-window setup crash the accessibility service.
            }
            showPointerFromMouseService(ms);
        }
        setKeyFilteringEnabled(ms != null);
    }

    /** Enable key filtering only while the mouse pointer service is running. */
    public static void setKeyFilteringEnabled(boolean enabled) {
        MouseAccessibilityService s = instance;
        if (s == null) return;
        try {
            AccessibilityServiceInfo info = s.getServiceInfo();
            if (info == null) return;
            if (enabled) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            s.setServiceInfo(info);
        } catch (RuntimeException ignored) {
            // A vendor accessibility implementation must not crash the service
            // merely because key-filter flags cannot be updated.
        }
    }

    @Override public boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;
        MouseService ms = ServiceRegistry.get();

        // Fail safe: if the pointer service is gone, do not consume remote keys.
        if (ms == null) {
            pressedShortcutKeys.clear();
            cancelShortcutHolds();
            return false;
        }

        try {
            // Back is reserved only for closing Fixed Scroll after a 3-second hold.
            // Short Back presses always pass through to Android/launcher.
            if (k == KeyEvent.KEYCODE_BACK && (ms.isScroll() || backKeyTracking)) {
                if (down && e.getRepeatCount() == 0 && ms.isScroll()) {
                    backKeyTracking = true;
                    backLongPressTriggered = false;
                    if (backLongPress != null) handler.removeCallbacks(backLongPress);
                    backLongPress = new Runnable() {
                        @Override public void run() {
                            MouseService current = ServiceRegistry.get();
                            if (backKeyTracking && current != null && current.isScroll()) {
                                backLongPressTriggered = true;
                                current.setScroll(false);
                            }
                        }
                    };
                    handler.postDelayed(backLongPress, 3000);
                    return false;
                }
                if (up && backKeyTracking) {
                    if (backLongPress != null) handler.removeCallbacks(backLongPress);
                    backLongPress = null;
                    backKeyTracking = false;
                    boolean consume = backLongPressTriggered;
                    backLongPressTriggered = false;
                    return consume;
                }
                return false;
            }

            if (down) pressedShortcutKeys.add(k);
            boolean shortcutConsumed = processConfiguredShortcuts(k, down, up, e.getRepeatCount());
            if (up) pressedShortcutKeys.remove(k);

            // Menu is consumed only when it participates in a configured combo.
            // A lone Menu press never opens settings and never triggers an action.
            if (shortcutConsumed) return true;
            if (isConfiguredComboKey(k) && k == KeyEvent.KEYCODE_MENU) return true;

        } catch (Throwable failure) {
            // A malformed shortcut or vendor-specific key event must never leave
            // the remote control locked. Disable filtering until the service reconnects.
            pressedShortcutKeys.clear();
            cancelShortcutHolds();
            setKeyFilteringEnabled(false);
            return false;
        }

        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) { startMove(k); return true; }
            if (up) { stopMove(k); return true; }
        }

        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (down && e.getRepeatCount() == 0) {
                centerDownAt = System.currentTimeMillis(); longPressSent = false; cancelLongPress();
                longPress = new Runnable() { @Override public void run() {
                    if (centerDownAt != 0) { longPressSent = true; performLongClickAt(currentX(), currentY()); }
                }};
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            if (up) {
                cancelLongPress();
                if (!longPressSent) performClickAt(currentX(), currentY());
                centerDownAt = 0; longPressSent = false; return true;
            }
            return true;
        }
        return false;
    }


    private boolean processConfiguredShortcuts(int key, boolean down, boolean up, int repeatCount) {
        String[] actions = new String[]{ShortcutConfig.OPEN_SETTINGS, ShortcutConfig.ENABLE_SCROLL,
                ShortcutConfig.TOGGLE_VISIBILITY};
        boolean consumed = false;
        for (final String action : actions) {
            ShortcutConfig.Config cfg = ShortcutConfig.get(this, action);
            boolean contains = contains(cfg.keys, key);
            if (ShortcutConfig.HOLD.equals(cfg.mode)) {
                if (contains && down && repeatCount == 0) {
                    cancelShortcutHold(action);
                    Runnable task = new Runnable() {
                        @Override public void run() {
                            if (!pressedShortcutKeys.contains(cfg.keys[0])) return;
                            runShortcutAction(action);
                        }
                    };
                    shortcutHolds.put(action, task);
                    handler.postDelayed(task, cfg.holdMs);
                    consumed = true;
                } else if (contains && (up || down)) {
                    if (up) cancelShortcutHold(action);
                    consumed = true;
                }
            } else {
                boolean all = cfg.keys.length > 0;
                for (int required : cfg.keys) if (!pressedShortcutKeys.contains(required)) all = false;
                if (all && down && !Boolean.TRUE.equals(comboFired.get(action))) {
                    comboFired.put(action, true);
                    runShortcutAction(action);
                    consumed = true;
                }
                if (contains && up) {
                    comboFired.put(action, false);
                    // If this key is part of the combo, consume its release too.
                    if (action.equals(ShortcutConfig.OPEN_SETTINGS) ||
                        action.equals(ShortcutConfig.ENABLE_SCROLL) ||
                        action.equals(ShortcutConfig.TOGGLE_VISIBILITY)) consumed = true;
                }
            }
        }
        return consumed;
    }

    private void runShortcutAction(String action) {
        MouseService ms = ServiceRegistry.get();
        if (ms == null) return;
        if (ShortcutConfig.OPEN_SETTINGS.equals(action)) {
            ms.showTvPanel();
        } else if (ShortcutConfig.ENABLE_SCROLL.equals(action)) {
            if (!ms.isScroll()) ms.setScroll(true);
        } else if (ShortcutConfig.TOGGLE_VISIBILITY.equals(action)) {
            ms.toggleHidden();
        }
    }

    private boolean isConfiguredComboKey(int key) {
        String[] actions = new String[]{ShortcutConfig.OPEN_SETTINGS, ShortcutConfig.ENABLE_SCROLL,
                ShortcutConfig.TOGGLE_VISIBILITY};
        for (String action : actions) {
            ShortcutConfig.Config cfg = ShortcutConfig.get(this, action);
            if (ShortcutConfig.COMBO.equals(cfg.mode) && contains(cfg.keys, key)) return true;
        }
        return false;
    }

    private boolean contains(int[] keys, int key) {
        if (keys == null) return false;
        for (int k : keys) if (k == key) return true;
        return false;
    }

    private void cancelShortcutHold(String action) {
        Runnable r = shortcutHolds.remove(action);
        if (r != null) handler.removeCallbacks(r);
    }

    private void cancelShortcutHolds() {
        for (Runnable r : shortcutHolds.values()) handler.removeCallbacks(r);
        shortcutHolds.clear();
    }

    private int currentX() { MouseService s=ServiceRegistry.get(); return s==null?1:s.getPointerX(); }
    private int currentY() { MouseService s=ServiceRegistry.get(); return s==null?1:s.getPointerY(); }

    private void startMove(final int key) {
        stopMove(0); heldKey=key;
        moveOnce(key, 10f * getResources().getDisplayMetrics().density);
        repeat = new Runnable() { @Override public void run() {
            if (heldKey != key) return;
            MouseService s=ServiceRegistry.get();
            if (s != null) {
                float speed=s.getMoveSpeedPx();
                if (s.getMoveHoldTime()>800) speed*=1.35f;
                if (s.getMoveHoldTime()>1800) speed*=1.65f;
                float dx=0,dy=0;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)dx=-speed;
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)dx=speed;
                if(key==KeyEvent.KEYCODE_DPAD_UP)dy=-speed;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN)dy=speed;
                s.move(dx,dy);
            }
            // Do not start a new accessibility gesture while the previous
            // one is still running. Overlapping dispatchGesture() calls cancel
            // the previous gesture and can make scrolling appear completely
            // stuck. A short completed gesture is repeated continuously.
            handler.postDelayed(this, sIsScrolling()?130:28);
        }};
        handler.postDelayed(repeat, sIsScrolling()?130:100);
    }

    private boolean sIsScrolling() {
        MouseService s=ServiceRegistry.get();
        return s != null && s.isScroll();
    }

    private void moveOnce(int key,float speed) {
        MouseService s=ServiceRegistry.get(); if(s==null)return;
        float scale=s.getMoveSpeedPx()/(16f*getResources().getDisplayMetrics().density);
        speed*=Math.max(0.65f,Math.min(2.0f,scale));
        float dx=0,dy=0;
        if(key==KeyEvent.KEYCODE_DPAD_LEFT)dx=-speed;
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT)dx=speed;
        if(key==KeyEvent.KEYCODE_DPAD_UP)dy=-speed;
        if(key==KeyEvent.KEYCODE_DPAD_DOWN)dy=speed;
        s.move(dx,dy); s.beginMoveHold();
    }

    private void stopMove(int key) {
        if(key!=0 && heldKey!=key)return;
        heldKey=0; if(repeat!=null)handler.removeCallbacks(repeat); repeat=null;
        MouseService s=ServiceRegistry.get();if(s!=null)s.endMoveHold();
    }

    private void cancelLongPress(){if(longPress!=null)handler.removeCallbacks(longPress);longPress=null;}

    public static void showPointerFromMouseService(MouseService owner){
        MouseAccessibilityService s=instance;
        if(s==null || owner==null) return;
        s.pointerOwner=owner;
        if(s.pointerView==null){
            try {
                s.pointerWm=(WindowManager)s.getSystemService(WINDOW_SERVICE);
                s.pointerView=new ImageView(s);
                s.pointerView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                float d=owner.getResources().getDisplayMetrics().density;
                int size=(int)(owner.getPointerSizeDp()*d+0.5f);
                s.pointerParams=new WindowManager.LayoutParams(size,size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
                s.pointerParams.gravity=Gravity.TOP|Gravity.LEFT;
                s.pointerParams.x=owner.getPointerX(); s.pointerParams.y=owner.getPointerY();
                s.pointerWm.addView(s.pointerView,s.pointerParams);
            } catch (RuntimeException e) {
                s.removePointerInternal();
                return;
            }
        }
        applyPointerAppearance(owner);
        updatePointerPosition(owner.getPointerX(),owner.getPointerY(),owner.isHidden());
    }

    public static void updatePointerPosition(int x,int y,boolean hidden){
        MouseAccessibilityService s=instance;
        if(s==null || s.pointerView==null || s.pointerWm==null || s.pointerParams==null) return;
        try {
            s.pointerParams.x=x; s.pointerParams.y=y;
            s.pointerView.setVisibility(hidden?View.GONE:View.VISIBLE);
            if(!hidden) s.pointerWm.updateViewLayout(s.pointerView,s.pointerParams);
        } catch (RuntimeException ignored) { s.removePointerInternal(); }
    }

    public static void applyPointerSize(MouseService owner){
        MouseAccessibilityService s=instance;
        if(s==null || s.pointerView==null || s.pointerWm==null || s.pointerParams==null || owner==null) return;
        try {
            int size=(int)(owner.getPointerSizeDp()*owner.getResources().getDisplayMetrics().density+0.5f);
            s.pointerParams.width=size; s.pointerParams.height=size;
            s.pointerWm.updateViewLayout(s.pointerView,s.pointerParams);
        } catch(RuntimeException ignored) {}
    }

    public static void applyPointerAppearance(MouseService owner){
        MouseAccessibilityService s=instance;
        if(s==null || s.pointerView==null || owner==null) return;
        try {
            boolean fixed=owner.isScroll();
            // Fixed-scroll mode has its own built-in cursor even when no custom
            // fixed appearance has been selected.
            s.pointerView.setImageResource(fixed ? R.drawable.ic_scroll_pointer : R.drawable.ic_mouse_pointer);
            // When fixed mode shares the normal appearance, keep the distinct
            // built-in scroll cursor visible instead of reapplying the normal image.
            File f = fixed ? AppearanceManager.selectedFixedFile(owner) : AppearanceManager.selectedFile(owner);
            if(f!=null){
                BitmapFactory.Options o=new BitmapFactory.Options(); o.inScaled=false;
                android.graphics.Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
                if(b!=null) s.pointerView.setImageBitmap(b);
            }
        } catch (RuntimeException ignored) {}
    }

    public static void hideAndRemovePointer(){
        if(instance!=null) instance.removePointerInternal();
    }

    private void removePointerInternal(){
        if(pointerView!=null && pointerWm!=null){try{pointerWm.removeView(pointerView);}catch(Exception ignored){}}
        pointerView=null; pointerParams=null; pointerWm=null; pointerOwner=null;
    }

    public static MouseAccessibilityService getInstance(){ return instance; }

    public static void performClickAt(int x,int y){
        if(instance==null)return;
        if(Build.VERSION.SDK_INT>=24){
            Path p=new Path();p.moveTo(Math.max(1,x),Math.max(1,y));
            GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,70)).build();
            instance.dispatchGesture(g,null,null);
        } else instance.clickNodeAt(x,y,false);
    }

    private void performLongClickAt(int x,int y){
        if(Build.VERSION.SDK_INT>=24){
            Path p=new Path();p.moveTo(Math.max(1,x),Math.max(1,y));
            GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,850)).build();
            dispatchGesture(g,null,null);
        } else clickNodeAt(x,y,true);
    }

    private void clickNodeAt(int x,int y,boolean longClick){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
        AccessibilityNodeInfo node=findNode(root,x,y);
        if(node!=null){
            boolean ok=false;
            if(longClick && Build.VERSION.SDK_INT>=21) ok=node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            if(!ok) ok=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            node.recycle();
        }
        root.recycle();
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo n,int x,int y){
        Rect r=new Rect();n.getBoundsInScreen(r); if(!r.contains(x,y))return null;
        for(int i=n.getChildCount()-1;i>=0;i--){
            AccessibilityNodeInfo c=n.getChild(i); if(c==null)continue;
            AccessibilityNodeInfo hit=findNode(c,x,y); if(hit!=null)return hit; c.recycle();
        }
        return AccessibilityNodeInfo.obtain(n);
    }

    public static void performScroll(float dx,float dy){
        if(instance==null)return;
        if(Build.VERSION.SDK_INT>=24){
            // Use a real, visible drag distance and let the gesture finish
            // before the next repeat. Starting another gesture too early
            // cancels the current one on Android.
            MouseService owner=ServiceRegistry.get();
            float cx=owner!=null?owner.getPointerX():500;
            float cy=owner!=null?owner.getPointerY():400;
            float distance=5.0f;
            Path p=new Path();
            p.moveTo(cx,cy);
            p.lineTo(cx-dx*distance,cy-dy*distance);
            GestureDescription g=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p,0,110))
                .build();
            instance.dispatchGesture(g,null,null);
        } else {
            // Android 4.4 has no dispatchGesture; use accessibility scroll
            // actions for immediate, repeatable scrolling.
            AccessibilityNodeInfo root=instance.getRootInActiveWindow();
            if(root==null)return;
            AccessibilityNodeInfo node=findScrollableNode(root);
            if(node!=null){
                int action;
                if(Math.abs(dy)>=Math.abs(dx))
                    action=dy>0?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                else
                    action=dx>0?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                node.performAction(action);
                node.recycle();
            }
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo n){
        if(n==null)return null;
        if(n.isScrollable())return AccessibilityNodeInfo.obtain(n);
        for(int i=n.getChildCount()-1;i>=0;i--){
            AccessibilityNodeInfo c=n.getChild(i);
            if(c==null)continue;
            AccessibilityNodeInfo hit=findScrollableNode(c);
            if(hit!=null){c.recycle();return hit;}
            c.recycle();
        }
        return null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){
        // Accessibility interruption must not leave global key filtering enabled.
        setKeyFilteringEnabled(false);
        pressedShortcutKeys.clear();
        cancelShortcutHolds();
        stopMove(0);
    }
    @Override public void onDestroy(){
        setKeyFilteringEnabled(false);
        pressedShortcutKeys.clear();
        cancelShortcutHolds();
        stopMove(0);cancelLongPress();
        if (backLongPress != null) handler.removeCallbacks(backLongPress);
        backLongPress = null;
        removePointerInternal();
        MouseService ms=ServiceRegistry.get();
        if(ms!=null) ms.restoreFallbackPointer();
        instance=null;super.onDestroy();
    }
}