package com.jituileet.tvmouse;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Android-TV-style right-side settings panel used from both the app and the global shortcut. */
public class SettingsActivity extends Activity {
    public static final String EXTRA_PAGE = "page";
    public static final String PAGE_APPEARANCE = "appearance";
    public static final String PAGE_LANGUAGE = "language";
    private static final int PICK_IMAGE = 2001;
    private LinearLayout box;
    private final int PANEL_DP = 430;

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        // Use a normal full-screen activity and draw the TV-style right-side panel ourselves.
        // This avoids translucent Holo window issues on some Android TV ROMs.
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        FrameLayout screen=new FrameLayout(this);
        screen.setBackgroundColor(Color.argb(175,16,17,20));
        box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22),dp(18),dp(16),dp(18));
        box.setBackgroundColor(Color.rgb(47,48,51));
        FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(dp(PANEL_DP),-1,Gravity.RIGHT|Gravity.TOP);
        screen.addView(box,panel);
        setContentView(screen);
        String page=getIntent().getStringExtra(EXTRA_PAGE);
        if(PAGE_APPEARANCE.equals(page)) showAppearancePanel();
        else if(PAGE_LANGUAGE.equals(page)) showLanguagePanel();
        else buildMainPanel();
    }

    private void buildMainPanel() {
        box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(22),dp(18),dp(16),dp(18)); box.setBackgroundColor(Color.rgb(47,48,51));
        TextView title=text(getString(R.string.mouse_settings),23,true); title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        addRow("⌖",getString(R.string.hide_pointer),v->{MouseService s=ServiceRegistry.get();if(s!=null)s.toggleHidden();else toast(getString(R.string.mouse_not_started));});
        if(ServiceRegistry.get()!=null) addRow("↕",getString(R.string.scroll_toggle),v->{MouseService s=ServiceRegistry.get();if(s!=null)s.toggleScroll();});
        addRow("✦",getString(R.string.custom_appearance),v->showAppearancePanel());
        addRow("文",getString(R.string.language),v->showLanguagePanel());
        addRow("■",getString(R.string.close_mouse),v->{MouseService s=ServiceRegistry.get();if(s!=null)s.stopSelf();finish();});
        addRow("G",getString(R.string.github_project),v->openGithub());
        setContentView(box);
        if(box.getChildCount()>1) box.getChildAt(1).requestFocus();
    }

    private void showLanguagePanel() {
        box.removeAllViews();
        TextView title=text(getString(R.string.language),23,true); title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        String selected=LanguageManager.getMode(this);
        addChoiceRow(getString(R.string.language_follow_system),LanguageManager.SYSTEM,selected);
        addChoiceRow(getString(R.string.language_chinese),LanguageManager.ZH,selected);
        addChoiceRow(getString(R.string.language_english),LanguageManager.EN,selected);
        addRow("‹",getString(R.string.back),v->buildMainPanel());
        if(box.getChildCount()>1) box.getChildAt(1).requestFocus();
    }

    private void addChoiceRow(String name, final String mode, String selected) {
        final LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true); row.setPadding(dp(10),0,dp(8),0); row.setBackgroundResource(R.drawable.tv_row_bg);
        TextView radio=text(selected.equals(mode)?"●":"○",22,true); radio.setGravity(Gravity.CENTER);
        row.addView(radio,new LinearLayout.LayoutParams(dp(44),dp(68)));
        TextView tx=text(name,16,false); row.addView(tx,new LinearLayout.LayoutParams(0,dp(68),1));
        row.setOnClickListener(v->{LanguageManager.setMode(this,mode);recreate();});
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));
    }

    private void showAppearancePanel() {
        box.removeAllViews();
        TextView title=text(getString(R.string.custom_appearance),23,true); title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView hint=text(getString(R.string.appearance_hint),13,false);
        hint.setTextColor(Color.LTGRAY); hint.setPadding(0,0,0,dp(12));
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(68)));

        addAppearanceRow(getString(R.string.default_appearance), "default");
        int count=AppearanceManager.count(this);
        for(int i=0;i<count;i++) addAppearanceRow(AppearanceManager.name(this,i), AppearanceManager.id(i));
        addRow("＋",getString(R.string.upload_appearance),v->pickImage());
        addRow("‹",getString(R.string.back),v->buildMainPanel());
        if(box.getChildCount()>1) box.getChildAt(1).requestFocus();
    }

    private void addAppearanceRow(String name, final String id) {
        final LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true); row.setPadding(dp(10),0,dp(8),0); row.setBackgroundResource(R.drawable.tv_row_bg);
        TextView radio=text(AppearanceManager.getSelected(this).equals(id)?"●":"○",22,true); radio.setGravity(Gravity.CENTER);
        row.addView(radio,new LinearLayout.LayoutParams(dp(44),dp(68)));
        TextView tx=text(name,16,false); row.addView(tx,new LinearLayout.LayoutParams(0,dp(68),1));
        row.setOnClickListener(v->{AppearanceManager.select(this,id);showAppearancePanel();});
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));
    }

    private void pickImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        try { startActivityForResult(i,PICK_IMAGE); }
        catch(Exception e) { Intent j=new Intent(Intent.ACTION_GET_CONTENT);j.setType("image/*");j.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(j,PICK_IMAGE); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE || resultCode!=RESULT_OK || data==null || data.getData()==null)return;
        final Uri uri=data.getData();
        final EditText input=new EditText(this); input.setSingleLine(true); input.setHint(getString(R.string.appearance_name_example));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(getString(R.string.name_appearance)).setView(input)
            .setPositiveButton(getString(R.string.save),null).setNegativeButton(getString(R.string.cancel),null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            AppearanceManager.Result r=AppearanceManager.importImage(this,uri,input.getText().toString());
            if(!r.success){toast(r.message);return;}
            toast(getString(R.string.appearance_added));d.dismiss();showAppearancePanel();
        }));
        d.show();
    }

    private void addRow(String icon,String label,View.OnClickListener click) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setFocusable(true);row.setClickable(true);row.setPadding(dp(10),0,dp(10),0);row.setBackgroundResource(R.drawable.tv_row_bg);
        TextView ic=text(icon,20,true);ic.setGravity(Gravity.CENTER);row.addView(ic,new LinearLayout.LayoutParams(dp(46),dp(68)));
        TextView tx=text(label,16,false);tx.setGravity(Gravity.CENTER_VERTICAL);row.addView(tx,new LinearLayout.LayoutParams(0,dp(68),1));row.setOnClickListener(click);
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));
    }

    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    private void openGithub(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/JituiLeet/TV-Mouse")));}catch(Exception e){toast(getString(R.string.cannot_open_link));}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
