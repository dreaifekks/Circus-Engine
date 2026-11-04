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
        session=new Dc2Session(data,codec);captures=new DesktopCaptures(width,height,this::drawCapture);captures.screen=()->lastFrame;host().captureRenderer=captures;nativeData=PcHost.supports(data.profile)?null:new Dc2NativeData(data,codec);
        Path progress=saveDirectory.resolve("progress.dat");if(!audit&&Files.exists(progress))try(DataInputStream in=new DataInputStream(Files.newInputStream(progress))){host().loadPersistent(in);}
        scanSaves();
    }
    public Dc2Host host(){return session.host;}
    public MesVm vm(){return session.vm;}
    private boolean timedInput(){return host() instanceof PcHost&&((PcHost)host()).hasTimedInput();}
    public void tick(long elapsed)throws Exception {
        if(flash>0){flash-=elapsed;return;} // 41D980: the white flash holds the script and the particles.
        Dc2Host h=host();boolean exe=followsPc2Exe();if(exe)((PcHost)h).ctrlHeld=rapid;h.fullscreen=fullscreenRequested;session.vm.elapse(elapsed);
        if(h.presentationWaiting||h.textWaiting){if(textTicket!=h.waitTicket()){textClock=0;textTicket=h.waitTicket();}textClock+=elapsed;if(textClock>=textDuration())session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        if(h.returnRequested){session.returnToStory();finishSystemAudio();h=host();if(exe){((PcHost)h).ctrlHeld=rapid;if(h.read(99,19)==4){((PcHost)h).leaveForLabel();session.vm.jump("new");}}}
        MesVm.Status status=session.vm.run(1000);if(status==MesVm.Status.FAULT)throw new IOException(session.vm.error());session.observeTransfer();
        if(exe&&((PcHost)h).callRequest!=null){String called=((PcHost)h).callRequest;((PcHost)h).callRequest=null;if(!session.inSystem()){beginSystemAudio();session.openCall(called);auto=false;toolbar=false;return;}}if(h.windowRequest>=0){fullscreenRequested=h.windowRequest!=0;h.windowRequest=-1;}
        session.captureHistory();
        if(!session.inSystem()&&!h.body.isEmpty())lastLine=h.body;
        if(!h.keepsOnlyLogText()&&!session.inSystem()&&(h.textWaiting||h.presentationWaiting)&&!h.history.entries.isEmpty()){Dc2History.Entry entry=h.history.entries.get(h.history.entries.size()-1);if(entry.scene.length==0){ByteArrayOutputStream b=new ByteArrayOutputStream();h.scene.shown.save(new DataOutputStream(b));entry.scene=b.toByteArray();}}
        if(openPendingSystem())return;
        if(h.history.rewindRequest>=0){session.rewindHistory(h.history.rewindRequest);auto=rapid=false;}
        if(h.saveRequest>=0){writeSlot(h.saveRequest);h.completeSave();session.vm.elapse(0);}
        if(h.loadRequest>=0)readSlot(h.loadRequest);
        if(h.autosaveGeneration!=savedAutoGeneration&&!session.inSystem()&&h.textWaiting){savedAutoGeneration=h.autosaveGeneration;if(!audit)writeSlot(h instanceof PcHost?0:2);}
        if(exe){
            // 41BC70, 420F8E: entering a wait hit-tests the pointer again. 422219: auto counts only in text waits.
            Dc2Host now=host();if(now.waitTicket()!=hoverTicket){hoverTicket=now.waitTicket();if(now.ui.waiting||now.textWaiting)hoverPc2(pointerX,pointerY);else hovered=-1;}
            if(pendingSystemAction==null&&!now.ui.waiting&&!hidden&&!now.history.active&&(now.textWaiting||now.presentationWaiting)&&(((PcHost)now).fast()||(auto||now.read(99,13)>=2)&&now.textWaiting&&textClock>autoDelay()&&!now.audioActive[5]))session.vm.signalHost(now.waitTicket(),MesVm.HostSignal.ADVANCE);
            return;
        }
        if(pendingSystemAction==null&&!h.ui.waiting&&(rapid&&h.skipAllowed||(auto||h instanceof PcHost&&h.read(99,13)>=2)&&textClock>autoDelay()&&!h.audioActive[5]))session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
    }
    public java.util.List<Dc2Ui.Button> buttons(){Dc2Host h=host();if(h.ui.waiting){boolean choice=followsPc2Exe()&&((PcHost)h).choiceWaiting();if(hidden&&(choice||followsPc2Exe()&&h.history.active))return Collections.emptyList();java.util.List<Dc2Ui.Button> bs=new ArrayList<>(h.ui.buttons);if(choice)bs.addAll(h.ui.buttonTemplates.get(h.messageMode==0?1:4)); /* 424D22: the toolbar stays usable over a choice; it comes last so option numbers hold. */if(h.gridWaiting)for(int n=0;n<h.grid.size();n++){Dc2Ui.Button b=h.grid.get(n);b.action="@slot:"+n;bs.add(b);}return bs;}return !h.body.isEmpty()&&!hidden?h.ui.buttonTemplates.get(host() instanceof PcHost?(h.messageMode==0?1:4):(toolbar?6:1)):Collections.emptyList();}
    /** 40B39A, 4125F0, 41FFC0: the wheel pages long lists and steps through the log in text waits. */
    private void scrollPc2(int units)throws Exception{
        PcHost h=(PcHost)host();
        if(inputBlocked())return;
        if(hidden){hidden=false;return;}
        if(h.gridWaiting&&h.list.active()){int page=h.variables.read(3,30)!=0||h.variables.read(3,31)!=0?h.grid.size():h.list.columns;h.write(99,246,h.list.clamp(h.read(99,246)+Integer.signum(units)*page,h.grid.size()));return;}
        if(h.history.active){h.scrollHistory(units);return;}
        boolean choice=h.choiceWaiting();
        if(choice&&units>0)return; // 420020: going down only means something inside the log.
        if(units<0&&(choice||h.textWaiting||h.presentationWaiting)&&!h.hasEarlierLine(choice))return;
        if(choice||h.textWaiting||h.presentationWaiting){if(!choice)h.play(5,"",false);action(units<0?":up":":down");}
    }
    public void scroll(int units)throws Exception{if(followsPc2Exe()){scrollPc2(units);return;}if(host().history.active&&!(host() instanceof PcHost)){host().history.scroll(units);host().historyButtons();return;}if(host() instanceof PcHost){PcHost h=(PcHost)host();if(h.gridWaiting&&h.list.active())h.write(99,246,h.list.clamp(h.read(99,246)+units*h.list.columns,h.grid.size()));}}
    public void click(int x,int y,boolean right)throws Exception {
        Dc2Host h=host();
        if(inputBlocked())return;
        if(right&&followsPc2Exe()&&((PcHost)h).skipButton&&!h.ui.waiting){((PcHost)h).skipButton=false;return;} // 422A81: right click only stops the skip.
        if(h instanceof PcHost&&((PcHost)h).nameInput.active){PcHost p=(PcHost)h;if(right){p.nameAction("@name:cancel");return;}for(Control b:controls())if(b.contains(x,y)){p.nameAction(b.action);return;}return;}
        if(hidden){hidden=false;return;}
        boolean exe=followsPc2Exe();
        if(exe&&right&&((PcHost)h).choiceWaiting()){action(":menu");return;} // 4232D6
        if(exe&&!h.ui.waiting&&!h.textWaiting&&!h.presentationWaiting&&!((PcHost)h).pcCredits.active&&!h.credits.active()){
            // 422BF4, 422BBF: fades ignore input; a timed wait takes its actions, or a click if bit 0 allows it.
            if(timedInput()){if(!h.ui.actions[right?0:1].isEmpty())session.vm.activateMenu(h.waitTicket(),right?-1:-2);else if(((PcHost)h).waitDismissable())session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
            return;
        }
        if(!right&&drag(x,y,true)){endDrag();return;}
        if(!right&&!exe&&h instanceof PcHost&&h.gridWaiting){PcHost p=(PcHost)h;if(p.list.active()){int offset=p.list.scrollAt(x,y,p.gridBounds,p.read(99,246),p.grid.size());if(offset>=0){p.write(99,246,offset);return;}}}
        java.util.List<Dc2Ui.Button> bs=buttons();
        // 411ED0: the first enabled button under the pointer takes the click.
        if(!right)for(int k=0;k<bs.size();k++){int n=exe?k:bs.size()-1-k;Dc2Ui.Button b=bs.get(n);if(exe&&((b.flags&2)!=0||hiddenButton(b)))continue;if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom&&!b.action.isEmpty()){
            if(h instanceof PcHost&&h.history.active&&(b.action.equals(":close")||b.action.equals(":change")||exe&&b.action.startsWith(":")&&!b.action.equals(":up")&&!b.action.equals(":down"))){action(b.action);return;}
            for(Dc2Ui.Quad q:b.sprites)if(q.state==5){session.vm.variables.write(q.sy,q.width,sliderValue(q,x));}
            if(b.action.startsWith("@slot:"))session.vm.activateGrid(h.waitTicket(),Integer.parseInt(b.action.substring(6)));else if(h.ui.waiting&&n<h.ui.buttons.size())session.vm.activateMenu(h.waitTicket(),n);else{h.toolbarSound(b);action(b.action);}return;}}
        // 4232BD: a press that finished the typewriter does not also advance; 41D310: a click on the log replays its voice.
        if(exe&&!right&&swallowRelease){swallowRelease=false;return;}
        if(exe&&!right&&h.history.active){((PcHost)h).replayHistoryVoice();return;}
        if(exe&&!right&&((PcHost)h).choiceWaiting()){h.play(5,"",false);return;} // 420360: a click beside the options only stops the voice.
        if(h instanceof PcHost&&!h.confirming&&(timedInput()||h.ui.waiting)&&!h.ui.actions[right?0:1].isEmpty()){session.vm.activateMenu(h.waitTicket(),right?-1:-2);return;}
        if(right&&h.ui.waiting)session.vm.activateMenu(h.waitTicket(),-1);
        else if(!h.ui.waiting){if(right)action(":menu");else session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);}
    }
    /** 41137B snaps with a 4 px offset. */
    private int sliderValue(Dc2Ui.Quad q,int x){
        if(followsPc2Exe()){int span=Math.max(1,q.y-q.x);return Math.max(0,Math.min(q.sx-1,((x-q.x-4)*Math.max(0,q.sx-1)+span/2)/span));}
        return (int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));
    }
    /** 4144E0: only inside the bar, not snapped to columns. */
    private int scrollPc2Offset(PcHost h,int y){int[] b=h.gridBounds;return h.list.clamp((y-b[1])*(h.list.size()-h.grid.size()+1)/Math.max(1,b[3]-b[1]),h.grid.size());}
    private void scrollbarPc2(PcHost h,int y)throws IOException{int next=scrollPc2Offset(h,y);if(next!=h.read(99,246)){h.write(99,246,next);if(h.gridHoverSound!=255)h.play(1,resources.named("sound",h.gridHoverSound),false);}}
    public boolean drag(int x,int y,boolean begin)throws Exception{
        if(begin){dragged=null;dragKind=0;Dc2Host h=host();if(!h.ui.waiting)return false;if(h.history.active&&!(h instanceof PcHost)&&x>=944&&x<984&&y>=112&&y<444)dragKind=1;else if(h instanceof PcHost&&h.gridWaiting&&((PcHost)h).list.active()){int[] b=((PcHost)h).gridBounds;int margin=followsPc2Exe()?0:4;if(x>=b[0]-margin&&x<b[2]+margin&&y>=b[1]&&y<b[3])dragKind=2;}if(dragKind==0)for(Dc2Ui.Button b:h.ui.buttons)if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)for(Dc2Ui.Quad q:b.sprites)if(q.state==5)dragged=b;}
        if(dragKind==1){Dc2History h=host().history;int max=Math.max(0,h.entries.size()-4);h.cursor=Math.max(0,Math.min(max,(int)Math.round((y-112)*max/300.0)));host().historyButtons();return true;}
        if(dragKind==2&&followsPc2Exe()){PcHost h=(PcHost)host();int[] b=h.gridBounds;scrollbarPc2(h,Math.max(b[1],Math.min(b[3]-1,y)));return true;}
        if(dragKind==2){PcHost h=(PcHost)host();int[] b=h.gridBounds;int cy=Math.max(b[1],Math.min(b[3]-1,y));h.write(99,246,y>=b[3]?h.list.clamp(Integer.MAX_VALUE,h.grid.size()):h.list.scrollAt((b[0]+b[2])/2,cy,b,h.read(99,246),h.grid.size()));return true;}
        if(dragged==null)return false;Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:dragged.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return false;
        int value=sliderValue(cfg,x);if(stops!=null){int[] ends={stops.x,stops.y,stops.sx,stops.sy};value=0;while(value<3&&x>=ends[value])value++;}
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
        if(key.equals(":rapid")){toolbar=false;if(followsPc2Exe()){if((host().textWaiting||host().presentationWaiting)&&!host().ui.waiting)((PcHost)host()).skipButton=true;return;}rapid=!rapid;return;} // 423184: on, in text only; a right click stops it.
        if(key.equals(":qsave")||key.equals(":qs")){writeSlot(followsPc2Exe()?0:1);if(followsPc2Exe())flash=200;toolbar=false;return;} // 423247 writes slot 0, then 41D980 flashes white.
        if(key.equals(":qload")||key.equals(":ql")){if(Files.exists(slot(1)))readSlot(1);toolbar=false;return;}
        String script=key.equals(":backlog")?"LOG":key.equals(":torisetu")?"MANUAL":key.equals(":option")&&host() instanceof PcHost?"OPTION":key.equals(":menu")||key.equals(":option")?"ADVMENU":null;
        if(script!=null){requestSystem(script);return;}
        throw new IOException("Unimplemented original system action "+a);
    }
    /** Input may arrive after ADVANCE releases a wait but before the next tick runs MES. */
    private void requestSystem(String action)throws IOException {
        if(session.inSystem())return;
        auto=false;if(!followsPc2Exe())rapid=false;
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
        boolean nativeHistory=pendingSystemAction.equals("@history"),fromChoice=nativeHistory&&followsPc2Exe()&&((PcHost)host()).choiceWaiting();
        if(!host().textWaiting&&!fromChoice&&(nativeHistory||!host().ui.waiting))return false;
        String action=pendingSystemAction;pendingSystemAction=null;
        session.captureHistory();beginSystemAudio();
        if(nativeHistory){session.openNativeSystem();((PcHost)host()).openHistory(fromChoice);}
        else session.openSystem(action);
        toolbar=auto=false;if(!followsPc2Exe())rapid=false;return true;
    }
    /** The Mac app follows DC2PC.exe here. Other builds of the game have not been read, so they keep the shared path. */
    private boolean followsPc2Exe(){return PcHost.followsExe&&host() instanceof PcHost&&((PcHost)host()).second;}
    @Override protected boolean keepsPointerAcrossRebuild(){return followsPc2Exe();}
    /** 41D980, 422A53: nothing reaches the game during the white flash, or while a scene sets register 25. */
    private long flash;
    private boolean inputBlocked()throws IOException{return followsPc2Exe()&&(flash>0||host().read(99,25)!=0);}
    private int pointerX,pointerY,pointerTier=-1;private String pointerButton;private long hoverTicket=-1;private boolean swallowRelease,restoredFlag;
    /** 40AF6F, 42207B: pressing during the typewriter completes the line at once. */
    @Override public void pointerDown(int x,int y)throws Exception{
        if(inputBlocked())return;
        if(followsPc2Exe()){Dc2Host h=host();swallowRelease=h.presentationWaiting;if(swallowRelease)session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);}
        super.pointerDown(x,y);
    }
    /** 40AD71: space hides the text window; 422FB3: any key brings it back. Enter presses what is under the pointer. */
    @Override public void keyPress(boolean space)throws Exception{
        if(!followsPc2Exe()){super.keyPress(space);return;}
        Dc2Host h=host();PcHost p=(PcHost)h;
        if(inputBlocked())return;
        if(hidden){hidden=false;return;}
        if(space){if(p.choiceWaiting()||h.history.active||h.textWaiting||h.presentationWaiting)hidden=true;return;}
        if(h.history.active){p.replayHistoryVoice();return;}
        if(h.ui.waiting){
            java.util.List<Dc2Ui.Button> all=buttons();
            if(hovered>=0&&hovered<all.size()){Dc2Ui.Button b=all.get(hovered);boolean under=pointerX>=b.left&&pointerX<b.right&&pointerY>=b.top&&pointerY<b.bottom;click(under?pointerX:(b.left+b.right)/2,under?pointerY:(b.top+b.bottom)/2,false);}
            else click(pointerX,pointerY,false);
            return;
        }
        if(h.presentationWaiting)session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);else session.vm.signalHost(h.waitTicket(),MesVm.HostSignal.ADVANCE);
    }
    /** 40AC84, 413E40: up and down walk a choice, all four arrows walk a menu, skipping what cannot be selected. Page keys are the wheel. */
    @Override public void navKey(int code)throws Exception{
        if(!followsPc2Exe()||inputBlocked()||hidden)return;
        PcHost h=(PcHost)host();
        if(code==KeyEvent.VK_PAGE_UP||code==KeyEvent.VK_PAGE_DOWN){scrollPc2(code==KeyEvent.VK_PAGE_UP?-1:1);return;}
        boolean back=code==KeyEvent.VK_UP||code==KeyEvent.VK_LEFT,step=back||code==KeyEvent.VK_DOWN||code==KeyEvent.VK_RIGHT;
        if(!step||!h.ui.waiting||h.history.active||h.map.active||h.nameInput.active)return;
        if(h.choiceWaiting()&&code!=KeyEvent.VK_UP&&code!=KeyEvent.VK_DOWN)return;
        navStep(h,back?-1:1);
    }
    private void navStep(PcHost h,int direction)throws IOException{
        java.util.List<Dc2Ui.Button> all=buttons();int grids=h.gridWaiting?h.grid.size():0,plain=all.size()-grids,count=h.choiceWaiting()?h.ui.buttons.size():all.size();
        if(count==0)return;
        if(grids>0&&h.list.active()&&hovered>=plain){ // 4125F0: a long list turns over at its edge.
            int row=hovered-plain,offset=h.read(99,246),next=h.list.clamp(offset+direction,grids);
            if((direction>0?row==grids-1:row==0)&&next!=offset){h.write(99,246,next);if(h.gridHoverSound!=255)h.play(1,resources.named("sound",h.gridHoverSound),false);return;}
        }
        int at=hovered>=0&&hovered<count?hovered:direction>0?-1:0;
        for(int tried=0;tried<count;tried++){
            at=Math.floorMod(at+direction,count);Dc2Ui.Button b=all.get(at);boolean usable;
            if(at>=plain){int row=at-plain;if(h.list.active()){ScriptList.Item item=h.list.at(h.read(99,246),row,grids);usable=item!=null&&!item.action.isEmpty();}else{int slot=h.gridSlot(row);usable=h.saveGrid?slot>=2:h.availableSaves.contains(slot);}}
            else usable=(b.flags&3)==0&&!hiddenButton(b)&&!b.action.isEmpty()&&!b.action.equals("*");
            if(!usable)continue;
            if(at==hovered)return;
            hovered=at;
            if(at>=plain){h.write(99,253,at-plain);if(h.gridHoverSound!=255)h.play(1,resources.named("sound",h.gridHoverSound),false);h.ui.hovered=-1;}
            else{if(b.hoverSound!=255)h.play(1,resources.named("sound",b.hoverSound),false);h.ui.hovered=at<h.ui.buttons.size()?at:-1;if(grids>0)h.write(99,253,-1);}
            return;
        }
    }
    @Override protected String saveScript(byte[] state){try{return MesVm.scriptOf(state);}catch(IOException e){return "";}}
    @Override public boolean takeRestored(){boolean was=restoredFlag;restoredFlag=false;return was;}
    /** 41B740: bit 15 inverts the variable test that hides a button. */
    private boolean hiddenButton(Dc2Ui.Button b)throws IOException{
        if(b.binding==0)return false;
        int value=session.vm.variables.read((b.binding>>8)&127,b.binding&255);
        return (b.binding&0x8000)!=0?value!=0:value==0;
    }
    /**
     * 41D530, 411B90 and 412B60. Menu and text waits track the pointer; timed waits do not. The first enabled button
     * under the pointer wins, "*" continues the button before it, and a sound plays only on reaching another button.
     * One departure: the exe forgets the hovered button whenever a script rebuilds the menu, and its music page rebuilds
     * every frame while nothing plays. The same button under the pointer stays silent here.
     */
    private void hoverPc2(int x,int y)throws IOException{
        PcHost h=(PcHost)host();pointerX=x;pointerY=y;
        if(h.map.active&&h.map.selected<0){int was=h.map.hovered;h.map.hovered=-1;for(int n=0;n<h.map.regions().length;n++){int[] r=h.map.regions()[n];if(x>=r[0]&&x<r[2]&&y>=r[1]&&y<r[3])h.map.hovered=n;}if(h.map.hovered>=0&&h.map.hovered!=was)h.play(1,resources.named("sound",224),false);}
        if(timedInput()||(!h.ui.waiting&&!h.textWaiting))return;
        java.util.List<Dc2Ui.Button> all=buttons();int grids=h.gridWaiting?h.grid.size():0,plain=all.size()-grids,row=-1,hit=-1;
        for(int n=0;n<grids&&row<0;n++){Dc2Ui.Button b=h.grid.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)row=n;}
        if(row>=0&&h.list.active()){ScriptList.Item item=h.list.at(h.read(99,246),row,grids);if(item==null||item.action.isEmpty())row=-1;}
        else if(row>=0){int slot=h.gridSlot(row);if(h.saveGrid?slot<2:!h.availableSaves.contains(slot))row=-1;} // 412B60: rows without an action do not hover.
        if(grids>0){
            int current=h.read(99,253);if(current==255)current=-1;
            if(row!=current){if(row>=0&&h.gridHoverSound!=255)h.play(1,resources.named("sound",h.gridHoverSound),false);h.write(99,253,row);}
        }
        for(int n=0;n<plain&&hit<0;n++){Dc2Ui.Button b=all.get(n);if((b.flags&2)==0&&!hiddenButton(b)&&x>b.left&&x<b.right&&y>b.top&&y<b.bottom)hit=n;}
        if(hit>0&&all.get(hit).action.equals("*"))hit--;
        String button=null;
        if(hit>=0){Dc2Ui.Button b=all.get(hit);button=b.action+'@'+b.left+','+b.top+','+b.right+','+b.bottom;if(!button.equals(pointerButton)&&b.flags!=12&&b.hoverSound!=255)h.play(1,resources.named("sound",b.hoverSound),false);}
        // 41176B: a four-step slider sounds on each step instead of on entry.
        int tier=-1;if(hit>=0&&all.get(hit).flags==12)for(Dc2Ui.Quad q:all.get(hit).sprites)if(q.state==7)tier=x<q.x?0:x<q.y?1:x<q.sx?2:3;
        if(tier>=0&&tier!=pointerTier&&all.get(hit).hoverSound!=255)h.play(1,resources.named("sound",all.get(hit).hoverSound),false);
        pointerTier=tier;pointerButton=button;hovered=hit>=0?hit:row>=0?plain+row:-1;
        if(h.ui.waiting){int own=hit<h.ui.buttons.size()?hit:-1;h.ui.hovered=own;if(own>=0)h.registers.put(245,own);}
    }
    public void hover(int x,int y)throws IOException{if(followsPc2Exe()){if(flash<=0)hoverPc2(x,y);return;}int old=hovered;hovered=-1;java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);if(x>=b.left&&x<b.right&&y>=b.top&&y<b.bottom)hovered=n;}if(host().ui.waiting)host().hoverMenu(hovered<host().ui.buttons.size()?hovered:-1);else if(old!=hovered&&hovered>=0&&bs.get(hovered).hoverSound!=255)host().play(1,resources.named("sound",bs.get(hovered).hoverSound),false);if(host() instanceof PcHost){Pc2Map m=((PcHost)host()).map;if(m.active&&m.selected<0){m.hovered=-1;for(int n=0;n<m.regions().length;n++){int[] r=m.regions()[n];if(x>=r[0]&&x<r[2]&&y>=r[1]&&y<r[3])m.hovered=n;}}}}
    public java.util.List<Control> controls(){java.util.List<Control> out=new ArrayList<>();for(Dc2Ui.Button b:buttons())if(!b.action.isEmpty())out.add(new Control(b.action,b.left,b.top,b.right,b.bottom));if(host() instanceof PcHost&&((PcHost)host()).nameInput.active){PcNameInput name=((PcHost)host()).nameInput;if(name.confirming)return out;for(int n=0;n<8;n++){int x=336+(n/4)*192+(n%4)*32;out.add(new Control("@name:field:"+n,x,40,x+32,72));}java.util.List<String> rows=name.visibleRows();for(int r=0;r<rows.size();r++)for(int c=0;c<Math.min(17,rows.get(r).length());c++){int x=280+c*24,y=96+r*24+(r>=4?8:0);if(rows.get(r).charAt(c)!='　')out.add(new Control("@name:char:"+(r*17+c),x,y,x+24,y+24));}}return out;}
    private int textSpeed()throws IOException{return host().read(99,host() instanceof PcHost?13:auto?27:26);}
    private long textDuration()throws IOException{if(followsPc2Exe())return TextPlayback.duration(resources.profile.id,textSpeed(),PcText.layout(body(),0,0,40).ticks);return TextPlayback.duration(resources.profile.id,textSpeed(),body().codePointCount(0,body().length()));}
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
    public float volume(int ch)throws IOException{int enabled=ch==0?14:ch==5?5:15,level=ch==0?7:ch==5?9:8;if(host().read(99,enabled)==0)return 0;float gain=ch==0?host().musicFadeGain():ch==5?host().voiceGain():1;int l=host().read(99,level);if(followsPc2Exe())return l<=0?0:gain*(float)Math.pow(10,((2*l-60)*.83-(ch==0?3:0))/20); /* 4273A0, 429130 */return gain*Math.max(0,Math.min(1,l/30f));}
    public byte[] snapshot()throws IOException{return session.storySnapshot();}
    public void restore(byte[] bytes)throws IOException{cancelPointer();session.restore(bytes);host().ui.pressed=-1;savedAutoGeneration=host().autosaveGeneration;textTicket=-1;hidden=toolbar=auto=false;if(!followsPc2Exe())rapid=false;pendingSystemAction=null;scanSaves();restoredFlag=true;if(followsPc2Exe()){Dc2Host h=host();((PcHost)h).skipButton=false;h.play(5,"",false);if(!h.audioLoop[1])h.play(1,"",false);}} // 4203B0, 420566.
    private Path slot(int n)throws IOException{if(n<0||n>255)throw new IOException("Invalid save slot "+n);return saveDirectory.resolve("slot-"+n+".sav");}
    private void writeSlot(int n)throws IOException{save(slot(n));scanSaves();persist();}
    private void readSlot(int n)throws IOException{restore(slot(n));}
    private void scanSaves()throws IOException{host().availableSaves.clear();host().saveLabels.clear();thumbnails.clear();for(int n=0;n<256;n++)if(Files.exists(slot(n))){host().availableSaves.add(n);long modified=Files.getLastModifiedTime(slot(n)).toMillis();host().saveLabels.put(n,followsPc2Exe()?saveDate(n,modified):new java.text.SimpleDateFormat("MM/dd HH:mm").format(new Date(modified)));scanThumbnail(n,slot(n));}}
    /** 0x4528C0 */
    private String saveDate(int n,long modified)throws IOException{DesktopSave meta=metadata(slot(n));Calendar c=Calendar.getInstance();c.setTimeInMillis(meta!=null&&meta.created>0?meta.created:modified);return String.format(Locale.ROOT,"%4d年%2d月%2d日 %2d時%2d分",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));}
    public String saveCaption(){return followsPc2Exe()||host().checkpointLabel.isEmpty()?PcText.plain(body().isEmpty()?lastLine:body()):codec.apply(host().checkpointLabel);}
    public BufferedImage preview()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{scene(g,host().systemScene!=null?host().systemScene:host().scene.shown);}finally{g.dispose();}return image;}
    public void persist()throws IOException{if(audit)return;ByteArrayOutputStream b=new ByteArrayOutputStream();host().savePersistent(new DataOutputStream(b));atomic(saveDirectory.resolve("progress.dat"),b.toByteArray());}
    private Picture picture(String name)throws IOException {
        if(name==null||name.isEmpty())return null;if(name.equals("@ui-capture"))return new Picture(captures.resolve(host().uiCapture));
        Picture p=images.get(name);if(p==null){
            try{p=new Picture(resources.image(name));}catch(java.io.FileNotFoundException|java.nio.file.NoSuchFileException missing){
                // A repacked install can lack an optional sheet; the native loader also leaves the
                // slot empty. Keep the name so audits can list it instead of failing the frame.
                missingImages.add(name);return null;
            }
            images.put(name,p);loadedImages.add(name);
        }
        return p;
    }
    private int color(int style){return (captureState==null?host().ui.palette:captureState.palette)[style&63];}
    private BufferedImage drawCapture(int kind,DataInputStream input)throws IOException {
        if(kind!=UiCapture.LEGACY)throw new IOException("Wrong legacy UI capture profile");SceneCaptures.Legacy saved=captureState;captureState=new SceneCaptures.Legacy(input);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);scene(g,captureState.frame);}finally{g.dispose();captureState=saved;}return image;
    }
    private void at(Graphics2D g,String name,int x,int y)throws IOException {Picture p=picture(name);if(p!=null)g.drawImage(p.image,x+p.raw.x,y+p.raw.y,null);}
    private static String pcFontFamily;
    /** 40BAD4 asks for ＭＳ ゴシック at twice the height; Macs have no MS Gothic, and a Chinese font would misplace 、。 */
    private static Font pcFont(int size){
        if(pcFontFamily==null){java.util.Set<String> have=new java.util.HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));pcFontFamily="Dialog";for(String name:new String[]{"Hiragino Sans","Hiragino Kaku Gothic ProN"}){if(have.contains(name)){pcFontFamily=name;break;}}for(String name:new String[]{"MS Gothic","ＭＳ ゴシック"})if(have.contains(name))pcFontFamily=name;}
        return new Font(pcFontFamily,Font.PLAIN,size);
    }
    private static int pcBaseline(int y,int size){return y+Math.round(size*.859f);}
    private void pcGlyph(Graphics2D g,String c,int x,int y,int size,Color fg,Color shade){g.setFont(pcFont(size));int base=pcBaseline(y,size);if(shade!=null){g.setColor(shade);g.drawString(c,x+1,base+1);}g.setColor(fg);g.drawString(c,x,base);}
    private Color pcShade(int style){return (style&0x80)==0?null:new Color(color((style&63)==0?9:0),true);}
    /** 41C5E0: base text first, ruby over it. 41C8A6: a ruby dot is a full size ・ pulled back to mark the character. */
    private void drawPcBody(Graphics2D g,String raw,int x,int y,int lineHeight,int style)throws IOException{
        PcText.Layout l=PcText.layout(raw,x,y,lineHeight);Dc2Host h=host();
        int shown=h.textRendered||!h.presentationWaiting||(textSpeed()&1)!=0?l.ticks:(int)Math.min(l.ticks,playbackMillis()/5);
        Color fg=new Color(color(style),true),shade=pcShade(style);
        for(int pass=0;pass<2;pass++)for(PcText.Glyph c:l.glyphs){
            if(c.tick>=shown||(c.size==PcText.RUBY_SIZE)!=(pass==1))continue;
            if(pass==1&&c.text.equals("・"))pcGlyph(g,c.text,c.x-8,c.y-5,PcText.BODY_SIZE,fg,shade);else pcGlyph(g,c.text,c.x,c.y,c.size,fg,shade);
        }
    }
    /** 41F7D0: 24 px cells centred on the name plate, whose template carries the centre in its zero width entry. */
    private void drawPcName(Graphics2D g,String name,Dc2Ui.Quad plate,java.util.List<Dc2Ui.Quad> template)throws IOException{
        int cells=0;for(Dc2Ui.Quad q:template)if(q.type==2&&q.width==0)cells=q.y;
        String line=PcText.plain(name).replace("\n","");int bytes=line.getBytes(Binary.SJIS).length,x=plate.x+12*cells-6*bytes;
        Color fg=new Color(color(plate.height),true),shade=pcShade(plate.height);
        for(int pos=0;pos<line.length();){int cp=line.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=c.length();pcGlyph(g,c,x,plate.y,24,fg,shade);x+=c.getBytes(Binary.SJIS).length*12;}
    }
    /** 41D3F0, 41CC80: labels use the same cells, half width for single byte characters, and 3:30, 3:31 choose the size. */
    private void drawPcLabel(Graphics2D g,String s,int x,int y,int style)throws IOException{
        int size=host().variables.read(3,30)!=0?(s.getBytes(Binary.SJIS).length>20?14:16):host().variables.read(3,31)!=0?16:PcText.BODY_SIZE,at=x,line=y;
        Color fg=new Color(color(style),true),shade=pcShade(style);
        String plain=PcText.plain(s);for(int pos=0;pos<plain.length();){int cp=plain.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=c.length();if(cp=='\n'){at=x;line+=size+4;continue;}pcGlyph(g,c,at,line,size,fg,shade);at+=c.getBytes(Binary.SJIS).length==1?size/2:size;}
    }
    private void text(Graphics2D g,String s,int x,int y,int lineHeight,int style,int width)throws IOException{
        if(followsPc2Exe()){drawPcLabel(g,s,x,y,style);return;}
        g.setFont(new Font("Microsoft YaHei",Font.PLAIN,22));Color foreground=new Color(color(style),true);
        // PC1 417A5E / PC2 421CA6 / DM 4024E8 draw style 0x80 at (+1,+1), using palette 0; PC2 uses 9 for black text.
        boolean shadow=(style&0x80)!=0;Color shade=shadow?new Color(color(host() instanceof PcHost&&((PcHost)host()).second&&(style&63)==0?9:0),true):foreground;
        g.setColor(foreground);int at=x,base=y+22;for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=Character.charCount(cp);int w=g.getFontMetrics().stringWidth(c);if(cp==10||at+w>x+width){at=x;base+=Math.max(22,lineHeight);if(cp==10)continue;}if(shadow){g.setColor(shade);g.drawString(c,at+1,base+1);g.setColor(foreground);}g.drawString(c,at,base);at+=w;}
    }
    private void quad(Graphics2D g,Dc2Ui.Quad q,String[] slots,boolean sprite)throws IOException {
        if(q.binding!=0&&session.vm.variables.read(q.binding>>8,q.binding&255)==0)return;
        if(q.type==2){if(!q.text.isEmpty())text(g,codec.apply(q.text),q.x,q.y,sprite?23:Math.max(22,q.width),sprite?q.flags:q.height,width-q.x);return;}
        if(q.width<=0||q.height<=0)return;
        // A "restore" blit copies the scene background back over a rectangle where a hover bubble
        // or label used to sit: D.C.II's alarm clock stores three of them as button sprites with
        // source == destination (meza_nin_20 40EB/417D/41FB, slot 0 = the background texture). The
        // native widget paints into the background layer, but this renderer rebuilds the
        // background every frame, so the copy is a no-op — drawing it over the panel quad that was
        // laid down before the buttons would punch a background hole through the panel's left edge.
        if(q.type==0&&q.slot()==1&&q.sx==q.x&&q.sy==q.y)return;
        Composite old=g.getComposite();if(q.opaque()){float alpha=80/255f;if(followsPc2Exe())alpha=host().read(99,10)!=0?Math.max(0,Math.min(1,host().read(99,11)/16f)):1;g.setComposite(AlphaComposite.SrcOver.derive(alpha));} /* 40D030 */
        if(q.type==1){g.setColor(new Color(color(q.flags),true));g.fillRect(q.x,q.y,q.width,q.height);}
        else if(q.type==0){Picture p=picture(q.text.isEmpty()?slots[q.slot()]:q.text);if(p!=null){int sx=q.sx,sy=q.sy;if(q.animation!=0&&q.frames>0&&q.period>0)sx+=(int)(host().scene.effectClock/(34L*q.period)%q.frames)*q.step;int l=Math.max(0,sx),t=Math.max(0,sy),r=Math.min(p.image.getWidth(),sx+q.width),b=Math.min(p.image.getHeight(),sy+q.height);if(l<r&&t<b)g.drawImage(p.image,q.x+l-sx,q.y+t-sy,q.x+r-sx,q.y+b-sy,l,t,r,b,null);}}
        else throw new IOException("Desktop draw type pending: "+q.type);
        g.setComposite(old);
    }
    private void scene(Graphics2D g,Dc2Scene.Frame f)throws IOException {sceneUnder(g,f);drawEffects(g);sceneOver(g,f);}
    private void sceneOver(Graphics2D g,Dc2Scene.Frame f)throws IOException {for(Dc2Ui.Quad q:f.quads)quad(g,q,f.slots,false);}
    private void sceneUnder(Graphics2D g,Dc2Scene.Frame f)throws IOException {
        g.setColor(Color.BLACK);g.fillRect(0,0,width,height);
        // Scroll arguments are already drawing offsets: an 800x1200 background scrolls to y=-600 to show its lower half.
        at(g,f.slots[1],DesktopMac.ACTIVE?f.x:-f.x,DesktopMac.ACTIVE?f.y:-f.y);
        boolean pc2=resources.profileId.equals("dc2pc-5769");
        // PC2 41A950 visits actor slots 0..3, so a later close-up covers earlier actors.
        for(int order=0;order<4;order++){int n=pc2?order:3-order;int[] a=f.actors[n];if(captureState==null&&f==host().scene.shown&&followsPc2Exe())a=host().scene.presented(n,a);for(int slot:new int[]{n+2,n+6}){String name=slot==n+6?resources.characterOverlay(f.slots[n+2],f.slots[slot]):f.slots[slot];Picture p=picture(name);if(p==null)continue;
            if(pc2){PcActorLayout r=PcActorLayout.dc2pc(a,p.raw.width,p.raw.height);g.drawImage(p.image,r.x,r.y,r.x+r.width,r.y+r.height,0,0,p.raw.width,p.raw.height,null);}
            else{AffineTransform tr=g.getTransform();g.translate(a[0]+a[3],a[1]+a[4]);g.scale(a[2]/100.0,a[2]/100.0);g.drawImage(p.image,p.raw.x-a[3],p.raw.y-a[4],null);g.setTransform(tr);}
        }}
    }
    private void drawEffects(Graphics2D g)throws IOException{
        Dc2Scene s=host().scene;SceneCaptures.Legacy frozen=captureState;
        if(frozen==null?!host().effectsEnabled():!frozen.effects)return;
        long t=(frozen==null?s.effectClock:frozen.clock)/34;int particleKind=frozen==null?s.particleKind:frozen.particleKind,particleStrength=frozen==null?s.particleStrength:frozen.particleStrength,overlayKind=frozen==null?s.overlayKind:frozen.overlayKind;String[] slots=frozen==null?s.slots:frozen.frame.slots;
        if(overlayKind==7){Picture p=picture(slots[15]);if(p!=null){g.setComposite(AlphaComposite.SrcOver.derive(96/255f));int x=-(int)(t%p.image.getWidth());for(;x<width;x+=p.image.getWidth())g.drawImage(p.image,x,0,null);g.setComposite(AlphaComposite.SrcOver);}}
        if(particleKind==0)return;Picture p=picture(slots[particleKind==2?17:16]);if(p==null)return;int level=particleStrength&15,count=level>=5?50:new int[]{0,5,10,15,30}[level];
        for(int n=0;n<count;n++){int w=particleKind==2?8:32,h=particleKind==2?80:24,sy=particleKind==2?0:n%6*28;if(sy+h>p.image.getHeight())continue;int sx=particleKind==2?0:(int)(t/(4+n%8)%5)*w;int x=(int)((n*193+Math.sin((t*(4+n%8)+n*71)*Math.PI/180)*(24+n%24)+width)%width),y=(int)((n*137+t*(particleKind==2?32:2+n%4))%(height+64))-32;if(particleKind==9)y=height-y;g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    }
    private BufferedImage frozenScreen,fadeFrom,lastFrame,layerUnder,layerOver;private long frozenRevision;private int fadeFromSerial,seenSerial=-1;private boolean frozenLayers;
    private boolean particlesOn()throws IOException{Dc2Scene s=host().scene;return host().effectsEnabled()&&!s.effectsFrozen&&(s.particleKind!=0||s.overlayKind==7);}
    public BufferedImage render()throws IOException {
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();try{
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);Dc2Host h=host();boolean exe=followsPc2Exe();
            if(exe&&flash>0){g.setColor(new Color(color(7),true));g.fillRect(0,0,width,height);return result;} // 41D980
            // 4210C9: every effect starts from what was on the screen, text window included.
            if(exe&&h.scene.transitionSerial!=seenSerial){seenSerial=h.scene.transitionSerial;if(h.scene.transitionRemaining>0){fadeFrom=lastFrame;fadeFromSerial=seenSerial;}}
            // 424523: the exe's screen is a surface that keeps its pixels. A click and a timed wait do not repaint it,
            // so the menu stays up until the script actually draws, and a fade starts from that picture.
            if(frozenScreen!=null){
                if(frozenRevision==h.scene.revision){
                    if(!h.ui.waiting&&exe){
                        // 422800: snow and petals run on their own timer, whatever the script is doing.
                        if(!frozenLayers||!particlesOn())return lastFrame=frozenScreen;
                        g.drawImage(layerUnder,0,0,null);drawEffects(g);g.drawImage(layerOver,0,0,null);return lastFrame=frozenScreen=result;
                    }
                }
                else{if(!exe){fadeFrom=h.scene.transitionRemaining>0?frozenScreen:null;fadeFromSerial=h.scene.transitionSerial;}frozenScreen=null;frozenLayers=false;}
            }
            if(h instanceof PcHost&&((PcHost)h).map.active){drawMap(g,(PcHost)h);if(exe)lastFrame=result;return result;}
            if(exe&&h.ui.waiting&&h.scene.transitionRemaining==0&&particlesOn()){
                if(layerUnder==null){layerUnder=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);layerOver=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);}
                Graphics2D under=layerUnder.createGraphics();try{sceneUnder(under,h.scene.shown);}finally{under.dispose();}
                Graphics2D over=layerOver.createGraphics();try{
                    over.setComposite(AlphaComposite.Clear);over.fillRect(0,0,width,height);over.setComposite(AlphaComposite.SrcOver);
                    over.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    sceneOver(over,h.scene.shown);drawInterface(over,h);
                }finally{over.dispose();}
                g.drawImage(layerUnder,0,0,null);drawEffects(g);g.drawImage(layerOver,0,0,null);
                frozenScreen=result;frozenRevision=h.scene.revision;frozenLayers=true;return lastFrame=result;
            }
            scene(g,h.scene.shown);drawTransition(g);
            if(!drawInterface(g,h))return result;
            if(h.ui.waiting&&exe){frozenScreen=result;frozenRevision=h.scene.revision;frozenLayers=false;}
            if(exe)lastFrame=result;
        }finally{g.dispose();}return result;
    }
    /** Everything over the scene: credits, the text window, buttons and lists. False if credits took the whole screen. */
    private boolean drawInterface(Graphics2D g,Dc2Host h)throws IOException{
            if(h instanceof PcHost&&((PcHost)h).pcCredits.active){PcCredits c=((PcHost)h).pcCredits;int first=(int)c.scroll()/c.stripHeight();g.setColor(Color.BLACK);g.fillRect(0,0,width,height);for(int n=first;n<=Math.min(c.lastStrip(),first+1);n++)at(g,c.image(n),0,(int)(n*c.stripHeight()-c.scroll()));return false;}if(h.credits.active()){drawCredits(g);return false;}
            if(!h.body.isEmpty()&&!hidden){
                Dc2Ui.Quad body=null,name=null;for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode==0?1:4)){quad(g,q,h.scene.slots,false);if(q.type==2&&q.width>2)body=q;}
                if(!h.speaker.isEmpty())for(Dc2Ui.Quad q:h.ui.quadTemplates.get(h.messageMode==0?2:5)){quad(g,q,h.scene.slots,false);if(q.type==2&&q.width>2)name=q;}
                boolean pc2=followsPc2Exe();
                if(name!=null){if(pc2)drawPcName(g,h.speaker,name,h.ui.quadTemplates.get(2));else text(g,h.speaker,name.x,name.y,name.width,name.height,700);}
                if(body!=null){if(pc2){int style=body.height;if(h.history.active)for(Dc2Ui.Quad q:h.ui.quadTemplates.get(1))if(q.type==2&&q.width==0)style=q.x; /* 41FA3A */drawPcBody(g,h.body,body.x,body.y,body.width,style);}else text(g,visibleBody(),body.x,body.y,body.width,body.height,width-40-body.x);}
                for(int n=18;n<=19;n++)at(g,h.scene.shown.slots[n],h.scene.portraitX,h.scene.portraitY);
            }
            if(h instanceof PcHost&&((PcHost)h).nameInput.active)drawName(g,((PcHost)h).nameInput);
            java.util.List<Dc2Ui.Button> bs=buttons();for(int n=0;n<bs.size();n++){Dc2Ui.Button b=bs.get(n);boolean slider=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==5)slider=true;for(Dc2Ui.Quad q:b.sprites)if(q.state==0||q.state==99||(q.state==1&&n==hovered)||(q.state==2&&!slider&&(b.action.equals(":c")?h.confirmationChecked():n==pressedButton)))quad(g,q,h.scene.slots,true);}
            for(Dc2Ui.Button b:bs)drawSlider(g,b);
            if(h.gridWaiting)drawGrid(g);
            if(h.history.active&&!(h instanceof PcHost))drawHistory(g);
            if(h.musicRoom.active)drawMusicRoom(g);
            return true;
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
        BufferedImage old=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D prev=old.createGraphics();if(fadeFrom!=null&&fadeFromSerial==s.transitionSerial)prev.drawImage(fadeFrom,0,0,null);else scene(prev,s.previous);prev.dispose();
        if(followsPc2Exe()&&s.transitionKind==2){g.drawImage(old,0,0,null);g.setColor(new Color(0f,0f,0f,1-s.transitionRemaining/(float)s.transitionDuration));g.fillRect(0,0,width,height);return;} // 4210C9
        if(followsPc2Exe()&&s.transitionKind>=140&&s.transitionKind<=150){drawShatter(g,old,s);return;}
        if(s.transitionKind>=130&&s.transitionKind<=132){int variant=s.transitionKind-130,ticks=(int)((s.transitionDuration-s.transitionRemaining)/40),gain=0;if(followsPc2Exe())gain=((PcHost)host()).maskGain(ticks);else for(int n=2;n<=ticks;n++)gain+=Math.min(16,n);boolean second=((PcHost)host()).second;
            int[] pixels=old.getRGB(0,0,width,height,null,0,width);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int v=variant==0?0:(second?8:0)-x/4-(variant==2?y/4:0);int alpha=255-255*Math.max(0,Math.min(16,v+gain))/16;int i=y*width+x;pixels[i]=(pixels[i]&0xffffff)|(alpha<<24);}old.setRGB(0,0,width,height,pixels,0,width);
        }else g.setComposite(AlphaComposite.SrcOver.derive(s.transitionRemaining/(float)s.transitionDuration));
        g.drawImage(old,0,0,null);g.setComposite(AlphaComposite.SrcOver);
    }
    private PcShatter shatter;private int shatterSerial=-1,shatterSteps;
    private void drawShatter(Graphics2D g,BufferedImage old,Dc2Scene s){
        if(shatter==null||shatterSerial!=s.transitionSerial){shatter=new PcShatter(s.transitionKind-140,s.transitionSerial);shatterSerial=s.transitionSerial;shatterSteps=0;}
        for(long due=(s.transitionDuration-s.transitionRemaining)/40+1;shatterSteps<due;shatterSteps++)shatter.step();
        g.setColor(Color.BLACK);g.fillRect(0,0,width,height);
        for(int row=0;row<PcShatter.ROWS;row++)for(int col=0;col<PcShatter.COLUMNS;col++){
            int n=row*PcShatter.COLUMNS+col;if(shatter.alpha[n]<=0)continue;int x=shatter.x[n]>>10,y=shatter.y[n]>>10;
            g.setComposite(AlphaComposite.SrcOver.derive(Math.min(16,shatter.alpha[n])/16f));g.drawImage(old,x,y,x+32,y+32,col*32,row*32,col*32+32,row*32+32,null);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }
    private void drawSlider(Graphics2D g,Dc2Ui.Button b)throws IOException{
        Dc2Ui.Quad cfg=null,stops=null;for(Dc2Ui.Quad q:b.sprites){if(q.state==5)cfg=q;if(q.state==7)stops=q;}if(cfg==null)return;
        int index=host().ui.buttons.indexOf(b),atlasState=index==pressedButton?26:index==hovered?25:24;boolean hasAtlas=false;for(Dc2Ui.Quad q:b.sprites)if(q.state==atlasState)hasAtlas=true;if(!hasAtlas)atlasState=24;
        int value=Math.max(0,Math.min(cfg.sx-1,vm().variables.read(cfg.sy,cfg.width))),dx=(cfg.y-cfg.x)*value/Math.max(1,cfg.sx-1),left=cfg.x,right=cfg.x+dx;
        if(stops!=null){int[] ends={cfg.x,stops.x,stops.y,stops.sx,stops.sy};left=ends[Math.min(3,value)];right=ends[Math.min(3,value)+1];}
        boolean pc2Tiers=followsPc2Exe()&&stops!=null;
        if(pc2Tiers){ // 4114D0: states 24, 25 and 26 are whole layers, cut to the current, hovered and other steps.
            int[] ends={cfg.x,stops.x,stops.y,stops.sx,stops.sy};int shown=Math.min(3,value),under=index==hovered?(pointerX<stops.x?0:pointerX<stops.y?1:pointerX<stops.sx?2:3):-1;
            for(int tier=0;tier<4;tier++){int state=tier==shown?26:tier==under?25:24;for(Dc2Ui.Quad q:b.sprites)if(q.state==state){Shape clip=g.getClip();g.clipRect(ends[tier],0,Math.max(0,ends[tier+1]-ends[tier]),height);quad(g,q,host().scene.slots,true);g.setClip(clip);}}
        }
        for(Dc2Ui.Quad q:b.sprites){Shape clip=g.getClip();AffineTransform tr=g.getTransform();if(!pc2Tiers&&q.state==atlasState){Picture p=picture(host().scene.slots[q.slot()]);if(p!=null){int w=q.width/Math.max(1,cfg.sx),sx=q.sx+w*value;g.drawImage(p.image,q.x,q.y,q.x+w,q.y+q.height,sx,q.sy,sx+w,q.sy+q.height,null);}}if(q.state==2||q.state==12){g.clipRect(left,0,Math.max(0,right-left),height);quad(g,q,host().scene.slots,true);}else if(q.state==6){g.translate(dx,0);quad(g,q,host().scene.slots,true);}g.setClip(clip);g.setTransform(tr);}
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
    /** 412390: the slider on long lists. */
    private void drawPcScrollThumb(Graphics2D g,PcHost h)throws IOException{
        int off=h.read(99,246),total=h.list.size(),visible=h.grid.size();if(total<=visible)return;
        if(h.variables.read(3,31)!=0){Picture p=picture(h.scene.slots[10]);if(p!=null){int y=160+306*off/(total-visible);g.drawImage(p.image,744,y,760,y+16,72,216,88,232,null);}}
        else if(h.variables.read(3,30)!=0){Picture p=picture(h.scene.slots[12]);if(p!=null){int y=120+334*off/(total-visible);g.drawImage(p.image,742,y,766,y+24,464,0,488,24,null);}}
    }
    private void drawPcGrid(Graphics2D g,PcHost h)throws IOException{
        if(followsPc2Exe()&&h.list.active()){
            drawPcScrollThumb(g,h);
            if(h.list.textStyle==3){String title=codec.apply(h.musicTitle());Shape c=g.getClip();g.clipRect(96,100,332,18);roomText(g,title,101,103-2,400,16,color(15));roomText(g,title,100,102-2,400,16,color(7));g.setClip(c);} // 408A70
        }
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
            DesktopSave meta=metadata(slot(slot));if(meta!=null&&slot>=2&&meta.icon>=0&&meta.icon<(followsPc2Exe()?17:16)){chip(g,11,meta.icon*56,312,56,56,b.left+8,b.top+8);}
            if(followsPc2Exe()){ // 4078E0
                if(meta==null)continue;String title=codec.apply(h.saveTitle(meta.script)),date=h.saveLabels.get(slot);Shape clip=g.getClip();g.clipRect(b.left,b.top,b.right-b.left,b.bottom-b.top);
                pcRowText(g,title,b.left+72,b.top+10);if(date!=null)pcRowText(g,date,b.left+72,b.top+50);g.setClip(clip);continue;
            }
            String label=h.saveLabels.get(slot);if(label!=null)roomText(g,label,b.left+(h.second?72:104),b.top+(h.second?50:42),180,16,0xffffffff);
            if(meta!=null&&!meta.caption.isEmpty()){Shape clip=g.getClip();g.clipRect(b.left+64,b.top,224,42);roomText(g,meta.caption,b.left+(h.second?72:64),b.top+(h.second?10:6),216,18,0xffffffff);g.setClip(clip);}
        }
    }
    private void pcRowText(Graphics2D g,String s,int x,int y)throws IOException{
        Color fg=new Color(color(9),true),shade=new Color(color(0),true);int at=x;
        for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=c.length();pcGlyph(g,c,at+1,y+1,16,shade,null);pcGlyph(g,c,at,y,16,fg,null);at+=c.getBytes(Binary.SJIS).length==1?8:16;}
    }
    /** 407DD6..40814E uses palette 7 for an eight-neighbour outline and palette 14 for the text. */
    private void pcSceneText(Graphics2D g,String value,int rawLength,int x,int y,int width)throws IOException{int size=host().variables.read(3,30)!=0?(rawLength>20?14:16):host().variables.read(3,31)!=0?16:28;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dy!=0)roomText(g,value,x+dx,y+dy,width,size,color(7));roomText(g,value,x,y,width,size,color(14));}
    private void chip(Graphics2D g,int slot,int sx,int sy,int w,int h,int x,int y)throws IOException{Picture p=picture(host().scene.slots[slot]);if(p!=null)g.drawImage(p.image,x,y,x+w,y+h,sx,sy,sx+w,sy+h,null);}
    private void roomText(Graphics2D g,String value,int x,int y,int w,int size,int color){g.setFont(followsPc2Exe()?pcFont(size):new Font("Microsoft YaHei",Font.PLAIN,size));g.setColor(new Color(color,true));int at=x,base=y+size;for(char ch:value.toCharArray()){int cw=g.getFontMetrics().charWidth(ch);if(ch=='\n'||at+cw>x+w){at=x;base+=size+4;if(ch=='\n')continue;}g.drawString(String.valueOf(ch),at,base);at+=cw;}}
    private void drawHistory(Graphics2D g)throws IOException{
        Dc2History h=host().history;for(int row=0;row<4&&row+h.cursor<h.entries.size();row++){int index=row+h.cursor,y=25+row*133;Dc2History.Entry e=h.entries.get(index);chip(g,10,228,0,680,124,200,y-4);Shape clip=g.getClip();g.clipRect(200,y-4,680,124);
            Integer face=nativeData==null?null:nativeData.faces.get(e.speaker);if(face!=null)chip(g,10,face==24?864:168+(face%6)*116,face==24?480:132+(face/6)*116,116,116,240,y);else roomText(g,e.speaker,212,y+12,135,20,0xff9a386d);
            roomText(g,e.body,360,y+5,500,22,index==h.selected?0xff99325b:0xff663b63);if(!e.voice.isEmpty())chip(g,10,84,120,24,24,332,y+6);g.setClip(clip);
        }chip(g,10,988,0,32,332,948,112);chip(g,10,908,0,40,24,944,88);chip(g,10,948,0,40,24,944,444);chip(g,10,908,72,32,32,948,112+(int)(300L*h.cursor/Math.max(1,h.entries.size()-4)));
        if(h.selected>=0&&h.selected<h.entries.size()){byte[] bytes=h.entries.get(h.selected).scene;if(bytes.length>0){Dc2Scene.Frame f=new Dc2Scene.Frame();f.load(new DataInputStream(new ByteArrayInputStream(bytes)));AffineTransform old=g.getTransform();Shape clip=g.getClip();g.clipRect(28,184,160,90);g.translate(28,184);g.scale(160.0/width,90.0/height);scene(g,f);g.setTransform(old);g.setClip(clip);}}
    }
    private void drawMusicRoom(Graphics2D g)throws IOException{Dc2MusicRoom r=host().musicRoom;roomText(g,String.format(Locale.ROOT,"%03d  %s",r.selected+1,r.data.musicTitles.get(r.selected)),42,201,320,19,0xffa73563);for(int row=0;row<10&&row+r.top<r.data.musicTitles.size();row++){int index=row+r.top,y=78+row*41;boolean selected=index==r.selected;chip(g,10,491,selected?159:77,59,37,435,y);roomText(g,String.format(Locale.ROOT,"%03d",index+1),450,y+7,47,18,selected?0xffcc416e:0xff95536b);roomText(g,r.data.musicTitles.get(index),504,y+6,440,20,0xffa73563);}chip(g,10,436,88,10,388,960,88);chip(g,10,392,32,27,32,951,88+350*r.top/63);}
}
