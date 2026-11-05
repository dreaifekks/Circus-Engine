package ios.awt;
/** Source-over software drawing on int ARGB pixels: the part of Java2D the Mac runtime uses. */
public final class Graphics2D extends Graphics {
    private final BufferedImage target;private final int[] d;private final int W,H;
    private double sx=1,sy=1,tx,ty;private int cx0,cy0,cx1,cy1;private boolean clipped;
    private Color color=Color.BLACK;private Font font;private AlphaComposite composite=AlphaComposite.SrcOver;private int ca=255;private boolean bilinear;
    Graphics2D(BufferedImage t){t.changed();target=t;d=t.data;W=t.width;H=t.height;cx1=W;cy1=H;}
    public void dispose(){}
    public Graphics2D create(){Graphics2D g=new Graphics2D(target);g.sx=sx;g.sy=sy;g.tx=tx;g.ty=ty;g.cx0=cx0;g.cy0=cy0;g.cx1=cx1;g.cy1=cy1;g.clipped=clipped;g.color=color;g.font=font;g.composite=composite;g.ca=ca;g.bilinear=bilinear;return g;}
    public void setRenderingHint(Object k,Object v){if(k==RenderingHints.KEY_INTERPOLATION)bilinear=v!=RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;}
    public void setColor(Color c){color=c;}public Color getColor(){return color;}
    public void setFont(Font f){font=f;}public Font getFont(){return font;}
    public FontMetrics getFontMetrics(){return new FontMetrics(font);}public FontMetrics getFontMetrics(Font f){return new FontMetrics(f);}
    public void setComposite(Composite c){composite=(AlphaComposite)c;ca=Math.round(composite.alpha*255);}public Composite getComposite(){return composite;}
    public void translate(int x,int y){tx+=sx*x;ty+=sy*y;}public void translate(double x,double y){tx+=sx*x;ty+=sy*y;}public void scale(double x,double y){sx*=x;sy*=y;}
    public AffineTransform getTransform(){AffineTransform a=new AffineTransform();a.sx=sx;a.sy=sy;a.tx=tx;a.ty=ty;return a;}
    public void setTransform(AffineTransform a){sx=a.sx;sy=a.sy;tx=a.tx;ty=a.ty;}
    // Clip is held in device space, as Java2D does, and reported in the current user space.
    public Shape getClip(){if(!clipped)return null;int x=(int)Math.round((cx0-tx)/sx),y=(int)Math.round((cy0-ty)/sy);return new Rectangle(x,y,(int)Math.round((cx1-cx0)/sx),(int)Math.round((cy1-cy0)/sy));}
    public void setClip(Shape s){if(s==null){clipped=false;cx0=cy0=0;cx1=W;cy1=H;return;}Rectangle r=(Rectangle)s;setClip(r.x,r.y,r.width,r.height);}
    public void setClip(int x,int y,int w,int h){clipped=true;int[] b=device(x,y,w,h);cx0=Math.max(0,b[0]);cy0=Math.max(0,b[1]);cx1=Math.min(W,b[2]);cy1=Math.min(H,b[3]);}
    public void clipRect(int x,int y,int w,int h){clipped=true;int[] b=device(x,y,w,h);cx0=Math.max(cx0,b[0]);cy0=Math.max(cy0,b[1]);cx1=Math.min(cx1,b[2]);cy1=Math.min(cy1,b[3]);}
    private int[] device(int x,int y,int w,int h){return new int[]{(int)Math.round(tx+sx*x),(int)Math.round(ty+sy*y),(int)Math.round(tx+sx*(x+w)),(int)Math.round(ty+sy*(y+h))};}
    private void put(int i,int c,int sa){
        if(sa==0)return;if(sa==255){d[i]=c|0xFF000000;return;}int b=d[i],da=b>>>24,ia=255-sa;
        if(da==255){d[i]=0xFF000000|(((c&0xFF00FF)*sa+(b&0xFF00FF)*ia)>>>8&0xFF00FF)|(((c&0xFF00)*sa+(b&0xFF00)*ia)>>>8&0xFF00);return;}
        if(da==0){d[i]=sa<<24|(c&0xFFFFFF);return;}
        int w=da*ia/255,oa=sa+w;d[i]=oa<<24|(((c>>16&255)*sa+(b>>16&255)*w)/oa)<<16|(((c>>8&255)*sa+(b>>8&255)*w)/oa)<<8|((c&255)*sa+(b&255)*w)/oa;}
    public void fillRect(int x,int y,int w,int h){int[] b=device(x,y,w,h);int x0=Math.max(cx0,b[0]),y0=Math.max(cy0,b[1]),x1=Math.min(cx1,b[2]),y1=Math.min(cy1,b[3]);if(x0>=x1||y0>=y1)return;
        int c=color.getRGB(),rule=composite.rule;if(rule==AlphaComposite.CLEAR){for(int r=y0;r<y1;r++)java.util.Arrays.fill(d,r*W+x0,r*W+x1,0);return;}if(rule==AlphaComposite.SRC){for(int r=y0;r<y1;r++)java.util.Arrays.fill(d,r*W+x0,r*W+x1,c);return;}
        int sa=(c>>>24)*ca/255;if(sa==255){int v=c|0xFF000000;for(int r=y0;r<y1;r++)java.util.Arrays.fill(d,r*W+x0,r*W+x1,v);return;}for(int r=y0;r<y1;r++){int i=r*W+x0;for(int n=x0;n<x1;n++,i++)put(i,c,sa);}}
    public void clearRect(int x,int y,int w,int h){AlphaComposite o=composite;composite=AlphaComposite.Clear;fillRect(x,y,w,h);composite=o;}
    public void drawRect(int x,int y,int w,int h){fillRect(x,y,w+1,1);fillRect(x,y+h,w+1,1);fillRect(x,y,1,h+1);fillRect(x+w,y,1,h+1);}
    public boolean drawImage(Image i,int x,int y,Object o){if(i==null)return true;BufferedImage b=(BufferedImage)i;return drawImage(i,x,y,x+b.width,y+b.height,0,0,b.width,b.height,o);}
    public boolean drawImage(Image i,int x,int y,int w,int h,Object o){if(i==null)return true;BufferedImage b=(BufferedImage)i;return drawImage(i,x,y,x+w,y+h,0,0,b.width,b.height,o);}
    public boolean drawImage(Image i,int dx1,int dy1,int dx2,int dy2,int sx1,int sy1,int sx2,int sy2,Object o){
        if(i==null)return true;BufferedImage img=(BufferedImage)i;double ax=tx+sx*dx1,ay=ty+sy*dy1,bx=tx+sx*dx2,by=ty+sy*dy2;
        if(ax==bx||ay==by||sx1==sx2||sy1==sy2)return true;
        // The exe samples one source pixel per target pixel (40E3E0). That was fine at its own resolution; here the screen
        // is enlarged again afterwards, and the dropped rows show. A whole picture drawn smaller is area averaged instead.
        int dw=(int)Math.rint(bx-ax),dh=(int)Math.rint(by-ay);
        if(!bilinear&&sx1==0&&sy1==0&&sx2==img.width&&sy2==img.height&&dw>0&&dh>0&&dw<=img.width&&dh<=img.height&&(dw<img.width||dh<img.height)){
            double osx=sx,osy=sy,otx=tx,oty=ty;sx=sy=1;tx=ty=0;int left=(int)Math.rint(ax),top=(int)Math.rint(ay);BufferedImage small=img.shrunk(dw,dh);
            try{return drawImage(small,left,top,left+dw,top+dh,0,0,dw,dh,o);}finally{sx=osx;sy=osy;tx=otx;ty=oty;}
        }
        int rule=composite.rule;boolean rgb=img.type==BufferedImage.TYPE_INT_RGB;
        if(bx-ax==sx2-sx1&&by-ay==sy2-sy1&&ax==Math.rint(ax)&&ay==Math.rint(ay)){ // one to one
            int ox=(int)ax-sx1,oy=(int)ay-sy1;int x0=Math.max(Math.max(cx0,(int)ax),ox),y0=Math.max(Math.max(cy0,(int)ay),oy),x1=Math.min(Math.min(cx1,(int)bx),ox+img.width),y1=Math.min(Math.min(cy1,(int)by),oy+img.height);if(x0>=x1||y0>=y1)return true;
            int[] s=img.data;for(int r=y0;r<y1;r++){int si=(r-oy)*img.width+x0-ox,di=r*W+x0;
                if(rule==AlphaComposite.CLEAR){java.util.Arrays.fill(d,di,di+x1-x0,0);continue;}
                if(rule==AlphaComposite.SRC||(rgb&&ca==255)){System.arraycopy(s,si,d,di,x1-x0);if(rgb)for(int n=0;n<x1-x0;n++)d[di+n]|=0xFF000000;continue;}
                for(int n=x0;n<x1;n++,si++,di++){int c=s[si],a=c>>>24;if(rgb)a=255;if(ca!=255)a=a*ca/255;if(a==0)continue;if(a==255){d[di]=c|0xFF000000;continue;}put(di,c,a);}}
            return true;}
        int x0=Math.max(cx0,(int)Math.floor(Math.min(ax,bx))),y0=Math.max(cy0,(int)Math.floor(Math.min(ay,by))),x1=Math.min(cx1,(int)Math.ceil(Math.max(ax,bx))),y1=Math.min(cy1,(int)Math.ceil(Math.max(ay,by)));if(x0>=x1||y0>=y1)return true;
        double ux=(sx2-sx1)/(bx-ax),uy=(sy2-sy1)/(by-ay);int[] s=img.data;int sw=img.width,sh=img.height;
        for(int r=y0;r<y1;r++){double v=sy1+(r+.5-ay)*uy;int di=r*W+x0;for(int n=x0;n<x1;n++,di++){double u=sx1+(n+.5-ax)*ux;int c;
                if(bilinear){double fu=u-.5,fv=v-.5;int iu=(int)Math.floor(fu),iv=(int)Math.floor(fv);if(iu<-1||iv<-1||iu>=sw||iv>=sh)continue;int wu=(int)((fu-iu)*256),wv=(int)((fv-iv)*256);int u0=Math.max(0,iu),u1=Math.min(sw-1,iu+1),v0=Math.max(0,iv),v1=Math.min(sh-1,iv+1);c=mix(mix(s[v0*sw+u0],s[v0*sw+u1],wu),mix(s[v1*sw+u0],s[v1*sw+u1],wu),wv);}
                else{int iu=(int)Math.floor(u),iv=(int)Math.floor(v);if(iu<0||iv<0||iu>=sw||iv>=sh)continue;c=s[iv*sw+iu];}
                if(rule==AlphaComposite.CLEAR){d[di]=0;continue;}int a=rgb?255:c>>>24;if(rule==AlphaComposite.SRC){d[di]=rgb?c|0xFF000000:c;continue;}if(ca!=255)a=a*ca/255;if(a==0)continue;put(di,c,a);}}
        return true;}
    private static int mix(int a,int b,int w){if(a==b||w==0)return a;int iw=256-w;return (((a>>>24)*iw+(b>>>24)*w)>>>8)<<24|(((a&0xFF00FF)*iw+(b&0xFF00FF)*w)>>>8&0xFF00FF)|(((a&0xFF00)*iw+(b&0xFF00)*w)>>>8&0xFF00);}
    public void drawString(String s,int x,int y){drawString(s,(float)x,(float)y);}
    public void drawString(String s,float x,float y){if(font==null)font=new Font("Hiragino Sans",Font.PLAIN,12);
        Font f=sx==1&&sy==1?font:Font.shared(font.name,Math.max(1,(int)Math.round(font.size*sy)));double pen=tx+sx*x;int base=(int)Math.round(ty+sy*y);int c=color.getRGB(),a0=(c>>>24)*ca/255;if(a0==0)return;
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);i+=Character.charCount(cp);Font.Glyph g=f.glyph(cp);int left=(int)Math.round(pen)-g.ox,top=base-g.baseline;int x0=Math.max(cx0,left),y0=Math.max(cy0,top),x1=Math.min(cx1,left+g.w),y1=Math.min(cy1,top+g.h);
            for(int r=y0;r<y1;r++){int mi=(r-top)*g.w+x0-left,di=r*W+x0;for(int n=x0;n<x1;n++,mi++,di++){int m=g.mask[mi]&255;if(m!=0)put(di,c,m*a0/255);}}pen+=g.advance;}}
}
