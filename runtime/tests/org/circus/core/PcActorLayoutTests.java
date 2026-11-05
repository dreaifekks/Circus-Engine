package org.circus.core;

/** Values independently calculated from DC2PC 41AA7D..41AB14 integer divisions. */
public final class PcActorLayoutTests {
    private static int checks;
    private static void rectangle(int[] actor,int w,int h,int x,int y,int rw,int rh,String why){
        PcActorLayout r=PcActorLayout.dc2pc(actor,w,h);checks++;
        if(r.x!=x||r.y!=y||r.width!=rw||r.height!=rh)throw new AssertionError(why+": "+r.x+","+r.y+","+r.width+","+r.height);
    }
    /** A crop inside a canvas, as the difference images of the ARCHIVES build decode. */
    private static void cropped(int[] actor,int ox,int oy,int w,int h,int cw,int ch,boolean composed,int x,int y,int rw,int rh,String why){
        Crx.Image image=new Crx.Image(ox,oy,w,h,32,new byte[0],null);image.canvasWidth=cw;image.canvasHeight=ch;image.composed=composed;
        PcActorLayout r=PcActorLayout.dc2pc(actor,image);checks++;
        if(r.x!=x||r.y!=y||r.width!=rw||r.height!=rh)throw new AssertionError(why+": "+r.x+","+r.y+","+r.width+","+r.height);
    }
    public static int run(){
        checks=0;
        cropped(new int[]{-240,0,100,441,277},323,61,285,695,882,756,true,83,61,285,695,"Cropped actor sits at its origin on the canvas");
        cropped(new int[]{160,0,100,441,285},313,28,283,728,882,756,true,473,28,283,728,"The second actor of the same scene");
        cropped(new int[]{-125,0,100,441,296},462,88,405,992,1260,1080,true,152,-36,405,992,"A cropped back-view still takes the large-canvas branch");
        cropped(new int[]{87,0,75,441,322},323,61,285,695,882,756,true,440,126,213,521,"Zoom scales the crop and its origin");
        cropped(new int[]{10,20,100,441,296},323,61,285,695,882,756,false,10,20,285,695,"A plain CRXG draws at the position: 40C610 adds no origin");
        cropped(new int[]{10,20,100,441,296},0,0,882,756,0,0,true,10,20,882,756,"Old installs have no canvas table");
        rectangle(new int[]{-125,0,100,441,296},1260,1080,-310,-124,1260,1080,"Dacapo Otome back-view anchor");
        rectangle(new int[]{87,0,75,441,322},882,756,198,81,661,567,"Dacapo Sakura at 75 percent");
        rectangle(new int[]{100,0,100,441,322},1260,1080,-85,-135,1260,1080,"Later reverse-angle Sakura back-view");
        rectangle(new int[]{-87,0,75,441,296},882,756,24,74,661,567,"Later reverse-angle Otome");
        rectangle(new int[]{8,-9,100,441,296},882,756,8,-9,882,756,"Ordinary canvas must not move");
        rectangle(new int[]{0,0,100,441,296},891,764,-4,-2,891,764,"Two integer divisions rather than floating point scaling");
        rectangle(new int[]{3,4,100,-441,-296},1260,1080,188,128,1260,1080,"Signed division truncates toward zero");
        rectangle(new int[]{8,9,50,441,296},1260,1080,229,157,630,540,"Explicit zoom does not apply the native-size branch");
        rectangle(new int[]{0,0,100,441,296},1260,756,-185,0,1260,756,"Oversize width and height are independent");
        return checks;
    }
    public static void main(String[] args){System.out.println("PC ACTOR LAYOUT PASS "+run());}
}
