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
        // DC2DM: 21 px cells, half width 10, ruby 12 px high up, and lines holding ruby drop.
        l=PcText.layout("｛かな／漢字｝a\nい",312,456,32,PcText.DM);
        check(at(l,"漢").y==462&&at(l,"か").y==450&&at(l,"い").y==494,"DM lines with ruby drop by 6");
        check(at(l,"a").x==312+42,"DM full width cells are 21 px");
        l=PcText.layout("a｛かな／字｝\n｛か／字｝い",312,456,32,PcText.DM);
        check(at(l,"い").y==456+6+32+10,"Later ruby lines drop 10 more");
        // D.D. 43CC50: kana are centred in equal cells over 22 px base characters.
        l=PcText.layout("｛さき／咲姫｝",100,0,34,PcText.DC3);
        check(at(l,"さ").x==100+4&&at(l,"き").x==100+22+4&&at(l,"さ").y==-10,"D.D. ruby is centred in its cells");
        l=PcText.layout("あ｛あいうえ／字｝",100,0,34,PcText.DC3);
        check(at(l,"あ").size==22&&at(l,"い").x==100+22-17+14,"Wide D.D. ruby moves left by half its overflow");
        check(at(PcText.layout("あ\nい",0,0,34,PcText.DC3),"い").line==1,"Glyphs know their line");
        try{
            check(NativeText.expandRubyDots("あ｛・／甘味｝い").equals("あ｛・・／甘味｝い"),"One dot per double byte base character");
            check(NativeText.expandRubyDots("｛かな／漢字｝").equals("｛かな／漢字｝"),"Ordinary ruby is left alone");
        }catch(java.io.IOException e){throw new AssertionError(e);}
        System.out.println("PcText "+checks+" checks PASS");
    }
}
