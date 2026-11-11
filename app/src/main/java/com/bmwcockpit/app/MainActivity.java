package com.bmwcockpit.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import java.util.Random;

public class MainActivity extends Activity {
  LinearLayout root, metrics; TextView status; Spinner theme, page; CheckBox rpm, speed, boost, oil, coolant, battery;
  final Handler handler = new Handler(); final Random random = new Random(); int tick = 0;
  int bg = 0xff0b1018, accent = 0xff46a8ff;
  TextView label(String text, int size, int color) { TextView t = new TextView(this); t.setText(text); t.setTextColor(color); t.setTextSize(size); t.setGravity(Gravity.CENTER); t.setPadding(12,12,12,12); return t; }
  @Override public void onCreate(Bundle b) { super.onCreate(b); build(); handler.postDelayed(new Runnable(){ public void run(){ tick++; render(); handler.postDelayed(this,900); } },900); }
  void build(){ ScrollView sc = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(1); root.setPadding(18,12,18,18); sc.addView(root); setContentView(sc);
    TextView title=label("BMW  /  DIGITAL COCKPIT",23,Color.WHITE); title.setTypeface(null,Typeface.BOLD); root.addView(title);
    status=label("● MODO DEMONSTRAÇÃO — sem conexão OBD2",13,0xffffbc60); root.addView(status);
    LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); root.addView(controls);
    theme=new Spinner(this); theme.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"M Performance","Race Dashboard"})); controls.addView(theme,new LinearLayout.LayoutParams(0,60,1));
    page=new Spinner(this); page.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Todos","Motor","Condução"})); controls.addView(page,new LinearLayout.LayoutParams(0,60,1));
    LinearLayout toggles=new LinearLayout(this); toggles.setOrientation(LinearLayout.VERTICAL); root.addView(toggles);
    TextView choose=label("SENSORES VISÍVEIS (personalize)",12,0xffa8b7cc); toggles.addView(choose);
    LinearLayout row1=new LinearLayout(this),row2=new LinearLayout(this); toggles.addView(row1); toggles.addView(row2);
    rpm=check("RPM",row1); speed=check("Velocidade",row1); boost=check("Turbo",row1); oil=check("Óleo",row2); coolant=check("Água",row2); battery=check("Bateria",row2);
    metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.VERTICAL); root.addView(metrics);
    theme.setOnItemSelectedListener(listener()); page.setOnItemSelectedListener(listener()); render(); }
  CheckBox check(String s,LinearLayout row){CheckBox c=new CheckBox(this); c.setText(s); c.setTextColor(Color.WHITE); c.setTextSize(12); c.setChecked(true); row.addView(c,new LinearLayout.LayoutParams(0,50,1)); c.setOnCheckedChangeListener((v,on)->render());return c;}
  android.widget.AdapterView.OnItemSelectedListener listener(){return new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){render();}};}
  void card(String title,String val,String unit){ LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(18,12,18,12); GradientDrawable shape=new GradientDrawable();shape.setColor(0xff172332);shape.setCornerRadius(18);shape.setStroke(2,accent);box.setBackground(shape); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);metrics.addView(box,lp);box.addView(label(title,13,0xff9db2cb));TextView number=label(val+"  "+unit,28,Color.WHITE);number.setTypeface(null,Typeface.BOLD);box.addView(number); }
  void render(){if(metrics==null)return; accent=theme.getSelectedItemPosition()==0?0xff46a8ff:0xffff5050; bg=theme.getSelectedItemPosition()==0?0xff0b1018:0xff180c10;root.setBackgroundColor(bg);metrics.removeAllViews();int p=page.getSelectedItemPosition();double x=Math.sin(tick*.3);if(rpm.isChecked() && p!=2)card("ROTAÇÃO",String.valueOf((int)(2100+950*x)),"rpm");if(speed.isChecked() && p!=1)card("VELOCIDADE",String.valueOf((int)(65+18*x)),"km/h");if(boost.isChecked() && p!=2)card("PRESSÃO TURBO (simulada)",String.format(java.util.Locale.US,"%.2f",.65+.3*x),"bar");if(oil.isChecked() && p!=2)card("TEMPERATURA ÓLEO",String.valueOf(105+(tick%7)),"°C");if(coolant.isChecked() && p!=2)card("TEMPERATURA ÁGUA",String.valueOf(91+(tick%4)),"°C");if(battery.isChecked())card("TENSÃO BATERIA",String.format(java.util.Locale.US,"%.1f",14.0+.1*x),"V");}
}
