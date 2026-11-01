package org.circus.core;

import java.io.*;
import java.util.*;

/** Serializable logical scene. Resource loading and drawing belong to the platform. */
public final class Dc3Scene {
    public final Dc3Petals petals=new Dc3Petals();
    public final Dc3Camera camera=new Dc3Camera();
    public final Dc3Transition fade=new Dc3Transition();
    public Frame published=new Frame();
    public static final class Frame {
        public final Layer[] layers=new Layer[43];public final List<Quad> quads=new ArrayList<>();
        public Dc3Petals petals=new Dc3Petals();public Dc3Camera camera=new Dc3Camera();public boolean fog,clouds;public long environmentTime;
        public Frame(){for(int i=0;i<layers.length;i++)layers[i]=new Layer();}
        void save(DataOutput out)throws IOException {petals.save(out);camera.save(out);out.writeBoolean(fog);out.writeBoolean(clouds);out.writeLong(environmentTime);for(Layer l:layers)l.save(out);out.writeInt(quads.size());for(Quad q:quads)q.save(out);}
        void load(DataInput in)throws IOException {petals.load(in);camera.load(in);fog=in.readBoolean();clouds=in.readBoolean();environmentTime=in.readLong();if(environmentTime<0)throw new IOException("Invalid environment clock");for(Layer l:layers)l.load(in);int n=in.readInt();if(n<0||n>1024)throw new IOException("Frame quad limit");quads.clear();for(int i=0;i<n;i++){Quad q=new Quad();q.load(in);quads.add(q);}}
    }
    public static final class Layer {
        public String resource="";
        public boolean centered;
        public int x,y,scaleX=100,scaleY=100,pivotX,pivotY,alpha=255,priority=50;
        public Layer copy(){Layer r=new Layer();r.resource=resource;r.centered=centered;r.x=x;r.y=y;r.scaleX=scaleX;r.scaleY=scaleY;r.pivotX=pivotX;r.pivotY=pivotY;r.alpha=alpha;r.priority=priority;return r;}
        void save(DataOutput out)throws IOException {out.writeUTF(resource);out.writeBoolean(centered);for(int n:new int[]{x,y,scaleX,scaleY,pivotX,pivotY,alpha,priority})out.writeInt(n);}
        void load(DataInput in)throws IOException {resource=in.readUTF();centered=in.readBoolean();x=in.readInt();y=in.readInt();scaleX=in.readInt();scaleY=in.readInt();pivotX=in.readInt();pivotY=in.readInt();alpha=in.readInt();priority=in.readInt();if(alpha<0||alpha>255||priority<0||priority>100||Math.abs((long)scaleX)>10000||Math.abs((long)scaleY)>10000)throw new IOException("Invalid scene transform");}
    }
    // Logical resource slots are not the original render-object indices.
    public final Layer[] slots=new Layer[43];
    public final String[] pending=new String[43];
    /** Slot layout of the build this scene belongs to; see Dc3Layout. */
    public Dc3Layout layout=Dc3Layout.DD;
    public boolean centerNextBackground;
    public int revision,transition,transitionParameter;
    public boolean novelMode,fog,clouds;public long environmentTime;
    public int uiX,uiY;
    public static final class Quad {
        public String resource;public int x,y,sx,sy,width,height;public boolean opaque;
        public Quad copy(){Quad q=new Quad();q.resource=resource;q.x=x;q.y=y;q.sx=sx;q.sy=sy;q.width=width;q.height=height;q.opaque=opaque;return q;}
        void save(DataOutput out)throws IOException {out.writeUTF(resource);for(int n:new int[]{x,y,sx,sy,width,height})out.writeInt(n);out.writeBoolean(opaque);}
        void load(DataInput in)throws IOException {resource=in.readUTF();x=in.readInt();y=in.readInt();sx=in.readInt();sy=in.readInt();width=in.readInt();height=in.readInt();opaque=in.readBoolean();}
    }
    public final List<Quad> quads=new ArrayList<>();
    public static final class Motion {public int slot,axis,repeats=1;public long elapsed;public boolean bipolar;
        Motion copy(){Motion m=new Motion();m.slot=slot;m.axis=axis;m.repeats=repeats;m.elapsed=elapsed;m.bipolar=bipolar;return m;}
        void save(DataOutput out)throws IOException{out.writeInt(slot);out.writeInt(axis);out.writeInt(repeats);out.writeLong(elapsed);out.writeBoolean(bipolar);}
        void load(DataInput in)throws IOException{slot=in.readInt();axis=in.readInt();repeats=in.readInt();elapsed=in.readLong();bipolar=in.readBoolean();if(slot<0||slot>=43||axis<0||axis>1||repeats<1||repeats>255||elapsed<0||elapsed>86400000)throw new IOException("Invalid motion");}
    }
    public final List<Motion> motions=new ArrayList<>();
    public final List<Motion> recorded=new ArrayList<>();public boolean recording;
    public static final class Move {public int slot,x0,y0,x1,y1,a0,a1;public long elapsed,duration;
        void save(DataOutput out)throws IOException{for(int n:new int[]{slot,x0,y0,x1,y1,a0,a1})out.writeInt(n);out.writeLong(elapsed);out.writeLong(duration);}
        void load(DataInput in)throws IOException{slot=in.readInt();x0=in.readInt();y0=in.readInt();x1=in.readInt();y1=in.readInt();a0=in.readInt();a1=in.readInt();elapsed=in.readLong();duration=in.readLong();if(slot<0||slot>=43||duration<=0||duration>86400000||elapsed<0||elapsed>duration||a0<0||a0>255||a1<0||a1>255)throw new IOException("Invalid move");}
    }
    public final List<Move> moves=new ArrayList<>();
    public void move(int slot,int x,int y,int alpha,long duration)throws IOException {if(recording)throw new IOException("Recording non-shake motion is not implemented");if(duration<=0||duration>86400000)throw new IOException("Invalid motion duration");stop(slot);Move m=new Move();m.slot=slot;m.x0=slots[slot].x;m.y0=slots[slot].y;m.a0=slots[slot].alpha;m.x1=x;m.y1=y;m.a1=alpha;m.duration=duration;moves.add(m);}
    public void stop(int slot){motions.removeIf(m->m.slot==slot);moves.removeIf(m->m.slot==slot);}
    public void stop(){motions.clear();moves.clear();}
    public void record(boolean begin){recording=begin;if(begin)recorded.clear();}
    public void replay(int count)throws IOException{if(count<0||count>255)throw new IOException("Invalid animation repeat count");recording=false;for(Motion saved:recorded){stop(saved.slot);if(count!=0){Motion m=saved.copy();m.repeats=count;m.elapsed=0;motions.add(m);}}}
    public void shake(int slot,int axis){shake(slot,axis,axis==1);}
    public void shake(int slot,int axis,boolean bipolar){Motion m=new Motion();m.slot=slot;m.axis=axis;m.bipolar=bipolar;if(recording){recorded.removeIf(r->r.slot==slot);recorded.add(m);}else{stop(slot);motions.add(m);}}
    public long animationRemaining(){long n=Math.max(fade.duration-fade.elapsed,camera.duration-camera.elapsed);for(Move m:moves)n=Math.max(n,m.duration-m.elapsed);for(Motion m:motions)if(m.repeats!=255)n=Math.max(n,(m.repeats*40000L+59)/60-m.elapsed);return Math.max(0,n);}
    public void elapse(long ms){ms=Math.max(0,ms);environmentTime=(environmentTime+ms)%120000;camera.elapse(ms);fade.elapse(ms);petals.elapse(ms);for(Iterator<Motion> it=motions.iterator();it.hasNext();){Motion m=it.next();m.elapsed+=ms;if(m.repeats==255)m.elapsed%=2000;else if(m.elapsed*60>=m.repeats*40000L)it.remove();}
        for(Iterator<Move> it=moves.iterator();it.hasNext();){Move m=it.next();m.elapsed=Math.min(m.duration,m.elapsed+ms);float t=m.elapsed/(float)m.duration;Layer l=slots[m.slot];l.x=m.x0+Math.round((m.x1-m.x0)*t);l.y=m.y0+Math.round((m.y1-m.y0)*t);l.alpha=m.a0+Math.round((m.a1-m.a0)*t);if(m.elapsed==m.duration)it.remove();}
    }
    public float displacement(int slot,int axis){for(Motion m:motions)if(m.slot==slot&&m.axis==axis){float t=(m.elapsed*60/1000f%40)/10;int segment=(int)t;float[] v={0,8,0,m.bipolar?-8:8,0};return v[segment]+(v[segment+1]-v[segment])*(t-segment);}return 0;}
    public Dc3Scene(){for(int i=0;i<slots.length;i++)slots[i]=new Layer();slots[1].priority=0;}
    /** 427620 / 4240C0 releases scenario images; the native UI slots above uiSlotBase survive. */
    public void resetForTransfer(){
        for(Move m:moves){Layer l=slots[m.slot];l.x=m.x1;l.y=m.y1;l.alpha=m.a1;}
        stop();recorded.clear();recording=false;quads.clear();petals.start(0);fog=false;clouds=false;
        fade.elapsed=fade.duration;for(int i=0;i<slots.length;i++){if(i<layout.uiBase)slots[i].resource="";pending[i]=null;slots[i].priority=i==1?0:50;}
        centerNextBackground=false;published=capture();revision++;
    }
    public void publish(){boolean changed=false;for(int i=0;i<slots.length;i++)if(pending[i]!=null){slots[i].resource=pending[i].equals("-")?"":pending[i];if(i==1)slots[i].centered=centerNextBackground;pending[i]=null;changed=true;}centerNextBackground=false;if(changed)revision++;published=capture();}
    public Frame capture(){Frame f=new Frame();f.petals=petals.copy();f.camera=camera.copy();f.fog=fog;f.clouds=clouds;f.environmentTime=environmentTime;for(int i=0;i<slots.length;i++){f.layers[i]=slots[i].copy();f.layers[i].x+=(int)displacement(i,0);f.layers[i].y+=(int)displacement(i,1);}for(Quad q:quads)f.quads.add(q.copy());return f;}
    public void save(DataOutput out)throws IOException {
        petals.save(out);camera.save(out);out.writeBoolean(clouds);out.writeLong(environmentTime);published.save(out);fade.save(out);
        out.writeInt(revision);out.writeInt(transition);out.writeInt(transitionParameter);out.writeBoolean(novelMode);out.writeBoolean(fog);out.writeBoolean(centerNextBackground);
        out.writeInt(uiX);out.writeInt(uiY);out.writeInt(quads.size());for(Quad q:quads){out.writeUTF(q.resource);for(int n:new int[]{q.x,q.y,q.sx,q.sy,q.width,q.height})out.writeInt(n);out.writeBoolean(q.opaque);}
        out.writeInt(motions.size());for(Motion m:motions)m.save(out);out.writeBoolean(recording);out.writeInt(recorded.size());for(Motion m:recorded)m.save(out);out.writeInt(moves.size());for(Move m:moves)m.save(out);
        for(int i=0;i<slots.length;i++){slots[i].save(out);out.writeBoolean(pending[i]!=null);if(pending[i]!=null)out.writeUTF(pending[i]);}
    }
    public void load(DataInput in)throws IOException {
        petals.load(in);camera.load(in);clouds=in.readBoolean();environmentTime=in.readLong();if(environmentTime<0||environmentTime>=120000)throw new IOException("Invalid environment clock");published.load(in);fade.load(in);
        revision=in.readInt();transition=in.readInt();transitionParameter=in.readInt();novelMode=in.readBoolean();fog=in.readBoolean();centerNextBackground=in.readBoolean();
        uiX=in.readInt();uiY=in.readInt();int count=in.readInt();if(count<0||count>1024)throw new IOException("UI quad limit");quads.clear();for(int i=0;i<count;i++){Quad q=new Quad();q.resource=in.readUTF();q.x=in.readInt();q.y=in.readInt();q.sx=in.readInt();q.sy=in.readInt();q.width=in.readInt();q.height=in.readInt();q.opaque=in.readBoolean();quads.add(q);}
        count=in.readInt();if(count<0||count>43)throw new IOException("Motion limit");motions.clear();for(int i=0;i<count;i++){Motion m=new Motion();m.load(in);motions.add(m);}recording=in.readBoolean();count=in.readInt();if(count<0||count>43)throw new IOException("Recorded motion limit");recorded.clear();for(int i=0;i<count;i++){Motion m=new Motion();m.load(in);recorded.add(m);}count=in.readInt();if(count<0||count>43)throw new IOException("Move limit");moves.clear();for(int i=0;i<count;i++){Move m=new Move();m.load(in);moves.add(m);}
        for(int i=0;i<slots.length;i++){slots[i].load(in);pending[i]=in.readBoolean()?in.readUTF():null;}
    }
}
