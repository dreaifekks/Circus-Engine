package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Same dependency-free regression suite runs on desktop JVM and Android ART. */
public final class CoreTests {
    public interface Assets {byte[] read(String name)throws IOException;}
    private static int assertions;
    private static void check(boolean test,String message){assertions++;if(!test)throw new AssertionError(message);}
    private interface Action {void run()throws Exception;}
    private static void reject(Action a,String name)throws Exception{boolean failed=false;try{a.run();}catch(IOException e){failed=true;}check(failed,name);}
    private static Archive archive(byte[] data)throws IOException{return new Archive(new Archive.Source(){public long size(){return data.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,data.length);return Arrays.copyOfRange(data,(int)p,(int)p+n);}});}
    private static byte[] words(int op,int...args)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(op);for(int a:args){out.write(a);out.write(a>>8);}return out.toByteArray();}
    private static byte[] mes(int version,byte[]...blocks)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{0,0,0,0,(byte)version,(byte)(version>>8),0});for(byte[] b:blocks)out.write(b);return out.toByteArray();}
    private static byte[] textRecord(int opcode,String text)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(opcode);for(byte v:text.getBytes(Binary.SJIS)){int c=v&255;b.write(((c==32?36:c)-32)&255);}b.write(0);return b.toByteArray();}
    private static MesVm machine(byte[] code,String profile)throws IOException{return new MesVm(name->new MesScript(name,code,Profiles.get(profile)),new DemoHost(),"test");}
    private static byte[] names()throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int stride:new int[]{32,48,36,32,32}){out.write(new byte[]{1,0,0,0});byte[] row=new byte[stride];row[0]='A';if(stride>32)row[32]=42;out.write(row);}return out.toByteArray();}
    private static byte[] compressedSilence(byte[] spectrum)throws IOException{ByteArrayOutputStream packed=new ByteArrayOutputStream();try(java.util.zip.DeflaterOutputStream z=new java.util.zip.DeflaterOutputStream(packed)){z.write(spectrum);}java.nio.ByteBuffer h=java.nio.ByteBuffer.allocate(32+packed.size()).order(java.nio.ByteOrder.LITTLE_ENDIAN);h.putInt(0x4d435058).putInt(882).putInt(0x303).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16).putInt(packed.size()).put(packed.toByteArray());return h.array();}
    public static String run(Assets assets)throws Exception{
        assertions=Dc3rHostTests.run();Profile demo=Profiles.get("circus-demo-v1");
        MesVm.Library lib=name->new MesScript(name,assets.read(name+".mes"),demo);
        DemoHost host=new DemoHost();MesVm vm=new MesVm(lib,host,"main");
        check(vm.run(1000)==MesVm.Status.WAIT_CLICK,"intro wait");check(host.scene.equals("dawn.crx"),"scene event");
        vm.advance();check(vm.run(1000)==MesVm.Status.WAIT_CHOICE,"choice wait");check(host.choices.size()==2,"two choices");check(vm.variables.read(3,1)==0,"byte wrap");
        byte[] before=vm.snapshot();vm.choose(host.choices.get(1).action,1);check(vm.run(1000)==MesVm.Status.WAIT_TIME,"timed wait");
        vm.elapse(200);check(vm.status()==MesVm.Status.WAIT_TIME,"timer remains");vm.elapse(50);check(vm.run(1000)==MesVm.Status.WAIT_CLICK,"route wait");
        check(host.scene.equals("gold.crx")&&vm.variables.read(3,2)==2,"selected branch");byte[] saved=vm.snapshot();
        DemoHost restoredHost=new DemoHost();MesVm restored=new MesVm(lib,restoredHost,"main");restored.restore(saved);
        check(restored.status()==vm.status()&&restored.offset()==vm.offset(),"restore PC/wait");check(restoredHost.scene.equals(host.scene)&&restoredHost.text.equals(host.text),"restore display");
        check(restored.variables.read(3,2)==2,"restore variables");restored.advance();check(restored.run(1000)==MesVm.Status.WAIT_CLICK,"cross-script wait");check(restored.scriptName().equals("ending"),"cross-script target");restored.advance();check(restored.run(1000)==MesVm.Status.HALTED,"end");
        DemoHost blueHost=new DemoHost();MesVm blue=new MesVm(lib,blueHost,"main");blue.restore(before);blue.choose(blueHost.choices.get(0).action,0);blue.run(1000);blue.elapse(250);check(blue.run(1000)==MesVm.Status.WAIT_CLICK&&blue.variables.read(3,2)==1,"alternate branch after restore");
        byte[] damaged=saved.clone();damaged[20]^=1;reject(()->new MesVm(lib,new DemoHost(),"main").restore(damaged),"save corruption rejected");
        MesVm.Library changed=name->{byte[] b=assets.read(name+".mes");b=b.clone();b[b.length-1]=1;return new MesScript(name,b,demo);};
        reject(()->new MesVm(changed,new DemoHost(),"main").restore(saved),"changed script rejected");
        byte[] stop={0,0,0};MesVm unknown=machine(mes(0xa5a8,new byte[]{0x38,0,0},stop),"dc3dd-a5a8");check(unknown.run(100)==MesVm.Status.FAULT&&unknown.offset()==7,"unknown is not NOP");
        MesVm nativeVars=machine(mes(0xa5a8,words(0x7c,3,1,-1,0),words(0x7c,3,1,1,1),stop),"dc3dd-a5a8");check(nativeVars.run(100)==MesVm.Status.HALTED&&nativeVars.variables.read(3,1)==0,"native DD binding");
        MesVm wy=machine(mes(0xa09f,words(0x78,3,1,9,0),stop),"dc3wy-a09f");check(wy.run(100)==MesVm.Status.HALTED&&wy.variables.read(3,1)==9,"native WY distinct binding");
        MesVm special=machine(mes(0xa5a8,words(0x7c,99,14,1,0),stop),"dc3dd-a5a8");check(special.run(100)==MesVm.Status.FAULT,"unimplemented host setter rejected");
        MesVm badIndex=machine(mes(0xa5a8,words(0x7c,0,-1,2,0),stop),"dc3dd-a5a8");check(badIndex.run(100)==MesVm.Status.FAULT,"negative index rejected");
        MesVm falseBranch=machine(mes(0xa5a8,words(0x80,0,1,7,0),new byte[]{2,(byte)200,0},words(0x7c,0,1,99,0),new byte[]{0x1f,1,(byte)200},words(0x7c,0,2,23,0),stop),"dc3dd-a5a8");
        check(falseBranch.run(100)==MesVm.Status.HALTED&&falseBranch.variables.read(0,1)==0&&falseBranch.variables.read(0,2)==23,"false branch uses signed block ID");
        MesVm block=machine(mes(0xa5a8,new byte[]{0x1f,0,(byte)200},words(0x7c,0,1,99,0),new byte[]{0x1f,1,(byte)200},stop),"dc3dd-a5a8");check(block.run(100)==MesVm.Status.HALTED&&block.variables.read(0,1)==0,"unconditional signed block ID");
        MesVm indirect=machine(mes(0xa5a8,words(0x7c,3,2,9,0),words(0x80,3,2,0x302,0x80),new byte[]{2,1,0},words(0x7c,0,0,1,0),new byte[]{0x1f,1,1},stop),"dc3dd-a5a8");check(indirect.run(100)==MesVm.Status.HALTED&&indirect.variables.read(0,0)==1,"indirect comparison bank/index");
        MesVm sum=machine(mes(0xa5a8,words(0x7c,0,1,200,0),words(0x7c,0,2,100,0),words(0x7e,0,3,0,0),words(0x81,0,1,0,0),words(0x82,0,2,0,0),new byte[]{2,0,0},stop),"dc3dd-a5a8");check(sum.run(100)==MesVm.Status.HALTED&&sum.variables.read(0,3)==44,"sum consumes raw list terms and wraps");
        MesVm extended=machine(mes(0xa5a8,words(0x7c,0,1,42,0),words(0x71,9,8,7,6),stop),"dc3dd-a5a8");check(extended.run(1)==MesVm.Status.RUNNING&&extended.offset()==25&&extended.variables.read(0,1)==42,"extension consumed once with owner");
        byte[] loopLabel={0x47,'L',0},loopJump={0x50,'L',0};MesVm loop=machine(mes(0xa5a8,loopLabel,loopJump,stop),"dc3dd-a5a8");check(loop.run(1000)==MesVm.Status.RUNNING,"instruction budget yields infinite loop");
        MesScript wyScan=new MesScript("wy-scan",mes(0xa09f,new byte[]{(byte)0x80,1,2,0x1e,1,4,0,0,0},new byte[]{0x1e,1,4},stop),Profiles.get("dc3wy-a09f"));
        reject(()->wyScan.scanBlock(7,4),"WY signed-opcode scan rejects resulting non-boundary PC");
        Variables vars=new Variables(new DemoHost());vars.write(0,1,9);vars.appendHistory();vars.write(0,1,20);vars.appendHistory();vars.replayHistory(1);check(vars.read(0,1)==9&&vars.historyCount()==1,"history replays NEW values");reject(()->vars.replayHistory(1001),"history bounds");
        for(int i=0;i<1100;i++)vars.write(i/100,i%100,1);vars.appendHistory();check(vars.historyCount()==1000,"history saturates at native capacity");
        reject(()->Binary.safePath("../escape"),"path traversal");reject(()->new MesScript("short",new byte[3],demo),"truncated header");
        ScriptList nativeList=new ScriptList();nativeList.columns=4;for(int n=0;n<17;n++){nativeList.add(new int[]{n,0,0,0,40,20,19,0},19);nativeList.last().action="item"+n;}
        check(nativeList.clamp(999,12)==8,"list last partial page retains whole rows");check(nativeList.at(8,8,12).id==16&&nativeList.at(8,9,12)==null,"partial page has no phantom entries");
        check(nativeList.scrollAt(980,500,new int[]{968,152,993,428,0,0,0,0},0,12)==-1,"scroll ignores outside bounds");
        Dc2Ui.Quad overlay=ScriptList.sprite(new int[]{0,0,0,0,40,20,19,1});nativeList.last().sprites.add(overlay);check(ScriptList.visible(nativeList.last().sprites,1).size()==2,"hover retains entry base image");
        ByteArrayOutputStream listBytes=new ByteArrayOutputStream();nativeList.save(new DataOutputStream(listBytes));ScriptList restoredList=new ScriptList();restoredList.load(new DataInputStream(new ByteArrayInputStream(listBytes.toByteArray())));check(restoredList.items.size()==17&&restoredList.items.get(16).action.equals("item16"),"list roundtrip preserves labels and entries");
        Dc3State musicState=new Dc3State(true);Variables musicVars=new Variables(musicState);Dc3Music room=new Dc3Music();
        musicState.write(99,246,8);room.command(201,2,musicVars,musicState);check(musicState.musicFavorites[Dc3Music.TRACKS[10]]==1,"favorite uses offset plus row, then catalogue ID");room.command(202,2,musicVars,musicState);check(musicVars.read(3,0)==1,"favorite query result");musicState.write(100,21,1);room.command(200,0,musicVars,musicState);check(room.command(200,1,musicVars,musicState)==Dc3Music.TRACKS[10],"favorite-only playback");room.command(200,2,musicVars,musicState);check(room.command(200,1,musicVars,musicState)==Dc3Music.TRACKS[10],"one favorite wraps without infinite loop");
        byte[] prefs=musicState.settingsSnapshot();Dc3State loadedPrefs=new Dc3State(true);loadedPrefs.restoreSettings(prefs);check(loadedPrefs.musicFavorites[Dc3Music.TRACKS[10]]==1,"favorite survives persistent settings");loadedPrefs.restoreSettings(Arrays.copyOf(prefs,prefs.length-2008));check(loadedPrefs.acceptsSettingsLength(prefs.length-2008),"pre-gallery preferences migrate");
        room.command(201,254,musicVars,musicState);room.command(200,0,musicVars,musicState);check(musicState.read(100,21)==0,"empty favorites disables filter");musicState.write(100,26,2);room.command(200,0,musicVars,musicState);check(!Arrays.equals(room.order,Dc3Music.TRACKS),"original persistent shuffle button feeds legacy service");
        Dc3Host timedGallery=new Dc3Host(null,TextCodec.identity(),true);
        byte[] galleryCode=mes(0xa5a8,new byte[]{0x41,3,'r',0},new byte[]{0x14,0,1},new byte[]{0x47,'r',0},words(0x7c,3,1,42,0),stop);
        MesVm galleryVm=new MesVm(name->new MesScript(name,galleryCode,Profiles.get("dc3dd-a5a8")),timedGallery,"gallery");check(galleryVm.run(50)==MesVm.Status.WAIT_HOST,"gallery waits for input");check(galleryVm.activateMenu(timedGallery.waitTicket(),-1),"gallery right click resolves original action");check(galleryVm.run(50)==MesVm.Status.HALTED&&galleryVm.variables.read(3,1)==42,"gallery right click resumes original label");
        Dc3Host callbackHost=new Dc3Host(null,TextCodec.identity(),true);byte[] callbackCode=mes(0xa5a8,new byte[]{0x5c,'r',0},new byte[]{(byte)Profiles.get("dc3dd-a5a8").opcode("ui.wait"),0,0},new byte[]{0x47,'r',0},words(0x7c,3,2,7,0),stop);
        MesVm callbackVm=new MesVm(name->new MesScript(name,callbackCode,Profiles.get("dc3dd-a5a8")),callbackHost,"callback");check(callbackVm.run(50)==MesVm.Status.WAIT_HOST,"callback is armed at wait");callbackVm.elapse(501);check(callbackVm.run(50)==MesVm.Status.HALTED&&callbackVm.variables.read(3,2)==7,"idle media callback resumes script once");check(callbackHost.pollMenuAction()==null,"callback does not fire outside menu wait");
        Archive pck=archive(assets.read("sample.pck"));check(pck.entries.size()==1&&pck.kind.equals("PCK"),"PCK index");Xpcm sound=Xpcm.decode(pck.read(pck.entries.get(0)));check(sound.channels==1&&sound.sampleRate==22050&&sound.wav().length==sound.payload.length+44,"XPCM PCM and WAV");
        byte[] badPck=assets.read("sample.pck").clone();badPck[4]^=1;reject(()->archive(badPck),"duplicate PCK index mismatch");
        Archive crm=archive(assets.read("sample.crm"));Crx.Resolver resolver=new Crx.Resolver(){public byte[] byOffset(long off)throws IOException{return crm.read(crm.atOffset(off));}public byte[] byName(byte[] n)throws IOException{return crm.read(crm.find(n));}};
        Crx.Image base=Crx.decode(crm.read(crm.entries.get(0)),resolver),diff=Crx.decode(crm.read(crm.entries.get(1)),resolver);
        check(base.argb()[0]==0xff1e140a,"CRX color order");check(diff.argb()[5]==0xff21160b,"CRXD additive patch");check(diff.argb()[0]==base.argb()[0],"CRXD clipping");
        NamesCatalog names=new NamesCatalog(names());check(names.table("images").get(0).name.equals("A")&&names.table("music").get(0).extra[0]==42,"NAMES keeps opaque numeric fields");
        reject(()->new NamesCatalog(Arrays.copyOf(names(),names().length+1)),"unknown NAMES tail rejected");
        GameResources.Storage storage=new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("demo/main.mes","assets/sample.crm","assets/sample.pck"));}
            public byte[] read(String path,int limit)throws IOException{String name=path.substring(path.indexOf('/')+1);byte[] b=assets.read(name);if(b.length>limit)throw new IOException("Too large");return b;}
            public Archive.Source source(String path)throws IOException{byte[] b=read(path,128*1024*1024);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long p,int n)throws IOException{Binary.range(p,n,b.length);return Arrays.copyOfRange(b,(int)p,(int)p+n);}};}
        };
        GameResources resources=new GameResources(storage);check(resources.scripts("DEMO",demo).script("MAIN").profile.demo,"portable library normalizes path case");
        check(resources.image("ASSETS\\sample.crm",crm.entries.get(1).nameBytes).argb()[5]==0xff21160b,"portable archive-backed image");
        check(resources.audio("assets/sample.pck",pck.entries.get(0).nameBytes).channels==1,"portable archive-backed audio");
        check(resources.archive("assets/sample.crm")==resources.archive("ASSETS/SAMPLE.CRM"),"bounded archive index cache");
        reject(()->resources.read("../outside",10),"portable resource path traversal rejected");
        check(Binary.nativeResourcePath("BST\\\\SRR_0F1_013_B").equals("BST/SRR_0F1_013_B"),"Windows image paths collapse repeated native separators");
        check(Binary.nativeResourcePath("VOICE//CHAPTER\\\\LINE").equals("VOICE/CHAPTER/LINE"),"mixed native separators share one canonical relative path");
        reject(()->Binary.safePath("BST//POSE"),"inventory paths still reject empty components");
        for(String unsafe:new String[]{"../outside","BST//../outside","BST\\\\.\\POSE","/root//file","\\\\server\\file","C:\\game\\file","https://example/file","BST/POSE/","BST/\0POSE"})reject(()->Binary.nativeResourcePath(unsafe),"native path still rejects rooted, traversal or invalid filename: "+unsafe);
        GameResources nativeFiles=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/GRP/NAMES.DAT","AdvData/GRP/test.crm","AdvData/SE/test.PCM"));}
            public byte[] read(String path,int limit)throws IOException{return path.equalsIgnoreCase("AdvData/GRP/NAMES.DAT")?new byte[20]:path.equalsIgnoreCase("AdvData/GRP/test.crm")?assets.read("sample.crm"):pck.read(pck.entries.get(0));}
            public Archive.Source source(String path)throws IOException{byte[] b=read(path,128*1024*1024);return new Archive.Source(){public long size(){return b.length;}public byte[] read(long at,int n)throws IOException{Binary.range(at,n,b.length);return Arrays.copyOfRange(b,(int)at,(int)at+n);}};}
        });
        CircusResources nativeData=new CircusResources(nativeFiles,Profiles.get("dc3wy-a09f"));
        String entry=crm.entries.get(1).name;
        check(Arrays.equals(diff.argb(),nativeData.image("BST\\\\"+entry+",test.crm").argb()),"duplicate image-directory separator still decodes exact CRXD pixels");
        check(Arrays.equals(nativeData.audio("SE/test").wav(),nativeData.audio("SE\\\\test").wav()),"duplicate audio separator resolves exact PCM data");
        ByteArrayOutputStream normalizedAudio=new ByteArrayOutputStream();nativeData.writeAudio("SE\\\\test",normalizedAudio);check(Arrays.equals(nativeData.audio("SE/test").wav(),normalizedAudio.toByteArray()),"streaming playback uses the same native path normalization");
        byte[] corrupt=assets.read("dawn.crx");reject(()->Crx.plain(Arrays.copyOf(corrupt,corrupt.length-5)),"truncated zlib");
        int sizeAt=20;if(Binary.u16(corrupt,12)>=3)sizeAt+=4+(int)Binary.u32(corrupt,20)*16;
        byte[] totalSizeCrx=new byte[corrupt.length+4];System.arraycopy(corrupt,0,totalSizeCrx,0,sizeAt);System.arraycopy(corrupt,sizeAt,totalSizeCrx,sizeAt+4,corrupt.length-sizeAt);totalSizeCrx[14]|=16;
        java.nio.ByteBuffer.wrap(totalSizeCrx).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(sizeAt,totalSizeCrx.length);
        check(Arrays.equals(Crx.plain(corrupt).argb(),Crx.plain(totalSizeCrx).argb()),"translated CRX total-file size header preserves exact pixels");
        reject(()->Crx.plain(Arrays.copyOf(totalSizeCrx,totalSizeCrx.length-5)),"translated CRX still rejects truncated stream");
        TextCodec mapping=new TextCodec("漢語\uD83D\uDE00","汉文\uD83D\uDE42");
        check(mapping.decode("漢語".getBytes(Binary.SJIS)).equals("汉文")&&mapping.apply("\uD83D\uDE00").equals("\uD83D\uDE42"),"Unicode codepoint character substitution");
        check(new TextCodec("ab","bc").apply("a").equals("b"),"character substitution is not recursive");
        TextCodec chinese=new TextCodec("","","GBK");check(NativeText.plain("回溯。&f".getBytes(chinese.charset),chinese,"朝仓","清隆",false).equals("回溯。朝仓"),"DD family native prose uses selected GBK encoding and name macros");
        check(NativeText.plain("a".getBytes("US-ASCII"),new TextCodec("ab","bc","GBK"),"","",false).equals("b"),"native text substitution applies exactly once after decoding");
        reject(()->new TextCodec("aa","bc"),"conflicting character mapping rejected");
        reject(()->new TextCodec("a",""),"mapping length mismatch rejected");
        reject(()->new TextCodec("\uD800","a"),"unpaired surrogate rejected");
        reject(()->mapping.decode(new byte[]{(byte)0x81}),"malformed CP932 rejected");
        check(TextCodec.cp932("ソ表".getBytes(Binary.SJIS)).equals("ソ表"),"CP932 trail backslash preserved");
        check(NativeText.plain("&f &n".getBytes(Binary.SJIS),TextCodec.identity(),"山田","太郎",false).equals("山田 太郎"),"native name variables");
        reject(()->NativeText.plain("&n".getBytes(Binary.SJIS),mapping,null,null,false),"missing native name not invented");
        reject(()->NativeText.plain("&f".getBytes(Binary.SJIS),mapping,"&f",null,false),"recursive name bounded");
        reject(()->NativeText.plain("A@w1B".getBytes(Binary.SJIS),mapping,null,null,false),"inline performance control not discarded");
        check(NativeText.plain("さくら２".getBytes(Binary.SJIS),TextCodec.identity(),null,null,true).equals("さくら"),"native speaker alias");
        check(NativeText.plain(new byte[0],TextCodec.identity(),null,null,true).equals("さくら"),"native zero-length prefix alias");
        byte[] nativeCode=mes(0xa5a8,textRecord(0x64,"テスト"),textRecord(0x65,"One\nTwo"),new byte[]{6,1,0},textRecord(0x65,"Three"),textRecord(0x65,"Four"),new byte[]{6,0,0},stop);
        MesVm.Library dialogueLib=name->new MesScript(name,nativeCode,Profiles.get("dc3dd-a5a8"));
        DialogueHost dh=new DialogueHost(TextCodec.identity());MesVm dv=new MesVm(dialogueLib,dh,"dialogue");
        check(dv.run(100)==MesVm.Status.WAIT_HOST&&dh.waitReason()==6&&dh.shownSpeaker.equals("テスト")&&dh.shownText.equals("One\nTwo"),"DD text decoding and explicit wait");
        long firstTicket=dh.waitTicket();int atWait=dv.offset();byte[] dialogueSave=dv.snapshot();
        check(dv.signalHost(firstTicket,MesVm.HostSignal.ADVANCE)&&dv.status()==MesVm.Status.WAIT_HOST&&dh.rendered(),"first click only finishes rendering");
        check(dv.offset()==atWait,"text completion does not consume script");
        dv.signalHost(firstTicket,MesVm.HostSignal.ADVANCE);
        check(dv.run(100)==MesVm.Status.WAIT_HOST&&dh.waitReason()==8&&dh.shownText.equals("Three")&&dh.shownSpeaker.isEmpty(),"implicit presentation boundary clears prior speaker");
        int nextBody=dv.offset();check(dialogueLib.script("dialogue").at(nextBody).opcode==0x65,"implicit wait leaves next instruction unconsumed");
        check(!dv.signalHost(firstTicket,MesVm.HostSignal.TEXT_COMPLETE)&&dv.status()==MesVm.Status.WAIT_HOST,"stale presentation callback rejected");
        dv.signalHost(dh.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);
        check(dv.run(100)==MesVm.Status.WAIT_HOST&&dh.waitReason()==6&&dh.shownText.equals("Four"),"implicit wait completes without user click");
        dv.signalHost(dh.waitTicket(),MesVm.HostSignal.TEXT_COMPLETE);dv.signalHost(dh.waitTicket(),MesVm.HostSignal.ADVANCE);
        check(dv.run(100)==MesVm.Status.HALTED,"native dialogue finishes");
        DialogueHost loadedHost=new DialogueHost(TextCodec.identity());MesVm loaded=new MesVm(dialogueLib,loadedHost,"dialogue");loaded.restore(dialogueSave);
        check(loaded.status()==MesVm.Status.WAIT_HOST&&loadedHost.waitReason()==6&&!loadedHost.rendered()&&loaded.offset()==atWait&&loadedHost.shownText.equals("One\nTwo"),"restore native display and pending presentation");
        check(Arrays.equals(dialogueSave,loaded.snapshot()),"native wait snapshot byte exact");
        reject(()->new MesVm(dialogueLib,new DialogueHost(mapping),"dialogue").restore(dialogueSave),"changed font mapping invalidates saved display state");
        byte[] skipCode=mes(0xa5a8,textRecord(0x65,"A"),new byte[]{6,0,0},words(0x7c,0,1,7,0),stop);
        DialogueHost skipHost=new DialogueHost(TextCodec.identity());skipHost.skipNextMessageWait=true;MesVm skip=new MesVm(name->new MesScript(name,skipCode,Profiles.get("dc3dd-a5a8")),skipHost,"skip");
        check(skip.run(100)==MesVm.Status.HALTED&&!skipHost.skipNextMessageWait&&skip.variables.read(0,1)==7,"one-shot wait gate consumes once");
        DialogueHost suppressHost=new DialogueHost(TextCodec.identity());suppressHost.suppressMessageWait=true;suppressHost.skipNextMessageWait=true;
        MesVm suppress=new MesVm(name->new MesScript(name,skipCode,Profiles.get("dc3dd-a5a8")),suppressHost,"skip");
        check(suppress.run(100)==MesVm.Status.HALTED&&suppressHost.skipNextMessageWait,"suppression preserves one-shot field");
        DialogueHost slicedHost=new DialogueHost(TextCodec.identity());byte[] slicedCode=mes(0xa5a8,textRecord(0x65,"Sliced"),stop);MesVm sliced=new MesVm(name->new MesScript(name,slicedCode,Profiles.get("dc3dd-a5a8")),slicedHost,"slice");
        check(sliced.run(1)==MesVm.Status.RUNNING&&slicedHost.backlog.isEmpty(),"text opcode alone does not fabricate click wait");
        check(sliced.run(1)==MesVm.Status.WAIT_HOST&&slicedHost.waitReason()==8,"implicit boundary survives scheduler budget");
        sliced.signalHost(slicedHost.waitTicket(),MesVm.HostSignal.ADVANCE);check(sliced.run(1)==MesVm.Status.HALTED,"implicit fast-forward completes presentation without second click");
        MesVm badText=new MesVm(name->new MesScript(name,mes(0xa5a8,textRecord(0x65,"@mu1"),stop),Profiles.get("dc3dd-a5a8")),new DialogueHost(TextCodec.identity()),"bad-text");
        check(badText.run(100)==MesVm.Status.FAULT&&badText.offset()==7&&badText.error().contains("@mu"),"native unimplemented inline control preserves fault PC");
        check(Profiles.get("dc3wy-a09f").operation(0x65).equals("unknown"),"DD dialogue not guessed into WY binding");
        Dc3State state=new Dc3State();state.write(96,399,257);check(state.read(96,399)==1,"special byte bank wraps");reject(()->state.read(96,400),"special bank read bounds");state.write(96,400,9);
        state.voiceCount=2;state.write(94,1,200);state.write(94,2,20);check(state.read(94,1)==100&&state.read(94,2)==-1,"voice bank uses registered character count");state.write(99,245,255);check(state.read(99,245)==-1,"choice sentinel conversion");
        Dc3Host nativeHost=new Dc3Host(null,TextCodec.identity());
        byte[] choiceCode=mes(0xa5a8,new byte[]{9,1,0},new byte[]{0x57,'L',0},textRecord(0x66,"Left"),new byte[]{0x57,'R',0},textRecord(0x66,"Right"),new byte[]{10,0,0},new byte[]{0x47,'L',0},words(0x7c,0,0,11,0),stop,new byte[]{0x47,'R',0},words(0x7c,0,0,22,0),stop);
        MesVm.Library choiceLib=n->new MesScript(n,choiceCode,Profiles.get("dc3dd-a5a8"));MesVm choiceVm=new MesVm(choiceLib,nativeHost,"choice");
        check(choiceVm.run(100)==MesVm.Status.WAIT_CHOICE&&nativeHost.choices().size()==2,"native choice consumes encoded captions");check(nativeHost.choices().get(1).caption.equals("Right"),"native caption decoding");
        byte[] choiceSave=choiceVm.snapshot();reject(()->choiceVm.choose(new byte[]{'R'},0),"wrong choice index cannot jump");check(choiceVm.status()==MesVm.Status.WAIT_CHOICE,"rejected choice leaves wait intact");
        for(int i=0;i<2;i++){Dc3Host h=new Dc3Host(null,TextCodec.identity());MesVm m=new MesVm(choiceLib,h,"choice");m.restore(choiceSave);check(Arrays.equals(choiceSave,m.snapshot()),"choice snapshot exact");m.choose(h.choices().get(i).label(),i);check(m.run(100)==MesVm.Status.HALTED&&m.variables.read(0,0)==(i==0?11:22)&&h.state.read(99,245)==i&&!h.choosing(),"native selected branch and result");}
        nativeHost.scene.pending[1]="BG\\test";nativeHost.command("scene.center_background",new int[]{0,0},new byte[0]);nativeHost.command("wait.duration",new int[]{1,0},new byte[0]);long timerTicket=nativeHost.waitTicket();
        check(nativeHost.scene.slots[1].resource.equals("BG\\test")&&nativeHost.scene.slots[1].centered,"queued background publishes centered at wait");check(!nativeHost.signal(timerTicket-1,MesVm.HostSignal.ADVANCE),"stale scheduler event rejected");check(!nativeHost.signal(timerTicket,MesVm.HostSignal.ADVANCE),"fixed timer cannot be skipped");nativeHost.elapse(199);check(nativeHost.waiting(),"native timer not early");nativeHost.elapse(1);check(!nativeHost.waiting(),"native timer completes");
        Dc3Performance performance=new Dc3Performance();byte[] plain=performance.parse("@b2@t3@w1Text".getBytes(Binary.SJIS),0);Dc3Scene scene=new Dc3Scene();scene.slots[19].resource="expression";check(new String(plain,Binary.SJIS).equals("Text"),"inline controls removed before text mapping");performance.elapse(299,scene);check(scene.slots[5].resource.isEmpty(),"inline event waits for deadline");performance.elapse(1,scene);check(scene.slots[5].resource.equals("expression")&&scene.slots[19].resource.isEmpty(),"inline transfer targets selected character");
        Dc3Scene animated=new Dc3Scene();animated.slots[4].resource="actor";animated.slots[4].alpha=0;animated.move(4,100,-20,255,1000);animated.elapse(500);
        check(animated.slots[4].x==50&&animated.slots[4].y==-10&&animated.slots[4].alpha==128&&animated.animationRemaining()==500,"move position and alpha share the native clock");
        animated.record(true);animated.shake(5,0);animated.replay(2);animated.elapse(167);check(animated.displacement(5,0)>7.9,"recorded shake replays");
        animated.camera.start(1,140,-900,600);animated.elapse(500);check(animated.camera.zoom==120&&animated.camera.x==-256&&animated.camera.y==144,"camera interpolation and native bounds");
        animated.petals.start(3,3);animated.clouds=true;animated.slots[27].resource="weather";animated.publish();Dc3Scene.Frame oldFrame=animated.published;animated.elapse(500);
        check(oldFrame.petals.particles.size()==15&&oldFrame.petals.particles.get(0).y!=animated.petals.particles.get(0).y,"published frame owns its weather snapshot");
        animated.fade.start(17,1,1000,"mask",oldFrame);check(animated.fade.maskAlpha(0)==255&&animated.fade.maskAlpha(255)==255,"mask fade begins opaque");animated.elapse(500);check(animated.fade.maskAlpha(32)==32&&animated.fade.maskAlpha(224)==224,"mask uses native blue intensity");
        ByteArrayOutputStream animationBytes=new ByteArrayOutputStream();animated.save(new DataOutputStream(animationBytes));Dc3Scene restoredScene=new Dc3Scene();restoredScene.load(new DataInputStream(new ByteArrayInputStream(animationBytes.toByteArray())));animated.elapse(500);restoredScene.elapse(500);ByteArrayOutputStream a1=new ByteArrayOutputStream(),a2=new ByteArrayOutputStream();animated.save(new DataOutputStream(a1));restoredScene.save(new DataOutputStream(a2));check(Arrays.equals(a1.toByteArray(),a2.toByteArray()),"save mid-camera/weather/fade resumes deterministically");check(animated.fade.maskAlpha(255)==0&&!animated.fade.active(),"mask fade completes transparent");
        Dc3Transition fragments=new Dc3Transition();fragments.start(16,5,1000,"atlas",oldFrame);check(fragments.pieces.size()==627&&fragments.duration>1000,"staggered fragment lifetime covers all tiles");fragments.elapse(fragments.duration);check(!fragments.active(),"fragment transition ends on its computed deadline");
        Crx.Image tintInput=new Crx.Image(0,0,1,1,32,new byte[]{100,80,60,(byte)255},null);int pixel=Dc3Resources.tint(tintInput,'@').argb()[0];check(pixel==0xff2d3c5b,"evening filter integer channel arithmetic");check((tintInput.pixels[0]&255)==100,"color filtering does not mutate cached source pixels");
        Dc3Performance soundMacro=new Dc3Performance();byte[] words=soundMacro.parse("@t3@se8A@mu2B@muf600".getBytes(Binary.SJIS),0);check(new String(words,Binary.SJIS).equals("AB")&&soundMacro.events.size()==3,"inline sound/music macros preserve dialogue");List<Integer> emitted=new ArrayList<>();soundMacro.elapse(299,animated,e->emitted.add(e.kind));check(!emitted.contains(3),"inline SE respects explicit delay");soundMacro.elapse(1,animated,e->emitted.add(e.kind));check(emitted.contains(3),"inline SE dispatches at deadline");
        check(NativeText.plain("literal@b @bt".getBytes(Binary.SJIS),TextCodec.identity(),null,null,false).equals("literal@b @bt"),"incomplete native controls remain literal");
        nativeHost.command("scene.camera",new int[]{1,125,0,0},new byte[0]);nativeHost.command("wait.duration",new int[]{254,0},new byte[0]);check(nativeHost.remainingMillis()==1000,"render wait follows camera duration");nativeHost.elapse(1000);check(!nativeHost.waiting(),"render wait completes");
        byte[] returnCode=mes(0xa5a8,new byte[]{0x4b,'M','A','I','N',0},stop);Dc3Host endingHost=new Dc3Host(null,TextCodec.identity());MesVm endingVm=new MesVm(n->new MesScript(n,returnCode,Profiles.get("dc3dd-a5a8")),endingHost,"ending");check(endingVm.run(10)==MesVm.Status.HALTED&&endingHost.titleRequested,"desktop MAIN transfers yield to the platform title screen");
        Dc3Scene boundary=new Dc3Scene();boundary.slots[4].resource="actor";boundary.slots[36].resource="message-ui";boundary.slots[4].priority=20;boundary.clouds=true;boundary.move(4,77,88,200,500);boundary.resetForTransfer();
        Crx.Image keyImage=new Crx.Image(0,0,1,1,24,new byte[]{(byte)255,0,(byte)255,0},null);check(keyImage.argb()[0]==0xffff00ff&&keyImage.argb(true)[0]==0,"legacy texture color key is optional and preserves raw image decode");
        check(boundary.slots[4].resource.isEmpty()&&boundary.slots[36].resource.equals("message-ui")&&!boundary.clouds,"native script boundary releases scene resources but preserves UI slots");
        check(boundary.slots[1].priority==0&&boundary.slots[4].priority==50&&boundary.slots[4].x==77,"native boundary restores render order and settles motion");
        Dc3Host original=new Dc3Host(null,TextCodec.identity(),true);original.scene.slots[1].y=-576;original.scene.slots[1].scaleX=200;original.command("image.load",new int[]{1},"background".getBytes(Binary.SJIS));
        check(original.scene.slots[1].y==0&&original.scene.slots[1].scaleX==100,"replacement background clears title scroll transform");
        byte[] menuCode=mes(0xa5a8,words(0x6c,10,20,80,50),new byte[]{0x50,'o','k',0},new byte[]{0x16,(byte)255,(byte)255},new byte[]{0x0b,0,0},new byte[]{0x47,'o','k',0},words(0x7c,3,7,9,0),stop);
        MesVm.Library menuLib=n->new MesScript(n,menuCode,Profiles.get("dc3dd-a5a8"));MesVm menuVm=new MesVm(menuLib,original,"menu");
        check(menuVm.run(100)==MesVm.Status.WAIT_HOST&&original.ui.waiting,"original UI wait does not fall through to next label");
        check(original.ui.current.buttons.size()==1&&original.ui.hit(10,20)==0&&original.ui.hit(80,20)==-1,"original script hit rectangle uses exclusive right edge");
        check(!menuVm.activateMenu(original.waitTicket()-1,0),"stale menu tap rejected");
        byte[] menuSave=menuVm.snapshot();Dc3Host savedMenuHost=new Dc3Host(null,TextCodec.identity(),true);MesVm savedMenu=new MesVm(menuLib,savedMenuHost,"menu");savedMenu.restore(menuSave);
        check(Arrays.equals(menuSave,savedMenu.snapshot()),"UI labels/templates and wait ticket survive snapshot");
        check(savedMenu.activateMenu(savedMenuHost.waitTicket(),0)&&savedMenu.run(10)==MesVm.Status.HALTED&&savedMenu.variables.read(3,7)==9,"menu tap jumps to exact script label");
        check(original.read(99,21)==0,"fresh original UI has no quick save");original.availableSaves.add(2);check(original.read(99,21)==1,"quick save query delegates to host availability");
        original.ui.current.buttons.clear();original.ui.button(new int[]{1,2,3,4},"any_label");original.ui.store(1,false);original.ui.button(new int[]{5,6,7,8},"another");
        check(original.ui.templates[9].buttons.get(0).action.equals("any_label")&&original.ui.current.buttons.get(0).action.equals("another"),"native panel selector retains independent template");
        Dc3Ui.Button ub=original.ui.current.buttons.get(0);for(int n:new int[]{99,1,2}){Dc3Ui.Sprite sp=new Dc3Ui.Sprite();sp.state=n;sp.quad.resource="atlas";sp.quad.sx=n;ub.sprites.add(sp);}original.ui.waiting=true;
        check(original.ui.visible().get(0).sx==99,"original normal button sprite");original.ui.hovered=0;check(original.ui.visible().get(0).sx==1,"original hover sprite");original.ui.pressed=0;check(original.ui.visible().get(0).sx==2,"original pressed sprite");
        Dc3Host originalTransfer=new Dc3Host(null,TextCodec.identity(),true);check(!originalTransfer.returnToTitle(0,"MAIN",new byte[0]),"original mode executes MAIN instead of platform title interception");
        byte[] backCode=mes(0xa5a8,new byte[]{0x41,3,'b','a','c','k',0},new byte[]{0x0b,0,0},new byte[]{0x47,'b','a','c','k',0},stop);MesVm backVm=new MesVm(n->new MesScript(n,backCode,Profiles.get("dc3dd-a5a8")),originalTransfer,"back");backVm.run(100);
        check(backVm.activateMenu(originalTransfer.waitTicket(),-1)&&backVm.run(100)==MesVm.Status.HALTED,"mobile back dispatches the script's right-click label");
        byte[] uiTextCode=mes(0xa5a8,words(0x81,0,0,0,0),textRecord(0x66,""),words(0x81,10,20,32,1),textRecord(0x66,"Example text"),stop);Dc3Host uiTextHost=new Dc3Host(null,TextCodec.identity(),true);MesVm uiTextVm=new MesVm(n->new MesScript(n,uiTextCode,Profiles.get("dc3dd-a5a8")),uiTextHost,"text");
        check(uiTextVm.run(100)==MesVm.Status.HALTED&&uiTextHost.ui.current.texts.size()==1&&uiTextHost.ui.current.texts.get(0).text.equals("Example text"),"UI encoded text is consumed only for the native nonzero-size case");
        uiTextHost.command("ui.wait",new int[]{0,0},new byte[0]);check(uiTextHost.ui.waiting&&uiTextHost.ui.current.texts.size()==1,"active UI text survives the menu wait for platform rendering");
        Dc3Host options=new Dc3Host(null,TextCodec.identity(),true);Variables optionVars=new Variables(options);options.attach(optionVars);
        options.command("system.command",new int[]{18,0},new byte[0]);options.state.write(100,54,4);optionVars.write(9,91,7);options.command("system.command",new int[]{18,1},new byte[0]);
        check(optionVars.read(3,0)==0&&options.state.read(100,54)==4,"config comparison ignores active tab and ordinary VM variables");
        options.command("system.command",new int[]{18,0},new byte[0]);options.state.write(99,11,17);options.command("system.command",new int[]{18,1},new byte[0]);check(optionVars.read(3,0)==1,"config detects native preference change");
        options.command("system.command",new int[]{18,2},new byte[0]);check(options.state.read(99,11)==17,"completed config comparison disarms rollback");
        options.command("system.command",new int[]{18,0},new byte[0]);options.state.write(99,11,22);options.state.write(99,245,2);ByteArrayOutputStream optionSave=new ByteArrayOutputStream();options.save(new DataOutputStream(optionSave));Dc3Host resumedOptions=new Dc3Host(null,TextCodec.identity(),true);resumedOptions.load(new DataInputStream(new ByteArrayInputStream(optionSave.toByteArray())));resumedOptions.command("system.command",new int[]{18,2},new byte[0]);
        check(resumedOptions.state.read(99,11)==17&&resumedOptions.state.read(99,245)==2,"settings rollback survives snapshot without rewinding live selection");
        Dc3Host confirm=new Dc3Host(null,TextCodec.identity(),true);confirm.ui.button(new int[]{0,0,40,20},"");
        for(int k:new int[]{99,2,2}){Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=k;s.quad.resource="atlas";s.quad.width=40;s.quad.height=20;s.quad.sx=confirm.ui.last().sprites.size()*40;confirm.ui.last().sprites.add(s);}
        confirm.ui.button(new int[]{1,1,10,10},":y");confirm.ui.button(new int[]{11,1,20,10},":n");confirm.ui.button(new int[]{21,1,30,10},":c");for(int k:new int[]{99,1,2}){Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=k;s.quad.resource="check";s.quad.width=s.quad.height=10;s.quad.sx=k;confirm.ui.last().sprites.add(s);}confirm.ui.store(2,false);confirm.ui.button(new int[]{0,0,100,100},"underlay");confirm.ui.actions[0]="yes";confirm.ui.actions[1]="no";
        byte[] confirmCode=mes(0xa5a8,words(0x73,100,200,999,1),new byte[]{0x55,0},new byte[]{0x47,'y','e','s',0},words(0x7c,3,1,1,0),stop,new byte[]{0x47,'n','o',0},words(0x7c,3,1,2,0),stop);
        MesVm.Library confirmLib=n->new MesScript(n,confirmCode,Profiles.get("dc3dd-a5a8"));MesVm confirmVm=new MesVm(confirmLib,confirm,"confirm");
        check(confirmVm.run(100)==MesVm.Status.WAIT_HOST&&confirm.ui.activeConfirmation==1&&confirm.ui.current.quads.size()==2&&confirm.ui.current.quads.get(1).sx==80,"confirmation uses indexed template caption, not a hardcoded dialog");
        check(confirm.ui.hit(102,202)==0&&confirm.ui.hit(2,2)==-1&&confirm.ui.templates[12].buttons.get(1).left==1,"modal origin affects input without moving source template");
        byte[] beforeConfirm=confirmVm.snapshot();long confirmTicket=confirm.waitTicket();check(confirmVm.activateMenu(confirmTicket,2)&&confirm.state.read(93,1)==1&&confirm.state.read(93,2)==1&&confirmVm.status()==MesVm.Status.WAIT_HOST,"confirmation checkbox toggles paired native remember flags without leaving modal");
        check(confirm.ui.visible().size()==4&&confirm.ui.visible().get(3).sx==2,"checked prompt renders independent tick over normal checkbox");confirm.ui.pressed=2;check(confirm.ui.visible().get(2).sx==1&&confirm.ui.visible().get(3).sx==2,"pressed checkbox retains hover label and checked tick");
        check(!confirmVm.activateMenu(confirmTicket,0),"checkbox invalidates earlier input ticket");
        check(confirmVm.activateMenu(confirm.waitTicket(),-1)&&confirmVm.run(100)==MesVm.Status.HALTED&&confirmVm.variables.read(3,1)==2&&confirm.ui.current.buttons.get(0).action.equals("underlay"),"mobile back resolves confirmation cancel and restores underlying panel");
        Dc3Host confirmRestored=new Dc3Host(null,TextCodec.identity(),true);MesVm restoredConfirmVm=new MesVm(confirmLib,confirmRestored,"confirm");restoredConfirmVm.restore(beforeConfirm);check(Arrays.equals(beforeConfirm,restoredConfirmVm.snapshot()),"open confirmation round trips original labels and underlying UI");
        check(restoredConfirmVm.activateMenu(confirmRestored.waitTicket(),0)&&restoredConfirmVm.run(100)==MesVm.Status.HALTED&&restoredConfirmVm.variables.read(3,1)==1,"confirmation yes resolves action slot zero");
        MesVm rememberVm=new MesVm(confirmLib,confirm,"confirm");check(rememberVm.run(100)==MesVm.Status.HALTED&&rememberVm.variables.read(3,1)==1&&!confirm.ui.waiting,"remembered confirmation skips template and jumps to yes label");
        Dc3Host exitHost=new Dc3Host(null,TextCodec.identity(),true);MesVm exitVm=new MesVm(n->new MesScript(n,mes(0xa5a8,new byte[]{0x10,0,0},words(0x7c,3,0,99,0),stop),Profiles.get("dc3dd-a5a8")),exitHost,"exit");check(exitVm.run(100)==MesVm.Status.HALTED&&exitHost.exitRequested&&exitVm.variables.read(3,0)==0,"native game exit cannot fall into subsequent script instructions");
        Dc3Host settingsTest=new Dc3Host(null,TextCodec.identity(),true);settingsTest.attach(new Variables(settingsTest));
        settingsTest.state.write(97,12,1);settingsTest.state.write(100,42,1);settingsTest.state.musicFavorites[2]=1;settingsTest.state.write(99,225,11);settingsTest.command("system.persist",new int[]{20,0},new byte[0]);
        check(settingsTest.state.read(99,225)==100&&settingsTest.state.read(99,203)==1&&settingsTest.state.read(99,7)==65&&settingsTest.state.read(99,11)==49,"DD defaults match native table and complemented transparency");
        check(settingsTest.state.read(97,12)==1&&settingsTest.state.read(100,42)==1&&settingsTest.state.musicFavorites[2]==0,"Default settings preserve story progress while clearing native favorites");
        check(settingsTest.state.read(99,196)==4&&settingsTest.state.read(99,199)==0,"Default UI navigation fields follow native offsets");
        settingsTest.command("audio.restore_volume",new int[]{50,0},new byte[0]);check(settingsTest.state.read(99,7)==32,"Music attenuation uses integer percentage");
        ByteArrayOutputStream attenuated=new ByteArrayOutputStream();settingsTest.save(new DataOutputStream(attenuated));Dc3Host resumedAudio=new Dc3Host(null,TextCodec.identity(),true);resumedAudio.load(new DataInputStream(new ByteArrayInputStream(attenuated.toByteArray())));resumedAudio.command("audio.restore_volume",new int[]{255,0},new byte[0]);check(resumedAudio.state.read(99,7)==65,"Temporary music volume backup survives save/load");
        settingsTest.command("audio.voice",new int[0],"voice/test".getBytes(Binary.SJIS));check(settingsTest.audio[5].isEmpty(),"Voice remains pending before audio commit");settingsTest.command("audio.restore_volume",new int[]{254,0},new byte[0]);check(settingsTest.audio[5].equals("voice/test")&&settingsTest.audioIsActive(5),"Mode 254 submits pending audio without changing volume");
        settingsTest.command("system.persist",new int[]{8,1},new byte[0]);check(settingsTest.read(99,4)==1&&settingsTest.windowRequest==1,"Fullscreen native query follows request");settingsTest.command("system.persist",new int[]{8,0},new byte[0]);check(settingsTest.read(99,4)==0,"Window mode zero is windowed");
        settingsTest.command("system.persist",new int[]{27,0},new byte[0]);check(settingsTest.windowSizeReset,"Window reset reaches platform");
        Dc2History visuals=new Dc2History();visuals.append("speaker","body","voice","file",10);visuals.entries.get(0).scene=new byte[]{1,2,3};visuals.previewEnabled=false;ByteArrayOutputStream visualSave=new ByteArrayOutputStream();visuals.save(new DataOutputStream(visualSave));visuals.saveVisuals(new DataOutputStream(visualSave));Dc2History loadedVisuals=new Dc2History();DataInputStream visualIn=new DataInputStream(new ByteArrayInputStream(visualSave.toByteArray()));loadedVisuals.load(visualIn);loadedVisuals.loadVisuals(visualIn);check(!loadedVisuals.previewEnabled&&Arrays.equals(loadedVisuals.entries.get(0).scene,new byte[]{1,2,3}),"History picture state survives roundtrip");
        check(TextPlayback.visible("dc3dd-a5a8",3,"甲乙丙",0,false)==0&&TextPlayback.visible("dc3dd-a5a8",3,"甲乙丙",17,false)==1,"DD typewriter follows native frame-based speed");
        check(TextPlayback.duration("dc2dm-9d72",0,10)>TextPlayback.duration("dc2dm-9d72",8,10)&&TextPlayback.duration("dc2dm-9d72",11,10)==0,"DM speed slider changes output rate and supports instant display");
        check(TextPlayback.visible("dcpc-2063",0,"A\uD83C\uDF38B",10,false)==3&&TextPlayback.visible("dcpc-2063",1,"abc",0,false)==3,"PC slow/instant modes preserve Unicode code points");
        Dc3Host voices=new Dc3Host(null,TextCodec.identity(),true);voices.attach(new Variables(voices));voices.command("audio.voice_register",new int[0],"rcc".getBytes(Binary.SJIS));voices.write(94,0,25);voices.write(95,0,0);
        check(voices.read(99,7)==65&&voices.read(99,8)==80&&voices.read(99,12)==6&&voices.read(99,26)==3,"Fresh DD settings use the native default table");
        voices.command("choice.begin",new int[]{0,0},new byte[0]);check(voices.read(94,0)==25&&voices.read(95,0)==0,"Choice construction cannot erase the character voice registry");
        voices.command("audio.voice",new int[0],"voice/test_rcc001".getBytes(Binary.SJIS));voices.command("audio.restore_volume",new int[]{254,0},new byte[0]);check(!voices.audioIsActive(5),"Disabled DD character does not start a voice");
        voices.write(95,0,1);voices.command("audio.voice",new int[0],"voice/test_rcc002".getBytes(Binary.SJIS));voices.command("audio.restore_volume",new int[]{254,0},new byte[0]);check(voices.audioIsActive(5)&&voices.voiceGain()==.25f,"DD per-character volume reaches playback gain");
        byte[] voicePrefs=voices.state.settingsSnapshot();voices.write(94,0,100);voices.state.restoreSettings(voicePrefs);check(voices.read(94,0)==25,"DD character preferences persist with global settings");
        voices.write(95,50,0);voices.command("audio.voice",new int[0],"VOICE/SYSTEM/TEST".getBytes(Binary.SJIS));voices.command("audio.restore_volume",new int[]{254,0},new byte[0]);check(voices.audioIsActive(5)&&voices.voiceGain()==1,"DD system announcements bypass other-character mute");
        Dc3Host oldLayout=new Dc3Host(null,TextCodec.identity(),true);ByteArrayOutputStream oldOut=new ByteArrayOutputStream();oldLayout.save(new DataOutputStream(oldOut));byte[] oldBytes=Arrays.copyOf(oldOut.toByteArray(),oldOut.size()-22);oldBytes[3]=0x3c;Dc3Host migratedHost=new Dc3Host(null,TextCodec.identity(),true);migratedHost.load(new DataInputStream(new ByteArrayInputStream(oldBytes)));check(migratedHost.read(99,203)==1&&migratedHost.read(94,50)==100,"Pre-clock 0.9 development snapshot migrates without muting audio");
        byte[] silent=compressedSilence(new byte[8192]);Xpcm zero=Xpcm.decode(silent);check(zero.payload.length==882&&Arrays.equals(zero.payload,new byte[882]),"verified zero-spectrum XPCM transform decodes to exact silence");
        byte[] notSilent=new byte[8192];notSilent[42]=1;check(Binary.hex(java.security.MessageDigest.getInstance("SHA-256").digest(Xpcm.decode(compressedSilence(notSilent)).payload)).equals("442538214e988a84533db39fec0a48c760c7a324ca6c912ad788ecc73de11c33"),"XPCM integer transform matches independent C# reference byte for byte");
        reject(()->Xpcm.decode(compressedSilence(new byte[8191])),"incomplete spectrum rejected");reject(()->Xpcm.decode(Arrays.copyOf(silent,silent.length-1)),"truncated compressed silence rejected");
        assertions+=CaptureTests.run();
        assertions+=CompatibilityTests.run();
        assertions+=PcCompatibilityTests.run();
        assertions+=PcChoiceTests.run();
        assertions+=PcActorLayoutTests.run();
        assertions+=DmDdPaletteTests.run();
        assertions+=PcNativeProgressTests.run();
        assertions+=NativeProgressTests.run();
        assertions+=AudioStreamingTests.run(assets);
        Dc3Host reloading=new Dc3Host(null,TextCodec.identity(),true);Variables transientVars=new Variables(reloading);reloading.attach(transientVars);transientVars.write(3,12,77);
        Dc3State persistedSettings=new Dc3State(true);persistedSettings.write(100,10,1);persistedSettings.write(99,7,37);persistedSettings.write(97,14,1);persistedSettings.write(94,50,22);
        byte[] durable=persistedSettings.settingsSnapshot();int[] reads={0};reloading.settingsLoader=target->{reads[0]++;target.restoreSettings(durable);};
        reloading.write(99,7,99);reloading.command("system.persist",new int[]{26,0},new byte[0]);
        check(reads[0]==1&&reloading.read(99,7)==37&&reloading.read(100,10)==1,"persist 26 reloads stored settings and initialized flag");
        check(reloading.read(97,14)==1&&reloading.read(94,50)==22&&transientVars.read(3,12)==77,"persist 26 retains progress and voice preferences without replacing ordinary variables");
        reloading.settingsLoader=target->{throw new IOException("unreadable settings");};reject(()->reloading.command("system.persist",new int[]{26,0},new byte[0]),"persist 26 storage failure is not ignored");
        assertions+=ResourceSelectionTest.run();
        assertions+=DdGalleryTests.run();
        assertions+=CircusScrVmTests.run();
        assertions+=UnityResourceTests.run();
        assertions+=Dc3rNativeProgressTests.run();
        assertions+=Fsb5VorbisTests.run();
        return "PASS "+assertions+" assertions; self-authored fixtures; DD choices/flow/original UI/animation/macros, WY bindings, DM memory/control and collection detection; archives, CRX/CRXD, audio, save integrity";
    }
    public static void main(String[] args)throws Exception{Path root=Paths.get(args[0]);System.out.println(run(name->Files.readAllBytes(root.resolve(name))));}
}
