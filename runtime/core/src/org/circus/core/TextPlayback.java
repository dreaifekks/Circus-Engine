package org.circus.core;

/** Text timing derived from each native family; platform clocks are in milliseconds. */
public final class TextPlayback {
    private TextPlayback(){}
    public static long duration(String profile,int speed,int characters){
        int count=Math.max(0,characters);
        if(Dc3Host.supports(profile))return (long)(count*22.0/(Math.max(0,speed)*2+16)*16.66);
        if(profile.equals("dc2dm-9d72")){
            int nativeSpeed=Math.max(0,11-speed);
            if(nativeSpeed==0)return 0;
            int batch=nativeSpeed<4?4-nativeSpeed:1;
            return ((count+batch-1)/batch)*(nativeSpeed<4?30L:(nativeSpeed-3)*17L);
        }
        // PC1 417D20 / PC2 421F90: slow modes use a 5 ms tick; fast is immediate.
        return (speed&1)==0?count*5L:0;
    }
    public static int visible(String profile,int speed,String text,long elapsed,boolean complete){
        int count=text.codePointCount(0,text.length());if(complete)return text.length();
        int lo=0,hi=count;while(lo<hi){int mid=(lo+hi+1)/2;if(duration(profile,speed,mid)<=elapsed)lo=mid;else hi=mid-1;}
        return text.offsetByCodePoints(0,lo);
    }
}
