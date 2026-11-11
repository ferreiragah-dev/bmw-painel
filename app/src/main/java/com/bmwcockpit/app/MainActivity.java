package com.bmwcockpit.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;


import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String ENTRY = "file:///android_asset/index.html";
    private static final String[] SCREENS = {"performance","race","telemetry","layouts","editor",
            "diagnostics","timing","themes","settings"};
    private static final String[] TITLES = {"M Performance","Race Dashboard","Gráficos","Selecione um Painel",
            "Editor de Painel","Diagnóstico","Medições de Performance","Temas e Skins","Configurações"};
    private FrameLayout root;
    private WebView web;
    private volatile JSONObject snapshot;
    private boolean resumed;
    private boolean startingBack;
    private long lastRecovery;
    private long lastBack;
    private int galleryIndex;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(2,5,8));
        setContentView(root);
        immersive();
        if (saved != null) {
            try { snapshot = new JSONObject(saved.getString("screen", "{}")); }
            catch (org.json.JSONException ignored) { snapshot = null; }
            galleryIndex = Math.max(0, Math.min(SCREENS.length - 1, saved.getInt("gallery", 0)));
        }
        openCockpit();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) immersive();
    }

    private void openCockpit() {
        if (isFinishing() || isDestroyed()) return;
        try {
            WebView next = new WebView(this);
            web = next;
            next.setBackgroundColor(Color.rgb(2,5,8));
            WebSettings settings = next.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setBlockNetworkLoads(true);
            settings.setAllowContentAccess(false);
            next.addJavascriptInterface(new ScreenState(), "NativeScreen");
            next.setWebViewClient(Build.VERSION.SDK_INT >= 26 ? new RecoveringClient() : new LocalClient());
            root.removeAllViews();
            root.addView(next);
            next.loadUrl(ENTRY);
        } catch (RuntimeException | LinkageError unavailable) {
            releaseWeb();
            showGallery();
        }
    }

    private class LocalClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !ENTRY.equals(url);
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !ENTRY.equals(request.getUrl().toString());
        }
        @Override public void onPageFinished(WebView view, String url) {
            if (view != web || !ENTRY.equals(url)) return;
            if (snapshot != null && snapshot.has("route")) {
                view.evaluateJavascript("window.cockpitRestore && window.cockpitRestore(" + snapshot + ")", null);
            }
            view.evaluateJavascript("typeof window.cockpitBack === 'function'", result -> {
                if (view == web && !"true".equals(result)) { releaseWeb(); showGallery(); }
            });
        }
    }

    @TargetApi(26)
    private class RecoveringClient extends LocalClient {
        @Override public boolean onRenderProcessGone(WebView dead, RenderProcessGoneDetail detail) {
            if (dead != web) return true;
            releaseWeb();
            long now = SystemClock.elapsedRealtime();
            boolean repeated = lastRecovery != 0 && now - lastRecovery < 30000;
            lastRecovery = now;
            if (repeated) showGallery();
            else root.post(() -> { if (!isFinishing() && !isDestroyed()) openCockpit(); });
            return true;
        }
    }

    private void releaseWeb() {
        WebView old = web;
        web = null;
        startingBack = false;
        if (old != null) { root.removeView(old); old.destroy(); }
    }

    // If the installed WebView provider is unavailable, the nine offline views
    // remain accessible without restarting the Activity or using another renderer.
    private void showGallery() {
        root.removeAllViews();
        ImageView image = new ImageView(this);
        image.setContentDescription(TITLES[galleryIndex] + " · demonstração");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try (InputStream input = getAssets().open("screens/" + SCREENS[galleryIndex] + ".webp")) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            image.setImageBitmap(bitmap);
        } catch (IOException unavailable) {
            Toast.makeText(this, "Não foi possível abrir esta tela.", Toast.LENGTH_SHORT).show();
        }
        root.addView(image);
        image.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this).setItems(TITLES, (dialog, which) -> {
                galleryIndex = which; showGallery();
            }).setNeutralButton("Reabrir painel interativo", (dialog, which) -> openCockpit()).show();
        });
    }

    // This bridge only records UI state from the bundled, network-blocked page.
    public final class ScreenState {
        @JavascriptInterface public void remember(String json) {
            if (json == null || json.length() > 8192) return;
            try {
                JSONObject state = new JSONObject(json);
                String route = state.optString("route");
                for (String name : SCREENS) {
                    if (name.equals(route)) { snapshot = state; return; }
                }
            } catch (org.json.JSONException ignored) { /* Ignore incomplete state. */ }
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (snapshot != null) out.putString("screen", snapshot.toString());
        out.putInt("gallery", galleryIndex);
    }
    @Override protected void onPause() {
        resumed = false;
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (web != null) web.onResume();
    }
    @Override public void onBackPressed() {
        if (web == null) {
            if (galleryIndex != 0) { galleryIndex = 0; showGallery(); }
            else exitOnSecondBack();
            return;
        }
        if (startingBack) return;
        startingBack = true;
        WebView current = web;
        current.evaluateJavascript("window.cockpitBack ? window.cockpitBack() : true", consumed -> {
            if (web != current) return;
            startingBack = false;
            if ("false".equals(consumed) && resumed) exitOnSecondBack();
            else lastBack = 0;
        });
    }
    private void exitOnSecondBack() {
        long now = SystemClock.elapsedRealtime();
        if (lastBack != 0 && now - lastBack < 2000) finish();
        else { lastBack = now; Toast.makeText(this, "Pressione Voltar novamente para sair", Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onDestroy() {
        releaseWeb();
        super.onDestroy();
    }
}
