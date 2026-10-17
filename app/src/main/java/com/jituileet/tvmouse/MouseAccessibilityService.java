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
    private int volumeKeyHeld = 0;
    private boolean menuHeld, comboTriggered;
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

        // Only watch the Back key while Fixed Scroll is active. Holding it for
        // three seconds exits that mode; otherwise Back is left to the system.
        if (k == KeyEvent.KEYCODE_BACK) {
            if (down && e.getRepeatCount() == 0 && ms.isScroll()) {
                backKeyTracking = true;
                if (backLongPress != null) handler.removeCallbacks(backLongPress);
                backLongPress = new Runnable() {
                    @Override public void run() {
                        MouseService current = ServiceRegistry.get();
                        if (current != null && current.isScroll()) current.setScroll(false);
                    }
                };
                handler.postDelayed(backLongPress, 3000);
            }
            if (backKeyTracking) {
                if (up) {
                    if (backLongPress != null) handler.removeCallbacks(backLongPress);
                    backLongPress = null;
                    backKeyTracking = false;
                }
                return true;
            }
            return false;
        }

        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_MENU) {
            if (down) {
                if (k == KeyEvent.KEYCODE_MENU) menuHeld = true;
                else volumeKeyHeld = k;
                if (menuHeld && volumeKeyHeld != 0 && !comboTriggered) {
                    comboTriggered = true;
                    if (volumeKeyHeld == KeyEvent.KEYCODE_VOLUME_UP) ms.showTvPanel();
                    else if (volumeKeyHeld == KeyEvent.KEYCODE_VOLUME_DOWN) ms.setScroll(!ms.isScroll());
                }
                if (menuHeld && volumeKeyHeld != 0) return true;
            } else if (up) {
                boolean combo = menuHeld && volumeKeyHeld != 0;
                if (k == KeyEvent.KEYCODE_MENU) menuHeld = false;
                else if (volumeKeyHeld == k) volumeKeyHeld = 0;
                if (!menuHeld || volumeKeyHeld == 0) comboTriggered = false;
                if (combo) return true;
            }
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
                int size=(int)(42*d+0.5f);
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
            File f=fixed
                    ? (AppearanceManager.isFixedShared(owner) ? null : AppearanceManager.selectedFixedFile(owner))
                    : AppearanceManager.selectedFile(owner);
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
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        stopMove(0);cancelLongPress();
        if (backLongPress != null) handler.removeCallbacks(backLongPress);
        backLongPress = null;
        removePointerInternal();
        MouseService ms=ServiceRegistry.get();
        if(ms!=null) ms.restoreFallbackPointer();
        instance=null;super.onDestroy();
    }
}