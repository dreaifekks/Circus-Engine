package ios.awt;
public final class AlphaComposite implements Composite {
    public static final int CLEAR=1,SRC=2,SRC_OVER=3;
    public static final AlphaComposite Clear=new AlphaComposite(CLEAR,1),Src=new AlphaComposite(SRC,1),SrcOver=new AlphaComposite(SRC_OVER,1);
    public final int rule;public final float alpha;
    private AlphaComposite(int r,float a){rule=r;alpha=a;}
    public static AlphaComposite getInstance(int r){return new AlphaComposite(r,1);}
    public static AlphaComposite getInstance(int r,float a){return new AlphaComposite(r,a);}
    public AlphaComposite derive(float a){return new AlphaComposite(rule,Math.max(0,Math.min(1,a)));}
    public float getAlpha(){return alpha;}public int getRule(){return rule;}
}
