package com.lumen.stream;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private Button nextButton;
  private View episodesButton;
  private final Handler imageHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService imagePool = Executors.newFixedThreadPool(3);
  private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(1024*12) {
    @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1,bitmap.getByteCount()/1024); }
  };
  private View fullScreenView;
  private WebChromeClient.CustomViewCallback fullScreenCallback;
  private Dialog episodeDialog;
  private String baseUrl, title = "Now Playing", titleId = "", type = "movie", tmdb = "";
  private int season = 1, number = 1;
  private final List<Episode> episodes = new ArrayList<>();

  private static class Episode {
    int season, number;
    String title, released, id, thumbnail, synopsis;
    boolean watched;
    Episode(int s, int n, String t, String r, String id, String img, String desc, boolean done) {
      this.season = s; this.number = n; this.title = t; this.released = r; this.id = id;
      this.thumbnail = img; this.synopsis = desc; this.watched = done;
    }
  }

  @Override public void onCreate(Bundle state) {
    // A dedicated Android process isolates unstable third-party web video from the catalog.
    if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("lumen_embed");
    super.onCreate(state);
    CrashTracker.install(this, "KaiserWatchPlayer");
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().setNavigationBarColor(BG);
    // Prevent normal Android screen timeout while watching inside KaiserWatch.
    // This is a window flag, not a CPU wakelock. It is cleared when playback
    // leaves the foreground; it will not prevent manual power-button locking.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
            e.optString("r",""),e.optString("id",""),
            e.optString("img",""),e.optString("desc",""),e.optBoolean("watched",false)));
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


  private View episodePickerButton() {
    View v=new View(this) {
      final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(dp(2.7f));
        paint.setStyle(Paint.Style.STROKE);
        float x=getWidth()/2f-dp(15), y=getHeight()/2f-dp(12);
        canvas.drawRoundRect(x+dp(8),y-dp(6),x+dp(30),y+dp(11),dp(2),dp(2),paint);
        canvas.drawRoundRect(x+dp(4),y-dp(2),x+dp(26),y+dp(17),dp(2),dp(2),paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        canvas.drawRoundRect(x,y+dp(4),x+dp(22),y+dp(23),dp(2),dp(2),paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(x,y+dp(4),x+dp(22),y+dp(23),dp(2),dp(2),paint);
      }
    };
    v.setBackground(shape(Color.rgb(49,42,52),Color.rgb(71,55,67)));
    v.setContentDescription("Browse seasons and episodes");
    v.setClickable(true);v.setFocusable(true);
    v.setOnClickListener(w->showEpisodes());
    return v;
  }

  private void loadThumbnail(ImageView target,String remote) {
    if(remote==null||!remote.startsWith("https://"))return;
    target.setTag(remote);
    Bitmap cached=imageCache.get(remote);
    if(cached!=null) {target.setImageBitmap(cached);return;}
    imagePool.execute(()->{
      Bitmap result=null;
      HttpURLConnection connection=null;
      try {
        connection=(HttpURLConnection)new URL(remote).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent","KaiserWatch/2.2");
        if(connection.getResponseCode()==200 && connection.getContentLengthLong()<3_000_000) {
          try(InputStream in=connection.getInputStream()) {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=2;
            result=BitmapFactory.decodeStream(in,null,options);
          }
        }
      }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
      if(result!=null) {
        final Bitmap img=result;
        imageCache.put(remote,img);
        imageHandler.post(()->{
          if(!isFinishing()&&remote.equals(target.getTag()))target.setImageBitmap(img);
        });
      }
    });
  }

  private void buildPlayerScreen() {
    root = new FrameLayout(this); root.setBackgroundColor(BG);
    root.setKeepScreenOn(true);
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

    episodesButton=episodePickerButton();
    toolbar.addView(episodesButton,new LinearLayout.LayoutParams(dp(52),dp(47)));

    nextButton=button("Next ›",true);
    nextButton.setOnClickListener(v->playNext());
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(76),dp(45));
    np.leftMargin=dp(7);
    toolbar.addView(nextButton,np);

    playerSlot=new FrameLayout(this);
    playerSlot.setBackgroundColor(Color.BLACK);
    playerSlot.setKeepScreenOn(true);
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

  // Netflix-inspired episode overlay, kept in the native Android activity so
  // viewers can switch episodes without navigating out of KaiserWatch.
  private void showEpisodes() {
    if(!"series".equals(type))return;
    final Dialog dialog=new Dialog(this);
    episodeDialog=dialog;
    LinearLayout sheet=new LinearLayout(this);
    sheet.setOrientation(LinearLayout.VERTICAL);
    sheet.setBackgroundColor(Color.rgb(19,19,20));

    LinearLayout heading=new LinearLayout(this);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.setPadding(dp(14),dp(8),dp(14),dp(6));
    sheet.addView(heading,new LinearLayout.LayoutParams(-1,dp(67)));

    Button close=button("‹",false);
    close.setTextSize(27);
    close.setBackgroundColor(Color.TRANSPARENT);
    close.setOnClickListener(v->dialog.dismiss());
    heading.addView(close,new LinearLayout.LayoutParams(dp(42),dp(50)));

    TextView seasonHeading=label("Season "+season,24,Color.WHITE);
    seasonHeading.setTypeface(null,1);
    seasonHeading.setPadding(dp(5),0,0,0);
    heading.addView(seasonHeading,new LinearLayout.LayoutParams(0,-2,1));

    List<Integer> seasons=new ArrayList<>();
    for(Episode e:episodes)if(!seasons.contains(e.season))seasons.add(e.season);
    if(seasons.isEmpty())seasons.add(season);
    Collections.sort(seasons);
    Spinner seasonSelect=new Spinner(this);
    ArrayList<String> labels=new ArrayList<>();
    for(Integer s:seasons)labels.add("Season "+s);
    ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels) {
      @Override public View getView(int p,View old,ViewGroup parent) {
        TextView t=(TextView)super.getView(p,old,parent);
        t.setTextColor(GOLD);t.setTextSize(13);
        return t;
      }
      @Override public View getDropDownView(int p,View old,ViewGroup parent) {
        TextView t=(TextView)super.getDropDownView(p,old,parent);
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.rgb(48,46,48));
        t.setPadding(dp(12),dp(14),dp(12),dp(14));
        return t;
      }
    };
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    seasonSelect.setAdapter(adapter);
    heading.addView(seasonSelect,new LinearLayout.LayoutParams(dp(125),dp(52)));

    TextView sub=label("Select an episode to start playing",12,MUTED);
    sub.setPadding(dp(20),0,0,dp(13));
    sheet.addView(sub);

    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(false);
    LinearLayout rows=new LinearLayout(this);
    rows.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(rows);
    sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

    seasonSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
      @Override public void onItemSelected(AdapterView<?> p,View v,int index,long id){
        int chosenSeason=seasons.get(index);
        seasonHeading.setText("Season "+chosenSeason);
        rows.removeAllViews();
        boolean added=false;
        for(Episode ep:episodes) {
          if(ep.season!=chosenSeason)continue;
          added=true;
          addEpisodeRow(rows,ep);
        }
        if(!added){
          TextView noEpisodes=label("No episodes listed for this season.",15,MUTED);
          noEpisodes.setPadding(dp(20),dp(26),dp(20),dp(26));
          rows.addView(noEpisodes);
        }
      }
      @Override public void onNothingSelected(AdapterView<?> p){}
    });
    dialog.setContentView(sheet);
    dialog.show();
    Window window=dialog.getWindow();
    if(window!=null){
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams params=window.getAttributes();
      params.gravity=Gravity.BOTTOM;
      params.dimAmount=.76f;
      window.setAttributes(params);
      window.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.91f));
    }
    seasonSelect.setSelection(Math.max(0,seasons.indexOf(season)));
  }

  private void addEpisodeRow(LinearLayout parent,Episode ep){
    boolean current=ep.season==season&&ep.number==number;
    boolean upcoming=!released(ep);
    LinearLayout whole=new LinearLayout(this);
    whole.setOrientation(LinearLayout.VERTICAL);
    whole.setPadding(dp(15),dp(15),dp(15),dp(15));
    whole.setBackgroundColor(current?Color.rgb(32,32,34):
      (ep.number%2==1?Color.rgb(39,39,41):Color.rgb(22,22,23)));
    whole.setClickable(!upcoming);
    whole.setEnabled(!upcoming);
    whole.setAlpha(upcoming?.48f:1f);

    LinearLayout top=new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setOrientation(LinearLayout.HORIZONTAL);
    whole.addView(top,new LinearLayout.LayoutParams(-1,-2));

    TextView num=label(String.valueOf(ep.number),21,Color.WHITE);
    num.setTypeface(null,1);
    num.setGravity(Gravity.CENTER);
    top.addView(num,new LinearLayout.LayoutParams(dp(34),-2));

    LinearLayout middle=new LinearLayout(this);
    middle.setOrientation(LinearLayout.VERTICAL);
    middle.setPadding(dp(8),0,dp(10),0);
    top.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
    TextView name=label(ep.title,16,Color.WHITE);
    name.setTypeface(null,1);
    name.setMaxLines(2);
    name.setEllipsize(TextUtils.TruncateAt.END);
    middle.addView(name);
    String stat=current?"● Now Playing":newEpisode(ep)?"★ NEW EPISODE":
      upcoming?"Coming soon":ep.watched?"✓ Watched":
      ep.released!=null&&ep.released.length()>=10?ep.released.substring(0,10):"";
    if(!stat.isEmpty()){
      TextView info=label(stat,11,current?GOLD:newEpisode(ep)?GOLD:MUTED);
      LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
      ip.topMargin=dp(5);
      middle.addView(info,ip);
    }
    TextView right=label(current?"◆":upcoming?"":"▶",17,current?GOLD:Color.WHITE);
    right.setGravity(Gravity.CENTER);
    top.addView(right,new LinearLayout.LayoutParams(dp(32),-2));

    LinearLayout detail=new LinearLayout(this);
    detail.setOrientation(LinearLayout.HORIZONTAL);
    detail.setGravity(Gravity.TOP);
    LinearLayout.LayoutParams details=new LinearLayout.LayoutParams(-1,-2);
    details.topMargin=dp(11);
    whole.addView(detail,details);

    FrameLayout imageBox=new FrameLayout(this);
    imageBox.setBackground(shape(Color.rgb(51,47,51),0));
    ImageView image=new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    imageBox.addView(image,new FrameLayout.LayoutParams(-1,-1));
    TextView placeholder=label("S"+ep.season+" · E"+ep.number,13,GOLD);
    placeholder.setGravity(Gravity.CENTER);
    imageBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
    if(ep.thumbnail!=null&&ep.thumbnail.startsWith("https://")){
      loadThumbnail(image,ep.thumbnail);
      // Keep placeholder as a low-contrast fallback until the thumbnail loads.
      placeholder.setAlpha(.25f);
    }
    if(current){
      TextView now=label("▶ Now Playing",12,Color.WHITE);
      now.setGravity(Gravity.CENTER);
      now.setBackgroundColor(0xA8000000);
      imageBox.addView(now,new FrameLayout.LayoutParams(-1,-1));
    }
    detail.addView(imageBox,new LinearLayout.LayoutParams(dp(128),dp(73)));

    LinearLayout summaryBox=new LinearLayout(this);
    summaryBox.setPadding(dp(14),0,0,0);
    summaryBox.setOrientation(LinearLayout.VERTICAL);
    detail.addView(summaryBox,new LinearLayout.LayoutParams(0,-2,1));
    TextView description=label(ep.synopsis!=null&&!ep.synopsis.trim().isEmpty()
      ?ep.synopsis:"Select to watch this episode.",13,Color.rgb(221,219,220));
    description.setMaxLines(4);
    description.setEllipsize(TextUtils.TruncateAt.END);
    description.setLineSpacing(dp(3),1f);
    summaryBox.addView(description);

    if(ep.watched){
      View bar=new View(this);
      bar.setBackgroundColor(GOLD);
      LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(2));
      bp.topMargin=dp(10);
      summaryBox.addView(bar,bp);
    }

    whole.setOnClickListener(v->switchEpisode(ep));
    parent.addView(whole,new LinearLayout.LayoutParams(-1,-2));
    View separator=new View(this);
    separator.setBackgroundColor(Color.rgb(95,95,97));
    parent.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
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
          v.setKeepScreenOn(true);
          getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    super.onPause();
  }
  @Override protected void onResume(){
    super.onResume();
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if(web!=null)web.onResume();
  }
  @Override protected void onDestroy(){
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    imagePool.shutdownNow();
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
