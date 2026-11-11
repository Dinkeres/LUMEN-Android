package com.lumen.stream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class EmbedPlayerActivity extends Activity {
  private static final String TAG = "LumenEmbed";
  private FrameLayout root;
  private FrameLayout playerSlot;
  private LinearLayout page;
  private WebView playerWebView;
  private TextView status;
  private View fullscreenView;
  private WebChromeClient.CustomViewCallback fullscreenCallback;
  private String baseUrl;

  @Override public void onCreate(Bundle state) {
    // This Activity runs in a separate process so an unstable web player cannot kill the catalog.
    // Android 9+ requires each process using WebView to have its own data directory.
    if(Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("lumen_embed");
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(8,9,15));
    getWindow().setNavigationBarColor(Color.rgb(8,9,15));
    String incoming=getIntent().getStringExtra("url");
    if(!isVidlinkUrl(incoming)) { finish(); return; }
    baseUrl=incoming;

    root=new FrameLayout(this);
    root.setBackgroundColor(Color.rgb(8,9,15));
    page=new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(Color.rgb(8,9,15));
    root.addView(page,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout toolbar=new LinearLayout(this);
    toolbar.setOrientation(LinearLayout.HORIZONTAL);
    toolbar.setPadding(dp(6),dp(8),dp(6),dp(8));
    toolbar.setBackgroundColor(Color.rgb(19,20,29));
    page.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));

    Button back=button("← LUMEN");
    back.setOnClickListener(v->finish());
    toolbar.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));

    Button jw=button("Mobile / JW");
    jw.setOnClickListener(v->loadPlayer(true));
    toolbar.addView(jw,new LinearLayout.LayoutParams(0,dp(50),1));

    Button standard=button("Standard");
    standard.setOnClickListener(v->loadPlayer(false));
    toolbar.addView(standard,new LinearLayout.LayoutParams(0,dp(50),1));

    playerSlot=new FrameLayout(this);
    page.addView(playerSlot,new LinearLayout.LayoutParams(-1,0,1));

    status=new TextView(this);
    status.setTextSize(12);
    status.setTextColor(Color.rgb(170,174,187));
    status.setPadding(dp(12),dp(9),dp(12),dp(12));
    status.setText("Vidlink mobile player — if this player fails, try Standard above.");
    page.addView(status,new LinearLayout.LayoutParams(-1,-2));

    setContentView(root);
    loadPlayer(true);
  }

  private int dp(int val){return (int)(val*getResources().getDisplayMetrics().density+.5f);}
  private Button button(String text) {
    Button b=new Button(this);
    b.setText(text); b.setTextSize(12);b.setAllCaps(false);
    b.setTextColor(Color.WHITE);
    return b;
  }
  private static boolean isVidlinkUrl(String text) {
    if(text==null)return false;
    Uri u=Uri.parse(text);
    return "https".equalsIgnoreCase(u.getScheme()) &&
      "vidlink.pro".equalsIgnoreCase(u.getHost()) &&
      (u.getPath()!=null && (u.getPath().startsWith("/movie/") || u.getPath().startsWith("/tv/")));
  }

  private void loadPlayer(boolean jw) {
    releaseWeb();
    Uri source=Uri.parse(baseUrl);
    Uri.Builder builder=source.buildUpon().clearQuery();
    for(String name:source.getQueryParameterNames()) {
      if(!"player".equals(name)) {
        for(String value:source.getQueryParameters(name)) builder.appendQueryParameter(name,value);
      }
    }
    if(jw)builder.appendQueryParameter("player","jw");
    String uri=builder.build().toString();

    status.setText(jw?"Loading Vidlink mobile/JW engine…":"Loading Vidlink standard engine…");
    try {
      WebView web=new WebView(this);
      playerWebView=web;
      web.setBackgroundColor(Color.BLACK);
      WebSettings settings=web.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setMediaPlaybackRequiresUserGesture(true);
      settings.setJavaScriptCanOpenWindowsAutomatically(false);
      settings.setSupportMultipleWindows(false);
      settings.setAllowFileAccess(false);
      settings.setAllowContentAccess(false);
      settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      settings.setUseWideViewPort(true);
      settings.setLoadWithOverviewMode(true);
      CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
      web.setWebViewClient(new WebViewClient(){
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
          Uri next=req.getUrl();
          if(!"https".equalsIgnoreCase(next.getScheme()))return true;
          if(req.isForMainFrame() && !"vidlink.pro".equalsIgnoreCase(next.getHost())) {
            status.setText("A redirect outside the player was blocked. Try the other player engine.");
            return true;
          }
          return false;
        }
        @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error) {
          if(req.isForMainFrame())status.setText("Player network error: "+error.getErrorCode()+". Try the other engine.");
        }
        @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail) {
          Log.e(TAG,"Embed renderer exited, didCrash="+detail.didCrash());
          if(view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
          if(view==playerWebView)playerWebView=null;
          view.destroy();
          status.setText("This player engine stopped unexpectedly. Try the other engine or return to LUMEN.");
          Toast.makeText(EmbedPlayerActivity.this,"Video engine stopped. LUMEN is still running.",Toast.LENGTH_LONG).show();
          return true;
        }
      });
      web.setWebChromeClient(new WebChromeClient(){
        @Override public void onShowCustomView(View v,CustomViewCallback cb){
          hideFullscreen();
          fullscreenView=v;
          fullscreenCallback=cb;
          if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
          page.setVisibility(View.GONE);
          root.addView(v,new FrameLayout.LayoutParams(-1,-1));
        }
        @Override public void onHideCustomView(){hideFullscreen();}
      });
      playerSlot.addView(web,new FrameLayout.LayoutParams(-1,-1));
      web.loadUrl(uri);
    }catch(Exception ex) {
      Log.e(TAG,"Unable to open in-app player",ex);
      status.setText("Could not load this engine. Try Standard or close the player.");
    }
  }

  private void hideFullscreen() {
    if(fullscreenView==null)return;
    root.removeView(fullscreenView);
    fullscreenView=null;
    page.setVisibility(View.VISIBLE);
    WebChromeClient.CustomViewCallback cb=fullscreenCallback;
    fullscreenCallback=null;
    if(cb!=null)cb.onCustomViewHidden();
  }
  private void releaseWeb() {
    hideFullscreen();
    if(playerWebView!=null) {
      playerWebView.stopLoading();
      playerSlot.removeView(playerWebView);
      playerWebView.destroy();
      playerWebView=null;
    }
  }
  @Override public void onBackPressed(){if(fullscreenView!=null)hideFullscreen();else finish();}
  @Override protected void onDestroy(){releaseWeb();super.onDestroy();}
}
