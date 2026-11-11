package com.lumen.stream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
  private static final String TAG = "LumenAndroid";
  private FrameLayout root;
  private WebView browser;
  private View fullscreen;
  private WebChromeClient.CustomViewCallback callback;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setStatusBarColor(Color.rgb(10,10,14));
    getWindow().setNavigationBarColor(Color.rgb(10,10,14));
    root = new FrameLayout(this);
    setContentView(root);
    createBrowser();
  }

  private void createBrowser() {
    browser = new WebView(this);
    browser.setBackgroundColor(Color.rgb(10,10,14));
    WebSettings settings = browser.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setSupportMultipleWindows(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    browser.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (!"https".equals(uri.getScheme()) || !"app.lumen.local".equals(uri.getHost())) return null;
        if (!"/index.html".equals(uri.getPath()) && !"/".equals(uri.getPath())) {
          return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
        }
        try {
          InputStream stream = getAssets().open("index.html");
          return new WebResourceResponse("text/html","UTF-8",stream);
        } catch(IOException error) {
          Log.e(TAG,"Cannot load bundled HTML",error);
          return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
        }
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if("app.lumen.local".equals(uri.getHost()))return false;
        if(request.isForMainFrame()) {
          if("https".equals(uri.getScheme())) openExternal(uri);
          return true;
        }
        return false;
      }
      @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Log.e(TAG,"WebView renderer exited; crashed="+detail.didCrash());
        hideVideo();
        if(view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        view.destroy();
        if(view==browser) {
          browser=null;
          createBrowser(); // Never reload the crashed third-party player automatically.
          Toast.makeText(MainActivity.this,
            "The video player stopped unexpectedly. LUMEN has recovered; try browser playback.",
            Toast.LENGTH_LONG).show();
        }
        return true;
      }
    });
    browser.setWebChromeClient(new WebChromeClient() {
      @Override public void onShowCustomView(View view, CustomViewCallback c) {
        if(fullscreen!=null)hideVideo();
        fullscreen=view;
        callback=c;
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        browser.setVisibility(View.GONE);
      }
      @Override public void onHideCustomView() { hideVideo(); }
    });
    root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
    browser.loadUrl("https://app.lumen.local/index.html");
  }

  private void openExternal(Uri url) {
    try {
      Intent intent=new Intent(Intent.ACTION_VIEW,url);
      startActivity(intent);
    }catch(Exception error){
      Log.e(TAG,"No browser can open "+url.getHost(),error);
      Toast.makeText(this,"No browser available to open video. Install or enable a browser.",Toast.LENGTH_LONG).show();
    }
  }

  private void hideVideo() {
    if(fullscreen!=null) {
      root.removeView(fullscreen);
      fullscreen=null;
      if(browser!=null)browser.setVisibility(View.VISIBLE);
      WebChromeClient.CustomViewCallback cb=callback;
      callback=null;
      if(cb!=null)cb.onCustomViewHidden();
    }
  }

  @Override public void onBackPressed() {
    if(fullscreen!=null) { hideVideo(); return; }
    if(browser==null) { super.onBackPressed(); return; }
    browser.evaluateJavascript("Boolean(window.lumenBack && window.lumenBack())", result -> {
      if(!"true".equals(result)) MainActivity.super.onBackPressed();
    });
  }

  @Override protected void onDestroy(){
    hideVideo();
    if(browser!=null) {
      root.removeView(browser);
      browser.destroy();
      browser=null;
    }
    super.onDestroy();
  }
}
