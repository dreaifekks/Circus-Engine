package org.circus.runtime;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.io.IOException;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.circus.core.*;

/** Actual Android admission plus original-script Canvas/menu/save checks. */
public final class Dc3FamilyInstrumentation extends Instrumentation {
    private String pack,exe,profile;private RuntimeActivity activity;
    public void onCreate(Bundle args){super.onCreate(args);pack=args.getString("pack");exe=args.getString("exe");profile=args.getString("profile");start();}
    public void onStart(){Bundle result=new Bundle();try{
        if(!"dc3wy-a09f".equals(profile)&&!"dc3pp-9872".equals(profile))throw new IOException("Expected W.Y. or P.P. audit profile");
        Intent launch=new Intent(getTargetContext(),RuntimeActivity.class).putExtra("audit",true).putExtra("range_pack",pack).putExtra("executable",exe).putExtra("encoding","auto").putExtra("configuration","@none").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(RuntimeActivity)startActivitySync(launch);long end=System.currentTimeMillis()+60000;
        while(activity.engine==null&&!activity.failed&&System.currentTimeMillis()<end)Thread.sleep(50);
        if(activity.failed)throw new IOException(activity.error);
        if(!(activity.engine instanceof DesktopDd)||!profile.equals(activity.engine.resources.profileId))throw new IOException("Android selected wrong backend");
        runOnMainSync(()->callActivityOnPause(activity));
        String report=activity.worker.submit(()->new Dc3FamilyAudit((DesktopDd)activity.engine,new File(getTargetContext().getFilesDir(),"wy-pp-audit/"+profile).toPath()).run()).get(120,TimeUnit.SECONDS);
        result.putString("stream","PASS shipping RuntimeActivity selects "+profile+" with selected EXE and auto encoding\n"+report);finish(-1,result);
    }catch(Throwable t){result.putString("stream","FAIL "+t+"\n");finish(0,result);}finally{if(activity!=null)runOnMainSync(()->activity.finish());}}
}
