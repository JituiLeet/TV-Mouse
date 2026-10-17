package com.jituileet.tvmouse;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private Button enable, hide, customAppearance, language, overlay, accessibility, about;
    private String appliedLanguageMode;

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override public void onCreate(Bundle b){super.onCreate(b);appliedLanguageMode=LanguageManager.getMode(this);buildMain();}
    @Override protected void onResume(){super.onResume();if(!LanguageManager.getMode(this).equals(appliedLanguageMode)){appliedLanguageMode=LanguageManager.getMode(this);buildMain();}else if(status!=null)updateStatus();}

    private TextView title(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(17);b.setAllCaps(false);b.setFocusable(true);b.setFocusableInTouchMode(false);b.setMinHeight(dp(60));return b;}

    private void buildMain(){
        ScrollView sv=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(48),dp(24),dp(48),dp(24));root.setBackgroundColor(Color.rgb(16,17,20));sv.addView(root,new ScrollView.LayoutParams(-1,-1));
        root.addView(title(getString(R.string.title),30),new LinearLayout.LayoutParams(-1,dp(72)));
        status=title("",15);status.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);root.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));

        enable=button(getString(R.string.toggle_mouse));hide=button(getString(R.string.hide_pointer));customAppearance=button(getString(R.string.custom_appearance));language=button(getString(R.string.language));overlay=button(getString(R.string.open_overlay));accessibility=button(getString(R.string.open_accessibility));about=button(getString(R.string.about));
        addButton(enable);addButton(hide);addButton(customAppearance);addButton(language);addButton(overlay);addButton(accessibility);addButton(about);
        setContentView(sv);

        enable.setOnClickListener(v->toggleMouse());
        hide.setOnClickListener(v->{MouseService s=ServiceRegistry.get();if(s!=null)s.toggleHidden();else toast(getString(R.string.mouse_not_started));});
        customAppearance.setOnClickListener(v->openMouseSettings(SettingsActivity.PAGE_APPEARANCE));
        language.setOnClickListener(v->openMouseSettings(SettingsActivity.PAGE_LANGUAGE));
        overlay.setOnClickListener(v->openOverlaySettings());
        accessibility.setOnClickListener(v->openAccessibilitySettings());
        about.setOnClickListener(v->showAbout());
        enable.requestFocus();updateStatus();
    }

    private void addButton(Button b){root.addView(b,new LinearLayout.LayoutParams(dp(560),dp(66)));Space s=new Space(this);root.addView(s,new LinearLayout.LayoutParams(1,dp(10)));}
    private void updateStatus(){boolean a=isAccessibilityEnabled();boolean m=ServiceRegistry.get()!=null;status.setText(getString(R.string.status, m?getString(R.string.enabled):getString(R.string.disabled), a?getString(R.string.enabled):getString(R.string.disabled)));status.setTextColor(a&&m?Color.rgb(130,210,150):Color.rgb(255,190,90));}
    private boolean isAccessibilityEnabled(){try{String enabledServices=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabledServices==null)return false;String me=new ComponentName(this,MouseAccessibilityService.class).flattenToString();for(String s:enabledServices.split(":"))if(me.equalsIgnoreCase(s))return true;}catch(Exception ignored){}return false;}

    private void toggleMouse(){
        MouseService s=ServiceRegistry.get();
        if(s!=null){s.stopSelf();updateStatus();}
        else startMouse();
    }

    private void startMouse(){
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle(getString(R.string.overlay_required_title)).setMessage(getString(R.string.overlay_required_message)).setPositiveButton(getString(R.string.open_settings),(d,w)->openOverlaySettings()).setNegativeButton(getString(R.string.cancel),null).show();return;}
        Intent i=new Intent(this,MouseService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        new Handler(Looper.getMainLooper()).postDelayed(()->updateStatus(),350);
        if(!isAccessibilityEnabled())new AlertDialog.Builder(this).setTitle(getString(R.string.accessibility_required_title)).setMessage(getString(R.string.accessibility_required_message)).setPositiveButton(getString(R.string.open_settings),(d,w)->openAccessibilitySettings()).setNegativeButton(getString(R.string.later),null).show();else toast(getString(R.string.mouse_started));
        updateStatus();
    }

    private void openMouseSettings(String page){Intent i=new Intent(this,SettingsActivity.class);i.putExtra(SettingsActivity.EXTRA_PAGE,page);startActivity(i);}
    private void openAccessibilitySettings(){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){toast(getString(R.string.cannot_open_settings));}}
    private void openOverlaySettings(){try{if(Build.VERSION.SDK_INT>=23)startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));else toast(getString(R.string.overlay_not_required));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private void openGithub(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/JituiLeet/TV-Mouse")));}catch(Exception e){toast(getString(R.string.cannot_open_link));}}

    private void showAbout(){startActivity(new Intent(this, AboutActivity.class));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
