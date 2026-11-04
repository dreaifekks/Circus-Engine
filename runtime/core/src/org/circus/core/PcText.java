package org.circus.core;

import java.util.ArrayList;
import java.util.List;

/**
 * D.C.II P.C. body text as DC2PC.exe lays it out (41C970, 41C230, 421F90): 28 px cells, breaks only where the script
 * has a line feed, and ruby written as ｛kana／base｝. Each entry below costs the typewriter one tick; ruby kana appear
 * as the base text reaches them and do not count on their own.
 */
public final class PcText {
    public static final int BODY_SIZE=28,RUBY_SIZE=12,RUBY_RISE=11;
    public static final class Glyph {
        public final String text;public final int x,y,size,tick,line;
        Glyph(String text,int x,int y,int size,int tick,int line){this.text=text;this.x=x;this.y=y;this.size=size;this.tick=tick;this.line=line;}
    }
    public static final class Layout {
        public final List<Glyph> glyphs=new ArrayList<>();public int ticks;
    }
    private PcText(){}

    /** What differs between games. DC2DM 425DF4, 4261AE, 4261CB, 426438 and 42650E give its column. */
    public static final class Rules {
        public final int body,advance,half,rubySize,rubyRise,dotDx,dotDy,firstRubyDrop,laterRubyDrop;public final boolean cellRuby;
        Rules(int body,int advance,int half,int rubyRise,int dotDx,int dotDy,int firstRubyDrop,int laterRubyDrop){this(body,advance,half,RUBY_SIZE,rubyRise,dotDx,dotDy,firstRubyDrop,laterRubyDrop);}
        Rules(int body,int advance,int half,int rubySize,int rubyRise,int dotDx,int dotDy,int firstRubyDrop,int laterRubyDrop){cellRuby=rubySize==14;this.rubySize=rubySize;this.body=body;this.advance=advance;this.half=half;this.rubyRise=rubyRise;this.dotDx=dotDx;this.dotDy=dotDy;this.firstRubyDrop=firstRubyDrop;this.laterRubyDrop=laterRubyDrop;}
    }
    public static final Rules PC=new Rules(BODY_SIZE,BODY_SIZE,BODY_SIZE,RUBY_RISE,-8,-16,0,0),DM=new Rules(22,21,10,12,-5,-16,6,10),DC3=new Rules(22,22,11,14,10,0,0,0,0);
    public static Layout layout(String raw,int x0,int y0,int lineHeight){return layout(raw,x0,y0,lineHeight,PC);}
    public static Layout layout(String raw,int x0,int y0,int lineHeight,Rules r){
        Layout out=new Layout();int x=x0,y=y0,tick=0,line=0;
        String[] ruby=null;int rubyX=0,gap=0,placed=0;
        int[] cp=raw.codePoints().toArray();
        if(r.firstRubyDrop!=0&&lineHasRuby(cp,0))y+=r.firstRubyDrop;
        for(int i=0;i<cp.length;i++){
            int c=cp[i];
            if(c=='\n'){y+=lineHeight;x=x0;tick++;line++;if(r.laterRubyDrop!=0&&lineHasRuby(cp,i+1))y+=r.laterRubyDrop;continue;}
            if(c=='｛'&&ruby==null){
                int slash=find(cp,i+1,'／'),close=slash<0?-1:find(cp,slash+1,'｝');
                if(close>=0){
                    ruby=new String[slash-i-1];for(int k=0;k<ruby.length;k++)ruby[k]=new String(Character.toChars(cp[i+1+k]));
                    int room=r.advance*(close-slash-1)-r.rubySize*ruby.length;
                    rubyX=room<0?x-(-room)/2:x;gap=Math.max(0,room);placed=0;
                    if(r.cellRuby){gap=-Math.max(r.rubySize,r.advance*(close-slash-1)/Math.max(1,ruby.length));rubyX=Math.max(x0,x-Math.max(0,-room)/2);} // D.D. 43CC50: kana centred in equal cells.
                    i=slash;tick++;continue;
                }
            }
            if(c=='｝'&&ruby!=null){
                for(;placed<ruby.length;placed++)out.glyphs.add(new Glyph(ruby[placed],rubyAt(rubyX,gap,ruby.length,placed,r.rubySize),y-r.rubyRise,r.rubySize,tick,line));
                ruby=null;tick++;continue;
            }
            out.glyphs.add(new Glyph(new String(Character.toChars(c)),x,y,r.body,tick,line));x+=c<0x80||c>=0xff61&&c<=0xff9f?r.half:r.advance;
            if(ruby!=null)for(;placed<ruby.length&&rubyAt(rubyX,gap,ruby.length,placed,r.rubySize)<x;placed++)out.glyphs.add(new Glyph(ruby[placed],rubyAt(rubyX,gap,ruby.length,placed,r.rubySize),y-r.rubyRise,r.rubySize,tick,line));
            tick++;
        }
        out.ticks=tick;
        return out;
    }
    private static boolean lineHasRuby(int[] cp,int from){for(int i=from;i<cp.length&&cp[i]!='\n';i++)if(cp[i]=='｛')return true;return false;}
    private static int rubyAt(int start,int gap,int count,int k,int size){if(gap<0)return start+k*-gap+(-gap-size)/2;return gap>0?start+(2*k+1)*gap/count/2+size*k:start+size*k;}
    private static int find(int[] cp,int from,int wanted){for(int i=from;i<cp.length;i++)if(cp[i]==wanted)return i;return -1;}

    /** The text with ruby reduced to its base, for captions. */
    public static String plain(String raw){return raw.replaceAll("｛[^／｝]*／([^｝]*)｝","$1");}
}
