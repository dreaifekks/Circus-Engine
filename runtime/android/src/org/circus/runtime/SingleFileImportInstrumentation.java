package org.circus.runtime;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Actual selected-document reads and production import services, away from user save roots. */
public final class SingleFileImportInstrumentation extends Instrumentation {
    private static final String[] IDS={"dcpc-2063","dc2pc-5769","dc2dm-9d72","dc3dd-a5a8","dc3wy-a09f","dc3pp-9872",UnityCircusResources.ID};
    private static final String DEFAULT_TREE="content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FDC2PC";
    private Bundle args;private int checks;private Path output;private Uri selectedTree;private final StringBuilder log=new StringBuilder();
    private interface Action {void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
    private void check(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);checks++;log.append("PASS ").append(why).append('\n');}
    private void reject(Action action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;log.append("REJECT ").append(expected.getMessage()).append('\n');}check(rejected,why);}
    private SelectedSaveFile.Source selected(String file)throws IOException{String document=DocumentsContract.getTreeDocumentId(selectedTree)+"/SingleFileImportChecks/"+file;Uri uri=DocumentsContract.buildDocumentUriUsingTree(selectedTree,document);return SelectedSaveFile.open(getTargetContext().getContentResolver(),uri);}
    private static byte[] fixture(String id)throws IOException{
        if(UnityCircusResources.ID.equals(id))return Dc3rNativeProgressTests.fixture();
        byte[] bytes=new byte[NativeProgress.fileSize(id)];
        if(id.equals("dcpc-2063")){bytes[0x70+31]=1;bytes[0xd4+7]=1;bytes[0x2d4+8]=1;}
        else if(id.equals("dc2pc-5769")){bytes[0x70]=1;bytes[0xd4+7]=1;bytes[0x6b0+8]=1;}
        else if(id.equals("dc2dm-9d72")){bytes[0xe4]=1;bytes[0x148]=2;bytes[0x17a]=4;bytes[0x210]=8;}
        else if(id.equals("dc3dd-a5a8")){bytes[0xcc+100]=1;bytes[0x174+1]=1;bytes[0x304+2]=1;bytes[0x7b4+3]=1;}
        else if(id.equals("dc3wy-a09f")){bytes[0xc0+102]=1;bytes[0x168+1]=1;bytes[0x2f8+2]=1;bytes[0x7a8+3]=1;}
        else if(id.equals("dc3pp-9872")){bytes[0x88]=1;bytes[0xec]=2;bytes[0x107]=4;bytes[0x19d]=8;}
        else throw new IOException("Unknown fixture "+id);
        return bytes;
    }
    private void fixtures()throws Exception{
        output=getTargetContext().getFilesDir().toPath().resolve("single-file-import-fixtures");Files.createDirectories(output);
        for(String id:IDS){byte[] bytes=fixture(id);Files.write(output.resolve(id+".system.data"),bytes);Files.write(output.resolve(id+".unusual"),bytes);}
        Files.write(output.resolve("truncated.bin"),new byte[]{1,2,3});
        try(OutputStream out=Files.newOutputStream(output.resolve("oversized.bin"))){byte[] block=new byte[65536];for(int n=0;n<128;n++)out.write(block);out.write(0);}
        check(true,"self-authored selected-document fixtures exported for copying to emulator Documents/DC2PC/SingleFileImportChecks");
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        if("true".equals(args.getString("fixtures")))fixtures();
        else{
            output=Files.createTempDirectory(getTargetContext().getFilesDir().toPath(),"single-file-import-audit-");selectedTree=Uri.parse(args.getString("selectedTree",DEFAULT_TREE));
            Intent picker=LauncherActivity.progressFilePicker();check(Intent.ACTION_OPEN_DOCUMENT.equals(picker.getAction())&&picker.hasCategory(Intent.CATEGORY_OPENABLE)&&"*/*".equals(picker.getType()),"picker accepts one openable document regardless extension");
            check((picker.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(picker.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0&&!picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false),"picker requests read access only and one file");
            reject(()->SelectedSaveFile.open(getTargetContext().getContentResolver(),Uri.parse("file:///sdcard/untrusted.dat")),"non-content URI rejected");
            String id=args.getString("profile");if(!Arrays.asList(IDS).contains(id))throw new IOException("Specify supported -e profile");
            SelectedSaveFile.Source source=selected(id+".system.data");byte[] original=SelectedSaveFile.read(source,8*1024*1024);
            check(source.name().equals(id+".system.data")&&Arrays.equals(original,fixture(id)),"ContentResolver reads actual renamed selected document and expected bytes");
            if(UnityCircusResources.ID.equals(id))unity(source,original);else mes(id,source,original);
            check(Arrays.equals(original,SelectedSaveFile.read(source,8*1024*1024)),"selected source is byte-identical after all imports");
        }
        log.append("CHECKS ").append(checks).append("\nOUTPUT ").append(output).append('\n');Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));result.putString("stream",log.toString());finish(-1,result);
    }catch(Throwable failure){log.append("FAIL ").append(failure).append('\n');try{if(output!=null)Files.write(output.resolve("result.txt"),log.toString().getBytes("UTF-8"));}catch(Exception ignored){}result.putString("stream",log.toString());finish(0,result);}}
    private void mes(String id,SelectedSaveFile.Source source,byte[] original)throws Exception{
        String tree=args.getString("gameTree"),pack=args.getString("pack");if(tree==null&&pack==null)throw new IOException("Specify gameTree or audit pack");
        Path input=pack==null?null:getTargetContext().getFilesDir().toPath().resolve(pack).normalize();if(input!=null&&!input.startsWith(getTargetContext().getFilesDir().toPath()))throw new IOException("Audit pack escapes app files");
        GameResources files=new GameResources(pack==null?new DocumentTree(getTargetContext().getContentResolver(),Uri.parse(tree)):RangeStorage.replay(input));
        Profile profile=GameVersion.detect(files).profile();check(id.equals(profile.id),"selected game is independently identified from its resources");CircusResources data=new CircusResources(files,profile);TextCodec codec=GameText.detect(files,profile);
        Path root=output.resolve("phone"),fresh=output.resolve("cancelled");NativeSaveImport.Preview abandoned=NativeSaveImport.prepareFile(data,codec,fresh,source);check(abandoned.additions.total>0&&!Files.exists(fresh),"preparation with no apply leaves phone root absent");
        NativeSaveImport.Preview preview=NativeSaveImport.prepareFile(data,codec,root,source);check(preview.storySlots==0,"single selected document does not scan sibling story slots");
        NativeProgress.Target phone=NativeSaveImport.readTarget(preview);phone.write(99,7,37);phone.write(100,49,27);phone.write(97,511,1);if(phone.legacy!=null)phone.legacy.readCheckpoints.add("phone-read-history");if(phone.legacy instanceof PcHost)((PcHost)phone.legacy).givenName="名";if(phone.dd!=null)phone.dd.musicFavorites[6]=1;
        byte[] before=phone.save();Path progress=preview.directory.resolve("progress.dat"),slot=preview.directory.resolve("slot-250.sav");DesktopEngine.atomic(progress,before);byte[] slotBytes={10,20,30,40};DesktopEngine.atomic(slot,slotBytes);
        NativeProgress.Target expected=new NativeProgress.Target(data,codec);expected.load(before);preview.progress.apply(expected);byte[] expectedBytes=expected.save();NativeSaveImport.Result applied=NativeSaveImport.apply(preview);
        check(applied.added.total>0&&applied.backup!=null&&Arrays.equals(before,Files.readAllBytes(applied.backup)),"changed phone progress gets an exact automatic backup");
        check(Arrays.equals(expectedBytes,Files.readAllBytes(progress)),"selected-file result equals native progress conversion with every phone field preserved");
        NativeProgress.Target loaded=NativeSaveImport.readTarget(preview);check(loaded.read(99,7)==37&&loaded.read(100,49)==27&&loaded.read(97,511)==1,"phone preferences and existing gallery progress retained");
        check(preview.progress.preview(loaded).total==0&&Arrays.equals(slotBytes,Files.readAllBytes(slot)),"all selected unlocks persisted while existing story slot stayed unchanged");
        byte[] merged=Files.readAllBytes(progress);NativeSaveImport.Result repeat=NativeSaveImport.apply(NativeSaveImport.prepareFile(data,codec,root,selected(id+".unusual")));
        check(repeat.added.total==0&&repeat.backup==null&&Arrays.equals(merged,Files.readAllBytes(progress)),"same content with a different extension imports idempotently without backup or write");
        for(String invalid:new String[]{"truncated.bin","oversized.bin",id.equals("dcpc-2063")?"dc2pc-5769.system.data":"dcpc-2063.system.data"}){Path rejected=output.resolve("reject-"+invalid);reject(()->NativeSaveImport.prepareFile(data,codec,rejected,selected(invalid)),"invalid selected content rejected: "+invalid);check(!Files.exists(rejected),"invalid source creates no destination: "+invalid);}
        SelectedSaveFile.Source oversize=new SelectedSaveFile.Source(){public String name(){return "provider-ignores-limit";}public byte[] read(int limit){return new byte[limit+1];}};Path rejected=output.resolve("oversize-provider");reject(()->NativeSaveImport.prepareFile(data,codec,rejected,oversize),"unbounded provider return rejected");check(!Files.exists(rejected),"provider size violation causes no destination writes");
        final byte[][] changed={original.clone()};SelectedSaveFile.Source mutable=mutable(source,changed);NativeSaveImport.Preview changedPreview=NativeSaveImport.prepareFile(data,codec,output.resolve("changed"),mutable);changed[0][0]^=1;reject(()->NativeSaveImport.apply(changedPreview),"source changed after preparation rejected");check(!Files.exists(changedPreview.directory),"changed source creates no phone destination");
        NativeSaveImport.Preview corrupt=NativeSaveImport.prepareFile(data,codec,output.resolve("corrupt"),source);Path bad=corrupt.directory.resolve("progress.dat");DesktopEngine.atomic(bad,slotBytes);reject(()->NativeSaveImport.apply(corrupt),"malformed existing phone progress is not replaced");check(Arrays.equals(slotBytes,Files.readAllBytes(bad)),"malformed phone progress retained byte-for-byte");
    }
    private static SelectedSaveFile.Source mutable(SelectedSaveFile.Source source,byte[][] bytes){return new SelectedSaveFile.Source(){public String name(){return source.name();}public byte[] read(int limit){return bytes[0].clone();}};}
    private static byte[] persistent(Dc3rHost host)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();host.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    private void unity(SelectedSaveFile.Source source,byte[] original)throws Exception{
        Path script=getTargetContext().getFilesDir().toPath().resolve(args.getString("script0","dc3r-import-script0"));final byte[] scriptBytes=Files.readAllBytes(script);
        GameResources.Storage storage=new GameResources.Storage(){public Set<String> paths(){return Collections.singleton("StreamingAssets/script0");}public byte[] read(String p,int limit)throws IOException{if(!p.equalsIgnoreCase("StreamingAssets/script0")||scriptBytes.length>limit)throw new IOException("Unexpected resource "+p);return scriptBytes.clone();}public Archive.Source source(String p)throws IOException{if(!p.equalsIgnoreCase("StreamingAssets/script0"))throw new FileNotFoundException(p);return new Archive.Source(){public long size(){return scriptBytes.length;}public byte[] read(long at,int count)throws IOException{Binary.range(at,count,scriptBytes.length);return Arrays.copyOfRange(scriptBytes,(int)at,(int)at+count);}};}};
        UnityCircusResources data=new UnityCircusResources(new GameResources(storage,"@none"));Path root=output.resolve("phone"),cache=output.resolve("cache");
        try(UnitySaveImport.Preview abandoned=UnitySaveImport.prepareFile(data,output.resolve("cancelled"),cache,source)){check(abandoned.additions.total==7&&!Files.exists(abandoned.directory),"valid renamed native global content previews without phone writes");}
        check(!Files.exists(output.resolve("cancelled")),"discarding preparation creates no phone save root");
        Path directory=root.resolve(UnityCircusResources.ID+"-"+data.identity()),progress=directory.resolve("progress.dat"),slot=directory.resolve("slot-250.sav");Dc3rHost phone=new Dc3rHost();phone.writeFlag((99<<16)+7,3);phone.writeFlag((100<<16)+60,0);phone.writeFlag((100<<16)+200,1);phone.viewedScripts.add("phone-existing-read");byte[] before=persistent(phone),slotBytes={10,20,30,40};DesktopEngine.atomic(progress,before);DesktopEngine.atomic(slot,slotBytes);
        try(UnitySaveImport.Preview preview=UnitySaveImport.prepareFile(data,root,cache,source)){check(preview.additions.total==7&&preview.slots.isEmpty(),"one selected global document imports progress only");Dc3rHost expected=UnitySaveImport.target(preview);preview.progress.apply(expected);byte[] expectedBytes=persistent(expected);UnitySaveImport.Result applied=UnitySaveImport.apply(preview,false);check(applied.progressOnly&&applied.progress.total==7&&applied.backup!=null&&Arrays.equals(before,Files.readAllBytes(applied.backup.resolve("progress.dat"))),"selected global import backs up exact previous phone progress");check(Arrays.equals(expectedBytes,Files.readAllBytes(progress)),"converted global import preserves every unrelated phone field");Dc3rHost loaded=UnitySaveImport.target(preview);check(loaded.readFlag((99<<16)+7)==3&&loaded.readFlag((100<<16)+60)==0&&loaded.readFlag((100<<16)+200)==1&&loaded.viewedScripts.contains("phone-existing-read"),"phone settings and earlier completion/read history retained");check(preview.progress.preview(loaded).total==0&&Arrays.equals(slotBytes,Files.readAllBytes(slot)),"selected native unlocks persisted without replacing story slots");}
        byte[] merged=Files.readAllBytes(progress);try(UnitySaveImport.Preview repeated=UnitySaveImport.prepareFile(data,root,cache,selected(UnityCircusResources.ID+".unusual"))){UnitySaveImport.Result result=UnitySaveImport.apply(repeated,false);check(result.progress.total==0&&result.backup==null&&Arrays.equals(merged,Files.readAllBytes(progress)),"same global bytes with another extension are idempotent");}
        for(String invalid:new String[]{"truncated.bin","oversized.bin","dc2pc-5769.system.data"}){Path rejected=output.resolve("reject-"+invalid),badCache=output.resolve("reject-cache-"+invalid);reject(()->{try(UnitySaveImport.Preview ignored=UnitySaveImport.prepareFile(data,rejected,badCache,selected(invalid))){}},"invalid selected global rejected: "+invalid);check(!Files.exists(rejected)&&!Files.exists(badCache),"invalid global creates neither staging nor phone destination: "+invalid);}
        SelectedSaveFile.Source oversize=new SelectedSaveFile.Source(){public String name(){return "provider-ignores-limit";}public byte[] read(int limit){return new byte[limit+1];}};Path rejected=output.resolve("oversize-provider"),badCache=output.resolve("oversize-cache");reject(()->{try(UnitySaveImport.Preview ignored=UnitySaveImport.prepareFile(data,rejected,badCache,oversize)){}},"unbounded global provider return rejected");check(!Files.exists(rejected)&&!Files.exists(badCache),"oversized provider causes no destination or staging writes");
        final byte[][] changed={original.clone()};try(UnitySaveImport.Preview preview=UnitySaveImport.prepareFile(data,output.resolve("changed"),cache,mutable(source,changed))){changed[0][0]^=1;reject(()->UnitySaveImport.apply(preview,false),"selected global changed after preparation rejected");check(!Files.exists(preview.directory),"changed selected global leaves phone destination absent");}
        try(UnitySaveImport.Preview preview=UnitySaveImport.prepareFile(data,output.resolve("corrupt"),cache,source)){Path bad=preview.directory.resolve("progress.dat");DesktopEngine.atomic(bad,slotBytes);reject(()->UnitySaveImport.apply(preview,false),"malformed existing Unity phone progress is not replaced");check(Arrays.equals(slotBytes,Files.readAllBytes(bad)),"corrupt Unity phone progress remains byte-identical");}
    }
}
