package org.circus.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Game-supplied font substitution, applied after CP932 decoding, never to resource names. */
public final class TextCodec {
    private final Map<Integer,Integer> substitution;
    public final String fingerprint;
    public final Charset charset;
    public TextCodec(String source,String target)throws IOException {this(source,target,"windows-31j");}
    public TextCodec(String source,String target,String encoding)throws IOException {
        if(!encoding.equals("windows-31j")&&!encoding.equals("GBK"))throw new IOException("Unsupported text encoding");charset=Charset.forName(encoding);
        int[] from=points(source),to=points(target);
        if(from.length!=to.length||from.length>65536)throw new IOException("Invalid character substitution lengths");
        Map<Integer,Integer> map=new TreeMap<>();
        for(int i=0;i<from.length;i++){
            Integer previous=map.put(from[i],to[i]);
            if(previous!=null&&previous!=to[i])throw new IOException("Conflicting character substitution U+"+Integer.toHexString(from[i]));
        }
        substitution=Collections.unmodifiableMap(map);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        for(Map.Entry<Integer,Integer> e:map.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue());}
        if(encoding.equals("GBK"))out.writeUTF(encoding);
        fingerprint=Binary.sha256(bytes.toByteArray());
    }
    private static int[] points(String value)throws IOException {
        if(value==null)throw new IOException("Character substitution is missing");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){if(++i==value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IOException("Unpaired surrogate in character map");}
            else if(Character.isLowSurrogate(c))throw new IOException("Unpaired surrogate in character map");
        }
        return value.codePoints().toArray();
    }
    public static TextCodec identity(){try{return new TextCodec("","");}catch(IOException e){throw new AssertionError(e);}}
    public TextCodec withEncoding(String encoding)throws IOException{StringBuilder source=new StringBuilder(),target=new StringBuilder();for(Map.Entry<Integer,Integer> e:substitution.entrySet()){source.appendCodePoint(e.getKey());target.appendCodePoint(e.getValue());}return new TextCodec(source.toString(),target.toString(),encoding);}
    public static String cp932(byte[] bytes)throws IOException {
        try{return Binary.SJIS.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException("Invalid CP932 text",e);}
    }
    public String decode(byte[] bytes)throws IOException{
        return apply(decodeUnmapped(bytes));
    }
    public String decodeUnmapped(byte[] bytes)throws IOException{
        try{return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException("Invalid "+charset.name()+" text",e);}
    }
    public String apply(String value){
        StringBuilder out=new StringBuilder();
        value.codePoints().forEach(c->out.appendCodePoint(substitution.getOrDefault(c,c)));
        return out.toString();
    }
}
