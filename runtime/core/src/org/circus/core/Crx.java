package org.circus.core;

// Format algorithms adapted from GARbro, Copyright (C) 2015-2016 by morkt (MIT).
// Full permission and warranty notice is retained in THIRD_PARTY.md.
import java.io.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

public final class Crx {
    public interface Resolver { byte[] byOffset(long offset)throws IOException; byte[] byName(byte[] name)throws IOException; }
    public static final class Image {
        public final int x,y,width,height,bpp,stride;public final byte[] pixels;public final int[] palette;
        Image(int x,int y,int w,int h,int bpp,byte[] pixels,int[] palette){this.x=x;this.y=y;width=w;height=h;this.bpp=bpp;stride=(w*(bpp/8)+3)&~3;this.pixels=pixels;this.palette=palette;}
        public int[] argb(){return argb(false);}
        /** DD's 24-bit blitters skip magenta (44C420..44C436, 44C7C2..44C7D8). */
        public int[] argb(boolean colorKey){int[] out=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=y*stride+x*(bpp/8);int c=bpp==8?palette[pixels[p]&255]:((bpp==32?pixels[p+3]&255:255)<<24)|((pixels[p+2]&255)<<16)|((pixels[p+1]&255)<<8)|(pixels[p]&255);out[y*width+x]=colorKey&&bpp!=32&&(c&0xffffff)==0xff00ff?0:c;}return out;}
    }
    private Crx(){}
    public static Image decode(byte[] data,Resolver resolver)throws IOException {
        long magic=Binary.u32(data,0);if(magic==0x47585243L)return plain(data);
        if(magic!=0x44585243L||resolver==null)throw new IOException("CRXD requires archive context");
        Binary.range(0,36,data.length);byte[] base;
        try{base=resolver.byOffset(Binary.u32(data,8));}catch(FileNotFoundException e){int end=12;while(end<32&&data[end]!=0)end++;base=resolver.byName(Arrays.copyOfRange(data,12,end));}
        // GARbro's CRXD base must be CRXG; do not guess recursive base composition.
        byte[] change=diffBytes(data,resolver,new HashSet<Long>());
        // A 0x0 difference means this picture is its base unchanged: D.C.II P.C. has actors stored this way.
        Binary.range(0,20,change.length);if(Binary.u16(change,8)==0||Binary.u16(change,10)==0)return plain(base);
        Image original=plain(base),diff=plain(change);
        if(original.bpp!=diff.bpp||original.bpp==8)throw new IOException("Unsupported CRXD pixel layout");
        int left=Math.max(original.x,diff.x),top=Math.max(original.y,diff.y),right=Math.min(original.x+original.width,diff.x+diff.width),bottom=Math.min(original.y+original.height,diff.y+diff.height),ps=original.bpp/8;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
            int d=(y-original.y)*original.stride+(x-original.x)*ps,s=(y-diff.y)*diff.stride+(x-diff.x)*ps;
            for(int c=0;c<3;c++)original.pixels[d+c]=(byte)(original.pixels[d+c]+diff.pixels[s+c]);
            if(ps==4)original.pixels[d+3]=(byte)(original.pixels[d+3]-diff.pixels[s+3]);
        }
        return original;
    }
    private static byte[] diffBytes(byte[] data,Resolver resolver,Set<Long> seen)throws IOException {
        if(Binary.u32(data,0)!=0x44585243L)throw new IOException("Referenced difference is not CRXD");
        long magic=Binary.u32(data,32);if(magic==0x47585243L)return Arrays.copyOfRange(data,32,data.length);
        if(magic!=0x4a585243L)throw new IOException("Unknown CRXD payload");
        long offset=Binary.u32(data,40);if(seen.size()>=32 || !seen.add(offset))throw new IOException("Cyclic/excessive CRXJ dependency");
        return diffBytes(resolver.byOffset(offset),resolver,seen);
    }
    public static Image plain(byte[] data)throws IOException {return plain(data,false);}
    public static Image plain(byte[] data,boolean legacyPadding)throws IOException {
        if(Binary.u32(data,0)!=0x47585243L)throw new IOException("Expected CRXG");
        int x=Binary.i16(data,4),y=Binary.i16(data,6),w=Binary.u16(data,8),h=Binary.u16(data,10),comp=Binary.u16(data,12),flags=Binary.u16(data,14),depth=Binary.i16(data,16),mode=Binary.u16(data,18);
        if(w==0||h==0||(long)w*h>16777216L||comp<1||comp>3)throw new IOException("Invalid CRX geometry/compression");
        int bpp=depth==0?24:depth==1?32:8,ps=bpp/8,stride=(w*ps+3)&~3,p=20;int[] palette=null;
        if(bpp==8){int colors=Math.min(depth,256),entryBytes=depth==258?4:3;if(colors<=0)throw new IOException("Invalid palette");palette=new int[256];Binary.range(p,colors*entryBytes,data.length);for(int i=0;i<colors;i++){int r=data[p]&255,g=data[p+1]&255,b=data[p+2]&255;if(r==255&&g==0&&b==255)g=255;palette[i]=0xff000000|r<<16|g<<8|b;p+=entryBytes;}}
        if(comp>=3){long count=Binary.u32(data,p);p+=4;Binary.range(p,count*16,data.length);p+=(int)(count*16);}
        if((flags&16)!=0){long size=Binary.u32(data,p);p+=4;
            // Loose W.Y./P.P. translation images put the complete file length
            // here. Original encoders put the compressed payload length here.
            // Both still pass the full predictor/zlib integrity checks below.
            if(size!=data.length)Binary.range(p,size,data.length);
        }
        byte[] output=new byte[h*stride];
        if(comp==1)unpackLz(data,p,output);
        else try(DataInputStream in=new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(data,p,data.length-p)),65536))){
            for(int row=0;row<h;row++){
                int dst=row*stride,previous=dst-stride,line=w*ps;
                if(bpp==8){in.readFully(output,dst,line);continue;}
                int control=in.readUnsignedByte();if(control>4||(row==0&&control>=1&&control<=3))throw new IOException("Invalid CRX row predictor");
                switch(control){
                case 0:in.readFully(output,dst,ps);for(int j=ps;j<line;j++)output[dst+j]=(byte)(in.readUnsignedByte()+output[dst+j-ps]);break;
                case 1:for(int j=0;j<line;j++)output[dst+j]=(byte)(in.readUnsignedByte()+output[previous+j]);break;
                case 2:in.readFully(output,dst,ps);for(int j=ps;j<line;j++)output[dst+j]=(byte)(in.readUnsignedByte()+output[previous+j-ps]);break;
                case 3:for(int j=0;j<line-ps;j++)output[dst+j]=(byte)(in.readUnsignedByte()+output[previous+j+ps]);in.readFully(output,dst+line-ps,ps);break;
                case 4:for(int c=0;c<ps;c++){int col=0,val=in.readUnsignedByte();while(col<w){output[dst+col++*ps+c]=(byte)val;if(col==w)break;int next=in.readUnsignedByte();if(next==val){int n=in.readUnsignedByte();if(n>w-col)throw new IOException("CRX run exceeds row");for(int k=0;k<n;k++)output[dst+col++*ps+c]=(byte)val;if(col<w)val=in.readUnsignedByte();}else val=next;}}break;
                default:throw new IOException("CRX predictor");
                }
            }
            // Consume the zlib trailer, reject extra decompressed bytes and truncated streams.
            int extra=in.read(),padding=0,alignment=h*(stride-w*ps),limit=alignment+3;
            while(legacyPadding&&extra==0&&padding<limit){padding++;extra=in.read();}
            // PC encoders reserve bitmap row alignment after their predictor stream.
            // cvpats2: 795x984 RGB -> 3*984 zeros; namechip2: 593x384 -> 384 zeros.
            if(extra!=-1||padding>3&&padding!=alignment)throw new IOException("Extra CRX decoded payload");
        }
        if(bpp==32&&mode!=1){int flip=mode==2?0:255;for(int row=0;row<h;row++)for(int col=0;col<w;col++){int q=row*stride+4*col;byte a=output[q],b=output[q+1],g=output[q+2],r=output[q+3];output[q]=b;output[q+1]=g;output[q+2]=r;output[q+3]=(byte)(a^flip);}}
        return new Image(x,y,w,h,bpp,output,palette);
    }
    private static void unpackLz(byte[] data,int p,byte[] out)throws IOException {
        byte[] window=new byte[65536];int flags=0,win=0,dst=0;
        while(dst<out.length){flags>>=1;if((flags&256)==0)flags=Binary.u8(data,p++)|0xff00;
            if((flags&1)!=0){byte value=(byte)Binary.u8(data,p++);out[dst++]=window[win]=value;win=(win+1)&65535;}
            else {int control=Binary.u8(data,p++),count,offset;
                if(control>=192){offset=((control&3)<<8)|Binary.u8(data,p++);count=4+((control>>2)&15);}
                else if((control&128)!=0){offset=control&31;count=2+((control>>5)&3);if(offset==0)offset=Binary.u8(data,p++);}
                else if(control==127){count=2+Binary.u16(data,p);offset=Binary.u16(data,p+2);p+=4;}
                else {offset=Binary.u16(data,p);p+=2;count=control+4;}
                int src=win-offset;for(int i=0;i<count&&dst<out.length;i++){byte value=window[src++&65535];out[dst++]=window[win]=value;win=(win+1)&65535;}
            }
        }
    }
}
