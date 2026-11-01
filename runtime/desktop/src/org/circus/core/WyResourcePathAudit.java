package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Reported W.Y. chapter, actual script bytes, rendering and save/load across the failing pose. */
public final class WyResourcePathAudit {
    private final DesktopDd e;private final Path out;private final StringBuilder log=new StringBuilder();private int checks;
    public WyResourcePathAudit(DesktopDd e,Path out){this.e=e;this.out=out;}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why+" at "+e.scriptName()+"@"+Integer.toHexString(e.scriptOffset()));checks++;log.append("PASS ").append(why).append('\n');}
    private List<String> actions(){List<String>a=new ArrayList<>();for(DesktopEngine.Control c:e.controls())a.add(c.action);return a;}
    private void tick()throws Exception{for(int c=0;c<6;c++)e.audioCompleted(c,e.audioGeneration(c));e.mediaCompleted(e.ticket());e.tick(1000);}
    private void shot(String name)throws Exception{BufferedImage b=e.render();try(OutputStream s=Files.newOutputStream(out.resolve(name+".png"))){ImageIO.write(b,"png",s);}finally{b.flush();}}
    private boolean samePose(String canonical,String repeated)throws IOException{Crx.Image a=e.resources.image(canonical),b=e.resources.image(repeated);return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height&&Arrays.equals(a.argb(),b.argb());}
    public String run()throws Exception{
        Files.createDirectories(out);try{
            MesScript chapter=e.resources.scripts().script("WY_2_1226_A1_SRR");MesScript.Record pose=chapter.at(0xe1a4);String repeated=TextCodec.cp932(pose.bytes),canonical=repeated.replace("\\\\","\\");
            check(e.resources.profile.id.equals("dc3wy-a09f")&&e.resources.profile.operation(pose.opcode).equals("image.load")&&repeated.contains("\\\\"),"original reported instruction contains the repeated separator");
            check(samePose(canonical,repeated),"reported pose equals the single-separator resource pixel for pixel");
            for(int n=0;n<2000;n++){tick();if(e.menuWaiting()){List<String> menus=actions();if(menus.contains("start")||menus.contains("sce_start"))break;if(menus.contains("window"))e.choose("window");else if(menus.contains("03"))e.choose("03");else throw new IOException("Unexpected startup "+menus);}}
            check(e.menuWaiting(),"original START initializes title and dialogue UI");e.host.enterSystem();e.vm().enterSystem("WY_2_1226_A1_SRR");
            int lines=0,after=0;boolean found=false;String previous="";
            for(int n=0;n<20000&&after<12;n++){
                tick();if(!e.scriptName().equalsIgnoreCase("WY_2_1226_A1_SRR"))throw new IOException("Reported chapter exited before target");
                if(e.textWaiting()){
                    String where=e.scriptName()+":"+e.scriptOffset();if(!where.equals(previous)){
                        previous=where;lines++;BufferedImage image=e.render();image.flush();
                        if(e.body().contains("夏露露松了一口气")){found=true;log.append("REPORTED_TEXT ").append(e.body()).append(" @").append(Integer.toHexString(e.scriptOffset())).append('\n');shot("reported-line");}
                        if(found&&e.scriptOffset()>=0xe24c){after++;if(after==1){shot("fixed-pose");Path save=out.resolve("pose.sav");String text=e.body();int pc=e.scriptOffset();BufferedImage before=e.render();e.save(save);e.restore(save);BufferedImage restored=e.render();try{check(e.body().equals(text)&&e.scriptOffset()==pc&&Arrays.equals(before.getRGB(0,0,e.width,e.height,null,0,e.width),restored.getRGB(0,0,e.width,e.height,null,0,e.width)),"save/load across the reported pose retains exact dialogue, PC and pixels");}finally{before.flush();restored.flush();}}}
                    }
                    e.vm().signalHost(e.ticket(),MesVm.HostSignal.TEXT_COMPLETE);e.click(50,50,false);
                }else if(e.menuWaiting()){throw new IOException("Unexpected chapter choice "+actions());}else if(e.waiting())e.click(50,50,false);
            }
            check(found&&after==12,"reported Chinese line and twelve following dialogue waits render without path errors");
            log.append("CHAPTER ").append(chapter.sha256).append(" LINES ").append(lines).append(" CHECKS ").append(checks).append("\nMedia completion and elapsed time simulated; original script/render/save paths used.\n");return log.toString();
        }catch(Exception failure){log.append("FAIL ").append(failure).append('\n');throw failure;}finally{Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));}
    }
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args[1]);System.setProperty("dc.saveRoot",out.resolve("saves").toAbsolutePath().toString());RangeStorage recording=RangeStorage.recording(new LocalGameStorage(Paths.get(args[0])));
        try{GameResources files=new GameResources(recording,"DC3WY_CN.exe");Profile p=GameVersion.detect(files).profile();TextCodec codec=GameText.select(files,p,"auto","@none");CircusResources data=new CircusResources(files,p);ExecutableData.validate(data,codec);System.out.print(new WyResourcePathAudit(new DesktopDd(data,codec,true),out).run());}finally{recording.write(Paths.get(args[2]));}
    }
}
