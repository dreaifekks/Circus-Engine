package org.circus.core;

import java.io.*;
import java.util.*;

/** Collection-wide framing identification, separate from host implementation status. */
public final class GameVersion {
    public final String profileId;public final int scriptCount;
    private GameVersion(String id,int count){profileId=id;scriptCount=count;}
    public Profile profile()throws IOException{try{return Profiles.get(profileId);}catch(IllegalArgumentException e){throw new IOException("尚未实现此脚本版本的指令语义："+profileId,e);}}
    public static GameVersion detect(GameResources files)throws IOException{
        Set<String> common=null,pinned=new HashSet<>();int count=0;
        for(String path:new TreeSet<>(files.paths())){
            if(!path.startsWith("advdata/mes/")||!path.endsWith(".mes"))continue;
            Set<String> candidates=new HashSet<>();
            // Version admission needs only the header and marker after its label
            // table. Keep one provider handle per file, without reading the body
            // or trusting a cached result when the game directory changes.
            try(Archive.Reader reader=files.openReader(path)){
                long size=reader.size();if(size<8||size>64*1024*1024)throw new IOException("Invalid MES size: "+path);
                byte[] head=read(reader,0,8);long labels=Binary.u32(head,0);if(labels>1000000)throw new IOException("MES header count limit: "+path);
                Map<Integer,byte[]> markers=new HashMap<>();
                for(MesFormats.Format f:MesFormats.ALL){
                    long offset=4+labels*f.stride;int length=f.stride==6||Binary.u32(head,4)==3?3:f.version<256?1:2;
                    if(offset+length>=size)continue;
                    byte[] marker=markers.get(f.stride);if(marker==null){marker=read(reader,offset,(int)Math.min(3,size-offset));markers.put(f.stride,marker);}
                    if((length==1?Binary.u8(marker,0):Binary.u16(marker,0))==f.version){candidates.add(f.id);if(!f.mixed)pinned.add(f.id);}
                }
            }
            if(candidates.isEmpty())throw new IOException("无法识别 MES 版本："+path);
            if(common==null)common=candidates;else common.retainAll(candidates);
            if(common.isEmpty())throw new IOException("游戏目录混有不同版本的 MES："+path);
            count++;
        }
        if(count==0)throw new IOException("目录中没有 AdvData/MES/*.mes");
        common.retainAll(pinned);if(common.isEmpty())throw new IOException("无法识别 MES 版本");
        if(common.size()!=1)throw new IOException("MES 版本无法唯一确定："+new TreeSet<>(common));
        if(!files.paths().contains("advdata/mes/start.mes"))throw new IOException("缺少原版启动脚本 START.mes");
        return new GameVersion(common.iterator().next(),count);
    }
    private static byte[] read(Archive.Reader reader,long offset,int length)throws IOException{
        byte[] bytes=reader.read(offset,length);if(bytes.length!=length)throw new EOFException("Short MES version header");return bytes;
    }
}
