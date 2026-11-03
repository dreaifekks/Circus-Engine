package org.circus.core;

import java.io.*;
import java.util.*;

/** Native scroll-list data. Screen rectangles and entry data are separate in MES. */
public final class ScriptList {
    public static final class Item {
        public int id;public String action="";
        public final List<Dc2Ui.Quad> sprites=new ArrayList<>();
    }
    public final List<Item> items=new ArrayList<>();
    public final List<Dc2Ui.Quad> common=new ArrayList<>();
    public Dc2Ui.Quad marker;
    public int columns=1,textStyle,hovered=-1;
    public boolean active(){return !items.isEmpty();}
    public int size(){return items.size();}
    public void clear(){items.clear();common.clear();marker=null;columns=1;textStyle=0;hovered=-1;}
    public Item last()throws IOException{if(items.isEmpty())throw new IOException("List property without entry");return items.get(items.size()-1);}
    public void add(int[] a,int slot)throws IOException{
        if(a.length!=8||items.size()>=4096)throw new IOException("List item bounds");
        Item i=new Item();i.id=a[0];Dc2Ui.Quad q=new Dc2Ui.Quad();q.sx=a[2];q.sy=a[3];q.width=a[4]-a[2];q.height=a[5]-a[3];q.flags=slot;q.state=-2;i.sprites.add(q);items.add(i);
    }
    public static Dc2Ui.Quad sprite(int[] a)throws IOException{if(a.length!=8)throw new IOException("List sprite extension");Dc2Ui.Quad q=new Dc2Ui.Quad();q.x=(short)a[0];q.y=(short)a[1];q.sx=a[2];q.sy=a[3];q.width=a[4];q.height=a[5];q.flags=a[6];q.state=(short)a[7];return q;}
    public int clamp(int offset,int visible){return Math.max(0,Math.min(offset,Math.max(0,((items.size()+columns-1)/columns-(visible+columns-1)/columns)*columns)));}
    public int scrollAt(int x,int y,int[] bounds,int offset,int visible){if(x<bounds[0]-4||x>=bounds[2]+4||y<bounds[1]-20||y>=bounds[3]+20)return -1;if(y<bounds[1])return clamp(offset-columns,visible);if(y>=bounds[3])return clamp(offset+columns,visible);int max=clamp(Integer.MAX_VALUE,visible);int row=(int)Math.round((y-bounds[1])/(double)Math.max(1,bounds[3]-bounds[1])*max/columns);return clamp(row*columns,visible);}
    public Item at(int offset,int row,int visible){int n=clamp(offset,visible)+row;return n>=0&&n<items.size()?items.get(n):null;}
    public static List<Dc2Ui.Quad> visible(List<Dc2Ui.Quad> sprites,int state){
        List<Dc2Ui.Quad> out=new ArrayList<>();boolean found=false;for(Dc2Ui.Quad q:sprites)if(q.state==state)found=true;
        for(Dc2Ui.Quad q:sprites)if(q.state==-2||q.state==(found?state:99)||q.state==0&&state==99)out.add(q);return out;
    }
    public void save(DataOutput o)throws IOException{o.writeInt(columns);o.writeInt(textStyle);Dc2Ui.saveQuads(o,common);o.writeBoolean(marker!=null);if(marker!=null)marker.save(o);o.writeInt(items.size());for(Item i:items){o.writeInt(i.id);o.writeUTF(i.action);Dc2Ui.saveQuads(o,i.sprites);}}
    public void load(DataInput i)throws IOException{clear();columns=i.readInt();if(columns<1||columns>50)throw new IOException("List columns");textStyle=i.readInt();common.addAll(Dc2Ui.loadQuads(i,30));if(i.readBoolean())marker=Dc2Ui.Quad.load(i);int n=i.readInt();if(n<0||n>4096)throw new IOException("List count");while(n-->0){Item e=new Item();e.id=i.readInt();e.action=i.readUTF();e.sprites.addAll(Dc2Ui.loadQuads(i,30));items.add(e);}}
}
