package org.circus.runtime;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Real selected-document import, backups and original galleries; all destinations isolated. */
public final class Dc3ProgressImportInstrumentation extends Instrumentation {
    private Bundle args;private int checks;private Path out;private final StringBuilder log=new StringBuilder();
    public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);checks++;log.append("PASS ").append(why).append('\n');}
    public void onStart(){Bundle result=new Bundle();String previous=System.getProperty("dc.saveRoot");try{
        out=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"wy-pp-native-import-");Path files=getTargetContext().getFilesDir().toPath(),pack=files.resolve(args.getString("pack")).normalize();if(!pack.startsWith(files))throw new IOException("Invalid audit pack");
        GameResources resources=new GameResources(RangeStorage.replay(pack));Profile profile=GameVersion.detect(resources).profile();check(Dc3Host.supports(profile)&&NativeProgress.supports(profile.id),"game MES independently selects a supported native layout");CircusResources data=new CircusResources(resources,profile);TextCodec codec=GameText.select(resources,profile,"auto","@none");
        SelectedSaveFile.Source source=SelectedSaveFile.open(getTargetContext().getContentResolver(),Uri.parse(args.getString("source")));byte[] original=SelectedSaveFile.read(source,NativeProgress.fileSize(profile.id));
        Path root=out.resolve("phone");NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,root,source);check(preview.additions.total>0&&!Files.exists(root),"renamed native system document previews without writes");
        NativeProgress.Target phone=NativeSaveImport.readTarget(preview);phone.write(99,7,37);phone.write(100,49,27);phone.dd.musicFavorites[6]=1;byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat"),slot=preview.directory.resolve("slot-250.sav");byte[] slotBytes={10,20,30,40};DesktopEngine.atomic(progress,before);DesktopEngine.atomic(slot,slotBytes);
        NativeSaveImport.Result applied=NativeSaveImport.apply(preview);check(applied.added.total>0&&applied.backup!=null&&Arrays.equals(before,Files.readAllBytes(applied.backup)),"production importer writes exact backup before merge");
        NativeProgress.Target merged=NativeSaveImport.readTarget(preview);check(preview.progress.preview(merged).total==0,"all selected native completion and gallery flags persist");check(merged.read(99,7)==37&&merged.read(100,49)==27&&merged.dd.musicFavorites[6]==1,"phone volume, preferences and favorites retained");check(Arrays.equals(slotBytes,Files.readAllBytes(slot)),"phone story slot unchanged");
        byte[] persisted=Files.readAllBytes(progress);NativeSaveImport.Result repeated=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,root,source));check(repeated.added.total==0&&repeated.backup==null&&Arrays.equals(persisted,Files.readAllBytes(progress)),"repeat selected-file import makes no write or backup");
        final byte[][] changing={original.clone()};SelectedSaveFile.Source mutable=new SelectedSaveFile.Source(){public String name(){return "system.data";}public byte[] read(int limit){return changing[0].clone();}};NativeSaveImport.Preview changed=NativeSaveImport.prepareFile(data,codec,out.resolve("changed"),mutable);changing[0][0]^=1;boolean rejected=false;try{NativeSaveImport.apply(changed);}catch(IOException expected){rejected=true;}check(rejected&&!Files.exists(changed.directory),"source changed after preparation is rejected without destination writes");
        System.setProperty("dc.saveRoot",root.toString());log.append(new Dc3ProgressGalleryAudit(new DesktopDd(data,codec,false),out.resolve("galleries")).run(original));
        check(Arrays.equals(original,SelectedSaveFile.read(source,NativeProgress.fileSize(profile.id))),"selected native document remains byte-identical");check(Arrays.equals(slotBytes,Files.readAllBytes(slot)),"gallery navigation leaves phone story slot unchanged");
        log.append("PROFILE ").append(profile.id).append(" COUNTS ").append(preview.progress.source).append("\nIMPORT CHECKS ").append(checks).append('\n');
        Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log+"OUTPUT "+out+"\n");finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');try{if(out!=null)Files.write(out.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(IOException ignored){}result.putString("stream",log.toString());finish(0,result);}finally{if(previous==null)System.clearProperty("dc.saveRoot");else System.setProperty("dc.saveRoot",previous);}}
}
