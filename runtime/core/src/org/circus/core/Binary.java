package org.circus.core;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;

/** Bounded binary operations shared by the JVM tests and Android runtime. */
public final class Binary {
    private Binary() {}
    public static final Charset SJIS = Charset.forName("windows-31j");
    public static void range(long at, long size, long total) throws IOException {
        if (at < 0 || size < 0 || at > total || size > total-at) throw new IOException("Binary range outside input: " + at + "+" + size + "/" + total);
    }
    public static int u8(byte[] b, int p) throws IOException { range(p,1,b.length); return b[p]&255; }
    public static int u16(byte[] b, int p) throws IOException { return u8(b,p)|(u8(b,p+1)<<8); }
    public static int i16(byte[] b, int p) throws IOException { return (short)u16(b,p); }
    public static long u32(byte[] b, int p) throws IOException { return u16(b,p)|((long)u16(b,p+2)<<16); }
    public static int endZ(byte[] b, int p) throws IOException {
        range(p,1,b.length); for (int i=p;i<b.length;i++) if(b[i]==0)return i;
        throw new IOException("Unterminated string at " + p);
    }
    public static byte[] z(byte[] b, int p) throws IOException { return Arrays.copyOfRange(b,p,endZ(b,p)); }
    public static String hex(byte[] data) {
        StringBuilder s=new StringBuilder(data.length*2);
        for(byte b:data)s.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));
        return s.toString();
    }
    public static String sha256(byte[] data) {
        try{return hex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new AssertionError(e);}
    }
    public static byte[] read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n;
        while((n=in.read(b))!=-1){if(out.size()>limit-n)throw new IOException("Input exceeds limit " + limit);out.write(b,0,n);}
        return out.toByteArray();
    }
    public static String safePath(String name) throws IOException {
        if(name==null || name.length()==0 || name.length()>512 || name.startsWith("/") || name.startsWith("\\") || name.indexOf(':')>=0 || name.indexOf('\0')>=0)
            throw new IOException("Invalid relative resource path");
        String path=name.replace('\\','/');
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Invalid resource path segment");
        return path;
    }
    /** Relative filenames emitted by Windows MES scripts may repeat separators.
     * Inventory/document paths keep using safePath's stricter canonical form. */
    public static String nativeResourcePath(String name)throws IOException{
        if(name==null||name.isEmpty()||name.length()>512||name.startsWith("/")||name.startsWith("\\")||name.indexOf(':')>=0||name.indexOf('\0')>=0)return safePath(name);
        StringBuilder path=new StringBuilder(name.length());boolean slash=false;
        for(int i=0;i<name.length();i++){char c=name.charAt(i);if(c=='\\')c='/';if(c!='/'||!slash)path.append(c);slash=c=='/';}
        return safePath(path.toString());
    }
}
