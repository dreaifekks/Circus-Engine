package org.circus.core;

// XPCM container reference: GARbro, Copyright (C) 2015-2016 by morkt (MIT).
// Full permission and warranty notice in THIRD_PARTY.md.
import java.io.*;
import java.util.Arrays;
import java.util.zip.*;

public final class Xpcm {
    private static final int LIMIT=128*1024*1024;
    public final String codec;public final byte[] payload;public final int channels,sampleRate,bits;
    private Xpcm(String c,byte[] p,int ch,int hz,int b){codec=c;payload=p;channels=ch;sampleRate=hz;bits=b;}
    static Xpcm pcm(byte[] p,int channels,int rate,int bits){return new Xpcm("pcm",p,channels,rate,bits);}
    public static Xpcm ogg(byte[] data)throws IOException{if(data.length<4||data[0]!='O'||data[1]!='g'||data[2]!='g'||data[3]!='S')throw new IOException("Expected Ogg stream");return new Xpcm("ogg",data,0,0,0);}
    public static Xpcm decode(byte[] data)throws IOException{
        // The 2006 D.C.II keeps eight long SE (222..229, the alarm clock bed) as plain RIFF/WAVE
        // next to 128 XPCM siblings; its own mixer reads both containers, so accept either.
        if(Binary.u32(data,0)==0x46464952L&&data.length>=44&&Binary.u32(data,8)==0x45564157L)return wave(data);
        if(Binary.u32(data,0)!=0x4d435058L)throw new IOException("Expected XPCM");
        long size=Binary.u32(data,4);if(size==0||size>128*1024*1024)throw new IOException("Invalid decoded audio size");int mode=Binary.u8(data,8);
        if(mode==5){long packed=Binary.u32(data,12);Binary.range(16,packed,data.length);if(Binary.u32(data,16)!=0x5367674fL)throw new IOException("Invalid Ogg payload");return new Xpcm("ogg",Arrays.copyOfRange(data,16,16+(int)packed),0,0,0);}
        if(mode!=0&&mode!=1&&mode!=3)throw new IOException("Unsupported XPCM transform mode "+mode);
        int tag=Binary.u16(data,12),ch=Binary.u16(data,14),hz=(int)Binary.u32(data,16),align=Binary.u16(data,24),bits=Binary.u16(data,26);
        if(tag!=1||ch<1||ch>2||hz<8000||hz>192000||(bits!=8&&bits!=16)||align!=ch*bits/8||size%align!=0)throw new IOException("Invalid PCM wave format");
        if(mode==1||mode==3){if(bits!=16)throw new IOException("XPCM transform requires PCM16");return new Xpcm("pcm",XpcmTransform.decode(data,(int)size,mode,Binary.u8(data,9)),ch,hz,bits);}
        Binary.range(28,size,data.length);return new Xpcm("pcm",Arrays.copyOfRange(data,28,28+(int)size),ch,hz,bits);
    }
    /** RIFF/WAVE with a single {@code fmt } chunk and (optionally) a trailing {@code data} chunk. */
    private static Xpcm wave(byte[] data)throws IOException{
        int tag=-1,ch=-1,hz=-1,bits=-1,offset=-1,length=-1;
        for(int p=12;p+8<=data.length;){
            long size=Binary.u32(data,p+4);if(size<0||size>data.length-p-8)throw new IOException("Invalid WAVE chunk size");
            long id=Binary.u32(data,p);
            if(id==0x20746d66L&&size>=16){
                tag=Binary.u16(data,p+8);ch=Binary.u16(data,p+10);hz=(int)Binary.u32(data,p+12);bits=Binary.u16(data,p+22);
            }else if(id==0x61746164L){
                offset=p+8;length=(int)size;break;
            }
            p+=(int)size+8+(size&1);
        }
        if(tag!=1||ch<1||ch>2||hz<8000||hz>192000||(bits!=8&&bits!=16)||offset<0||length<=0)
            throw new IOException("Invalid PCM wave format");
        if(length%(ch*bits/8)!=0)throw new IOException("Invalid PCM wave length");
        Binary.range(offset,length,data.length);
        return new Xpcm("pcm",Arrays.copyOfRange(data,offset,offset+length),ch,hz,bits);
    }
    /** Write an already decoded sound without constructing a second complete WAV. */
    public void writeTo(OutputStream out)throws IOException{
        if(codec.equals("pcm"))waveHeader(out,payload.length,channels,sampleRate,bits);
        else if(!codec.equals("ogg"))throw new IOException("Unsupported audio codec "+codec);
        out.write(payload);
    }
    /** Uncompressed PCM and embedded Ogg are copied in bounded chunks. The
     * existing transform decoder remains authoritative for modes 1 and 3. */
    public static void write(Archive.Source source,OutputStream out)throws IOException{
        long length=source.size();if(length<16||length>LIMIT)throw new IOException("Invalid XPCM resource size");
        byte[] header=readExact(source,0,(int)Math.min(length,32));
        if(Binary.u32(header,0)==0x46464952L&&Binary.u32(header,8)==0x45564157L){source.copyTo(0,length,out);return;}
        if(Binary.u32(header,0)!=0x4d435058L)throw new IOException("Expected XPCM");
        long size=Binary.u32(header,4);if(size==0||size>LIMIT)throw new IOException("Invalid decoded audio size");int mode=Binary.u8(header,8);
        if(mode==5){long packed=Binary.u32(header,12);Binary.range(16,packed,length);
            if(packed<4||Binary.u32(header,16)!=0x5367674fL)throw new IOException("Invalid Ogg payload");
            source.copyTo(16,packed,out);return;
        }
        if(mode!=0&&mode!=1&&mode!=3)throw new IOException("Unsupported XPCM transform mode "+mode);
        int tag=Binary.u16(header,12),ch=Binary.u16(header,14),hz=(int)Binary.u32(header,16),align=Binary.u16(header,24),bits=Binary.u16(header,26);
        if(tag!=1||ch<1||ch>2||hz<8000||hz>192000||(bits!=8&&bits!=16)||align!=ch*bits/8||size%align!=0)throw new IOException("Invalid PCM wave format");
        if(mode==1||mode==3){decode(readExact(source,0,(int)length)).writeTo(out);return;}
        Binary.range(28,size,length);waveHeader(out,(int)size,ch,hz,bits);source.copyTo(28,size,out);
    }
    private static byte[] readExact(Archive.Source source,long offset,int count)throws IOException{
        byte[] bytes=source.read(offset,count);if(bytes.length!=count)throw new EOFException("Short XPCM resource read");return bytes;
    }
    private static void waveHeader(OutputStream out,int size,int channels,int sampleRate,int bits)throws IOException{
        out.write(new byte[]{82,73,70,70});le(out,size+36,4);out.write(new byte[]{87,65,86,69,102,109,116,32});le(out,16,4);le(out,1,2);le(out,channels,2);le(out,sampleRate,4);le(out,sampleRate*channels*bits/8,4);le(out,channels*bits/8,2);le(out,bits,2);out.write(new byte[]{100,97,116,97});le(out,size,4);
    }
    public byte[] wav()throws IOException{
        if(!codec.equals("pcm"))throw new IOException("Not PCM");ByteArrayOutputStream out=new ByteArrayOutputStream(payload.length+44);writeTo(out);return out.toByteArray();
    }
    private static void le(OutputStream out,int value,int count)throws IOException{for(int i=0;i<count;i++)out.write(value>>>(8*i));}
}
