package org.circus.core;

import java.io.*;
import java.util.*;

/** Dialogue history, kept separately from the VM's variable undo stack. */
public final class Dc2History {
    public static final int LIMIT=200, REWIND_LIMIT=64;
    public static final class Entry {
        public String speaker,body,voice,script;public int pc;public byte[] resume=new byte[0],scene=new byte[0];
        Entry(String s,String b,String v,String file,int offset){speaker=s;body=b;voice=v;script=file;pc=offset;}
    }
    public final List<Entry> entries=new ArrayList<>();
    public int limit=LIMIT;
    public int cursor,selected=-1,rewindRequest=-1;public boolean active,omitFromSnapshot;
    public boolean previewEnabled=true;
    public void append(String speaker,String body,String voice,String script,int pc){
        entries.add(new Entry(speaker,body,voice,script,pc));
        while(entries.size()>limit)entries.remove(0);
        for(int n=0;n<entries.size()-REWIND_LIMIT;n++)entries.get(n).resume=new byte[0];
    }
    public void open(){active=true;cursor=Math.max(0,entries.size()-4);selected=entries.size()-1;rewindRequest=-1;}
    public void scroll(int amount){cursor=Math.max(0,Math.min(Math.max(0,entries.size()-4),cursor+amount));}
    public void clear(){entries.clear();cursor=0;selected=rewindRequest=-1;active=false;}
    public void save(DataOutput out)throws IOException{
        out.writeBoolean(!omitFromSnapshot&&active);out.writeInt(omitFromSnapshot?0:cursor);out.writeInt(omitFromSnapshot?-1:selected);
        out.writeInt(omitFromSnapshot?0:entries.size());if(omitFromSnapshot)return;
        for(Entry e:entries){out.writeUTF(e.speaker);out.writeUTF(e.body);out.writeUTF(e.voice);out.writeUTF(e.script);out.writeInt(e.pc);out.writeInt(e.resume.length);out.write(e.resume);}
    }
    public void load(DataInput in)throws IOException{
        clear();active=in.readBoolean();cursor=in.readInt();selected=in.readInt();int count=in.readInt(),total=0;
        if(count<0||count>limit)throw new IOException("DM history size");
        for(int n=0;n<count;n++){Entry e=new Entry(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readInt());int size=in.readInt();total+=size;
            if(size<0||size>1024*1024||total>16*1024*1024)throw new IOException("DM history snapshot size");e.resume=new byte[size];in.readFully(e.resume);entries.add(e);}
        if(cursor<0||cursor>Math.max(0,count-1)||selected < -1||selected>=count)throw new IOException("DM history cursor");
    }
    public void saveVisuals(DataOutput out)throws IOException{out.writeBoolean(previewEnabled);out.writeInt(omitFromSnapshot?0:entries.size());if(!omitFromSnapshot)for(Entry e:entries){out.writeInt(e.scene.length);out.write(e.scene);}}
    public void loadVisuals(DataInput in)throws IOException{previewEnabled=in.readBoolean();int count=in.readInt(),total=0;if(count!=entries.size())throw new IOException("History visual count");for(Entry e:entries){int size=in.readInt();total+=size;if(size<0||size>128*1024||total>8*1024*1024)throw new IOException("History visual size");e.scene=new byte[size];in.readFully(e.scene);}}
}
