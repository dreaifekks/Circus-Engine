package org.circus.core;

import java.util.*;

/** Version binding. Numeric opcodes are never inferred from another game's offsets. */
public final class Profile {
    public final String id;
    public final int version, headerStride, versionBytes, string8Start, stringStart, encodedStart, wordsStart;
    public final int extension, label, block, companion;
    public final boolean signedBlockScan, dollarToSpace, demo;
    public final boolean textVerified;
    public final Variables.Model variableModel;
    private final Map<Integer,String> operations;
    private int[] accepted;
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean demo,Map<Integer,String> ops){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,postmap,demo,Variables.Model.BYTE_WRAP,ops);
    }
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops){
        this(id,version,stride,vb,s8,s,enc,words,ext,label,block,companion,signed,postmap,textVerified,demo,variableModel,ops,null);
    }
    /** altVersions lists the other build stamps that share this exact framing and dispatch table. */
    public Profile(String id,int version,int stride,int vb,int s8,int s,int enc,int words,
                   int ext,int label,int block,int companion,boolean signed,boolean postmap,boolean textVerified,
                   boolean demo,Variables.Model variableModel,Map<Integer,String> ops,int[] altVersions){
        this.id=id;this.version=version;this.headerStride=stride;this.versionBytes=vb;this.string8Start=s8;
        this.stringStart=s;this.encodedStart=enc;this.wordsStart=words;this.extension=ext;
        this.label=label;this.block=block;this.companion=companion;this.signedBlockScan=signed;
        this.dollarToSpace=postmap;this.textVerified=textVerified;this.variableModel=variableModel;
        this.demo=demo;this.operations=Collections.unmodifiableMap(new HashMap<>(ops));
        if(altVersions==null||altVersions.length==0){accepted=new int[]{version};}
        else{accepted=new int[altVersions.length+1];accepted[0]=version;System.arraycopy(altVersions,0,accepted,1,altVersions.length);}
    }
    public boolean acceptsVersion(int v){for(int x:accepted)if(x==v)return true;return false;}
    public String operation(int opcode){if(id.equals("dcpc-2063")&&opcode>=0x80&&opcode<=0xff)return "native.no_effect";String s=operations.get(opcode);return s==null?"unknown":s;}
    public int opcode(String operation){for(Map.Entry<Integer,String> e:operations.entrySet())if(e.getValue().equals(operation))return e.getKey();throw new IllegalArgumentException(operation);}
}
