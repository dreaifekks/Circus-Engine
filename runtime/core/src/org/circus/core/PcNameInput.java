package org.circus.core;

import java.io.*;
import java.util.*;

/** DCPC native name editor: 40C2E0, 40C6D0, 40D340 and 40D400.
 * Character tables are read as data from the user's PE, never executed. */
public final class PcNameInput {
    public boolean active,confirming;
    public int category,page,group,field=1,cursor;
    public String family="",given="",originalFamily="",originalGiven="";
    private final List<List<String>> tables=new ArrayList<>();
    private final int[][] rectangles=new int[14][7],atlas=new int[63][4];
    private final int[] groups=new int[51];
    private boolean prepared;
    private String defaultFamily,defaultGiven,confirmationText;

    public void prepare(CircusResources resources,TextCodec codec)throws IOException {
        if(prepared)return;Dc2NativeData.Pe pe=null;
        for(String path:resources.files.executableCandidates()){
            try{Dc2NativeData.Pe candidate=new Dc2NativeData.Pe(resources.files.read(path,4*1024*1024));if(tableShift(candidate)==0&&(candidate.bytes[candidate.at(0x40c2e0)]&255)!=0x8b)throw new IOException("DCPC name table signature");pe=candidate;break;}
            catch(IOException invalid){if(resources.files.explicitExecutable())throw new IOException("所选 EXE 不含兼容的 D.C.P.C 姓名表："+path,invalid);}
        }
        if(pe==null)throw new IOException("姓名输入需要兼容的 EXE 数据表，请在游戏设置中选择原版或兼容的补丁 EXE");
        final int shift=tableShift(pe);if(shift==0&&(pe.bytes[pe.at(0x40c2e0)]&255)!=0x8b)throw new IOException("DCPC name table signature mismatch");
        for(int n=0;n<14;n++)for(int k=0;k<7;k++)rectangles[n][k]=word(pe,shift+0x4306a0+n*28+k*4);
        for(int n=0;n<63;n++)for(int k=0;k<4;k++)atlas[n][k]=word(pe,shift+0x430828+n*16+k*4);
        for(int n=0;n<51;n++)groups[n]=word(pe,shift+0x43c57c+n*4);
        // The localized PE converts the four literal grids to GBK but leaves the
        // numeric CP932 character ranges unchanged. Identify the kana by bytes.
        byte[] firstRow=pe.stringBytes(shift+0x430cb8);TextCodec tableCodec=null;
        for(String encoding:new String[]{"windows-31j","GBK"}){TextCodec candidate=new TextCodec("","",encoding);try{if(candidate.decode(firstRow).startsWith("あいうえお"))tableCodec=candidate;}catch(IOException invalid){}}
        if(tableCodec==null)throw new IOException("Unrecognized DCPC name table encoding");
        tables.add(rows(pe,tableCodec,shift+0x430cb8,60,7));tables.add(rows(pe,tableCodec,shift+0x430e98,60,7));tables.add(rows(pe,tableCodec,shift+0x431078,60,14));
        tables.add(rows(pe,tableCodec,shift+0x4313fc,40,4));
        for(int n=0;n<44;n++){
            int first=word(pe,shift+0x43c64c+n*8),last=word(pe,shift+0x43c650+n*8);if(first<0||last<first||last-first>65536)throw new IOException("DCPC character range");
            List<String> rows=new ArrayList<>();StringBuilder row=new StringBuilder();
            for(int cp=first;cp<=last;cp++){
                boolean excluded=false;for(int a=shift+0x43c7ac;a<shift+0x43c8a4;a+=8)if(cp>=word(pe,a)&&cp<=word(pe,a+4)){excluded=true;break;}
                if(excluded)continue;
                row.append(TextCodec.cp932(new byte[]{(byte)(cp>>>8),(byte)cp}));
                if(n==15&&cp==0x91f2)row.append(pe.string(shift+0x43c8a4));
                if(row.length()>=17){rows.add(row.toString());row.setLength(0);}
            }
            if(row.length()>0)rows.add(row.toString());tables.add(rows);
        }
        boolean chinese=codec.charset.name().equalsIgnoreCase("GBK");defaultFamily=chinese?"朝仓":codec.apply(tableCodec.decode(pe.stringBytes(shift+0x43c8b4)));defaultGiven=chinese?"纯一":codec.apply(tableCodec.decode(pe.stringBytes(shift+0x43c8ac)));confirmationText=chinese?"使用这个姓名吗？":codec.apply(tableCodec.decode(pe.stringBytes(shift+0x43c8bc)));prepared=true;
    }
    /** The Vista build carries the same tables 0x3080 bytes later; code bytes are no help, so look for the first row. */
    private static int tableShift(Dc2NativeData.Pe pe){
        try{byte[] row=pe.stringBytes(0x433d38),kana="あいうえお".getBytes(Binary.SJIS);if(row.length<kana.length)return 0;for(int n=0;n<kana.length;n++)if(row[n]!=kana[n])return 0;return 0x3080;}
        catch(IOException e){return 0;}
    }
    private static int word(Dc2NativeData.Pe pe,int address)throws IOException{return (int)Binary.u32(pe.bytes,pe.at(address));}
    private static List<String> rows(Dc2NativeData.Pe pe,TextCodec codec,int address,int stride,int limit)throws IOException {
        List<String> result=new ArrayList<>();for(int n=0;n<limit;n++){int at=address+n*stride,p=pe.at(at);if((pe.bytes[p]&255)==255)break;try{result.add(codec.decode(pe.stringBytes(at)));}catch(IOException e){throw new IOException("姓名字符表无法解码（0x"+Integer.toHexString(at)+"）。请选择兼容的原版 EXE；脚本文字编码可独立配置。",e);}}return result;
    }
    public void open(String f,String g){family=originalFamily=f;given=originalGiven=g;active=true;confirming=false;category=page=group=cursor=0;field=1;}
    private List<String> rows(){return tables.get(category==3?3+group:category);}
    public int pages(){return Math.max(1,(rows().size()+6)/7);}
    public List<String> visibleRows(){List<String> r=rows();int start=Math.min(r.size(),page*7);return r.subList(start,Math.min(r.size(),start+7));}
    public int[] rectangle(int index){return rectangles[index].clone();}
    public int[] atlas(int index){return atlas[index].clone();}
    public void build(Dc2Ui ui)throws IOException{
        ui.buttons.clear();ui.waiting=active;ui.hovered=ui.pressed=-1;
        if(!active)return;
        if(confirming){List<Dc2Ui.Button> template=Dc2Ui.copyButtons(ui.buttonTemplates.get(2));if(template.size()<3)throw new IOException("Name confirmation template missing");Dc2Ui.Button panel=template.get(0);int dx=300-panel.left,dy=300-panel.top;for(Dc2Ui.Button b:template){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(Dc2Ui.Quad q:b.sprites){q.x+=dx;q.y+=dy;if(b==panel&&q.type==2){q.text=confirmationText;q.width=confirmationText.length()*24;}}if(b.action.equals(":y"))b.action="@name:yes";else if(b.action.equals(":n"))b.action="@name:no";}ui.buttons.addAll(template);return;}
        for(int n=0;n<14;n++){
            if(n>=10&&pages()==1)continue;int[] r=rectangles[n];Dc2Ui.Button b=new Dc2Ui.Button();b.left=r[0];b.top=r[1];b.right=r[2];b.bottom=r[3];b.action="@name:button:"+n;
            for(int state=0;state<3;state++){int[] a=atlas[r[4+state]];Dc2Ui.Quad q=new Dc2Ui.Quad();q.x=r[0];q.y=r[1];q.sx=a[0];q.sy=a[1];q.width=r[2]-r[0];q.height=r[3]-r[1];q.flags=10;q.state=state;b.sprites.add(q);}ui.buttons.add(b);
        }
    }
    /** Returns 1 to accept, -1 to cancel, 0 while editing. */
    public int action(String action)throws IOException {
        if(action.equals("@name:yes")){confirming=false;active=false;return 1;}if(action.equals("@name:no")){confirming=false;return 0;}
        if(action.equals("@name:cancel")){if(confirming){confirming=false;return 0;}active=false;family=originalFamily;given=originalGiven;return -1;}
        if(action.equals("@name:accept")){if(!family.trim().isEmpty()&&!given.trim().isEmpty()){confirming=true;return 0;}return 0;}
        if(action.startsWith("@name:field:")){int n=Integer.parseInt(action.substring(12));field=n/4;cursor=n%4;return 0;}
        if(action.startsWith("@name:char:")){
            int n=Integer.parseInt(action.substring(11)),row=n/17,col=n%17;List<String> r=visibleRows();if(row>=r.size()||col>=r.get(row).length())return 0;char c=r.get(row).charAt(col);if(c=='\u3000'||c==' ')return 0;
            if(category==3&&group==0){group=groups[row*17+col];page=0;return 0;}
            insert(String.valueOf(c));return 0;
        }
        if(!action.startsWith("@name:button:"))throw new IOException("Unknown name editor action");
        int n=Integer.parseInt(action.substring(13));
        if(n<4){category=n;page=group=0;}
        else if(n==4){StringBuilder b=new StringBuilder(field==0?family:given);while(b.length()<=cursor)b.append(' ');if(b.charAt(cursor)==' '&&cursor>0)cursor--;else b.setCharAt(cursor,' ');setField(b.toString());}
        else if(n==5){if(cursor<3)cursor++;else if(field==0){field=1;cursor=0;}}
        else if(n==6)cursor=Math.max(0,cursor-1);
        else if(n==7)return action("@name:accept");
        else if(n==8){family=defaultFamily;given=defaultGiven;}
        else if(n==9)return action("@name:cancel");
        else if(n==10)page=Math.max(0,page-5);
        else if(n==11)page=Math.max(0,page-1);
        else if(n==12)page=Math.min(pages()-1,page+1);
        else if(n==13)page=Math.min(pages()-1,page+5);
        return 0;
    }
    private void setField(String value){if(field==0)family=value;else given=value;}
    public void insert(String text){for(int cp:text.codePoints().toArray()){if(Character.isISOControl(cp)||Character.charCount(cp)>1)continue;StringBuilder b=new StringBuilder(field==0?family:given);while(b.length()<=cursor)b.append(' ');b.setCharAt(cursor,(char)cp);setField(b.toString());if(cursor<3)cursor++;else if(field==0){field=1;cursor=0;}}}
    public void save(DataOutput out)throws IOException{out.writeBoolean(active);out.writeBoolean(confirming);for(int n:new int[]{category,page,group,field,cursor})out.writeInt(n);for(String s:new String[]{family,given,originalFamily,originalGiven})out.writeUTF(s);}
    public void load(DataInput in)throws IOException{active=in.readBoolean();confirming=in.readBoolean();category=in.readInt();page=in.readInt();group=in.readInt();field=in.readInt();cursor=in.readInt();family=in.readUTF();given=in.readUTF();originalFamily=in.readUTF();originalGiven=in.readUTF();if(category<0||category>3||group<0||group>44||page<0||page>16||field<0||field>1||cursor<0||cursor>3||family.length()>4||given.length()>4)throw new IOException("Invalid saved name editor");}
}
