package ios.awt;
public final class FontMetrics {
    private final Font font;FontMetrics(Font f){font=f;}
    public Font getFont(){return font;}
    public int getAscent(){return Math.round(font.metrics[0]);}public int getDescent(){return Math.round(font.metrics[1]);}public int getLeading(){return Math.round(font.metrics[2]);}public int getHeight(){return getAscent()+getDescent()+getLeading();}
    public int charWidth(char c){return Math.round(font.advance(c));}public int charWidth(int c){return Math.round(font.advance(c));}
    public int stringWidth(String s){float w=0;for(int i=0;i<s.length();){int cp=s.codePointAt(i);i+=Character.charCount(cp);w+=font.advance(cp);}return Math.round(w);}
}
