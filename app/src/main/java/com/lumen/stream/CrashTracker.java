package com.lumen.stream;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class CrashTracker {
  private static final String TAG="LumenDiag";
  private static final String FILENAME="lumen-player-diagnostics.txt";
  private CrashTracker(){}

  public static void record(Context c,String source,String detail) {
    String safe=detail==null?"":detail.replaceAll("[\\r\\n]+"," ").trim();
    if(safe.length()>420)safe=safe.substring(0,420);
    String msg=System.currentTimeMillis()+" ["+source+"] "+safe+"\n";
    Log.i(TAG,source+": "+safe);
    try {
      File file=new File(c.getFilesDir(),FILENAME);
      if(file.length()>40000)file.delete();
      try(FileOutputStream out=new FileOutputStream(file,true)){
        out.write(msg.getBytes(StandardCharsets.UTF_8));
      }
    }catch(Exception e){Log.w(TAG,"Could not write diagnostics",e);}
  }

  public static void install(Context context,String component){
    Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((t,err)->{
      try {record(context,component+" JAVA CRASH",Log.getStackTraceString(err));}
      catch(Throwable ignored){}
      if(original!=null)original.uncaughtException(t,err);
      else android.os.Process.killProcess(android.os.Process.myPid());
    });
  }

  public static String report(Context c){
    StringBuilder b=new StringBuilder();
    b.append("LUMEN player diagnostics\n");
    b.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
    b.append("\nDevice: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);
    if(Build.VERSION.SDK_INT>=30){
      try {
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ApplicationExitInfo> list=manager.getHistoricalProcessExitReasons(c.getPackageName(),0,15);
        b.append("\n\nRecent process exits (newest first):");
        if(list.isEmpty())b.append("\nNo recent Android process exits.");
        for(ApplicationExitInfo info:list){
          b.append("\n").append(new java.util.Date(info.getTimestamp()));
          b.append(" | ").append(info.getProcessName());
          b.append(" | reason=").append(info.getReason());
          b.append(" | status=").append(info.getStatus());
          b.append(" | description=").append(info.getDescription());
        }
      }catch(Exception ex){b.append("\nExit history unavailable: ").append(ex.getClass().getSimpleName());}
    }
    try{
      File f=new File(c.getFilesDir(),FILENAME);
      b.append("\n\nLUMEN player events:\n");
      if(f.exists())b.append(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
      else b.append("No recorded events.");
    }catch(Exception ex){b.append("Could not read diagnostics: ").append(ex);}
    String report=b.toString();
    return report.length()>12000?report.substring(report.length()-12000):report;
  }
}
