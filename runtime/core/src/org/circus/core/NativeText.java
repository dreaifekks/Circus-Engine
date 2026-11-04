package org.circus.core;

import java.io.*;
import java.util.*;

/** Reviewed DD plain-text subset. Inline performance/ruby commands fail explicitly. */
public final class NativeText {
    private NativeText(){}
    public static final class UnsupportedControl extends IOException {
        public final int byteOffset;public final String control;
        UnsupportedControl(int offset,String control){super("Unimplemented text control "+control+" at decoded byte "+offset);byteOffset=offset;this.control=control;}
    }
    // 420CE0 macros and renderer-side commands. The native preprocessor scans bytes,
    // so do not hide a command-looking sequence just because @ is a CP932 trail byte.
    private static final String[] CONTROLS={"@re1","@re2","@muf","@lse","@wb","@bt1","@bt2","@bt3","@bt4","@bt5","@by","@bi","@bo","@hy","@ht","@mu","@se","@bc","@bf","@w","@b1","@b2","@b3","@b4","@b5","@t","@n","@c","@r","@z","@l","@m"};
    public static String plain(byte[] decoded,TextCodec codec,String family,String given,boolean speaker)throws IOException {
        if(decoded.length>8191)throw new IOException("Native text buffer limit");
        byte[] raw=decoded.clone();
        if(speaker){
            // strncmp compares using INPUT byte length, including the empty-prefix case.
            for(String alias:new String[]{"さくら２","さくら３"}){
                byte[] candidate=alias.getBytes(Binary.SJIS);
                if(raw.length<=candidate.length&&Arrays.equals(raw,Arrays.copyOf(candidate,raw.length))){raw="さくら".getBytes(Binary.SJIS);break;}
            }
        }
        for(int i=0;i<raw.length;i++){
            if(raw[i]==0)throw new IOException("Embedded NUL in decoded native text");
            for(String control:CONTROLS)if(starts(raw,i,control))throw new UnsupportedControl(i,control);
        }
        String value=codec.decodeUnmapped(raw);
        // 422567 also rewrites this byte pattern; keeping it as literal text is unsafe.
        int ruby=value.indexOf("｛・");if(ruby>=0&&!PcHost.followsExe)throw new UnsupportedControl(value.substring(0,ruby).getBytes(Binary.SJIS).length,"｛・");
        int replacements=0;
        while(true){
            int f=value.indexOf("&f"),n=value.indexOf("&n");if(f<0&&n<0)break;
            int at=f<0?n:n<0?f:Math.min(f,n);String name=at==f?family:given;
            if(name==null)throw new IOException("Native name variable "+(at==f?"&f":"&n")+" is not initialized");
            if(++replacements>64)throw new IOException("Recursive native name substitution");
            // Names may not inject unimplemented commands or bypass the byte-stage checks.
            if(name.indexOf('@')>=0||name.indexOf('\0')>=0||name.contains("｛・"))throw new IOException("Unimplemented control in native name");
            value=value.substring(0,at)+name+value.substring(at+2);
            if(value.length()>8191)throw new IOException("Expanded native text buffer limit");
        }
        return codec.apply(value);
    }
    /** 422567: one dot per double byte base character, so "｛・／甘｝" style marks spread over the whole word. */
    public static String expandRubyDots(String value)throws IOException{
        StringBuilder out=new StringBuilder();int at=0;
        while(true){
            int open=value.indexOf("｛・",at),slash=open<0?-1:value.indexOf('／',open),close=slash<0?-1:value.indexOf('｝',slash);
            if(close<0){out.append(value.substring(at));return out.toString();}
            String kana=value.substring(open+1,slash),base=value.substring(slash+1,close);
            out.append(value,at,open+1);
            if(kana.replace("・","").isEmpty()){int dots=(base.getBytes(Binary.SJIS).length+1)/2;for(int n=0;n<dots;n++)out.append('・');}else out.append(kana);
            out.append(value,slash,close+1);at=close+1;
        }
    }
    private static boolean starts(byte[] b,int at,String s){if(at+s.length()>b.length)return false;for(int i=0;i<s.length();i++)if((b[at+i]&255)!=s.charAt(i))return false;return true;}
}
