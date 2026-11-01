package org.circus.runtime;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;
import org.circus.graphics.BufferedImage;

/** Android platform boundary. Script execution, menus, rendering commands,
 * captures and save semantics are the same sources as the verified PC engine. */
public final class RuntimeActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler();
    volatile DesktopEngine engine;volatile boolean failed,closed,active;volatile String error="";volatile Stage stage;int audioStarted;
    volatile long initializedNanos,firstPresentationNanos;
    private GameResources resources;private long lastTick;private boolean busy;private Object seenVm;
    private final MediaPlayer[] players=new MediaPlayer[6];private final boolean[] prepared=new boolean[6];
    private final int[] heard={-1,-1,-1,-1,-1,-1},seekGeneration={-1,-1,-1,-1,-1,-1};
    private final long[] requestedResume=new long[6];private final File[] audioFiles=new File[6];
    private int movieGeneration=-1;private volatile int movieToken;private volatile NativeMovie movie;private android.widget.VideoView platformMovie;private Dialog movieDialog;private volatile File movieFile;
    private String tree;private boolean resume;private boolean audit,rangeReplay;
    NativeMovie activeMovie(){return movie;}
    // Sample animation time at vsync. Keep one worker frame in flight so a slow
    // draw never builds an input-delaying queue; a busy frame waits for the next vsync.
    private final Choreographer.FrameCallback frame=new Choreographer.FrameCallback(){public void doFrame(long frameTimeNanos){if(!active||closed||failed)return;if(!busy&&engine!=null){busy=true;worker.execute(()->{try{
        updatePositions();long now=frameTimeNanos/1000000;engine.tick(Math.min(100,Math.max(0,now-lastTick)));lastTick=now;syncMedia();
        if(engine.exitRequested()){engine.persist();main.post(()->finish());return;}
        if(movieFile==null){BufferedImage image=engine.render();main.post(()->{if(closed){image.flush();return;}stage.present(image);});}
    }catch(Throwable e){fail(e);}finally{main.post(()->busy=false);}});}Choreographer.getInstance().postFrameCallback(this);}};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(128|1024);getWindow().getDecorView().setSystemUiVisibility(5894);stage=new Stage();setContentView(stage);
        tree=getIntent().getStringExtra("tree");resume=saved!=null&&saved.getBoolean("resume");audit=getIntent().getBooleanExtra("audit",false)&&(getApplicationInfo().flags&2)!=0;
        String rangePack=getIntent().getStringExtra("range_pack");rangeReplay=audit&&rangePack!=null;
        if(tree==null&&!rangeReplay){fail(new IOException("请选择包含 AdvData 或 DC3_Data 的游戏目录"));return;}
        worker.execute(()->{try{
            System.setProperty("dc.saveRoot",new File(getFilesDir(),audit?"android-011-audit-saves":"shared-saves").getAbsolutePath());
            GameResources.Storage storage;if(rangeReplay){File pack=new File(getFilesDir(),rangePack).getCanonicalFile();if(!pack.toPath().startsWith(getFilesDir().getCanonicalFile().toPath()))throw new IOException("Invalid audit pack path");storage=RangeStorage.replay(pack.toPath());}else storage=new DocumentTree(getContentResolver(),Uri.parse(tree));
            resources=new GameResources(storage,getIntent().getStringExtra("executable"));
            if(UnityCircusResources.candidate(resources))engine=new DesktopUnity(new UnityCircusResources(resources),audit);
            else {
            Profile profile=storage instanceof DocumentTree?GameVersionCache.detect((DocumentTree)storage,resources,getCacheDir()):GameVersion.detect(resources).profile();
            if(!Dc3Host.supports(profile)&&!profile.id.equals("dc2dm-9d72")&&!PcHost.supports(profile))throw new IOException("此版本尚未验证："+profile.id);
            CircusResources data=new CircusResources(resources,profile);TextCodec codec=GameText.select(resources,profile,getIntent().getStringExtra("encoding"),getIntent().getStringExtra("configuration"));ExecutableData.validate(data,codec);
            engine=Dc3Host.supports(profile)?new DesktopDd(data,codec,audit):new DesktopLegacy(data,codec,audit);
            }
            if(!audit&&!(engine instanceof DesktopUnity))migratePreviousAndroid();Path automatic=engine.saveDirectory.resolve("mobile-resume.sav");if(resume&&Files.isRegularFile(automatic))engine.restore(automatic);
            String libraryId=getIntent().getStringExtra("library_id");if(!audit&&libraryId!=null)new GameLibrary(this).opened(libraryId);
            initializedNanos=System.nanoTime();lastTick=SystemClock.uptimeMillis();main.post(()->{stage.setContentDescription("原版游戏画面，点击推进，双指点按或返回键打开菜单");wake();});
        }catch(Throwable e){fail(e);}});
    }
    private void migratePreviousAndroid()throws IOException {
        String key=engine.resources.profileId,oldKey=key.equals("dc3dd-a5a8")?"dc3dd":key.equals("dc2dm-9d72")?"dc2dm":key;
        GameSaves old=new GameSaves(this,tree,oldKey);Path marker=engine.saveDirectory.resolve("android-cas-migrated");if(Files.exists(marker))return;
        File prefs=new File(old.directory,"progress.dm");if(engine instanceof DesktopLegacy&&(prefs.isFile()||new File(prefs+".bak").isFile())&&!Files.exists(engine.saveDirectory.resolve("progress.dat"))){try(DataInputStream in=new DataInputStream(new android.util.AtomicFile(prefs).openRead())){((DesktopLegacy)engine).host().loadPersistent(in);}engine.persist();}
        if(engine instanceof DesktopDd&&!Files.exists(engine.saveDirectory.resolve("progress.dat"))){SharedPreferences prior=getSharedPreferences(old.directory.getName(),0);Dc3Host host=((DesktopDd)engine).host;for(int flag=100;flag<=110;flag++)host.state.write(100,flag,Math.max(host.state.read(100,flag),prior.getInt("clear_"+flag,0)));engine.persist();}
        for(int slot=0;slot<256;slot++){Path target=engine.saveDirectory.resolve("slot-"+slot+".sav");if(!old.exists(slot)||Files.exists(target))continue;GameSaves.Save prior=old.read(slot);DesktopSave migrated=new DesktopSave();migrated.state=prior.core;migrated.created=prior.when;migrated.caption=prior.script;migrated.textElapsed=prior.revealTime;DesktopEngine.atomic(target,migrated.encode());}
        // Refresh availability without replacing persistent settings or launching a script.
        engine.restore(engine.snapshot());DesktopEngine.atomic(marker,new byte[]{1});
    }
    void submit(Runnable action){if(!closed&&!worker.isShutdown())worker.execute(()->{try{action.run();}catch(Throwable e){fail(e);}});}
    interface Work {void run()throws Exception;}
    void input(Work action){submit(()->{try{if(engine!=null&&!failed){action.run();lastTick=SystemClock.uptimeMillis();}}catch(Exception e){fail(e);}});}
    private void wake(){Choreographer.getInstance().removeFrameCallback(frame);if(active&&!closed&&!failed){lastTick=SystemClock.uptimeMillis();Choreographer.getInstance().postFrameCallback(frame);}}
    private void fail(Throwable e){if(closed||failed)return;failed=true;error=e.toString();android.util.Log.e("CIRCUS-011",engine==null?"startup":engine.scriptName()+" @"+Integer.toHexString(engine.scriptOffset()),e);
        main.post(()->{Choreographer.getInstance().removeFrameCallback(frame);if(!closed)new AlertDialog.Builder(this).setTitle("CIRCUS 兼容诊断").setMessage((engine==null?"":engine.scriptName()+" @0x"+Integer.toHexString(engine.scriptOffset())+"\n")+error).setPositiveButton("返回",(d,w)->finish()).setCancelable(false).show();});
    }
    private void updatePositions()throws IOException {if(engine==null)return;for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])engine.audioPosition(ch,players[ch].getCurrentPosition()*1000L,players[ch].getDuration()*1000L);}
    private void syncMedia()throws IOException {
        if(seenVm!=engine.executionIdentity()){for(int ch=0;ch<6;ch++)stopAudio(ch);Arrays.fill(heard,-1);Arrays.fill(seekGeneration,-1);seenVm=engine.executionIdentity();movieGeneration=-1;}
        for(int ch=0;ch<6;ch++){long position=engine.takeAudioResume(ch);if(position>0)requestedResume[ch]=position;
            if(heard[ch]!=engine.audioGeneration(ch)){heard[ch]=engine.audioGeneration(ch);startAudio(ch,engine.audioActive(ch)?engine.audio(ch):"",heard[ch],engine.audioLoop(ch));}
            if(players[ch]!=null&&prepared[ch]){MediaPlayer p=players[ch];float gain=engine.volume(ch),pan=Math.max(-1,Math.min(1,engine.pan(ch)/100f));p.setVolume(gain*(1-Math.max(0,pan)),gain*(1+Math.min(0,pan)));
                if(requestedResume[ch]>0){p.seekTo(Math.min((int)(requestedResume[ch]/1000),Math.max(0,p.getDuration()-1)));requestedResume[ch]=0;}
                int seek=engine.audioSeekGeneration(ch);if(seekGeneration[ch]!=seek){if(seekGeneration[ch]>=0)p.seekTo((int)(p.getDuration()*(long)engine.audioSeekPercent(ch)/100));seekGeneration[ch]=seek;}
            }
        }
        if(!engine.movie().isEmpty()&&movieGeneration!=engine.movieGeneration()){movieGeneration=engine.movieGeneration();if(rangeReplay)engine.mediaCompleted(engine.ticket());else startMovie(engine.movie(),engine.ticket());}
    }
    private void stopAudio(int ch){prepared[ch]=false;if(players[ch]!=null){players[ch].release();players[ch]=null;}if(audioFiles[ch]!=null){audioFiles[ch].delete();audioFiles[ch]=null;}}
    private void startAudio(int ch,String resource,int serial,boolean loop)throws IOException {
        stopAudio(ch);if(resource.isEmpty())return;File file=File.createTempFile("circus-"+ch+"-",".audio",getCacheDir());audioFiles[ch]=file;
        // MediaPlayer detects WAV/Ogg from the header; long PCM tracks are copied
        // in bounded blocks instead of retaining several complete WAV arrays.
        try(FileOutputStream out=new FileOutputStream(file)){engine.resources.writeAudio(resource,out);}
        catch(IOException|RuntimeException e){file.delete();audioFiles[ch]=null;throw e;}
        MediaPlayer player=new MediaPlayer();players[ch]=player;player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(file.getAbsolutePath());player.setLooping(loop);
        player.setOnPreparedListener(p->submit(()->{if(players[ch]!=p)return;file.delete();audioFiles[ch]=null;prepared[ch]=true;try{syncMedia();if(active&&players[ch]==p){p.start();audioStarted++;}}catch(Exception e){fail(e);}}));
        player.setOnCompletionListener(p->submit(()->{if(players[ch]==p){engine.audioCompleted(ch,serial);stopAudio(ch);}}));
        player.setOnErrorListener((p,what,extra)->{submit(()->{if(players[ch]==p)fail(new IOException("音频播放失败："+resource+" ("+what+","+extra+")"));});return true;});player.prepareAsync();
    }
    private void startMovie(String reference,long ticket)throws IOException {
        final int token=++movieToken;boolean platform=reference.toLowerCase(Locale.ROOT).endsWith(".mp4");File file=File.createTempFile("circus-movie-",platform?".mp4":".mpg",getCacheDir());movieFile=file;try(FileOutputStream out=new FileOutputStream(file)){engine.resources.files.copy(reference,out,1024L*1024*1024);}
        if(platform){main.post(()->startPlatformMovie(file,token,ticket));return;}
        main.post(()->{if(closed||token!=movieToken){file.delete();return;}NativeMovie video=new NativeMovie(this);movie=video;movieDialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);movieDialog.setContentView(video);movieDialog.setCancelable(false);movieDialog.show();movieDialog.getWindow().getDecorView().setSystemUiVisibility(5894);
            video.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)finishMovie(token,ticket);return true;});movieDialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP)finishMovie(token,ticket);return true;});
            if(!active)video.pauseMovie();video.play(file,new NativeMovie.Listener(){public void finished(){finishMovie(token,ticket);}public void failed(Exception e){fail(e);}});
        });
    }
    /** Unity editions ship H.264/AAC MP4; Android owns both decoders and their clock. */
    private void startPlatformMovie(File file,int token,long ticket){
        if(closed||token!=movieToken){file.delete();return;}
        android.widget.VideoView video=new android.widget.VideoView(this);platformMovie=video;
        android.widget.FrameLayout box=new android.widget.FrameLayout(this);box.setBackgroundColor(Color.BLACK);
        box.addView(video,new android.widget.FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        movieDialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);movieDialog.setContentView(box);movieDialog.setCancelable(false);movieDialog.show();movieDialog.getWindow().getDecorView().setSystemUiVisibility(5894);
        video.setOnPreparedListener(p->{if(token==movieToken&&active)video.start();});
        video.setOnCompletionListener(p->finishMovie(token,ticket));
        video.setOnErrorListener((p,what,extra)->{if(token==movieToken)fail(new IOException("MP4 视频播放失败 ("+what+","+extra+")"));return true;});
        video.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP)finishMovie(token,ticket);return true;});
        movieDialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP)finishMovie(token,ticket);return true;});video.setVideoPath(file.getAbsolutePath());
    }
    private void finishMovie(int token,long ticket){if(token!=movieToken)return;closeMovie();input(()->engine.mediaCompleted(ticket));}
    private void closeMovie(){movieToken++;if(movie!=null){movie.close();movie=null;}if(platformMovie!=null){platformMovie.stopPlayback();platformMovie=null;}if(movieDialog!=null){movieDialog.dismiss();movieDialog=null;}if(movieFile!=null){movieFile.delete();movieFile=null;}}
    @Override public void onBackPressed(){if(engine==null||failed){super.onBackPressed();return;}input(()->engine.click(0,0,true));}
    @Override protected void onResume(){super.onResume();active=true;getWindow().getDecorView().setSystemUiVisibility(5894);submit(()->{for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])players[ch].start();});if(movie!=null)movie.resumeMovie();if(platformMovie!=null)platformMovie.start();wake();}
    @Override protected void onPause(){active=false;if(stage!=null)stage.cancelGesture();Choreographer.getInstance().removeFrameCallback(frame);if(movie!=null)movie.pauseMovie();if(platformMovie!=null)platformMovie.pause();submit(()->{try{if(engine!=null&&!failed){engine.cancelPointer();updatePositions();if(!audit){engine.persist();engine.save(engine.saveDirectory.resolve("mobile-resume.sav"));}}}catch(Exception e){fail(e);}finally{for(int ch=0;ch<6;ch++)if(players[ch]!=null&&prepared[ch])players[ch].pause();}});super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("resume",true);}
    @Override protected void onDestroy(){closed=true;Choreographer.getInstance().removeFrameCallback(frame);closeMovie();worker.execute(()->{for(int ch=0;ch<6;ch++)stopAudio(ch);});worker.shutdown();stage.release();super.onDestroy();}
    @Override public boolean onKeyUp(int code,KeyEvent event){if(code==KeyEvent.KEYCODE_BACK)return super.onKeyUp(code,event);input(()->{if(engine.editingText()){int mapped=code==KeyEvent.KEYCODE_DEL?8:code==KeyEvent.KEYCODE_FORWARD_DEL?127:code==KeyEvent.KEYCODE_DPAD_LEFT?37:code==KeyEvent.KEYCODE_DPAD_RIGHT?39:code==KeyEvent.KEYCODE_ENTER?10:0;if(mapped!=0)engine.editKey(mapped);else if(event.getUnicodeChar()!=0)engine.typeText(new String(Character.toChars(event.getUnicodeChar())));}else if(code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_SPACE)engine.click(engine.width/2,engine.height/2,false);});return true;}
    final class Stage extends View {
        java.util.function.LongConsumer presentationObserver;
        private BufferedImage image;private float scale=1,ox,oy;private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);private boolean held,multiTouch,tapCandidate,scrollAllowed,firstMoved;
        private int firstPointer=-1,secondPointer=-1;private float firstX,firstY,secondX,secondY,scrollY;private long touchStarted;
        private static final long TWO_FINGER_TAP_MS=350;
        private final int touchSlop=ViewConfiguration.get(RuntimeActivity.this).getScaledTouchSlop();
        Stage(){super(RuntimeActivity.this);setBackgroundColor(Color.BLACK);setFocusableInTouchMode(true);setContentDescription("正在读取游戏资源");}
        void present(BufferedImage next){BufferedImage old=image;image=next;if(old!=null)old.flush();if(firstPresentationNanos==0)firstPresentationNanos=System.nanoTime();invalidate();if(presentationObserver!=null)presentationObserver.accept(System.nanoTime());}
        void release(){if(image!=null){image.flush();image=null;}}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(Color.BLACK);if(image==null){Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(0xffdddddd);label.setTextSize(18*getResources().getDisplayMetrics().scaledDensity);label.setTextAlign(Paint.Align.CENTER);canvas.drawText("正在读取游戏资源…",getWidth()/2f,getHeight()/2f,label);return;}scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());ox=(getWidth()-image.getWidth()*scale)/2;oy=(getHeight()-image.getHeight()*scale)/2;canvas.drawBitmap(image.bitmap,null,new RectF(ox,oy,ox+image.getWidth()*scale,oy+image.getHeight()*scale),paint);}
        void cancelGesture(){held=multiTouch=tapCandidate=scrollAllowed=firstMoved=false;firstPointer=secondPointer=-1;}
        private boolean moved(MotionEvent e,int id,float startX,float startY){
            int i=e.findPointerIndex(id);if(i<0)return false;float limit=touchSlop*(float)touchSlop;
            for(int h=0;h<=e.getHistorySize();h++){float dx=(h==e.getHistorySize()?e.getX(i):e.getHistoricalX(i,h))-startX,dy=(h==e.getHistorySize()?e.getY(i):e.getHistoricalY(i,h))-startY;if(dx*dx+dy*dy>limit)return true;}return false;
        }
        private void trackTap(MotionEvent e){firstMoved|=moved(e,firstPointer,firstX,firstY);if(firstMoved||secondPointer>=0&&moved(e,secondPointer,secondX,secondY))tapCandidate=false;}
        @Override public boolean onTouchEvent(MotionEvent event){if(engine==null||failed||image==null)return true;int x=Math.round((event.getX()-ox)/scale),y=Math.round((event.getY()-oy)/scale);int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){cancelGesture();held=true;firstPointer=event.getPointerId(0);firstX=event.getX();firstY=event.getY();touchStarted=event.getEventTime();input(()->engine.pointerDown(x,y));}
            else if(action==MotionEvent.ACTION_POINTER_DOWN){
                trackTap(event);boolean pair=!multiTouch&&held&&event.getPointerCount()==2;
                tapCandidate=pair&&!firstMoved&&event.getEventTime()-touchStarted<=TWO_FINGER_TAP_MS;
                if(pair){int i=event.getActionIndex();secondPointer=event.getPointerId(i);secondX=event.getX(i);secondY=event.getY(i);scrollY=(event.getY(0)+event.getY(1))/2;}
                scrollAllowed=pair;multiTouch=true;held=false;input(()->engine.cancelPointer());
            }
            else if(action==MotionEvent.ACTION_MOVE){trackTap(event);if(multiTouch){if(scrollAllowed&&event.getPointerCount()==2){float center=(event.getY(0)+event.getY(1))/2,delta=(center-scrollY)/scale;if(Math.abs(delta)>35){tapCandidate=false;int units=delta>0?-1:1;scrollY=center;input(()->engine.scroll(units));}}}else if(held)input(()->engine.pointerMove(x,y));}
            else if(action==MotionEvent.ACTION_POINTER_UP){trackTap(event);scrollAllowed=false;}
            else if(action==MotionEvent.ACTION_UP){
                trackTap(event);boolean right=multiTouch&&tapCandidate&&event.getEventTime()-touchStarted<=TWO_FINGER_TAP_MS,left=held&&!multiTouch;cancelGesture();
                if(right)input(()->{engine.cancelPointer();engine.click(0,0,true);});else if(left)input(()->engine.pointerUp(x,y));performClick();
            }
            else if(action==MotionEvent.ACTION_CANCEL){cancelGesture();input(()->engine.cancelPointer());}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL){int units=-(int)Math.signum(event.getAxisValue(MotionEvent.AXIS_VSCROLL));input(()->engine.scroll(units));return true;}if(event.getAction()==MotionEvent.ACTION_HOVER_MOVE){int x=Math.round((event.getX()-ox)/scale),y=Math.round((event.getY()-oy)/scale);input(()->engine.hover(x,y));return true;}return super.onGenericMotionEvent(event);}
        @Override public boolean onCheckIsTextEditor(){return engine!=null&&engine.editingText();}
        @Override public InputConnection onCreateInputConnection(EditorInfo info){info.inputType=android.text.InputType.TYPE_CLASS_TEXT;info.imeOptions=EditorInfo.IME_ACTION_DONE;return new BaseInputConnection(this,false){public boolean commitText(CharSequence text,int cursor){input(()->engine.typeText(text.toString()));return true;}public boolean deleteSurroundingText(int before,int after){input(()->engine.editKey(8));return true;}public boolean performEditorAction(int action){input(()->engine.editKey(10));return true;}};}
    }
}
