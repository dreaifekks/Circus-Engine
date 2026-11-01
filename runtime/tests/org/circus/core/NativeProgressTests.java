package org.circus.core;

import java.io.*;
import java.util.*;

/** Synthetic native layouts, including packed-bit and byte-bank boundaries. No game assets. */
public final class NativeProgressTests {
    private static int count;
    private interface Work {void run()throws Exception;}
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void reject(Work work,String why)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}check(rejected,why);}
    private static NativeProgress.Target target(String id)throws Exception{
        GameResources files=new GameResources(new GameResources.Storage(){
            public Set<String> paths(){return new HashSet<>(Arrays.asList("AdvData/DAT/NAMES.DAT","AdvData/GRP/NAMES.DAT"));}
            public byte[] read(String path,int limit){return new byte[20];}
            public Archive.Source source(String path){return new Archive.Source(){
                public long size(){return 20;}
                public byte[] read(long at,int n)throws IOException{Binary.range(at,n,20);return new byte[n];}
            };}
        });
        Profile profile=Profiles.get(id);
        NativeProgress.Target result=new NativeProgress.Target(new CircusResources(files,profile),TextCodec.identity());
        if(result.legacy!=null)result.legacy.attach(new Variables(result.legacy,profile.variableModel));
        return result;
    }
    private static void flag(byte[] bytes,int offset,int index,boolean packed){
        if(packed)bytes[offset+index/8]|=(byte)(1<<(index&7));else bytes[offset+index]=1;
    }
    private static void formatAndIdentity()throws Exception{
        String[] ids={"dcpc-2063","dc2pc-5769","dc2dm-9d72","dc3dd-a5a8","dc3wy-a09f","dc3pp-9872"};
        int[] sizes={1748,3312,15716,21356,19744,15500};
        check(!NativeProgress.supports(null)&&!NativeProgress.supports("dcpc-3d63")&&!NativeProgress.supports("dc3r"),"Unknown/older release IDs never inherit a similar game's native layout");
        reject(()->NativeProgress.fileSize("dcpc"),"Unversioned family ID has no native layout");
        reject(()->NativeProgress.parse(null,new byte[1748]),"Null profile cannot be inferred from file length");
        for(int i=0;i<ids.length;i++){
            final String id=ids[i];final int size=sizes[i];
            check(NativeProgress.supports(id)&&NativeProgress.fileSize(id)==size,"Exact independently reviewed file size: "+id);
            check(NativeProgress.parse(id,new byte[size]).source.total==0,"Fresh native save has no imported flags: "+id);
            reject(()->NativeProgress.parse(id,null),"Null native source: "+id);
            for(int n:new int[]{0,size-1,size+1})reject(()->NativeProgress.parse(id,new byte[n]),"Truncation/trailing bytes rejected: "+id+"/"+n);
            for(int j=0;j<sizes.length;j++)if(j!=i){final int other=sizes[j];reject(()->NativeProgress.parse(id,new byte[other]),"Cross-version file cannot be interpreted by size coincidence: "+id+"/"+other);}
            NativeProgress progress=NativeProgress.parse(id,new byte[size]);
            NativeProgress.Target wrong=target(ids[(i+1)%ids.length]);byte[] before=wrong.save();
            reject(()->progress.preview(wrong),"Wrong target preview rejected: "+id);
            reject(()->progress.apply(wrong),"Wrong target mutation rejected: "+id);
            check(Arrays.equals(before,wrong.save()),"Wrong target remains unchanged: "+id);
            reject(()->progress.apply((NativeProgress.Target)null),"Null target rejected: "+id);
        }
    }
    private static void pc1Counters()throws Exception{
        String id="dcpc-2063";byte[] bytes=new byte[1748];
        bytes[0x70]=2;bytes[0x71]=(byte)255;bytes[0x70+26]=3;
        for(int index:new int[]{31,32,58,59})bytes[0x70+index]=1;
        bytes[0x70+60]=(byte)255;bytes[0x70+85]=18;bytes[0x70+99]=(byte)255;
        NativeProgress p=NativeProgress.parse(id,bytes);check(p.source.completion==7&&p.source.total==7,"Completion summary counts fields, not accumulated replay counters");
        NativeProgress.Target t=target(id);
        t.write(100,0,5);t.write(100,1,1);t.write(100,2,7);t.write(100,26,2);t.write(100,31,2);t.write(100,58,1);
        t.write(100,60,1);t.write(100,85,4);t.write(100,99,17);
        byte[] before=t.save();check(p.preview(t).completion==4&&Arrays.equals(before,t.save()),"Counter increases and boolean unlock additions preview without mutation");
        byte[] original=bytes.clone();p.apply(t);check(Arrays.equals(bytes,original),"Merge never changes native source bytes");
        check(t.read(100,0)==5&&t.read(100,1)==255&&t.read(100,2)==7&&t.read(100,26)==3,"PC1 counters merge by unsigned maximum, never sum, reset, or normalize");
        check(t.read(100,31)==2&&t.read(100,32)==1&&t.read(100,58)==1&&t.read(100,59)==1,"PC1 boolean merge preserves existing nonzero target values");
        check(t.read(100,60)==1&&t.read(100,85)==4&&t.read(100,99)==17,"Voice mute, selected system voice and unknown globals excluded");
        byte[] merged=t.save();check(p.apply(t).total==0&&Arrays.equals(merged,t.save()),"Counter import is byte-stable on repeated apply");
        NativeProgress.Target reloaded=target(id);reloaded.load(merged);
        check(Arrays.equals(merged,reloaded.save())&&p.preview(reloaded).total==0,"Unsigned255 counters survive portable persistence");
        for(int index:new int[]{31,32,58,59}){
            byte[] bad=new byte[1748];bad[0x70+index]=2;
            reject(()->NativeProgress.parse(id,bad),"Boolean PC1 field rejects counter values: "+index);
        }
    }
    private static void completionAllowlists()throws Exception{
        String[] ids={"dcpc-2063","dc2pc-5769","dc2dm-9d72","dc3dd-a5a8","dc3wy-a09f","dc3pp-9872"};
        int[] offsets={0x70,0x70,0xe4,0xcc,0xc0,0x88};
        int[][] accepted={
            {0,1,2,3,4,5,6,7,8,9,10,11,12,26,31,32,58,59},
            {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,21,28,29},
            {0,1,2,14,15,20,29,30,31,32},
            {100,101,102,103,104,105,106,107,110},
            {100,101,102,103,104,105,106,110},
            {0,1,2,3,4,5,28}
        };
        for(int k=0;k<ids.length;k++){
            String id=ids[k];int size=NativeProgress.fileSize(id),length=k==3||k==4?150:100;
            byte[] bytes=new byte[size];Arrays.fill(bytes,offsets[k],offsets[k]+length,(byte)0xee);
            Set<Integer> expected=new HashSet<>();for(int index:accepted[k]){bytes[offsets[k]+index]=1;expected.add(index);}
            NativeProgress p=NativeProgress.parse(id,bytes);NativeProgress.Target t=target(id);
            for(int n=0;n<length;n++)t.write(100,n,expected.contains(n)?0:7);
            check(p.source.completion==accepted[k].length&&p.source.total==accepted[k].length,"Only independently reviewed completion indices accepted: "+id);
            p.apply(t);boolean correct=true;for(int n=0;n<length;n++)correct&=t.read(100,n)==(expected.contains(n)?1:7);
            check(correct,"Whole native global bank cannot overwrite preferences or unreviewed state: "+id);
        }
    }
    private static void boundariesAndPreservation(String id,int size,int globals,int completion,int music,int cg,int scenes,int musicCount,int cgCount,int sceneCount,boolean packed)throws Exception{
        byte[] bytes=new byte[size];Arrays.fill(bytes,0,globals,(byte)0xfe);
        bytes[globals+completion]=1;
        int ignored=id.equals("dc3dd-a5a8")?20:85;bytes[globals+ignored]=(byte)255;
        for(int index:new int[]{7,8,cgCount-1})flag(bytes,cg,index,packed);
        for(int index:new int[]{7,8,sceneCount-1})flag(bytes,scenes,index,packed);
        if(musicCount>0)for(int index:new int[]{7,8,musicCount-1})flag(bytes,music,index,packed);
        if(packed)bytes[scenes+81]|=(byte)0xfc; // Only bits0..1 of byte81 belong to the 650-entry scene bank.
        NativeProgress p=NativeProgress.parse(id,bytes);
        check(p.source.completion==1&&p.source.cg==3&&p.source.scenes==3&&p.source.music==(musicCount==0?0:3),"Independent native bank boundaries/bit order: "+id);
        NativeProgress.Target t=target(id);
        t.write(100,ignored,4);t.write(99,7,37);t.write(99,8,46);
        t.write(97,7,1);t.write(97,9,1);t.write(98,7,1);t.write(98,9,1);
        if(musicCount>0){t.write(96,7,1);t.write(96,9,1);}
        if(t.dd!=null){t.dd.musicFavorites[12]=1;t.write(94,50,41);t.write(95,50,0);t.write(93,3,1);}
        else{
            t.legacy.readCheckpoints.add("phone_only:42");t.legacy.availableSaves.add(6);t.legacy.variables.write(3,4,123);
            if(t.legacy instanceof PcHost){PcHost pc=(PcHost)t.legacy;pc.familyName="Own";pc.givenName="Name";pc.write(100,60,1);pc.write(97,cgCount,1);pc.write(98,sceneCount,1);}
            else{t.write(94,50,41);t.write(95,50,0);}
        }
        byte[] before=t.save(),nativeBefore=bytes.clone();NativeProgress.Summary preview=p.preview(t);
        check(preview.completion==1&&preview.cg==2&&preview.scenes==2&&preview.music==(musicCount==0?0:2),"Preview subtracts existing phone unlocks: "+id);
        check(Arrays.equals(before,t.save())&&Arrays.equals(nativeBefore,bytes),"Preview preserves phone and source bytes: "+id);
        Arrays.fill(bytes,(byte)0);NativeProgress.Summary applied=p.apply(t);
        check(applied.total==preview.total,"Parser owns its data after caller modifies input: "+id);
        check(t.read(100,completion)==1&&t.read(97,8)==1&&t.read(97,cgCount-1)==1&&t.read(98,8)==1&&t.read(98,sceneCount-1)==1,"End entries and bit7/8 transition imported: "+id);
        check(t.read(97,9)==1&&t.read(98,9)==1,"Source locked entries do not clear phone unlocks: "+id);
        if(musicCount>0)check(t.read(96,8)==1&&t.read(96,musicCount-1)==1&&t.read(96,9)==1,"Music first-byte boundary and final entry merge: "+id);
        check(t.read(100,ignored)==4&&t.read(99,7)==37&&t.read(99,8)==46,"Native preferences and unknown globals do not overwrite phone settings: "+id);
        if(t.dd!=null)check(t.dd.musicFavorites[12]==1&&t.read(94,50)==41&&t.read(95,50)==0&&t.read(93,3)==1,"DD favorites, voice levels and confirmations retained");
        else{
            check(t.legacy.readCheckpoints.contains("phone_only:42")&&t.legacy.availableSaves.equals(Collections.singleton(6))&&t.legacy.variables.read(3,4)==123,"Read history, story variables and phone slot availability retained: "+id);
            if(t.legacy instanceof PcHost){PcHost pc=(PcHost)t.legacy;check(pc.familyName.equals("Own")&&pc.givenName.equals("Name")&&pc.read(100,60)==1&&pc.read(97,cgCount)==1&&pc.read(98,sceneCount)==1,"PC names/mute and host bank tail retained: "+id);}
            else check(t.read(94,50)==41&&t.read(95,50)==0,"DM voice preferences retained");
        }
        byte[] merged=t.save();check(p.apply(t).total==0&&Arrays.equals(merged,t.save()),"Repeated merge makes no persistent change: "+id);
        NativeProgress.Target again=target(id);again.load(merged);
        check(Arrays.equals(merged,again.save())&&p.preview(again).total==0,"Exact current persistent schema reload: "+id);
        check(NativeProgress.parse(id,new byte[size]).apply(t).total==0&&Arrays.equals(merged,t.save()),"Fresh PC progress cannot erase phone progress: "+id);
        reject(()->again.load(Arrays.copyOf(merged,merged.length-1)),"Truncated phone persistence rejected: "+id);
        reject(()->target(id).load(Arrays.copyOf(merged,merged.length+1)),"Trailing phone persistence bytes rejected: "+id);
        if(packed){
            byte[] padding=new byte[size];padding[scenes+81]=(byte)0xfc;
            check(NativeProgress.parse(id,padding).source.total==0,"Unused packed scene tail bits650..655 never become extra unlocks");
            reject(()->t.read(98,650),"Host bounds enforce exactly650 scenes");
            byte[] bad=new byte[size];bad[globals+completion]=2;reject(()->NativeProgress.parse(id,bad),"Packed format still validates byte completion flags");
        }else{
            for(int offset:new int[]{cg,cg+cgCount-1,scenes,scenes+sceneCount-1}){
                byte[] bad=new byte[size];bad[offset]=2;reject(()->NativeProgress.parse(id,bad),"Nonbinary gallery flag rejected at "+id+"/"+offset);
            }
            if(musicCount>0){byte[] bad=new byte[size];bad[music+musicCount-1]=2;reject(()->NativeProgress.parse(id,bad),"Final byte music flag validated: "+id);}
        }
    }
    public static int run()throws Exception{
        count=0;formatAndIdentity();pc1Counters();completionAllowlists();
        boundariesAndPreservation("dcpc-2063",1748,0x70,31,0,0xd4,0x2d4,0,512,1024,false);
        boundariesAndPreservation("dc2pc-5769",3312,0x70,21,0,0xd4,0x6b0,0,1500,1500,false);
        boundariesAndPreservation("dc2dm-9d72",15716,0xe4,14,0x148,0x17a,0x210,400,1200,650,true);
        boundariesAndPreservation("dc3dd-a5a8",21356,0xcc,100,0x174,0x304,0x7b4,400,1200,650,false);
        boundariesAndPreservation("dc3wy-a09f",19744,0xc0,100,0x168,0x2f8,0x7a8,400,1200,650,false);
        boundariesAndPreservation("dc3pp-9872",15500,0x88,28,0xec,0x107,0x19d,210,1200,650,true);
        byte[] wy=new byte[19744];wy[0xc0+101]=(byte)255;NativeProgress.Target wyTarget=target("dc3wy-a09f");wyTarget.write(100,101,2);NativeProgress.parse("dc3wy-a09f",wy).apply(wyTarget);check(wyTarget.read(100,101)==255,"WY route completion counter merges unsigned maximum");
        byte[] pp=new byte[15500];pp[0xec+26]=(byte)0xfc;check(NativeProgress.parse("dc3pp-9872",pp).source.total==0,"PP music padding bits210..215 are not CG or music unlocks");
        return count;
    }
    public static void main(String[] args)throws Exception{System.out.println("NativeProgressTests: "+run()+" assertions passed");}
}
