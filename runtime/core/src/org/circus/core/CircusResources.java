package org.circus.core;

import java.io.*;
import java.util.*;

/** Shared CIRCUS resource access with an explicitly selected MES profile. */
public class CircusResources {
    public final GameResources files;
    public final NamesCatalog names;
    public final Profile profile;
    public final String profileId;
    public CircusResources(GameResources files,Profile profile)throws IOException {
        this.files=files;this.profile=profile;profileId=profile.id;names=PcHost.supports(profile)?new NamesCatalog(files.read("AdvData/DAT/NAMES.DAT",32*1024*1024),true):files.names("AdvData/GRP/NAMES.DAT");
    }
    /** Resource-only backends have their own script format and no MES profile. */
    protected CircusResources(GameResources files,String profileId){this.files=files;this.profileId=profileId;profile=null;names=null;}
    public String named(String category,int index)throws IOException {
        List<NamesCatalog.Entry> entries=names.table(category);
        if(index<0||index>=entries.size())throw new IOException("NAMES "+category+" index "+index);
        return entries.get(index).name;
    }
    private static String extension(String path,String ext){return path.toLowerCase(Locale.ROOT).endsWith(ext.toLowerCase(Locale.ROOT))?path:path+ext;}
    private static String basename(String path){int slash=Math.max(path.lastIndexOf('/'),path.lastIndexOf('\\'));return path.substring(slash+1);}
    /** DM 428990: an accessory follows its actor's close-up variant and color filter.
     * Resolve at presentation time so old saves/captures and a retained accessory also
     * follow a later actor change. Never rewrite the MES reference or archive name. */
    public String characterOverlay(String actor,String overlay){
        if(!profile.id.equals("dc2dm-9d72")||actor==null||actor.isEmpty()||overlay==null||overlay.isEmpty())return overlay;
        String base=actor.split(",",-1)[0];
        int comma=overlay.indexOf(',');String name=comma<0?overlay:overlay.substring(0,comma),archive=comma<0?"":overlay.substring(comma);
        String ext=name.toLowerCase(Locale.ROOT).endsWith(".crx")?name.substring(name.length()-4):"";
        if(!ext.isEmpty())name=name.substring(0,name.length()-4);
        if(base.toLowerCase(Locale.ROOT).endsWith(".crx"))base=base.substring(0,base.length()-4);
        char filter=base.charAt(base.length()-1);
        if(filter=='@'||filter=='*')base=base.substring(0,base.length()-1);else filter=0;
        if(name.endsWith("@")||name.endsWith("*"))name=name.substring(0,name.length()-1);
        if(name.toLowerCase(Locale.ROOT).endsWith("_b"))name=name.substring(0,name.length()-2);
        if(base.toLowerCase(Locale.ROOT).endsWith("_b"))name+="_B";
        if(filter!=0)name+=filter;
        return name+ext+archive;
    }
    /** Windows archive lookup ignores ASCII case; non-ASCII filename bytes remain exact. */
    private Archive.Entry archiveEntry(String archive,String requested)throws IOException {
        byte[] raw=requested.getBytes(Binary.SJIS);Archive.Entry found=null;
        for(Archive.Entry e:files.archive(archive).entries){
            if(e.nameBytes.length!=raw.length)continue;boolean same=true;
            for(int i=0;i<raw.length;i++){int a=raw[i]&255,b=e.nameBytes[i]&255;
                if((a>=0x81&&a<=0x9f)||(a>=0xe0&&a<=0xfc)){if(a!=b||++i>=raw.length||raw[i]!=e.nameBytes[i]){same=false;break;}continue;}
                if(a>='a'&&a<='z')a-=32;if(b>='a'&&b<='z')b-=32;if(a!=b){same=false;break;}}
            // DD's CRM loop 44B2DE..44B33E selects the first matching index.
            if(same){found=e;break;}
        }
        if(found==null)throw new FileNotFoundException(archive+" : "+requested);return found;
    }
    public Crx.Image image(String reference)throws IOException {
        if(reference.startsWith("pck:")){int hash=reference.lastIndexOf('#');if(hash<5)throw new IOException("Invalid indexed image");String path="AdvData/GRP/"+Binary.safePath(reference.substring(4,hash));int index;try{index=Integer.parseInt(reference.substring(hash+1));}catch(NumberFormatException e){throw new IOException("Invalid image index",e);}Archive a=files.archive(path);if(index<0||index>=a.entries.size())throw new IOException("Image index out of range");return files.imageAtOffset(path,a.entries.get(index).offset);}
        String[] parts=reference.split(",",-1);if(parts.length>2)throw new IOException("Unsupported image reference "+reference);
        int end=parts[0].toLowerCase(Locale.ROOT).endsWith(".crx")?parts[0].length()-4:parts[0].length();char filter=end==0?0:parts[0].charAt(end-1);
        if(filter=='@'||filter=='*'){if(!Dc3Host.supports(profile)&&!profile.id.equals("dc2dm-9d72")&&!PcHost.supports(profile))throw new IOException("Unverified image filter for "+profile.id);parts[0]=parts[0].substring(0,end-1)+parts[0].substring(end);}else filter=0;
        if(parts.length==1){String file="AdvData/GRP/"+extension(Binary.safePath(parts[0]),".CRX");return tint(PcHost.supports(profile)?Crx.plain(files.read(file,128*1024*1024),true):files.image(file),filter,profile.id.equals("dc2pc-5769")?2:profile.id.equals("dcpc-2063")?1:0);}
        String archive="AdvData/GRP/"+extension(Binary.safePath(parts[1]),".CRM");
        String entry=extension(basename(Binary.safePath(parts[0])),".CRX");
        return tint(files.imageAtOffset(archive,archiveEntry(archive,entry).offset),filter,profile.id.equals("dc2pc-5769")?2:profile.id.equals("dcpc-2063")?1:0);
    }
    /** DD 44A1A0/44A2D0 and DM 405560/405690 integer transforms; transparent pixels are untouched. */
    static Crx.Image tint(Crx.Image source,char filter)throws IOException {return tint(source,filter,false);}
    /** PC2 404110/404240 uses a different warm tint from DD/DM. */
    static Crx.Image tint(Crx.Image source,char filter,boolean pc2)throws IOException {return tint(source,filter,pc2?2:0);}
    static Crx.Image tint(Crx.Image source,char filter,int generation)throws IOException {
        if(filter==0)return source;if(filter!='@'&&filter!='*')throw new IOException("Unknown image color filter");
        if(source.bpp==8)throw new IOException("Native color filter requires truecolor image");
        byte[] pixels=source.pixels.clone();int size=source.bpp/8;
        for(int y=0;y<source.height;y++)for(int x=0;x<source.width;x++){
            int p=y*source.stride+x*size,b=pixels[p]&255,g=pixels[p+1]&255,r=pixels[p+2]&255;
            if(size==4?(pixels[p+3]==0):(b==255&&g==0&&r==255))continue;
            if(filter=='@'){b=b*3/4+16;g=g*3/4;r=r*3/4;}else if(generation==1){b=b*3/4;g=g*3/4;r=r*3/4+16;}else if(generation==2){b=b*3/4;g=g*3/4+16;r=r*3/4+48;}else{b=b*7/8;g=g*7/8+8;r=r*7/8+32;}
            pixels[p]=(byte)b;pixels[p+1]=(byte)g;pixels[p+2]=(byte)r;
        }
        return new Crx.Image(source.x,source.y,source.width,source.height,source.bpp,pixels,source.palette);
    }
    public Xpcm audio(String reference)throws IOException {
        if(reference.startsWith("mix:"))return MixedVoice.decode(this,reference);
        String path=Binary.safePath(reference);
        if(path.regionMatches(true,0,"VOICE/",0,6)){
            String archive="AdvData/VOICE.DAT",entry=extension(path.substring(6).replace('/','\\'),".PCM");
            if(PcHost.supports(profile)){
                String raw=path.substring(6).replace('/','\\');if(raw.toLowerCase(Locale.ROOT).endsWith(".wav"))raw=raw.substring(0,raw.length()-4);raw=extension(raw,".PCM");
                Archive a=files.legacyVoice(archive);for(Archive.Entry e:a.entries)if(e.name.equalsIgnoreCase(raw))return Xpcm.decode(a.read(e));throw new FileNotFoundException("VOICE64 : "+raw);
            }
            Archive a=files.archive(archive);return Xpcm.decode(a.read(archiveEntry(archive,entry)));
        }
        return files.audio("AdvData/"+extension(path,".PCM"));
    }
    /** Playback export keeps long native PCM tracks out of the Java heap. */
    public void writeAudio(String reference,OutputStream out)throws IOException {
        // Resource-only editions override audio(); mixed voice still needs its
        // sample-wise mixer. Preserve those backends rather than interpreting
        // their references as ordinary XPCM paths.
        if(profile==null||reference.startsWith("mix:")){audio(reference).writeTo(out);return;}
        String path=Binary.safePath(reference);
        if(path.regionMatches(true,0,"VOICE/",0,6)){
            String archive="AdvData/VOICE.DAT",raw=path.substring(6).replace('/','\\');
            if(PcHost.supports(profile)){
                if(raw.toLowerCase(Locale.ROOT).endsWith(".wav"))raw=raw.substring(0,raw.length()-4);raw=extension(raw,".PCM");
                Archive a=files.legacyVoice(archive);for(Archive.Entry e:a.entries)if(e.name.equalsIgnoreCase(raw)){Xpcm.write(a.source(e),out);return;}
                throw new FileNotFoundException("VOICE64 : "+raw);
            }
            Archive a=files.archive(archive);Xpcm.write(a.source(archiveEntry(archive,extension(raw,".PCM"))),out);return;
        }
        Xpcm.write(files.source("AdvData/"+extension(path,".PCM")),out);
    }
    public MesVm.Library scripts()throws IOException{return files.scripts("AdvData/MES",profile);}
}
