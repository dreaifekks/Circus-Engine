package ios.awt;
/** Translation and scale: nothing in the runtime rotates or shears. */
public final class AffineTransform {
    public double sx=1,sy=1,tx,ty;
    public AffineTransform(){}public AffineTransform(AffineTransform o){sx=o.sx;sy=o.sy;tx=o.tx;ty=o.ty;}
    public void translate(double x,double y){tx+=sx*x;ty+=sy*y;}public void scale(double x,double y){sx*=x;sy*=y;}
}
