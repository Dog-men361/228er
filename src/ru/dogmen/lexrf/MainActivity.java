package ru.dogmen.lexrf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private final ArrayList<Code> codes = new ArrayList<>();
    private final ArrayList<Article> articles = new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout root, body;
    private int bg, card, text, muted, accent, onAccent, border;
    private float textScale;
    private String screen = "home", selectedCode = "", query = "";
    private Article selectedArticle;
    private static final String OFFICIAL = "https://pravo.gov.ru/";
    private static final String VERSION = "1.2.0";
    private AppUpdates updates;
    private static final String NOTICE = "Справочная информация, не юридическое заключение. Применимость зависит от обстоятельств и действующей редакции закона. Проверьте полный текст и при необходимости обратитесь к юристу.";

    static class Code {
        final String name, description;
        final String[] documents;
        Code(String[] f) { name=f[0]; description=f[1]; documents=f[2].split(";"); }
    }
    static class Article {
        final String code, number, title, summary, conditions;
        final String[] keywords;
        Article(String[] f) { code=f[0]; number=f[1]; title=f[2]; summary=f[3]; conditions=f[4]; keywords=f[5].split(","); }
    }
    static class Match {
        final Article article; final int score;
        Match(Article a,int s) { article=a; score=s; }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("lexrf", MODE_PRIVATE);
        updates=new AppUpdates(this,prefs);
        try {
            for(String[] f: load("codes.tsv",3)) codes.add(new Code(f));
            for(String[] f: load("articles.tsv",6)) articles.add(new Article(f));
        } catch(IOException e) { Toast.makeText(this,"Не удалось загрузить справочник",Toast.LENGTH_LONG).show(); }
        if(saved!=null) { query=saved.getString("query",""); }
        home();
    }
    @Override protected void onResume() {
        super.onResume();
        if (updates!=null) updates.check(false);
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("query",query); super.onSaveInstanceState(out); }
    private List<String[]> load(String asset,int count) throws IOException {
        ArrayList<String[]> data=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(asset),StandardCharsets.UTF_8))) {
            String line; while((line=reader.readLine())!=null) { String[] f=line.split("\\|",-1); if(f.length==count) data.add(f); }
        }
        return data;
    }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void palette() {
        boolean light=prefs.getBoolean("light",false);
        bg=Color.parseColor(light?"#F0F2F8":"#10131D"); card=Color.parseColor(light?"#FFFFFF":"#1A2030");
        text=Color.parseColor(light?"#152037":"#F2F5FF"); muted=Color.parseColor(light?"#526077":"#AAB5CE");
        border=Color.parseColor(light?"#D5DEF0":"#303A50");
        String[] dark={"#A4B9FF","#C6A4FF","#8EE1BB","#FFABC7","#FFC88A","#80D7E9"};
        String[] bright={"#395DBD","#6942AD","#147554","#A63D69","#92510D","#117489"};
        int i=Math.max(0,Math.min(5,prefs.getInt("accent",0))); accent=Color.parseColor((light?bright:dark)[i]);
        onAccent=Color.parseColor(light?"#FFFFFF":"#11203B"); textScale=prefs.getInt("size",100)/100f;
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
    private GradientDrawable rounded(int color,int radius,boolean stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(stroke)d.setStroke(dp(1),border); return d; }
    private TextView label(String s,float size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size*textScale); t.setTextColor(color); t.setLineSpacing(dp(3),1); if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t;
    }
    private void add(LinearLayout parent,View view,int gap) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(gap); parent.addView(view,p); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout panel(LinearLayout parent) { LinearLayout l=column(); l.setPadding(dp(18),dp(16),dp(18),dp(16)); l.setBackground(rounded(card,22,true)); add(parent,l,12); return l; }
    private TextView button(String title,boolean primary,Runnable action) {
        TextView t=label(title,16,primary?onAccent:text,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(54)); t.setPadding(dp(14),dp(12),dp(14),dp(12)); t.setBackground(rounded(primary?accent:card,18,!primary)); t.setClickable(true); t.setFocusable(true); t.setOnClickListener(v->action.run()); return t;
    }
    private void base(String page,String title,String sub) {
        screen=page; palette(); root=column(); root.setBackgroundColor(bg); root.setPadding(dp(18),dp(10),dp(18),0); setContentView(root);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        if(!page.equals("home")) { TextView back=button("‹",false,this::back); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(52),dp(46)); bp.rightMargin=dp(12); bar.addView(back,bp); }
        TextView brand=label("LexRF",21,text,true); bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if (!page.equals("settings")) {
            TextView settings=button("⚙",false,this::settings);
            settings.setContentDescription("Настройки");
            bar.addView(settings,new LinearLayout.LayoutParams(dp(54),dp(46)));
        }
        add(root,bar,18);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false); body=column(); body.setPadding(0,0,0,dp(22)); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!title.isEmpty())add(body,label(title,29,text,true),6);
        if(!sub.isEmpty())add(body,label(sub,15,muted,false),20);
    }
    private void home() {
        base("home","", "");
        add(body,button("Поиск по ситуации",true,this::search),12);
        add(body,button("Список законов",false,this::catalog),0);
    }
    private EditText input(String hint,String value,boolean multi) { EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(text); e.setHintTextColor(muted); e.setTextSize(16*textScale); e.setGravity(Gravity.TOP|Gravity.START); e.setPadding(dp(15),dp(12),dp(15),dp(12)); e.setBackground(rounded(card,18,true)); e.setMinLines(multi?4:1); e.setMaxLines(multi?9:1); e.setInputType(multi?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES:android.text.InputType.TYPE_CLASS_TEXT); return e; }
    private void search() {
        base("search","Поиск по ситуации","Локальный подбор статей по словам и фразам");
        EditText field=input("Например: работодатель задерживает зарплату…",query,true); add(body,field,12);
        LinearLayout results=column();
        add(body,button("Найти возможные статьи",true,()->{query=field.getText().toString().trim(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0); field.clearFocus(); renderResults(results);}),14);
        add(body,results,0);
        add(body,label("Можно также вводить номер и кодекс, например «УК РФ 158».",13,muted,false),8);
        if(!query.isEmpty())renderResults(results);
        field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int c){query=s.toString();} public void afterTextChanged(Editable e){}});
    }
    private String norm(String s) { return s.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[\\s\\p{Punct}&&[^.]]+"," ").trim(); }
    private int score(Article a,String q) { String n=norm(q); int score=0; String code=norm(a.code); boolean number=java.util.regex.Pattern.compile("(?<![0-9.])"+java.util.regex.Pattern.quote(a.number)+"(?![0-9.])").matcher(n).find(); if(number && n.contains(code))score+=40; else if(number&&(n.contains("стать")||n.contains("ст.")))score+=8; for(String k:a.keywords)if(n.contains(norm(k)))score+=k.length()>=6?3:2; if(n.equals(norm(a.title)))score+=18; return score; }
    private void renderResults(LinearLayout result) {
        result.removeAllViews(); if(query.trim().isEmpty()){add(result,label("Сначала опишите ситуацию.",16,muted,false),8);return;}
        List<Match> found=new ArrayList<>(); for(Article a:articles){int s=score(a,query);if(s>0)found.add(new Match(a,s));} found.sort((a,b)->Integer.compare(b.score,a.score));
        LinearLayout explain=panel(result);
        if(found.isEmpty()) { add(explain,label("Совпадений не найдено",20,accent,true),8); add(explain,label("Это не означает, что ситуация законна. Уточните действия, участников и обстоятельства или откройте полный текст закона.",15,text,false),0); }
        else { add(explain,label("Возможные нормы · "+Math.min(12,found.size()),20,accent,true),8); add(explain,label("Подбор по словам, не вывод о нарушении. В карточках указано, что нужно проверить. Если признаки статьи отсутствуют или есть законное основание, она может не применяться.",15,text,false),0); }
        for(int i=0;i<Math.min(12,found.size());i++){Article a=found.get(i).article;articleCard(result,a,()->article(a));}
        add(result,label(NOTICE,13,muted,false),8);
    }
    private void articleCard(LinearLayout parent,Article a,Runnable action) { LinearLayout p=panel(parent); add(p,label(a.code+" · Статья "+a.number,14,accent,true),5); add(p,label(a.title,19,text,true),6); add(p,label(a.summary,15,muted,false),0); p.setClickable(true); p.setFocusable(true); p.setOnClickListener(v->action.run()); }
    private void catalog() {
        base("catalog","Список законов","Выберите раздел, чтобы открыть статьи и полный текст документа");
        for(Code c:codes) { LinearLayout p=panel(body); add(p,label(c.name,21,text,true),5); add(p,label(c.description,15,muted,false),8); add(p,label("Все статьи   →",14,accent,true),0); p.setClickable(true); p.setFocusable(true); p.setOnClickListener(v->code(c)); }
        add(body,button("Официальный портал правовой информации",false,()->open(OFFICIAL)),8);
    }
    private Code findCode(String name) { for(Code c:codes)if(c.name.equals(name))return c; return null; }
    private void code(Code c) {
        selectedCode=c.name;
        base("code",c.name,c.description);
        add(body,label("Полный текст и оглавление · требуется интернет",15,muted,false),10);
        for(int i=0;i<c.documents.length;i++) {
            final int part=i;
            String caption=c.documents.length==1?"Все статьи": "Все статьи · часть "+(i+1);
            add(body,button(caption,true,()->readLaw(c,part,"")),8);
        }
        add(body,label("Краткие пояснения · офлайн",18,text,true),8);
        EditText f=input("Номер или название статьи","",false); add(body,f,12); LinearLayout list=column(); add(body,list,0); showCodeArticles(c,"",list);
        f.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c1){showCodeArticles(c,s.toString(),list);} public void afterTextChanged(Editable e){} });
    }
    private void readLaw(Code code,int part,String term) {
        Intent intent=new Intent(this,LawReaderActivity.class);
        String title=code.name+(code.documents.length==1?"":" · часть "+(part+1));
        intent.putExtra(LawReaderActivity.TITLE,title);
        intent.putExtra(LawReaderActivity.URL,code.documents[part]);
        intent.putExtra(LawReaderActivity.TERM,term);
        intent.putExtra(LawReaderActivity.ZOOM,prefs.getInt("size",100));
        intent.putExtra(LawReaderActivity.LIGHT,prefs.getBoolean("light",false));
        startActivity(intent);
    }
    private void showCodeArticles(Code c,String filter,LinearLayout list) { list.removeAllViews(); String f=norm(filter); int count=0; for(Article a:articles){if(a.code.equals(c.name)&&(f.isEmpty()||norm(a.number+" "+a.title+" "+a.summary).contains(f))){articleCard(list,a,()->article(a));count++;}} if(count==0)add(list,label("Нет совпадений в локальной подборке. Откройте полный текст закона.",15,muted,false),10); }
    private String articleOrigin="catalog";
    private void article(Article a) {
        articleOrigin=screen; selectedArticle=a; base("article","Статья "+a.number,a.code+" · "+a.title);
        LinearLayout p=panel(body); add(p,label("Что регулирует",18,accent,true),8); add(p,label(a.summary,17,text,false),0);
        p=panel(body); add(p,label("Что необходимо проверить",18,accent,true),8); add(p,label(a.conditions,17,text,false),0);
        Code c=findCode(a.code); if(c!=null)add(body,button("Найти статью в полном оглавлении",true,()->readLaw(c,0,"Статья "+a.number+".")),10); add(body,button("Официальный источник ↗",false,()->open(OFFICIAL)),10);
        add(body,label("Локальная карточка — краткое пояснение, не полный текст статьи. Версия справочника: "+VERSION+".",14,muted,false),10); add(body,label(NOTICE,13,muted,false),0);
    }
    private void settings() {
        base("settings","Настройки", "");
        LinearLayout appearance=panel(body);
        add(appearance,label("Оформление",20,text,true),12);
        Switch theme=settingSwitch("Светлая тема",prefs.getBoolean("light",false));
        theme.setOnCheckedChangeListener((view,checked)->{
            prefs.edit().putBoolean("light",checked).apply();
            settings();
        });
        add(appearance,theme,10);
        String[] names={"Синий","Фиолетовый","Зелёный","Розовый","Янтарный","Бирюзовый"};
        int chosen=prefs.getInt("accent",0);
        add(appearance,button("Цвет · "+names[chosen],false,()->new AlertDialog.Builder(this)
                .setTitle("Акцентный цвет")
                .setSingleChoiceItems(names,chosen,(dialog,index)->{
                    prefs.edit().putInt("accent",index).apply();
                    dialog.dismiss();
                    settings();
                }).setNegativeButton("Закрыть",null).show()),12);

        TextView size=label("Размер текста · "+prefs.getInt("size",100)+"%",16,text,true);
        add(appearance,size,0);
        SeekBar slider=new SeekBar(this);
        slider.setMax(60);
        slider.setProgress(prefs.getInt("size",100)-80);
        slider.setContentDescription("Размер текста");
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(accent));
        add(appearance,slider,4);
        TextView sample=label("Пример текста статьи",16,muted,false);
        add(appearance,sample,0);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onProgressChanged(SeekBar s,int progress,boolean user) {
                int percent=progress+80;
                size.setText("Размер текста · "+percent+"%");
                sample.setTextSize(16*percent/100f);
                if(user)prefs.edit().putInt("size",percent).apply();
            }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        LinearLayout updatePanel=panel(body);
        add(updatePanel,label("Обновления",20,text,true),8);
        Switch automatic=settingSwitch("Скачивать автоматически",prefs.getBoolean("auto_update",true));
        automatic.setOnCheckedChangeListener((view,checked)->{
            prefs.edit().putBoolean("auto_update",checked).apply();
            if(checked)updates.check(false);
        });
        add(updatePanel,automatic,8);
        add(updatePanel,label("Версия "+VERSION+" · проверка при открытии приложения",14,muted,false),12);
        add(updatePanel,button("Проверить сейчас",false,()->updates.check(true)),0);
    }
    private Switch settingSwitch(String caption,boolean checked) {
        Switch view=new Switch(this);
        view.setText(caption);
        view.setTextColor(text);
        view.setTextSize(16*textScale);
        view.setMinHeight(dp(48));
        view.setChecked(checked);
        return view;
    }
    private void open(String link){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception e){Toast.makeText(this,"Браузер недоступен",Toast.LENGTH_SHORT).show();}}
    private void back(){switch(screen){case "article":if(articleOrigin.equals("search"))search();else{Code c=findCode(selectedArticle.code);if(c!=null)code(c);else catalog();}break;case "code":catalog();break;case "home":finish();break;default:home();}}
    @Override public void onBackPressed(){back();}
}
