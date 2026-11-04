package org.circus.core;

import java.io.*;
import java.util.*;

/** Counterexamples to reusing DD semantics for older CIRCUS engines. No game assets. */
public final class CompatibilityTests {
    private static int checks;
    private static void check(boolean value,String label)throws IOException{checks++;if(!value)throw new IOException(label);}
    private interface Task {void run()throws Exception;}
    private static void reject(Task task,String label)throws Exception{checks++;try{task.run();}catch(IOException expected){return;}throw new IOException(label);}
    private static byte[] word(int opcode,int... args){byte[] bytes=new byte[9];bytes[0]=(byte)opcode;for(int n=0;n<4;n++){bytes[1+2*n]=(byte)args[n];bytes[2+2*n]=(byte)(args[n]>>8);}return bytes;}
    private static byte[] str(int opcode,String value)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(opcode);b.write(value.getBytes(Binary.SJIS));b.write(0);return b.toByteArray();}
    private static byte[] script(int version,byte[]... body)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,0,0,0,(byte)version,(byte)(version>>8),0});for(byte[] record:body)b.write(record);b.write(new byte[3]);return b.toByteArray();}
    private static final class Host implements MesVm.Host {
        final Dc2State state=new Dc2State();
        public int read(int bank,int index)throws IOException{return state.read(bank,index);}
        public void write(int bank,int index,int value)throws IOException{state.write(bank,index,value);}
        public boolean command(String semantic,int[] args,byte[] bytes){if(!semantic.equals("variable.indirect_result"))return false;state.indirectResult(args[0]);return true;}
        public boolean beforeTransfer(int mode,String name){return false;}
        public boolean allowNamedJump(){return true;}
        public void save(DataOutput out)throws IOException{state.save(out);}
        public void load(DataInput in)throws IOException{state.load(in);}
    }
    private static MesVm machine(byte[] bytes,Host host)throws IOException{return new MesVm(n->new MesScript(n,bytes,Profiles.get("dc2dm-9d72")),host,"main");}
    private static final class Storage implements GameResources.Storage {
        final Map<String,byte[]> files=new HashMap<>();int maxRead,wholeReads,readersOpened,readersClosed;long bytesRead,sizeOverride=-1;boolean shortRead,failRead;
        public Set<String> paths(){return files.keySet();}
        public byte[] read(String path,int limit)throws IOException{wholeReads++;byte[] b=files.get(path);if(b.length>limit)throw new IOException("limit");return b.clone();}
        public Archive.Source source(String path){final byte[] b=files.get(path);return new Archive.Source(){
            public long size(){return sizeOverride>=0?sizeOverride:b.length;}
            public byte[] read(long offset,int length)throws IOException{if(failRead)throw new IOException("Provider read failure");Binary.range(offset,length,b.length);maxRead=Math.max(maxRead,length);bytesRead+=length;return Arrays.copyOfRange(b,(int)offset,(int)offset+length-(shortRead?1:0));}
            public Archive.Reader openReader(){Archive.Source source=this;readersOpened++;return new Archive.Reader(){private boolean closed;
                private void requireOpen()throws IOException{if(closed)throw new IOException("Reader already closed");}
                public long size()throws IOException{requireOpen();return source.size();}
                public byte[] read(long offset,int length)throws IOException{requireOpen();return source.read(offset,length);}
                public void close(){if(!closed){closed=true;readersClosed++;}}
            };}
        };}
    }
    private static void boundedVersionReads()throws Exception{
        Storage files=new Storage();byte[] large=new byte[8*1024*1024];large[4]=0x72;large[5]=(byte)0x9d;files.files.put("AdvData/MES/START.MES",large);
        check(GameVersion.detect(new GameResources(files)).profileId.equals("dc2dm-9d72")&&files.wholeReads==0&&files.bytesRead<=14,"Large MES payload is not read during version admission");
        check(files.readersOpened==1&&files.readersClosed==1,"Version admission closes its single source reader");
        files.sizeOverride=64L*1024*1024+1;long readBefore=files.bytesRead;
        reject(()->GameVersion.detect(new GameResources(files)),"Oversized MES rejected before payload read");
        check(files.bytesRead==readBefore&&files.readersOpened==files.readersClosed,"Oversized-file rejection closes the reader without reading data");files.sizeOverride=-1;
        files.files.put("AdvData/MES/START.MES",new byte[7]);reject(()->GameVersion.detect(new GameResources(files)),"Short MES header rejected");
        files.files.put("AdvData/MES/START.MES",new byte[]{-1,-1,-1,-1,0,0,0,0});reject(()->GameVersion.detect(new GameResources(files)),"Unsigned excessive label count rejected");
        files.files.put("AdvData/MES/START.MES",new byte[]{100,0,0,0,0,0,0,0});reject(()->GameVersion.detect(new GameResources(files)),"Label table pointing outside file rejected");
        files.files.put("AdvData/MES/START.MES",script(0x9d72));files.shortRead=true;
        reject(()->GameVersion.detect(new GameResources(files)),"Provider short reads become checked I/O errors");files.shortRead=false;files.failRead=true;
        reject(()->GameVersion.detect(new GameResources(files)),"Provider read failure is propagated");files.failRead=false;
        check(files.readersOpened==files.readersClosed,"All failed version inspections close provider readers");
        files.files.put("AdvData/MES/START.MES",script(0xa5a8));check(GameVersion.detect(new GameResources(files)).profileId.equals("dc3dd-a5a8"),"A changed script header is re-read instead of using a stale version cache");
        files.files.put("AdvData/MES/main.mes",files.files.remove("AdvData/MES/START.MES"));reject(()->GameVersion.detect(new GameResources(files)),"Valid collection still requires START");
        files.files.clear();reject(()->GameVersion.detect(new GameResources(files)),"Empty collection remains invalid");
    }
    public static int run()throws Exception{
        checks=0;Dc2State state=new Dc2State();Variables v=new Variables(state,Variables.Model.DC2_PACKED);
        v.write(0,0,-1);v.write(0,1,256);check(v.read(0,0)==0&&v.read(0,1)==255,"DM bytes clamp instead of wrap");
        v.write(200,90,0x89abcdef);check(v.read(0,90)==0x89&&v.read(0,93)==0xef&&v.read(200,90)==0x89abcdef,"BE32 signed view aliases bytes");
        v.write(0,91,1);check(v.read(200,90)==0x8901cdef,"byte writes change packed reads");
        v.write(300,97,-40000);check(v.read(300,97)==-32768&&v.read(0,97)==128&&v.read(0,98)==0,"BE16 negative saturation");
        v.write(300,97,40000);check(v.read(300,97)==32767,"BE16 positive saturation");
        v.write(200,97,123);v.write(300,99,123);check(v.read(200,97)==0&&v.read(300,99)==0&&v.read(300,97)==32767,"invalid packed windows do not cross bank boundaries");
        v.write(100,20,300);check(v.read(100,20)==44&&v.read(10,20)==44,"persistent write wraps and mirrors bank 10");
        v.write(10,20,300);check(v.read(100,20)==255&&v.read(10,20)==255,"bank 10 write clamps before persistent mirror");
        v.write(10,21,12);check(v.read(10,21)==0,"bank 10 write above index 20 ignored");
        v.write(400,18,-123);check(v.read(400,18)==-123&&v.read(10,18)==255&&v.read(10,19)==133,"packed persistent writes maintain aliases");
        v.write(96,0,1);v.write(96,7,-2);check(v.read(96,0)==1&&v.read(96,7)==1&&v.read(100,100)==129,"persistent byte read overlaps LSB-first music bits");
        v.write(400,99,0x1234);check(v.read(400,99)==0x1281&&v.read(100,99)==0x12,"last persistent word writes high byte only, reads following music byte");
        v.write(96,7,0);v.write(97,1199,3);v.write(98,649,1);check(v.read(96,7)==0&&v.read(97,1199)==1&&v.read(98,649)==1,"bitsets keep independent boundaries");
        reject(()->v.write(300,-1,0),"negative packed write rejected");reject(()->v.read(400,100),"unverified persistent window rejected");
        reject(()->v.read(99,219),"platform state query is never fabricated");
        check(state.persistenceDirty,"persistent writes schedule storage work");
        Profile dm=Profiles.get("dc2dm-9d72"),dd=Profiles.get("dc3dd-a5a8");
        byte[] encoded={0x4f,4,5,(byte)255,0};MesScript dmText=new MesScript("text",script(0x9d72,encoded),dm);
        check(Arrays.equals(dmText.nativeText(dmText.at(7)),new byte[]{36,37,31}),"DM decoder preserves dollar control bytes");
        encoded[0]=0x65;MesScript ddText=new MesScript("text",script(0xa5a8,encoded),dd);
        check(Arrays.equals(ddText.nativeText(ddText.at(7)),new byte[]{32,37,31}),"DD dollar postmap remains independent");
        reject(()->new MesScript("wrong",script(0x9d72),dd),"cannot reinterpret DM as DD");
        Host host=new Host();MesVm vm=machine(script(0x9d72,
            word(0x62,0,0,256,0),word(0x62,0,0,300,2),word(0x62,0,2,7,0),word(0x62,1,7,234,0),word(0x62,0,2,1,3)),host);
        check(vm.run(100)==MesVm.Status.HALTED&&vm.variables.read(0,0)==0&&vm.variables.read(99,255)==234,"native DM opcodes execute update and indirect result");
        vm.variables.write(203,2,0x81234567);vm.variables.write(400,5,1234);vm.variables.write(98,649,1);byte[] saved=vm.snapshot();
        MesVm restored=machine(script(0x9d72,word(0x62,0,0,256,0),word(0x62,0,0,300,2),word(0x62,0,2,7,0),word(0x62,1,7,234,0),word(0x62,0,2,1,3)),new Host());restored.restore(saved);
        check(restored.variables.read(203,2)==0x81234567&&restored.variables.read(400,5)==1234&&restored.variables.read(10,5)==4&&restored.variables.read(98,649)==1,"snapshot retains packed views, aliases and bits");
        check(Arrays.equals(saved,restored.snapshot()),"DM snapshot exact round trip");
        MesVm other=new MesVm(n->new MesScript(n,script(0xa5a8),dd),new Host(),"main");reject(()->other.restore(saved),"cross-version save rejected");
        MesVm branch=machine(script(0x9d72,word(0x62,300,0,-20,0),word(0x66,300,0,0,4),new byte[]{2,(byte)200,0},word(0x62,0,2,9,0),new byte[]{0x1d,1,(byte)200},word(0x62,0,3,7,0)),new Host());
        check(branch.run(100)==MesVm.Status.HALTED&&branch.variables.read(0,2)==0&&branch.variables.read(0,3)==7,"signed wide value and signed block ID choose the correct false branch");
        MesVm sum=machine(script(0x9d72,word(0x62,0,1,80,0),word(0x62,0,2,90,0),word(0x64,0,3,0,0),word(0x51,0,1,0,0),word(0x51,0,2,0,0),new byte[]{2,0,0},word(0x63,0,4,0,3),word(0x65,0,4,0,2)),new Host());
        check(sum.run(100)==MesVm.Status.HALTED&&sum.variables.read(0,3)==170&&sum.variables.read(0,4)==255,"sum consumes nine-byte terms and addition uses saturation");
        MesVm jump=machine(script(0x9d72,str(0x3d,":a"),word(0x62,0,1,99,0),str(0x34,":a"),word(0x62,0,2,42,0)),new Host());
        check(jump.run(100)==MesVm.Status.HALTED&&jump.variables.read(0,1)==0&&jump.variables.read(0,2)==42,"DM jump uses independent label opcode");
        MesVm missing=machine(script(0x9d72,str(0x3d,":missing")),new Host());check(missing.run(100)==MesVm.Status.FAULT,"DM missing labels are errors, unlike DD");
        MesVm emptyJump=machine(script(0x9d72,str(0x3d,"")),new Host());check(emptyJump.run(100)==MesVm.Status.FAULT,"DM empty jump label is an error");
        MesVm lateLabel=machine(script(0x9d72,str(0x3d,":after"),new byte[3],str(0x34,":after")),new Host());check(lateLabel.run(100)==MesVm.Status.FAULT,"DM named scan cannot see labels after a stop");
        MesVm highBlock=machine(script(0x9d72,new byte[]{0x1d,0,(byte)200},new byte[]{0x1d,1,(byte)200}),new Host());check(highBlock.run(100)==MesVm.Status.FAULT,"DM unconditional block operand is unsigned but scanner marker is signed");
        MesVm unknown=machine(script(0x9d72,word(0x70,0,0,0,0)),new Host());check(unknown.run(100)==MesVm.Status.FAULT&&unknown.offset()==7,"unknown DM instructions stop at original PC");
        Storage files=new Storage();files.files.put("AdvData/MES/START.MES",script(0x9d72));files.files.put("AdvData/MES/main.mes",script(0x9d72));
        GameVersion version=GameVersion.detect(new GameResources(files));check(version.profileId.equals(dm.id)&&version.scriptCount==2&&files.maxRead<=8&&files.wholeReads==0,"collection detection uses small reads and ignores path case");
        check(files.readersOpened==2&&files.readersClosed==2,"Collection admission opens and closes one reader per script");
        files.files.put("AdvData/MES/third.mes",script(0xa5a8));reject(()->GameVersion.detect(new GameResources(files)),"mixed collection rejected even with a DM majority");files.files.remove("AdvData/MES/third.mes");
        files.files.put("AdvData/MES/main.mes",script(0x1111));reject(()->GameVersion.detect(new GameResources(files)),"unknown member is not silently ignored");
        files.files.remove("AdvData/MES/main.mes");files.files.put("AdvData/MES/START.MES",new byte[]{0,0,0,0,0x62,0,0,0});
        GameVersion early=GameVersion.detect(new GameResources(files));check(early.profileId.equals("dcmems-0062"),"early single-byte framing can be identified separately");reject(()->early.profile(),"framing knowledge does not grant runtime compatibility");
        files.files.put("AdvData/MES/START.MES",new byte[]{1,0,0,0,0,0,0,0,0x69,0x6e,0x72,(byte)0x9d,0,0,0,0});
        reject(()->GameVersion.detect(new GameResources(files)),"one ambiguous header does not select a guessed profile");
        files.files.put("AdvData/MES/main.mes",script(0x9d72));
        check(GameVersion.detect(new GameResources(files)).profileId.equals(dm.id),"collection intersection resolves coincidental version bytes inside metadata");
        boundedVersionReads();
        Storage dmFiles=new Storage();dmFiles.files.put("AdvData/GRP/NAMES.DAT",new byte[20]);
        dmFiles.files.put("AdvData/MES/START.MES",script(0x9d72,new byte[]{0x06,0,0}));
        dmFiles.files.put("AdvData/MES/ADVMENU.MES",script(0x9d72,new byte[]{0x1a,2,0}));
        CircusResources dmData=new CircusResources(new GameResources(dmFiles),dm);
        Dc2Session session=new Dc2Session(dmData,TextCodec.identity());session.vm.run(50);
        session.vm.variables.write(203,5,12345);session.host.body="saved body";
        session.openSystem("ADVMENU");session.vm.run(50);check(session.host.returnRequested,"DM native menu return request");
        session.host.write(99,7,12);session.host.write(100,33,1);session.returnToStory();
        check(session.host.textWaiting&&session.host.body.equals("saved body")&&session.vm.variables.read(203,5)==12345,"DM menu restores caller PC, variables and body");
        check(session.host.read(99,7)==12&&session.host.read(100,33)==1,"DM menu preserves settings and global progress");
        Dc2Host h=session.host;h.audioActive[0]=true;h.audioLoop[0]=true;h.audioActive[5]=true;
        h.command(dm.operation(0x11),new int[]{0,0},new byte[0]);
        check(h.musicFadeRemaining>0&&h.audioActive[5],"DM 11 fades BGM without stopping voice");
        h.elapse(2000);check(!h.audioActive[0]&&h.audioActive[5],"DM BGM fade terminates looping music");
        h.command("wait.audio",new int[]{1,0},new byte[0]);h.signal(h.waitTicket(),MesVm.HostSignal.ADVANCE);
        check(!h.waiting(),"DM BGM wait completes after fade");
        h.ui.waiting=h.confirming=true;h.ui.actions[0]="yes";h.ui.actions[1]="cancel";h.ui.actions[3]="wrong";
        check(new String(h.menuAction(h.waitTicket(),-1),Binary.SJIS).equals("cancel"),"DM confirmation Back uses cancel slot");
        h.confirmKind=3;Dc2Ui.Button remember=new Dc2Ui.Button();remember.action=":c";h.ui.buttons.add(remember);check(h.internalMenuAction(h.waitTicket(),0)&&h.confirmationChecked(),"DM remember-confirmation control stays inside modal");h.write(99,218,3);check(h.read(99,217)==1,"DM remembered prompt is exposed by the native selector");
        h.write(95,0,0);h.command("dm.audio.voice",new int[0],"voice/test_otm001".getBytes(Binary.SJIS));check(!h.audioActive[5],"DM per-character mute reaches playback");h.write(95,0,1);h.write(94,0,33);h.command("dm.audio.voice",new int[0],"voice/test_otm002".getBytes(Binary.SJIS));check(h.audioActive[5]&&Math.abs(h.voiceGain()-1/3f)<.0001,"DM per-character volume reaches playback");
        h.write(95,0,0);h.command("dm.audio.voice",new int[0],"#voice/test_otm003".getBytes(Binary.SJIS));check(h.audioActive[5]&&h.voiceGain()>0&&h.audio[5].equals("voice/test_otm003"),"DM forced voice bypasses character mute without leaking the prefix into resource lookup");
        h.playVoice("b;voice/test_otm004");check(h.voiceCharacter==1&&h.audioActive[5],"Backlog playback restores the explicit voice character instead of the last speaker");h.playVoice("#voice/test_otm003");
        h.ui.waiting=h.confirming=false;h.gridWaiting=h.saveGrid=true;h.grid.add(new Dc2Ui.Button());
        check(h.menuActionForGrid(0)==null,"DM reserved Quick entry cannot be overwritten manually");
        h.ui.waiting=true;byte[] hostSave=session.vm.snapshot();Dc2Session roundTrip=new Dc2Session(dmData,TextCodec.identity());roundTrip.vm.restore(hostSave);
        check(Arrays.equals(hostSave,roundTrip.vm.snapshot()),"DM host UI/audio/save state snapshot round trip");
        check(roundTrip.host.voiceGain()>0,"Forced voice gain survives a save round trip");
        h.ui.waiting=false;h.command("dm.movie",new int[0],"OPENING.MPG".getBytes(Binary.SJIS));
        check(h.movie.equals("AdvData/MOVIE/OPENING.MPG"),"DM movie filename resolves under MOVIE, not AdvData root");
        check(h.signal(h.waitTicket(),MesVm.HostSignal.MEDIA_COMPLETE)&&h.movie.isEmpty(),"DM movie completion releases the script wait");
        h.movie="AdvData/OPENING.MPG";byte[] legacyMovie=session.vm.snapshot();Dc2Session resumedMovie=new Dc2Session(dmData,TextCodec.identity());resumedMovie.vm.restore(legacyMovie);
        check(resumedMovie.host.movie.equals("AdvData/MOVIE/OPENING.MPG"),"legacy DM movie wait snapshot migrates its path");
        return checks;
    }
}
