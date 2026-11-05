package ios.awt;
import java.util.*;
public final class Font {
    public static final int PLAIN=0,BOLD=1,ITALIC=2;
    public final String name;public final int style,size;
    final String face;final float[] metrics=new float[3];
    private final Map<Integer,Glyph> glyphs=new HashMap<>();
    private static final Map<String,Font> SHARED=new HashMap<>();
    public Font(String n,int st,int sz){name=n;style=st;size=sz;face=st==BOLD?"HiraginoSans-W6":"HiraginoSans-W3";Native.fontMetrics(face,sz,metrics);}
    static Font shared(String face,int size){String key=face+"/"+size;Font f=SHARED.get(key);if(f==null){f=new Font(face,PLAIN,size);SHARED.put(key,f);}return f;}
    public int getSize(){return size;}public int getStyle(){return style;}public String getFamily(){return name;}public String getName(){return name;}
    static final class Glyph {final byte[] mask;final int w,h,ox,baseline;final float advance;Glyph(byte[] m,int w,int h,int ox,int b,float a){mask=m;this.w=w;this.h=h;this.ox=ox;baseline=b;advance=a;}}
    Glyph glyph(int cp){Glyph g=glyphs.get(cp);if(g!=null)return g;
        // Clear of the pen on all sides, so that overhangs survive: the mask is recorded relative to the pen and the baseline.
        int w=size*2+8,h=size*2+8,ox=size/2+2,bottom=size/2+2;byte[] mask=new byte[w*h];float adv=Native.glyph(face,size,cp,mask,w,h,ox,bottom);
        if(glyphs.size()>6000)glyphs.clear();g=new Glyph(mask,w,h,ox,h-bottom,adv);glyphs.put(cp,g);return g;}
    float advance(int cp){return glyph(cp).advance;}
}
