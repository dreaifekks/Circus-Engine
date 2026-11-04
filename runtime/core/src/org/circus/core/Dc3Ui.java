package org.circus.core;

import java.io.*;
import java.util.*;

/** Original MES UI objects. No game labels, routes or screen layouts live here. */
public final class Dc3Ui {
    // Native DD GRB palette at 4AA108; START customizes entries after these defaults.
    private static final int[] DEFAULT_PALETTE={0xff000000,0xff0000ff,0xffff0000,0xffff00ff,0xff00ff00,0xff00ffff,0xffffff00,0xffffffff,0xff854e28,0xffccccff,0xff774400,0xffff7f7f,0xffcccccc};
    private void defaultPalette(boolean restoring){
        if(restoring){for(int n=0;n<DEFAULT_PALETTE.length;n++)if(palette[n]!=0)return;}
        System.arraycopy(DEFAULT_PALETTE,0,palette,0,DEFAULT_PALETTE.length);
    }
    public static final class Sprite {
        public Dc3Scene.Quad quad=new Dc3Scene.Quad();public int state;
        void save(DataOutput o)throws IOException{quad.save(o);o.writeInt(state);}
        void load(DataInput i)throws IOException{quad.load(i);state=i.readInt();}
    }
    public static final class Button {
        public int left,top,right,bottom,hoverSound=255,clickSound=255;
        public String action="";private byte[] actionBytes=new byte[0];public final List<Sprite> sprites=new ArrayList<>();
        public byte[] actionBytes(){return actionBytes.clone();}
        public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
        void save(DataOutput o)throws IOException{o.writeInt(left);o.writeInt(top);o.writeInt(right);o.writeInt(bottom);o.writeInt(hoverSound);o.writeInt(clickSound);o.writeInt(actionBytes.length);o.write(actionBytes);o.writeInt(sprites.size());for(Sprite s:sprites)s.save(o);}
        void load(DataInput i)throws IOException{left=i.readInt();top=i.readInt();right=i.readInt();bottom=i.readInt();hoverSound=i.readInt();clickSound=i.readInt();actionBytes=new byte[count(i,255)];i.readFully(actionBytes);action=TextCodec.cp932(actionBytes);int n=count(i,1024);for(int j=0;j<n;j++){Sprite s=new Sprite();s.load(i);sprites.add(s);}}
    }
    public static final class Text {
        public int[] args;public String text;public boolean button;
        void save(DataOutput o)throws IOException{for(int n:args)o.writeInt(n);o.writeUTF(text);o.writeBoolean(button);}
        void load(DataInput i)throws IOException{args=new int[4];for(int n=0;n<4;n++)args[n]=i.readInt();text=i.readUTF();button=i.readBoolean();}
    }
    public static final class Panel {
        public int x,y,flags;public boolean focus,hidden;
        public final List<Button> buttons=new ArrayList<>();public final List<Dc3Scene.Quad> quads=new ArrayList<>();public final List<Text> texts=new ArrayList<>();
        public int[] textStyle={0,0,0,0};
        void save(DataOutput o)throws IOException{o.writeInt(x);o.writeInt(y);o.writeInt(flags);o.writeBoolean(focus);o.writeBoolean(hidden);for(int a:textStyle)o.writeInt(a);o.writeInt(buttons.size());for(Button b:buttons)b.save(o);o.writeInt(quads.size());for(Dc3Scene.Quad q:quads)q.save(o);o.writeInt(texts.size());for(Text t:texts)t.save(o);}
        void load(DataInput i)throws IOException{x=i.readInt();y=i.readInt();flags=i.readInt();focus=i.readBoolean();hidden=i.readBoolean();for(int n=0;n<4;n++)textStyle[n]=i.readInt();int n=count(i,1024);for(int j=0;j<n;j++){Button b=new Button();b.load(i);buttons.add(b);}n=count(i,1024);for(int j=0;j<n;j++){Dc3Scene.Quad q=new Dc3Scene.Quad();q.load(i);quads.add(q);}n=count(i,1024);for(int j=0;j<n;j++){Text t=new Text();t.load(i);texts.add(t);}}
    }
    public Panel current=new Panel();public final Panel[] templates=new Panel[22];
    public final List<Button> grid=new ArrayList<>();public final int[] gridBounds=new int[8];public boolean gridWaiting,saveGrid;public int gridHoverSound=255,gridClickSound=255;
    public Panel underlay;public int confirmX,confirmY,confirmKind,activeConfirmation=-1;public boolean confirmChecked;
    public final int[] palette=new int[64];public final String[] actions=new String[17];
    public final List<String> voiceNames=new ArrayList<>();
    public boolean waiting;public String version="";public int hovered=-1,pressed=-1,pointerX,pointerY,messageX,messageY,inputMode;
    public Dc3Ui(){Arrays.fill(actions,"");defaultPalette(false);}
    public void clear(){shown=false;grid.clear();gridWaiting=false;current=new Panel();underlay=null;activeConfirmation=-1;waiting=false;hovered=pressed=-1;Arrays.fill(actions,"");}
    public Button last()throws IOException{if(current.buttons.isEmpty())throw new IOException("UI property without a button");return current.buttons.get(current.buttons.size()-1);}
    public void button(int[] a,String action)throws IOException{button(a,action.getBytes(Binary.SJIS));}
    public void button(int[] a,byte[] action)throws IOException{if(current.buttons.size()>=1024||action.length>255)throw new IOException("UI button limit");Button b=new Button();b.left=a[0];b.top=a[1];b.right=a[2];b.bottom=a[3];b.actionBytes=action.clone();b.action=TextCodec.cp932(action);current.buttons.add(b);}
    /** Native 432948 / 43298C selector tables, not a per-game layout table. */
    public void store(int selector,boolean alternate)throws IOException{
        int[] mapping=alternate?new int[]{-1,0,1,2,3,4,5,6,-1,-1,-1,-1,-1,17,18,7,8}:new int[]{-1,9,12,11,-1,-1,10,13,-1,-1,-1,-1,-1,17,18,19,20,21};
        if(alternate&&(selector==254||selector==255)){current.hidden=selector==254;return;}
        if(selector<0||selector>=mapping.length||mapping[selector]<0)return;
        templates[mapping[selector]]=current;current=new Panel();hovered=pressed=-1;
    }
    public int hit(float x,float y){if(!waiting||current.hidden)return -1;for(int n=current.buttons.size()-1;n>=0;n--)if(!current.buttons.get(n).action.isEmpty()&&current.buttons.get(n).contains(x,y))return n;return -1;}
    /** D.D. 4339D5, 423BCA: a click only jumps to a label; the panel stays drawn until 4203A0 takes it down. */
    public boolean keepsPanels,shown;
    public boolean panelUp(){return waiting||keepsPanels&&shown;}
    public void dropPanel(){if(keepsPanels&&shown&&!waiting){current=new Panel();underlay=null;shown=false;}}
    public List<Dc3Scene.Quad> visible(){List<Dc3Scene.Quad> out=new ArrayList<>();if(!panelUp())return out;if(underlay!=null)visible(underlay,-1,-1,false,false,out);visible(current,hovered,pressed,activeConfirmation>=0,confirmChecked,out);return out;}
    private static void visible(Panel panel,int hovered,int pressed,boolean modal,boolean checked,List<Dc3Scene.Quad> out){if(panel.hidden)return;out.addAll(panel.quads);for(int n=0;n<panel.buttons.size();n++){Button b=panel.buttons.get(n);int state=n==pressed?2:n==hovered?1:99;boolean checkbox=modal&&b.action.equals(":c");if(checkbox&&state==2)state=1;boolean found=false;for(Sprite s:b.sprites)if(s.state==state)found=true;for(Sprite s:b.sprites)if(s.state==(found?state:99)||checkbox&&checked&&s.state==2)out.add(s.quad);}}
    public Set<String> resources(){Set<String> out=new HashSet<>();if(panelUp())for(Panel p:new Panel[]{underlay,current})if(p!=null&&!p.hidden){for(Dc3Scene.Quad q:p.quads)out.add(q.resource);for(Button b:p.buttons)for(Sprite s:b.sprites)if(s.state!=5)out.add(s.quad.resource);}return out;}
    static Panel copy(Panel panel)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();panel.save(new DataOutputStream(bytes));Panel result=new Panel();result.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));return result;}
    /** Native 43059A / 417E20: template 12, first backdrop and indexed caption from anonymous button. */
    public void confirmation()throws IOException{
        if(underlay!=null||templates[12]==null)throw new IOException("Confirmation template unavailable");
        Panel p=copy(templates[12]);boolean backdrop=false;int caption=0;
        for(Iterator<Button> it=p.buttons.iterator();it.hasNext();){Button b=it.next();if(!b.action.isEmpty())continue;
            for(Sprite s:b.sprites){if(s.state==99&&!backdrop){p.quads.add(s.quad);backdrop=true;}if(s.state==2&&caption++==confirmKind)p.quads.add(s.quad);}it.remove();}
        if(!backdrop||confirmKind<0||confirmKind>=caption)throw new IOException("Confirmation caption unavailable: "+confirmKind);
        for(Dc3Scene.Quad q:p.quads){q.x+=confirmX;q.y+=confirmY;}
        for(Button b:p.buttons){b.left+=confirmX;b.right+=confirmX;b.top+=confirmY;b.bottom+=confirmY;for(Sprite s:b.sprites){s.quad.x+=confirmX;s.quad.y+=confirmY;}}
        underlay=current;current=p;activeConfirmation=confirmKind;confirmX=confirmY=0;hovered=pressed=-1;waiting=true;
    }
    public void closeConfirmation(){if(underlay!=null){current=underlay;underlay=null;activeConfirmation=-1;}hovered=pressed=-1;}
    public void save(DataOutput o)throws IOException{o.writeBoolean(gridWaiting);o.writeBoolean(saveGrid);o.writeInt(gridHoverSound);o.writeInt(gridClickSound);for(int v:gridBounds)o.writeInt(v);o.writeInt(grid.size());for(Button b:grid)b.save(o);o.writeBoolean(waiting);o.writeUTF(version);o.writeInt(hovered);o.writeInt(pressed);o.writeInt(pointerX);o.writeInt(pointerY);o.writeInt(messageX);o.writeInt(messageY);o.writeInt(inputMode);o.writeInt(confirmX);o.writeInt(confirmY);o.writeInt(confirmKind);o.writeInt(activeConfirmation);o.writeBoolean(confirmChecked);o.writeBoolean(underlay!=null);if(underlay!=null)underlay.save(o);current.save(o);for(Panel p:templates){o.writeBoolean(p!=null);if(p!=null)p.save(o);}for(int p:palette)o.writeInt(p);for(String a:actions)o.writeUTF(a);o.writeInt(voiceNames.size());for(String n:voiceNames)o.writeUTF(n);}
    public void load(DataInput i)throws IOException{gridWaiting=i.readBoolean();saveGrid=i.readBoolean();gridHoverSound=i.readInt();gridClickSound=i.readInt();for(int n=0;n<8;n++)gridBounds[n]=i.readInt();grid.clear();for(int n=count(i,50);n>0;n--){Button b=new Button();b.load(i);grid.add(b);}waiting=i.readBoolean();version=i.readUTF();hovered=i.readInt();pressed=i.readInt();pointerX=i.readInt();pointerY=i.readInt();messageX=i.readInt();messageY=i.readInt();inputMode=i.readInt();confirmX=i.readInt();confirmY=i.readInt();confirmKind=i.readInt();activeConfirmation=i.readInt();confirmChecked=i.readBoolean();underlay=null;if(i.readBoolean()){underlay=new Panel();underlay.load(i);}if((underlay!=null)!=(activeConfirmation>=0)||activeConfirmation>=20)throw new IOException("Invalid confirmation state");current=new Panel();current.load(i);for(int n=0;n<templates.length;n++){templates[n]=null;if(i.readBoolean()){templates[n]=new Panel();templates[n].load(i);}}for(int n=0;n<palette.length;n++)palette[n]=i.readInt();for(int n=0;n<actions.length;n++)actions[n]=i.readUTF();voiceNames.clear();int n=count(i,256);for(int j=0;j<n;j++)voiceNames.add(i.readUTF());if(hovered< -1||hovered>=current.buttons.size()||pressed< -1||pressed>=current.buttons.size())throw new IOException("Invalid UI focus");defaultPalette(true);}
    private static int count(DataInput i,int max)throws IOException{int n=i.readInt();if(n<0||n>max)throw new IOException("UI object limit");return n;}
}
