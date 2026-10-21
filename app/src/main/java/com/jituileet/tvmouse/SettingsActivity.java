package com.jituileet.tvmouse;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Android-TV-style right-side settings panel used from both the app and the global shortcut. */
public class SettingsActivity extends Activity {
    public static final String EXTRA_PAGE = "page";
    public static final String PAGE_APPEARANCE = "appearance";
    public static final String PAGE_LANGUAGE = "language";
    private static final int PICK_IMAGE = 2001;
    private int appearanceTarget=0;
    private LinearLayout box;
    private FrameLayout screen;
    private ScrollView panelScroll;
    private final int PANEL_DP = 430;

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.wrap(newBase));
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        // Use a normal full-screen activity and draw the TV-style right-side panel ourselves.
        // This avoids translucent Holo window issues on some Android TV ROMs.
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        screen=new FrameLayout(this);
        screen.setBackgroundColor(Color.argb(175,16,17,20));
        box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22),dp(18),dp(16),dp(18));
        box.setBackgroundColor(Color.rgb(47,48,51));
        panelScroll=new ScrollView(this); panelScroll.setFillViewport(true);
        panelScroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(dp(PANEL_DP),-1,Gravity.RIGHT|Gravity.TOP);
        screen.addView(panelScroll,panel);
        setContentView(screen);
        String page=getIntent().getStringExtra(EXTRA_PAGE);
        if(PAGE_APPEARANCE.equals(page)) showAppearancePanel();
        else if(PAGE_LANGUAGE.equals(page)) showLanguagePanel();
        else buildMainPanel();
    }

    private void buildMainPanel() {
        box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(22),dp(18),dp(16),dp(18)); box.setBackgroundColor(Color.rgb(47,48,51));
        panelScroll.removeAllViews(); panelScroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        TextView title=text(getString(R.string.mouse_settings),23,true); title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        addRow("➤",getString(R.string.mouse_sensitivity),v->showSensitivityDialog());
        addRow("↗",getString(R.string.pointer_size),v->showPointerSizeDialog());
        addRow("✦",getString(R.string.custom_appearance),v->showAppearancePanel());
        addRow("⌨",getString(R.string.shortcut_settings),v->showShortcutSettings());
        addRow("文",getString(R.string.language),v->showLanguagePanel());
        addRow("■",getString(R.string.close_mouse),v->{MouseService s=ServiceRegistry.get();if(s!=null)s.stopSelf();finish();});
        addRow("G",getString(R.string.github_project),v->openGithub());
        setContentView(screen);
        if(box.getChildCount()>1) box.getChildAt(1).requestFocus();
    }

    private void showSensitivityDialog() {
        final int current=getSharedPreferences("mouse_settings",MODE_PRIVATE).getInt("sensitivity",5);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView value=text(getString(R.string.sensitivity_value,current),16,false); value.setGravity(Gravity.CENTER);
        SeekBar seek=new SeekBar(this); seek.setMax(9); seek.setProgress(current-1);
        content.addView(value,new LinearLayout.LayoutParams(-1,dp(36))); content.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean fromUser){value.setText(getString(R.string.sensitivity_value,progress+1));} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle(R.string.mouse_sensitivity).setView(content)
            .setPositiveButton(R.string.save,(d,w)->getSharedPreferences("mouse_settings",MODE_PRIVATE).edit().putInt("sensitivity",seek.getProgress()+1).apply())
            .setNegativeButton(R.string.cancel,null).show();
    }

    private void showPointerSizeDialog() {
        final int current=getSharedPreferences("mouse_settings",MODE_PRIVATE).getInt("pointer_size",42);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView value=text(getString(R.string.pointer_size_value,current),16,false); value.setGravity(Gravity.CENTER);
        SeekBar seek=new SeekBar(this); seek.setMax(56); seek.setProgress(Math.max(0,Math.min(56,current-24)));
        content.addView(value,new LinearLayout.LayoutParams(-1,dp(36))); content.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean fromUser){value.setText(getString(R.string.pointer_size_value,progress+24));} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle(R.string.pointer_size).setView(content)
            .setPositiveButton(R.string.save,(d,w)->{getSharedPreferences("mouse_settings",MODE_PRIVATE).edit().putInt("pointer_size",seek.getProgress()+24).apply();MouseService ms=ServiceRegistry.get();if(ms!=null)ms.applyPointerSize();})
            .setNegativeButton(R.string.cancel,null).show();
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
        TextView title=text(getString(R.string.pointer_schemes),23,true);title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        addRow("➤",getString(R.string.normal_pointer),v->{appearanceTarget=0;showAppearanceTargetPanel();});
        addRow("↕",getString(R.string.fixed_pointer),v->{appearanceTarget=1;showAppearanceTargetPanel();});
        addRow("☛",getString(R.string.link_pointer),v->{appearanceTarget=2;showAppearanceTargetPanel();});
        addRow("‹",getString(R.string.back),v->buildMainPanel());
        if(box.getChildCount()>1)box.getChildAt(1).requestFocus();
    }

    private void showAppearanceTargetPanel(){
        box.removeAllViews();
        String titleText=appearanceTarget==0?getString(R.string.normal_pointer):(appearanceTarget==1?getString(R.string.fixed_pointer):getString(R.string.link_pointer));
        TextView title=text(titleText,22,true);title.setGravity(Gravity.CENTER_VERTICAL);box.addView(title,new LinearLayout.LayoutParams(-1,dp(66)));
        boolean shared=appearanceTarget==1?AppearanceManager.isFixedShared(this):(appearanceTarget==2?AppearanceManager.isLinkShared(this):false);
        if(appearanceTarget!=0)addRow(shared?"●":"○",getString(shared?R.string.share_appearance:R.string.individual_appearance),v->{
            if(appearanceTarget==1)AppearanceManager.setFixedShared(this,!AppearanceManager.isFixedShared(this));
            else AppearanceManager.setLinkShared(this,!AppearanceManager.isLinkShared(this));showAppearanceTargetPanel();
        });
        TextView hint=text(getString(R.string.appearance_hint),13,false);hint.setTextColor(Color.LTGRAY);hint.setPadding(0,0,0,dp(8));box.addView(hint,new LinearLayout.LayoutParams(-1,dp(54)));
        String selected=selectedForTarget();
        addAppearanceRowForTarget(getString(R.string.default_appearance),"default",selected);
        for(int i=0;i<AppearanceManager.count(this);i++)addAppearanceRowForTarget(AppearanceManager.name(this,i),AppearanceManager.id(i),selected);
        addRow("＋",getString(R.string.upload_appearance),v->pickImage());
        addRow("‹",getString(R.string.back),v->showAppearancePanel());
        if(box.getChildCount()>1)box.getChildAt(1).requestFocus();
    }

    private String selectedForTarget(){return appearanceTarget==0?AppearanceManager.getSelected(this):(appearanceTarget==1?AppearanceManager.getSelectedFixed(this):AppearanceManager.getSelectedLink(this));}
    private void selectForTarget(String id){
        if(appearanceTarget==0)AppearanceManager.select(this,id);
        else if(appearanceTarget==1)AppearanceManager.selectFixed(this,id);
        else AppearanceManager.selectLink(this,id);
    }

    private void addAppearanceRowForTarget(String name,final String id,String selected){
        final LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setFocusable(true);row.setClickable(true);row.setPadding(dp(10),0,dp(8),0);row.setBackgroundResource(R.drawable.tv_row_bg);
        TextView radio=text(selected.equals(id)?"●":"○",22,true);radio.setGravity(Gravity.CENTER);row.addView(radio,new LinearLayout.LayoutParams(dp(44),dp(68)));
        TextView tx=text(name,16,false);row.addView(tx,new LinearLayout.LayoutParams(0,dp(68),1));
        row.setOnClickListener(v->{if(AppearanceManager.isCustom(id))showTargetAppearanceActions(id);else{selectForTarget(id);showAppearanceTargetPanel();}});
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));
    }

    private void showTargetAppearanceActions(final String id){
        final int index;try{index=Integer.parseInt(id.substring("custom_".length()));}catch(Exception e){return;}
        if(index<0||index>=AppearanceManager.count(this))return;
        final String currentName=AppearanceManager.name(this,index);
        String[] actions={getString(R.string.use_as_pointer),getString(R.string.rename_appearance),getString(R.string.delete_appearance)};
        new AlertDialog.Builder(this).setTitle(currentName).setItems(actions,(dialog,which)->{
            if(which==0){selectForTarget(id);toast(getString(R.string.appearance_added));showAppearanceTargetPanel();}
            else if(which==1){final EditText input=new EditText(this);input.setSingleLine(true);input.setText(currentName);input.setSelection(input.length());
                AlertDialog rename=new AlertDialog.Builder(this).setTitle(R.string.rename_appearance).setView(input).setPositiveButton(R.string.save,null).setNegativeButton(R.string.cancel,null).create();
                rename.setOnShowListener(x->rename.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!AppearanceManager.rename(this,index,input.getText().toString())){toast(getString(R.string.appearance_error_name));return;}rename.dismiss();showAppearanceTargetPanel();}));rename.show();}
            else if(which==2)new AlertDialog.Builder(this).setTitle(R.string.delete_appearance).setMessage(getString(R.string.delete_appearance_confirm,currentName)).setPositiveButton(R.string.delete_appearance,(d,w)->{AppearanceManager.delete(this,index);showAppearanceTargetPanel();}).setNegativeButton(R.string.cancel,null).show();
        }).show();
    }

    private void showShortcutSettings(){
        box.removeAllViews();TextView title=text(getString(R.string.shortcut_settings),23,true);title.setGravity(Gravity.CENTER_VERTICAL);box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView hint=text(getString(R.string.shortcut_instructions),14,false);hint.setTextColor(Color.LTGRAY);box.addView(hint,new LinearLayout.LayoutParams(-1,dp(76)));
        addRow("⚙",getString(R.string.shortcut_open_settings),v->showShortcutEditor("settings"));
        addRow("↕",getString(R.string.shortcut_fixed_scroll),v->showShortcutEditor("fixed"));
        addRow("‹",getString(R.string.back),v->buildMainPanel());
        if(box.getChildCount()>1)box.getChildAt(1).requestFocus();
    }

    private static final int[] SHORTCUT_KEYS={KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent.KEYCODE_MENU,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_INFO};
    private void showShortcutEditor(final String action){
        final int currentMode=ShortcutConfig.mode(this,action);
        final RadioGroup modeGroup=new RadioGroup(this);modeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton combo=new RadioButton(this);combo.setText(getString(R.string.shortcut_combination));combo.setId(1);modeGroup.addView(combo);
        RadioButton hold=new RadioButton(this);hold.setText(getString(R.string.shortcut_long_press));hold.setId(2);modeGroup.addView(hold);modeGroup.check(currentMode==ShortcutConfig.LONG_PRESS?2:1);
        new AlertDialog.Builder(this).setTitle(action.equals("settings")?R.string.shortcut_open_settings:R.string.shortcut_fixed_scroll).setView(modeGroup)
            .setPositiveButton(R.string.save,(d,w)->{if(modeGroup.getCheckedRadioButtonId()==2)chooseHoldKey(action);else chooseComboKeys(action);})
            .setNegativeButton(R.string.cancel,null).show();
    }

    private void chooseComboKeys(final String action){
        final String[] names=new String[SHORTCUT_KEYS.length];final boolean[] checked=new boolean[SHORTCUT_KEYS.length];List<Integer> current=ShortcutConfig.keys(this,action);
        for(int i=0;i<SHORTCUT_KEYS.length;i++){names[i]=ShortcutConfig.keyName(SHORTCUT_KEYS[i]);checked[i]=current.contains(SHORTCUT_KEYS[i]);}
        new AlertDialog.Builder(this).setTitle(R.string.shortcut_choose_keys).setMultiChoiceItems(names,checked,(d,which,isChecked)->checked[which]=isChecked)
            .setPositiveButton(R.string.save,(d,w)->{List<Integer> selected=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(SHORTCUT_KEYS[i]);if(selected.isEmpty()){toast(getString(R.string.shortcut_choose_keys));return;}ShortcutConfig.save(this,action,ShortcutConfig.COMBINATION,selected,ShortcutConfig.holdKey(this,action),ShortcutConfig.holdMs(this,action));toast(getString(R.string.shortcut_saved));showShortcutSettings();})
            .setNegativeButton(R.string.cancel,null).show();
    }

    private void chooseHoldKey(final String action){
        final String[] names=new String[SHORTCUT_KEYS.length];int selected=0;int current=ShortcutConfig.holdKey(this,action);
        for(int i=0;i<SHORTCUT_KEYS.length;i++){names[i]=ShortcutConfig.keyName(SHORTCUT_KEYS[i]);if(SHORTCUT_KEYS[i]==current)selected=i;}
        new AlertDialog.Builder(this).setTitle(R.string.shortcut_choose_hold_key).setSingleChoiceItems(names,selected,(d,which)->{
            int key=SHORTCUT_KEYS[which];d.dismiss();chooseHoldDuration(action,key);
        }).setNegativeButton(R.string.cancel,null).show();
    }

    private void chooseHoldDuration(final String action,final int key){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),dp(8));
        final int old=ShortcutConfig.holdMs(this,action);TextView value=text(getString(R.string.shortcut_hold_duration,old),16,false);value.setGravity(Gravity.CENTER);
        SeekBar seek=new SeekBar(this);seek.setMax(47);seek.setProgress(Math.max(0,Math.min(47,(old-300)/100)));content.addView(value,new LinearLayout.LayoutParams(-1,dp(36)));content.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){value.setText(getString(R.string.shortcut_hold_duration,300+p*100));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle(R.string.shortcut_long_press).setView(content).setPositiveButton(R.string.save,(d,w)->{List<Integer> keys=new ArrayList<>();keys.add(key);ShortcutConfig.save(this,action,ShortcutConfig.LONG_PRESS,keys,key,300+seek.getProgress()*100);toast(getString(R.string.shortcut_saved));showShortcutSettings();}).setNegativeButton(R.string.cancel,null).show();
    }

    private void pickImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        try { startActivityForResult(i,PICK_IMAGE); }
        catch(Exception e) { Intent j=new Intent(Intent.ACTION_GET_CONTENT);j.setType("image/*");j.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(j,PICK_IMAGE); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();final EditText input=new EditText(this);input.setSingleLine(true);input.setHint(getString(R.string.appearance_name_example));
        new AlertDialog.Builder(this).setTitle(R.string.name_appearance).setView(input).setPositiveButton(R.string.save,(d,w)->{
            AppearanceManager.Result result=AppearanceManager.importImage(this,uri,input.getText().toString());
            if(!result.success){toast(result.message);return;}
            if(appearanceTarget==1)AppearanceManager.selectFixed(this,result.id);
            else if(appearanceTarget==2)AppearanceManager.selectLink(this,result.id);
            else AppearanceManager.select(this,result.id);
            toast(getString(R.string.appearance_added));showAppearanceTargetPanel();
        }).setNegativeButton(R.string.cancel,null).show();
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
