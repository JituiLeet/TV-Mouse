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
    private boolean pickingFixedAppearance=false;
    private String previousGeneralAppearance="default";
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
        addRow("文",getString(R.string.language),v->showLanguagePanel());
        addRow("⌨",getString(R.string.shortcut_settings),v->showShortcutPanel());
        addRow("■",getString(R.string.close_mouse),v->{MouseService s=ServiceRegistry.get();if(s!=null)s.stopSelf();finish();});
        addRow("G",getString(R.string.github_project),v->openGithub());
        setContentView(screen);
        if(box.getChildCount()>1) box.getChildAt(1).requestFocus();
    }


    private void showShortcutPanel() {
        box.removeAllViews();
        TextView title=text(getString(R.string.shortcut_settings),23,true); title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        addShortcutRow(getString(R.string.shortcut_open_settings),ShortcutConfig.OPEN_SETTINGS);
        addShortcutRow(getString(R.string.shortcut_enable_scroll),ShortcutConfig.ENABLE_SCROLL);
        addShortcutRow(getString(R.string.shortcut_hide_pointer),ShortcutConfig.HIDE_POINTER);
        addShortcutRow(getString(R.string.shortcut_close_scroll),ShortcutConfig.CLOSE_SCROLL);
        TextView hint=text(getString(R.string.shortcut_settings_hint),13,false); hint.setTextColor(Color.LTGRAY);
        hint.setPadding(dp(4),dp(10),dp(4),dp(12)); box.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        addRow("↺",getString(R.string.shortcut_reset_defaults),v->new AlertDialog.Builder(this)
            .setTitle(R.string.shortcut_reset_defaults)
            .setMessage(R.string.shortcut_reset_confirm)
            .setPositiveButton(R.string.confirm,(d,w)->{ShortcutConfig.resetDefaults(this);showShortcutPanel();})
            .setNegativeButton(R.string.cancel,null).show());
        addRow("‹",getString(R.string.back),v->buildMainPanel());
        if(box.getChildCount()>1)box.getChildAt(1).requestFocus();
    }

    private void addShortcutRow(String label,String action) {
        addRow("⌨",label+"\n"+ShortcutConfig.summary(this,action),v->new AlertDialog.Builder(this)
            .setTitle(label).setItems(new String[]{getString(R.string.shortcut_combo),getString(R.string.shortcut_hold)},(d,w)->{
                if(w==0)showComboEditor(action,label);else showHoldEditor(action,label);
            }).show());
    }

    private void showComboEditor(final String action,String label) {
        final int[] keys=new int[]{0,0,0};
        final Button[] buttons=new Button[3];
        final ShortcutConfig.Config old=ShortcutConfig.get(this,action);
        if("combo".equals(old.mode))for(int i=0;i<Math.min(3,old.keys.length);i++)keys[i]=old.keys[i];
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(8));
        TextView desc=text(getString(R.string.shortcut_combo_hint),15,false);content.addView(desc,new LinearLayout.LayoutParams(-1,dp(54)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(content)
            .setPositiveButton(R.string.save,null).setNegativeButton(R.string.cancel,null).create();
        for(int i=0;i<3;i++){
            final int slot=i; buttons[i]=new Button(this);buttons[i].setAllCaps(false);buttons[i].setText(keys[i]==0?getString(R.string.shortcut_choose_button, i+1):ShortcutConfig.keyName(keys[i]));
            content.addView(buttons[i],new LinearLayout.LayoutParams(-1,dp(54)));
            buttons[i].setOnClickListener(v->showKeyCapture(key->{keys[slot]=key;buttons[slot].setText(key==0?getString(R.string.shortcut_unassigned):ShortcutConfig.keyName(key));}));
            buttons[i].setOnLongClickListener(v->{keys[slot]=0;buttons[slot].setText(getString(R.string.shortcut_choose_button,slot+1));return true;});
        }
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            java.util.ArrayList<Integer> list=new java.util.ArrayList<>();for(int k:keys)if(k!=0&&!list.contains(k))list.add(k);
            int[] result=new int[list.isEmpty()?1:list.size()];if(list.isEmpty()){result[0]=0;}else for(int i=0;i<list.size();i++)result[i]=list.get(i);
            ShortcutConfig.saveCombo(this,action,result);dialog.dismiss();showShortcutPanel();
        }));
        dialog.show();
    }

    private interface KeySelected { void selected(int keyCode); }
    private void showKeyCapture(KeySelected selected) {
        TextView catcher=text(getString(R.string.shortcut_press_any_key),18,true);catcher.setGravity(Gravity.CENTER);catcher.setFocusable(true);catcher.setFocusableInTouchMode(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(R.string.shortcut_select_key).setView(catcher).setNegativeButton(R.string.cancel,null).create();
        final int[] pressedKey={0};
        final boolean[] cleared={false};
        final Runnable[] clearRunnable={null};
        catcher.setOnKeyListener((view,keyCode,event)->{
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
                pressedKey[0]=keyCode; cleared[0]=false;
                clearRunnable[0]=()->{cleared[0]=true;pressedKey[0]=0;selected.selected(0);d.dismiss();};
                handler.postDelayed(clearRunnable[0],3000);
                return true;
            }
            if(event.getAction()==KeyEvent.ACTION_UP && pressedKey[0]==keyCode){
                if(clearRunnable[0]!=null)handler.removeCallbacks(clearRunnable[0]);
                if(!cleared[0]){selected.selected(keyCode);d.dismiss();}
                return true;
            }
            return true;
        });
        d.setOnDismissListener(x->{if(clearRunnable[0]!=null)handler.removeCallbacks(clearRunnable[0]);});
        d.setOnShowListener(x->catcher.requestFocus());d.show();catcher.requestFocus();
    }

    private void showHoldEditor(final String action,String label) {
        final int[] key={ShortcutConfig.get(this,action).keys[0]};
        final long[] duration={Math.max(1000,ShortcutConfig.get(this,action).duration)};
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(8),dp(18),dp(8));
        TextView desc=text(getString(R.string.shortcut_hold_hint),15,false);content.addView(desc,new LinearLayout.LayoutParams(-1,dp(48)));
        Button choose=new Button(this);choose.setAllCaps(false);choose.setText(key[0]==0?getString(R.string.shortcut_unassigned):ShortcutConfig.keyName(key[0]));content.addView(choose,new LinearLayout.LayoutParams(-1,dp(54)));
        EditText time=new EditText(this);time.setSingleLine(true);time.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);time.setText(String.valueOf(duration[0]));time.setHint(getString(R.string.shortcut_milliseconds));content.addView(time,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView unit=text(getString(R.string.shortcut_time_hint),12,false);unit.setTextColor(Color.LTGRAY);content.addView(unit,new LinearLayout.LayoutParams(-1,dp(32)));
        choose.setOnClickListener(v->showKeyCapture(k->{key[0]=k;choose.setText(k==0?getString(R.string.shortcut_unassigned):ShortcutConfig.keyName(k));}));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(content).setPositiveButton(R.string.save,null).setNegativeButton(R.string.cancel,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            long ms;try{ms=Long.parseLong(time.getText().toString().trim());}catch(Exception e){ms=0;}
            if(key[0]==0){toast(getString(R.string.shortcut_need_key));return;}if(ms<1000){time.setError(getString(R.string.shortcut_minimum_time));return;}
            ShortcutConfig.saveHold(this,action,key[0],ms);dialog.dismiss();showShortcutPanel();
        }));dialog.show();
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
        if (AppearanceManager.isCustom(id)) {
            row.setOnClickListener(v -> showAppearanceActions(id));
        } else {
            row.setOnClickListener(v->{AppearanceManager.select(this,id);showAppearancePanel();});
        }
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(68)));
    }

    private void showAppearanceActions(final String id) {
        final int index;
        try { index = Integer.parseInt(id.substring("custom_".length())); }
        catch (Exception e) { return; }
        if (index < 0 || index >= AppearanceManager.count(this)) return;
        final String currentName = AppearanceManager.name(this, index);
        final String[] actions = new String[] {
            getString(R.string.use_as_pointer), getString(R.string.rename_appearance), getString(R.string.delete_appearance)
        };
        new AlertDialog.Builder(this).setTitle(currentName).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                AppearanceManager.select(this, id);
                toast(getString(R.string.appearance_added));
                showAppearancePanel();
            } else if (which == 1) {
                final EditText input = new EditText(this);
                input.setSingleLine(true); input.setText(currentName); input.setSelection(input.length());
                AlertDialog rename = new AlertDialog.Builder(this).setTitle(R.string.rename_appearance).setView(input)
                    .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create();
                rename.setOnShowListener(x -> rename.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (!AppearanceManager.rename(this, index, input.getText().toString())) {
                        toast(getString(R.string.appearance_error_name)); return;
                    }
                    rename.dismiss(); showAppearancePanel();
                }));
                rename.show();
            } else if (which == 2) {
                new AlertDialog.Builder(this).setTitle(R.string.delete_appearance)
                    .setMessage(getString(R.string.delete_appearance_confirm, currentName))
                    .setPositiveButton(R.string.delete_appearance, (d, w) -> {
                        AppearanceManager.delete(this, index); showAppearancePanel();
                    }).setNegativeButton(R.string.cancel, null).show();
            }
        }).show();
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
        if(pickingFixedAppearance) {
            AppearanceManager.Result fixed=AppearanceManager.importImage(this,uri,"固定滑动外观");
            pickingFixedAppearance=false;
            if(!fixed.success){toast(fixed.message);return;}
            AppearanceManager.select(this,previousGeneralAppearance);
            AppearanceManager.selectFixed(this,fixed.id);
            toast("固定滑动外观已设置");
            showAppearancePanel();
            return;
        }
        final EditText input=new EditText(this); input.setSingleLine(true); input.setHint(getString(R.string.appearance_name_example));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(getString(R.string.name_appearance)).setView(input)
            .setPositiveButton(getString(R.string.save),null).setNegativeButton(getString(R.string.cancel),null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            AppearanceManager.Result r=AppearanceManager.importImage(this,uri,input.getText().toString());
            if(!r.success){toast(r.message);return;}
            String generalId=r.id;
            toast(getString(R.string.appearance_added));d.dismiss();
            new AlertDialog.Builder(this)
                .setTitle("固定滑动的鼠标指针")
                .setMessage("是否让固定滑动共用刚才的鼠标指针？")
                .setPositiveButton("共用鼠标指针",(dialog,which)->{
                    AppearanceManager.setFixedShared(this,true);
                    showAppearancePanel();
                })
                .setNegativeButton("单独自定义",(dialog,which)->{
                    previousGeneralAppearance=generalId;
                    pickingFixedAppearance=true;
                    pickImage();
                }).show();
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
