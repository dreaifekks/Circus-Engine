package ios.awt;
public final class BufferedImage extends Image {
    public static final int TYPE_INT_RGB=1,TYPE_INT_ARGB=2;
    public final int width,height,type;public final int[] data;
    public BufferedImage(int w,int h,int t){width=w;height=h;type=t;data=new int[w*h];if(t==TYPE_INT_RGB)java.util.Arrays.fill(data,0xFF000000);}
    public int getWidth(){return width;}public int getHeight(){return height;}public int getType(){return type;}
    @Override public int getWidth(Object o){return width;}@Override public int getHeight(Object o){return height;}
    public Graphics2D createGraphics(){return new Graphics2D(this);}public Graphics getGraphics(){return createGraphics();}
    public void flush(){}
    private BufferedImage smaller;
    void changed(){smaller=null;}
    /** Area average, weighted by alpha, kept for the next frame: a sprite is drawn at one size for many frames. */
    BufferedImage shrunk(int w,int h){
        if(smaller!=null&&smaller.width==w&&smaller.height==h)return smaller;
        BufferedImage out=new BufferedImage(w,h,type);double fx=(double)width/w,fy=(double)height/h;boolean opaque=type==TYPE_INT_RGB;
        for(int y=0;y<h;y++){double y0=y*fy,y1=(y+1)*fy;int ya=(int)y0,yb=Math.min(height,(int)Math.ceil(y1));
            for(int x=0;x<w;x++){double x0=x*fx,x1=(x+1)*fx;int xa=(int)x0,xb=Math.min(width,(int)Math.ceil(x1));double a=0,r=0,g=0,b=0,area=0;
                for(int v=ya;v<yb;v++){double wy=Math.min(y1,v+1)-Math.max(y0,v);for(int u=xa;u<xb;u++){double k=wy*(Math.min(x1,u+1)-Math.max(x0,u));int c=data[v*width+u];double pa=(opaque?255:c>>>24)*k;area+=k;a+=pa;r+=(c>>16&255)*pa;g+=(c>>8&255)*pa;b+=(c&255)*pa;}}
                if(a>0)out.data[y*w+x]=(int)(a/area+.5)<<24|(int)(r/a+.5)<<16|(int)(g/a+.5)<<8|(int)(b/a+.5);}}
        smaller=out;return out;
    }
    public int getRGB(int x,int y){return data[y*width+x]|(type==TYPE_INT_RGB?0xFF000000:0);}
    public void setRGB(int x,int y,int c){smaller=null;data[y*width+x]=type==TYPE_INT_RGB?c|0xFF000000:c;}
    public int[] getRGB(int x,int y,int w,int h,int[] out,int off,int scan){if(out==null)out=new int[off+h*scan];for(int r=0;r<h;r++)System.arraycopy(data,(y+r)*width+x,out,off+r*scan,w);return out;}
    public void setRGB(int x,int y,int w,int h,int[] in,int off,int scan){smaller=null;for(int r=0;r<h;r++){int di=(y+r)*width+x;System.arraycopy(in,off+r*scan,data,di,w);if(type==TYPE_INT_RGB)for(int i=0;i<w;i++)data[di+i]|=0xFF000000;}}
}
