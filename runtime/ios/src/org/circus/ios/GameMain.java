package org.circus.ios;
import ios.awt.BufferedImage;
import ios.awt.ImageIO;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.circus.core.*;
import org.robovm.apple.avfoundation.*;
import org.robovm.apple.coregraphics.*;
import org.robovm.apple.foundation.*;
import org.robovm.apple.uikit.*;
import org.robovm.objc.ObjCClass;
import org.robovm.objc.annotation.CustomClass;
/** iPad window for the Mac runtime: DesktopRuntime.window(), with touch instead of a mouse. */
public final class GameMain {
    static final File DOCS=new File(System.getenv("HOME"),"Documents");
    static PrintStream log;
    static void log(String s){if(log!=null)log.println(s);}
    public static void main(String[] a)throws Exception{
        DOCS.mkdirs();log=new PrintStream(new FileOutputStream(new File(DOCS,"app.log")),true,"UTF-8");log("start "+System.getProperty("os.version"));for(File f:DOCS.listFiles())if(f.getName().startsWith("shot-"))f.delete();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace(log);});
        try(NSAutoreleasePool p=new NSAutoreleasePool()){ObjCClass.getByType(SceneDel.class);UIApplication.main(a,null,AppDel.class);}
    }
    public static final class AppDel extends UIApplicationDelegateAdapter {
        @Override public boolean didFinishLaunching(UIApplication app,UIApplicationLaunchOptions o){return true;}
    }
    @CustomClass("CircusSceneDelegate")
    public static final class SceneDel extends UIResponder implements UIWindowSceneDelegate {
        private UIWindow window;private static Stage stage;
        @Override public UIWindow getWindow(){return window;}
        @Override public void setWindow(UIWindow w){window=w;}
        @Override public void willConnect(UIScene scene,UISceneSession session,UISceneConnectionOptions o){
            UIWindow w=new UIWindow((UIWindowScene)scene);stage=new Stage();UIViewController c=new Root();c.setView(stage);w.setRootViewController(c);w.makeKeyAndVisible();window=w;
            UIApplication.getSharedApplication().setIdleTimerDisabled(true);
            try{AVAudioSession a=AVAudioSession.getSharedInstance();a.setCategory(AVAudioSessionCategory.Playback);a.setActive(true);}catch(Exception e){log("audio session "+e);}
            stage.boot();
        }
        @Override public void sceneDidDisconnect(UIScene s){}@Override public void sceneDidBecomeActive(UIScene s){}@Override public void sceneWillResignActive(UIScene s){}@Override public void sceneWillEnterForeground(UIScene s){}
        @Override public void sceneDidEnterBackground(UIScene s){if(stage!=null)stage.persist();}
        @Override public void openURLContexts(UIScene s,NSSet<UIOpenURLContext> c){}
        @Override public NSUserActivity stateRestorationActivityForScene(UIScene s){return null;}
        @Override public void restoreInteractionState(UIScene s,NSUserActivity a){}
        @Override public void willContinueUserActivity(UIScene s,String t){}
        @Override public void continueUserActivity(UIScene s,NSUserActivity a){}
        @Override public void didFailToContinueUserActivity(UIScene s,String t,NSError e){}
        @Override public void didUpdateUserActivity(UIScene s,NSUserActivity a){}
        @Override public void didUpdateCoordinateSpace(UIWindowScene s,UICoordinateSpace c,UIInterfaceOrientation o,UITraitCollection t){}
        @Override public void didUpdateEffectiveGeometry(UIWindowScene s,UIWindowSceneGeometry g){}
        @Override public void performAction(UIWindowScene s,UIApplicationShortcutItem i,org.robovm.objc.block.VoidBooleanBlock h){}
        @Override public void userDidAcceptCloudKitShare(UIWindowScene s,org.robovm.apple.cloudkit.CKShareMetadata m){}
        @Override public UISceneWindowingControlStyle preferredWindowingControlStyleForScene(UIWindowScene s){return null;}
    }
    public static final class Root extends UIViewController {
        @Override public boolean prefersStatusBarHidden(){return true;}
        @Override public boolean prefersHomeIndicatorAutoHidden(){return true;}
        @Override public UIEditingInteractionConfiguration getEditingInteractionConfiguration(){return UIEditingInteractionConfiguration.None;}
    }
    public static final class Stage extends UIView {
        final UIImageView image=new UIImageView();final UILabel banner=new UILabel();NSTimer timer;
        DesktopLegacy engine;IosMedia media;Object mediaVm;String problem="";int[] fb;CGBitmapContext ctx;
        double scale=1,ox,oy;long last;double panned;
        final ConcurrentLinkedQueue<Runnable> inbox=new ConcurrentLinkedQueue<>();
        long frames,tickN,tickMax,drawN,drawMax,presentN,statAt;String lastEvent="";
        // unattended check
        final String pathEnv=System.getenv("CIRCUS_PATH");final Deque<String> path=new ArrayDeque<>(pathEnv==null?Collections.<String>emptyList():Arrays.asList(pathEnv.split(",")));long stepAt,pathDone;int step,upX=-1,upY,burst;String burstName="";BufferedImage shown;final int limit=Integer.parseInt(System.getenv("CIRCUS_LIMIT")==null?"40":System.getenv("CIRCUS_LIMIT"));
        Stage(){setBackgroundColor(UIColor.black());setMultipleTouchEnabled(true);image.setUserInteractionEnabled(false);image.setContentMode(UIViewContentMode.ScaleToFill);addSubview(image);
            banner.setTextColor(UIColor.white());banner.setNumberOfLines(0);banner.setFont(UIFont.getSystemFont(20));banner.setFrame(new CGRect(40,40,1000,200));addSubview(banner);
            // There is no right button on a tablet: two fingers, or a long press, stand in for it. Two fingers dragging are the wheel.
            UITapGestureRecognizer two=new UITapGestureRecognizer(g->secondary(g.getLocationInView(this)));two.setNumberOfTouchesRequired(2);addGestureRecognizer(two);
            UILongPressGestureRecognizer hold=new UILongPressGestureRecognizer(g->{if(g.getState()==UIGestureRecognizerState.Began)secondary(g.getLocationInView(this));});hold.setMinimumPressDuration(.8);addGestureRecognizer(hold);
            UIPanGestureRecognizer pan=new UIPanGestureRecognizer(g->wheel((UIPanGestureRecognizer)g));pan.setMinimumNumberOfTouches(2);pan.setMaximumNumberOfTouches(2);addGestureRecognizer(pan);
            // Three fingers held are the Ctrl key; four fingers tapped lock it on or off.
            UILongPressGestureRecognizer ctrl=new UILongPressGestureRecognizer(g->{UIGestureRecognizerState s=g.getState();if(s==UIGestureRecognizerState.Began)fast(true);else if(s==UIGestureRecognizerState.Ended||s==UIGestureRecognizerState.Cancelled||s==UIGestureRecognizerState.Failed)fast(locked);});ctrl.setNumberOfTouchesRequired(3);ctrl.setMinimumPressDuration(.12);ctrl.setAllowableMovement(80);addGestureRecognizer(ctrl);
            UITapGestureRecognizer four=new UITapGestureRecognizer(g->{locked=!locked;fast(locked);log("fast lock "+locked);});four.setNumberOfTouchesRequired(4);addGestureRecognizer(four);
            badge.setText("快进");badge.setTextColor(UIColor.white());badge.setBackgroundColor(UIColor.fromRGBA(0,0,0,.55));badge.setTextAlignment(NSTextAlignment.Center);badge.setFont(UIFont.getSystemFont(16));badge.setHidden(true);badge.setFrame(new CGRect(12,12,64,30));addSubview(badge);}
        final UILabel badge=new UILabel();boolean locked;
        void fast(boolean on){if(engine!=null)engine.rapid=on;badge.setHidden(!on);bringSubviewToFront(badge);}
        void boot(){banner.setText("正在读取游戏…");System.setProperty("dc.saveRoot",new File(DOCS,"saves").getPath());System.setProperty("dc.cacheRoot",new File(System.getenv("TMPDIR"),"media").getPath());PcHost.followsExe=true;
            Thread t=new Thread(()->{try{long t0=System.nanoTime();GameResources files=new GameResources(new FileGameStorage(gameDirectory()));GameVersion v=GameVersion.detect(files);if(!PcHost.supports(v.profile()))throw new IOException("Unsupported "+v.profileId);CircusResources r=new CircusResources(files,v.profile());TextCodec c=GameText.detect(files,v.profile());DesktopLegacy e=new DesktopLegacy(r,c,false);long ms=(System.nanoTime()-t0)/1000000;
                inbox.add(()->{engine=e;media=new IosMedia(e,new File(System.getenv("TMPDIR"),"media"));mediaVm=e.executionIdentity();fb=new int[e.width*e.height];ctx=CGBitmapContext.create(fb,e.width,e.height,8,e.width*4,CGColorSpace.createDeviceRGB(),new CGBitmapInfo(CGBitmapInfo.ByteOrder32Little.value()|CGImageAlphaInfo.NoneSkipFirst.value()));banner.setText("");layout();log("ready "+v.profileId+" "+e.width+"x"+e.height+" "+ms+"ms");});}catch(Throwable e){inbox.add(()->fail(e));}},"dc-boot");t.setDaemon(true);t.start();
            timer=new NSTimer(1.0/60,true,x->frame(),true);last=System.nanoTime();}
        /** The first folder in Documents with an AdvData directory: put one there from Finder or Files. */
        static File gameDirectory()throws IOException{File[] all=DOCS.listFiles();if(all!=null)for(File f:all)if(new File(f,"AdvData").isDirectory())return f;throw new IOException("把游戏目录放进本应用的“文稿”文件夹（要包含 AdvData）");}
        void fail(Throwable e){if(!problem.isEmpty())return;problem=e.toString();StringWriter s=new StringWriter();e.printStackTrace(new PrintWriter(s));log("FAIL "+s);banner.setText("错误："+e);bringSubviewToFront(banner);}
        void persist(){try{if(engine!=null)engine.persist();log("persisted");}catch(Exception e){log("persist "+e);}}
        void layout(){if(engine==null)return;CGRect b=getBounds();double w=b.getWidth(),h=b.getHeight();scale=Math.min(w/engine.width,h/engine.height);ox=(w-engine.width*scale)/2;oy=(h-engine.height*scale)/2;image.setFrame(new CGRect(ox,oy,engine.width*scale,engine.height*scale));}
        @Override public void layoutSubviews(){super.layoutSubviews();layout();}
        void tick(long elapsed)throws Exception{if(!media.playingMovie())engine.tick(elapsed);if(mediaVm!=engine.executionIdentity()){if(media.inBackground()&&!engine.takeRestored())media.rebase();else media.reset();mediaVm=engine.executionIdentity();}media.sync();}
        void click(int x,int y,boolean right)throws Exception{if(media.playingMovie())media.finishMovie();else engine.click(x,y,right);}
        void frame(){long t0=System.nanoTime();try(NSAutoreleasePool pool=new NSAutoreleasePool()){Runnable r;while((r=inbox.poll())!=null)r.run();if(engine==null||!problem.isEmpty())return;
                long now=System.nanoTime(),elapsed=(now-last)/1000000;last+=elapsed*1000000;elapsed=Math.min(250,elapsed);tick(elapsed);if(pathEnv!=null)script();
                if(engine.exitRequested()){persist();System.exit(0);}
                long t1=System.nanoTime();BufferedImage movie=media.movieFrame();BufferedImage img=movie!=null?movie:engine.render();long t2=System.nanoTime();present(img);long t3=System.nanoTime();
                if(burst>0){shot(burstName+"-"+(4-burst),img);burst--;}
                frames++;tickN+=t1-t0;tickMax=Math.max(tickMax,t1-t0);drawN+=t2-t1;drawMax=Math.max(drawMax,t2-t1);presentN+=t3-t2;
                if(!lastEvent.equals(media.lastEvent)){lastEvent=media.lastEvent;log(lastEvent);}
                long ms=now/1000000;if(ms-statAt>5000){if(statAt!=0){Runtime rt=Runtime.getRuntime();log(String.format("STAT frames=%d logic avg=%.1f max=%.1f render avg=%.1f max=%.1f present avg=%.1f ms heap=%dMB script=%s",frames,tickN/1e6/frames,tickMax/1e6,drawN/1e6/frames,drawMax/1e6,presentN/1e6/frames,(rt.totalMemory()-rt.freeMemory())/1048576,engine.scriptName()));}frames=tickN=tickMax=drawN=drawMax=presentN=0;statAt=ms;}
            }catch(Throwable e){fail(e);}}
        void present(BufferedImage img){shown=img;if(img.width==engine.width&&img.height==engine.height&&img.data.length==fb.length){System.arraycopy(img.data,0,fb,0,fb.length);CGImage c=ctx.toImage();UIImage u=new UIImage(c);image.setImage(u);c.dispose();u.dispose();return;}
            CGBitmapContext m=CGBitmapContext.create(img.data,img.width,img.height,8,img.width*4,CGColorSpace.createDeviceRGB(),new CGBitmapInfo(CGBitmapInfo.ByteOrder32Little.value()|CGImageAlphaInfo.NoneSkipFirst.value()));CGImage c=m.toImage();UIImage u=new UIImage(c);image.setImage(u);c.dispose();u.dispose();m.dispose();}
        // ---- input: DesktopRuntime's mouse listener
        int gx(CGPoint p){return (int)((p.getX()-ox)/scale);}int gy(CGPoint p){return (int)((p.getY()-oy)/scale);}
        void secondary(CGPoint p){if(engine==null||!problem.isEmpty())return;try{locked=false;fast(false);engine.cancelPointer();click(gx(p),gy(p),true);log("secondary "+engine.scriptName());}catch(Throwable e){fail(e);}}
        void wheel(UIPanGestureRecognizer g){if(engine==null||!problem.isEmpty())return;try{if(g.getState()==UIGestureRecognizerState.Began){panned=0;engine.cancelPointer();}double y=g.getTranslation(this).getY();if(Math.abs(y-panned)>=48){if(!media.playingMovie())engine.scroll(y<panned?1:-1);panned=y;}}catch(Throwable e){fail(e);}}
        int fingers;boolean several;
        @Override public void touchesBegan(NSSet<UITouch> t,UIEvent e){fingers+=(int)t.size();if(engine==null||!problem.isEmpty())return;try{if(fingers>1){if(!several){several=true;engine.cancelPointer();engine.hover(-1,-1);}return;}CGPoint p=t.any().getLocationInView(this);if(!media.playingMovie())engine.pointerDown(gx(p),gy(p));}catch(Throwable x){fail(x);}}
        @Override public void touchesMoved(NSSet<UITouch> t,UIEvent e){if(engine==null||!problem.isEmpty()||several)return;CGPoint p=t.any().getLocationInView(this);try{if(!media.playingMovie())engine.pointerMove(gx(p),gy(p));}catch(Throwable x){fail(x);}}
        @Override public void touchesEnded(NSSet<UITouch> t,UIEvent e){fingers=Math.max(0,fingers-(int)t.size());if(several){several=fingers>0;return;}if(engine==null||!problem.isEmpty())return;CGPoint p=t.any().getLocationInView(this);release(gx(p),gy(p));}
        @Override public void touchesCancelled(NSSet<UITouch> t,UIEvent e){fingers=Math.max(0,fingers-(int)t.size());several=fingers>0;if(engine==null)return;try{engine.cancelPointer();engine.hover(-1,-1);}catch(Throwable x){fail(x);}}
        void release(int x,int y){try{if(media.playingMovie()){engine.cancelPointer();click(x,y,false);}else{engine.pointerUp(x,y);engine.hover(-1,-1);}}catch(Throwable x2){fail(x2);}}
        // ---- unattended check: real pointer sequences, with frames between press and release
        void shot(String name,BufferedImage img){try(FileOutputStream o=new FileOutputStream(new File(DOCS,"shot-"+name+".png"))){o.write(ImageIO.png(img));}catch(Exception e){log("shot "+e);}}
        void pressAt(int x,int y)throws Exception{engine.pointerDown(x,y);upX=x;upY=y;}
        void script()throws Exception{long now=System.nanoTime()/1000000;String head=path.peek();if(head!=null&&head.startsWith("script:")&&engine.scriptName().equalsIgnoreCase(head.substring(7))){path.poll();fast(false);log("in "+engine.scriptName());stepAt=now;return;}if(head!=null&&head.startsWith("until:")&&(engine.scriptName()+"@"+Integer.toHexString(engine.scriptOffset())).equalsIgnoreCase(head.substring(6))&&engine.textWaiting()){path.poll();fast(false);log("reached "+engine.scriptName()+" : "+engine.body().replace('\n',' '));stepAt=now;return;}if(upX>=0){release(upX,upY);upX=-1;return;}if(now-stepAt<1500)return;stepAt=now;
            if(media.playingMovie()&&media.movieFrame()!=null&&movieSeen++%2==0)shot("movie-"+movieSeen,media.movieFrame());if(path.isEmpty()){if(pathDone==0){pathDone=now;log("PATH COMPLETE");shot("complete",engine.render());}if(now-pathDone>limit*1000L){persist();log("PATH DONE");System.exit(0);}if(engine.menuWaiting()&&!media.playingMovie()){StringBuilder m=new StringBuilder();List<DesktopEngine.Control> cs=engine.controls();for(DesktopEngine.Control c:cs)m.append(c.action).append(' ');log("menu "+engine.scriptName()+" : "+m);if(!cs.isEmpty()&&System.getenv("CIRCUS_CHOOSE")!=null){DesktopEngine.Control c=cs.get(0);pressAt((c.left+c.right)/2,(c.top+c.bottom)/2);}}if(!engine.menuWaiting()&&!media.playingMovie()){log("line "+engine.scriptName()+" : "+engine.body().replace('\n',' '));pressAt(engine.width/2,engine.height/2);}return;}
            String t=path.peek();
            if(media.playingMovie()&&!path.isEmpty()){if(t.equals("skipmovie")){path.poll();}if(media.movieFrame()!=null){shot("movie",media.movieFrame());}if(t.equals("skipmovie")||now-pathDone>0&&movieSeen++>4){log("skip movie");media.finishMovie();}return;}
            if(t.equals("skipmovie")){path.poll();return;}
            StringBuilder names=new StringBuilder();for(DesktopEngine.Control c:engine.controls())names.append(c.action).append(' ');
            if(!names.toString().equals(lastControls)){lastControls=names.toString();log("controls "+engine.scriptName()+" menu="+engine.menuWaiting()+" : "+lastControls);}
            step++;shot(String.format("%02d-at-%s",step,safe(t)),shown!=null?shown:engine.render());
            if(t.equals("back")){path.poll();log("step back");click(engine.width/2,engine.height/2,true);burst(t);return;}
            if(t.equals("tap")){path.poll();log("step tap : "+engine.body().replace('\n',' '));pressAt(engine.width/2,engine.height/2);burst(t);return;}
            if(t.startsWith("xy-")){path.poll();String[] xy=t.split("-");log("step "+t+" controls "+lastControls);pressAt(Integer.parseInt(xy[1]),Integer.parseInt(xy[2]));burst(t);return;}if(t.startsWith("script:")&&!engine.menuWaiting())return;if(t.startsWith("until:")&&!engine.menuWaiting()){pressAt(engine.width/2,engine.height/2);return;}if(t.startsWith("until:")||t.startsWith("script:")){if(engine.menuWaiting()&&!engine.controls().isEmpty()){DesktopEngine.Control c=engine.controls().get(0);pressAt((c.left+c.right)/2,(c.top+c.bottom)/2);}return;}if(t.equals("zoomtest")){path.poll();Crx.Image im=engine.resources.image("bst\\wtr_0a_100");BufferedImage src=new BufferedImage(im.width,im.height,BufferedImage.TYPE_INT_ARGB);src.setRGB(0,0,im.width,im.height,im.argb(),0,im.width);BufferedImage canvas=new BufferedImage(400,500,BufferedImage.TYPE_INT_RGB);ios.awt.Graphics2D g=canvas.createGraphics();g.setColor(new ios.awt.Color(120,150,180));g.fillRect(0,0,400,500);long t0=System.nanoTime();g.drawImage(src,20,20,20+im.width*60/100,20+im.height*60/100,0,0,im.width,im.height,null);log("zoom first "+(System.nanoTime()-t0)/1000+"us");t0=System.nanoTime();g.drawImage(src,20,20,20+im.width*60/100,20+im.height*60/100,0,0,im.width,im.height,null);log("zoom cached "+(System.nanoTime()-t0)/1000+"us");shot("zoom",canvas);return;}if(t.equals("ctrl")){path.poll();fast(true);log("step ctrl on : "+engine.body());return;}if(t.equals("ctrloff")){path.poll();fast(false);log("step ctrl off : "+engine.body());return;}if(t.equals("skip")){path.poll();for(DesktopEngine.Control c:engine.controls())if(c.action.equals(":rapid")){log("step skip");pressAt((c.left+c.right)/2,(c.top+c.bottom)/2);return;}log("no skip button");return;}if(t.startsWith("wheel")){path.poll();engine.scroll(t.endsWith("up")?-1:1);log("step "+t);return;}
            for(DesktopEngine.Control c:engine.controls())if(c.action.equalsIgnoreCase(t)){path.poll();log("step "+t+" in "+engine.scriptName());pressAt((c.left+c.right)/2,(c.top+c.bottom)/2);burst(t);return;}
            if(!engine.menuWaiting())pressAt(engine.width/2,engine.height/2);else if(++stuck>12){log("cannot find "+t+" among "+lastControls);path.poll();stuck=0;}
        }
        int movieSeen,stuck;String lastControls="";String safe(String t){return t.replaceAll("[^a-zA-Z0-9_]","_");}void burst(String t){burst=4;burstName="burst-"+step+"-"+safe(t);}
    }
}
