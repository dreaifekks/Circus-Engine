package org.circus.core;

import java.nio.file.*;
import java.util.*;

/** Read-only corpus audit against the same Java API used on Android. No native EXE. */
public final class CompatibilityAudit {
    public static void main(String[] args)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(Paths.get(args[0])));
        GameVersion version=GameVersion.detect(files);CircusResources data=new CircusResources(files,version.profile());
        int scripts=0,records=0,encoded=0,headers=0;Map<String,Integer> operations=new TreeMap<>();
        for(String path:new TreeSet<>(files.paths()))if(path.startsWith("advdata/mes/")&&path.endsWith(".mes")){
            String name=path.substring("advdata/mes/".length());MesScript script=data.scripts().script(name);scripts++;
            for(long offset:script.headerOffsets){if(!script.boundary(script.versionOffset+(int)(offset&0x7fffffff)))throw new java.io.IOException("Header target outside boundary: "+path);headers++;}
            for(MesScript.Record record:script.records()){
                records++;String operation=script.profile.operation(record.opcode);operations.put(operation,operations.getOrDefault(operation,0)+1);
                if(record.opcode>=script.profile.encodedStart&&record.opcode<script.profile.wordsStart&&script.profile.textVerified){script.nativeText(record);encoded++;}
            }
        }
        String shared="SYSTEM/ADVMENUCHIP";if(!files.paths().contains("advdata/grp/system/advmenuchip.crx"))shared="SYSTEM/ADVMENU_CHIP";
        Crx.Image image=data.image(shared);
        System.out.println("profile="+version.profileId+" scripts="+scripts+" records="+records+" header_boundaries="+headers+" encoded_records="+encoded);
        System.out.println("shared_resource_decode="+shared+" "+image.width+"x"+image.height+" bpp="+image.bpp);
        System.out.println("physical_operation_counts="+operations);
        System.out.println("scope=structural/decoder/resource audit only; no native execution or playability claim");
    }
}
