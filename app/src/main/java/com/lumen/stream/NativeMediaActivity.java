package com.lumen.stream;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.ui.PlayerView;

public class NativeMediaActivity extends Activity {
  private ExoPlayer media;
  private PlayerView view;
  private TextView status;
  private Uri uri;
  private boolean software=true;

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    CrashTracker.install(this,"NativeMedia");
    String link=getIntent().getStringExtra("url");
    uri=link==null?Uri.EMPTY:Uri.parse(link);
    if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null){
      CrashTracker.record(this,"NativeMedia","Rejected invalid video URL");
      finish();return;
    }
    CrashTracker.record(this,"NativeMedia","Opened host="+uri.getHost());
    getWindow().setStatusBarColor(Color.rgb(9,10,17));
    getWindow().setNavigationBarColor(Color.rgb(9,10,17));

    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setBackgroundColor(Color.rgb(9,10,17));
    Button back=new Button(this);
    back.setText("← Back to LUMEN");
    back.setTextColor(Color.WHITE);back.setAllCaps(false);
    back.setOnClickListener(v->finish());
    layout.addView(back,new LinearLayout.LayoutParams(-1,-2));

    status=new TextView(this);
    status.setTextColor(Color.rgb(205,207,218));
    status.setTextSize(14);
    status.setPadding(20,14,20,14);
    layout.addView(status,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout modes=new LinearLayout(this);
    modes.setOrientation(LinearLayout.HORIZONTAL);
    Button soft=new Button(this);
    soft.setText("Software decoder");
    soft.setAllCaps(false);
    soft.setOnClickListener(v->startPlayer(true));
    modes.addView(soft,new LinearLayout.LayoutParams(0,-2,1));
    Button system=new Button(this);
    system.setText("System decoder");
    system.setAllCaps(false);
    system.setOnClickListener(v->startPlayer(false));
    modes.addView(system,new LinearLayout.LayoutParams(0,-2,1));
    layout.addView(modes,new LinearLayout.LayoutParams(-1,-2));

    view=new PlayerView(this);
    view.setUseController(true);
    layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(layout);
    startPlayer(true);
  }

  private void startPlayer(boolean preferSoftware){
    if(media!=null){view.setPlayer(null);media.release();media=null;}
    software=preferSoftware;
    status.setText((software?"Software-preferred":"System")+" decoder: initializing. Tap ▶ in the player to start.");
    CrashTracker.record(this,"NativeMedia","Initializing "+(software?"software-preferred":"system")+" decoder");
    try{
      DefaultRenderersFactory renderers=new DefaultRenderersFactory(this)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(software?MediaCodecSelector.PREFER_SOFTWARE:MediaCodecSelector.DEFAULT);
      media=new ExoPlayer.Builder(this,renderers).build();
      view.setPlayer(media);
      media.addListener(new Player.Listener(){
        @Override public void onPlayerError(PlaybackException err){
          String message=err.getErrorCodeName()+": "+err.getMessage();
          CrashTracker.record(NativeMediaActivity.this,"NativeMedia playback error",message);
          status.setText("Playback error: "+message+"\nTry the other decoder.");
        }
        @Override public void onPlaybackStateChanged(int state){
          if(state==Player.STATE_READY){
            status.setText((software?"Software-preferred":"System")+" decoder ready. Tap ▶ to play.");
            CrashTracker.record(NativeMediaActivity.this,"NativeMedia","STATE_READY");
          }
          if(state==Player.STATE_BUFFERING)status.setText("Buffering video…");
          if(state==Player.STATE_ENDED)status.setText("Playback finished.");
        }
        @Override public void onIsPlayingChanged(boolean playing){
          if(playing){
            status.setText("Playing video inside LUMEN");
            CrashTracker.record(NativeMediaActivity.this,"NativeMedia","isPlaying=true");
          }
        }
      });
      media.setMediaItem(MediaItem.fromUri(uri));
      media.prepare();
      // Deliberately do not auto-play: lets user separate startup from decoder crashes.
    }catch(Exception err){
      CrashTracker.record(this,"NativeMedia init error",err.toString());
      status.setText("Could not initialize player: "+err.getClass().getSimpleName());
    }
  }

  @Override protected void onPause(){if(media!=null)media.pause();super.onPause();}
  @Override protected void onDestroy(){
    if(media!=null){view.setPlayer(null);media.release();media=null;}
    super.onDestroy();
  }
}
