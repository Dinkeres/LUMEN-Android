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
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class NativeMediaActivity extends Activity {
  private ExoPlayer media;
  private TextView status;
  @Override public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    String link=getIntent().getStringExtra("url");
    Uri uri=link==null?Uri.EMPTY:Uri.parse(link);
    if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null){finish();return;}
    getWindow().setStatusBarColor(Color.rgb(9,10,17));
    getWindow().setNavigationBarColor(Color.rgb(9,10,17));

    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setBackgroundColor(Color.rgb(9,10,17));
    Button back=new Button(this);
    back.setText("← Back to LUMEN");
    back.setTextColor(Color.WHITE);
    back.setAllCaps(false);
    back.setOnClickListener(v->finish());
    layout.addView(back,new LinearLayout.LayoutParams(-1,-2));

    status=new TextView(this);
    status.setTextColor(Color.rgb(185,188,201));
    status.setTextSize(13);
    status.setText("Native Android player • MP4 / HLS");
    status.setPadding(18,12,18,14);
    layout.addView(status,new LinearLayout.LayoutParams(-1,-2));

    PlayerView view=new PlayerView(this);
    view.setUseController(true);
    layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(layout);
    try {
      media=new ExoPlayer.Builder(this).build();
      view.setPlayer(media);
      media.addListener(new Player.Listener(){
        @Override public void onPlayerError(PlaybackException error){
          status.setText("This source cannot be played: "+error.getErrorCodeName()+
            ". Try a compatible HTTPS MP4/HLS source or check your connection.");
        }
        @Override public void onPlaybackStateChanged(int state){
          if(state==Player.STATE_READY)status.setText("Playing inside LUMEN (Android Media3)");
          else if(state==Player.STATE_BUFFERING)status.setText("Loading video…");
        }
      });
      media.setMediaItem(MediaItem.fromUri(uri));
      media.prepare();
      media.play();
    }catch(Exception e){
      status.setText("Could not start video playback: "+e.getClass().getSimpleName());
    }
  }
  @Override protected void onPause(){if(media!=null)media.pause();super.onPause();}
  @Override protected void onDestroy(){if(media!=null){media.release();media=null;}super.onDestroy();}
}
