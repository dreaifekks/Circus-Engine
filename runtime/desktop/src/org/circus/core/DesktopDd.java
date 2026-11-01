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
    public DesktopDd(CircusResources r,TextCodec c,boolean audit)throws IOException{super(r,c,audit,1024,576);nativeData=new Dc3NativeData(r,c);host=new Dc3Host(r,c,true);host.settingsLoader=this::reloadSettings;captures=new DesktopCaptures(width,height,this::drawCapture);host.captureRenderer=captures;vm=new MesVm(r.scripts(),host,"START");Path p=saveDirectory.resolve("progress.dat");if(!audit&&Files.exists(p))loadProgress(host.state,Files.readAllBytes(p));if(!audit)recoverGallerySaves();savedProgressRevision=host.state.progressRevision;scanSaves();}
    public MesVm vm(){return vm;}
    public void tick(long elapsed)throws Exception{
        host.fullscreen=fullscreenRequested;vm.elapse(elapsed);
        if(textWaiting()){if(textTicket!=ticket()){textTicket=ticket();textClock=0;}textClock+=elapsed;if(textClock>=textDuration())vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);if(rapid&&host.skipAllowed||auto&&textClock>textDuration()+Math.max(1,host.read(99,29))*100L&&!audioActive(5))vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);}
        if(vm.run(1000)==MesVm.Status.FAULT)throw new IOException(vm.error());
        if(host.windowRequest>=0){fullscreenRequested=host.windowRequest!=0;host.windowRequest=-1;}
        if(host.windowSizeReset){windowSizeReset=true;host.windowSizeReset=false;}
        if(caller==null&&textWaiting()&&!host.history.entries.isEmpty()){Dc2History.Entry entry=host.history.entries.get(host.history.entries.size()-1);if(entry.scene.length==0){ByteArrayOutputStream b=new ByteArrayOutputStream();host.scene.capture().save(new DataOutputStream(b));entry.scene=b.toByteArray();}}
        if(host.returnRequested){if(caller==null)throw new IOException("System return without story caller");byte[] saved=caller;restore(saved);finishSystemAudio();caller=null;}
        if(host.saveRequest>=0){int slot=host.saveRequest;save(slot(slot));scanSaves();host.saveRequest=-1;vm.elapse(0);persist();}
        if(host.loadRequest>=0){restore(slot(host.loadRequest));caller=null;}
        if(caller!=null&&vm.scriptName().equalsIgnoreCase("MAIN"))caller=null;
        if(host.autosaveGeneration!=savedAutoGeneration&&caller==null&&textWaiting()){savedAutoGeneration=host.autosaveGeneration;if(!audit){save(slot(2));scanSaves();persist();}}
        if(host.state.progressRevision!=savedProgressRevision)persist();
    }
    public List<Control> controls(){List<Control> r=new ArrayList<>();if(host.ui.waiting){for(Dc3Ui.Button b:host.ui.current.buttons)if(!b.action.isEmpty())r.add(new Control(b.action,b.left,b.top,b.right,b.bottom));if(host.ui.gridWaiting)for(int n=0;n<host.ui.grid.size();n++){Dc3Ui.Button b=host.ui.grid.get(n);r.add(new Control("@slot:"+n,b.left,b.top,b.right,b.bottom));}}
        else if(host.choosing()){Dc3Ui.Panel p=host.ui.templates[11];if(p!=null)for(int n=0;n<host.choices().size()&&n+1<p.buttons.size();n++){Dc3Ui.Button b=p.buttons.get(n+1);r.add(new Control("@choice:"+n,b.left,b.top,b.right,b.bottom));}}
        else if(textWaiting()&&!hidden){Dc3Ui.Panel p=host.ui.templates[10];if(p!=null)for(Dc3Ui.Button b:p.buttons)if(!b.action.isEmpty())r.add(new Control(b.action,b.left,b.top,b.right,b.bottom));}return r;
    }
    public void scroll(int units)throws IOException{if(host.history.active){host.history.scroll(units);Dc3Backlog.build(host.ui,host.history);return;}if(host.ui.gridWaiting&&host.list.active())host.write(99,246,host.list.clamp(host.read(99,246)+units*host.list.columns,host.ui.grid.size()));if(host.ui.gridWaiting&&!host.ui.actions[15].isEmpty())vm.activateMenu(ticket(),-15);}
    public void click(int x,int y,boolean right)throws Exception{
        if(hidden){hidden=false;return;}if(!right&&drag(x,y,true)){endDrag();return;}
        if(right&&host.hasTimedInput()&&!host.ui.actions[3].isEmpty()){vm.activateMenu(ticket(),-1);return;}
        if(host.ui.waiting){if(!right&&host.ui.gridWaiting&&host.list.active()){int offset=host.list.scrollAt(x,y,host.ui.gridBounds,host.read(99,246),host.ui.grid.size());if(offset>=0){host.write(99,246,offset);if(!host.ui.actions[15].isEmpty())vm.activateMenu(ticket(),-15);return;}}if(!right&&host.ui.gridWaiting)for(int n=0;n<host.ui.grid.size();n++)if(host.ui.grid.get(n).contains(x,y)){vm.activateGrid(ticket(),n);return;}int hit=right?-1:hit(x,y);if(hit>=0){Dc3Ui.Button b=host.ui.current.buttons.get(hit);for(Dc3Ui.Sprite s:b.sprites)if(s.state==5){Dc3Scene.Quad q=s.quad;int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));vm.variables.write(q.sy,q.width,value);if(q.sy==99&&q.width==189)vm.variables.write(99,187,value);if(b.action.isEmpty())return;}}if(right||hit>=0)vm.activateMenu(ticket(),hit);return;}
        for(Control b:controls())if(!right&&b.contains(x,y)){if(b.action.startsWith("@choice:")){int n=Integer.parseInt(b.action.substring(8));vm.choose(host.choices().get(n).label(),n);return;}action(b.action);return;}
        if(right){action(":menu");return;}vm.signalHost(ticket(),MesVm.HostSignal.ADVANCE);
    }
    public boolean drag(int x,int y,boolean begin)throws Exception{
        if(begin){dragged=null;dragKind=0;if(!host.ui.waiting)return false;if(host.history.active&&host.history.entries.size()>4&&x>=940&&x<980&&y>=76&&y<416)dragKind=1;else if(host.ui.gridWaiting&&host.list.active()&&x>=host.ui.gridBounds[0]-4&&x<host.ui.gridBounds[2]+4&&y>=host.ui.gridBounds[1]&&y<host.ui.gridBounds[3])dragKind=2;else for(Dc3Ui.Button b:host.ui.current.buttons)if(b.contains(x,y))for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)dragged=b;}
        if(dragKind==1){int max=Math.max(0,host.history.entries.size()-4);host.history.cursor=Math.max(0,Math.min(max,(int)Math.round((y-80)*max/332.0)));Dc3Backlog.build(host.ui,host.history);return true;}
        if(dragKind==2){int[] b=host.ui.gridBounds;int cy=Math.max(b[1],Math.min(b[3]-1,y));host.write(99,246,y>=b[3]?host.list.clamp(Integer.MAX_VALUE,host.ui.grid.size()):host.list.scrollAt((b[0]+b[2])/2,cy,b,host.read(99,246),host.ui.grid.size()));return true;}
        if(dragged==null)return false;for(Dc3Ui.Sprite s:dragged.sprites)if(s.state==5){Dc3Scene.Quad q=s.quad;int value=(int)Math.round(Math.max(0,Math.min(1,(x-q.x)/(double)Math.max(1,q.y-q.x)))*Math.max(0,q.sx-1));vm.variables.write(q.sy,q.width,value);if(q.sy==99&&q.width==189)vm.variables.write(99,187,value);}return true;
    }
    public void endDrag()throws Exception{int kind=dragKind;dragKind=0;if(kind==2&&!host.ui.actions[15].isEmpty()){vm.activateMenu(ticket(),-15);return;}if(dragged==null)return;String action=dragged.action;dragged=null;if(!action.isEmpty())for(int n=0;n<host.ui.current.buttons.size();n++)if(host.ui.current.buttons.get(n).action.equals(action)){vm.activateMenu(ticket(),n);break;}}
    protected void cancelDrag(){dragged=null;dragKind=0;}
    protected void pressed(int x,int y,boolean down){host.ui.pressed=down&&host.ui.waiting?hit(x,y):-1;pressedControl=-1;if(down){List<Control> controls=controls();for(int n=0;n<controls.size();n++)if(controls.get(n).contains(x,y))pressedControl=n;}}
    public void action(String a)throws IOException{switch(a){case ":auto":auto=!auto;return;case ":rapid":rapid=!rapid;return;case ":close":case ":clear":hidden=true;return;case ":full":fullscreenRequested=!fullscreenRequested;return;case ":qsave":case ":qs":save(slot(1));scanSaves();return;case ":qload":case ":ql":if(Files.exists(slot(1)))restore(slot(1));return;
        case ":menu":case ":option":case ":backlog":case ":save":case ":load":case ":torisetu":
            if(caller!=null)throw new IOException("Nested system caller");if(!textWaiting())return;
            vm.signalHost(ticket(),MesVm.HostSignal.TEXT_COMPLETE);vm.run(1000);caller=vm.snapshot();beginSystemAudio();host.enterSystem();if(a.equals(":backlog")){host.history.open();Dc3Backlog.build(host.ui,host.history);auto=rapid=false;return;}vm.enterSystem(a.equals(":menu")?"ADVMENU":a.equals(":option")?"CONFIG":a.equals(":backlog")?"LOG":a.equals(":torisetu")?"MANUAL":a.substring(1).toUpperCase(Locale.ROOT));auto=rapid=false;return;
        default:throw new IOException("DD original system service pending: "+a);}}
    private int hit(int x,int y){for(int n=host.ui.current.buttons.size()-1;n>=0;n--){Dc3Ui.Button b=host.ui.current.buttons.get(n);if(b.contains(x,y)){if(!b.action.isEmpty())return n;for(Dc3Ui.Sprite s:b.sprites)if(s.state==5)return n;}}return -1;}
    public void hover(int x,int y)throws IOException{historyHover=host.history.active&&x>=206&&x<885&&y>=44&&y<564?(y-44)/130:-1;if(host.ui.waiting)host.hoverMenu(hit(x,y));hovered=-1;List<Control> bs=controls();for(int n=0;n<bs.size();n++)if(bs.get(n).contains(x,y))hovered=n;}
    private Picture picture(String name)throws IOException{if(name==null||name.isEmpty())return null;if(name.equals("@ui-capture"))return new Picture(captures.resolve(host.uiCapture));Picture p=pictures.get(name);if(p==null){p=new Picture(resources.image(name));pictures.put(name,p);loadedImages.add(name);}return p;}
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
    private void text(Graphics2D g,String s,int x,int y,int leading,int style,int w){g.setFont(new Font("Microsoft YaHei",Font.PLAIN,24));int color=(capturePalette==null?host.ui.palette:capturePalette)[style&63];g.setColor(new Color(color==0?0xffffffff:color,true));int at=x,base=y+24;for(int pos=0;pos<s.length();){int cp=s.codePointAt(pos);String c=new String(Character.toChars(cp));pos+=Character.charCount(cp);int cw=g.getFontMetrics().stringWidth(c);if(cp==10||at+cw>x+w){at=x;base+=Math.max(24,leading);if(cp==10)continue;}if((style&0x80)!=0){g.setColor(new Color(0,55,90,210));for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)g.drawString(c,at+dx,base+dy);g.setColor(new Color(color==0?0xffffffff:color,true));}g.drawString(c,at,base);at+=cw;}}
    private void dialogue(Graphics2D g)throws IOException{
        int bodyIndex=host.scene.novelMode?3:0,nameIndex=host.scene.novelMode?4:1;Dc3Ui.Panel p=host.ui.templates[bodyIndex],name=host.ui.templates[nameIndex];panel(g,p);if(!host.dialogue.shownSpeaker.isEmpty())panel(g,name);
        if(p==null)throw new IOException("Original dialogue template missing");
        for(int slot:new int[]{host.scene.layout.face,host.scene.layout.face+1}){Picture face=picture(host.scene.slots[slot].resource);if(face!=null)g.drawImage(face.image,host.ui.messageX+face.raw.x,host.ui.messageY+face.raw.y,null);}
        if(name!=null&&!host.dialogue.shownSpeaker.isEmpty())for(Dc3Ui.Text t:name.texts)if(t.args[2]>0)text(g,host.dialogue.shownSpeaker,t.args[0],t.args[1],t.args[2],t.args[3],width-t.args[0]-30);
        for(Dc3Ui.Text t:p.texts)if(t.args[2]>0)text(g,visibleBody(),t.args[0],t.args[1],t.args[2],t.args[3],width-t.args[0]-60);
        panel(g,host.ui.templates[10]);
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
    public BufferedImage render()throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(Color.BLACK);g.fillRect(0,0,width,height);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);scene(g,host.scene.slots,host.scene.quads,host.scene.camera,host.scene.petals,host.scene.fog,host.scene.clouds,host.scene.environmentTime,true);
            Dc3Transition f=host.scene.fade;
            if(!f.active())clearTransitionBuffers();
            else if(f.mode==3){clearTransitionBuffers();g.setColor(new Color(1f,1f,1f,1-Math.abs(f.progress()*2-1)));g.fillRect(0,0,width,height);}
            else{
                // Repaint an opaque base each frame: a prior mask must not accumulate alpha.
                // Keep only one scratch image, including ordinary crossfades with no mask.
                if(transitionFrame==null)transitionFrame=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                BufferedImage before=transitionFrame;
                try{
                    Graphics2D bg=before.createGraphics();
                    try{bg.setColor(Color.BLACK);bg.fillRect(0,0,width,height);scene(bg,f.from.layers,f.from.quads,f.from.camera,f.from.petals,f.from.fog,f.from.clouds,f.from.environmentTime,false);}finally{bg.dispose();}
                    Picture mask=picture(f.mask);
                    if(mask!=null&&f.mode!=16)transitionMask.apply(before,mask.image,f);
                    else{transitionMask.clear();g.setComposite(AlphaComposite.SrcOver.derive(1-f.progress()));}
                    g.drawImage(before,0,0,null);
                }finally{g.setComposite(AlphaComposite.SrcOver);}
            }
            if(host.credits.active()){drawCredits(g);return image;}
            if(textWaiting()&&!hidden)dialogue(g);
            for(Dc3Scene.Quad q:host.ui.visible())quad(g,q);
            if(host.ui.waiting)for(Dc3Ui.Text t:host.ui.current.texts)if(!t.text.isEmpty())text(g,codec.apply(t.text),t.args[0],t.args[1],32,t.args[2],width-t.args[0]);
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
    private long textDuration()throws IOException{return TextPlayback.duration(resources.profile.id,textSpeed(),body().codePointCount(0,body().length()));}
    public String visibleBody()throws IOException{return body().substring(0,TextPlayback.visible(resources.profile.id,textSpeed(),body(),playbackMillis(),host.dialogue.rendered()));}
    public long playbackMillis(){return textTicket==ticket()?textClock:0;}
    public void playbackMillis(long value){textTicket=ticket();textClock=value;}
    public String body(){return host.dialogue.shownText;}
    public boolean textWaiting(){return host.dialogue.waiting();}
    public boolean menuWaiting(){return host.ui.waiting||host.choosing();}
    public boolean actorVisible(){for(int n=4;n<=13;n++)if(!host.scene.slots[n].resource.isEmpty())return true;return false;}
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
    public float volume(int ch)throws IOException{return (host.read(99,203)==0?0:Math.max(0,Math.min(1,host.read(99,225)/100f)))*(ch==0?host.musicGain:ch==5?host.voiceGain():1)*Math.max(0,Math.min(1,host.read(99,ch==0?7:ch==5?9:8)/100f))*(host.read(99,ch==0?14:ch==5?5:15)==0?0:1);}
    private Path slot(int n)throws IOException{if(n<0||n>255)throw new IOException("Invalid DD save slot "+n);return saveDirectory.resolve("slot-"+n+".sav");}
    public byte[] snapshot()throws IOException{return caller==null?vm.snapshot():caller.clone();}
    public void restore(byte[] bytes)throws IOException{cancelPointer();Dc3Host h=new Dc3Host(resources,codec,true);MesVm m=new MesVm(resources.scripts(),h,"START");m.restore(bytes);h.gallery.recover(h);Dc3State globals=new Dc3State(true);globals.restoreSettings(host.state.settingsSnapshot());globals.mergeGallery(h.state);h.state.restoreSettings(globals.settingsSnapshot());clearTransitionBuffers();host=h;host.settingsLoader=this::reloadSettings;host.captureRenderer=captures;vm=m;host.ui.pressed=-1;savedAutoGeneration=h.autosaveGeneration;textTicket=-1;historyHover=-1;hidden=false;caller=null;scanSaves();persist();}
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
