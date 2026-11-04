package org.circus.core;
import java.io.*;
/** Native DD backlog geometry from 4060E0, 401110 and 405680. */
public final class Dc3Backlog {
    private Dc3Backlog(){}
    private static void quad(Dc3Ui ui,int x,int y,int sx,int sy,int w,int h){Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource="SYSTEM/LOGCHIP";q.x=x;q.y=y;q.sx=sx;q.sy=sy;q.width=w;q.height=h;ui.current.quads.add(q);}
    private static void button(Dc3Ui ui,String action,int x,int y,int r,int b,int sx,int sy,int w,int h)throws IOException{
        ui.button(new int[]{x,y,r,b},action);Dc3Ui.Button button=ui.last();button.hoverSound=223;button.clickSound=224;
        if(w>0){Dc3Ui.Sprite sprite=new Dc3Ui.Sprite();sprite.state=99;sprite.quad.resource="SYSTEM/LOGCHIP";sprite.quad.x=x;sprite.quad.y=y;sprite.quad.sx=sx;sprite.quad.sy=sy;sprite.quad.width=w;sprite.quad.height=h;button.sprites.add(sprite);}
    }
    private static void state(Dc3Ui ui,int state,int sx,int sy,int w,int h,int dx)throws IOException{Dc3Ui.Button b=ui.last();Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.state=state;s.quad.resource="SYSTEM/LOGCHIP";s.quad.x=b.left+dx;s.quad.y=b.top;s.quad.sx=sx;s.quad.sy=sy;s.quad.width=w;s.quad.height=h;b.sprites.add(s);}
    public static void build(Dc3Ui ui,Dc2History history)throws IOException{
        ui.clear();ui.waiting=true;
        Dc3Scene.Quad bg=new Dc3Scene.Quad();bg.resource="SYSTEM/LOGBACK";bg.width=1024;bg.height=576;ui.current.quads.add(bg);
        if(history.entries.size()>4){
            quad(ui,943,76,820,0,35,336);
            button(ui,"@log:first",940,12,978,54,696,228,40,37);
            state(ui,1,696,266,40,37,0);state(ui,2,696,266,40,37,0);
            button(ui,"@log:up",940,54,978,78,680,0,40,23);
            state(ui,1,680,24,40,23,0);state(ui,2,680,48,40,23,0);
            button(ui,"@log:down",940,416,978,440,720,0,40,23);
            state(ui,1,720,24,40,23,0);state(ui,2,720,48,40,23,0);
            button(ui,"@log:last",940,450,978,492,736,228,40,37);
            state(ui,1,736,266,40,37,0);state(ui,2,736,266,40,37,0);
            quad(ui,944,80+332*history.cursor/Math.max(1,history.entries.size()-4),216,0,32,31);
        }
        button(ui,"@log:exit",908,516,1017,572,696,364,109,55);
        state(ui,1,696,420,109,55,0);state(ui,2,696,476,109,55,1);
        for(int row=0;row<4&&row+history.cursor<history.entries.size();row++){
            int at=row+history.cursor,y=44+130*row;Dc2History.Entry entry=history.entries.get(at);
            quad(ui,206,y,0,0,679,131);
            button(ui,"@log:select:"+at,206,y,885,y+131,0,0,0,0);
            if(!entry.voice.isEmpty()){button(ui,"@log:voice:"+at,98,138+130*row,198,170+130*row,696,132,100,31);state(ui,1,696,164,100,31,0);state(ui,2,696,196,100,31,0);}
        }
        button(ui,"@log:preview",14,80,206,110,696,775,186,30);
        state(ui,1,696,806,186,30,0);state(ui,2,696,806,186,30,0);
        if(history.previewEnabled)for(int s:new int[]{99,1,2}){state(ui,s,884,775,25,24,2);Dc3Ui.Button b=ui.last();b.sprites.get(b.sprites.size()-1).quad.y+=2;}
    }
    public static void build(Dc3Ui ui,Dc2History history,String profile)throws IOException{
        build(ui,history);boolean pp=Dc3Host.ppFamily(profile),wy=profile.equals("dc3wy-a09f");if(!pp&&!wy)return;
        // Earlier atlases use different crop rectangles (and P.P. underscored names).
        // Never feed the later D.D. portrait/button rectangles into this texture.
        history.previewEnabled=false;
        ui.current.buttons.removeIf(b->b.action.equals("@log:preview"));
        for(Dc3Scene.Quad q:ui.current.quads){if(q.resource.equals("SYSTEM/LOGBACK")){if(pp)q.resource="SYSTEM/LOG_BACK";}
            else{q.resource=pp?"SYSTEM/LOG_CHIP":"SYSTEM/LOGCHIP";if(q.sx==0&&q.sy==0){q.sx=pp?228:0;q.width=677;q.height=123;}else if(q.sx==943||q.sx==820){q.sx=pp?989:820;q.sy=pp?0:4;q.width=pp?30:35;q.height=332;}else{q.sx=pp?908:680;q.sy=75;q.width=q.height=31;}}
        }
        for(Dc3Ui.Button b:ui.current.buttons)for(Dc3Ui.Sprite s:b.sprites){Dc3Scene.Quad q=s.quad;q.resource=pp?"SYSTEM/LOG_CHIP":"SYSTEM/LOGCHIP";
            if(b.action.equals("@log:exit")){q.sx=pp?0:s.state==99?0:110;q.sy=pp?(s.state==99?0:67):600;q.width=pp?82:92;q.height=pp?66:50;}
            else if(b.action.startsWith("@log:voice:")){q.sx=pp?83:696;q.sy=pp?120:s.state==99?132:164;q.width=pp?25:100;q.height=pp?25:31;}
            else if(b.action.equals("@log:first")||b.action.equals("@log:up")){q.sx=pp?907:680;q.sy=s.state==99?0:24;q.width=40;q.height=23;}
            else if(b.action.equals("@log:last")||b.action.equals("@log:down")){q.sx=pp?948:720;q.sy=s.state==99?0:24;q.width=40;q.height=23;}
        }
    }
}
