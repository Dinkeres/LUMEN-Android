package com.lumen.stream;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.widget.FrameLayout;
import android.content.Intent;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends Activity {
  private FrameLayout root; private WebView browser; private View fullscreen; private WebChromeClient.CustomViewCallback callback;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.rgb(10,10,14)); getWindow().setNavigationBarColor(Color.rgb(10,10,14));
    root = new FrameLayout(this); setContentView(root);
    browser = new WebView(this); browser.setBackgroundColor(Color.rgb(10,10,14));
    browser.getSettings().setJavaScriptEnabled(true);
    browser.getSettings().setDomStorageEnabled(true);
    browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
    browser.getSettings().setAllowFileAccess(false);
    browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
    browser.getSettings().setSupportMultipleWindows(false);
    browser.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    browser.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
        Uri u=request.getUrl();
        if(!"https".equals(u.getScheme()) || !"app.lumen.local".equals(u.getHost())) return null;
        if(!"/index.html".equals(u.getPath()) && !"/".equals(u.getPath())) return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
        try { InputStream in=getAssets().open("index.html"); return new WebResourceResponse("text/html","UTF-8",in); }
        catch(IOException e) {return null;}
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
        Uri u=request.getUrl(); if("app.lumen.local".equals(u.getHost())) return false;
        if(request.isForMainFrame()) {
          if("https".equals(u.getScheme())) try {startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
          return true;
        }
        return false;
      }
    });
    browser.setWebChromeClient(new WebChromeClient() {
      @Override public void onShowCustomView(View v, CustomViewCallback c) {
        fullscreen=v; callback=c; root.addView(v,new FrameLayout.LayoutParams(-1,-1)); browser.setVisibility(View.GONE);
      }
      @Override public void onHideCustomView() { hideVideo(); }
    });
    root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
    browser.loadUrl("https://app.lumen.local/index.html");
  }
  private void hideVideo() { if(fullscreen!=null){root.removeView(fullscreen);fullscreen=null;browser.setVisibility(View.VISIBLE);if(callback!=null){callback.onCustomViewHidden();callback=null;}} }
  @Override public void onBackPressed() {if(fullscreen!=null){hideVideo();return;}browser.evaluateJavascript("window.lumenBack && window.lumenBack()",result->{if(!"true".equals(result)) MainActivity.super.onBackPressed();});}
  @Override protected void onDestroy(){hideVideo();browser.destroy();super.onDestroy();}
}
