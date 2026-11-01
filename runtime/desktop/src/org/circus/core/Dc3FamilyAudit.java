package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Original startup, translated loose resources and system menus; isolated saves only. */
public final class Dc3FamilyAudit {
    private final DesktopDd e;private final Path out;private final StringBuilder log=new StringBuilder();
    private final Set<String> sounds=new HashSet<>();private int checks,lines;
    public Dc3FamilyAudit(DesktopDd e,Path out){this.e=e;this.out=out;}
    private void check(boolean ok,String message)throws IOException{if(!ok)throw new IOException(message+" at "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset()));checks++;log.append("PASS ").append(message).append('\n');}
    private List<String> actions(){List<String> result=new ArrayList<>();for(DesktopEngine.Control c:e.controls())result.add(c.action);return result;}
    private void tick()throws Exception{
        for(int ch=0;ch<6;ch++){if(e.audioActive(ch)&&!e.audio(ch).isEmpty()&&sounds.add(e.audio(ch)))e.resources.audio(e.audio(ch));e.audioCompleted(ch,e.audioGeneration(ch));}
        if(!e.movie().isEmpty()){e.resources.files.slice(e.movie(),0,4);e.mediaCompleted(e.ticket());}
        e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.tick(1000);
    }
    private void draw()throws Exception{BufferedImage image=e.render();image.flush();}
    private void shot(String name)throws Exception{BufferedImage image=e.render();try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){ImageIO.write(image,"png",stream);}finally{image.flush();}}
    private void menu()throws Exception{for(int n=0;n<2000;n++){tick();if(e.menuWaiting()){draw();return;}if(e.textWaiting())throw new IOException("Story reached before menu");}throw new IOException("Menu timeout");}
    private void story()throws Exception{for(int n=0;n<2000;n++){tick();if(e.textWaiting()){draw();return;}if(e.menuWaiting())throw new IOException("Unexpected menu "+actions());}throw new IOException("Story timeout");}
    public String run()throws Exception{
        Files.createDirectories(out);try{
            int patchedScripts=0,patchedImages=0;
            for(String path:new TreeSet<>(e.resources.files.paths()))if(path.startsWith("cn_data/")){
                if(path.endsWith(".mes")){new MesScript(path,e.resources.files.read(path,64*1024*1024),e.resources.profile);patchedScripts++;}
                else if(path.endsWith(".crx")){Crx.plain(e.resources.files.read(path,128*1024*1024));patchedImages++;}
            }
            check(patchedScripts>0&&patchedImages>0,"complete loose translation script/image corpus parses and decodes");
            log.append("PATCH scripts=").append(patchedScripts).append(" images=").append(patchedImages).append('\n');
            boolean title=false,actor=false;String last="";
            for(int n=0;n<6000&&(lines<24||!actor);n++){
                tick();
                if(e.menuWaiting()){
                    List<String> a=actions();String action=null;
                    if(a.contains("start")||a.contains("sce_start")){check(!title,"original title reached once");title=true;shot("title");}
                    for(String candidate:new String[]{"window","03","sce_start","start","menu01","sel01","sceselect01"})if(a.contains(candidate)){action=candidate;break;}
                    if(action==null)throw new IOException("Unresolved startup menu "+a);e.choose(action);
                }else if(e.textWaiting()){
                    String location=e.scriptName()+":"+e.scriptOffset();if(!location.equals(last)){last=location;lines++;draw();if(lines==1){log.append("FIRST_TEXT ").append(e.body()).append('\n');shot("first-text");}if(e.actorVisible())actor=true;}
                    if(lines<24||!actor)e.click(50,50,false);
                }else if(e.waiting())e.click(50,50,false);
            }
            log.append("STATE ").append(e.vm().status()).append(" lines=").append(lines).append(" remaining=").append(e.host.remainingMillis()).append(" waiting=").append(e.host.waiting()).append(" movie=").append(e.movie()).append(" body=").append(e.body()).append('\n');
            check(title&&lines>=24,"original title leads to at least 24 dialogue waits");check(actor,"original character appears");shot("story");
            String body=e.body(),script=e.scriptName();int pc=e.scriptOffset();Path saved=out.resolve("story.sav");BufferedImage before=e.render();e.save(saved);e.restore(saved);BufferedImage after=e.render();
            try{check(Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),after.getRGB(0,0,e.width,e.height,null,0,e.width)),"story save restores exact pixels");}finally{before.flush();after.flush();}
            e.click(1,1,true);menu();check(e.scriptName().equalsIgnoreCase("ADVMENU"),"right click opens original ADV menu");shot("advmenu");
            e.choose("save");menu();check(e.host.ui.gridWaiting&&e.host.ui.saveGrid,"original save grid opens");shot("save");
            int target=e.host.gridSlot(0);Path nativeSlot=e.saveDirectory.resolve("slot-"+target+".sav");Files.deleteIfExists(nativeSlot);e.choose("@slot:0");
            for(int n=0;n<200&&!Files.isRegularFile(nativeSlot);n++){
                tick();if(e.menuWaiting()&&!e.host.ui.gridWaiting){boolean confirmed=false;for(String a:new String[]{":y","yes","ok","y"})if(actions().contains(a)){e.choose(a);confirmed=true;break;}if(!confirmed)throw new IOException("Unresolved save confirmation "+actions());}
            }
            check(Files.isRegularFile(nativeSlot),"original save grid writes selected slot");
            e.restore(nativeSlot);check(body.equals(e.body())&&script.equals(e.scriptName())&&pc==e.scriptOffset(),"original slot contains the story caller rather than the SAVE script");
            e.restore(saved);story();check(body.equals(e.body())&&script.equals(e.scriptName())&&pc==e.scriptOffset(),"return to saved story preserves dialogue and PC");
            e.action(":backlog");menu();check(e.host.history.active&&!e.host.history.entries.isEmpty(),"original backlog contains actual dialogue");shot("backlog");
            e.click(1,1,true);story();check(body.equals(e.body()),"backlog return preserves story");
            check(!sounds.isEmpty(),"original audio resources decode");
            log.append("PROFILE ").append(e.resources.profileId).append(" ENCODING ").append(e.codec.charset.name()).append(" CHECKS ").append(checks).append(" LINES ").append(lines).append('\n');
        }catch(Exception ex){log.append("FAIL ").append(ex).append('\n');throw ex;}
        finally{Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));}
        return log.toString();
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("GAME OUTPUT EXE RANGE_PACK");Path out=Paths.get(args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toAbsolutePath().toString());
        RangeStorage recording=RangeStorage.recording(new LocalGameStorage(Paths.get(args[0])));
        try{GameResources files=new GameResources(recording,args[2]);Profile profile=GameVersion.detect(files).profile();TextCodec codec=GameText.select(files,profile,"auto","@none");CircusResources data=new CircusResources(files,profile);ExecutableData.validate(data,codec);System.out.println(new Dc3FamilyAudit(new DesktopDd(data,codec,true),out).run());}
        finally{recording.write(Paths.get(args[3]));}
    }
}
