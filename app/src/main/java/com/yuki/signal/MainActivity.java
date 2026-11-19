package com.yuki.signal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.os.*;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import org.json.*;

public class MainActivity extends Activity {
  static final String API = "https://jxzmkeufooycdlzbhsih.supabase.co/rest/v1/signal?select=*&limit=1";
  static final String KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imp4em1rZXVmb295Y2RsemJoc2loIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3NzU3MDcsImV4cCI6MjEwNjM1MTcwN30.QheYWXn_CFxAly5-tOuoB1ioRYQU2rCxsu7WSZMN-kg";

  TextView tv;
  Handler h = new Handler(Looper.getMainLooper());
  BluetoothLeAdvertiser adv;
  AdvertiseCallback cb;
  String last = "", info = "连接中…", st = "";

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    tv = new TextView(this);
    tv.setTextSize(18);
    tv.setPadding(40, 100, 40, 40);
    setContentView(tv);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{"android.permission.BLUETOOTH_ADVERTISE", "android.permission.BLUETOOTH_CONNECT"}, 1);
    BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
    if (a == null) st = "这台机子没有蓝牙";
    else if (!a.isEnabled()) st = "蓝牙没开，开了再重进";
    else { adv = a.getBluetoothLeAdvertiser(); if (adv == null) st = "不支持BLE广播"; }
    show();
    h.post(loop);
  }

  void show() { tv.setText(info + "\n\n" + st); }

  Runnable loop = new Runnable() { public void run() { fetch(); h.postDelayed(this, 1000); } };

  void fetch() {
    new Thread(() -> {
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setRequestProperty("apikey", KEY);
        c.setRequestProperty("Authorization", "Bearer " + KEY);
        c.setConnectTimeout(5000); c.setReadTimeout(5000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        JSONArray arr = new JSONArray(sb.toString());
        if (arr.length() == 0) { ui("表是空的"); return; }
        JSONObject o = arr.getJSONObject(0);
        boolean on = o.optBoolean("on_air", false);
        Object ci = o.opt("company_id");
        int cid = 0;
        if (ci instanceof Number) cid = ((Number) ci).intValue();
        else if (ci != null && ci != JSONObject.NULL) cid = Integer.decode(ci.toString().trim());
        String hex = o.optString("data_hex", "");
        String mode = o.optString("mode", "");
        String k = on + "|" + cid + "|" + hex;
        final int fc = cid;
        runOnUiThread(() -> {
          info = "on_air: " + on + "\nmode: " + mode + "\ncompany_id: " + fc + "\ndata: " + hex;
          if (!k.equals(last)) { last = k; apply(on, fc, hex); }
          show();
        });
      } catch (Exception e) { ui("读表失败: " + e.getMessage()); }
    }).start();
  }

  void ui(String s) { runOnUiThread(() -> { info = s; show(); }); }

  void stopAdv() {
    try { if (adv != null && cb != null) adv.stopAdvertising(cb); } catch (SecurityException e) {}
    cb = null;
  }

  void apply(boolean on, int cid, String hex) {
    stopAdv();
    if (!on) { st = "待命中"; return; }
    if (adv == null) return;
    byte[] d = parse(hex);
    AdvertiseSettings s = new AdvertiseSettings.Builder()
      .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
      .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
      .setConnectable(false).build();
    AdvertiseData ad = new AdvertiseData.Builder()
      .setIncludeDeviceName(false)
      .addManufacturerData(cid, d).build();
    cb = new AdvertiseCallback() {
      public void onStartSuccess(AdvertiseSettings x) { st = "正在发送"; show(); }
      public void onStartFailure(int code) { st = "发送失败 code=" + code; show(); }
    };
    try { adv.startAdvertising(s, ad, cb); } catch (SecurityException e) { st = "没给蓝牙权限"; }
  }

  static byte[] parse(String hex) {
    String x = hex.replaceAll("[^0-9a-fA-F]", "");
    if (x.length() % 2 == 1) x = "0" + x;
    byte[] b = new byte[x.length() / 2];
    for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(x.substring(i * 2, i * 2 + 2), 16);
    return b;
  }

  @Override protected void onDestroy() { h.removeCallbacks(loop); stopAdv(); super.onDestroy(); }
}
