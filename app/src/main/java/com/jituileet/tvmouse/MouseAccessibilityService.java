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
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.graphics.Color;
import java.util.HashSet;
import java.util.List;
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
    private final Set<Integer> shortcutKeysDown = new HashSet<>();
    private boolean settingsShortcutTriggered, fixedShortcutTriggered;
    private Runnable settingsHoldRunnable, fixedHoldRunnable;
    private View hoverHighlight;
    private WindowManager.LayoutParams hoverParams;
    private AccessibilityNodeInfo hoveredNode;
    private long lastHoverFocusAt;
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
        try {
            AccessibilityServiceInfo info = s.getServiceInfo();
            if (info == null) return;
            if (enabled) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            // Interactive windows are retrieved through the service XML config;
            // do not mutate unrelated flags while toggling key filtering.
            s.setServiceInfo(info);
        } catch (RuntimeException ignored) {
            // Fail open if the platform rejects a runtime service-info update.
        }
    }

    @Override public boolean onKeyEvent(KeyEvent e) {
        try { return handleKeyEventSafely(e); }
        catch (RuntimeException ignored) {
            // Fail open: a malformed node/window state must not lock the remote.
            return false;
        }
    }

    private boolean handleKeyEventSafely(KeyEvent e) {
        int k=e.getKeyCode();
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN, up=e.getAction()==KeyEvent.ACTION_UP;
        MouseService ms=ServiceRegistry.get();
        if(ms==null) return false;

        // Back remains an ordinary Android Back key unless fixed-scroll is active.
        if(k==KeyEvent.KEYCODE_BACK && (ms.isScroll() ||
            !(ShortcutConfig.mode(this,"settings")==ShortcutConfig.LONG_PRESS && ShortcutConfig.holdKey(this,"settings")==k) &&
            !(ShortcutConfig.mode(this,"fixed")==ShortcutConfig.LONG_PRESS && ShortcutConfig.holdKey(this,"fixed")==k))){
            if(down && e.getRepeatCount()==0 && ms.isScroll()){
                backKeyTracking=true; backLongPressTriggered=false;
                if(backLongPress!=null)handler.removeCallbacks(backLongPress);
                backLongPress=()->{
                    MouseService current=ServiceRegistry.get();
                    if(backKeyTracking && current!=null && current.isScroll()){
                        backLongPressTriggered=true; current.setScroll(false);
                    }
                };
                handler.postDelayed(backLongPress,3000); return false;
            }
            if(up && backKeyTracking){
                if(backLongPress!=null)handler.removeCallbacks(backLongPress);
                backLongPress=null; backKeyTracking=false;
                boolean consume=backLongPressTriggered; backLongPressTriggered=false; return consume;
            }
            return false;
        }

        if(down && e.getRepeatCount()==0){
            shortcutKeysDown.add(k);
            checkCombinationShortcut(ms,"settings");
            checkCombinationShortcut(ms,"fixed");
            scheduleHoldShortcut(ms,"settings",k);
            scheduleHoldShortcut(ms,"fixed",k);
        }
        if(up){
            shortcutKeysDown.remove(k);
            if(settingsHoldRunnable!=null && ShortcutConfig.holdKey(this,"settings")==k){handler.removeCallbacks(settingsHoldRunnable);settingsHoldRunnable=null;}
            if(fixedHoldRunnable!=null && ShortcutConfig.holdKey(this,"fixed")==k){handler.removeCallbacks(fixedHoldRunnable);fixedHoldRunnable=null;}
            settingsShortcutTriggered=false; fixedShortcutTriggered=false;
        }
        // Menu is consumed while the pointer is running so the launcher cannot
        // treat a partial shortcut as a standalone Menu action. Configured keys
        // are consumed only as part of shortcut handling.
        if(k==KeyEvent.KEYCODE_MENU || isConfiguredShortcutKey(k)) return true;

        if(k==KeyEvent.KEYCODE_DPAD_UP || k==KeyEvent.KEYCODE_DPAD_DOWN ||
           k==KeyEvent.KEYCODE_DPAD_LEFT || k==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(down){startMove(k);return true;} if(up){stopMove(k);return true;}
        }
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){
            if(down && e.getRepeatCount()==0){
                centerDownAt=System.currentTimeMillis();longPressSent=false;cancelLongPress();
                longPress=()->{if(centerDownAt!=0){longPressSent=true;performLongClickAt(currentX(),currentY());}};
                handler.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());return true;
            }
            if(up){cancelLongPress();if(!longPressSent)performClickAt(currentX(),currentY());centerDownAt=0;longPressSent=false;return true;}
            return true;
        }
        return false;
    }

    private boolean isConfiguredShortcutKey(int key){
        for(String action:new String[]{"settings","fixed"}){
            if(ShortcutConfig.mode(this,action)==ShortcutConfig.LONG_PRESS && ShortcutConfig.holdKey(this,action)==key)return true;
            if(ShortcutConfig.keys(this,action).contains(key))return true;
        }
        return false;
    }

    private void checkCombinationShortcut(MouseService ms,String action){
        if(ShortcutConfig.mode(this,action)!=ShortcutConfig.COMBINATION)return;
        List<Integer> required=ShortcutConfig.keys(this,action);if(required.isEmpty())return;
        if(!shortcutKeysDown.containsAll(required))return;
        if("settings".equals(action)){
            if(!settingsShortcutTriggered){settingsShortcutTriggered=true;ms.showTvPanel();}
        }else if(!fixedShortcutTriggered && !ms.isScroll()){
            fixedShortcutTriggered=true;ms.setScroll(true);
        }
    }

    private void scheduleHoldShortcut(MouseService ms,String action,int key){
        if(ShortcutConfig.mode(this,action)!=ShortcutConfig.LONG_PRESS || ShortcutConfig.holdKey(this,action)!=key)return;
        final Runnable task;
        if("settings".equals(action)){
            if(settingsHoldRunnable!=null)handler.removeCallbacks(settingsHoldRunnable);
            task=()->{if(shortcutKeysDown.contains(key)){settingsShortcutTriggered=true;MouseService current=ServiceRegistry.get();if(current!=null)current.showTvPanel();}};
            settingsHoldRunnable=task;
        }else{
            if(fixedHoldRunnable!=null)handler.removeCallbacks(fixedHoldRunnable);
            task=()->{if(shortcutKeysDown.contains(key)){fixedShortcutTriggered=true;MouseService current=ServiceRegistry.get();if(current!=null&&!current.isScroll())current.setScroll(true);}};
            fixedHoldRunnable=task;
        }
        handler.postDelayed(task,ShortcutConfig.holdMs(this,action));
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
            s.pointerView.setImageResource(owner.isLinkHovered() ? R.drawable.ic_link_select : (fixed ? R.drawable.ic_scroll_pointer : R.drawable.ic_mouse_pointer));
            // When fixed mode shares the normal appearance, keep the distinct
            // built-in scroll cursor visible instead of reapplying the normal image.
            File f = owner.isLinkHovered() ? AppearanceManager.selectedLinkFile(owner) : (fixed ? AppearanceManager.selectedFixedFile(owner) : AppearanceManager.selectedFile(owner));
            if(f!=null){
                BitmapFactory.Options o=new BitmapFactory.Options(); o.inScaled=false;
                android.graphics.Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
                if(b!=null) s.pointerView.setImageBitmap(b);
            }
        } catch (RuntimeException ignored) {}
    }

    public static void updateHoverAt(int x,int y){
        MouseAccessibilityService s=instance;if(s==null)return;
        try{s.updateHoverInternal(x,y);}catch(RuntimeException ignored){s.clearHoverInternal();}
    }

    private void updateHoverInternal(int x,int y){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null){clearHoverInternal();return;}
        AccessibilityNodeInfo hit=findInteractiveNode(root,x,y);
        root.recycle();
        boolean interactive=hit!=null;
        MouseService owner=ServiceRegistry.get();
        if(owner!=null)owner.setLinkHovered(interactive);
        if(!interactive){if(hit!=null)hit.recycle();clearHoverInternal();return;}
        Rect bounds=new Rect();hit.getBoundsInScreen(bounds);
        if(hoveredNode!=null)try{hoveredNode.recycle();}catch(Exception ignored){}
        hoveredNode=AccessibilityNodeInfo.obtain(hit);hit.recycle();
        showHoverHighlight(bounds);
    }

    private AccessibilityNodeInfo findInteractiveNode(AccessibilityNodeInfo node,int x,int y){
        return findInteractiveNode(node,x,y,0);
    }
    private AccessibilityNodeInfo findInteractiveNode(AccessibilityNodeInfo node,int x,int y,int depth){
        if(node==null || depth>24)return null;
        Rect r=new Rect();node.getBoundsInScreen(r);if(!r.contains(x,y))return null;
        for(int i=node.getChildCount()-1;i>=0;i--){
            AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;
            AccessibilityNodeInfo found=findInteractiveNode(child,x,y,depth+1);
            child.recycle();if(found!=null)return found;
        }
        boolean interactive=node.isClickable() || node.isLongClickable() || node.isCheckable();
        if(interactive)return AccessibilityNodeInfo.obtain(node);
        return null;
    }

    private void showHoverHighlight(Rect r){
        if(r.width()<=0||r.height()<=0)return;
        try{
            if(pointerWm==null)pointerWm=(WindowManager)getSystemService(WINDOW_SERVICE);
            if(hoverHighlight==null){
                hoverHighlight=new View(this);hoverHighlight.setBackgroundResource(R.drawable.hover_highlight);
                hoverParams=new WindowManager.LayoutParams(Math.max(1,r.width()),Math.max(1,r.height()),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
                hoverParams.gravity=Gravity.TOP|Gravity.LEFT;hoverParams.x=r.left;hoverParams.y=r.top;
                pointerWm.addView(hoverHighlight,hoverParams);
            }else{
                hoverParams.width=Math.max(1,r.width());hoverParams.height=Math.max(1,r.height());hoverParams.x=r.left;hoverParams.y=r.top;
                hoverHighlight.setVisibility(View.VISIBLE);pointerWm.updateViewLayout(hoverHighlight,hoverParams);
            }
        }catch(RuntimeException ignored){removeHoverHighlight();}
    }

    private void clearHoverInternal(){
        MouseService owner=ServiceRegistry.get();if(owner!=null)owner.setLinkHovered(false);
        if(hoveredNode!=null){try{hoveredNode.recycle();}catch(Exception ignored){}hoveredNode=null;}
        if(hoverHighlight!=null)hoverHighlight.setVisibility(View.GONE);
    }

    private void removeHoverHighlight(){
        if(hoverHighlight!=null&&pointerWm!=null){try{pointerWm.removeView(hoverHighlight);}catch(Exception ignored){}}
        hoverHighlight=null;hoverParams=null;
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
        // Remove key filtering before doing any other cleanup so a service shutdown
        // cannot leave the TV remote trapped by this service.
        setKeyFilteringEnabled(false);
        stopMove(0);cancelLongPress();
        if (backLongPress != null) handler.removeCallbacks(backLongPress);
        backLongPress = null;
        removeHoverHighlight();removePointerInternal();clearHoverInternal();
        MouseService ms=ServiceRegistry.get();
        if(ms!=null) ms.restoreFallbackPointer();
        instance=null;super.onDestroy();
    }
}