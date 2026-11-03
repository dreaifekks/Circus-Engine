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
        public final String text;public final int x,y,size,tick;
        Glyph(String text,int x,int y,int size,int tick){this.text=text;this.x=x;this.y=y;this.size=size;this.tick=tick;}
    }
    public static final class Layout {
        public final List<Glyph> glyphs=new ArrayList<>();public int ticks;
    }
    private PcText(){}

    public static Layout layout(String raw,int x0,int y0,int lineHeight){
        Layout out=new Layout();int x=x0,y=y0,tick=0;
        String[] ruby=null;int rubyX=0,gap=0,placed=0;
        int[] cp=raw.codePoints().toArray();
        for(int i=0;i<cp.length;i++){
            int c=cp[i];
            if(c=='\n'){y+=lineHeight;x=x0;tick++;continue;}
            if(c=='｛'&&ruby==null){
                int slash=find(cp,i+1,'／'),close=slash<0?-1:find(cp,slash+1,'｝');
                if(close>=0){
                    ruby=new String[slash-i-1];for(int k=0;k<ruby.length;k++)ruby[k]=new String(Character.toChars(cp[i+1+k]));
                    int room=BODY_SIZE*(close-slash-1)-RUBY_SIZE*ruby.length;
                    rubyX=room<0?x-(-room)/2:x;gap=Math.max(0,room);placed=0;
                    i=slash;tick++;continue;
                }
            }
            if(c=='｝'&&ruby!=null){
                for(;placed<ruby.length;placed++)out.glyphs.add(new Glyph(ruby[placed],rubyAt(rubyX,gap,ruby.length,placed),y-RUBY_RISE,RUBY_SIZE,tick));
                ruby=null;tick++;continue;
            }
            out.glyphs.add(new Glyph(new String(Character.toChars(c)),x,y,BODY_SIZE,tick));x+=BODY_SIZE;
            if(ruby!=null)for(;placed<ruby.length&&rubyAt(rubyX,gap,ruby.length,placed)<x;placed++)out.glyphs.add(new Glyph(ruby[placed],rubyAt(rubyX,gap,ruby.length,placed),y-RUBY_RISE,RUBY_SIZE,tick));
            tick++;
        }
        out.ticks=tick;
        return out;
    }
    private static int rubyAt(int start,int gap,int count,int k){return gap>0?start+(2*k+1)*gap/count/2+RUBY_SIZE*k:start+RUBY_SIZE*k;}
    private static int find(int[] cp,int from,int wanted){for(int i=from;i<cp.length;i++)if(cp[i]==wanted)return i;return -1;}

    /** The text with ruby reduced to its base, for captions. */
    public static String plain(String raw){return raw.replaceAll("｛[^／｝]*／([^｝]*)｝","$1");}
}
