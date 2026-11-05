package ios.awt;
public final class Color {
    public static final Color BLACK=new Color(0,0,0),WHITE=new Color(255,255,255),RED=new Color(255,0,0),GREEN=new Color(0,255,0),BLUE=new Color(0,0,255),YELLOW=new Color(255,255,0),GRAY=new Color(128,128,128),DARK_GRAY=new Color(64,64,64),LIGHT_GRAY=new Color(192,192,192),CYAN=new Color(0,255,255),MAGENTA=new Color(255,0,255),ORANGE=new Color(255,200,0),PINK=new Color(255,175,175);
    private final int argb;
    public Color(int r,int g,int b){this(r,g,b,255);}
    public Color(int r,int g,int b,int a){argb=(a&255)<<24|(r&255)<<16|(g&255)<<8|(b&255);}
    public Color(float r,float g,float b){this(r,g,b,1f);}
    public Color(float r,float g,float b,float a){this((int)(r*255+.5f),(int)(g*255+.5f),(int)(b*255+.5f),(int)(a*255+.5f));}
    public Color(int rgb){argb=rgb|0xFF000000;}
    public Color(int v,boolean hasAlpha){argb=hasAlpha?v:v|0xFF000000;}
    public int getRGB(){return argb;}public int getAlpha(){return argb>>>24;}public int getRed(){return argb>>16&255;}public int getGreen(){return argb>>8&255;}public int getBlue(){return argb&255;}
}
