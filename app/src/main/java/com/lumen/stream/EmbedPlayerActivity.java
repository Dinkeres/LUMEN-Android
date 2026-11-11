package com.lumen.stream;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EmbedPlayerActivity extends Activity {
  private static final String TAG = "KaiserWatchPlayer";
  private static final int BG = Color.rgb(9, 9, 13);
  private static final int PANEL = Color.rgb(25, 22, 29);
  private static final int GOLD = Color.rgb(255, 208, 82);
  private static final int MUTED = Color.rgb(183, 172, 179);

  private FrameLayout root, playerSlot;
  private LinearLayout page;
  private WebView web;
  private TextView nowPlaying, nextSummary, status;
  private Button nextButton, episodesButton;
  private View fullScreenView;
  private WebChromeClient.CustomViewCallback fullScreenCallback;
  private Dialog episodeDialog;
  private String baseUrl, title = "Now Playing", titleId = "", type = "movie", tmdb = "";
  private int season = 1, number = 1;
  private final List<Episode> episodes = new ArrayList<>();

  private static class Episode {
    int season, number;
    String title, released, id;
    Episode(int s, int n, String t, String r, String id) {
      this.season = s; this.number = n; this.title = t; this.released = r; this.id = id;
    }
  }

  @Override public void onCreate(Bundle state) {
    // A dedicated Android process isolates unstable third-party web video from the catalog.
    if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("lumen_embed");
    super.onCreate(state);
    CrashTracker.install(this, "KaiserWatchPlayer");
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().setNavigationBarColor(BG);
    baseUrl = getIntent().getStringExtra("url");
    if (!validUrl(baseUrl)) { finish(); return; }
    readMetadata(getIntent().getStringExtra("meta"));
    buildPlayerScreen();
    reportEpisode();
    createWeb();
    loadEpisode();
  }

  private static boolean validUrl(String url) {
    try {
      Uri uri = Uri.parse(url);
      return "https".equalsIgnoreCase(uri.getScheme())
        && "vidlink.pro".equalsIgnoreCase(uri.getHost())
        && uri.getPath() != null
        && (uri.getPath().matches("/movie/[0-9]+")
        || uri.getPath().matches("/tv/[0-9]+/[0-9]+/[0-9]+"));
    } catch(Exception ex) { return false; }
  }

  private void readMetadata(String json) {
    try {
      JSONObject m = new JSONObject(json == null ? "{}" : json);
      title = m.optString("name", "Now Playing");
      titleId = m.optString("id", "");
      type = m.optString("type", "movie");
      tmdb = m.optString("tmdb", "");
      season = m.optInt("season", 1);
      number = m.optInt("episode", 1);
      JSONArray list = m.optJSONArray("episodes");
      if (list != null) {
        for (int i=0; i<list.length() && i<2400; i++) {
          JSONObject e = list.optJSONObject(i);
          if (e==null) continue;
          int s=e.optInt("s", 0), n=e.optInt("e", 0);
          if (s<=0 || n<=0) continue;
          episodes.add(new Episode(s,n,e.optString("t","Episode "+n),
            e.optString("r",""),e.optString("id","")));
        }
      }
      Collections.sort(episodes, Comparator.comparingInt((Episode e)->e.season)
        .thenComparingInt(e->e.number));
    } catch(Exception ex) {
      Log.w(TAG,"Could not read episode metadata",ex);
    }
    // Keep the episode URL the source of truth, even without optional metadata.
    List<String> parts = Uri.parse(baseUrl).getPathSegments();
    if (parts.size()==4 && "tv".equals(parts.get(0))) {
      type="series";tmdb=parts.get(1);
      try { season=Integer.parseInt(parts.get(2));number=Integer.parseInt(parts.get(3)); }
      catch(NumberFormatException ignored){}
    } else if (parts.size()==2 && "movie".equals(parts.get(0))) {
      type="movie";tmdb=parts.get(1);
    }
  }

  private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  private GradientDrawable shape(int color, int border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color); d.setCornerRadius(dp(13));
    if(border!=0) d.setStroke(dp(1), border);
    return d;
  }
  private Button button(String label, boolean primary) {
    Button b = new Button(this);
    b.setAllCaps(false);b.setText(label);b.setTextSize(13);
    b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);
    b.setTextColor(primary?Color.BLACK:Color.WHITE);
    b.setBackground(shape(primary?GOLD:Color.rgb(49,42,52),primary?0:Color.rgb(71,55,67)));
    b.setPadding(dp(11),dp(2),dp(11),dp(2));
    b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
    return b;
  }
  private TextView label(String text, float sp, int color) {
    TextView v = new TextView(this);
    v.setText(text);v.setTextColor(color);v.setTextSize(sp);
    return v;
  }

  private void buildPlayerScreen() {
    root = new FrameLayout(this); root.setBackgroundColor(BG);
    page = new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
    root.addView(page,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout toolbar=new LinearLayout(this);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(10),dp(7),dp(10),dp(7));
    toolbar.setBackgroundColor(Color.rgb(20,17,23));
    page.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(65)));

    Button back=button("←",false);
    back.setOnClickListener(v->finish());
    toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(45)));

    LinearLayout heading=new LinearLayout(this);
    heading.setOrientation(LinearLayout.VERTICAL);
    heading.setPadding(dp(12),0,dp(8),0);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView eyebrow=label("KAISERWATCH  ·  NOW PLAYING",10,GOLD);
    heading.addView(eyebrow);
    nowPlaying=label("",15,Color.WHITE);nowPlaying.setSingleLine(true);
    nowPlaying.setEllipsize(TextUtils.TruncateAt.END);
    heading.addView(nowPlaying);
    toolbar.addView(heading,new LinearLayout.LayoutParams(0,-1,1));

    episodesButton=button("Episodes",false);
    episodesButton.setOnClickListener(v->showEpisodes());
    toolbar.addView(episodesButton,new LinearLayout.LayoutParams(dp(90),dp(45)));

    nextButton=button("Next ›",true);
    nextButton.setOnClickListener(v->playNext());
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(76),dp(45));
    np.leftMargin=dp(7);
    toolbar.addView(nextButton,np);

    playerSlot=new FrameLayout(this);
    playerSlot.setBackgroundColor(Color.BLACK);
    page.addView(playerSlot,new LinearLayout.LayoutParams(-1,0,1));

    LinearLayout bottom=new LinearLayout(this);
    bottom.setOrientation(LinearLayout.VERTICAL);
    bottom.setPadding(dp(16),dp(10),dp(16),dp(12));
    bottom.setBackgroundColor(PANEL);
    page.addView(bottom,new LinearLayout.LayoutParams(-1,-2));

    nextSummary=label("",13,Color.WHITE);
    nextSummary.setMaxLines(2);
    bottom.addView(nextSummary);
    status=label("Loading player…",11,MUTED);
    LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);
    statusParams.topMargin=dp(4);
    bottom.addView(status,statusParams);

    setContentView(root);
    refreshLabels();
  }

  private boolean released(Episode e) {
    if(e.released==null||e.released.length()<10)return true;
    try {return !LocalDate.parse(e.released.substring(0,10)).isAfter(LocalDate.now());}
    catch(Exception ignored){return true;}
  }
  private boolean newEpisode(Episode e) {
    if(e.released==null||e.released.length()<10)return false;
    try {
      long age=ChronoUnit.DAYS.between(LocalDate.parse(e.released.substring(0,10)),LocalDate.now());
      return age>=0&&age<=14;
    }catch(Exception ignored){return false;}
  }
  private Episode findNext() {
    if(!"series".equals(type))return null;
    for(Episode e:episodes) if((e.season>season||(e.season==season&&e.number>number))&&released(e))return e;
    return null;
  }
  private void refreshLabels() {
    if(nowPlaying==null)return;
    boolean show="series".equals(type);
    String numberLabel=show?"  ·  S"+season+" E"+number:"";
    nowPlaying.setText(title+numberLabel);
    episodesButton.setVisibility(show?View.VISIBLE:View.GONE);
    Episode next=findNext();
    nextButton.setVisibility(next==null?View.GONE:View.VISIBLE);
    if(show) {
      if(next!=null) {
        nextSummary.setText("UP NEXT   S"+next.season+" E"+next.number+"  ·  "+next.title
          +(newEpisode(next)?"  •  NEW":""));
      } else {
        nextSummary.setText("You're on the latest listed episode. Use Episodes to browse this series.");
      }
    } else nextSummary.setText("Enjoy the movie. Use ← to return to the KaiserWatch catalog.");
  }

  private void reportEpisode() {
    try {
      JSONObject out=new JSONObject();
      out.put("id",titleId);out.put("type",type);out.put("tmdb",tmdb);
      out.put("season",season);out.put("episode",number);
      for(Episode e:episodes) if(e.season==season&&e.number==number) {
        out.put("episodeId",e.id);break;
      }
      setResult(RESULT_OK,new Intent().putExtra("selection",out.toString()));
    }catch(Exception ex){Log.w(TAG,"Could not report episode selection",ex);}
  }

  private void playNext() {
    Episode next=findNext();
    if(next!=null)switchEpisode(next);
  }
  private void switchEpisode(Episode chosen) {
    if(chosen==null || !released(chosen))return;
    if(episodeDialog!=null&&episodeDialog.isShowing())episodeDialog.dismiss();
    hideFullscreen();
    season=chosen.season;number=chosen.number;
    reportEpisode();refreshLabels();loadEpisode();
  }

  private void showEpisodes() {
    if(!"series".equals(type))return;
    final Dialog dialog=new Dialog(this);
    episodeDialog=dialog;
    LinearLayout sheet=new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setPadding(dp(19),dp(16),dp(19),dp(18));
    sheet.setBackground(shape(Color.rgb(26,22,30),Color.rgb(79,54,64)));

    LinearLayout head=new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=label("Episodes  ·  "+title,19,Color.WHITE);
    name.setTypeface(null,1);name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    Button close=button("✕",false);
    close.setOnClickListener(v->dialog.dismiss());
    head.addView(close,new LinearLayout.LayoutParams(dp(50),dp(43)));
    sheet.addView(head);
    TextView explanation=label("Switch seasons or tap an episode. Newly released episodes are marked NEW.",12,MUTED);
    explanation.setPadding(0,dp(8),0,dp(10));
    sheet.addView(explanation);

    List<Integer> seasons=new ArrayList<>();
    for(Episode e:episodes)if(!seasons.contains(e.season))seasons.add(e.season);
    if(seasons.isEmpty()){
      sheet.addView(label("No episode information is available for this series.",14,MUTED));
    }else{
      Spinner spinner=new Spinner(this);
      List<String> seasonLabels=new ArrayList<>();
      for(int s:seasons)seasonLabels.add("Season "+s);
      ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,seasonLabels);
      spinner.setAdapter(adapter);
      sheet.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));
      ScrollView scroll=new ScrollView(this);
      scroll.setFillViewport(false);
      LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
      scroll.addView(rows);
      sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
      spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
          int s=seasons.get(position);
          rows.removeAllViews();
          for(Episode ep:episodes) {
            if(ep.season!=s)continue;
            boolean upcoming=!released(ep);
            String caption="E"+ep.number+"  ·  "+ep.title;
            if(newEpisode(ep))caption+="   ★ NEW";
            else if(upcoming)caption+="   · Upcoming";
            else if(ep.released!=null&&ep.released.length()>=10)caption+="   · "+ep.released.substring(0,10);
            if(ep.season==season&&ep.number==number)caption="▶ "+caption;
            Button b=button(caption,ep.season==season&&ep.number==number);
            b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setEnabled(!upcoming);
            b.setAlpha(upcoming?.45f:1f);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(52));
            bp.bottomMargin=dp(7);
            rows.addView(b,bp);
            b.setOnClickListener(w->switchEpisode(ep));
          }
        }
        @Override public void onNothingSelected(AdapterView<?> p){}
      });
      int selected=seasons.indexOf(season);
      spinner.setSelection(Math.max(selected,0));
    }

    dialog.setContentView(sheet);
    Window window=dialog.getWindow();
    if(window!=null) {
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams lp=window.getAttributes();
      lp.gravity=Gravity.BOTTOM;lp.dimAmount=.74f;
      window.setAttributes(lp);
    }
    dialog.show();
    window=dialog.getWindow();
    if(window!=null)window.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.78f));
  }

  private String activeUrl() {
    if(!"series".equals(type))return baseUrl;
    Uri old=Uri.parse(baseUrl);
    return old.buildUpon().path("/tv/"+tmdb+"/"+season+"/"+number).build().toString();
  }
  private void loadEpisode() {
    String url=activeUrl();
    if(!validUrl(url)){
      status.setText("The episode URL is invalid.");
      return;
    }
    CrashTracker.record(this,"KaiserWatchPlayer","Loading "+type+" S"+season+" E"+number);
    status.setText("Loading "+("series".equals(type)?"S"+season+" E"+number:"movie")+"…");
    if(web==null)createWeb();
    if(web!=null)web.loadUrl(url);
  }

  private void createWeb() {
    if(web!=null)return;
    try{
      WebView w=new WebView(this);
      web=w;
      w.setBackgroundColor(Color.BLACK);
      WebSettings settings=w.getSettings();
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
      CookieManager.getInstance().setAcceptThirdPartyCookies(w,true);
      w.setWebViewClient(new WebViewClient(){
        @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
          Uri next=request.getUrl();
          if(!"https".equalsIgnoreCase(next.getScheme()))return true;
          if(request.isForMainFrame()&&!"vidlink.pro".equalsIgnoreCase(next.getHost())){
            status.setText("A third-party redirect was blocked.");
            return true;
          }
          return false;
        }
        @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
          if(request.isForMainFrame()){
            CrashTracker.record(EmbedPlayerActivity.this,"Video network", "Code="+error.getErrorCode());
            status.setText("Network problem loading the player. Try again.");
          }
        }
        @Override public void onPageFinished(WebView view,String url){
          status.setText("Player ready. Choose Play in the video, or select another episode above.");
        }
        @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
          CrashTracker.record(EmbedPlayerActivity.this,"Video renderer","Exited; crash="+detail.didCrash());
          if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
          if(view==web)web=null;
          view.destroy();
          status.setText("Video player stopped unexpectedly. Return to the catalog and retry.");
          Toast.makeText(EmbedPlayerActivity.this,"Video player stopped, but KaiserWatch is still open.",Toast.LENGTH_LONG).show();
          return true;
        }
      });
      w.setWebChromeClient(new WebChromeClient(){
        @Override public void onShowCustomView(View v,CustomViewCallback cb){
          hideFullscreen();
          fullScreenView=v;fullScreenCallback=cb;
          if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
          w.setVisibility(View.GONE);
          // Fullscreen video stays inside the player slot, leaving Episodes and Next accessible.
          playerSlot.addView(v,new FrameLayout.LayoutParams(-1,-1));
        }
        @Override public void onHideCustomView(){hideFullscreen();}
      });
      playerSlot.addView(w,new FrameLayout.LayoutParams(-1,-1));
    }catch(Exception ex){
      CrashTracker.record(this,"KaiserWatchPlayer","WebView init error "+ex.getClass().getSimpleName());
      status.setText("Could not load the video player.");
    }
  }

  private void hideFullscreen(){
    if(fullScreenView==null)return;
    if(fullScreenView.getParent() instanceof ViewGroup)
      ((ViewGroup)fullScreenView.getParent()).removeView(fullScreenView);
    fullScreenView=null;
    if(web!=null)web.setVisibility(View.VISIBLE);
    WebChromeClient.CustomViewCallback cb=fullScreenCallback;
    fullScreenCallback=null;
    if(cb!=null)cb.onCustomViewHidden();
  }

  @Override public void onBackPressed(){
    if(episodeDialog!=null&&episodeDialog.isShowing()){episodeDialog.dismiss();return;}
    if(fullScreenView!=null){hideFullscreen();return;}
    finish();
  }
  @Override protected void onPause(){
    if(web!=null)web.onPause();
    super.onPause();
  }
  @Override protected void onResume(){
    super.onResume();
    if(web!=null)web.onResume();
  }
  @Override protected void onDestroy(){
    if(episodeDialog!=null&&episodeDialog.isShowing())episodeDialog.dismiss();
    hideFullscreen();
    if(web!=null){
      web.stopLoading();
      if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);
      web.destroy();web=null;
    }
    super.onDestroy();
  }
}
