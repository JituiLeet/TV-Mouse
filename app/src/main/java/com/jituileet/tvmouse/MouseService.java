package com.jituileet.tvmouse;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;

public class MouseService extends Service {
    private WindowManager wm;
    private ImageView pointer;
    private float x=500,y=300;
    private boolean scroll=false,hidden=false;
    private long moveHoldStart;
    private final float density;

    public MouseService(){ density=1f; }

    @Override public void onCreate() {
        super.onCreate(); ServiceRegistry.set(this); wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=26) startForegroundCompat(); createPointer(); applyAppearance();
        if(MouseAccessibilityService.getInstance()!=null) {
            MouseAccessibilityService.setKeyFilteringEnabled(true);
            refreshPointerWindowType();
        }
    }

    private void startForegroundCompat(){
        String channel="tv_mouse"; NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(channel,"电视鼠标",NotificationManager.IMPORTANCE_LOW);c.setDescription("电视鼠标后台运行");nm.createNotificationChannel(c);}
        Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,i,Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        Notification n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel).setSmallIcon(R.drawable.ic_mouse_pointer).setContentTitle("电视鼠标").setContentText("鼠标指针正在运行").setContentIntent(pi).setOngoing(true).build():new Notification.Builder(this).setSmallIcon(R.drawable.ic_mouse_pointer).setContentTitle("电视鼠标").setContentText("鼠标指针正在运行").setContentIntent(pi).setOngoing(true).build();
        startForeground(1001,n);
    }

    private int overlayType(){
        // TYPE_ACCESSIBILITY_OVERLAY can only be added by the AccessibilityService
        // itself.  MouseService therefore always owns a normal fallback overlay.
        return Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private void createPointer(){
        pointer=new ImageView(this); pointer.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(42),dp(42),overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT; p.x=(int)x; p.y=(int)y;
        try { wm.addView(pointer,p); } catch (RuntimeException e) { pointer=null; throw e; }
    }

    /**
     * Keeps the fallback overlay available, but when the accessibility service is
     * connected it displays its own TYPE_ACCESSIBILITY_OVERLAY instead.
     * This method deliberately does not try to add an accessibility window from
     * this Service; doing so causes BadTokenException on Android.
     */
    public void refreshPointerWindowType(){
        if(pointer==null || wm==null) return;
        if(MouseAccessibilityService.getInstance()!=null){
            pointer.setVisibility(View.GONE);
            MouseAccessibilityService.showPointerFromMouseService(this);
        } else {
            pointer.setVisibility(hidden?View.GONE:View.VISIBLE);
        }
    }

    public void restoreFallbackPointer(){
        if(pointer!=null) pointer.setVisibility(hidden?View.GONE:View.VISIBLE);
    }

    public void applyAppearance(){
        if(pointer!=null){
            // Always switch the built-in cursor too; previously fixed-scroll mode
            // fell back to the normal cursor whenever no custom fixed image existed.
            pointer.setImageResource(scroll ? R.drawable.ic_scroll_pointer : R.drawable.ic_mouse_pointer);
            File f=scroll ? AppearanceManager.selectedFixedFile(this) : AppearanceManager.selectedFile(this);
            if(f!=null){
                BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;
                Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath(),o);
                if(b!=null) pointer.setImageBitmap(b);
            }
        }
        MouseAccessibilityService.applyPointerAppearance(this);
    }

    public void move(float dx,float dy){
        if(scroll){
            // In scroll mode the pointer stays fixed. The remote controls scrolling only.
            if(MouseAccessibilityService.getInstance()!=null) MouseAccessibilityService.performScroll(dx,dy);
            return;
        }
        int sw=getResources().getDisplayMetrics().widthPixels;
        int sh=getResources().getDisplayMetrics().heightPixels;
        float maxX=Math.max(0,sw-dp(42)), maxY=Math.max(0,sh-dp(42));
        float targetX=x+dx, targetY=y+dy;
        // At a screen edge, clamp the pointer and scroll for the part of the
        // movement that would otherwise carry it beyond the display.
        float edgeDx=(targetX<0 || targetX>maxX) ? dx : 0;
        float edgeDy=(targetY<0 || targetY>maxY) ? dy : 0;
        x=Math.max(0,Math.min(maxX,targetX)); y=Math.max(0,Math.min(maxY,targetY));
        if((edgeDx!=0 || edgeDy!=0) && MouseAccessibilityService.getInstance()!=null)
            MouseAccessibilityService.performScroll(edgeDx,edgeDy);
        if(!hidden&&pointer!=null && MouseAccessibilityService.getInstance()==null){
            WindowManager.LayoutParams p=(WindowManager.LayoutParams)pointer.getLayoutParams();p.x=(int)x;p.y=(int)y;wm.updateViewLayout(pointer,p);
        }
        if(MouseAccessibilityService.getInstance()!=null) MouseAccessibilityService.updatePointerPosition((int)x,(int)y,hidden);
    }
    public int getPointerX(){return (int)x;} public int getPointerY(){return (int)y;}
    public void beginMoveHold(){moveHoldStart=System.currentTimeMillis();} public void endMoveHold(){moveHoldStart=0;}
    public long getMoveHoldTime(){return moveHoldStart==0?0:System.currentTimeMillis()-moveHoldStart;}
    public float getMoveSpeedPx(){float d=getResources().getDisplayMetrics().density;return 20f*d;}
    public void click(){MouseAccessibilityService.performClickAt((int)x,(int)y);}

    public void setScroll(boolean value){scroll=value; applyScrollAppearance(); Toast.makeText(this,scroll?"已开启固定滑动":"已关闭固定滑动",Toast.LENGTH_SHORT).show();}
    public void applyScrollAppearance(){
        // Refresh both overlay implementations, including the accessibility-owned
        // pointer used on Android TV when the accessibility service is connected.
        applyAppearance();
        if(MouseAccessibilityService.getInstance()!=null){
            MouseAccessibilityService.showPointerFromMouseService(this);
            MouseAccessibilityService.applyPointerAppearance(this);
        }
    }
    public boolean isScroll(){return scroll;} public void toggleScroll(){setScroll(!scroll);}
    public void toggleHidden(){
        hidden=!hidden;
        if(pointer!=null) pointer.setVisibility(hidden?View.GONE:(MouseAccessibilityService.getInstance()==null?View.VISIBLE:View.GONE));
        if(MouseAccessibilityService.getInstance()!=null) MouseAccessibilityService.updatePointerPosition((int)x,(int)y,hidden);
        Toast.makeText(this,hidden?"鼠标指针已隐藏":"鼠标指针已显示",Toast.LENGTH_SHORT).show();
    }
    public boolean isHidden(){return hidden;}

    public void showTvPanel(){
        Intent i=new Intent(this,SettingsActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
    }

    @Override public int onStartCommand(Intent i,int f,int id){
        return START_STICKY;
    }
    @Override public void onDestroy(){
        MouseAccessibilityService.setKeyFilteringEnabled(false);
        MouseAccessibilityService.hideAndRemovePointer();
        if(pointer!=null){try{wm.removeView(pointer);}catch(Exception ignored){}}
        ServiceRegistry.set(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
