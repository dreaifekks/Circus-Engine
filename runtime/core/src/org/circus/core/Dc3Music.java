package org.circus.core;

import java.io.*;
import java.util.*;

/** DD native services 200..202 (40A3B0, order builder 408FF0).
 * Numeric catalogue is the 4AAEC8 table; captions remain in MUSIC.MES. */
public final class Dc3Music {
    public static final int[] TRACKS={1,2,3,4,5,6,7,8,9,10,11,13,14,15,16,17,18,19,20,21,22,23,27,28,29,31,32,33,34,35,36,40,80,81,82,83,84,85,109,110,112,113,115,116,121,122,130,131,132,133,134,136,138,142,143,144,146,148,149,150,151,152,153,155,156,160,162,163,173,175,176,178,184,185,187,188,191,193,199,260,261,262,263,265,280,281,282,283};
    /** W.Y.'s own list in its exe; it is exactly the tracks shipped with that game. */
    public static final int[] WY_TRACKS={1,2,3,4,5,6,7,8,9,10,11,13,14,15,16,17,18,19,20,21,22,23,24,25,27,29,30,31,32,33,34,35,36,40,80,81,83,84,85,110,111,116,129,142,143,144,149,150,151,152,153,154,156,158,159,162,163,170,174,175,176,177,199,260,261,262,263,264,265,270,271,272,273,274};
    public int[] tracks=TRACKS;
    public int[] order=tracks.clone();public void useTracks(int[] list){tracks=list;order=list.clone();selected=0;}public int selected,seed=0x44564d;public boolean caption;
    public int seek=-1,seekGeneration;public long position,duration;
    private int random(int n){seed=seed*214013+2531011;return ((seed>>>16)&32767)%n;}
    public int catalogueIndex(){int track=order[selected];for(int n=0;n<tracks.length;n++)if(tracks[n]==track)return n;return 0;}
    private int favorites(Dc3State s){int n=0;for(int f:s.musicFavorites)if(f!=0)n++;return n;}
    // The installed MUSIC.MES writes 100:26; native 408FF0 still reads the older 9:30.
    // Normalize that legacy service input, so the original random button selects shuffle.
    private int mode(Variables v)throws IOException{return v.read(100,26);}
    private void rebuild(Variables v)throws IOException{int current=order[selected];System.arraycopy(tracks,0,order,0,order.length);if(mode(v)>1){for(int n=order.length-1;n>0;n--){int k=random(n+1),t=order[n];order[n]=order[k];order[k]=t;}int hold=Math.max(0,Math.min(order.length-1,v.read(7,98)));for(int n=0;n<order.length;n++)if(order[n]==current){order[n]=order[hold];order[hold]=current;break;}selected=hold;}else{for(int n=0;n<order.length;n++)if(order[n]==current)selected=n;}v.write(7,98,selected);}
    private void move(int direction,Variables v,Dc3State s)throws IOException{boolean filtered=favorites(s)>0&&s.read(100,21)!=0;for(int n=0;n<order.length;n++){selected=Math.floorMod(selected+direction,order.length);if(!filtered||s.musicFavorites[order[selected]]!=0)break;}v.write(7,98,selected);}
    /** Returns a resource ID to play, or -1 for non-playback operations. */
    public int command(int service,int mode,Variables v,Dc3State s)throws IOException{
        if(service==201){if(mode==255||mode==254)Arrays.fill(s.musicFavorites,mode==255?1:0);else{int n=s.read(99,246)+mode;if(n>=0&&n<tracks.length){int id=tracks[n];s.musicFavorites[id]=s.musicFavorites[id]==0?1:0;}}return -1;}
        if(service==202){int n=s.read(99,246)+mode;v.write(3,0,n>=0&&n<tracks.length?s.musicFavorites[tracks[n]]:0);return -1;}
        int count=favorites(s);if(mode==6){v.write(3,0,count);return -1;}if(count==0)s.write(100,21,0);
        switch(mode){
        case 0:rebuild(v);if(s.read(100,21)!=0){if(s.musicFavorites[order[selected]]==0)move(1,v,s);s.write(99,254,catalogueIndex());}return -1;
        case 1:case 99:selected=Math.max(0,Math.min(order.length-1,v.read(7,98)));v.write(3,11,0);s.write(99,254,catalogueIndex());if(mode==99){seek=s.read(99,187);seekGeneration++;return -1;}return order[selected];
        case 2:move(1,v,s);return -1;
        case 3:move(-1,v,s);return -1;
        case 4:caption=true;return -1;
        case 5:int n=Math.max(0,Math.min(tracks.length-1,v.read(7,98)));int track=tracks[n];v.write(3,11,0);if(s.read(100,21)!=0)s.musicFavorites[track]=1;for(int k=0;k<order.length;k++)if(order[k]==track)selected=k;v.write(7,98,selected);s.write(99,254,n);return track;
        default:throw new IOException("DD music service mode "+mode);
        }
    }
    public void save(DataOutput o,Dc3State state)throws IOException{o.writeInt(selected);o.writeInt(seed);o.writeBoolean(caption);for(int n:order)o.writeInt(n);for(int n:state.musicFavorites)o.writeInt(n);}
    public void load(DataInput i,Dc3State state)throws IOException{selected=i.readInt();seed=i.readInt();caption=i.readBoolean();if(selected<0||selected>=order.length)throw new IOException("Music selection");Set<Integer> seen=new HashSet<>();for(int n=0;n<order.length;n++){order[n]=i.readInt();boolean valid=false;for(int t:tracks)if(t==order[n])valid=true;if(!valid||!seen.add(order[n]))throw new IOException("Music order");}for(int n=0;n<state.musicFavorites.length;n++)state.musicFavorites[n]=i.readInt();}
}
