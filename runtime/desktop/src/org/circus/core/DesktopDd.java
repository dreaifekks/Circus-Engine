package org.circus.core;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** DD platform binding. Original START, panel templates, atlases and scene state. */
public final class DesktopDd extends DesktopEngine {
    public Dc3Host host;private MesVm vm;
    private final DesktopCaptures captures;private int[] capturePalette;
    private long textTicket=-1,textClock;private int hovered=-1;
    private int historyHover=-1;private int savedAutoGeneration;private Dc3Ui.Button dragged;private int dragKind,pressedControl=-1;
    private byte[] caller;
    private long savedProgressRevision;
    private final Dc3NativeData nativeData;
    private final DesktopTransitionMask transitionMask=new DesktopTransitionMask();
    private BufferedImage transitionFrame;
    private final Map<String,Picture> pictures=new LinkedHashMap<String,Picture>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Picture> e){return size()>48;}};
    private static final class Picture {final Crx.Image raw;final BufferedImage image,opaque;Picture(BufferedImage b){raw=new Crx.Image(0,0,b.getWidth(),b.getHeight(),32,new byte[0],null);image=opaque=b;}Picture(Crx.Image r){raw=r;image=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,r.width,r.height,r.argb(true),0,r.width);opaque=new BufferedImage(r.width,r.height,BufferedImage.TYPE_INT_ARGB);opaque.setRGB(0,0,r.width,r.height,r.argb(),0,r.width);}}
    public DesktopDd(CircusResources r,TextCodec c,boolean audit)throws IOException{super(r,c,audit,1024,576);nativeData=new Dc3NativeData(r,c);host=new Dc3Host(r,c,true);host.settingsLoader=this::reloadSettings;captures=new DesktopCaptures(width,height,this::drawCapture);host.captureRenderer=captures;vm=new MesVm(r.scripts(),host,"START");Path p=saveDirectory.resolve("progress.dat");if(!audit&&Files.exists(p)){loadProgress(host.state,Files.readAllBytes(p));host.migrateProgressPp();}if(!audit)recoverGallerySaves();savedProgressRevision=host.state.progressRevision;scanSaves();}
    public MesVm vm(){return vm;}
    private boolean exe(){return PcHost.followsExe;}
    private boolean pp(){return host.followsPp();}
    @Override public boolean twoEffectChannels(){return pp();}
    @Override protected boolean keepsPointerAcrossRebuild(){return exe();}
    private void setAuto(boolean on)throws IOException{auto=on;if(exe())host.write(99,13,on?1:0);}
    private boolean restoredFlag;
    @Override public boolean takeRestored(){boolean was=restoredFlag;restoredFlag=false;return was;}
    /** 42996x. F9 stays quick load here, as the app's menu shows it, where the exe opens the settings. */
    @Override public boolean functionKey(int code)throws Exception{
        if(!exe())return false;
        String a=code==java.awt.event.KeyEvent.VK_F1?":torisetu":code==java.awt.event.KeyEvent.VK_F2?":rapid":code==java.awt.event.KeyEvent.VK_F3?":auto":code==java.awt.event.KeyEvent.VK_F5?":qs":code==java.awt.event.KeyEvent.VK_F6||code==java.awt.event.KeyEvent.VK_F9?":ql":code==java.awt.event.KeyEvent.VK_F7?":ds":code==java.awt.event.KeyEvent.VK_F8?":dl":null;
        if(a==null)return false;
        if(a.equals(":qs")||a.equals(":ql")||textWaiting())action(a);
        return true;
    }
    private boolean autoDue()throws IOException{
        if(!auto)return false;
        if(!exe())return textClock>textDuration()+Math.max(1,host.read(99,29))*100L&&!audioActive(5);
        if(pp())return textClock>textDuration()+(20-host.read(99,29))*5*16L+16&&(!audioActive(5)||host.read(99,28)==1);
        int mode=host.read(99,28); // 4342A5
        long wait=mode==2?host.read(99,184)*1000L:body().getBytes(Binary.SJIS).length/2*(23-host.read(99,29))*5L;
        return textClock>=wait&&host.dialogue.rendered()&&!(mode==1&&audioActive(5));
    }
    private int bar(){try{if(exe()&&host.read(99,227)==0&&host.ui.templates[9]!=null)return 9;}catch(IOException e){throw new IllegalStateException(e);}return 10;} // 423B02
    private boolean swallowRelease;
    /** P.P. 42DCE1, 43033D: as DC2PC, pressing completes the line and that release does not advance. D.D. and W.Y. act on release. */
    @Override public void pointerDown(int x,int y)throws Exception{
        if(pp()){swallowRelease=textWaiting()&&!host.dialogue.rendered();if(swallowRelease)vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);}
        super.pointerDown(x,y);
    }
    /** 42B04C, P.P. 430478: up opens the log, down is a click. */
    private void scrollExe(int units)throws IOException{
        if(hidden){hidden=false;return;}
        if(!textWaiting()||host.ui.waiting)return;
        if(units<0)action(":backlog");else vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);
    }
    @Override public void keyPress(boolean space)throws Exception{
        if(!exe()){super.keyPress(space);return;}
        if(hidden){hidden=false;return;}
        if(host.ui.waiting||host.choosing()){if(!space)click(pointerX,pointerY,false);return;}
        if(space){if(textWaiting()){hidden=true;if(pp())host.playSound(0xdf);else setAuto(false);}return;}
        if(textWaiting())vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);
    }
    private int pointerX,pointerY;
    public void tick(long elapsed)throws Exception{
        host.ctrlHeld=rapid;host.fullscreen=fullscreenRequested;
        if(exe()){auto=host.read(99,13)!=0;if(!pp()&&rapid&&vm.variables.read(9,99)==0)host.endTimedWait();if(!pp()&&rapid&&host.scene.fade.active())vm.elapse(elapsed);} // 408B50: Ctrl doubles a fade.
        vm.elapse(elapsed);
        if(textWaiting()){if(textTicket!=ticket()){textTicket=ticket();textClock=0;}textClock+=elapsed;if(textClock>=textDuration())vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);if(rapid&&host.skipAllowed||autoDue())vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);}
        if(vm.run(1000)==MesVm.Status.FAULT)throw new IOException(vm.error());
        if(host.windowRequest>=0){fullscreenRequested=host.windowRequest!=0;host.windowRequest=-1;}
        if(host.windowSizeReset){windowSizeReset=true;host.windowSizeReset=false;}
        if(caller==null&&textWaiting()&&!host.history.entries.isEmpty()){Dc2History.Entry entry=host.history.entries.get(host.history.entries.size()-1);if(entry.scene.length==0){ByteArrayOutputStream b=new ByteArrayOutputStream();host.scene.capture().save(new DataOutputStream(b));entry.scene=b.toByteArray();}}
        if(host.returnRequested){if(caller==null)throw new IOException("System return without story caller");byte[] saved=caller;restore(saved);restoredFlag=false;finishSystemAudio();caller=null;}
        if(host.saveRequest>=0){int slot=host.saveRequest;save(slot(slot));scanSaves();host.saveRequest=-1;vm.elapse(0);persist();}
        if(host.loadRequest>=0){restore(slot(host.loadRequest));caller=null;}
        if(caller!=null&&vm.scriptName().equalsIgnoreCase("MAIN"))caller=null;
        if(host.autosaveGeneration!=savedAutoGeneration&&caller==null&&textWaiting()){savedAutoGeneration=host.autosaveGeneration;if(!audit){save(slot(exe()?0:2));scanSaves();persist();}}
        if(host.state.progressRevision!=savedProgressRevision)persist();
    }
    public List<Control> controls(){List<Control> r=new ArrayList<>();if(host.ui.waiting){for(Dc3Ui.Button b:host.ui.current.buttons)if(!b.action.isEmpty())r.add(new Control(b.action,b.left,b.top,b.right,b.bottom));if(host.ui.gridWaiting)for(int n=0;n<host.ui.grid.size();n++){Dc3Ui.Button b=host.ui.grid.get(n);r.add(new Control("@slot:"+n,b.left,b.top,b.right,b.bottom));}}
        else if(host.choosing()){Dc3Ui.Panel p=host.ui.templates[11];if(p!=null)for(int n=0;n<host.choices().size()&&n+1<p.buttons.size();n++){Dc3Ui.Button b=p.buttons.get(n+1);r.add(new Control("@choice:"+n,b.left,b.top,b.right,b.bottom));}}
        else if(textWaiting()&&!hidden){Dc3Ui.Panel p=host.ui.templates[bar()];if(p!=null)for(Dc3Ui.Button b:p.buttons)if(!b.action.isEmpty())r.add(new Control(b.action,b.left,b.top,b.right,b.bottom));}return r;
    }
    public void scroll(int units)throws IOException{if(exe()&&!host.history.active&&!host.ui.gridWaiting){scrollExe(units);return;}if(exe()&&host.history.active&&units>0&&host.history.cursor>=Math.max(0,host.history.entries.size()-4)){host.internalMenuAction(ticket(),-1);return;} /* 4068B9 */if(host.history.active){host.history.scroll(units);Dc3Backlog.build(host.ui,host.history,resources.profile.id);return;}if(host.ui.gridWaiting&&host.list.active())host.write(99,246,host.list.clamp(host.read(99,246)+units*host.list.columns,host.ui.grid.size()));if(host.ui.gridWaiting&&!host.ui.actions[15].isEmpty())vm.activateMenu(ticket(),-15);}
    public void click(int x,int y,boolean right)throws Exception{
        if(hidden){hidden=false;if(exe()&&pp())host.playSound(0xdf);return;}
        if(exe()&&!pp()&&right&&host.scene.fade.active()&&host.read(99,192)==0){host.finishFade();return;}
        if(exe()&&!pp()&&right&&slotAction(3))return;
        if(exe()&&right&&textWaiting()&&!host.ui.waiting){ // 429874, P.P. 43036F: right click first stops skip and auto, then does what register 211 says.
            if(auto||rapid){setAuto(false);rapid=false;return;}
            int use=pp()||vm.variables.read(9,7)!=0?0:host.read(99,211);if(use==1){hidden=true;return;}if(use==2){action(":backlog");return;}
            if(!pp())host.uiSound(223);action(":menu");return;
        }
        if(!right&&drag(x,y,true)){endDrag();return;}
        if(right&&host.hasTimedInput()&&!host.ui.actions[3].isEmpty()){vm.activateMenu(ticket(),-1);return;}
        if(host.ui.waiting){if(!right&&host.ui.gridWaiting&&host.list.active()){int offset=host.list.scrollAt(x,y,host.ui.gridBounds,host.read(99,246),host.ui.grid.size());if(offset>=0){host.write(99,246,offset);if(!host.ui.actions[15].isEmpty())vm.activateMenu(ticket(),-15);return;}}if(!right&&host.ui.gridWaiting&&!(exe()&&host.ui.activeConfirmation>=0))for(int n=0;n<host.ui.grid.size();n++)if(host.ui.grid.get(n).contains(x,y)){vm.activateGrid(ticket(),n);return;} /* A confirmation box is modal over the save rows. */int hit=right?-1:hit(x,y);if(hit>=0){Dc3Ui.Button b=host.ui.current.buttons.get(hit);for(Dc3Ui.Sprite s:b.sprites)if(s.state==5){Dc3Scene.Quad q=s.quad;int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));vm.variables.write(q.sy,q.width,value);if(q.sy==99&&q.width==189)vm.variables.write(99,187,value);if(b.action.isEmpty())return;}}if(right||hit>=0)vm.activateMenu(ticket(),hit);return;}
        for(Control b:controls())if(!right&&b.contains(x,y)){if(b.action.startsWith("@choice:")){int n=Integer.parseInt(b.action.substring(8));vm.choose(host.choices().get(n).label(),n);return;}if(exe()&&textWaiting()){Dc3Ui.Panel bar=host.ui.templates[bar()];if(bar!=null)for(Dc3Ui.Button k:bar.buttons)if(k.action.equals(b.action)){host.uiSound(k.clickSound);break;}}action(b.action);return;}
        if(exe()&&pp()&&!right&&swallowRelease){swallowRelease=false;return;} // 43033D, after the buttons have had their turn.
        if(exe()&&!pp()&&!right&&slotAction(2))return;
        if(right){action(":menu");return;}vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);
    }
    /** 433F8C: a script can leave actions for any wait, such as a click ending a whole scene. */
    private boolean slotAction(int slot)throws IOException{
        if(host.ui.waiting||host.choosing())return false;String a=host.ui.actions[slot];if(a.isEmpty()||a.equals("-")||!vm.hasLabel(a.getBytes(Binary.SJIS)))return false;
        return vm.activateMenu(ticket(),slot==3?-1:-2);
    }
    public boolean drag(int x,int y,boolean begin)throws Exception{
        if(begin){dragged=null;dragKind=0;if(!host.ui.waiting)return false;if(host.history.active&&host.history.entries.size()>4&&x>=940&&x<980&&y>=76&&y<416)dragKind=1;else if(host.ui.gridWaiting&&host.list.active()&&x>=host.ui.gridBounds[0]-4&&x<host.ui.gridBounds[2]+4&&y>=host.ui.gridBounds[1]&&y<host.ui.gridBounds[3])dragKind=2;else for(Dc3Ui.Button b:host.ui.current.buttons)if(b.contains(x,y))for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)dragged=b;}
        if(dragKind==1){int max=Math.max(0,host.history.entries.size()-4);host.history.cursor=Math.max(0,Math.min(max,(int)Math.round((y-80)*max/332.0)));Dc3Backlog.build(host.ui,host.history,resources.profile.id);return true;}
        if(dragKind==2){int[] b=host.ui.gridBounds;int cy=Math.max(b[1],Math.min(b[3]-1,y));host.write(99,246,y>=b[3]?host.list.clamp(Integer.MAX_VALUE,host.ui.grid.size()):host.list.scrollAt((b[0]+b[2])/2,cy,b,host.read(99,246),host.ui.grid.size()));return true;}
        if(dragged==null)return false;for(Dc3Ui.Sprite s:dragged.sprites)if(s.state==5){Dc3Scene.Quad q=s.quad;int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));vm.variables.write(q.sy,q.width,value);if(q.sy==99&&q.width==189)vm.variables.write(99,187,value);}return true;
    }
    public void endDrag()throws Exception{int kind=dragKind;dragKind=0;if(kind==2&&!host.ui.actions[15].isEmpty()){vm.activateMenu(ticket(),-15);return;}if(dragged==null)return;String action=dragged.action;dragged=null;if(!action.isEmpty())for(int n=0;n<host.ui.current.buttons.size();n++)if(host.ui.current.buttons.get(n).action.equals(action)){vm.activateMenu(ticket(),n);break;}}
    protected void cancelDrag(){dragged=null;dragKind=0;}
    protected void pressed(int x,int y,boolean down){host.ui.pressed=down&&host.ui.waiting?hit(x,y):-1;pressedControl=-1;if(down){List<Control> controls=controls();for(int n=0;n<controls.size();n++)if(controls.get(n).contains(x,y))pressedControl=n;}}
    public void action(String a)throws IOException{if(exe()&&a.startsWith(":a")&&a.length()==3){char c=a.charAt(2);if(c=='>')host.write(99,27,Math.max(0,host.read(99,27)-1));else if(c=='<')host.write(99,27,Math.min(11,host.read(99,27)+1));else if(c=='+')host.write(99,29,Math.max(0,host.read(99,29)-1));else if(c=='-')host.write(99,29,Math.min(20,host.read(99,29)+1));return;}if(exe()&&a.equals(":a")){auto=false;return;}if(exe()&&a.equals(":n")){host.write(99,227,host.read(99,227)==0?1:0);return;} // 429059
        if(exe()&&(a.equals(":ds")||a.equals(":dl"))){if(vm.variables.read(9,7)!=0)return;a=a.equals(":ds")?":save":":load";} // 428C15, 428C5A
        switch(a){case ":auto":setAuto(!auto);return;case ":rapid":rapid=!rapid;return;case ":close":case ":clear":hidden=true;return;case ":full":fullscreenRequested=!fullscreenRequested;return;case ":qsave":case ":qs":save(slot(exe()?2:1));scanSaves();return;case ":qload":case ":ql":if(Files.exists(slot(exe()?2:1)))restore(slot(exe()?2:1));return; // 428C79: slot 2, whose confirmation box is not reproduced.
        case ":menu":case ":option":case ":backlog":case ":save":case ":load":case ":torisetu":
            if(caller!=null&&exe())return;if(caller!=null)throw new IOException("Nested system caller");if(!textWaiting())return;
            vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);vm.run(1000);caller=vm.snapshot();beginSystemAudio();host.enterSystem();if(a.equals(":backlog")){host.history.open();Dc3Backlog.build(host.ui,host.history,resources.profile.id);setAuto(false);rapid=false;return;}vm.enterSystem(a.equals(":menu")?"ADVMENU":a.equals(":option")?(pp()?"OPTION":"CONFIG"):a.equals(":backlog")?"LOG":a.equals(":torisetu")?"MANUAL":a.substring(1).toUpperCase(Locale.ROOT));setAuto(false);rapid=false;return;
        case ":n":case ":ds":case ":dl":if(exe())return;
        default:throw new IOException("DD original system service pending: "+a);}}
    private int hit(int x,int y){if(exe()){for(int n=0;n<host.ui.current.buttons.size();n++){Dc3Ui.Button b=host.ui.current.buttons.get(n);if(x>b.left&&x<b.right&&y>b.top&&y<b.bottom){if(!b.action.isEmpty())return n;for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)return n;}}return -1;} /* 41C260 */for(int n=host.ui.current.buttons.size()-1;n>=0;n--){Dc3Ui.Button b=host.ui.current.buttons.get(n);if(b.contains(x,y)){if(!b.action.isEmpty())return n;for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)return n;}}return -1;}
    public void hover(int x,int y)throws IOException{pointerX=x;pointerY=y;historyHover=host.history.active&&x>=206&&x<885&&y>=44&&y<564?(y-44)/130:-1;if(host.ui.waiting)host.hoverMenu(hit(x,y));int was=hovered;hovered=-1;List<Control> bs=controls();for(int n=0;n<bs.size();n++)if(bs.get(n).contains(x,y)&&!(exe()&&hovered>=0))hovered=n;
        if(exe()&&hovered!=was&&hovered>=0){String action=bs.get(hovered).action; // 415C60: only reaching another button sounds.
            if(action.startsWith("@slot:"))host.uiSound(host.ui.gridHoverSound);
            else if(textWaiting()){Dc3Ui.Panel bar=host.ui.templates[bar()];if(bar!=null)for(Dc3Ui.Button k:bar.buttons)if(k.action.equals(action)){host.uiSound(k.hoverSound);break;}}
        }}
    private Picture picture(String name)throws IOException{if(name==null||name.isEmpty())return null;if(name.equals("@ui-capture"))return new Picture(captures.resolve(host.uiCapture));Picture p=pictures.get(name);if(p==null){try{p=new Picture(resources.image(name));}catch(java.io.FileNotFoundException missing){if(!exe())throw missing;missingImages.add(name);return null;}pictures.put(name,p);loadedImages.add(name);}return p;} /* D.C.III R has no MSK\\MASK11A; a missing picture is simply not drawn. */
    private BufferedImage drawCapture(int kind,DataInputStream input)throws IOException {
        if(kind!=UiCapture.DD)throw new IOException("Wrong DD UI capture profile");SceneCaptures.Dd data=new SceneCaptures.Dd(input);int[] saved=capturePalette;capturePalette=data.palette;
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(Color.BLACK);g.fillRect(0,0,width,height);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);Dc3Scene.Frame f=data.frame;scene(g,f.layers,f.quads,f.camera,f.petals,f.fog,f.clouds,f.environmentTime,false);panel(g,data.panel);}finally{g.dispose();capturePalette=saved;}return image;
    }
    private void quad(Graphics2D g,Dc3Scene.Quad q)throws IOException{Picture p=picture(host.uiSurface(q.resource));if(p==null)return;g.drawImage(q.opaque?p.opaque:p.image,q.x,q.y,q.x+q.width,q.y+q.height,q.sx,q.sy,q.sx+q.width,q.sy+q.height,null);}
    private void button(Graphics2D g,Dc3Ui.Button b,int state)throws IOException{boolean found=false;for(Dc3Ui.Sprite s:b.sprites)if(s.state==state)found=true;for(Dc3Ui.Sprite s:b.sprites)if(s.state==(found?state:99)||s.state==0&&state==99)quad(g,s.quad);}
    private void panel(Graphics2D g,Dc3Ui.Panel p)throws IOException{if(p==null||p.hidden)return;for(Dc3Scene.Quad q:p.quads)quad(g,q);for(Dc3Ui.Button b:p.buttons)for(Dc3Ui.Sprite s:b.sprites)if(s.state==99||s.state==0)quad(g,s.quad);for(Dc3Ui.Text t:p.texts)if(!t.text.isEmpty())text(g,codec.apply(t.text),t.args[0],t.args[1],32,t.args[3],width-t.args[0]);}
    private void sliders(Graphics2D g)throws IOException{if(!host.ui.waiting)return;for(Dc3Ui.Button b:host.ui.current.buttons){Dc3Scene.Quad config=null;for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)config=s.quad;if(config==null)continue;int value=vm.variables.read(config.sy,config.width),offset=(config.y-config.x)*Math.max(0,Math.min(config.sx-1,value))/Math.max(1,config.sx-1);
        for(Dc3Ui.Sprite s:b.sprites){Dc3Scene.Quad q=s.quad;Shape old=g.getClip();AffineTransform tr=g.getTransform();if(s.state==2){g.clipRect(config.x,0,Math.max(0,offset),height);quad(g,q);}else if(s.state==6){g.translate(offset,0);quad(g,q);}g.setClip(old);g.setTransform(tr);}}
    }
    private PcText.Rules rules(){return pp()?PcText.DM:PcText.DC3;}
    private Color paint(int style){int color=(capturePalette==null?host.ui.palette:capturePalette)[style&63];return new Color(color==0?0xffffffff:color,true);}
    /** D.D. and W.Y. outline in eight directions (435EC0); P.P. draws DC2PC's one step shadow. */
    private void glyph(Graphics2D g,String c,int x,int y,int size,int style){
        g.setFont(DesktopLegacy.pcFont(size));int base=DesktopLegacy.pcBaseline(y,size);
        if((style&0x80)!=0){
            if(pp()){g.setColor(new Color((capturePalette==null?host.ui.palette:capturePalette)[0],true));g.drawString(c,x+1,base+1);}
            else{g.setColor(Color.BLACK);for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)g.drawString(c,x+dx,base+dy);}
        }
        g.setColor(paint(style));g.drawString(c,x,base);
    }
    /** 43D593: labels take inline size tags and break only at a line feed. */
    private void label(Graphics2D g,String s,int x,int y,int leading,int style){
        boolean ppCells=pp();int size=22,at=ppCells?x:x+1,line=ppCells?y:y+11; // 43E0AC: the texture puts the glyph one in and eleven down.
        for(int pos=0;pos<s.length();){
            int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=c.length();
            if(cp=='@'&&pos<s.length()){char t=s.charAt(pos);int tagged=t=='m'?22:t=='l'?32:t=='s'?(ppCells?16:20):t=='i'?18:t=='r'?(ppCells?12:14):t=='z'?12:t=='u'?9:0;if(tagged!=0){size=tagged;pos++;continue;}}
            if(cp=='\n'){at=ppCells?x:x+1;line+=ppCells?Math.max(size,leading):size+12;continue;}
            glyph(g,c,at,line,size,style);int wide=c.getBytes(Binary.SJIS).length;at+=wide==1?(ppCells?(size-1)/2:size/2):(ppCells?size-1:size);
        }
    }
    /** D.D. 43F3E0: each line is wiped on left to right, 2*r26+8 pixels a frame, then the next. Frames are taken as 60 a second. */
    private int[] lineWidths(PcText.Layout l,int x){
        int count=0;for(PcText.Glyph c:l.glyphs)count=Math.max(count,c.line+1);int[] w=new int[count];
        for(PcText.Glyph c:l.glyphs)w[c.line]=Math.max(w[c.line],c.x-x+(c.size==PcText.DC3.rubySize?c.size:c.text.getBytes(Binary.SJIS).length==1?11:22));
        return w;
    }
    private long wipeMillis(String raw)throws IOException{
        int s=host.read(99,26);if(s>=11)return 0;int step=2*s+8;long frames=0;for(int w:lineWidths(PcText.layout(raw,0,0,34,PcText.DC3),0))frames+=(w+2+step-1)/step;return frames*1000/60;
    }
    private int cursorX,cursorY;
    private void wipedBody(Graphics2D g,String raw,int x,int y,int style)throws IOException{
        PcText.Layout l=PcText.layout(raw,x+1,y+11,34,PcText.DC3);int[] widths=lineWidths(l,x+1);int s=host.read(99,26);
        long left=host.dialogue.rendered()||s>=11?Long.MAX_VALUE:playbackMillis()*60/1000*(2*s+8);
        for(int n=0;n<widths.length&&left>0;n++){
            Shape clip=g.getClip();g.clipRect(x-2,y+34*n-2,(int)Math.min(left,widths[n]+6),38);
            for(int pass=0;pass<2;pass++)for(PcText.Glyph c:l.glyphs)if(c.line==n&&(c.size==PcText.DC3.rubySize)==(pass==1))glyph(g,c.text,c.x,c.y,c.size,style);
            g.setClip(clip);left-=widths[n]+2;cursorX=x+widths[n];cursorY=y+34*(n+1);
        }
    }
    /** 423AB3, 432443: the click cursor sits at the end of the last line, six 24 px frames. 423B28: skip and auto have icons. */
    private void waitMarks(Graphics2D g)throws IOException{
        if(host.dialogue.rendered()&&!rapid&&!auto&&host.ui.templates[2]!=null)for(Dc3Scene.Quad q:host.ui.templates[2].quads){
            Picture p=picture(host.uiSurface(q.resource));if(p==null)continue;int frame=(int)(textClock/133%6),sx=q.sx+24*frame,top=cursorY-q.height;
            g.drawImage(p.image,cursorX,top,cursorX+q.width,top+q.height,sx,q.sy,sx+q.width,q.sy+q.height,null);
        }
        if(host.read(99,213)!=0){if(rapid)panel(g,host.ui.templates[17]);else if(auto)panel(g,host.ui.templates[18]);}
    }
    /** 415C60: the toolbar has hover and press pictures like any other panel. */
    private void toolbar(Graphics2D g)throws IOException{
        Dc3Ui.Panel p=host.ui.templates[bar()];if(p==null||p.hidden)return;for(Dc3Scene.Quad q:p.quads)quad(g,q);
        List<Control> all=controls();String over=hovered>=0&&hovered<all.size()?all.get(hovered).action:"",down=pressedControl>=0&&pressedControl<all.size()?all.get(pressedControl).action:"";
        for(Dc3Ui.Button b:p.buttons)button(g,b,!b.action.isEmpty()&&b.action.equals(down)?2:!b.action.isEmpty()&&b.action.equals(over)?1:99);
    }
    private long textTime(int ticks)throws IOException{
        if(!pp())return TextPlayback.duration(resources.profile.id,textSpeed(),ticks);
        int nativeSpeed=11-textSpeed();if(nativeSpeed<=0)return 0; // 42DAB2
        return nativeSpeed<4?((ticks+(4-nativeSpeed)-1)/(4-nativeSpeed))*15L:ticks*(nativeSpeed-3)*17L;
    }
    private void bodyText(Graphics2D g,String raw,int x,int y,int lineHeight,int style)throws IOException{
        PcText.Rules r=rules();PcText.Layout l=PcText.layout(raw,x,y,lineHeight,r);int shown=l.ticks;
        if(!host.dialogue.rendered()){int lo=0,hi=l.ticks;while(lo<hi){int mid=(lo+hi+1)/2;if(textTime(mid)<=playbackMillis())lo=mid;else hi=mid-1;}shown=lo;}
        for(int pass=0;pass<2;pass++)for(PcText.Glyph c:l.glyphs){
            if(c.tick>=shown||(c.size==r.rubySize)!=(pass==1))continue;
            if(pass==1&&pp()&&c.text.equals("・"))glyph(g,c.text,c.x+r.dotDx,c.y+r.dotDy+r.rubyRise,r.body,style);else glyph(g,c.text,c.x,c.y,c.size,style);
        }
    }
    private void text(Graphics2D g,String s,int x,int y,int leading,int style,int w){if(exe()){label(g,s,x,y,leading,style);return;}g.setFont(new Font("Microsoft YaHei",Font.PLAIN,24));int color=(capturePalette==null?host.ui.palette:capturePalette)[style&63];g.setColor(new Color(color==0?0xffffffff:color,true));int at=x,base=y+24;for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=Character.charCount(cp);int cw=g.getFontMetrics().stringWidth(c);if(cp==10||at+cw>x+w){at=x;base+=Math.max(24,leading);if(cp==10)continue;}if((style&0x80)!=0){g.setColor(new Color(0,55,90,210));for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)g.drawString(c,at+dx,base+dy);g.setColor(new Color(color==0?0xffffffff:color,true));}g.drawString(c,at,base);at+=cw;}}
    private void dialogue(Graphics2D g)throws IOException{
        int bodyIndex=host.scene.novelMode?3:0,nameIndex=host.scene.novelMode?4:1;Dc3Ui.Panel p=host.ui.templates[bodyIndex],name=host.ui.templates[nameIndex];panel(g,p);if(!host.dialogue.shownSpeaker.isEmpty())panel(g,name);
        if(p==null)throw new IOException("Original dialogue template missing");
        for(int slot:new int[]{host.scene.layout.face,host.scene.layout.face+1}){Picture face=picture(host.scene.slots[slot].resource);if(face!=null)g.drawImage(face.image,host.ui.messageX+face.raw.x,host.ui.messageY+face.raw.y,null);}
        if(name!=null&&!host.dialogue.shownSpeaker.isEmpty())for(Dc3Ui.Text t:name.texts)if(t.args[2]>0)text(g,host.dialogue.shownSpeaker,t.args[0],t.args[1],t.args[2],t.args[3],width-t.args[0]-30);
        for(Dc3Ui.Text t:p.texts)if(t.args[2]>0){if(exe()&&!pp())wipedBody(g,body(),t.args[0],t.args[1],t.args[3]);else if(exe())bodyText(g,body(),t.args[0],t.args[1],t.args[2],t.args[3]);else text(g,visibleBody(),t.args[0],t.args[1],t.args[2],t.args[3],width-t.args[0]-60);}
        if(exe()){if(!pp())waitMarks(g);toolbar(g);}else panel(g,host.ui.templates[10]);
    }
    private void scene(Graphics2D g,Dc3Scene.Layer[] layers,List<Dc3Scene.Quad> quads,Dc3Camera camera,Dc3Petals petals,boolean fog,boolean clouds,long time,boolean live)throws IOException{
        Dc3Layout layout=host.scene.layout;
        Integer[] order=new Integer[layout.order.length];for(int n=0;n<order.length;n++)order[n]=layout.order[n];
        Arrays.sort(order,(a,b)->Integer.compare(a<0?50:layers[a].priority,b<0?50:layers[b].priority));
        for(int s:order){if(s<0){petals(g,layout.petalGroup(s),petals,layers[layout.petals].resource);continue;}if(s==layout.fog&&!fog)continue;Dc3Scene.Layer l=layers[s];Picture p=picture(l.resource);if(p==null)continue;AffineTransform old=g.getTransform();Composite alpha=g.getComposite();
            if(s==1||layout.cast(s)){float px=512+camera.x*(s!=1?.8f:1),py=288+camera.y*(s!=1?.8f:1);g.translate(px,py);g.scale(camera.zoom/100f,camera.zoom/100f);g.translate(-px,-py);}
            boolean extra=layout.cast(s)&&!layout.lead(s);int motion=extra?s-layout.companionDelta:s;
            Dc3Scene.Layer t=extra?layers[motion]:l;
            g.translate(t.x+t.pivotX+(live?host.scene.displacement(motion,0):0),t.y+t.pivotY+(live?host.scene.displacement(motion,1):0));g.scale(t.scaleX/100f,t.scaleY/100f);g.translate(-t.pivotX,-t.pivotY);g.setComposite(AlphaComposite.SrcOver.derive(t.alpha/255f));g.drawImage(p.image,l.centered?(width-p.image.getWidth())/2:p.raw.x,l.centered?(height-p.image.getHeight())/2:p.raw.y,null);g.setTransform(old);g.setComposite(alpha);
        }
        if(clouds){Picture p=picture(layers[layout.clouds].resource);if(p!=null)for(int row=0;row<2;row++){int x=(int)(-2048*(time%(row==0?120000:60000))/(row==0?120000:60000));g.drawImage(p.image,x,0,x+3072,576,0,row*600,3072,row*600+576,null);}}
        for(Dc3Scene.Quad q:quads)quad(g,q);
    }
    private void petals(Graphics2D g,int group,Dc3Petals ps,String name)throws IOException{if(ps.particles.isEmpty())return;Picture p=picture(name);if(p==null)return;Composite old=g.getComposite();for(Dc3Petals.Particle a:ps.particles)if(a.group==group){g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,Math.min(1,a.alpha))));int sx=a.frame*32,sy=a.row*28,x=(int)ps.x(a),y=(int)a.y;g.drawImage(p.image,x,y,x+28,y+28,sx,sy,sx+28,sy+28,null);}g.setComposite(old);}
    private void clearTransitionBuffers(){transitionMask.clear();if(transitionFrame!=null){transitionFrame.flush();transitionFrame=null;}}
    private BufferedImage keptScreen,lastFrame,grabbed;private int keptRevision,grabbedGeneration=-1;
    public BufferedImage render()throws IOException{
        // 41C640, as DC2PC: a click does not repaint, so a menu stays up until the script draws.
        if(pp()&&keptScreen!=null){if(!host.ui.waiting&&!textWaiting()&&keptRevision==host.scene.revision&&!host.scene.fade.active()&&!host.credits.active()&&!host.choosing())return keptScreen;keptScreen=null;}
        BufferedImage shown=renderLive();lastFrame=shown;
        if(pp()&&host.ui.waiting){keptScreen=shown;keptRevision=host.scene.revision;}
        return shown;
    }
    private BufferedImage renderLive()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(Color.BLACK);g.fillRect(0,0,width,height);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);scene(g,host.scene.slots,host.scene.quads,host.scene.camera,host.scene.petals,host.scene.fog,host.scene.clouds,host.scene.environmentTime,true);
            Dc3Transition f=host.scene.fade;
            if(!f.active())clearTransitionBuffers();
            else if(f.mode==3){clearTransitionBuffers();g.setColor(new Color(1f,1f,1f,1-Math.abs(f.progress()*2-1)));g.fillRect(0,0,width,height);}
            else{
                // Repaint an opaque base each frame: a prior mask must not accumulate alpha.
                // Keep only one scratch image, including ordinary crossfades with no mask.
                if(transitionFrame==null)transitionFrame=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                BufferedImage before=transitionFrame;
                try{
                    if(exe()&&f.generation!=grabbedGeneration){grabbedGeneration=f.generation;grabbed=lastFrame;} // 426CA0: a fade starts from what was on the screen, interface included.
                    Graphics2D bg=before.createGraphics();
                    try{bg.setColor(Color.BLACK);bg.fillRect(0,0,width,height);if(exe()&&grabbed!=null)bg.drawImage(grabbed,0,0,null);else scene(bg,f.from.layers,f.from.quads,f.from.camera,f.from.petals,f.from.fog,f.from.clouds,f.from.environmentTime,false);}finally{bg.dispose();}
                    Picture mask=f.mode==16?null:picture(f.mask); // P.P. has no system\\effbreak; mode 16 draws no mask here in any game.
                    if(mask!=null&&f.mode!=16)transitionMask.apply(before,mask.image,f);
                    else{transitionMask.clear();g.setComposite(AlphaComposite.SrcOver.derive(1-f.progress()));}
                    g.drawImage(before,0,0,null);
                }finally{g.setComposite(AlphaComposite.SrcOver);}
            }
            if(host.credits.active()){drawCredits(g);return image;}
            if(textWaiting()&&!hidden)dialogue(g);
            for(Dc3Scene.Quad q:host.ui.visible())quad(g,q);
            if(host.ui.panelUp())for(Dc3Ui.Text t:host.ui.current.texts)if(!t.text.isEmpty())text(g,codec.apply(t.text),t.args[0],t.args[1],32,t.args[2],width-t.args[0]);
            sliders(g);if(host.history.active)drawHistory(g);if(host.ui.gridWaiting)drawGrid(g);
            if(host.choosing()){Dc3Ui.Panel p=host.ui.templates[11];if(p==null||p.buttons.size()<=host.choices().size())throw new IOException("Original choice template missing");for(int n=0;n<host.choices().size();n++){Dc3Ui.Button b=p.buttons.get(n+1);button(g,b,n==pressedControl?2:n==hovered?1:99);Dc3Ui.Text style=p.texts.size()>n*3?p.texts.get(n*3):null;if(style==null)throw new IOException("Original choice text position missing");text(g,host.choices().get(n).caption,style.args[0],style.args[1],32,style.args[2],b.right-style.args[0]-20);}}
        }finally{g.dispose();}return image;}
    private void drawHistory(Graphics2D g)throws IOException{for(int row=0;row<4&&row+host.history.cursor<host.history.entries.size();row++){Dc2History.Entry e=host.history.entries.get(row+host.history.cursor);Shape clip=g.getClip();g.clipRect(206,44+130*row,679,131);int face=nativeData.face(e);if(face>=0){Picture atlas=picture("SYSTEM/LOGCHIP");int sx=face%6*116,sy=132+face/6*116;g.drawImage(atlas.image,220,48+row*130,335,163+row*130,sx,sy,sx+115,sy+115,null);}else text(g,e.speaker,230,52+130*row,27,0x87,120);text(g,e.body,366,54+130*row,27,0x87,510);g.setClip(clip);}
        Dc2History history=host.history;int index=history.cursor+historyHover;
        if(history.previewEnabled&&historyHover>=0&&index<history.entries.size()){byte[] bytes=history.entries.get(index).scene;if(bytes.length>0){
            // 406670: 12.5% about the 512,288 pivot. Position -400,row*130-196
            // becomes left=48, top=row*130+56. 4066DF supplies the border crop.
            int y=56+historyHover*130;Picture atlas=picture("SYSTEM/LOGCHIP");g.drawImage(atlas.image,42,y-6,193,y+78,696,532,847,616,null);
            Dc3Scene.Frame f=new Dc3Scene.Frame();f.load(new DataInputStream(new ByteArrayInputStream(bytes)));AffineTransform old=g.getTransform();Shape clip=g.getClip();g.clipRect(48,y,128,72);g.translate(48,y);g.scale(.125,.125);scene(g,f.layers,f.quads,f.camera,f.petals,f.fog,f.clouds,f.environmentTime,false);g.setTransform(old);g.setClip(clip);
        }}
    }
    private void creditPicture(Graphics2D g,String name,float x,float y,float scale,float alpha)throws IOException{Picture p=picture(name);if(p==null)return;AffineTransform t=g.getTransform();g.translate(x,y);g.scale(scale,scale);g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,Math.min(1,alpha))));g.drawImage(p.image,p.raw.x,p.raw.y,null);g.setComposite(AlphaComposite.SrcOver);g.setTransform(t);}
    private void drawCredits(Graphics2D g)throws IOException{Dc3Credits r=host.credits;g.setColor(Color.BLACK);g.fillRect(0,0,width,height);creditPicture(g,r.image("01",r.index()),r.x(),r.y(),r.scale(),1);creditPicture(g,r.image("02",r.index()),r.x(),r.y(),r.scale(),r.variantAlpha());g.setColor(new Color(1f,1f,1f,r.whiteAlpha()));g.fillRect(0,0,width,height);creditPicture(g,"ED/EDBACK",0,0,1,1);float y=-(r.scroll()%576);creditPicture(g,r.image("",r.strip()),0,y,1,1);if(r.strip()+1<r.strips)creditPicture(g,r.image("",r.strip()+1),0,y+576,1,1);g.setColor(new Color(0f,0f,0f,r.blackAlpha()));g.fillRect(0,0,width,height);}
    private void listQuad(Graphics2D g,Dc2Ui.Quad q)throws IOException{if(q.type==2){text(g,codec.apply(q.text),q.x,q.y,32,q.flags,width);return;}if(q.width<=0||q.height<=0)return;Picture p=picture(host.scene.slots[q.slot()].resource);if(p!=null)g.drawImage(p.image,q.x,q.y,q.x+q.width,q.y+q.height,q.sx,q.sy,q.sx+q.width,q.sy+q.height,null);}
    private void drawGrid(Graphics2D g)throws IOException{
        if(host.list.active()){if(host.musicRoom.caption){int index=host.musicRoom.catalogueIndex();if(index<host.list.items.size())for(Dc2Ui.Quad q:host.list.items.get(index).sprites)if(q.type==2&&q.state==99){text(g,codec.apply(q.text),428,36,32,0x87,560);break;}}int offset=host.list.clamp(host.read(99,246),host.ui.grid.size());for(int n=0;n<host.ui.grid.size();n++){ScriptList.Item item=host.list.at(offset,n,host.ui.grid.size());if(item==null)continue;Dc3Ui.Button b=host.ui.grid.get(n);int state=99;List<Control> controls=controls();if(hovered>=0&&hovered<controls.size()&&controls.get(hovered).action.equals("@slot:"+n))state=1;AffineTransform old=g.getTransform();g.translate(b.left,b.top);for(Dc2Ui.Quad q:ScriptList.visible(host.list.common,state))listQuad(g,q);for(Dc2Ui.Quad q:ScriptList.visible(item.sprites,state))listQuad(g,q);g.setTransform(old);}if(host.list.marker!=null){Dc2Ui.Quad thumb=host.list.marker;int[] b=host.ui.gridBounds;int max=host.list.clamp(Integer.MAX_VALUE,host.ui.grid.size());AffineTransform old=g.getTransform();g.translate((b[0]+b[2]-thumb.width)/2-thumb.x,b[1]+(b[3]-b[1])*offset/Math.max(1,max)-thumb.height/2-thumb.y);listQuad(g,thumb);g.setTransform(old);}return;}
        Picture p=picture(host.scene.slots[host.scene.layout.chipFirst].resource);if(p==null)throw new IOException("DD save atlas missing");
        for(int n=0;n<host.ui.grid.size();n++){Dc3Ui.Button b=host.ui.grid.get(n);int slot=host.gridSlot(n);int sx=vm.variables.read(3,20)==1?336:0,sy=0;g.drawImage(p.image,b.left,b.top,b.right,b.bottom,sx,sy,sx+b.right-b.left,sy+76,null);
            BufferedImage thumb=thumbnails.get(slot);if(thumb!=null)g.drawImage(thumb,b.left+5,b.top+25,64,36,null);
            text(g,String.format(Locale.ROOT,"%02d",slot-2),b.left+72,b.top+2,24,13,b.right-b.left-75);
            if(Files.exists(slot(slot)))text(g,new java.text.SimpleDateFormat("MM/dd HH:mm").format(new Date(Files.getLastModifiedTime(slot(slot)).toMillis())),b.left+72,b.top+32,24,13,b.right-b.left-75);
        }
    }
    private void scanSaves()throws IOException{host.availableSaves.clear();thumbnails.clear();for(int n=0;n<256;n++)if(Files.exists(slot(n))){host.availableSaves.add(n);scanThumbnail(n,slot(n));}}
    public BufferedImage preview()throws IOException{Dc3Scene.Frame f=host.systemScene!=null?host.systemScene:host.scene.capture();BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{scene(g,f.layers,f.quads,f.camera,f.petals,f.fog,f.clouds,f.environmentTime,false);}finally{g.dispose();}return image;}
    private int textSpeed()throws IOException{return host.read(99,auto?27:26);}
    private long textDuration()throws IOException{if(exe()&&!pp())return wipeMillis(body());if(exe())return textTime(PcText.layout(body(),0,0,32,rules()).ticks);return TextPlayback.duration(resources.profile.id,textSpeed(),body().codePointCount(0,body().length()));}
    public String visibleBody()throws IOException{return body().substring(0,TextPlayback.visible(resources.profile.id,textSpeed(),body(),playbackMillis(),host.dialogue.rendered()));}
    public long playbackMillis(){return textTicket==ticket()?textClock:0;}
    public void playbackMillis(long value){textTicket=ticket();textClock=value;}
    public String body(){return host.dialogue.shownText;}
    public boolean textWaiting(){return host.dialogue.waiting();}
    public boolean menuWaiting(){return host.ui.waiting||host.choosing();}
    public boolean actorVisible(){Dc3Layout layout=host.layout();for(int n=layout.spriteFirst;n<layout.spriteFirst+2*layout.spriteCount;n++)if(!host.scene.slots[n].resource.isEmpty())return true;return false;}
    public boolean exitRequested(){return host.exitRequested;}
    public long ticket(){return host.waitTicket();}
    public String movie(){return host.movie;}
    public int movieGeneration(){return host.movieGeneration;}
    public String audio(int ch){return host.audio[ch];}
    public int audioGeneration(int ch){return host.audioGeneration[ch];}
    public boolean audioLoop(int ch){return host.audioLoop[ch];}
    public boolean audioActive(int ch){return host.audioIsActive(ch);}
    public void audioCompleted(int ch,int gen){host.audioCompleted(ch,gen);}
    public int audioSeekGeneration(int ch){return ch==0?host.musicRoom.seekGeneration:0;}
    public int audioSeekPercent(int ch){return ch==0?host.musicRoom.seek:0;}
    public void audioPosition(int ch,long pos,long duration)throws IOException{super.audioPosition(ch,pos,duration);if(ch==0){host.musicRoom.position=pos;host.musicRoom.duration=duration;host.write(99,189,duration<=0?0:(int)(pos*100/duration));}}
    public float volume(int ch)throws IOException{if(pp()){if(host.read(99,ch==0?14:ch==5?5:15)==0)return 0;int l=host.read(99,ch==0?7:ch==5?9:8);float gain=ch==0?host.musicGain:ch==5?host.voiceGain():1;return l<=0?0:gain*(float)Math.pow(10,(Math.min(2*l,60)-60)*.83/20);} /* 430DA0 */if(exe()){ /* 43B5D0: linear in decibels, -50 to 0. 432EAA: voices can duck the music. */
            int vol=host.read(99,ch==0?7:ch==5?9:8);if(ch==0&&audioActive(5)&&host.read(99,190)!=0&&host.read(99,206)<100)vol=vol*host.read(99,206)/100;
            int master=host.read(99,225);if(vol<=0||master==0||host.read(99,203)==0||host.read(99,ch==0?14:ch==5?5:15)==0)return 0;
            return (float)Math.pow(10,(master*vol/100*5000/100-5000)/2000.0)*(ch==0?host.musicGain:ch==5?host.voiceGain():1);
        }return (host.read(99,203)==0?0:Math.max(0,Math.min(1,host.read(99,225)/100f)))*(ch==0?host.musicGain:ch==5?host.voiceGain():1)*Math.max(0,Math.min(1,host.read(99,ch==0?7:ch==5?9:8)/100f))*(host.read(99,ch==0?14:ch==5?5:15)==0?0:1);}
    private Path slot(int n)throws IOException{if(n<0||n>255)throw new IOException("Invalid DD save slot "+n);return saveDirectory.resolve("slot-"+n+".sav");}
    public byte[] snapshot()throws IOException{return caller==null?vm.snapshot():caller.clone();}
    public void restore(byte[] bytes)throws IOException{cancelPointer();Dc3Host h=new Dc3Host(resources,codec,true);MesVm m=new MesVm(resources.scripts(),h,"START");m.restore(bytes);h.gallery.recover(h);Dc3State globals=new Dc3State(true);globals.restoreSettings(host.state.settingsSnapshot());globals.mergeGallery(h.state);h.state.restoreSettings(globals.settingsSnapshot());clearTransitionBuffers();host=h;host.settingsLoader=this::reloadSettings;host.captureRenderer=captures;vm=m;host.ui.pressed=-1;savedAutoGeneration=h.autosaveGeneration;textTicket=-1;historyHover=-1;hidden=false;caller=null;restoredFlag=true;scanSaves();persist();}
    private void reloadSettings(Dc3State state)throws IOException{Path p=saveDirectory.resolve("progress.dat");if(!audit&&Files.exists(p))loadProgress(state,Files.readAllBytes(p));else state.restoreSettings(new Dc3State(true).settingsSnapshot());}
    private void loadProgress(Dc3State state,byte[] bytes)throws IOException{state.loadPersistent(bytes);}
    public String saveCaption(){return host.checkpointLabel().isEmpty()?body():codec.apply(host.checkpointLabel());}
    public void persist()throws IOException{if(!audit)atomic(saveDirectory.resolve("progress.dat"),host.state.savePersistent());savedProgressRevision=host.state.progressRevision;}
    private void recoverGallerySaves()throws IOException{
        Path marker=saveDirectory.resolve("gallery-recovery-v1.txt");if(Files.exists(marker))return;
        StringBuilder report=new StringBuilder("Recover retained CG frames and completed script transitions only. Original saves are unchanged.\n");
        List<Path> paths=new ArrayList<>();for(int slot=0;slot<256;slot++)paths.add(slot(slot));paths.add(saveDirectory.resolve("mobile-resume.sav"));
        if(paths.stream().noneMatch(Files::isRegularFile))return; // Let the older Android importer create its settings first.
        for(Path path:paths)if(Files.isRegularFile(path)){
            try{
                if(Files.size(path)>64*1024*1024)throw new IOException("Save size limit");
                DesktopSave saved=DesktopSave.decode(Files.readAllBytes(path));Dc3Host prior=new Dc3Host(resources,codec,true);
                new MesVm(resources.scripts(),prior,"START").restore(saved.state);prior.gallery.recover(prior);host.state.mergeGallery(prior.state);
                report.append("Recovered ").append(path.getFileName()).append('\n');
            }catch(IOException ex){report.append("Skipped ").append(path.getFileName()).append(": ").append(ex.getMessage()).append('\n');}
        }
        // Commit merged flags before the marker. Interrupted migration can safely run again.
        persist();atomic(marker,report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
