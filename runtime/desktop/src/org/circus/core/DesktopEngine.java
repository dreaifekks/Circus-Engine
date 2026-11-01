package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Platform contract for script engines, independent of the bytecode format. */
public abstract class DesktopEngine {
    public final CircusResources resources;
    public final TextCodec codec;
    public final boolean audit;
    public final int width,height;
    public final Set<String> loadedImages=new TreeSet<>(),loadedAudio=new TreeSet<>();
    /**
     * Image names the script asked for but the install does not contain. D.C.II refers to the
     * thirty {@code system\scene_sam_NN} thumbnail sheets from the CG-unlock notification and the
     * replay lists; repacks that ship only {@code scene_sam.CRX} still play, they just cannot draw
     * those thumbnails, so the slot stays blank and the name is recorded here instead of aborting.
     */
    public final Set<String> missingImages=new TreeSet<>();
    public final Path saveDirectory;
    public boolean auto,rapid,hidden;
    public boolean fullscreenRequested,windowSizeReset;
    private final long[] mediaPosition=new long[6],resumePosition=new long[6];
    private final String[] mediaResource={"","","","","",""};
    private String[] systemAudio;private long[] systemAudioPosition;
    private final Map<Path,DesktopSave> saveMetadata=new HashMap<>();
    public final Map<Integer,BufferedImage> thumbnails=new HashMap<>();
    private boolean pointerHeld,pointerDragging;
    private long pointerTicket;
    private Control pointerControl;
    protected DesktopEngine(CircusResources r,TextCodec c,boolean audit,int w,int h)throws IOException{
        this(r,c,audit,w,h,mesIdentity(r));
    }
    protected DesktopEngine(CircusResources r,TextCodec c,boolean audit,int w,int h,String identity)throws IOException{
        this(r,c,audit,w,h,identity,Paths.get(System.getProperty("dc.saveRoot",audit?"build/desktop/audit-saves":"build/desktop/saves")));
    }
    protected DesktopEngine(CircusResources r,TextCodec c,boolean audit,int w,int h,String identity,Path root)throws IOException{
        resources=r;codec=c;this.audit=audit;width=w;height=h;
        if(!identity.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Invalid game identity");
        saveDirectory=root.resolve(r.profileId+"-"+identity);
    }
    public static String mesIdentity(CircusResources r)throws IOException{return Binary.sha256(r.files.read("AdvData/MES/START.MES",2*1024*1024)).substring(0,16);}
    public static Path mesSaveDirectory(Path root,CircusResources r)throws IOException{return root.resolve(r.profileId+"-"+mesIdentity(r));}
    public boolean editingText(){return false;}
    public void typeText(String text){}
    public void editKey(int code)throws IOException{}
    public abstract MesVm vm();
    public Object executionIdentity(){return vm();}
    public String scriptName(){return vm().scriptName();}
    public int scriptOffset(){return vm().offset();}
    public boolean halted(){return vm().status()==MesVm.Status.HALTED;}
    public boolean waiting(){return vm().status()==MesVm.Status.WAIT_HOST;}
    public void mediaCompleted(long ticket)throws IOException{vm().signalHost(ticket,MesVm.HostSignal.MEDIA_COMPLETE);}
    protected int saveIcon()throws IOException{return vm().variables.read(9,20);}
    public abstract void tick(long elapsed)throws Exception;
    public abstract void click(int x,int y,boolean right)throws Exception;
    public boolean drag(int x,int y,boolean begin)throws Exception{return false;}
    public void endDrag()throws Exception{}
    protected void pressed(int x,int y,boolean down){}
    protected void cancelDrag(){}
    private Control controlAt(int x,int y){List<Control> list=controls();for(int n=list.size()-1;n>=0;n--)if(list.get(n).contains(x,y))return list.get(n);return null;}
    private boolean sameControl(Control current){return pointerControl==null?current==null:current!=null&&pointerControl.action.equals(current.action)&&pointerControl.left==current.left&&pointerControl.top==current.top&&pointerControl.right==current.right&&pointerControl.bottom==current.bottom;}
    public void pointerDown(int x,int y)throws Exception{cancelPointer();hover(x,y);pointerHeld=true;pointerTicket=ticket();pointerControl=controlAt(x,y);pointerDragging=drag(x,y,true);if(!pointerDragging)pressed(x,y,true);}
    public void pointerMove(int x,int y)throws Exception{hover(x,y);if(!pointerHeld)return;if(ticket()!=pointerTicket){cancelPointer();return;}if(pointerDragging)drag(x,y,false);else pressed(x,y,sameControl(controlAt(x,y)));}
    public void pointerUp(int x,int y)throws Exception{if(!pointerHeld)return;if(ticket()!=pointerTicket){cancelPointer();return;}boolean activate=sameControl(controlAt(x,y)),dragging=pointerDragging;pointerHeld=pointerDragging=false;pressed(x,y,false);if(dragging)endDrag();else if(activate)click(x,y,false);pointerControl=null;}
    public void cancelPointer(){pointerHeld=pointerDragging=false;pointerControl=null;pressed(0,0,false);cancelDrag();}
    public void scroll(int units)throws Exception{}
    public abstract void hover(int x,int y)throws Exception;
    public abstract BufferedImage render()throws IOException;
    public abstract List<Control> controls();
    public abstract String body();
    public String visibleBody()throws IOException{return body();}
    public long playbackMillis(){return 0;}
    public void playbackMillis(long value){}
    public abstract boolean textWaiting();
    public abstract boolean menuWaiting();
    public abstract boolean actorVisible();
    public abstract boolean exitRequested();
    public abstract long ticket();
    public abstract String movie();
    public abstract int movieGeneration();
    public abstract String audio(int ch);
    public abstract int audioGeneration(int ch);
    public abstract boolean audioLoop(int ch);
    public abstract boolean audioActive(int ch);
    public abstract void audioCompleted(int ch,int generation);
    public int audioSeekGeneration(int ch){return 0;}
    public int audioSeekPercent(int ch){return 0;}
    public void audioPosition(int ch,long position,long duration)throws IOException{mediaPosition[ch]=Math.max(0,position);mediaResource[ch]=audio(ch);}
    protected void beginSystemAudio(){systemAudio=new String[6];systemAudioPosition=new long[6];for(int ch=0;ch<6;ch++){systemAudio[ch]=audio(ch);systemAudioPosition[ch]=audio(ch).equals(mediaResource[ch])?mediaPosition[ch]:0;}}
    protected void finishSystemAudio(){if(systemAudio!=null){for(int ch=0;ch<6;ch++)if(systemAudio[ch].equals(audio(ch)))resumePosition[ch]=audio(ch).equals(mediaResource[ch])?mediaPosition[ch]:systemAudioPosition[ch];systemAudio=null;systemAudioPosition=null;}}
    public long takeAudioResume(int ch){long result=resumePosition[ch];resumePosition[ch]=0;return result;}
    public abstract BufferedImage preview()throws IOException;
    public String saveCaption(){return body();}
    public int pan(int ch){return 0;}
    public abstract float volume(int ch)throws IOException;
    public abstract byte[] snapshot()throws IOException;
    public abstract void restore(byte[] bytes)throws IOException;
    public abstract void persist()throws IOException;
    public static final class Control {
        public final String action;public final int left,top,right,bottom;
        public Control(String a,int l,int t,int r,int b){action=a;left=l;top=t;right=r;bottom=b;}
        public boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
    }
    public void choose(String action)throws Exception{for(Control b:controls())if(b.action.equalsIgnoreCase(action)){click((b.left+b.right)/2,(b.top+b.bottom)/2,false);return;}throw new IOException("Missing original action "+action+" in "+scriptName());}
    public void save(Path path)throws IOException{
        DesktopSave s=new DesktopSave();s.state=snapshot();s.textElapsed=playbackMillis();s.created=System.currentTimeMillis();s.icon=saveIcon();String caption=saveCaption();s.caption=caption.length()>200?caption.substring(0,200):caption;
        BufferedImage image=preview();try{s.preview=DesktopSave.thumbnail(image,256,256*height/width);}finally{image.flush();}for(int ch=0;ch<6;ch++){s.audio[ch]=systemAudio==null?audio(ch):systemAudio[ch];s.position[ch]=s.audio[ch].equals(mediaResource[ch])?mediaPosition[ch]:systemAudioPosition==null?0:systemAudioPosition[ch];}
        atomic(path,s.encode());s.state=null;saveMetadata.put(path,s);
    }
    public void restore(Path path)throws IOException{DesktopSave s=DesktopSave.decode(Files.readAllBytes(path));restore(s.state);systemAudio=null;systemAudioPosition=null;playbackMillis(s.textElapsed);for(int ch=0;ch<6;ch++)resumePosition[ch]=s.audio[ch].equals(audio(ch))?s.position[ch]:0;}
    public DesktopSave metadata(Path path)throws IOException{DesktopSave s=saveMetadata.get(path);if(s==null&&Files.exists(path)){s=DesktopSave.decode(Files.readAllBytes(path));s.state=null;saveMetadata.put(path,s);}return s;}
    public void scanThumbnail(int slot,Path path)throws IOException{DesktopSave s=metadata(path);if(s!=null){BufferedImage image=s.image();if(image!=null)thumbnails.put(slot,image);}}
    public static void atomic(Path path,byte[] bytes)throws IOException{
        Files.createDirectories(path.toAbsolutePath().getParent());Path temp=Files.createTempFile(path.toAbsolutePath().getParent(),"dc-save-",".tmp");
        try{Files.write(temp,bytes);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
}
