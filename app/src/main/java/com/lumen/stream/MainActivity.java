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
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
  private static final String TAG = "KaiserWatch";
  private static final int PLAYER_REQUEST = 7206;
  private FrameLayout root;
  private WebView browser;
  private View fullscreen;
  private WebChromeClient.CustomViewCallback callback;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    CrashTracker.install(this,"Catalog");
    CrashTracker.record(this,"Catalog","Launched main screen");
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
    browser.addJavascriptInterface(new PlayerBridge(),"LUMEN_APP");
    browser.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (!"https".equals(uri.getScheme()) || !"app.lumen.local".equals(uri.getHost())) return null;
        if ("/icon.webp".equals(uri.getPath())) {
          return new WebResourceResponse("image/webp",null,getResources().openRawResource(R.drawable.kaiserwatch_icon));
        }
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
          Log.w(TAG,"Blocked external navigation in KaiserWatch: "+uri.getHost());
          return true;
        }
        return false;
      }
      @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Log.e(TAG,"WebView renderer exited; crashed="+detail.didCrash());
        CrashTracker.record(MainActivity.this,"Catalog WebView","Renderer exited, crashed="+detail.didCrash());
        hideVideo();
        if(view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        view.destroy();
        if(view==browser) {
          browser=null;
          createBrowser(); // Never reload the crashed third-party player automatically.
          Toast.makeText(MainActivity.this,
            "The KaiserWatch catalog recovered.",
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

  public class PlayerBridge {
    @JavascriptInterface public String diagnostics(){return CrashTracker.report(MainActivity.this);}
    @JavascriptInterface public void openEmbed(String url, String meta) {
      try {
        Uri parsed=Uri.parse(url);
        if(!"https".equalsIgnoreCase(parsed.getScheme()) ||
           !"vidlink.pro".equalsIgnoreCase(parsed.getHost()) ||
           parsed.getPath()==null ||
           !(parsed.getPath().startsWith("/movie/")||parsed.getPath().startsWith("/tv/")))return;
        final String payload = meta != null && meta.length() <= 300000 ? meta : "{}";
        runOnUiThread(()->{
          try {
            Intent playerIntent=new Intent(MainActivity.this,EmbedPlayerActivity.class);
            playerIntent.putExtra("url",url);
            playerIntent.putExtra("meta",payload);
            startActivityForResult(playerIntent,PLAYER_REQUEST);
          }catch(Exception e){Log.e(TAG,"Cannot open in-app embed player",e);
            Toast.makeText(MainActivity.this,"Could not open the in-app player",Toast.LENGTH_LONG).show();}
        });
      } catch(Exception e) { Log.e(TAG,"Invalid embed URL",e); }
    }

  }


  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode!=PLAYER_REQUEST||resultCode!=RESULT_OK||data==null||browser==null)return;
    String selection=data.getStringExtra("selection");
    if(selection==null||selection.length()>6000)return;
    browser.evaluateJavascript(
      "window.kaiserPlayerEnded && window.kaiserPlayerEnded("+
      org.json.JSONObject.quote(selection)+")",null);
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

  @Override protected void onResume(){
    super.onResume();
    if(browser!=null)browser.evaluateJavascript("window.kaiserRefresh && window.kaiserRefresh()",null);
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
