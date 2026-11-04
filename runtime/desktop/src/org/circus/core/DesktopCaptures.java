package org.circus.core;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Resolves the previous texture in a scoped, iterative render. The running PC
 * backend flattens each new capture so repeated menu visits do not grow saves. */
final class DesktopCaptures implements UiCapture.Renderer {
    interface Drawer {BufferedImage draw(int kind,DataInputStream input)throws IOException;}
    private final Drawer drawer;
    private final int width,height;
    private boolean drawing;
    private BufferedImage previous;
    private final Map<UiCapture,BufferedImage> cache=new LinkedHashMap<UiCapture,BufferedImage>(4,.75f,true){protected boolean removeEldestEntry(Map.Entry<UiCapture,BufferedImage> e){return size()>4;}};
    /** What the window shows now, for scripts that take the screen as it is. */
    java.util.function.Supplier<BufferedImage> screen=()->null;
    DesktopCaptures(int width,int height,Drawer drawer){this.width=width;this.height=height;this.drawer=drawer;}
    @Override public UiCapture screen()throws IOException{
        BufferedImage image=screen.get();if(image==null)return null;
        UiCapture flat=UiCapture.pixels(width,height,image.getRGB(0,0,width,height,null,0,width));cache.put(flat,image);return flat;
    }
    BufferedImage resolve(UiCapture capture)throws IOException {
        if(drawing){if(previous==null)throw new IOException("Missing preceding UI texture");return previous;}
        if(capture==null)throw new IOException("Missing UI texture");
        List<UiCapture> pending=new ArrayList<>();UiCapture cursor=capture;
        while(cursor!=null&&!cache.containsKey(cursor)){pending.add(cursor);cursor=cursor.previous;}
        BufferedImage result=cursor==null?null:cache.get(cursor);
        try{for(int n=pending.size()-1;n>=0;n--){UiCapture c=pending.get(n);
            if(c.kind==UiCapture.ARGB){UiCapture.Pixels p=c.pixels();if(p.width!=width||p.height!=height)throw new IOException("UI capture canvas mismatch");result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,width,height,p.argb,0,width);}
            else{previous=result;drawing=true;DataInputStream input=c.input();result=drawer.draw(c.kind,input);if(input.available()!=0)throw new IOException("Extra logical UI capture data");}
            cache.put(c,result);
        }}finally{drawing=false;previous=null;}
        return result;
    }
    public UiCapture flatten(UiCapture capture)throws IOException {
        BufferedImage image=resolve(capture);UiCapture flat=UiCapture.pixels(width,height,image.getRGB(0,0,width,height,null,0,width));cache.put(flat,image);return flat;
    }
}
