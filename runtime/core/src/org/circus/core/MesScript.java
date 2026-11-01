package org.circus.core;

import java.io.*;
import java.util.*;

/** Immutable MES code, exact byte labels and physical boundaries. No execution. */
public final class MesScript {
    public static final class Record {
        public final int offset, end, opcode;
        public final int[] args;
        public final byte[] bytes;
        Record(int off,int end,int op,int[] args,byte[] bytes){this.offset=off;this.end=end;this.opcode=op;this.args=args;this.bytes=bytes;}
    }
    public final Profile profile;
    public final String name, sha256;
    public final int versionOffset, firstOffset;
    public final long[] headerOffsets;
    public final int[] headerMetadata;
    private final byte[] data;
    private final Map<Integer,Record> records=new LinkedHashMap<>();
    private final Map<String,Integer> labels=new HashMap<>();
    public MesScript(String name,byte[] input,Profile profile)throws IOException {
        if(input.length>64*1024*1024)throw new IOException("MES exceeds 64 MiB");
        this.name=name;this.profile=profile;data=input.clone();sha256=Binary.sha256(data);
        long count=Binary.u32(data,0);if(count>1000000)throw new IOException("MES header count limit");
        long vo=4+count*profile.headerStride;Binary.range(vo,profile.versionBytes,data.length);versionOffset=(int)vo;
        int ver=profile.versionBytes==1?Binary.u8(data,versionOffset):Binary.u16(data,versionOffset);
        if(!profile.acceptsVersion(ver))throw new IOException("MES version does not match "+profile.id);
        headerOffsets=new long[(int)count];headerMetadata=new int[(int)count];
        for(int i=0;i<count;i++){headerOffsets[i]=Binary.u32(data,4+4*i);if(profile.headerStride==6)headerMetadata[i]=Binary.u16(data,4+(int)count*4+2*i);}
        firstOffset=versionOffset+profile.versionBytes;
        for(int p=firstOffset;p<data.length;){
            // 758 of the 938 local D.C.II scripts end with a two-byte 00 00 trailer that is not a
            // record; the remaining 180 end flush. Nothing else in the corpus is shorter than 3.
            if(profile.id.equals("dc2-3363")&&data.length-p<=2&&zeroBytes(data,p))break;
            Record r=decode(p);records.put(p,r);if(r.opcode==profile.label)labels.putIfAbsent(Binary.hex(r.bytes),p);p=r.end;
            if(profile.id.equals("dcpc-2063")&&r.opcode==0)break; // 41265F terminates; bytes beyond stop are not instructions.
            // These two PC2 scripts contain the literal trailer 61 64 after their terminal stop.
            if(profile.id.equals("dc2pc-5769")&&r.opcode==0&&data.length-p==2&&data[p]==0x61&&data[p+1]==0x64)break;
        }
        if(records.isEmpty())throw new IOException("Empty MES code");
    }
    private static boolean zeroBytes(byte[] data,int from){for(int i=from;i<data.length;i++)if(data[i]!=0)return false;return true;}
    private Record decode(int p)throws IOException {
        int op=Binary.u8(data,p),end;int[] a=new int[0];byte[] bytes=new byte[0];
        // 4125F4 movsx + 412618 signed compare: high-bit opcodes consume two bytes,
        // then the unsigned dispatch bound at 41264A takes the default continuation.
        if(profile.id.equals("dcpc-2063")&&op>=0x80){Binary.range(p,3,data.length);return new Record(p,p+3,op,new int[]{Binary.u8(data,p+1),Binary.u8(data,p+2)},bytes);}
        if(op==0&&PcHost.supports(profile)&&data.length-p<3){for(int n=p;n<data.length;n++)if(data[n]!=0)throw new IOException("Invalid PC terminal padding");return new Record(p,data.length,op,new int[]{0,0},bytes);}
        if(op<profile.string8Start){end=p+3;Binary.range(p,3,data.length);a=new int[]{Binary.u8(data,p+1),Binary.u8(data,p+2)};}
        else if(op<profile.wordsStart){int start=p+(op<profile.stringStart?2:1);end=Binary.endZ(data,start)+1;bytes=Arrays.copyOfRange(data,start,end-1);if(start==p+2)a=new int[]{Binary.u8(data,p+1)};}
        else {end=p+9;Binary.range(p,9,data.length);a=new int[4];for(int i=0;i<4;i++)a[i]=Binary.i16(data,p+1+2*i);}
        return new Record(p,end,op,a,bytes);
    }
    public Record at(int offset)throws IOException {Record r=records.get(offset);if(r==null)throw new IOException("PC is not a record boundary: "+offset);return r;}
    public boolean boundary(int offset){return records.containsKey(offset);}
    public Collection<Record> records(){return Collections.unmodifiableCollection(records.values());}
    public Integer label(byte[] name){return name.length==0?null:labels.get(Binary.hex(name));}
    /** Older DM named search sign-extends opcodes and stops at the first zero record. */
    public Integer requiredLabel(byte[] name)throws IOException{
        if(name.length==0)return null;
        for(int pos=firstOffset,steps=0;pos<data.length&&++steps<=data.length;){
            int op=data[pos];if(op==0)return null;
            if(op==profile.label){int end=Binary.endZ(data,pos+1);if(Arrays.equals(name,Arrays.copyOfRange(data,pos+1,end))){if(!boundary(pos))throw new IOException("Native label scan gives non-boundary PC");return pos;}}
            if(op<profile.string8Start)pos+=3;
            else if(op<profile.wordsStart)pos=Binary.endZ(data,pos+(op<profile.stringStart?2:1))+1;
            else pos+=9;
        }
        return null;
    }
    public int length(){return data.length;}
    public byte[] nativeText(Record r)throws IOException {
        byte[] out=r.bytes.clone();
        if(r.opcode>=profile.encodedStart && r.opcode<profile.wordsStart){
            if(!profile.textVerified)throw new IOException("Text postprocessing unverified for "+profile.id);
            for(int i=0;i<out.length;i++){int v=((out[i]&255)+32)&255;out[i]=(byte)(profile.dollarToSpace&&v==36?32:v);}
        }
        return out;
    }
    public int scanBlock(int start,int id)throws IOException {
        int p=start,steps=0;
        while(p<data.length && ++steps<=data.length){
            int op=profile.signedBlockScan?data[p]:data[p]&255;
            if(op==profile.block && p+3<=data.length && data[p+2]==id){int target=p+3;if(!boundary(target))throw new IOException("Native block scan gives non-boundary PC");return target;}
            if(op==0)throw new IOException("Block marker missing before stop");
            if(op<profile.string8Start)p+=3;
            else if(op<profile.wordsStart)p=Binary.endZ(data,p+(op<profile.stringStart?2:1))+1;
            else p+=9;
        }
        throw new IOException("Block scan exceeds script");
    }
}
