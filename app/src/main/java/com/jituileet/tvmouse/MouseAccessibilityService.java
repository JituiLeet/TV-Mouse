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
import java.util.Set;

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
    private final Set<Integer> shortcutPressed = new HashSet<>();
    private final Set<Integer> shortcutConsumed = new HashSet<>();
    private int activeHoldKey;
    private String activeHoldAction;
    private boolean activeHoldTriggered;
    private Runnable configuredHoldRunnable;
    private WindowManager pointerWm;
    private ImageView pointerView;
    private WindowManager.LayoutParams pointerParams;
    private MouseService pointerOwner;

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
        AccessibilityServiceInfo info = s.getServiceInfo();
        if (info == null) return;
        if (enabled) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        s.setServiceInfo(info);
    }

    @Override public boolean onKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;
        MouseService ms = ServiceRegistry.get();

        // When the mouse service is not running, this accessibility service must
        // not handle any remote-control key.
        if (ms == null) return false;


        if (handleConfiguredShortcuts(e, ms)) return true;

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

    private static final String[] SHORTCUT_ACTIONS = new String[]{
        ShortcutConfig.OPEN_SETTINGS, ShortcutConfig.ENABLE_SCROLL,
        ShortcutConfig.HIDE_POINTER, ShortcutConfig.CLOSE_SCROLL
    };

    private boolean handleConfiguredShortcuts(KeyEvent e, MouseService ms) {
        final int key=e.getKeyCode();
        final boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        final boolean up=e.getAction()==KeyEvent.ACTION_UP;
        if(!down&&!up)return key==KeyEvent.KEYCODE_MENU;

        if(down && e.getRepeatCount()==0) {
            shortcutPressed.add(key);
            // A hold shortcut is activated only after its configured duration.
            for(String action:SHORTCUT_ACTIONS) {
                ShortcutConfig.Config cfg=ShortcutConfig.get(this,action);
                if("hold".equals(cfg.mode) && cfg.keys.length>0 && cfg.keys[0]==key) {
                    activeHoldKey=key;activeHoldAction=action;activeHoldTriggered=false;
                    if(configuredHoldRunnable!=null)handler.removeCallbacks(configuredHoldRunnable);
                    configuredHoldRunnable=()->{
                        if(activeHoldKey==key && shortcutPressed.contains(key)) {
                            activeHoldTriggered=runShortcutAction(activeHoldAction,ms);
                        }
                    };
                    handler.postDelayed(configuredHoldRunnable,cfg.duration);
                    // Keep Back short presses available to Android; consume its release
                    // only when the configured hold actually fired.
                    if(key!=KeyEvent.KEYCODE_BACK)return true;
                    return false;
                }
            }
            boolean comboMatch=false;
            for(String action:SHORTCUT_ACTIONS) {
                ShortcutConfig.Config cfg=ShortcutConfig.get(this,action);
                if(!"combo".equals(cfg.mode)||cfg.keys.length==1&&cfg.keys[0]==0)continue;
                boolean all=true;
                for(int required:cfg.keys)if(!shortcutPressed.contains(required)){all=false;break;}
                if(all){comboMatch=true;for(int required:cfg.keys)shortcutConsumed.add(required);runShortcutAction(action,ms);}
            }
            boolean isConfigured=false;
            for(String action:SHORTCUT_ACTIONS){
                ShortcutConfig.Config cfg=ShortcutConfig.get(this,action);
                for(int configured:cfg.keys)if(configured!=0&&configured==key)isConfigured=true;
            }
            // Consume both halves of configured shortcut keys so the original
            // key does not leak into volume, launcher, or D-pad handling.
            if(isConfigured && key!=KeyEvent.KEYCODE_BACK && key!=KeyEvent.KEYCODE_VOLUME_UP && key!=KeyEvent.KEYCODE_VOLUME_DOWN) shortcutConsumed.add(key);
            // Menu is consumed to prevent the TV launcher from opening its own menu.
            return key==KeyEvent.KEYCODE_MENU || comboMatch || (isConfigured && key!=KeyEvent.KEYCODE_BACK && key!=KeyEvent.KEYCODE_VOLUME_UP && key!=KeyEvent.KEYCODE_VOLUME_DOWN);
        }
        if(up) {
            boolean consume=shortcutConsumed.remove(key);
            if(activeHoldKey==key) {
                if(configuredHoldRunnable!=null)handler.removeCallbacks(configuredHoldRunnable);
                configuredHoldRunnable=null;
                boolean fired=activeHoldTriggered;
                String action=activeHoldAction;
                activeHoldKey=0;activeHoldAction=null;activeHoldTriggered=false;
                shortcutPressed.remove(key);
                if(fired)return true;
                return key==KeyEvent.KEYCODE_MENU ? true : (key==KeyEvent.KEYCODE_BACK ? false : true);
            }
            shortcutPressed.remove(key);
            if(key==KeyEvent.KEYCODE_MENU)return true;
            return consume;
        }
        if(down) {
            boolean configured=false;
            for(String action:SHORTCUT_ACTIONS) {
                ShortcutConfig.Config cfg=ShortcutConfig.get(this,action);
                for(int configuredKey:cfg.keys)if(configuredKey!=0&&configuredKey==key)configured=true;
            }
            return key==KeyEvent.KEYCODE_MENU || (configured && key!=KeyEvent.KEYCODE_BACK && key!=KeyEvent.KEYCODE_VOLUME_UP && key!=KeyEvent.KEYCODE_VOLUME_DOWN);
        }
        return key==KeyEvent.KEYCODE_MENU;
    }

    private boolean runShortcutAction(String action,MouseService ms) {
        if(ms==null||action==null)return false;
        if(ShortcutConfig.OPEN_SETTINGS.equals(action)){ms.showTvPanel();return true;}
        if(ShortcutConfig.ENABLE_SCROLL.equals(action)){if(!ms.isScroll())ms.setScroll(true);return true;}
        if(ShortcutConfig.HIDE_POINTER.equals(action)){ms.toggleHidden();return true;}
        if(ShortcutConfig.CLOSE_SCROLL.equals(action)){if(ms.isScroll())ms.setScroll(false);return true;}
        return false;
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
        // Fail open immediately if Android interrupts the accessibility service.
        try { setKeyFilteringEnabled(false); } catch (RuntimeException ignored) {}
        MouseService ms=ServiceRegistry.get();
        if(ms!=null)try { ms.stopSelf(); } catch (RuntimeException ignored) {}
    }
    @Override public void onDestroy(){
        stopMove(0);cancelLongPress();
        if (backLongPress != null) handler.removeCallbacks(backLongPress);
        if (configuredHoldRunnable != null) handler.removeCallbacks(configuredHoldRunnable);
        backLongPress = null; configuredHoldRunnable=null;
        shortcutPressed.clear(); shortcutConsumed.clear(); activeHoldKey=0; activeHoldAction=null; activeHoldTriggered=false;
        try { setKeyFilteringEnabled(false); } catch (RuntimeException ignored) {}
        removePointerInternal();
        MouseService ms=ServiceRegistry.get();
        if(ms!=null) {
            try { ms.restoreFallbackPointer(); } catch (RuntimeException ignored) {}
            // Fail open: if the accessibility service disconnects/crashes, stop the
            // companion mouse service so no stale shortcut filtering can trap the remote.
            try { ms.stopSelf(); } catch (RuntimeException ignored) {}
        }
        instance=null;super.onDestroy();
    }
}
