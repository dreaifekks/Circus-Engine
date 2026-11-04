package org.circus.core;
import java.io.*;
import java.util.*;

/** DM resource slots (4DA0E8); UI slots 21..25 survive ordinary transfers. */
public final class Dc2Scene {
    public final String[] slots=new String[28];
    public final int[][] characters=new int[4][6];
    public int backgroundX,backgroundY,mode,targetX,targetY,scrollStep,scrollPeriod;
    public int portraitX,portraitY,transitionKind,transitionSerial;
    public long transitionDuration,transitionRemaining;
    public final int[] actorEffect=new int[4];
    public int particleKind,particleStrength,overlayKind,overlayStrength;public long effectClock;public boolean effectsFrozen;
    public void effect(int kind,int strength)throws IOException {
        if(kind==255){effectsFrozen=strength!=0;return;}
        if(kind==0){particleKind=particleStrength=overlayKind=overlayStrength=0;return;}
        if(kind==1||kind==2||kind==9){particleKind=strength==0?0:kind;particleStrength=strength;effectClock=0;}
        else if(kind==7){overlayKind=strength==0?0:kind;overlayStrength=strength;}
        else throw new IOException("Unimplemented DM procedural effect "+kind+","+strength);
        effectsFrozen=false;
    }
    public Frame shown=new Frame(),previous=new Frame();
    public static final class Frame {
        public String[] slots=new String[28];public int[][] actors=new int[4][6];public List<Dc2Ui.Quad> quads=new ArrayList<>();public int x,y;
        Frame(){Arrays.fill(slots,"");}
        void save(DataOutput o)throws IOException{for(String s:slots)o.writeUTF(s);for(int[] a:actors)for(int v:a)o.writeInt(v);Dc2Ui.saveQuads(o,quads);o.writeInt(x);o.writeInt(y);}
        void load(DataInput i)throws IOException{for(int n=0;n<28;n++)slots[n]=i.readUTF();for(int[] a:actors)for(int n=0;n<6;n++)a[n]=i.readInt();quads=Dc2Ui.loadQuads(i,2048);x=i.readInt();y=i.readInt();}
    }
    public void publish(Dc2Ui ui,int kind,long duration)throws IOException {
        previous=shown;Frame f=new Frame();f.slots=slots.clone();for(int n=0;n<4;n++)f.actors[n]=characters[n].clone();f.quads=Dc2Ui.copyQuads(ui.quads);f.quads.addAll(0,Dc2Ui.copyQuads(ui.quadTemplates.get(0)));f.x=backgroundX;f.y=backgroundY;shown=f;mode=0;
        transitionKind=kind;transitionDuration=transitionRemaining=duration;transitionSerial++;revision++;
    }
    public long scrollClock;
    /** Mac app, following DC2PC.exe. None of this is saved: a save made mid-move simply lands at the end of the move. */
    public boolean pauseEffectsInTransition;
    private int slideActor=-1,slideFromX,slideFromY,slideStep;private long slideClock;
    private int shakeKind,shakeMask;private long shakeLength,shakeLeft;
    /** 421C40: 10 ms ticks, x and y approach independently, and the first step comes at once. */
    public void beginSlide(int n,int x,int y,int step){slideActor=n;slideFromX=x;slideFromY=y;slideStep=step;slideClock=0;}
    public void beginShake(int kind,int mask,long ms){shakeKind=kind;shakeMask=mask;shakeLength=shakeLeft=ms;}
    private static final int[] SHAKE_Y={0,14,0,24,14,24,0};
    /** The actor as drawn right now; the frame itself already holds where it ends up. */
    public int[] presented(int n,int[] a){
        if(n!=slideActor&&(shakeLeft<=0||(shakeMask&1<<n)==0))return a;int[] c=a.clone();
        if(n==slideActor){long d=(1+slideClock/10)*slideStep;c[0]=approach(slideFromX,a[0],d);c[1]=approach(slideFromY,a[1],d);}
        if(shakeLeft>0&&(shakeMask&1<<n)!=0){int beat=(int)((shakeLength-shakeLeft)/40)+1; // 4215FC, 4217FF
            if(shakeKind==51)c[0]+=beat%3==1?-24:beat%3==2?24:0;else c[1]+=SHAKE_Y[Math.min(SHAKE_Y.length-1,beat-1)];}
        return c;
    }
    public long revision;
    public Dc2Scene(){Arrays.fill(slots,"");for(int[] c:characters)c[2]=100;}
    public void reset(){Arrays.fill(slots,0,21,"");mode=0;revision++;}
    public void elapse(long ms){if(!effectsFrozen&&!(pauseEffectsInTransition&&transitionRemaining>0))effectClock+=ms;
        if(slideActor>=0){slideClock+=ms;long d=(1+slideClock/10)*slideStep;int[] c=characters[slideActor];if(approach(slideFromX,c[0],d)==c[0]&&approach(slideFromY,c[1],d)==c[1])slideActor=-1;}
        if(shakeLeft>0)shakeLeft=Math.max(0,shakeLeft-ms);
        transitionRemaining=Math.max(0,transitionRemaining-ms);if(scrollStep<=0)return;scrollClock+=ms;long ticks=scrollClock/scrollPeriod;scrollClock%=scrollPeriod;long distance=Math.min(Integer.MAX_VALUE,ticks*scrollStep);backgroundX=approach(backgroundX,targetX,distance);backgroundY=approach(backgroundY,targetY,distance);shown.x=backgroundX;shown.y=backgroundY;if(backgroundX==targetX&&backgroundY==targetY)scrollStep=0;}
    private static int approach(int from,int to,long distance){return from<to?(int)Math.min(to,from+distance):(int)Math.max(to,from-distance);}
    public void save(DataOutput o)throws IOException{o.writeInt(particleKind);o.writeInt(particleStrength);o.writeInt(overlayKind);o.writeInt(overlayStrength);o.writeLong(effectClock);o.writeBoolean(effectsFrozen);for(String s:slots)o.writeUTF(s);for(int[] c:characters)for(int v:c)o.writeInt(v);for(int v:new int[]{backgroundX,backgroundY,mode,targetX,targetY,scrollStep,scrollPeriod})o.writeInt(v);o.writeLong(scrollClock);o.writeLong(revision);o.writeInt(portraitX);o.writeInt(portraitY);o.writeInt(transitionKind);o.writeInt(transitionSerial);o.writeLong(transitionDuration);o.writeLong(transitionRemaining);for(int v:actorEffect)o.writeInt(v);shown.save(o);previous.save(o);}
    public void load(DataInput i)throws IOException{particleKind=i.readInt();particleStrength=i.readInt();overlayKind=i.readInt();overlayStrength=i.readInt();effectClock=i.readLong();effectsFrozen=i.readBoolean();for(int n=0;n<slots.length;n++)slots[n]=i.readUTF();for(int[] c:characters)for(int n=0;n<c.length;n++)c[n]=i.readInt();backgroundX=i.readInt();backgroundY=i.readInt();mode=i.readInt();targetX=i.readInt();targetY=i.readInt();scrollStep=i.readInt();scrollPeriod=i.readInt();scrollClock=i.readLong();revision=i.readLong();portraitX=i.readInt();portraitY=i.readInt();transitionKind=i.readInt();transitionSerial=i.readInt();transitionDuration=i.readLong();transitionRemaining=i.readLong();for(int n=0;n<4;n++)actorEffect[n]=i.readInt();shown.load(i);previous.load(i);if(scrollStep<0||scrollStep>0&&scrollPeriod<=0)throw new IOException("Invalid DM scroll");}
}
