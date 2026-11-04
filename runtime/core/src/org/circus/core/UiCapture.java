package org.circus.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Immutable render-texture value. A logical capture refers to the texture that
 * existed before it; a platform may immediately flatten it to portable ARGB. */
public final class UiCapture {
    public static final int LEGACY=1,DD=2,ARGB=3;
    private static final int MAX_BYTES=8*1024*1024,MAX_DEPTH=256;
    public interface Renderer {UiCapture flatten(UiCapture capture)throws IOException;default UiCapture screen()throws IOException{return null;}}
    public final int kind,depth;
    public final UiCapture previous;
    private final byte[] payload;
    private final int bytes;
    private UiCapture(int kind,byte[] payload,UiCapture previous)throws IOException {
        if(kind<LEGACY||kind>ARGB||kind==ARGB&&previous!=null)throw new IOException("Invalid UI capture kind");
        long total=(long)payload.length+(previous==null?0:previous.bytes);
        depth=1+(previous==null?0:previous.depth);
        if(depth>MAX_DEPTH||payload.length==0||total>MAX_BYTES)throw new IOException("UI capture limit; attach a raster renderer for long capture chains");
        this.kind=kind;this.payload=payload.clone();this.previous=previous;bytes=(int)total;
    }
    public static UiCapture logical(int kind,byte[] payload,boolean depends,UiCapture previous)throws IOException {
        if(kind!=LEGACY&&kind!=DD)throw new IOException("Invalid logical capture kind");
        if(depends&&previous==null)throw new IOException("UI capture refers to an absent previous texture");
        return new UiCapture(kind,payload,depends?previous:null);
    }
    public DataInputStream input(){return new DataInputStream(new ByteArrayInputStream(payload));}
    public static UiCapture pixels(int width,int height,int[] argb)throws IOException {
        dimensions(width,height);if(argb.length!=(long)width*height)throw new IOException("UI capture pixel count");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(bytes);header.writeInt(width);header.writeInt(height);
        // Batch pixels before crossing the native zlib boundary. A menu capture
        // otherwise makes one compression call per pixel (480,000 at 800x600).
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(bytes),32768))){for(int pixel:argb)out.writeInt(pixel);}
        return new UiCapture(ARGB,bytes.toByteArray(),null);
    }
    public static final class Pixels {
        public final int width,height;public final int[] argb;
        Pixels(int width,int height){this.width=width;this.height=height;argb=new int[width*height];}
    }
    public Pixels pixels()throws IOException {
        if(kind!=ARGB)throw new IOException("Capture is not a raster");
        DataInputStream header=input();int width=header.readInt(),height=header.readInt();dimensions(width,height);Pixels result=new Pixels(width,height);
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new InflaterInputStream(header),32768))){for(int n=0;n<result.argb.length;n++)result.argb[n]=in.readInt();if(in.read()!=-1)throw new IOException("Extra UI capture pixels");}
        return result;
    }
    private static void dimensions(int w,int h)throws IOException{if(w<1||h<1||w>2048||h>2048||(long)w*h>2048*2048)throw new IOException("Invalid UI capture dimensions");}
    public static void save(DataOutput out,UiCapture capture)throws IOException {
        out.writeInt(capture==null?0:capture.depth);List<UiCapture> chain=new ArrayList<>();for(UiCapture c=capture;c!=null;c=c.previous)chain.add(c);
        for(int n=chain.size()-1;n>=0;n--){UiCapture c=chain.get(n);out.writeByte(c.kind);out.writeInt(c.payload.length);out.write(c.payload);}
    }
    public static UiCapture load(DataInput in)throws IOException {
        int count=in.readInt();if(count<0||count>MAX_DEPTH)throw new IOException("UI capture depth");UiCapture previous=null;int total=0;
        for(int n=0;n<count;n++){int kind=in.readUnsignedByte(),size=in.readInt();if(size<1||size>MAX_BYTES-total)throw new IOException("UI capture size");byte[] bytes=new byte[size];in.readFully(bytes);previous=new UiCapture(kind,bytes,previous);total+=size;}
        return previous;
    }
}
