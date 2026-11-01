package org.circus.core;

import java.io.*;
import java.util.*;

/** Version-bound original system progress. Never copies native preferences or story state.
 * Layout evidence and completion allowlists: docs/ANDROID_0135.md.
 * Native files have no game signature: independently identify the selected game's MES first.
 */
public final class NativeProgress {
    private static final class Layout {
        final String id,name;final int size,global,music,cg,scenes,musicCount,cgCount,sceneCount;
        final boolean packed;final int[] completion,counters;
        Layout(String id,String name,int size,int global,int music,int cg,int scenes,int musicCount,int cgCount,int sceneCount,boolean packed,int[] completion,int[] counters){
            this.id=id;this.name=name;this.size=size;this.global=global;this.music=music;this.cg=cg;this.scenes=scenes;this.musicCount=musicCount;this.cgCount=cgCount;this.sceneCount=sceneCount;this.packed=packed;this.completion=completion;this.counters=counters;
        }
        boolean counter(int index){for(int n:counters)if(n==index)return true;return false;}
    }
    private static final Layout[] LAYOUTS={
        new Layout("dcpc-2063","DCPC",1748,0x70,0,0xd4,0x2d4,0,512,1024,false,
            new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,26,31,32,58,59},new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,26}),
        // Original D.C.II 2006 loads the whole file into one 2260-byte table at 0x473F78 and reads
        // bank 97 from +0xD4, bank 98 from +0x4D4 and bank 100 from +0x70 (getter 0x413C09, setter
        // 0x413C70), so both gallery arrays are 1024 bytes. 0x41D2A3 and 0x41D325 store 1 when a
        // CG or scene is shown, the same "seen" polarity as P.C.
        new Layout("dc2-3363","DC2",2260,0x70,0,0xd4,0x4d4,0,1024,1024,false,
            new int[]{1,2,3,4,5,6,7,59},new int[]{59}),
        new Layout("dc2pc-5769","DC2PC",3312,0x70,0,0xd4,0x6b0,0,1500,1500,false,
            new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,21,28,29},new int[0]),
        new Layout("dc2dm-9d72","DC2DM",15716,0xe4,0x148,0x17a,0x210,400,1200,650,true,
            new int[]{0,1,2,14,15,20,29,30,31,32},new int[0]),
        new Layout("dc3dd-a5a8","DC3DD",21356,0xcc,0x174,0x304,0x7b4,400,1200,650,false,
            new int[]{100,101,102,103,104,105,106,107,110},new int[0]),
        // WY getter 40BF90: +C0 globals, +168 music, +2F8 CG, +7A8 scenes.
        // system.dat stores the first 4528 bytes of the object then 7F8 voice bytes.
        new Layout("dc3wy-a09f","DC3WY",19744,0xc0,0x168,0x2f8,0x7a8,400,1200,650,false,
            new int[]{100,101,102,103,104,105,106,110},new int[]{101}),
        // PP writes 3C8C bytes from 7410D8. Getter 4256A0/425AF3 binds globals
        // at 741160 and LSB-first flag arrays at 7411C4/7411DF/741275.
        new Layout("dc3pp-9872","DC3PP",15500,0x88,0xec,0x107,0x19d,210,1200,650,true,
            new int[]{0,1,2,3,4,5,28},new int[0])
    };
    private static Layout layout(String id)throws IOException{for(Layout l:LAYOUTS)if(l.id.equals(id))return l;throw new IOException("尚未适配此版本的原版进度："+id);}
    public static boolean supports(String id){for(Layout l:LAYOUTS)if(l.id.equals(id))return true;return false;}
    public static int fileSize(String id)throws IOException{return layout(id).size;}
    /** Only known locations within the independently selected game, never recursive filename guessing. */
    public static List<String> sourcePaths(GameResources files,String id)throws IOException{
        layout(id);List<String> result=new ArrayList<>();
        for(String path:new String[]{"AdvData/SaveData/system.dat","SaveData/system.dat","system.dat"})if(files.paths().contains(path.toLowerCase(Locale.ROOT)))result.add(path);
        if(id.equals("dc3wy-a09f")||id.equals("dc3pp-9872")){String path=(id.equals("dc3wy-a09f")?"dc3wy":"dc3pp")+"/system.dat";if(files.paths().contains(path))result.add(path);}
        return result;
    }
    public static boolean storySlot(String id,String source,String path)throws IOException{
        layout(id);String parent=source.substring(0,source.lastIndexOf('/')+1).toLowerCase(Locale.ROOT),p=path.toLowerCase(Locale.ROOT);
        String prefix=id.equals("dcpc-2063")?"dcpc":id.equals("dc2-3363")?"dc2":id.equals("dc2pc-5769")?"dc2pc":id.equals("dc2dm-9d72")?"dc2dm":id.equals("dc3wy-a09f")?"dc3wy":id.equals("dc3pp-9872")?"dc3pp":"dc3dd";
        return p.startsWith(parent)&&p.substring(parent.length()).matches(prefix+"[0-9]+\\.dat");
    }
    public static final class Summary {
        public final int completion,music,cg,scenes,total;
        Summary(int completion,int music,int cg,int scenes){this.completion=completion;this.music=music;this.cg=cg;this.scenes=scenes;total=completion+music+cg+scenes;}
        @Override public String toString(){return "completion="+completion+", music="+music+", cg="+cg+", scenes="+scenes+", total="+total;}
    }
    public final String profileId,sourceSummary;public final Summary source;
    private final Layout layout;private final int[] completion,music,cg,scenes;
    private NativeProgress(Layout l,byte[] bytes)throws IOException{
        layout=l;profileId=l.id;completion=new int[l.completion.length];
        for(int n=0;n<completion.length;n++){int index=l.completion[n],v=bytes[l.global+index]&255;if(!l.counter(index))flag(v,100,index);completion[n]=v;}
        music=flags(bytes,l.music,l.musicCount,l.packed,96);cg=flags(bytes,l.cg,l.cgCount,l.packed,97);scenes=flags(bytes,l.scenes,l.sceneCount,l.packed,98);
        source=new Summary(count(completion),count(music),count(cg),count(scenes));sourceSummary=l.name+" system.dat ("+l.size+" bytes; "+source+")";
    }
    public static NativeProgress parse(String id,byte[] bytes)throws IOException{
        Layout l=layout(id);if(bytes==null||bytes.length!=l.size)throw new IOException(l.name+" system.dat 文件长度应为 "+l.size+" 字节；所选文件格式不匹配");return new NativeProgress(l,bytes);
    }
    private static void flag(int v,int bank,int index)throws IOException{if(v!=0&&v!=1)throw new IOException("Invalid native unlock flag "+bank+":"+index+" = "+v);}
    private static int[] flags(byte[] b,int offset,int count,boolean packed,int bank)throws IOException{int[] r=new int[count];for(int n=0;n<count;n++){r[n]=packed?(b[offset+n/8]>>>(n&7))&1:b[offset+n]&255;flag(r[n],bank,n);}return r;}
    private static int count(int[] values){int n=0;for(int v:values)if(v!=0)n++;return n;}
    private void check(String id,Variables.Special target)throws IOException{if(target==null||!profileId.equals(id))throw new IOException("Native progress target profile mismatch");}
    private boolean addCompletion(Variables.Special target,int n)throws IOException{int prior=target.read(100,layout.completion[n]);return completion[n]>0&&(layout.counter(layout.completion[n])?completion[n]>prior:prior==0);}
    private static int additions(Variables.Special target,int bank,int[] source,boolean apply)throws IOException{int count=0;for(int n=0;n<source.length;n++)if(source[n]!=0&&target.read(bank,n)==0){count++;if(apply)target.write(bank,n,source[n]);}return count;}
    public Summary preview(String id,Variables.Special target)throws IOException{return merge(id,target,false);}
    public Summary apply(String id,Variables.Special target)throws IOException{return merge(id,target,true);}
    private Summary merge(String id,Variables.Special target,boolean apply)throws IOException{
        check(id,target);int c=0;for(int n=0;n<completion.length;n++)if(addCompletion(target,n)){c++;if(apply)target.write(100,layout.completion[n],completion[n]);}
        return new Summary(c,additions(target,96,music,apply),additions(target,97,cg,apply),additions(target,98,scenes,apply));
    }
    public Summary preview(Target target)throws IOException{return preview(target==null?null:target.profileId,target);}
    public Summary apply(Target target)throws IOException{return apply(target==null?null:target.profileId,target);}
    public Summary preview(Dc2Host host)throws IOException{return preview(host==null?null:host.resources.profileId,host);}
    public Summary apply(Dc2Host host)throws IOException{return apply(host==null?null:host.resources.profileId,host);}

    /** Uses the exact phone persistence schemas read by the runtime; no running game is needed. */
    public static final class Target implements Variables.Special {
        public final String profileId;public final Dc2Host legacy;public final Dc3State dd;
        public Target(CircusResources data,TextCodec codec)throws IOException{
            layout(data.profileId);profileId=data.profileId;dd=Dc3Host.supports(profileId)?new Dc3State(true):null;legacy=dd==null?Dc2Host.create(data,codec):null;
        }
        public int read(int bank,int index)throws IOException{return dd==null?legacy.read(bank,index):dd.read(bank,index);}
        public void write(int bank,int index,int value)throws IOException{if(dd==null)legacy.write(bank,index,value);else dd.write(bank,index,value);}
        public void load(byte[] bytes)throws IOException{
            if(dd!=null){dd.loadPersistent(bytes);return;}
            try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){legacy.loadPersistent(in);if(in.available()!=0)throw new IOException("手机系统进度存在无法识别的数据，已保留原文件");}
        }
        public byte[] save()throws IOException{if(dd!=null)return dd.savePersistent();ByteArrayOutputStream bytes=new ByteArrayOutputStream();legacy.savePersistent(new DataOutputStream(bytes));return bytes.toByteArray();}
    }
}
