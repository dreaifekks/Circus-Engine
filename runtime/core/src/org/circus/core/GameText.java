package org.circus.core;
import java.io.*;
/** Encoding applies to prose only. Verified localized MES fingerprints avoid guessing by folder/EXE name. */
public final class GameText {
    private GameText(){}
    public static TextCodec detect(GameResources files,Profile profile)throws IOException{
        String name,hash;
        if(profile.id.equals("dc2pc-5769")){name="fst_1216_a1_cmn";hash="7fbc02bc2b9634d376c7771c43c2d7c8e12ce46311347316353b5db7f87feabd";}
        // D.C.II (2006) ships Shift_JIS prose; the 蘑菇 V9.9 patch rewrites the same script verbatim in GBK.
        else if(profile.id.equals("dc2-3363")){name="fst_1216_a1_cmn";hash="780e8c2ec5fadae3c9cc61364e92aad7cc14d8d50c1d9cd23f187ae86f613e46";}
        else if(profile.id.equals("dcpc-2063")){name="0223A_F01";hash="69b250bc06c5150cba7d925920523adb14124d95d41f5dc2407d500d45036df1";}
        else if(profile.id.equals("dc3wy-a09f")){name="cn_Data/wy_1_0706_a1_rcc";hash="f6b0cbf77be63924c2170b8d56f360cdba4961e4640726613fcb16f874eb4dc0";}
        else if(profile.id.equals("dc3pp-9872")){name="cn_Data/dc3pp_0518_free_aoi";hash="273132e95d6cd6a3e73aad0fae11dac7cd71df6d27d44315c3ec67501c268aa4";}
        else return configuration(files);
        try{if(Binary.sha256(files.read((name.indexOf('/')>=0?name:"AdvData/MES/"+name)+".mes",2*1024*1024)).equals(hash))return new TextCodec("","","GBK");}catch(FileNotFoundException absent){}
        return TextCodec.identity();
    }
    public static TextCodec configuration(GameResources files)throws IOException{
        try{return configuration(files,"uif_config.json");}catch(FileNotFoundException e){return TextCodec.identity();}
    }
    public static TextCodec select(GameResources files,Profile profile,String encoding,String configuration)throws IOException{
        TextCodec map=configuration==null||configuration.isEmpty()?configuration(files):configuration.equals("@none")?TextCodec.identity():configuration(files,configuration);
        String detected=encoding==null||encoding.equals("auto")?detectEncoding(files,profile):encoding;
        return map.withEncoding(detected);
    }
    private static String detectEncoding(GameResources files,Profile profile)throws IOException{return detect(files,profile).charset.name().equalsIgnoreCase("GBK")?"GBK":"windows-31j";}
    public static TextCodec configuration(GameResources files,String path)throws IOException{
        byte[] raw=files.read(path,2*1024*1024);String text=new String(raw,java.nio.charset.StandardCharsets.UTF_8);if(text.startsWith("\uFEFF"))text=text.substring(1);
        Object parsed=DataJson.parse(text);
        if(!(parsed instanceof java.util.Map))throw new IOException("Invalid uif_config.json root");
        java.util.Map<?,?> root=(java.util.Map<?,?>)parsed;
        Object tunnel=root.get("tunnel_decoder"),sub=root.get("character_substitution");
        if(tunnel instanceof java.util.Map&&Boolean.TRUE.equals(((java.util.Map<?,?>)tunnel).get("enable")))throw new IOException("Enabled tunnel decoder is not implemented");
        if(!(sub instanceof java.util.Map)||!Boolean.TRUE.equals(((java.util.Map<?,?>)sub).get("enable")))return TextCodec.identity();
        java.util.Map<?,?> map=(java.util.Map<?,?>)sub;Object source=map.get("source_characters"),target=map.get("target_characters");
        if(!(source instanceof String)||!(target instanceof String))throw new IOException("Invalid character substitution table");
        return new TextCodec((String)source,(String)target);
    }
}
