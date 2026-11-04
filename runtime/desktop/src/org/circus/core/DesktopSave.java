package org.circus.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Atomic desktop save container. The embedded VM bytes retain their own versioning. */
public final class DesktopSave {
    private static final int MAGIC=0x44435332, LIMIT=64*1024*1024;
    public byte[] state,preview=new byte[0];
    public String caption="";
    /** Read from the state when the save is opened; not stored separately. */
    public transient String script="";
    public long created,textElapsed; public int icon=-1;
    public final String[] audio={"","","","","",""};
    public final long[] position=new long[6];
    public static BufferedImage scalePreview(BufferedImage image,int width,int height){
        BufferedImage scaled=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=scaled.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(image,0,0,width,height,null);}finally{g.dispose();}
        return scaled;
    }
    public static byte[] thumbnail(BufferedImage image,int width,int height)throws IOException{
        BufferedImage scaled=scalePreview(image,width,height);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try{ImageIO.write(scaled,"png",bytes);return bytes.toByteArray();}finally{scaled.flush();}
    }
    public byte[] encode()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeLong(created);out.writeInt(icon);out.writeUTF(caption);for(int n=0;n<6;n++){out.writeUTF(audio[n]);out.writeLong(position[n]);}
        blob(out,state);blob(out,preview);out.writeLong(textElapsed);out.flush();byte[] payload=bytes.toByteArray();out.write(digest(payload));return bytes.toByteArray();
    }
    public static DesktopSave decode(byte[] bytes)throws IOException{
        DesktopSave save=new DesktopSave();if(bytes.length<4||new DataInputStream(new ByteArrayInputStream(bytes)).readInt()!=MAGIC){save.state=bytes;return save;}
        if(bytes.length>LIMIT||bytes.length<36)throw new IOException("Invalid desktop save size");
        byte[] payload=Arrays.copyOf(bytes,bytes.length-32);
        if(!MessageDigest.isEqual(digest(payload),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IOException("Desktop save checksum mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload));in.readInt();save.created=in.readLong();save.icon=in.readInt();save.caption=in.readUTF();
        for(int n=0;n<6;n++){save.audio[n]=in.readUTF();save.position[n]=in.readLong();if(save.position[n]<0)throw new IOException("Invalid audio cursor");}
        save.state=blob(in,LIMIT);save.preview=blob(in,1024*1024);if(in.available()==8){save.textElapsed=in.readLong();if(save.textElapsed<0)throw new IOException("Invalid text cursor");}if(in.available()!=0)throw new IOException("Trailing desktop save data");return save;
    }
    public BufferedImage image()throws IOException{return preview.length==0?null:ImageIO.read(new ByteArrayInputStream(preview));}
    private static void blob(DataOutput out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] blob(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max||n>in.available())throw new IOException("Invalid desktop save block");byte[] b=new byte[n];in.readFully(b);return b;}
    private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
