package org.circus.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

/** PC platform adapter: original MES control flow, original atlas UI, shared VM. */
public final class DesktopLegacy extends DesktopEngine {
    public final Dc2Session session;
    private final Map<String,Picture> images=new LinkedHashMap<String,Picture>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Picture> e){return size()>48;}};
    private final DesktopCaptures captures;private SceneCaptures.Legacy captureState;
    private long textClock,textTicket=-1;
    /** Newest story line; the system menu clears {@code body}, so a save made from SAVE.mes still needs it. */
    private String lastLine="";
    private int savedAutoGeneration;
    public int hovered=-1;
    public String problem="";
    static final class Picture {final Crx.Image raw;final BufferedImage image;Picture(BufferedImage b){raw=new Crx.Image(0,0,b.getWidth(),b.getHeight(),32,new byte[0],null);image=b;}Picture(Crx.Image r){raw=r;image=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,r.width,r.height,r.argb(true),0,r.width);}}

    private boolean toolbar;private Dc2Ui.Button dragged;private int dragKind,pressedButton=-1;
    private String pendingSystemAction;
    private final Dc2NativeData nativeData;
    public DesktopLegacy(CircusResources data,TextCodec codec,boolean audit)throws Exception {
        super(data,codec,audit,PcHost.supports(data.profile)?800:1024,PcHost.supports(data.profile)?600:576);
        session=new Dc2Session(data,codec);captures=new DesktopCaptures(width,height,this::drawCapture);host().captureRenderer=captures;nativeData=PcHost.supports(data.profile)?null:new Dc2NativeData(data,codec);
        Path progress=saveDirectory.resolve("progress.dat");if(!audit&&Files.exists(progress))try(DataInputStream in=new DataInputStream(Files.newInputStream(progress))){host().loadPersistent(in);}
        scanSaves();
    }
    public Dc2Host host(){return session.host;}
    public MesVm vm(){return session.vm;}
    private boolean timedInput(){return host() instanceof PcHost&&((PcHost)host()).hasTimedInput();}
    public void tick(long elapsed)throws Exception {
        Dc2Host h=host();h.fullscreen=fullscreenRequested;session.vm.elapse(elapsed);
        if(h.presentationWaiting||h.textWaiting){if(textTicket!=h.waitTicket()){textClock=0;textTicket=h.waitTicket();}textClock+=elapsed;if(textClock>=textDuration())session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        if(h.returnRequested){session.returnToStory();finishSystemAudio();h=host();}
        MesVm.Status status=session.vm.run(1000);if(status==MesVm.Status.FAULT)throw new IOException(session.vm.error());session.observeTransfer();if(h.windowRequest>=0){fullscreenRequested=h.windowRequest!=0;h.windowRequest=-1;}
        session.captureHistory();
        if(!session.inSystem()&&!h.body.isEmpty())lastLine=h.body;
        if(!session.inSystem()&&(h.textWaiting||h.presentationWaiting)&&!h.history.entries.isEmpty()){Dc2History.Entry entry=h.history.entries.get(h.history.entries.size()-1);if(entry.scene.length==0){ByteArrayOutputStream b=new ByteArrayOutputStream();h.scene.shown.save(new DataOutputStream(b));entry.scene=b.toByteArray();}}
        if(openPendingSystem())return;
        if(h.history.rewindRequest>=0){session.rewindHistory(h.history.rewindRequest);auto=rapid=false;}
        if(h.saveRequest>=0){writeSlot(h.saveRequest);h.completeSave();session.vm.elapse(0);}
        if(h.loadRequest>=0)readSlot(h.loadRequest);
        if(h.autosaveGeneration!=savedAutoGeneration&&!session.inSystem()&&h.textWaiting){savedAutoGeneration=h.autosaveGeneration;if(!audit)writeSlot(h instanceof PcHost?0:2);}
        if(pendingSystemAction==null&&!h.ui.waiting&&(rapid&&h.skipAllowed||(auto||h instanceof PcHost&&h.read(99,13)>=2)&&textClock>autoDelay()&&!h.audioActive[5]))session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
    }
    public java.util.List<Dc2Ui.Button> buttons(){Dc2Host h=host();if(h.ui.waiting){java.util.List<Dc2Ui.Button> bs=new ArrayList<>(h.ui.buttons);if(h.gridWaiting)for(int n=0;n<h.grid.size();n++){Dc2Ui.Button b=h.grid.get(n);b.action="@slot:"+n;bs.add(b);}return bs;}return !h.body.isEmpty()&&!hidden?h.ui.buttonTemplates.get(host() instanceof PcHost?(h.messageMode==0?1:4):(toolbar?6:1)):Collections.emptyList();}
    public void scroll(int units)throws IOException{if(host().history.active&&!(host() instanceof PcHost)){host().history.scroll(units);host().historyButtons();return;}if(host() instanceof PcHost){PcHost h=(PcHost)host();if(h.gridWaiting&&h.list.active())h.write(99,246,h.list.clamp(h.read(99,246)+units*h.list.columns,h.grid.size()));}}
    public void click(int x,int y,boolean right)throws Exception {
        Dc2Host h=host();
        if(h instanceof PcHost&&((PcHost)h).nameInput.active){PcHost p=(PcHost)h;if(right){p.nameAction("@name:cancel");return;}for(Control b:controls())if(b.contains(x,y)){p.nameAction(b.action);return;}return;}
        if(hidden){hidden=false;return;}
        if(!right&&drag(x,y,true)){endDrag();return;}
        if(!right&&h instanceof PcHost&&h.gridWaiting){PcHost p=(PcHost)h;if(p.list.active()){int offset=p.list.scrollAt(x,y,p.gridBounds,p.read(99,246),p.grid.size());if(offset>=0){p.write(99,246,offset);return;}}}
        java.util.List<Dc2Ui.Button> bs=buttons();
        if(!right)for(int n=bs.size()-1;n>=0;n--){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom&&!b.action.isEmpty()){
            if(h instanceof PcHost&&h.history.active&&(b.action.equals(":close")||b.action.equals(":change"))){action(b.action);return;}
            for(Dc2Ui.Quad q:b.sprites)if(q.state==5){int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));session.vm.variables.write(q.sy,q.width,value);}
            if(b.action.startsWith("@slot:"))session.vm.activateGrid(h.waitTicket(),Integer.parseInt(b.action.substring(6)));else if(h.ui.waiting)session.vm.activateMenu(h.waitTicket(),n);else{h.toolbarSound(b);action(b.action);}return;}}
        if(h instanceof PcHost&&!h.confirming&&(timedInput()||h.ui.waiting)&&!h.ui.actions[right?0:1].isEmpty()){session.vm.activateMenu(h.waitTicket(),right?-1:-2);return;}
        if(right&&h.ui.waiting)session.vm.activateMenu(h.waitTicket(),-1);
        else if(!h.ui.waiting){if(right)action(":menu");else session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
    }
    public boolean drag(int x,int y,boolean begin)throws Exception{
        if(begin){dragged=null;dragKind=0;Dc2Host h=host();if(!h.ui.waiting)return false;if(h.history.active&&!(h instanceof PcHost)&&x>=944&&x<984&&y>=112&&y<444)dragKind=1;else if(h instanceof PcHost&&h.gridWaiting&&((PcHost)h).list.active()){int[] b=((PcHost)h).gridBounds;if(x>=b[0]-4&&x<b[2]+4&&y>=b[1]&&y<b[3])dragKind=2;}if(dragKind==0)for(Dc2Ui.Button b:h.ui.buttons)if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)for(Dc2Ui.Quad q:b.sprites)if(q.state==5)dragged=b;}
        if(dragKind==1){Dc2History h=host().history;int max=Math.max(0,h.entries.size()-4);h.cursor=Math.max(0,Math.min(max,(int)Math.round((y-112)*max/300.0)));host().historyButtons();return true;}
        if(dragKind==2){PcHost h=(PcHost)host();int[] b=h.gridBounds;int cy=Math.max(b[1],Math.min(b[3]-1,y));h.write(99,246,y>=b[3]?h.list.clamp(Integer.MAX_VALUE,h.grid.size()):h.list.scrollAt((b[0]+b[2])/2,cy,b,h.read(99,246),h.grid.size()));return true;}
        if(dragged==null)return false;Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:dragged.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return false;
        int value=(int)Math.round(Math.max(0,Math.min(1,(x-cfg.x)/(double)Math.max(1,cfg.y-cfg.x)))*Math.max(0,cfg.sx-1));if(stops!=null){int[] ends={stops.x,stops.y,stops.sx,stops.sy};value=0;while(value<3&&x>=ends[value])value++;}
        vm().variables.write(cfg.sy,cfg.width,value);return true;
    }
    public void endDrag()throws Exception{dragKind=0;if(dragged==null)return;String action=dragged.action;dragged=null;if(!action.isEmpty())for(int n=0;n<host().ui.buttons.size();n++)if(host().ui.buttons.get(n).action.equals(action)){vm().activateMenu(ticket(),n);break;}}
    protected void cancelDrag(){dragged=null;dragKind=0;}
    protected void pressed(int x,int y,boolean down){pressedButton=-1;if(down){java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)pressedButton=n;}}host().ui.pressed=pressedButton<host().ui.buttons.size()?pressedButton:-1;}
    public void action(String a)throws Exception {
        String key=a.toLowerCase(Locale.ROOT);
        if(key.equals(":close")||key.equals(":clear")||key.equals(":change")){hidden=true;return;}
        if(host() instanceof PcHost&&(key.equals(":up")||key.equals(":backlog"))){requestSystem("@history");return;}
        if(host() instanceof PcHost&&key.equals(":down")){vm().signalHost(ticket(),MesVm.HostSignal.ADVANCE);return;}
        if(key.equals(":full")){fullscreenRequested=!fullscreenRequested;return;}
        if(key.equals(":n")){toolbar=!toolbar;host().write(99,227,toolbar?1:0);return;}
        if(key.equals(":auto")){auto=!auto;toolbar=false;return;}
        if(key.equals(":rapid")){rapid=!rapid;toolbar=false;return;}
        if(key.equals(":qsave")||key.equals(":qs")){writeSlot(1);toolbar=false;return;}
        if(key.equals(":qload")||key.equals(":ql")){if(Files.exists(slot(1)))readSlot(1);toolbar=false;return;}
        String script=key.equals(":backlog")?"LOG":key.equals(":torisetu")?"MANUAL":key.equals(":option")&&host() instanceof PcHost?"OPTION":key.equals(":menu")||key.equals(":option")?"ADVMENU":null;
        if(script!=null){requestSystem(script);return;}
        throw new IOException("Unimplemented original system action "+a);
    }
    /** Input may arrive after ADVANCE releases a wait but before the next tick runs MES. */
    private void requestSystem(String action)throws IOException {
        if(session.inSystem())return;
        auto=rapid=false;
        // No story toolbar exists during startup, movies or a title transition.
        if(host().body.isEmpty()&&!host().ui.waiting)return;
        if(pendingSystemAction==null)pendingSystemAction=action;
        openPendingSystem();
    }
    private boolean openPendingSystem()throws IOException {
        if(pendingSystemAction==null)return false;
        if(session.inSystem()||vm().status()==MesVm.Status.HALTED){pendingSystemAction=null;return false;}
        if(host().presentationWaiting)vm().signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);
        // Resolve the released wait, but never advance a dialogue or complete media here.
        if(vm().run(1000)==MesVm.Status.FAULT)throw new IOException(vm().error());
        if(vm().scriptName().equalsIgnoreCase("MAIN")||vm().scriptName().equalsIgnoreCase("START")){pendingSystemAction=null;return false;}
        boolean nativeHistory=pendingSystemAction.equals("@history");
        if(!host().textWaiting&&(nativeHistory||!host().ui.waiting))return false;
        String action=pendingSystemAction;pendingSystemAction=null;
        session.captureHistory();beginSystemAudio();
        if(nativeHistory){session.openNativeSystem();((PcHost)host()).openHistory();}
        else session.openSystem(action);
        toolbar=auto=rapid=false;return true;
    }
    public void hover(int x,int y)throws IOException{int old=hovered;hovered=-1;java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)hovered=n;}if(host().ui.waiting)host().hoverMenu(hovered<host().ui.buttons.size()?hovered:-1);else if(old!=hovered&&hovered>=0&&bs.get(hovered).hoverSound!=255)host().play(1,resources.named("sound",bs.get(hovered).hoverSound),false);if(host() instanceof PcHost){Pc2Map m=((PcHost)host()).map;if(m.active&&m.selected<0){m.hovered=-1;for(int n=0;n<m.regions().length;n++){int[] r=m.regions()[n];if(x>=r[0]&&x<r[2]&&y>=r[1]&&y<r[3])m.hovered=n;}}}}
    public java.util.List<Control> controls(){java.util.List<Control> out=new ArrayList<>();for(Dc2Ui.Button b:buttons())if(!b.action.isEmpty())out.add(new Control(b.action,b.left,b.top,b.right,b.bottom));if(host() instanceof PcHost&&((PcHost)host()).nameInput.active){PcNameInput name=((PcHost)host()).nameInput;if(name.confirming)return out;for(int n=0;n<8;n++){int x=336+(n/4)*192+(n%4)*32;out.add(new Control("@name:field:"+n,x,40,x+32,72));}java.util.List<String> rows=name.visibleRows();for(int r=0;r<rows.size();r++)for(int c=0;c<Math.min(17,rows.get(r).length());c++){int x=280+c*24,y=96+r*24+(r>=4?8:0);if(rows.get(r).charAt(c)!='　')out.add(new Control("@name:char:"+(r*17+c),x,y,x+24,y+24));}}return out;}
    private int textSpeed()throws IOException{return host().read(99,host() instanceof PcHost?13:auto?27:26);}
    private long textDuration()throws IOException{return TextPlayback.duration(resources.profile.id,textSpeed(),body().codePointCount(0,body().length()));}
    private long autoDelay()throws IOException{if(host() instanceof PcHost){int bytes=body().getBytes(codec.charset).length;return bytes*(host().read(99,13)==3?200L/3:100L);}return Math.max(100,20-host().read(99,29))*100L;}
    public String visibleBody()throws IOException{return body().substring(0,TextPlayback.visible(resources.profile.id,textSpeed(),body(),playbackMillis(),host().textRendered||!host().presentationWaiting));}
    public long playbackMillis(){return textTicket==ticket()?textClock:0;}
    public void playbackMillis(long value){textTicket=ticket();textClock=value;}
    public String body(){return host().body;}
    public boolean textWaiting(){return host().textWaiting||host().presentationWaiting;}
    public boolean menuWaiting(){return host().ui.waiting;}
    public boolean actorVisible(){for(int n=2;n<=9;n++)if(!host().scene.shown.slots[n].isEmpty())return true;return false;}
    public boolean exitRequested(){return host().exitRequested;}
    public long ticket(){return host().waitTicket();}
    public String movie(){return host().movie;}
    public int movieGeneration(){return host().movieGeneration;}
    public String audio(int ch){return host().audio[ch];}
    public int audioGeneration(int ch){return host().audioGeneration[ch];}
    public boolean audioLoop(int ch){return host().audioLoop[ch];}
    public boolean audioActive(int ch){return host().audioActive[ch];}
    public void audioCompleted(int ch,int generation){host().audioCompleted(ch,generation);}
    public int pan(int ch){return ch==5?host().voicePan:0;}
    public float volume(int ch)throws IOException{int enabled=ch==0?14:ch==5?5:15,level=ch==0?7:ch==5?9:8;return host().read(99,enabled)==0?0:(ch==0?host().musicFadeGain():ch==5?host().voiceGain():1)*Math.max(0,Math.min(1,host().read(99,level)/30f));}
    public byte[] snapshot()throws IOException{return session.storySnapshot();}
    public void restore(byte[] bytes)throws IOException{cancelPointer();session.restore(bytes);host().ui.pressed=-1;savedAutoGeneration=host().autosaveGeneration;textTicket=-1;hidden=toolbar=auto=rapid=false;pendingSystemAction=null;scanSaves();}
    private Path slot(int n)throws IOException{if(n<0||n>255)throw new IOException("Invalid save slot "+n);return saveDirectory.resolve("slot-"+n+".sav");}
    private void writeSlot(int n)throws IOException{save(slot(n));scanSaves();persist();}
    private void readSlot(int n)throws IOException{restore(slot(n));}
    private void scanSaves()throws IOException{host().availableSaves.clear();host().saveLabels.clear();thumbnails.clear();for(int n=0;n<256;n++)if(Files.exists(slot(n))){host().availableSaves.add(n);host().saveLabels.put(n,new java.text.SimpleDateFormat("MM/dd HH:mm").format(new Date(Files.getLastModifiedTime(slot(n)).toMillis())));scanThumbnail(n,slot(n));}}
    public String saveCaption(){return host().checkpointLabel.isEmpty()?(body().isEmpty()?lastLine:body()):codec.apply(host().checkpointLabel);}
    public BufferedImage preview()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{scene(g,host().systemScene!=null?host().systemScene:host().scene.shown);}finally{g.dispose();}return image;}
    public void persist()throws IOException{if(audit)return;ByteArrayOutputStream b=new ByteArrayOutputStream();host().savePersistent(new DataOutputStream(b));atomic(saveDirectory.resolve("progress.dat"),b.toByteArray());}
    private Picture picture(String name)throws IOException {if(name==null||name.isEmpty())return null;if(name.equals("@ui-capture"))return new Picture(captures.resolve(host().uiCapture));Picture p=images.get(name);if(p==null){p=new Picture(resources.image(name));images.put(name,p);loadedImages.add(name);}return p;}
    private int color(int style){return (captureState==null?host().ui.palette:captureState.palette)[style&63];}
    private BufferedImage drawCapture(int kind,DataInputStream input)throws IOException {
        if(kind!=UiCapture.LEGACY)throw new IOException("Wrong legacy UI capture profile");SceneCaptures.Legacy saved=captureState;captureState=new SceneCaptures.Legacy(input);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);scene(g,captureState.frame);}finally{g.dispose();captureState=saved;}return image;
    }
    private void at(Graphics2D g,String name,int x,int y)throws IOException {Picture p=picture(name);if(p!=null)g.drawImage(p.image,x+p.raw.x,y+p.raw.y,null);}
    private void text(Graphics2D g,String s,int x,int y,int lineHeight,int style,int width){
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,22));Color foreground=new Color(color(style),true);
        // PC1 417A5E / PC2 421CA6 / DM 4024E8 draw style 0x80 at (+1,+1), using palette 0; PC2 uses 9 for black text.
        boolean shadow=(style&0x80)!=0;Color shade=shadow?new Color(color(host() instanceof PcHost&&((PcHost)host()).second&&(style&63)==0?9:0),true):foreground;
        g.setColor(foreground);int at=x,base=y+22;for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=Character.charCount(cp);int w=g.getFontMetrics().stringWidth(c);if(cp==10||at+w>x+width){at=x;base+=Math.max(22,lineHeight);if(cp==10)continue;}if(shadow){g.setColor(shade);g.drawString(c,at+1,base+1);g.setColor(foreground);}g.drawString(c,at,base);at+=w;}
    }
    private void quad(Graphics2D g,Dc2Ui.Quad q,String[] slots,boolean sprite)throws IOException {
        if(q.binding!=0&&session.vm.variables.read(q.binding>>8,q.binding&255)==0)return;
        if(q.type==2){if(!q.text.isEmpty())text(g,codec.apply(q.text),q.x,q.y,sprite?23:Math.max(22,q.width),sprite?q.flags:q.height,width-q.x);return;}
        if(q.width<=0||q.height<=0)return;
        Composite old=g.getComposite();if(q.opaque())g.setComposite(AlphaComposite.SrcOver.derive(80/255f));
        if(q.type==1){g.setColor(new Color(color(q.flags),true));g.fillRect(q.x,q.y,q.width,q.height);}
        else if(q.type==0){Picture p=picture(slots[q.slot()]);if(p!=null){int sx=q.sx,sy=q.sy;if(q.animation!=0&&q.frames>0&&q.period>0)sx+=(int)(host().scene.effectClock/(34L*q.period)%q.frames)*q.step;int l=Math.max(0,sx),t=Math.max(0,sy),r=Math.min(p.image.getWidth(),sx+q.width),b=Math.min(p.image.getHeight(),sy+q.height);if(l<r&&t<b)g.drawImage(p.image,q.x+l-sx,q.y+t-sy,q.x+r-sx,q.y+b-sy,l,t,r,b,null);}}
        else throw new IOException("Desktop draw type pending: "+q.type);
        g.setComposite(old);
    }
    private void scene(Graphics2D g,Dc2Scene.Frame f)throws IOException {
        g.setColor(Color.BLACK);g.fillRect(0,0,width,height);at(g,f.slots[1],-f.x,-f.y);
        boolean pc2=resources.profileId.equals("dc2pc-5769");
        // PC2 41A950 visits actor slots 0..3, so a later close-up covers earlier actors.
        for(int order=0;order<4;order++){int n=pc2?order:3-order;int[] a=f.actors[n];for(int slot:new int[]{n+2,n+6}){String name=slot==n+6?resources.characterOverlay(f.slots[n+2],f.slots[slot]):f.slots[slot];Picture p=picture(name);if(p==null)continue;
            if(pc2){PcActorLayout r=PcActorLayout.dc2pc(a,p.raw.width,p.raw.height);g.drawImage(p.image,r.x,r.y,r.x+r.width,r.y+r.height,0,0,p.raw.width,p.raw.height,null);}
            else{AffineTransform tr=g.getTransform();g.translate(a[0]+a[3],a[1]+a[4]);g.scale(a[2]/100.0,a[2]/100.0);g.drawImage(p.image,p.raw.x-a[3],p.raw.y-a[4],null);g.setTransform(tr);}
        }}
        drawEffects(g);for(Dc2Ui.Quad q:f.quads)quad(g,q,f.slots,false);
    }
    private void drawEffects(Graphics2D g)throws IOException{
        Dc2Scene s=host().scene;SceneCaptures.Legacy frozen=captureState;
        if(frozen==null?host().read(99,10)==0:!frozen.effects)return;
        long t=(frozen==null?s.effectClock:frozen.clock)/34;int particleKind=frozen==null?s.particleKind:frozen.particleKind,particleStrength=frozen==null?s.particleStrength:frozen.particleStrength,overlayKind=frozen==null?s.overlayKind:frozen.overlayKind;String[] slots=frozen==null?s.slots:frozen.frame.slots;
        if(overlayKind==7){Picture p=picture(slots[15]);if(p!=null){g.setComposite(AlphaComposite.SrcOver.derive(96/255f));int x=-(int)(t%p.image.getWidth());for(;x<width;x+=p.image.getWidth())g.drawImage(p.image,x,0,null);g.setComposite(AlphaComposite.SrcOver);}}
        if(particleKind==0)return;Picture p=picture(slots[particleKind==2?17:16]);if(p==null)return;int level=particleStrength&15,count=level>=5?50:new int[]{0,5,10,15,30}[level];
        for(int n=0;n<count;n++){int w=particleKind==2?8:32,h=particleKind==2?80:24,sy=particleKind==2?0:n%6*28;if(sy+h>p.image.getHeight())continue;int sx=particleKind==2?0:(int)(t/(4+n%8)%5)*w;int x=(int)((n*193+Math.sin((t*(4+n%8)+n*71)*Math.PI/180)*(24+n%24)+width)%width),y=(int)((n*137+t*(particleKind==2?32:2+n%4))%(height+64))-32;if(particleKind==9)y=height-y;g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    }
    public BufferedImage render()throws IOException {
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();try{
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);Dc2Host h=host();if(h instanceof PcHost&&((PcHost)h).map.active){drawMap(g,(PcHost)h);return result;}scene(g,h.scene.shown);drawTransition(g);if(h instanceof PcHost&&((PcHost)h).pcCredits.active){PcCredits c=((PcHost)h).pcCredits;int first=(int)c.scroll()/c.stripHeight();g.setColor(Color.BLACK);g.fillRect(0,0,width,height);for(int n=first;n<=Math.min(c.lastStrip(),first+1);n++)at(g,c.image(n),0,(int)(n*c.stripHeight()-c.scroll()));return result;}if(h.credits.active()){drawCredits(g);return result;}
            if(!h.body.isEmpty()&&!hidden){
                Dc2Ui.Quad body=null,name=null;for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode==0?1:4)){quad(g,q,h.scene.slots,false);if(q.type==2&&q.width>2)body=q;}
                if(!h.speaker.isEmpty())for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode==0?2:5)){quad(g,q,h.scene.slots,false);if(q.type==2&&q.width>2)name=q;}
                if(name!=null)text(g,h.speaker,name.x,name.y,name.width,name.height,700);
                if(body!=null)text(g,visibleBody(),body.x,body.y,body.width,body.height,width-40-body.x);
                for(int n=18;n<=19;n++)at(g,h.scene.shown.slots[n],h.scene.portraitX,h.scene.portraitY);
            }
            if(h instanceof PcHost&&((PcHost)h).nameInput.active)drawName(g,((PcHost)h).nameInput);
            java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);boolean slider=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==5)slider=true;for(Dc2Ui.Quad q:b.sprites)if(q.state==0||q.state==99||(q.state==1&&n==hovered)||(q.state==2&&!slider&&(b.action.equals(":c")?h.confirmationChecked():n==pressedButton)))quad(g,q,h.scene.slots,true);}
            for(Dc2Ui.Button b:bs)drawSlider(g,b);
            if(h.gridWaiting)drawGrid(g);
            if(h.history.active&&!(h instanceof PcHost))drawHistory(g);
            if(h.musicRoom.active)drawMusicRoom(g);
        }finally{g.dispose();}return result;
    }
    private void drawName(Graphics2D g,PcNameInput name)throws IOException{
        for(int n=0;n<14;n++){if(n>=10&&name.pages()==1)continue;int[] r=name.rectangle(n),a=name.atlas(r[4]);chip(g,10,a[0],a[1],r[2]-r[0],r[3]-r[1],r[0],r[1]);}
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,32));g.setColor(Color.WHITE);g.drawString(name.family,336,72);g.drawString(name.given,528,72);
        g.setColor(new Color(255,255,255,180));g.drawRect(336+name.field*192+name.cursor*32,40,31,33);
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,22));java.util.List<String> rows=name.visibleRows();for(int r=0;r<rows.size();r++)for(int c=0;c<Math.min(17,rows.get(r).length());c++){
            String ch=rows.get(r).substring(c,c+1);int x=280+24*c,y=96+24*r+(r>=4?8:0);g.setColor(new Color(0,70,110));g.drawString(ch,x+2,y+24);g.setColor(Color.WHITE);g.drawString(ch,x,y+22);
        }
        int[] a=name.atlas(name.page+41);chip(g,10,a[0],a[1],16,32,520,288);chip(g,10,160,304,16,32,536,288);a=name.atlas(name.pages()+30);chip(g,10,a[0],a[1],16,24,552,296);
    }
    public boolean editingText(){return host() instanceof PcHost&&((PcHost)host()).nameInput.active;}
    public void typeText(String text){if(editingText())((PcHost)host()).nameInput.insert(text);}
    public void editKey(int code)throws IOException{if(!editingText())return;PcHost p=(PcHost)host();if(code==KeyEvent.VK_BACK_SPACE||code==KeyEvent.VK_DELETE)p.nameAction("@name:button:4");else if(code==KeyEvent.VK_LEFT)p.nameAction("@name:button:6");else if(code==KeyEvent.VK_RIGHT)p.nameAction("@name:button:5");else if(code==KeyEvent.VK_ENTER)p.nameAction("@name:button:7");else if(code==KeyEvent.VK_ESCAPE)p.nameAction("@name:cancel");}
    private void mapChip(Graphics2D g,String name,int x,int y,int sx,int sy,int w,int h)throws IOException{Picture p=picture(name);if(p!=null)g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    private void drawMap(Graphics2D g,PcHost h)throws IOException{
        Pc2Map m=h.map;at(g,m.background(),0,0);int hover=m.selected>=0?m.selected:m.hovered;
        for(int n=0;n<10;n++){if(session.vm.variables.read(5,n)==0)continue;int location=session.vm.variables.read(5,n+20);if(location<0||location>=7)throw new IOException("Map actor location "+location);int[] r=(m.school?Pc2Map.SCHOOL_ACTOR:Pc2Map.ISLAND_ACTOR)[location];if(r[2]==r[0])continue;
            int shift=session.vm.variables.read(5,50)==1?-120:m.school?55:10,dy=0;if(m.school&&location==0){shift-=50;dy=100;}for(int later=n+1;later<10;later++)if(session.vm.variables.read(5,later+20)==location){shift+=120;break;}
            int[] periods=Pc2Map.DURATIONS[n];long total=0;for(int t:periods)total+=t;long t=m.elapsed%total;int frame=0;while(frame<periods.length-1&&t>periods[frame])t-=periods[frame++];
            mapChip(g,"MAP/chip_"+Pc2Map.ACTORS[n],r[0]+shift,r[1]+dy,frame%5*168,frame/5*152+(location==hover?760:0),168,152);
        }
        for(int[] r:m.regions())if(r[2]>r[0])mapChip(g,"MAP/movestop",r[0],r[1],816,0,24,24);
        if(hover>=0){mapChip(g,m.chip(),136,524,0,hover*56,424,56);int character=-1;for(int n=0;n<10;n++)if(session.vm.variables.read(5,20+n)==hover)character=n;
            if(character>=0&&session.vm.variables.read(5,character)!=0)mapChip(g,"MAP/movestop",0,440,character%6*136,(m.school?256:160)+(character>=6?320:0),136,160);
        }
        if(m.selected>=0){int k=0;for(Dc2Ui.Button b:h.ui.buttons){mapChip(g,m.chip(),b.left,b.top,k*96,hovered==k?464:393,96,72);k++;}}
    }
    private void drawTransition(Graphics2D g)throws IOException{
        Dc2Scene s=host().scene;if(s.transitionRemaining<=0||s.transitionDuration<=0)return;
        if(s.transitionKind==221||s.transitionKind==222){float p=s.transitionRemaining/(float)s.transitionDuration;g.setColor(new Color(0f,0f,0f,s.transitionKind==221?p:1-p));g.fillRect(0,0,width,height);return;}
        BufferedImage old=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D prev=old.createGraphics();scene(prev,s.previous);prev.dispose();
        if(s.transitionKind>=130&&s.transitionKind<=132){int variant=s.transitionKind-130,ticks=(int)((s.transitionDuration-s.transitionRemaining)/40),gain=0;for(int n=2;n<=ticks;n++)gain+=Math.min(16,n);boolean second=((PcHost)host()).second;
            int[] pixels=old.getRGB(0,0,width,height,null,0,width);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int v=variant==0?0:(second?8:0)-x/4-(variant==2?y/4:0);int alpha=255-255*Math.max(0,Math.min(16,v+gain))/16;int i=y*width+x;pixels[i]=(pixels[i]&0xffffff)|(alpha<<24);}old.setRGB(0,0,width,height,pixels,0,width);
        }else g.setComposite(AlphaComposite.SrcOver.derive(s.transitionRemaining/(float)s.transitionDuration));
        g.drawImage(old,0,0,null);g.setComposite(AlphaComposite.SrcOver);
    }
    private void drawSlider(Graphics2D g,Dc2Ui.Button b)throws IOException{
        Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:b.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return;
        int index=host().ui.buttons.indexOf(b),atlasState=index==pressedButton?26:index==hovered?25:24;boolean hasAtlas=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==atlasState)hasAtlas=true;if(!hasAtlas)atlasState=24;
        int value=Math.max(0,Math.min(cfg.sx-1,vm().variables.read(cfg.sy,cfg.width))),dx=(cfg.y-cfg.x)*value/Math.max(1,cfg.sx-1),left=cfg.x,right=cfg.x+dx;
        if(stops!=null){int[] ends={cfg.x,stops.x,stops.y,stops.sx,stops.sy};left=ends[Math.min(3,value)];right=ends[Math.min(3,value)+1];}
        for(Dc2Ui.Quad q:b.sprites){Shape clip=g.getClip();AffineTransform tr=g.getTransform();if(q.state==2||q.state==12){g.clipRect(left,0,Math.max(0,right-left),height);quad(g,q,host().scene.slots,true);}else if(q.state==6){g.translate(dx,0);quad(g,q,host().scene.slots,true);}else if(q.state==atlasState){Picture p=picture(host().scene.slots[q.slot()]);if(p!=null){int w=q.width/Math.max(1,cfg.sx),sx=q.sx+w*value;g.drawImage(p.image,q.x,q.y,q.x+w,q.y+q.height,sx,q.sy,sx+w,q.sy+q.height,null);}}g.setClip(clip);g.setTransform(tr);}
    }
    private void drawCredits(Graphics2D g)throws IOException{Dc2Credits r=host().credits;g.setColor(Color.BLACK);g.fillRect(0,0,width,height);Shape old=g.getClip();g.clipRect(80,160,400,300);at(g,r.image(true,r.picture()),80,160);g.setClip(old);at(g,"ED/photo",80,160);int y=-(int)(r.scroll()%600);at(g,r.image(false,r.strip()),480,y);if(r.strip()+1<r.strips)at(g,r.image(false,r.strip()+1),480,y+600);}
    private void drawGrid(Graphics2D g)throws IOException {
        if(host() instanceof PcHost){drawPcGrid(g,(PcHost)host());return;}
        Picture p=picture(host().scene.slots[10]);if(p==null)return;int page=host().read(99,0);
        for(int n=0;n<host().grid.size();n++){Dc2Ui.Button b=host().grid.get(n);int slot=1+page*10+n,sx=slot==1?1008:0,sy=slot==3?84:slot<=2?0:168;
            g.drawImage(p.image,b.left,b.top,b.right,b.bottom,sx,sy,Math.min(sx+336,p.image.getWidth()),sy+84,null);
            if(slot>3){int number=slot-3;for(int k=0;k<2;k++){int d=k==0?number/10:number%10,x=b.left+52+k*16;g.drawImage(p.image,x,b.top+28,x+16,b.top+52,336+d*16,508,352+d*16,532,null);}}
            String label=host().saveLabels.get(slot);if(label!=null)text(g,label,b.left+120,b.top+24,22,13,b.right-b.left-136);
        }
    }
    private void drawPcGrid(Graphics2D g,PcHost h)throws IOException{
        if(h.list.active()){int offset=h.list.clamp(h.read(99,246),h.grid.size());for(int n=0;n<h.grid.size();n++){ScriptList.Item item=h.list.at(offset,n,h.grid.size());if(item==null)continue;Dc2Ui.Button b=h.grid.get(n);java.awt.geom.AffineTransform old=g.getTransform();g.translate(b.left,b.top);boolean selected=hovered==h.ui.buttons.size()+n,sceneList=h.second&&h.list.textStyle==4;String name=sceneList?h.sceneListName(item):"";
        if(sceneList&&PcHost.sceneListSpecial(name))chip(g,12,232,selected?56:0,232,56,0,0);
        for(Dc2Ui.Quad q:ScriptList.visible(item.sprites,selected?1:99)){
            if(q.type!=2){quad(g,q,h.scene.slots,true);continue;}
            // 41169A..41177D: -1 Y hides a caption; -1 X centers it. Text is
            // clipped to the cell, independently of the atlas rectangle.
            if(q.y==-1)continue;Shape clip=g.getClip();g.clipRect(0,0,b.right-b.left-(sceneList?12:0),b.bottom-b.top);int x=q.x==-1?(b.right-b.left-q.width)/2:q.x;
            if(sceneList){String date=PcHost.sceneListDate(name);pcSceneText(g,codec.apply(q.text),q.width/14,x,q.y,b.right-b.left-x);pcSceneText(g,date,date.getBytes(Binary.SJIS).length,x,6,b.right-b.left-x);}else text(g,codec.apply(q.text),x,q.y,q.height,q.flags,b.right-b.left-x);g.setClip(clip);
        }g.setTransform(old);}return;}
        Picture p=picture(h.scene.slots[10]);if(p==null)return;
        for(int n=0;n<h.grid.size();n++){Dc2Ui.Button b=h.grid.get(n);int slot=h.gridSlot(n),sx=hovered==h.ui.buttons.size()+n?288:0;
            int sy=h.second?(slot==0?0:slot==1?(h.saveGrid?72:216):144):(h.saveGrid?0:72);
            if(!h.second&&slot<2)sx=0;
            g.drawImage(p.image,b.left,b.top,b.right,b.bottom,sx,sy,sx+288,sy+72,null);
            DesktopSave meta=metadata(slot(slot));if(meta!=null&&slot>=2&&meta.icon>=0&&meta.icon<16){chip(g,11,meta.icon*56,312,56,56,b.left+8,b.top+8);}
            String label=h.saveLabels.get(slot);if(label!=null)roomText(g,label,b.left+(h.second?72:104),b.top+(h.second?50:42),180,16,0xffffffff);
            if(meta!=null&&!meta.caption.isEmpty()){Shape clip=g.getClip();g.clipRect(b.left+64,b.top,224,42);roomText(g,meta.caption,b.left+(h.second?72:64),b.top+(h.second?10:6),216,18,0xffffffff);g.setClip(clip);}
        }
    }
    /** 407DD6..40814E uses palette 7 for an eight-neighbour outline and palette 14 for the text. */
    private void pcSceneText(Graphics2D g,String value,int rawLength,int x,int y,int width)throws IOException{int size=host().variables.read(3,30)!=0?(rawLength>20?14:16):host().variables.read(3,31)!=0?16:28;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dy!=0)roomText(g,value,x+dx,y+dy,width,size,color(7));roomText(g,value,x,y,width,size,color(14));}
    private void chip(Graphics2D g,int slot,int sx,int sy,int w,int h,int x,int y)throws IOException{Picture p=picture(host().scene.slots[slot]);if(p!=null)g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    private void roomText(Graphics2D g,String value,int x,int y,int w,int size,int color){g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(new Color(color,true));int at=x,base=y+size;for(char ch:value.toCharArray()){int cw=g.getFontMetrics().charWidth(ch);if(ch=='\n'||at+cw>x+w){at=x;base+=size+4;if(ch=='\n')continue;}g.drawString(String.valueOf(ch),at,base);at+=cw;}}
    private void drawHistory(Graphics2D g)throws IOException{
        Dc2History h=host().history;for(int row=0;row<4&&row+h.cursor<h.entries.size();row++){int index=row+h.cursor,y=25+row*133;Dc2History.Entry e=h.entries.get(index);chip(g,10,228,0,680,124,200,y-4);Shape clip=g.getClip();g.clipRect(200,y-4,680,124);
            Integer face=nativeData==null?null:nativeData.faces.get(e.speaker);if(face!=null)chip(g,10,face==24?864:168+(face%6)*116,face==24?480:132+(face/6)*116,116,116,240,y);else roomText(g,e.speaker,212,y+12,135,20,0xff9a386d);
            roomText(g,e.body,360,y+5,500,22,index==h.selected?0xff99325b:0xff663b63);if(!e.voice.isEmpty())chip(g,10,84,120,24,24,332,y+6);g.setClip(clip);
        }chip(g,10,988,0,32,332,948,112);chip(g,10,908,0,40,24,944,88);chip(g,10,948,0,40,24,944,444);chip(g,10,908,72,32,32,948,112+(int)(300L*h.cursor/Math.max(1,h.entries.size()-4)));
        if(h.selected>=0&&h.selected<h.entries.size()){byte[] bytes=h.entries.get(h.selected).scene;if(bytes.length>0){Dc2Scene.Frame f=new Dc2Scene.Frame();f.load(new DataInputStream(new ByteArrayInputStream(bytes)));AffineTransform old=g.getTransform();Shape clip=g.getClip();g.clipRect(28,184,160,90);g.translate(28,184);g.scale(160.0/width,90.0/height);scene(g,f);g.setTransform(old);g.setClip(clip);}}
    }
    private void drawMusicRoom(Graphics2D g)throws IOException{Dc2MusicRoom r=host().musicRoom;roomText(g,String.format(Locale.ROOT,"%03d  %s",r.selected+1,r.data.musicTitles.get(r.selected)),42,201,320,19,0xffa73563);for(int row=0;row<10&&row+r.top<r.data.musicTitles.size();row++){int index=row+r.top,y=78+row*41;boolean selected=index==r.selected;chip(g,10,491,selected?159:77,59,37,435,y);roomText(g,String.format(Locale.ROOT,"%03d",index+1),450,y+7,47,18,selected?0xffcc416e:0xff95536b);roomText(g,r.data.musicTitles.get(index),504,y+6,440,20,0xffa73563);}chip(g,10,436,88,10,388,960,88);chip(g,10,392,32,27,32,951,88+350*r.top/63);}
}
