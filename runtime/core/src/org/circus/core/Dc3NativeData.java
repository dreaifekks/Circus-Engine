package org.circus.core;
import java.io.*;
import java.util.*;

/** Native 401590 name-to-LOGCHIP tile mapping. Reads strings from the user's PE. */
public final class Dc3NativeData {
    private final Map<String,Integer> faces=new HashMap<>();
    private static final int[] ADDRESSES={0x4845b0,0x4845a8,0x48459c,0x484594,0x48458c,0x484584,0x484580,0x484578,0x484570,0x484568,0x484560,0x484558,0x484550,0x484548,0x484540,0x484538,0x484530,0x484524,0x484518,0x484510,0x4844f8,0x4844f0,0x4844e8,0x4844e4,0x4844e0,0x4844d4,0x4844cc,0x4844bc,0x4844ac,0x4844a4,0x484498,0x484490,0x484488};
    private static final int[] IDS={0,0,1,2,3,3,4,5,6,7,8,9,10,11,12,13,14,16,15,30,18,19,20,21,22,23,24,25,26,27,28,29,31};
    public Dc3NativeData(CircusResources data,TextCodec codec)throws IOException{
        // These addresses describe D.D.'s optional backlog portraits, not the
        // interpreter shared by W.Y./P.P. Never read them from a different PE.
        if(!data.profile.id.equals("dc3dd-a5a8"))return;
        for(String path:data.files.executableCandidates()){
            try{Dc2NativeData.Pe pe=new Dc2NativeData.Pe(data.files.read(path,8*1024*1024));if((pe.bytes[pe.at(0x401590)]&255)!=0x56)throw new IOException("DD native table signature");
                Map<String,Integer> found=new HashMap<>();for(int n=0;n<IDS.length;n++)found.put(codec.apply(pe.string(ADDRESSES[n])),IDS[n]);faces.putAll(found);break;
            }catch(IOException invalid){faces.clear();if(data.files.explicitExecutable())throw new IOException("所选 EXE 不含兼容的 DC3DD 数据表："+path,invalid);}
        }
    }
    public int face(Dc2History.Entry e){Integer id=faces.get(e.speaker);if(id==null)return -1;return id==30&&e.script.equalsIgnoreCase("dd_3_0220_a1")?17:id;}
}
