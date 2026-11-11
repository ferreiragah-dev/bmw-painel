package com.bmwcockpit.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs on an emulator with a real Android WebView and native Back dispatch. */
public class CockpitSmoke extends Instrumentation {
    private Activity activity;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent open = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(open);
            waitForScreen("performance");
            for (int i=0; i<4; i++) {
                tap("Telemetria"); waitForScreen("telemetry");
                tap("Diagnóstico"); waitForScreen("diagnostics");
                runOnMainSync(() -> activity.onBackPressed()); waitForScreen("telemetry");
                tap("Personalizar"); waitForScreen("editor");
                tap("Personalizar"); waitForScreen("themes");
                tap("Configurações"); waitForScreen("settings");
            }
            Instrumentation.ActivityMonitor recreated = addMonitor(MainActivity.class.getName(),null,false);
            runOnMainSync(() -> activity.recreate());
            Activity replacement = waitForMonitorWithTimeout(recreated,10000);
            removeMonitor(recreated);
            if (replacement == null) throw new AssertionError("Activity recreation timed out");
            activity = replacement;
            waitForScreen("settings");
            // Exercise the documented renderer termination path on Android Q+.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                final AtomicReference<WebView> before = new AtomicReference<>();
                runOnMainSync(() -> before.set(findWeb(activity.getWindow().getDecorView())));
                final boolean[] terminated = {false};
                runOnMainSync(() -> {
                    if(before.get().getWebViewRenderProcess()!=null)
                        terminated[0]=before.get().getWebViewRenderProcess().terminate();
                });
                if(terminated[0]) {
                    long deadline=SystemClock.elapsedRealtime()+12000;
                    final AtomicReference<WebView> current=new AtomicReference<>();
                    do {
                        runOnMainSync(() -> current.set(findWeb(activity.getWindow().getDecorView())));
                        if(current.get()!=null && current.get()!=before.get())break;
                        SystemClock.sleep(100);
                    } while(SystemClock.elapsedRealtime()<deadline);
                    if(current.get()==null||current.get()==before.get())throw new AssertionError("Renderer was not replaced");
                    waitForScreen("settings");
                }
                result.putString("renderer_restart",String.valueOf(terminated[0]));
            }
            result.putString("cockpit_smoke","PASS");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("cockpit_smoke","FAIL " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private WebView findWeb(View node) {
        if(node instanceof WebView)return (WebView)node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){WebView found=findWeb(group.getChildAt(i));if(found!=null)return found;}
        }
        return null;
    }
    private String eval(String expression) throws InterruptedException {
        AtomicReference<String> result=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(() -> {
            WebView w=findWeb(activity.getWindow().getDecorView());
            if(w==null)done.countDown();
            else w.evaluateJavascript(expression,value->{result.set(value);done.countDown();});
        });
        done.await(2,TimeUnit.SECONDS);
        return result.get();
    }
    private void tap(String label) throws Exception {
        String selector="button[aria-label=\""+label+"\"]";
        if(!"true".equals(eval("(function(){var b=document.querySelector('"+selector+"');if(!b)return false;b.click();return true;})()")))
            throw new AssertionError("Unavailable control: "+label);
    }
    private void waitForScreen(String expected) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<deadline){
            if(("\""+expected+"\"").equals(eval("document.getElementById('stage') && document.getElementById('stage').getAttribute('data-screen')")))return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Did not reach screen: "+expected);
    }
}
