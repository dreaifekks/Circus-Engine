package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Native progress must survive a runtime restart and expose original gallery entries. */
public final class Dc3ProgressGalleryAudit {
    private final DesktopDd e;private final Path out;private int checks;private final StringBuilder log=new StringBuilder();
    public Dc3ProgressGalleryAudit(DesktopDd e,Path out){this.e=e;this.out=out;}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why+" at "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset()));checks++;log.append("PASS ").append(why).append('\n');}
    private List<String> actions(){List<String> a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void menu()throws Exception{for(int n=0;n<2000;n++){for(int c=0;c<6;c++)e.audioCompleted(c,e.audioGeneration(c));e.mediaCompleted(e.ticket());e.tick(1000);if(e.menuWaiting())return;e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.vm().signalHost(e.ticket(),MesVm.HostSignal.ADVANCE);}throw new IOException("Menu timeout "+actions());}
    private void choose(String action)throws Exception{e.choose(action);menu();}
    private void shot(String name)throws Exception{BufferedImage image=e.render();try(OutputStream stream=Files.newOutputStream(out.resolve(name+".png"))){ImageIO.write(image,"png",stream);}finally{image.flush();}}
    private boolean unlocked(String prefix){for(DesktopEngine.Control c:e.controls())if(c.action.matches(prefix+"[0-9]+"))return true;for(ScriptList.Item item:e.host.list.items)if(item.action.matches(prefix+"[0-9]+"))return true;return false;}
    public String run(byte[] source)throws Exception{
        Files.createDirectories(out);NativeProgress progress=NativeProgress.parse(e.resources.profileId,source);try{
            check(progress.preview(e.resources.profileId,e.host.state).total==0,"runtime restart loads every imported unlock");
            byte[] early=new MesVm(e.resources.scripts(),new Dc3Host(e.resources,e.codec,true),"START").snapshot();e.restore(early);
            check(progress.preview(e.resources.profileId,e.host.state).total==0,"loading an old phone snapshot retains imported unlocks");
            for(int n=0;n<12;n++){menu();List<String> a=actions();if(a.contains("start")||a.contains("sce_start"))break;if(a.contains("window"))e.choose("window");else if(a.contains("03"))e.choose("03");else throw new IOException("Unresolved title "+a);}
            check(actions().contains("start")||actions().contains("sce_start"),"original title reached after import");shot("title");
            if(actions().contains("extra"))choose("extra");byte[] extra=e.snapshot();log.append("EXTRA ").append(actions()).append('\n');
            choose("cgmode");check(unlocked("cg"),"original CG script exposes imported unlocked entries");shot("cg");
            e.restore(extra);choose("scemode");check(unlocked("sce"),"original scenario script exposes imported unlocked entries");shot("scene");
            e.restore(extra);choose("musmode");check(e.menuWaiting(),"original music gallery opens with imported flags");shot("music");
            check(progress.preview(e.resources.profileId,e.host.state).total==0,"gallery navigation does not erase native progress");
            log.append("SOURCE ").append(progress.sourceSummary).append("\nCHECKS ").append(checks).append('\n');return log.toString();
        }catch(Exception failure){log.append("FAIL ").append(failure).append('\n');throw failure;}
        finally{Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("GAME NATIVE_SYSTEM OUTPUT PACK");Path out=Paths.get(args[2]);System.setProperty("dc.saveRoot",out.resolve("saves").toAbsolutePath().toString());RangeStorage storage=RangeStorage.recording(new LocalGameStorage(Paths.get(args[0])));
        try{GameResources files=new GameResources(storage);Profile profile=GameVersion.detect(files).profile();CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.select(files,profile,"auto","@none");byte[] nativeBytes=Files.readAllBytes(Paths.get(args[1]));NativeProgress p=NativeProgress.parse(profile.id,nativeBytes);NativeProgress.Target target=new NativeProgress.Target(data,codec);p.apply(target);DesktopEngine.atomic(DesktopEngine.mesSaveDirectory(out.resolve("saves"),data).resolve("progress.dat"),target.save());System.out.print(new Dc3ProgressGalleryAudit(new DesktopDd(data,codec,false),out).run(nativeBytes));}
        finally{storage.write(Paths.get(args[3]));}
    }
}
