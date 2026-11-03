package org.circus.core;

/** The ｛kana／base｝ layout against values worked out from DC2PC.exe 41CA2F..41CBED. */
public final class PcTextTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static PcText.Glyph at(PcText.Layout l,String text){for(PcText.Glyph g:l.glyphs)if(g.text.equals(text))return g;throw new AssertionError("No glyph "+text);}
    public static void main(String[] args){
        PcText.Layout l=PcText.layout("あ\nい",44,462,40);
        check(at(l,"あ").x==44&&at(l,"あ").y==462&&at(l,"あ").size==28,"First cell starts at the template origin");
        check(at(l,"い").x==44&&at(l,"い").y==502,"Only a line feed breaks a line, returning to the origin");
        check(l.ticks==3,"A line feed takes one typewriter tick");

        // Two base characters over four kana: room 56-48=8, so each kana takes 12 plus its share of 8.
        l=PcText.layout("｛おとねえ／音姉｝か",44,462,40);
        check(at(l,"音").x==44&&at(l,"姉").x==72&&at(l,"か").x==100,"Ruby takes no room in the base line");
        check(at(l,"お").y==451&&at(l,"お").size==12,"Kana sit 11 px above at 12 px");
        check(at(l,"お").x==44+1&&at(l,"ね").x==44+5+24&&at(l,"え").x==44+7+36,"Kana spread across the base width");
        check(l.ticks==5,"Opening and closing a group each cost a tick, kana do not");
        check(at(l,"お").tick==1,"The first kana appears with the base character that passes it");

        // More kana than the base can hold: they overflow both sides equally and do not push neighbours.
        l=PcText.layout("あ｛あいうえお／字｝か",44,462,40);
        check(at(l,"字").x==72&&at(l,"か").x==100,"Wide ruby does not move the base line");
        check(at(l,"い").x==72-16+12,"Wide ruby starts half its overflow to the left");

        // Unterminated markers are ordinary characters.
        l=PcText.layout("｛あ",44,462,40);
        check(at(l,"｛").x==44&&at(l,"あ").x==72,"An unclosed marker draws as text");
        check(PcText.plain("何故か｛おとねえ／音姉｝と").equals("何故か音姉と"),"Captions keep the base text");
        System.out.println("PcText "+checks+" checks PASS");
    }
}
