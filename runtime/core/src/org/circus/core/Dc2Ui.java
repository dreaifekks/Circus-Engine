package org.circus.core;

import java.io.*;
import java.util.*;

/** DM 42EBA1 / 42D314: independent draw-list and button-template stores.
 * Resources are slot references, resolved at presentation, including forward references. */
public final class Dc2Ui {
    public static final class Quad {
        public int x,y,sx,sy,width,height,flags,state,type,binding,animation,frames,step,period;
        public String text="";
        public int slot(){return flags&63;}
        public boolean opaque(){return (flags&128)!=0;}
        void save(DataOutput o)throws IOException{for(int v:new int[]{x,y,sx,sy,width,height,flags,state,type,binding,animation,frames,step,period})o.writeInt(v);o.writeUTF(text);}
        static Quad load(DataInput i)throws IOException{Quad q=new Quad();q.x=i.readInt();q.y=i.readInt();q.sx=i.readInt();q.sy=i.readInt();q.width=i.readInt();q.height=i.readInt();q.flags=i.readInt();q.state=i.readInt();q.type=i.readInt();q.binding=i.readInt();q.animation=i.readInt();q.frames=i.readInt();q.step=i.readInt();q.period=i.readInt();q.text=i.readUTF();return q;}
    }
    public static final class Button {
        public int left,top,right,bottom,hoverSound=255,clickSound=255,flags,binding;
        public int[] navigation=new int[4];
        public String action="",voice="",hoverVoice="";
        public final List<Quad> sprites=new ArrayList<>();
        void save(DataOutput o)throws IOException{for(int v:new int[]{left,top,right,bottom,hoverSound,clickSound,flags,binding})o.writeInt(v);for(int v:navigation)o.writeInt(v);o.writeUTF(action);o.writeUTF(hoverVoice.isEmpty()?voice:voice+'\u0001'+hoverVoice);saveQuads(o,sprites);}
        static Button load(DataInput i)throws IOException{Button b=new Button();b.left=i.readInt();b.top=i.readInt();b.right=i.readInt();b.bottom=i.readInt();b.hoverSound=i.readInt();b.clickSound=i.readInt();b.flags=i.readInt();b.binding=i.readInt();for(int n=0;n<4;n++)b.navigation[n]=i.readInt();b.action=i.readUTF();b.voice=i.readUTF();int mark=b.voice.indexOf('\u0001');if(mark>=0){b.hoverVoice=b.voice.substring(mark+1);b.voice=b.voice.substring(0,mark);}b.sprites.addAll(loadQuads(i,14));return b;}
    }
    public final List<Quad> quads=new ArrayList<>();
    public final List<Button> buttons=new ArrayList<>();
    public final List<List<Quad>> quadTemplates=new ArrayList<>();
    public final List<List<Button>> buttonTemplates=new ArrayList<>();
    public final String[] actions=new String[15];
    public final int[] palette=new int[64];
    /** Pending draw-list capacity. DM/PC cap at 30; D.C.II 416151 accepts 60 (cmp eax,0x3c). */
    public int quadLimit=30;
    public int x,y,sourceX,sourceY,layout,binding,hovered=-1,pressed=-1;
    public boolean waiting;public String version="";
    public Dc2Ui(){for(int n=0;n<14;n++)quadTemplates.add(new ArrayList<Quad>());for(int n=0;n<9;n++)buttonTemplates.add(new ArrayList<Button>());Arrays.fill(actions,"");Arrays.fill(palette,0xffffffff);}
    public void clearCurrent(){quads.clear();buttons.clear();hovered=pressed=-1;waiting=false;}
    public Button last()throws IOException{if(buttons.isEmpty())throw new IOException("DM button property without button");return buttons.get(buttons.size()-1);}
    public void storeQuads(int selector,int flag)throws IOException{
        // Caps come from native destinations, not the DD panel catalogue.
        int[] caps={0,12,5,20,12,5,20,20,6,6,8,4,6,1};
        if(selector==100){if(flag==0)layout=0;else if(flag==1)layout=2;else if(flag==2)layout=9;return;}
        if(selector>0&&selector<14){List<Quad> target=quadTemplates.get(selector);target.clear();target.addAll(copyQuads(quads.subList(0,Math.min(caps[selector],quads.size()))));}
        else if(selector!=0&&selector!=99)throw new IOException("DM draw template "+selector);
        quads.clear();
    }
    public void storeButtons(int selector)throws IOException{
        int[] caps={0,9,4,8,9,4,9,8,4};
        if(selector<0||selector>=caps.length)throw new IOException("DM button template "+selector);
        if(selector!=0){List<Button> target=buttonTemplates.get(selector);target.clear();target.addAll(copyButtons(buttons.subList(0,Math.min(caps[selector],buttons.size()))));
            if(selector==1||selector==4||selector==6||selector==7)for(Button b:target)b.flags|=1;
            if(selector==3&&!target.isEmpty())target.get(0).flags=3;
        }
        buttons.clear();hovered=pressed=-1;
    }
    public void button(int[] a,byte[] action)throws IOException{
        if(buttons.size()>=50)throw new IOException("DM button limit");Button b=new Button();
        b.left=a[0]+x;b.top=a[1]+y;b.right=a[2]+x;b.bottom=a[3]+y;b.action=TextCodec.cp932(action);b.binding=binding;binding=0;buttons.add(b);
    }
    public Quad quad(int[] a,boolean sprite)throws IOException{
        if(a.length!=8)throw new IOException("DM quad requires extension");Quad q=new Quad();q.x=a[0]+x;q.y=a[1]+y;q.sx=a[2];q.sy=a[3];q.width=a[4];q.height=a[5];q.flags=a[6];q.state=a[7];
        if(!sprite){if(q.sx<0x4000){q.sx+=sourceX;q.sy+=sourceY;}q.type=(q.flags&0x40)!=0?1:0;if((q.flags&0x100)!=0)q.type=7;if((q.flags&0x200)!=0)q.type=8;}
        if(sprite){Button b=last();if(b.sprites.size()>=14)throw new IOException("DM sprite limit");if(q.state==5)b.flags|=4;b.sprites.add(q);}
        else{if(quads.size()>=quadLimit)throw new IOException("DM quad limit");q.binding=binding;binding=0;quads.add(q);}return q;
    }
    public void text(int[] a,byte[] raw,boolean sprite)throws IOException{
        Quad q=new Quad();q.x=a[0];q.y=a[1];q.text=TextCodec.cp932(raw);q.type=2;q.width=a[2];q.height=a[3];q.binding=binding;binding=0;
        if(sprite){q.x+=x;q.y+=y;q.flags=a[2];q.state=a[3];q.width=raw.length*11+1;q.height=23;if(last().sprites.size()>=14)throw new IOException("DM text sprite limit");last().sprites.add(q);}
        else{if(quads.size()>=quadLimit)throw new IOException("DM text quad limit");quads.add(q);}
    }
    public int hit(int px,int py){for(int n=buttons.size()-1;n>=0;n--){Button b=buttons.get(n);if(!b.action.isEmpty()&&px>=b.left&&px<b.right&&py>=b.top&&py<b.bottom)return n;}return -1;}
    static List<Quad> copyQuads(List<Quad> src)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();saveQuads(new DataOutputStream(b),src);return loadQuads(new DataInputStream(new ByteArrayInputStream(b.toByteArray())),2048);}
    static List<Button> copyButtons(List<Button> src)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();saveButtons(new DataOutputStream(b),src);return loadButtons(new DataInputStream(new ByteArrayInputStream(b.toByteArray())));}
    private static int count(DataInput i,int max)throws IOException{int n=i.readInt();if(n<0||n>max)throw new IOException("DM UI count");return n;}
    static void saveQuads(DataOutput o,List<Quad> list)throws IOException{o.writeInt(list.size());for(Quad q:list)q.save(o);}
    static List<Quad> loadQuads(DataInput i,int max)throws IOException{List<Quad> list=new ArrayList<>();for(int n=count(i,max);n>0;n--)list.add(Quad.load(i));return list;}
    static void saveButtons(DataOutput o,List<Button> list)throws IOException{o.writeInt(list.size());for(Button b:list)b.save(o);}
    static List<Button> loadButtons(DataInput i)throws IOException{List<Button> list=new ArrayList<>();for(int n=count(i,50);n>0;n--)list.add(Button.load(i));return list;}
    public void save(DataOutput o)throws IOException{o.writeInt(0x444d5531);for(int v:new int[]{x,y,sourceX,sourceY,layout,binding,hovered,pressed})o.writeInt(v);o.writeBoolean(waiting);o.writeUTF(version);for(String a:actions)o.writeUTF(a);for(int c:palette)o.writeInt(c);saveQuads(o,quads);saveButtons(o,buttons);for(List<Quad> t:quadTemplates)saveQuads(o,t);for(List<Button> t:buttonTemplates)saveButtons(o,t);}
    public void load(DataInput i)throws IOException{if(i.readInt()!=0x444d5531)throw new IOException("DM UI format");x=i.readInt();y=i.readInt();sourceX=i.readInt();sourceY=i.readInt();layout=i.readInt();binding=i.readInt();hovered=i.readInt();pressed=i.readInt();waiting=i.readBoolean();version=i.readUTF();for(int n=0;n<actions.length;n++)actions[n]=i.readUTF();for(int n=0;n<64;n++)palette[n]=i.readInt();quads.clear();quads.addAll(loadQuads(i,2048));buttons.clear();buttons.addAll(loadButtons(i));for(List<Quad> t:quadTemplates){t.clear();t.addAll(loadQuads(i,30));}for(List<Button> t:buttonTemplates){t.clear();t.addAll(loadButtons(i));}}
}
